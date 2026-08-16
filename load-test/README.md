# Load Testing

1. **`k6/expense_hotpath_load_test.js`** - JDBC JSON hot path
   (`POST /api/expenses/fast` + `POST /api/expenses/{id}/submit/fast`) at
   **500 concurrent** VUs. Target: **p99 under 300ms**. See `results/README.md`
   for harness notes and the ~800ms scoring lag budget (saga path).

2. **`k6/expense_submission_load_test.js`** - older multipart harness against
   `/api/expenses` (UI path). Prefer the hotpath script for the sync-write target.

3. **`scripts/score_service_load_test.py`** - isolated anomaly-scoring service
   load generator (no backend).

4. **`nginx/`** - optional 2-replica LB config for local scale-out of the hot path.

## Running the hot-path k6 test

```bash
# Backend with loadtest profile + JWT_SECRET (Postgres required; Kafka optional)
cd backend
DB_USER=postgres DB_PASSWORD=postgres JWT_SECRET=... \
  java -jar target/expense-management-platform-1.0.0.jar --spring.profiles.active=loadtest

TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"employee1@techcorp.com","password":"employee123"}' | jq -r .token)

k6 run load-test/k6/expense_hotpath_load_test.js \
  --env BASE_URL=http://localhost:8080 \
  --env AUTH_TOKEN=$TOKEN
```

## Running the scoring-service load test

```bash
cd ml-service
python3 -m venv .venv && source .venv/bin/activate && pip install -r requirements.txt
python3 data/generate_dataset.py && python3 train.py
uvicorn app.main:app --port 8000 &

cd ../load-test
python3 scripts/score_service_load_test.py --url http://localhost:8000/score --concurrency 500 --requests 5000
```
