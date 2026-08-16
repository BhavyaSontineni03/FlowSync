"""
Expense anomaly-scoring microservice.

Exposes the trained scorer (Isolation Forest + reviewer calibrator) over
a small REST API. Given one submission plus a slice of the submitting
user's recent history, it returns an anomaly score. Approvals, payroll,
and workflow live in the Spring Boot backend, which calls this service
over gRPC in production (see `app/grpc_server.py`). This REST surface is
for local development, health checks, and the load-test harness.
"""

from __future__ import annotations

import logging
import time

from contextlib import asynccontextmanager

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware

from app.schemas import ScoreRequest, ScoreResponse
from app.scoring import AnomalyScorer

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("anomaly-scoring")

_scorer: AnomalyScorer | None = None


def load_model() -> None:
    """Loads the model into the module-level singleton. Called from the
    lifespan hook at process startup, and directly by tests that need a
    warm model without spinning up the ASGI lifespan machinery."""
    global _scorer
    start = time.perf_counter()
    _scorer = AnomalyScorer()
    logger.info("Model loaded in %.1fms", (time.perf_counter() - start) * 1000)


@asynccontextmanager
async def lifespan(_: FastAPI):
    load_model()
    yield


app = FastAPI(
    title="Expense Anomaly Scoring Service",
    description="Semi-supervised real-time anomaly scoring for expense submissions.",
    version="2.0.0",
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # internal service-to-service traffic only
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.get("/health")
def health() -> dict:
    return {"status": "ok", "model_loaded": _scorer is not None}


@app.post("/score", response_model=ScoreResponse)
def score(req: ScoreRequest) -> ScoreResponse:
    if _scorer is None:
        raise HTTPException(status_code=503, detail="Model not loaded yet")
    try:
        return _scorer.score(req)
    except Exception as exc:  # noqa: BLE001 - surfaced as a clean 500 for the caller's circuit breaker
        logger.exception("Scoring failed")
        raise HTTPException(status_code=500, detail=str(exc)) from exc
