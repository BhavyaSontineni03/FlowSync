"""Loads the trained semi-supervised model and scores a single submission."""

from __future__ import annotations

from datetime import timedelta
from pathlib import Path

import joblib
import numpy as np
import pandas as pd

from app.features import FEATURE_NAMES, OrgCategoryStats, _amount_closeness, _date_proximity
from app.schemas import ScoreRequest, ScoreResponse

MODELS_DIR = Path(__file__).resolve().parent.parent / "models"
NEW_MODEL_PATH = MODELS_DIR / "anomaly_scorer.joblib"
LEGACY_MODEL_PATH = MODELS_DIR / "isolation_forest.joblib"
ORG_STATS_PATH = MODELS_DIR / "org_stats.joblib"
REFERENCE_SCORES_PATH = MODELS_DIR / "reference_scores.npy"


class AnomalyScorer:
    """Thin wrapper around the persisted scaler + Isolation Forest +
    calibrator + reference stats. Loaded once at process startup.
    """

    def __init__(self) -> None:
        bundle_path = NEW_MODEL_PATH if NEW_MODEL_PATH.exists() else LEGACY_MODEL_PATH
        bundle = joblib.load(bundle_path)

        self.scaler = bundle["scaler"]
        self.feature_names = bundle["feature_names"]
        self.iforest = bundle.get("iforest") or bundle.get("model")
        self.calibrator = bundle.get("calibrator")
        self.model_version = bundle.get("model_version", "isolation-forest-v1")
        self.probability_threshold = float(bundle.get("probability_threshold", 0.5))

        self.org_stats: OrgCategoryStats = joblib.load(ORG_STATS_PATH)
        # Reference score distribution so a raw score can be reported as a
        # percentile -- easier for a reviewer UI than a raw probability.
        self._reference_scores = np.load(REFERENCE_SCORES_PATH)

    def _compute_features(self, req: ScoreRequest) -> dict[str, float]:
        history = req.user_category_history
        if len(history) >= 3:
            mean, std = float(np.mean(history)), float(np.std(history)) or 1.0
            amount_zscore = (req.amount - mean) / std
        else:
            org_arr = self.org_stats.stats.get(f"{req.org_id}:{req.category}")
            if org_arr is not None and len(org_arr) > 3:
                mean, std = float(np.mean(org_arr)), float(np.std(org_arr)) or 1.0
                amount_zscore = (req.amount - mean) / std
            else:
                amount_zscore = 0.0

        category_percentile = self.org_stats.percentile(req.org_id, req.category, req.amount)
        submission_lag_days = float((req.submitted_at.date() - req.expense_date).days)
        weekend_flag = 1.0 if req.expense_date.weekday() >= 5 else 0.0

        best_similarity = 0.0
        velocity = 1
        for prior in req.recent_submissions:
            gap = req.submitted_at - prior.submitted_at
            if timedelta(0) <= gap <= timedelta(days=14):
                similarity = (
                    0.5 * _amount_closeness(req.amount, prior.amount)
                    + 0.3 * _date_proximity(pd.Timestamp(req.expense_date), pd.Timestamp(prior.expense_date))
                    + 0.2 * (1.0 if prior.vendor == req.vendor and prior.category == req.category else 0.0)
                )
                best_similarity = max(best_similarity, similarity)
            if timedelta(0) <= gap <= timedelta(hours=24):
                velocity += 1

        return {
            "amount_zscore": amount_zscore,
            "category_percentile": category_percentile,
            "submission_lag_days": submission_lag_days,
            "weekend_flag": weekend_flag,
            "duplicate_similarity": best_similarity,
            "submission_velocity_24h": float(velocity),
        }

    def score(self, req: ScoreRequest) -> ScoreResponse:
        features = self._compute_features(req)
        vector = np.array([[features[name] for name in self.feature_names]])
        scaled = self.scaler.transform(vector)

        if_score = float(-self.iforest.score_samples(scaled)[0])

        if self.calibrator is not None:
            # Semi-supervised path: 6 features + IF score -> probability.
            z = np.hstack([scaled, np.array([[if_score]])])
            anomaly_score = float(self.calibrator.predict_proba(z)[0, 1])
            is_anomalous = anomaly_score >= self.probability_threshold
        else:
            # Legacy pure-IF fallback for older model artifacts.
            anomaly_score = if_score
            is_anomalous = bool(self.iforest.predict(scaled)[0] == -1)

        percentile = float((self._reference_scores <= anomaly_score).mean() * 100)

        return ScoreResponse(
            anomaly_score=round(anomaly_score, 4),
            is_anomalous=bool(is_anomalous),
            percentile_in_reference=round(percentile, 1),
            features={k: round(v, 4) for k, v in features.items()},
            model_version=self.model_version,
        )
