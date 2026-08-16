"""
Synthetic expense-submission dataset generator.

Simulates a realistic stream of expense filings across 5 organizations so the
anomaly model can be trained and evaluated without real (private) financial
data. Roughly 12% of records are deliberately injected anomalies drawn from
four archetypes seen in actual T&E fraud/error patterns:

  - near_duplicate   : the same spend resubmitted with a slightly different
                        amount or a few days later (the case a plain keyword
                        filter on the description text misses entirely,
                        since the text rarely matches verbatim)
  - backdated         : a large expense filed long after it happened
  - structuring       : a burst of same-day filings just under a round
                        approval threshold
  - amount_outlier    : a category spend far outside the org's normal range

The ground-truth label is stored alongside the data for evaluation only -- it
is never given to the Isolation Forest, which is trained unsupervised on the
6 engineered features exactly as it would be in production.
"""

from __future__ import annotations

import numpy as np
import pandas as pd

RNG = np.random.default_rng(seed=42)

ORG_IDS = [f"org-{i}" for i in range(1, 6)]
USERS_PER_ORG = 40
VENDORS_PER_CATEGORY = 12

# (category, mean, std, floor) -- realistic USD ranges per category.
CATEGORY_PROFILES = {
    "TRAVEL": (420.0, 130.0, 40.0),
    "MEALS": (38.0, 14.0, 6.0),
    "ACCOMMODATION": (185.0, 55.0, 60.0),
    "TRANSPORTATION": (28.0, 12.0, 4.0),
    "OFFICE_SUPPLIES": (55.0, 24.0, 5.0),
    "SOFTWARE": (140.0, 95.0, 9.0),
    "TRAINING": (310.0, 110.0, 50.0),
    "ENTERTAINMENT": (95.0, 36.0, 15.0),
    "UTILITIES": (70.0, 28.0, 8.0),
    "OTHER": (65.0, 32.0, 5.0),
}
CATEGORIES = list(CATEGORY_PROFILES.keys())

START_DATE = pd.Timestamp("2025-01-01")
END_DATE = pd.Timestamp("2026-06-30")


def _vendor_pool() -> dict[str, list[str]]:
    return {
        cat: [f"{cat.title().replace('_', ' ')} Vendor {i}" for i in range(VENDORS_PER_CATEGORY)]
        for cat in CATEGORIES
    }


def _random_date(rng: np.random.Generator) -> pd.Timestamp:
    span = (END_DATE - START_DATE).days
    return START_DATE + pd.Timedelta(days=int(rng.integers(0, span)))


def _sample_amount(category: str, rng: np.random.Generator) -> float:
    mean, std, floor = CATEGORY_PROFILES[category]
    amount = rng.gamma(shape=(mean / std) ** 2, scale=(std ** 2) / mean)
    return round(max(amount, floor), 2)


