# Anomaly Scoring Service

Python service that scores expense submissions. Two stages:

1. Isolation Forest on six engineered features (candidate generation).
2. HistGradientBoosting calibrator trained on a simulated reviewer-label set (top ~20% IF-flagged rows plus a small random audit).

A keyword or exact-amount duplicate filter misses resubmissions where the amount is nudged and the description is reworded. See `REPORT.md` for evaluation on a held-out chronological split.

## Features

Computed from the submitting user's own recent history (not other users):

1. `amount_zscore` - how unusual the amount is for this user in this category (falls back to org-wide stats for new users)
2. `category_percentile` - amount vs the org's spend in that category
3. `submission_lag_days` - gap between expense date and filing date
4. `weekend_flag` - whether the expense landed on a weekend
5. `duplicate_similarity` - fuzzy match (amount + date + vendor + category) against the user's recent filings
6. `submission_velocity_24h` - how many expenses this user filed in the last 24 hours

The calibrator also takes the Isolation Forest score as a seventh input.

## Run

```bash
python3.12 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt

python3 data/generate_dataset.py
python3 train.py

uvicorn app.main:app --reload --port 8000       # REST, local dev
python3 -m app.grpc_server                       # gRPC, what the backend calls

pytest
```

## Why this mix of unsupervised + labels

Confirmed fraud labels arrive late. Pure supervised training on the full history overstates what production would have. Pure Isolation Forest on this dataset is about 68% precision at a 12% flag rate (`pure_if_*` in `models/metrics.json`).

The IF proposes a review queue, reviewers label that queue plus a small audit, and the calibrator learns from those labels. About 20% of train+val rows get labels in the simulation. Headline numbers in `REPORT.md` are measured once on a chronological held-out test split.
