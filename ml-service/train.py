"""
Trains the semi-supervised anomaly scorer and evaluates it on
held-out, chronologically later data.

Pipeline (mirrors how this would run in production):
  1. Isolation Forest is fit unsupervised on the training split's 6 features
     only. It never sees labels.
  2. A reviewer-label simulator reveals ground truth for the top ~20% of
     submissions by IF score (the review queue) plus a small random audit
     sample of the rest. Everything else stays unlabeled.
  3. A HistGradientBoosting calibrator is fit on those reviewed labels,
     using the 6 engineered features plus the IF anomaly score.
  4. Live scoring ranks by calibrator probability. The operating point used
     for headline metrics is a 12% flag rate (business choice: how large a
     review queue the org can absorb).

Chronological 60/15/25 train/validation/test split. Contamination /
reviewer rates are selected on validation behavior; final numbers are
reported once on the untouched test split.

Run: python3 train.py
Writes: models/anomaly_scorer.joblib, models/org_stats.joblib,
        models/reference_scores.npy, models/metrics.json, REPORT.md
"""
from __future__ import annotations

import json
import time
from pathlib import Path

import joblib
import numpy as np
import pandas as pd
from sklearn.ensemble import HistGradientBoostingClassifier, IsolationForest
from sklearn.metrics import f1_score, precision_score, recall_score
from sklearn.preprocessing import RobustScaler

from app.baseline import flag_duplicates
from app.features import FEATURE_NAMES, OrgCategoryStats, compute_features_for_stream

RANDOM_STATE = 42
MODEL_VERSION = "semi-supervised-if-hgb-v1"
OPERATING_FLAG_RATE = 0.12
# Default reviewer simulation (validated in experiments/semi_supervised.py).
REVIEW_FLAG_RATE = 0.20
AUDIT_RATE = 0.05

MODELS_DIR = Path("models")


def _precision_recall_at_flag_rate(y_true, scores, flag_rate: float):
    threshold = np.percentile(scores, 100 - flag_rate * 100)
    flagged = (scores >= threshold).astype(int)
    return (
        float(precision_score(y_true, flagged, zero_division=0)),
        float(recall_score(y_true, flagged, zero_division=0)),
        flagged,
        float(threshold),
    )


def _build_reviewer_labels(
    y: np.ndarray,
    if_scores: np.ndarray,
    review_flag_rate: float,
    audit_rate: float,
    rng: np.random.Generator,
) -> np.ndarray:
    """Simulate production reviewer feedback.

    Top `review_flag_rate` by IF score are sent to review (labels revealed).
    An additional `audit_rate` of the remainder is randomly audited.
    Unreviewed rows stay unlabeled (-1).
    """
    labels = np.full(len(y), -1, dtype=int)
    review_cutoff = np.percentile(if_scores, 100 - review_flag_rate * 100)
    reviewed = if_scores >= review_cutoff
    labels[reviewed] = y[reviewed]

    unreviewed_idx = np.where(~reviewed)[0]
    n_audit = max(1, int(len(unreviewed_idx) * audit_rate))
    audit_idx = rng.choice(unreviewed_idx, size=min(n_audit, len(unreviewed_idx)), replace=False)
    labels[audit_idx] = y[audit_idx]
    return labels


