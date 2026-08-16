#!/bin/bash
set -euo pipefail

echo "Setting up FlowSync (expense management platform)..."

if command -v docker >/dev/null 2>&1; then
  echo "Docker found. Preferred path is:"
  echo "  docker compose up -d --build"
  echo ""
fi

missing=0
if ! command -v java >/dev/null 2>&1; then
  echo "Java is not installed. Install Java 17 or higher for a local backend."
  missing=1
fi
if ! command -v mvn >/dev/null 2>&1; then
  echo "Maven is not installed. Install Maven for a local backend."
  missing=1
fi
if ! command -v node >/dev/null 2>&1; then
  echo "Node.js is not installed. Install Node.js 18 or higher for a local frontend."
  missing=1
fi

if [ "$missing" -eq 1 ]; then
  echo "Install the missing tools, or use Docker Compose instead."
  exit 1
fi

echo "Building backend..."
(cd backend && mvn clean install -DskipTests)

echo "Installing frontend dependencies..."
(cd frontend && npm install)

echo "Setup complete."
echo ""
echo "Full stack (recommended):"
echo "  docker compose up -d --build"
echo ""
echo "Or run pieces locally:"
echo "  1. docker compose up -d postgres redis kafka"
echo "  2. cd backend && DB_USER=postgres DB_PASSWORD=postgres mvn spring-boot:run"
echo "  3. cd ml-service && python3 -m venv .venv && source .venv/bin/activate && pip install -r requirements.txt && python3 data/generate_dataset.py && python3 train.py && python3 -m app.grpc_server"
echo "  4. cd frontend && npm run dev"
echo ""
echo "Copy .env.example to .env to override defaults."
