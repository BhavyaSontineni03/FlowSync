"""
The naive baseline this project replaces: a keyword/exact-match duplicate
filter, the kind of rule commonly bolted onto an expense system first ("flag
it if the description contains 'duplicate'" or "flag it if the amount is
byte-for-byte identical to a prior filing"). Kept here, and measured against
the same dataset as the Isolation Forest, so the improvement claim is a real
comparison rather than an assertion.
"""

from __future__ import annotations

import pandas as pd

KEYWORDS = ("duplicate", "resubmit", "resend", "again")


def flag_duplicates(df: pd.DataFrame, lookback_days: int = 14) -> pd.Series:
    """Flags a row if either:
      (a) its description contains an explicit duplicate-ish keyword, or
      (b) the exact same user filed the exact same amount in the exact same
          category within the lookback window.
    This is what a first-pass rule-based filter looks like in practice --
    and why it misses paraphrased or slightly-adjusted resubmissions.
    """
    df = df.sort_values("submitted_at").reset_index(drop=True)
    flags = [False] * len(df)
    seen: dict[tuple, list[pd.Timestamp]] = {}

    for i, row in df.iterrows():
        text_hit = any(k in str(row["description"]).lower() for k in KEYWORDS)

        key = (row["user_id"], row["category"], row["amount"])
        prior_times = seen.setdefault(key, [])
        exact_hit = any(
            (row["submitted_at"] - t).days <= lookback_days for t in prior_times
        )
        prior_times.append(row["submitted_at"])

        flags[i] = text_hit or exact_hit

    result = pd.Series(flags, index=df.index)
    return result.reindex(df.index)
