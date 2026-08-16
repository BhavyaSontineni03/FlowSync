# Multi-stage build so the runtime image doesn't carry pip's build cache.
FROM python:3.11-slim AS builder
WORKDIR /build
COPY requirements.txt .
RUN pip install --no-cache-dir --target=/build/deps -r requirements.txt

FROM python:3.11-slim
WORKDIR /app
COPY --from=builder /build/deps /usr/local/lib/python3.11/site-packages
COPY app/ app/
COPY models/ models/
ENV PYTHONUNBUFFERED=1
EXPOSE 8000 50051

# The container runs both the REST surface (health checks, dev/testing) and
# the gRPC server (the real production traffic path) as two processes
# supervised by this tiny entrypoint script.
COPY docker-entrypoint.sh .
RUN chmod +x docker-entrypoint.sh
ENTRYPOINT ["./docker-entrypoint.sh"]
