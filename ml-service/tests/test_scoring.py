"""Sanity tests for the scoring service. Run with: pytest"""
from datetime import date, datetime, timedelta

from fastapi.testclient import TestClient

from app.main import app, load_model

client = TestClient(app)


def setup_module() -> None:
    load_model()


def test_health() -> None:
    resp = client.get("/health")
    assert resp.status_code == 200
    assert resp.json()["model_loaded"] is True


def test_score_normal_submission() -> None:
    resp = client.post("/score", json={
        "org_id": "org-1",
        "user_id": "org-1-user-1",
        "category": "MEALS",
        "amount": 32.50,
        "vendor": "Meals Vendor 1",
        "expense_date": str(date.today() - timedelta(days=1)),
        "submitted_at": datetime.now().isoformat(),
        "recent_submissions": [],
        "user_category_history": [30.0, 35.0, 28.0, 40.0, 33.0],
    })
    assert resp.status_code == 200
    body = resp.json()
    assert "anomaly_score" in body
    assert isinstance(body["is_anomalous"], bool)
    assert set(body["features"].keys()) == {
        "amount_zscore", "category_percentile", "submission_lag_days",
        "weekend_flag", "duplicate_similarity", "submission_velocity_24h",
    }


def test_score_near_duplicate_flagged_higher_than_normal() -> None:
    base_time = datetime.now()
    original = {
        "amount": 500.0, "category": "TRAVEL", "vendor": "Travel Vendor 1",
        "expense_date": str(date.today() - timedelta(days=5)),
        "submitted_at": (base_time - timedelta(days=4)).isoformat(),
    }
    duplicate_request = {
        "org_id": "org-2",
        "user_id": "org-2-user-5",
        "category": "TRAVEL",
        "amount": 502.0,  # near-identical amount
        "vendor": "Travel Vendor 1",  # same vendor
        "expense_date": str(date.today() - timedelta(days=3)),
        "submitted_at": base_time.isoformat(),
        "recent_submissions": [original],
        "user_category_history": [420.0, 410.0, 440.0],
    }
    normal_request = dict(duplicate_request, recent_submissions=[], amount=445.0)

    dup_score = client.post("/score", json=duplicate_request).json()["anomaly_score"]
    normal_score = client.post("/score", json=normal_request).json()["anomaly_score"]

    assert dup_score > normal_score


def test_rejects_non_positive_amount() -> None:
    resp = client.post("/score", json={
        "org_id": "org-1", "user_id": "u1", "category": "MEALS", "amount": -5,
        "vendor": "V", "expense_date": str(date.today()), "submitted_at": datetime.now().isoformat(),
    })
    assert resp.status_code == 422
