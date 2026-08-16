"""
Feature engineering for expense-submission anomaly scoring.

All six features are derived purely from information that is available at
submission time (the expense itself plus the submitting user's own prior
history). None of them peek at the ground-truth label used in evaluation,
and none of them peek at data that would not yet exist in production at
scoring time (later submissions never influence an earlier one's features).

The six features:
  1. amount_zscore            -- how unusual the amount is for this user in
                                  this category, relative to their own history
  2. category_percentile       -- how unusual the amount is relative to the
                                  whole organization's spending in that category
  3. submission_lag_days       -- gap between when the expense happened and
                                  when it was filed (very stale filings are
                                  a classic manipulation signal)
  4. weekend_flag              -- whether the expense date fell on a weekend
  5. duplicate_similarity      -- fuzzy match strength against the user's own
                                  recent submissions (amount + date + vendor +
                                  category), catching near-duplicates that a
                                  plain keyword/text match would miss
  6. submission_velocity_24h   -- how many expenses this user has filed in the
                                  trailing 24 hours, including this one
                                  (burst / structuring behaviour)
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime, timedelta
from typing import Iterable, Sequence

import numpy as np
import pandas as pd

FEATURE_NAMES: tuple[str, ...] = (
    "amount_zscore",
    "category_percentile",
    "submission_lag_days",
    "weekend_flag",
    "duplicate_similarity",
    "submission_velocity_24h",
)

# Categories mirror the ExpenseCategory enum on the Java side
# (backend/src/main/java/com/expensemanagement/model/Expense.java) so the
# feature contract stays aligned across both services.
CATEGORIES: tuple[str, ...] = (
    "TRAVEL",
    "MEALS",
    "ACCOMMODATION",
    "TRANSPORTATION",
    "OFFICE_SUPPLIES",
    "SOFTWARE",
    "TRAINING",
    "ENTERTAINMENT",
    "UTILITIES",
    "OTHER",
)


def _date_proximity(a: pd.Timestamp, b: pd.Timestamp, window_days: int = 14) -> float:
    """1.0 when same day, decaying linearly to 0 at `window_days` apart."""
    delta = abs((a - b).days)
    return max(0.0, 1.0 - delta / window_days)


def _amount_closeness(a: float, b: float) -> float:
    """1.0 when identical, decaying as the relative gap grows."""
    denom = max(abs(a), abs(b), 1.0)
    return max(0.0, 1.0 - abs(a - b) / denom)


@dataclass
class OrgCategoryStats:
    """Reference distribution of org-wide spend per category, used for the
    percentile feature. Built once from historical data (a snapshot), the
    same way a production system would maintain a rolling materialized view
    rather than scanning the whole ledger on every request."""

    stats: dict[str, np.ndarray]

    @classmethod
    def from_frame(cls, df: pd.DataFrame) -> "OrgCategoryStats":
        stats: dict[str, np.ndarray] = {}
        for (org_id, category), group in df.groupby(["org_id", "category"]):
            stats[f"{org_id}:{category}"] = np.sort(group["amount"].to_numpy())
        return cls(stats)

    def percentile(self, org_id: str, category: str, amount: float) -> float:
        key = f"{org_id}:{category}"
        arr = self.stats.get(key)
        if arr is None or len(arr) == 0:
            return 50.0
        return float(np.searchsorted(arr, amount, side="right") / len(arr) * 100.0)


def compute_features_for_stream(df: pd.DataFrame, org_stats: OrgCategoryStats) -> pd.DataFrame:
    """Compute the 6 features for every row of a chronologically-sorted
    expense stream. Each row only ever looks *backwards* in time (its own
    user's earlier submissions), matching how the service would behave
    scoring one submission at a time in production -- there is no leakage
    from future rows into past ones.
    """
    df = df.sort_values("submitted_at").reset_index(drop=True)

    # Running per-(user, category) amount history for the z-score feature.
    user_category_history: dict[tuple[str, str], list[float]] = {}
    # Running per-user submission history (amount, date, vendor, category,
    # submitted_at) for duplicate-similarity and velocity features.
    user_recent: dict[str, list[dict]] = {}

    rows = []
    for row in df.itertuples(index=False):
        uc_key = (row.user_id, row.category)
        history = user_category_history.setdefault(uc_key, [])

        # --- Feature 1: amount z-score vs the user's own category history ---
        if len(history) >= 3:
            mean = float(np.mean(history))
            std = float(np.std(history)) or 1.0
            amount_zscore = (row.amount - mean) / std
        else:
            # Not enough personal history yet -- fall back to org-wide
            # category stats so cold-start users still get a sane score.
            org_arr = org_stats.stats.get(f"{row.org_id}:{row.category}")
            if org_arr is not None and len(org_arr) > 3:
                mean, std = float(np.mean(org_arr)), float(np.std(org_arr)) or 1.0
                amount_zscore = (row.amount - mean) / std
            else:
                amount_zscore = 0.0

        # --- Feature 2: percentile within org-wide category spend ---
        category_percentile = org_stats.percentile(row.org_id, row.category, row.amount)

        # --- Feature 3: submission lag ---
        submission_lag_days = float((row.submitted_at - row.expense_date).days)

        # --- Feature 4: weekend flag ---
        weekend_flag = 1.0 if row.expense_date.weekday() >= 5 else 0.0

        # --- Feature 5: duplicate similarity against user's recent filings ---
        recent = user_recent.setdefault(row.user_id, [])
        best_similarity = 0.0
        for prior in recent:
            if (row.submitted_at - prior["submitted_at"]) > timedelta(days=14):
                continue
            similarity = (
                0.5 * _amount_closeness(row.amount, prior["amount"])
                + 0.3 * _date_proximity(row.expense_date, prior["expense_date"])
                + 0.2 * (1.0 if prior["vendor"] == row.vendor and prior["category"] == row.category else 0.0)
            )
            best_similarity = max(best_similarity, similarity)

        # --- Feature 6: submission velocity in the trailing 24h ---
        velocity = 1 + sum(
            1 for prior in recent
            if (row.submitted_at - prior["submitted_at"]) <= timedelta(hours=24)
        )

        rows.append({
            "amount_zscore": amount_zscore,
            "category_percentile": category_percentile,
            "submission_lag_days": submission_lag_days,
            "weekend_flag": weekend_flag,
            "duplicate_similarity": best_similarity,
            "submission_velocity_24h": float(velocity),
        })

        # Update running history *after* computing this row's features.
        history.append(row.amount)
        recent.append({
            "amount": row.amount,
            "expense_date": row.expense_date,
            "vendor": row.vendor,
            "category": row.category,
            "submitted_at": row.submitted_at,
        })

    features = pd.DataFrame(rows, columns=list(FEATURE_NAMES))
    return pd.concat([df.reset_index(drop=True), features], axis=1)
