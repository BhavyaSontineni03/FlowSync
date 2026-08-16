"""
gRPC server for the anomaly-scoring service. This is the transport the
Spring Boot backend actually talks to in production (see
backend/src/main/java/com/expensemanagement/grpc/GrpcAnomalyScoringClient.java);
the REST API in app/main.py exists alongside it for local curl-testing, the
load-testing harness, and the health check the container orchestrator polls.

Run: python3 -m app.grpc_server
"""

from __future__ import annotations

import logging
from concurrent import futures
from datetime import date, datetime

import grpc

from app.generated import anomaly_scoring_pb2 as pb2
from app.generated import anomaly_scoring_pb2_grpc as pb2_grpc
from app.schemas import RecentSubmission, ScoreRequest
from app.scoring import AnomalyScorer

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("anomaly-scoring-grpc")


class AnomalyScoringServicer(pb2_grpc.AnomalyScoringServiceServicer):
    def __init__(self) -> None:
        self.scorer = AnomalyScorer()

    def ScoreExpense(self, request, context):
        try:
            score_request = ScoreRequest(
                org_id=request.org_id,
                user_id=request.user_id,
                category=request.category,
                amount=request.amount,
                vendor=request.vendor,
                expense_date=date.fromisoformat(request.expense_date),
                submitted_at=datetime.fromisoformat(request.submitted_at),
                recent_submissions=[
                    RecentSubmission(
                        amount=r.amount,
                        category=r.category,
                        vendor=r.vendor,
                        expense_date=date.fromisoformat(r.expense_date),
                        submitted_at=datetime.fromisoformat(r.submitted_at),
                    )
                    for r in request.recent_submissions
                ],
                user_category_history=list(request.user_category_history),
            )
            result = self.scorer.score(score_request)
            return pb2.ScoreExpenseResponse(
                anomaly_score=result.anomaly_score,
                is_anomalous=result.is_anomalous,
                percentile_in_reference=result.percentile_in_reference,
                features=result.features,
                model_version=result.model_version,
            )
        except Exception as exc:  # noqa: BLE001
            logger.exception("gRPC ScoreExpense failed")
            context.set_code(grpc.StatusCode.INTERNAL)
            context.set_details(str(exc))
            return pb2.ScoreExpenseResponse()


def serve(port: int = 50051) -> None:
    server = grpc.server(futures.ThreadPoolExecutor(max_workers=10))
    pb2_grpc.add_AnomalyScoringServiceServicer_to_server(AnomalyScoringServicer(), server)
    server.add_insecure_port(f"[::]:{port}")
    server.start()
    logger.info("gRPC anomaly-scoring server listening on :%d", port)
    server.wait_for_termination()


if __name__ == "__main__":
    serve()
