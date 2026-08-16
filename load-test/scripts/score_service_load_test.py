"""
Small, dependency-light concurrent load generator for the anomaly-scoring
service's /score endpoint. Written by hand (asyncio + httpx) rather than
pulling in a full load-testing framework, since the point here is a
reproducible, inspectable measurement of exactly one thing: how the model
inference + feature computation path holds up under concurrency.

Usage:
    python3 score_service_load_test.py --url http://localhost:8000/score \
        --concurrency 500 --requests 5000
"""
from __future__ import annotations

import argparse
import asyncio
import json
import statistics
import time
from datetime import date, datetime, timedelta
from random import Random

import httpx

CATEGORIES = ["TRAVEL", "MEALS", "ACCOMMODATION", "TRANSPORTATION", "OFFICE_SUPPLIES", "SOFTWARE"]


def build_payload(rng: Random) -> dict:
    category = rng.choice(CATEGORIES)
    expense_date = date.today() - timedelta(days=rng.randint(0, 10))
    return {
        "org_id": f"org-{rng.randint(1, 5)}",
        "user_id": f"org-{rng.randint(1, 5)}-user-{rng.randint(1, 40)}",
        "category": category,
        "amount": round(rng.uniform(10, 500), 2),
        "vendor": f"{category.title()} Vendor {rng.randint(1, 12)}",
        "expense_date": expense_date.isoformat(),
        "submitted_at": datetime.now().isoformat(),
        "recent_submissions": [],
        "user_category_history": [round(rng.uniform(20, 200), 2) for _ in range(rng.randint(0, 8))],
    }


async def worker(client: httpx.AsyncClient, url: str, rng: Random, n: int, latencies: list[float], errors: list[str]) -> None:
    for _ in range(n):
        payload = build_payload(rng)
        start = time.perf_counter()
        try:
            resp = await client.post(url, json=payload, timeout=5.0)
            resp.raise_for_status()
            latencies.append((time.perf_counter() - start) * 1000)
        except Exception as exc:  # noqa: BLE001
            errors.append(str(exc))


async def run(url: str, concurrency: int, total_requests: int) -> dict:
    per_worker = max(1, total_requests // concurrency)
    latencies: list[float] = []
    errors: list[str] = []

    limits = httpx.Limits(max_connections=concurrency, max_keepalive_connections=concurrency)
    async with httpx.AsyncClient(limits=limits, trust_env=False) as client:
        start = time.perf_counter()
        tasks = [
            worker(client, url, Random(i), per_worker, latencies, errors)
            for i in range(concurrency)
        ]
        await asyncio.gather(*tasks)
        wall_time = time.perf_counter() - start

    latencies.sort()

    def pct(p: float) -> float:
        if not latencies:
            return 0.0
        idx = min(len(latencies) - 1, int(len(latencies) * p))
        return latencies[idx]

    return {
        "concurrency": concurrency,
        "requests_attempted": concurrency * per_worker,
        "requests_succeeded": len(latencies),
        "requests_failed": len(errors),
        "wall_time_seconds": round(wall_time, 3),
        "throughput_rps": round(len(latencies) / wall_time, 1) if wall_time > 0 else 0,
        "latency_ms": {
            "min": round(min(latencies), 3) if latencies else None,
            "p50": round(pct(0.50), 3),
            "p95": round(pct(0.95), 3),
            "p99": round(pct(0.99), 3),
            "max": round(max(latencies), 3) if latencies else None,
            "mean": round(statistics.mean(latencies), 3) if latencies else None,
        },
        "sample_errors": errors[:5],
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--url", default="http://localhost:8000/score")
    parser.add_argument("--concurrency", type=int, default=500)
    parser.add_argument("--requests", type=int, default=5000)
    args = parser.parse_args()

    result = asyncio.run(run(args.url, args.concurrency, args.requests))
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
