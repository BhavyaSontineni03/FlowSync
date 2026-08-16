#!/bin/sh
set -e
python -m app.grpc_server &
GRPC_PID=$!
python -m uvicorn app.main:app --host 0.0.0.0 --port 8000 &
REST_PID=$!
trap "kill $GRPC_PID $REST_PID" TERM INT
wait $GRPC_PID $REST_PID
