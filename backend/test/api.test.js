import test from 'node:test';
import assert from 'node:assert/strict';
import { once } from 'node:events';
import { createAI } from '../src/ai.js';
import { createServer } from '../src/server.js';
import { validateProfile, validateImage, validateAnalysis, validatePlanRequest, validatePlan } from '../src/domain.js';

const food = { name: 'Cơm gà', grams: 300, calories: 500, protein: 30, carbs: 65, fat: 15 };
const profile = { age: 25, height: 165, weight: 60, dailyCalories: 2000, goal: 'Duy trì', allergies: 'Tôm' };
const request = { profile, days: 2, startDate: '2026-12-31' };
const image = { mimeType: 'image/png', base64: Buffer.from([137, 80, 78, 71, 13, 10, 26, 10, 0]).toString('base64') };
const config = { baseUrl: 'http://localhost:9999/v1', apiKey: 'secret-test', model: 'test-model' };
const success = data => ({ ok: true, json: async () => ({ choices: [{ finish_reason: 'stop', message: { content: JSON.stringify(data) } }] }) });

test('profile validates adult scope, finite numbers, and limits fields', () => {
  assert.equal(validateProfile(profile).preferences, '');
  for (const bad of [{ ...profile, age: 17 }, { ...profile, weight: NaN }, { ...profile, dailyCalories: 900 }, { ...profile, allergies: 'x'.repeat(501) }]) {
    assert.throws(() => validateProfile(bad));
  }
});
test('image validates base64, MIME and file signature before calling upstream', () => {
  assert.equal(validateImage(image).mimeType, 'image/png');
  assert.throws(() => validateImage({ ...image, mimeType: 'image/jpeg' }));
  assert.throws(() => validateImage({ ...image, base64: 'not base64!' }));
  assert.throws(() => validateImage({ mimeType: 'text/html', base64: 'aGVsbG8=' }));
});
test('analysis computes its own totals rather than trusting AI totals', () => {
  const result = validateAnalysis({ isFood: true, items: [food, { ...food, calories: 250 }], totals: { calories: 99 } });
  assert.equal(result.totals.calories, 750);
  assert.equal(result.source, 'ai_estimate');
});
test('ambiguous or non-food image may return zero items, but food cannot', () => {
  assert.equal(validateAnalysis({ isFood: false, items: [], notes: 'Ảnh quá mờ' }).items.length, 0);
  assert.throws(() => validateAnalysis({ isFood: true, items: [] }));
  assert.throws(() => validateAnalysis({ isFood: false, items: [food] }));
});
test('rejects malformed, negative, or implausibly high AI nutrition', () => {
  for (const bad of [{ ...food, grams: -1 }, { ...food, calories: 10001 }, { ...food, name: '' }, { ...food, protein: '30' }]) {
    assert.throws(() => validateAnalysis({ isFood: true, items: [bad] }));
  }
});
test('planner rejects invalid dates, fractional days and oversized batches', () => {
  assert.equal(validatePlanRequest(request).days, 2);
  for (const bad of [{ ...request, startDate: '2026-02-30' }, { ...request, days: 1.5 }, { ...request, days: 30 }]) {
    assert.throws(() => validatePlanRequest(bad));
  }
});
test('planner assigns dates across year boundary and canonical meal slots', () => {
  const data = { days: [0, 1].map(() => ({ date: 'wrong', meals: Array.from({ length: 4 }, () => food) })) };
  const plan = validatePlan(data, validatePlanRequest(request));
  assert.deepEqual(plan.days.map(d => d.date), ['2026-12-31', '2027-01-01']);
  assert.deepEqual(plan.days[0].meals.map(m => m.slot), ['Sáng', 'Trưa', 'Tối', 'Ăn nhẹ']);
});
test('planner rejects incomplete days and calories outside allowed tolerance', () => {
  assert.throws(() => validatePlan({ days: [] }, request));
  assert.throws(() => validatePlan({ days: [0, 1].map(() => ({ meals: [food] })) }, request));
  assert.throws(() => validatePlan({ days: [0, 1].map(() => ({ meals: Array.from({ length: 4 }, () => ({ ...food, calories: 10 })) })) }, request));
});
test('AI adapter sends bearer key and image to OpenAI-compatible endpoint', async () => {
  let captured;
  const ai = createAI(config, async (url, options) => {
    captured = { url, ...options };
    return success({ isFood: true, items: [food] });
  });
  const result = await ai.analyze({ image, description: 'Ít sốt' });
  assert.equal(captured.url, 'http://localhost:9999/v1/chat/completions');
  assert.equal(captured.headers.Authorization, 'Bearer secret-test');
  const body = JSON.parse(captured.body);
  assert.equal(body.model, config.model);
  assert.equal(body.stream, false);
  assert.match(body.messages[1].content[1].image_url.url, /^data:image\/png;base64,/);
  assert.equal(result.totals.calories, 500);
});
test('upstream failures never expose provider response or API key', async () => {
  const ai = createAI(config, async () => ({ ok: false, status: 401, text: async () => 'secret-test' }));
  await assert.rejects(ai.analyze({ image }), error => error.status === 502 && !error.message.includes('secret-test'));
});
test('rejects truncated JSON and handles timeout', async () => {
  const truncated = createAI(config, async () => ({ ok: true, json: async () => ({ choices: [{ finish_reason: 'length', message: { content: '{}' } }] }) }));
  await assert.rejects(truncated.analyze({ image }), /cắt ngắn/);
  const timeout = createAI(config, async () => { const e = new Error(); e.name = 'TimeoutError'; throw e; });
  await assert.rejects(timeout.analyze({ image }), error => error.status === 504);
});
test('HTTP auth, JSON validation, health, and rate limits', async t => {
  const token = 'local-test-token-with-32-characters';
  let calls = 0;
  const server = createServer({ token, rateLimit: 2, ai: { analyze: async () => { calls++; return { ok: true }; } } });
  server.listen(0, '127.0.0.1'); await once(server, 'listening');
  t.after(() => new Promise(resolve => { server.close(resolve); server.closeAllConnections(); }));
  const base = `http://127.0.0.1:${server.address().port}`;
  assert.equal((await fetch(`${base}/health`)).status, 200);
  const options = { method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` }, body: '{}' };
  assert.equal((await fetch(`${base}/api/analyze`, { ...options, headers: { 'Content-Type': 'application/json' } })).status, 401);
  assert.equal((await fetch(`${base}/api/analyze`, { ...options, body: '{' })).status, 400);
  assert.equal((await fetch(`${base}/api/analyze`, options)).status, 200);
  assert.equal((await fetch(`${base}/api/analyze`, options)).status, 429);
  assert.equal(calls, 1);
});
