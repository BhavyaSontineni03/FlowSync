/**
 * Load test for the expense-submission path: login once, then ramp
 * concurrent virtual users each creating and submitting an expense.
 *
 * What this measures: the synchronous HTTP request/response latency of
 * POST /api/expenses and POST /api/expenses/{id}/submit. By design, that
 * path is fast and roughly constant under load -- submitting an expense
 * only writes the expense row and an outbox event row in the same
 * transaction; the anomaly-scoring call, Kafka publish, and saga execution
 * all happen asynchronously afterward and do NOT block this response. That
 * decoupling is the point of the outbox/event-driven design (see
 * ARCHITECTURE.md) -- a slow or backed-up scoring service should degrade
 * consumer lag, not the submission API's response time.
 *
 * To see the *other* half of the story -- how far end-to-end processing
 * (submit -> scored -> ready for approval) falls behind under load once
 * the Kafka consumer can't keep pace with the producer -- watch consumer
 * group lag directly (kafka-consumer-groups.sh --describe --group
 * expense-saga-orchestrator, or the equivalent metric in whatever
 * monitoring is wired up) while this script runs, rather than relying on
 * this script's own numbers, which only ever sees the fast/decoupled path.
 *
 * Run: k6 run expense_submission_load_test.js \
 *        --env BASE_URL=http://localhost:8080 \
 *        --env AUTH_TOKEN=<jwt>
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const AUTH_TOKEN = __ENV.AUTH_TOKEN || '';

const submitLatency = new Trend('expense_submit_latency', true);
const createLatency = new Trend('expense_create_latency', true);

export const options = {
  scenarios: {
    ramping_submissions: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 100 },
        { duration: '30s', target: 300 },
        { duration: '30s', target: 500 },  // operating point claimed on the resume
        { duration: '30s', target: 800 },  // where consumer lag is expected to climb
        { duration: '30s', target: 0 },
      ],
    },
  },
  thresholds: {
    // Synchronous HTTP path only -- see header comment.
    http_req_duration: ['p(95)<250', 'p(99)<300'],
    http_req_failed: ['rate<0.01'],
    expense_submit_latency: ['p(99)<300'],
  },
};

const CATEGORIES = ['TRAVEL', 'MEALS', 'ACCOMMODATION', 'TRANSPORTATION', 'OFFICE_SUPPLIES', 'SOFTWARE'];

export default function () {
  const headers = {
    Authorization: `Bearer ${AUTH_TOKEN}`,
  };

  const category = CATEGORIES[Math.floor(Math.random() * CATEGORIES.length)];
  const amount = (Math.random() * 300 + 10).toFixed(2);

  // The create endpoint accepts multipart/form-data with an `expense`
  // JSON part (see ExpenseController.createExpense), not a raw JSON body.
  const payload = JSON.stringify({
    description: `Load-test expense ${__VU}-${__ITER}`,
    amount: Number(amount),
    category,
    expenseDate: new Date().toISOString().slice(0, 10),
  });

  const formData = {
    expense: http.file(payload, 'expense.json', 'application/json'),
  };

  const createRes = http.post(`${BASE_URL}/api/expenses`, formData, { headers });
  createLatency.add(createRes.timings.duration);

  const created = check(createRes, {
    'expense created': (r) => r.status === 200 || r.status === 201,
  });

  if (created) {
    const expenseId = createRes.json('id');
    const submitRes = http.post(`${BASE_URL}/api/expenses/${expenseId}/submit`, null, { headers });
    submitLatency.add(submitRes.timings.duration);
    check(submitRes, { 'expense submitted': (r) => r.status === 200 });
  }

  sleep(Math.random() * 1.5);
}
