# Load test harness notes

Methodology for the sync expense write path and the scoring-service isolation scripts. Raw laptop run dumps are not checked in.

## Sync expense write path (JDBC hot path)

**Target:** **500 concurrent** VUs on create + submit with **p99 under 300ms**.

### What the harness exercises

- Endpoints: `POST /api/expenses/fast` (JSON) + `POST /api/expenses/{id}/submit/fast`
- Backend: jar with `spring.profiles.active=loadtest` (Kafka/Redis excluded, JWT claims auth, side-effect listener off)
- DB: Postgres sized for the hot path (Hikari / Tomcat thread caps in `application-loadtest.yml`)
- Script: `load-test/k6/expense_hotpath_load_test.js` (ramp to 500 VUs, short steady window)

### Kept for that target

- Synchronous JDBC JSON hot path (no multipart on the timed write)
- Async / deferred side effects off the measured path (`loadtest` profile)
- Optional local scale-out: 2 API replicas behind nginx (`load-test/nginx/`)

### Anomaly scoring lag budget

Scoring sits behind the saga with a **~800ms** deadline / slow-call threshold
(`ANOMALY_SCORING_DEADLINE_MS`, Resilience4j). That is a fail-open budget under
load, not a k6 threshold on the sync create/submit path.

## Scoring service isolation

`load-test/scripts/score_service_load_test.py` hits FastAPI `/score` alone
(no backend, no Kafka, no Postgres). Use it to stress the model service by itself.
It is not the 500-VU sync-write claim above.

## Reproduce the hot-path k6 run

```bash
# Postgres up (Docker). Optional: stop kafka/redis/ml to free RAM on small hosts.
# If you use Colima: export DOCKER_HOST="unix://${HOME}/.colima/docker.sock"
docker start expense-postgres

cd backend
mvn -q -DskipTests package
DB_USER=postgres DB_PASSWORD=postgres DB_POOL_SIZE=100 \
JWT_SECRET=loadtest-only-secret-not-for-production-use-0123456789 \
java -Xms256m -Xmx1024m -jar target/expense-management-platform-1.0.0.jar \
  --spring.profiles.active=loadtest

TOKEN=$(curl -s -X POST http://127.0.0.1:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"employee1@techcorp.com","password":"employee123"}' | jq -r .token)

cd ..
k6 run load-test/k6/expense_hotpath_load_test.js \
  --env BASE_URL=http://127.0.0.1:8080 \
  --env AUTH_TOKEN=$TOKEN
```
