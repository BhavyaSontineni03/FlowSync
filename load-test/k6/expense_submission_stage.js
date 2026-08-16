/**
 * Stage-isolated load test: hold a fixed VU count long enough to get a
 * clean p99 reading, then move to the next stage. Reports per-stage
 * metrics via tags so results can be sliced without a continuous ramp
 * that mixes 100-VU and 800-VU samples into one percentile.
 *
 * Usage:
 *   k6 run --env BASE_URL=http://127.0.0.1:8080 --env AUTH_TOKEN=... \
 *     --env STAGE=500 \
 *     expense_submission_stage.js
 *
 * STAGE may be 100, 300, 500, or 800.
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const AUTH_TOKEN = __ENV.AUTH_TOKEN || '';
const STAGE = Number(__ENV.STAGE || '500');
const DURATION = __ENV.DURATION || '45s';

const submitLatency = new Trend('expense_submit_latency', true);
const createLatency = new Trend('expense_create_latency', true);

export const options = {
  scenarios: {
    fixed: {
      executor: 'constant-vus',
      vus: STAGE,
      duration: DURATION,
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.05'],
  },
};

const CATEGORIES = ['TRAVEL', 'MEALS', 'ACCOMMODATION', 'TRANSPORTATION', 'OFFICE_SUPPLIES', 'SOFTWARE'];

export default function () {
  const headers = { Authorization: `Bearer ${AUTH_TOKEN}` };
  const category = CATEGORIES[Math.floor(Math.random() * CATEGORIES.length)];
  const amount = (Math.random() * 300 + 10).toFixed(2);
  const payload = JSON.stringify({
    description: `Load-test expense ${__VU}-${__ITER}`,
    amount: Number(amount),
    category,
    expenseDate: new Date().toISOString().slice(0, 10),
  });

  const createRes = http.post(
    `${BASE_URL}/api/expenses`,
    { expense: http.file(payload, 'expense.json', 'application/json') },
    { headers, tags: { stage: String(STAGE), name: 'create' } },
  );
  createLatency.add(createRes.timings.duration);

  const ok = check(createRes, {
    'expense created': (r) => r.status === 200 || r.status === 201,
  });

  if (ok) {
    const expenseId = createRes.json('id');
    const submitRes = http.post(
      `${BASE_URL}/api/expenses/${expenseId}/submit`,
      null,
      { headers, tags: { stage: String(STAGE), name: 'submit' } },
    );
    submitLatency.add(submitRes.timings.duration);
    check(submitRes, { 'expense submitted': (r) => r.status === 200 });
  }

  sleep(0.2 + Math.random() * 0.3);
}