def main() -> None:
    MODELS_DIR.mkdir(exist_ok=True)
    rng = np.random.default_rng(RANDOM_STATE)

    raw = pd.read_csv("data/expenses_raw.csv", parse_dates=["expense_date", "submitted_at"])
    raw = raw.sort_values("submitted_at").reset_index(drop=True)

    n = len(raw)
    train_end = int(n * 0.60)
    val_end = int(n * 0.75)

    org_stats = OrgCategoryStats.from_frame(raw.iloc[:train_end])
    featured = compute_features_for_stream(raw, org_stats)

    train_df = featured.iloc[:train_end].reset_index(drop=True)
    val_df = featured.iloc[train_end:val_end].reset_index(drop=True)
    test_df = featured.iloc[val_end:].reset_index(drop=True)

    y_train = train_df["is_anomaly"].to_numpy()
    y_val = val_df["is_anomaly"].to_numpy()
    y_test = test_df["is_anomaly"].to_numpy()

    scaler = RobustScaler().fit(train_df[list(FEATURE_NAMES)].to_numpy())
    X_train = scaler.transform(train_df[list(FEATURE_NAMES)].to_numpy())
    X_val = scaler.transform(val_df[list(FEATURE_NAMES)].to_numpy())
    X_test = scaler.transform(test_df[list(FEATURE_NAMES)].to_numpy())

    # ---- Stage 1: unsupervised Isolation Forest on train only ------------
    iforest = IsolationForest(
        n_estimators=300,
        contamination=0.12,
        random_state=RANDOM_STATE,
        n_jobs=-1,
    )
    iforest.fit(X_train)
    if_train = -iforest.score_samples(X_train)
    if_val = -iforest.score_samples(X_val)
    if_test = -iforest.score_samples(X_test)

    # ---- Stage 2: reviewer labels on train+val, then calibrator ----------
    X_pool = np.vstack([X_train, X_val])
    y_pool = np.concatenate([y_train, y_val])
    if_pool = np.concatenate([if_train, if_val])

    soft_labels = _build_reviewer_labels(
        y_pool, if_pool, REVIEW_FLAG_RATE, AUDIT_RATE, rng
    )
    labeled_mask = soft_labels >= 0
    n_labeled = int(labeled_mask.sum())
    n_pos = int((soft_labels[labeled_mask] == 1).sum())
    labeled_rate = n_labeled / len(soft_labels)

    Z_pool = np.hstack([X_pool, if_pool.reshape(-1, 1)])
    Z_test = np.hstack([X_test, if_test.reshape(-1, 1)])

    calibrator = HistGradientBoostingClassifier(
        max_depth=4,
        learning_rate=0.08,
        max_iter=200,
        min_samples_leaf=20,
        random_state=RANDOM_STATE,
    )
    calibrator.fit(Z_pool[labeled_mask], soft_labels[labeled_mask])

    t0 = time.perf_counter()
    calibrator_scores = calibrator.predict_proba(Z_test)[:, 1]
    scoring_ms_per_record = (time.perf_counter() - t0) / len(Z_test) * 1000

    precision, recall, y_pred, prob_threshold = _precision_recall_at_flag_rate(
        y_test, calibrator_scores, OPERATING_FLAG_RATE
    )
    f1 = float(f1_score(y_test, y_pred, zero_division=0))
    flagged_rate = float(y_pred.mean())

    # Pure-IF baseline at the same flag rate, for the report.
    if_precision, if_recall, _, _ = _precision_recall_at_flag_rate(
        y_test, if_test, OPERATING_FLAG_RATE
    )

    archetype_recall = {}
    for archetype in sorted(test_df["anomaly_type"].unique()):
        if archetype == "none":
            continue
        mask = test_df["anomaly_type"].to_numpy() == archetype
        if mask.sum() == 0:
            continue
        archetype_recall[archetype] = float(y_pred[mask].mean())

    baseline_flags = flag_duplicates(test_df).to_numpy()
    dup_mask = (test_df["anomaly_type"] == "near_duplicate").to_numpy()
    baseline_dup_recall = float(baseline_flags[dup_mask].mean()) if dup_mask.sum() else 0.0
    model_dup_recall = archetype_recall.get("near_duplicate", 0.0)
    baseline_missed_pct = (1 - baseline_dup_recall) * 100

    threshold_table = []
    for flag_pct in (15, 12, 10, 8, 6, 4):
        p, r, _, _ = _precision_recall_at_flag_rate(y_test, calibrator_scores, flag_pct / 100.0)
        threshold_table.append({
            "flag_rate_pct": flag_pct,
            "precision": round(p, 4),
            "recall": round(r, 4),
        })

    metrics = {
        "model_version": MODEL_VERSION,
        "pipeline": "isolation_forest_then_reviewer_calibrator",
        "review_flag_rate": REVIEW_FLAG_RATE,
        "audit_rate": AUDIT_RATE,
        "labeled_fraction_of_train_val": round(labeled_rate, 4),
        "n_reviewer_labels": n_labeled,
        "n_positive_reviewer_labels": n_pos,
        "operating_flag_rate": OPERATING_FLAG_RATE,
        "probability_threshold_at_operating_point": round(prob_threshold, 6),
        "test_set_size": int(len(test_df)),
        "test_set_anomaly_rate_pct": round(float(y_test.mean()) * 100, 2),
        "flagged_rate_pct": round(flagged_rate * 100, 2),
        "precision": round(precision, 4),
        "recall": round(recall, 4),
        "f1": round(f1, 4),
        "scoring_latency_ms_per_record": round(scoring_ms_per_record, 4),
        "pure_if_precision_at_operating_point": round(if_precision, 4),
        "pure_if_recall_at_operating_point": round(if_recall, 4),
        "recall_by_archetype": {k: round(v, 4) for k, v in archetype_recall.items()},
        "baseline_keyword_filter_near_duplicate_recall": round(baseline_dup_recall, 4),
        "baseline_keyword_filter_near_duplicate_missed_pct": round(baseline_missed_pct, 2),
        "model_near_duplicate_recall": round(model_dup_recall, 4),
        "threshold_sweep": threshold_table,
    }

    # Persist artifacts used by the live scoring service.
    joblib.dump(
        {
            "scaler": scaler,
            "iforest": iforest,
            "calibrator": calibrator,
            "feature_names": list(FEATURE_NAMES),
            "model_version": MODEL_VERSION,
            "operating_flag_rate": OPERATING_FLAG_RATE,
            "probability_threshold": float(prob_threshold),
        },
        MODELS_DIR / "anomaly_scorer.joblib",
    )
    # Keep the old filename as a symlink-equivalent copy so any leftover
    # tooling that still points at it keeps working during the transition.
    joblib.dump(
        {
            "model": iforest,
            "scaler": scaler,
            "feature_names": list(FEATURE_NAMES),
            "calibrator": calibrator,
            "model_version": MODEL_VERSION,
            "probability_threshold": float(prob_threshold),
        },
        MODELS_DIR / "isolation_forest.joblib",
    )

    full_org_stats = OrgCategoryStats.from_frame(raw.iloc[:val_end])
    joblib.dump(full_org_stats, MODELS_DIR / "org_stats.joblib")
    np.save(MODELS_DIR / "reference_scores.npy", calibrator_scores)
    with open(MODELS_DIR / "metrics.json", "w") as f:
        json.dump(metrics, f, indent=2)

    report = f"""# Anomaly Scoring Model -- Evaluation Report

Generated by `train.py`. Data is split chronologically 60/15/25 into
train/validation/test.

## Pipeline

1. **Isolation Forest (unsupervised)** is fit on the training split's 6
   engineered features only. It never sees the `is_anomaly` label.
2. **Reviewer labels** are simulated on train+val the way production would
   accumulate them: the top {REVIEW_FLAG_RATE:.0%} of submissions by IF score
   are sent to a review queue (labels revealed), plus a random
   {AUDIT_RATE:.0%} audit sample of the remainder. That labeled
   {labeled_rate:.1%} of train+val ({n_labeled} rows, {n_pos} confirmed
   anomalies).
3. A **HistGradientBoosting calibrator** is fit on those reviewer labels,
   using the 6 features plus the IF anomaly score.
4. Headline metrics use a **{OPERATING_FLAG_RATE:.0%} flag rate** on the
   calibrator's probability scores -- a business choice about review-queue
   size, not a number baked into the model.

Final precision/recall/F1 are measured once on the untouched test split.

## Headline numbers

| Metric | Value |
|---|---|
| Model version | {MODEL_VERSION} |
| Reviewer-labeled fraction of train+val | {labeled_rate:.1%} |
| Test set size | {metrics['test_set_size']} submissions |
| True anomaly rate in test set | {metrics['test_set_anomaly_rate_pct']}% |
| Rate flagged by the model | {metrics['flagged_rate_pct']}% |
| Precision | {metrics['precision']:.2%} |
| Recall | {metrics['recall']:.2%} |
| F1 | {metrics['f1']:.3f} |
| Scoring latency | {metrics['scoring_latency_ms_per_record']:.3f} ms/record (single-threaded, in-process) |
| Pure IF alone at the same flag rate | {if_precision:.2%} precision / {if_recall:.2%} recall |

## Recall by fraud archetype

| Archetype | Recall |
|---|---|
""" + "\n".join(f"| {k} | {v:.2%} |" for k, v in metrics["recall_by_archetype"].items()) + f"""

## Precision/recall at different flagging thresholds

The service returns a continuous anomaly probability; the flagging threshold
is a business decision. Sweeping it shows the tradeoff a reviewer queue
would actually operate under:

| Flag rate | Precision | Recall |
|---|---|---|
""" + "\n".join(
        f"| {row['flag_rate_pct']}% | {row['precision']:.1%} | {row['recall']:.1%} |"
        for row in threshold_table
    ) + f"""

## Versus the keyword/exact-match baseline it replaces

The baseline flags a submission as a duplicate only if the description
contains an explicit keyword (e.g. "duplicate") or the amount is an exact
byte-for-byte match to a prior filing. On the near-duplicate resubmissions in
the test set (amount nudged a fraction of a percent, description reworded,
filed a few days later):

- Baseline keyword/exact-match filter recall: {metrics['baseline_keyword_filter_near_duplicate_recall']:.2%}
  (misses {metrics['baseline_keyword_filter_near_duplicate_missed_pct']:.1f}% of them)
- Semi-supervised model recall on the same slice: {metrics['model_near_duplicate_recall']:.2%}

The gap is the `duplicate_similarity` feature plus the calibrator learning
from reviewer-confirmed near-duplicates: fuzzy closeness in amount, date,
vendor and category instead of requiring an exact text or value match.
"""
    with open("REPORT.md", "w") as f:
        f.write(report)

    print(json.dumps(metrics, indent=2))


if __name__ == "__main__":
    main()
