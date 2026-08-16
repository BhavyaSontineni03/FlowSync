# Option A: 2 API replicas behind nginx

Local scale-out for the JDBC hot path (`/api/expenses/fast` + `/submit/fast`).

## Topology

```
k6  ->  nginx :8080 (least_conn)  ->  java :8081  -+
                                  ->  java :8082  -+-> Docker Postgres :5432
```

## Config

- `load-test/nginx/nginx-hotpath-lb.conf` - standalone nginx conf (prefix = this directory)
- Upstream: `127.0.0.1:8081`, `127.0.0.1:8082` with `least_conn`

## Start

```bash
# If you use Colima:
# export DOCKER_HOST="unix://${HOME}/.colima/docker.sock"
docker start expense-postgres

# Replicas (from backend/)
JWT_SECRET=loadtest-only-secret-not-for-production-use-0123456789
DB_USER=postgres DB_PASSWORD=postgres DB_POOL_SIZE=45 TOMCAT_MAX_THREADS=300 \
  java -Xms128m -Xmx512m -jar target/expense-management-platform-1.0.0.jar \
  --spring.profiles.active=loadtest --server.port=8081 &
# same on --server.port=8082

# LB (from repo root)
nginx -c "$(pwd)/load-test/nginx/nginx-hotpath-lb.conf" -p "$(pwd)/load-test/nginx"

# Stop LB
nginx -c "$(pwd)/load-test/nginx/nginx-hotpath-lb.conf" -p "$(pwd)/load-test/nginx" -s stop
```

Hikari is split (~45 each) so total pool stays under Postgres `max_connections` (200). Small heaps fit an 8GB laptop.

## Load test

```bash
TOKEN=$(curl -s -X POST http://127.0.0.1:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"employee1@techcorp.com","password":"employee123"}' | jq -r .token)

k6 run load-test/k6/expense_hotpath_load_test.js \
  --env BASE_URL=http://127.0.0.1:8080 \
  --env AUTH_TOKEN=$TOKEN
```

Harness notes and targets: `load-test/results/README.md`.