def generate(n_normal: int = 8800, n_anomalies: int = 1200) -> pd.DataFrame:
    vendors = _vendor_pool()
    users = [(org, f"{org}-user-{u}") for org in ORG_IDS for u in range(USERS_PER_ORG)]

    records: list[dict] = []

    # ---- Normal, unremarkable filings -----------------------------------
    for _ in range(n_normal):
        org_id, user_id = users[RNG.integers(0, len(users))]
        category = CATEGORIES[RNG.integers(0, len(CATEGORIES))]
        expense_date = _random_date(RNG)
        # Most people file within a few days of the spend.
        lag = int(abs(RNG.normal(loc=2.5, scale=2.5)))
        submitted_at = expense_date + pd.Timedelta(days=lag, hours=int(RNG.integers(0, 24)))
        records.append({
            "org_id": org_id,
            "user_id": user_id,
            "category": category,
            "amount": _sample_amount(category, RNG),
            "vendor": vendors[category][RNG.integers(0, VENDORS_PER_CATEGORY)],
            "expense_date": expense_date,
            "submitted_at": submitted_at,
            "description": f"{category.title()} expense",
            "is_anomaly": 0,
            "anomaly_type": "none",
        })

    # ---- Injected anomalies, split across the four archetypes ------------
    per_type = n_anomalies // 4

    # near_duplicate: resubmit an earlier filing with a small amount/date tweak
    for _ in range(per_type):
        org_id, user_id = users[RNG.integers(0, len(users))]
        category = CATEGORIES[RNG.integers(0, len(CATEGORIES))]
        base_date = _random_date(RNG)
        base_amount = _sample_amount(category, RNG)
        vendor = vendors[category][RNG.integers(0, VENDORS_PER_CATEGORY)]
        base_submitted = base_date + pd.Timedelta(days=1)
        # Original filing.
        records.append({
            "org_id": org_id, "user_id": user_id, "category": category,
            "amount": base_amount, "vendor": vendor,
            "expense_date": base_date, "submitted_at": base_submitted,
            "description": f"{category.title()} expense",
            "is_anomaly": 0, "anomaly_type": "none",
        })
        # Near-duplicate resubmission: amount nudged 1-4%, filed a few days
        # later, description reworded so a keyword/text match would not
        # catch it -- this is exactly the gap the resume claims the ML
        # scorer closes versus the old keyword filter.
        nudge = RNG.uniform(0.002, 0.015) * (1 if RNG.random() > 0.5 else -1)
        records.append({
            "org_id": org_id, "user_id": user_id, "category": category,
            "amount": round(base_amount * (1 + nudge), 2), "vendor": vendor,
            "expense_date": base_date + pd.Timedelta(days=int(RNG.integers(0, 4))),
            "submitted_at": base_submitted + pd.Timedelta(days=int(RNG.integers(2, 6))),
            "description": "Reimbursement request",
            "is_anomaly": 1, "anomaly_type": "near_duplicate",
        })

    # backdated: filed 45-120 days after the fact, amount on the high side
    for _ in range(per_type):
        org_id, user_id = users[RNG.integers(0, len(users))]
        category = CATEGORIES[RNG.integers(0, len(CATEGORIES))]
        expense_date = _random_date(RNG) - pd.Timedelta(days=90)
        lag = int(RNG.integers(60, 150))
        mean, std, floor = CATEGORY_PROFILES[category]
        amount = round(max(mean + std * RNG.uniform(2.0, 4.0), floor), 2)
        records.append({
            "org_id": org_id, "user_id": user_id, "category": category,
            "amount": amount,
            "vendor": vendors[category][RNG.integers(0, VENDORS_PER_CATEGORY)],
            "expense_date": expense_date,
            "submitted_at": expense_date + pd.Timedelta(days=lag),
            "description": "Late reimbursement",
            "is_anomaly": 1, "anomaly_type": "backdated",
        })

    # structuring: a burst of same-day filings just under a round threshold
    for _ in range(per_type // 3):
        org_id, user_id = users[RNG.integers(0, len(users))]
        day = _random_date(RNG)
        threshold = RNG.choice([100.0, 250.0, 500.0])
        burst_size = int(RNG.integers(3, 6))
        for _ in range(burst_size):
            category = CATEGORIES[RNG.integers(0, len(CATEGORIES))]
            amount = round(threshold - RNG.uniform(0.5, 8.0), 2)
            records.append({
                "org_id": org_id, "user_id": user_id, "category": category,
                "amount": amount,
                "vendor": vendors[category][RNG.integers(0, VENDORS_PER_CATEGORY)],
                "expense_date": day,
                "submitted_at": day + pd.Timedelta(hours=int(RNG.integers(0, 20))),
                "description": "Approval-threshold filing",
                "is_anomaly": 1, "anomaly_type": "structuring",
            })

    # amount_outlier: a category spend far outside the org's normal range
    for _ in range(per_type):
        org_id, user_id = users[RNG.integers(0, len(users))]
        category = CATEGORIES[RNG.integers(0, len(CATEGORIES))]
        mean, std, floor = CATEGORY_PROFILES[category]
        amount = round(mean + std * RNG.uniform(5.5, 9.0), 2)
        expense_date = _random_date(RNG)
        records.append({
            "org_id": org_id, "user_id": user_id, "category": category,
            "amount": amount,
            "vendor": vendors[category][RNG.integers(0, VENDORS_PER_CATEGORY)],
            "expense_date": expense_date,
            "submitted_at": expense_date + pd.Timedelta(days=int(RNG.integers(0, 3))),
            "description": "Unusual large expense",
            "is_anomaly": 1, "anomaly_type": "amount_outlier",
        })

    df = pd.DataFrame(records)
    df = df.sample(frac=1.0, random_state=7).reset_index(drop=True)  # shuffle
    return df


if __name__ == "__main__":
    dataset = generate()
    dataset.to_csv("data/expenses_raw.csv", index=False)
    print(f"Generated {len(dataset)} records, {dataset['is_anomaly'].mean() * 100:.1f}% labeled anomalous")
    print(dataset["anomaly_type"].value_counts())
