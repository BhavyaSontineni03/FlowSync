"""Pydantic request/response contracts for the scoring API.

The service is intentionally stateless: it does not own a database
connection of its own. The caller (the Spring Boot backend, which already
holds the source-of-truth data) supplies the submission plus a short window
of the same user's recent submissions. This keeps the scoring service
horizontally scalable and easy to reason about -- it has no persistence
layer to keep consistent with the main platform's Postgres instance.
"""

from __future__ import annotations

from datetime import date, datetime
from typing import Optional

from pydantic import BaseModel, Field


class RecentSubmission(BaseModel):
    amount: float
    category: str
    vendor: str
    expense_date: date
    submitted_at: datetime


class ScoreRequest(BaseModel):
    org_id: str
    user_id: str
    category: str
    amount: float = Field(gt=0)
    vendor: str
    expense_date: date
    submitted_at: datetime
    # Same user's submissions from roughly the last 14 days, oldest first.
    # Used for the duplicate-similarity and submission-velocity features.
    recent_submissions: list[RecentSubmission] = Field(default_factory=list)
    # Same user's prior amounts in this category (any window the caller
    # deems reasonable, e.g. trailing 12 months). Used for the personalized
    # z-score feature; falls back to the org-wide reference when sparse.
    user_category_history: list[float] = Field(default_factory=list)


class ScoreResponse(BaseModel):
    anomaly_score: float = Field(description="Higher = more anomalous. Roughly in [-0.1, 0.7] in practice.")
    is_anomalous: bool
    percentile_in_reference: float = Field(description="Where this score falls versus the training distribution, 0-100.")
    features: dict[str, float]
    model_version: str
