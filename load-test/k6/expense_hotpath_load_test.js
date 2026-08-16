/**
 * Load test for the JDBC hot-path expense create + submit endpoints.
 *
 * Measures POST /api/expenses/fast (JSON) and POST /api/expenses/{id}/submit/fast
 * only, not multipart /api/expenses. Saga / Kafka / ML stay off this timed path.
 *
 * Run against a backend started with --spring.profiles.active=loadtest and JWT_SECRET set:
 *
 *   TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
 *     -H 'Content-Type: application/json' \
 *     -d '{"email":"employee1@techcorp.com","password":"employee123"}' | jq -r .token)
 *
 *   k6 run load-test/k6/expense_hotpath_load_test.js \
 *     --env BASE_URL=http://localhost:8080 \
 *     --env AUTH_TOKEN=$TOKEN \
 *     --summary-export=load-test/results/k6_hotpath_summary.json
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Rate } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const AUTH_TOKEN = __ENV.AUTH_TOKEN || '';

const createLatency = new Trend('expense_create_latency', true);
const submitLatency = new Trend('expense_submit_latency', true);
const createSubmitLatency = new Trend('expense_create_submit_latency', true);
const errorRate = new Rate('expense_hotpath_errors');

export const options = {
  scenarios: {
    hotpath_500: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '10s', target: 100 },
        { duration: '10s', target: 300 },
        { duration: '5s', target: 500 },
        { duration: '30s', target: 500 }, // steady operating point
        { duration: '10s', target: 0 },
      ],
    },
  },
  thresholds: {
    http_req_duration: ['p(99)<300'],
    http_req_failed: ['rate<0.01'],
    expense_create_latency: ['p(99)<300'],
    expense_submit_latency: ['p(99)<300'],
    expense_create_submit_latency: ['p(99)<300'],
    expense_hotpath_errors: ['rate<0.01'],
  },
};

const CATEGORIES = ['TRAVEL', 'MEALS', 'ACCOMMODATION', 'TRANSPORTATION', 'OFFICE_SUPPLIES', 'SOFTWARE'];

export default function () {
  if (!AUTH_TOKEN) {
    errorRate.add(1);
    throw new Error('AUTH_TOKEN env var is required');
  }

  const headers = {
    Authorization: `Bearer ${AUTH_TOKEN}`,
    'Content-Type': 'application/json',
  };

  const category = CATEGORIES[Math.floor(Math.random() * CATEGORIES.length)];
  const amount = (Math.random() * 300 + 10).toFixed(2);
  const payload = JSON.stringify({
    description: `Hotpath load-test ${__VU}-${__ITER}`,
    amount: Number(amount),
    category,
    expenseDate: new Date().toISOString().slice(0, 10),
  });

  const t0 = Date.now();
  const createRes = http.post(`${BASE_URL}/api/expenses/fast`, payload, { headers });
  createLatency.add(createRes.timings.duration);

  const created = check(createRes, {
    'fast create 201': (r) => r.status === 201,
  });
  if (!created) {
    errorRate.add(1);
    sleep(0.2);
    return;
  }
  errorRate.add(0);

  const expenseId = createRes.json('id');
  const submitRes = http.post(`${BASE_URL}/api/expenses/${expenseId}/submit/fast`, null, { headers });
  submitLatency.add(submitRes.timings.duration);
  createSubmitLatency.add(Date.now() - t0);

  const submitted = check(submitRes, {
    'fast submit 200': (r) => r.status === 200,
  });
  errorRate.add(submitted ? 0 : 1);

  // Tiny think time so VUs stay concurrent without pure spin
  sleep(0.05 + Math.random() * 0.1);
}
