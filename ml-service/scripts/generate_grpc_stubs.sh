#!/bin/bash
# Regenerates the Python gRPC stubs from proto/anomaly_scoring.proto.
# Run this after changing the .proto contract, then commit the .proto change
# only -- the generated app/generated/*.py files are gitignored.
set -e
cd "$(dirname "$0")/.."
python3 -m grpc_tools.protoc \
  -I../proto \
  --python_out=app/generated \
  --grpc_python_out=app/generated \
  ../proto/anomaly_scoring.proto
sed -i.bak 's/^import anomaly_scoring_pb2 as anomaly__scoring__pb2/from . import anomaly_scoring_pb2 as anomaly__scoring__pb2/' app/generated/anomaly_scoring_pb2_grpc.py
rm -f app/generated/anomaly_scoring_pb2_grpc.py.bak
echo "gRPC stubs regenerated in app/generated/"
