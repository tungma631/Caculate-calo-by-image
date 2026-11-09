import http from 'node:http';
import { timingSafeEqual } from 'node:crypto';
import { pathToFileURL } from 'node:url';
import { AppError, ensure } from './domain.js';
import { createAI } from './ai.js';

async function readJson(req) {
  ensure(req.headers['content-type']?.split(';')[0] === 'application/json', 'Yêu cầu phải là JSON.', 415);
  let size = 0;
  const chunks = [];
  for await (const chunk of req) {
    size += chunk.length;
    ensure(size <= 7_100_000, 'Dữ liệu quá lớn. Ảnh tối đa 5 MB.', 413);
    chunks.push(chunk);
  }
  try { return JSON.parse(Buffer.concat(chunks).toString('utf8')); }
  catch { throw new AppError(400, 'JSON không hợp lệ.'); }
}
function authorized(header, token) {
  const actual = Buffer.from(header ?? '');
  const expected = Buffer.from(`Bearer ${token}`);
  return actual.length === expected.length && timingSafeEqual(actual, expected);
}
export function createServer({ ai, token, rateLimit = 12 }) {
  ensure(typeof token === 'string' && token.length >= 24, 'APP_TOKEN phải có ít nhất 24 ký tự.', 500);
  const rates = new Map();
  return http.createServer(async (req, res) => {
    res.setHeader('Content-Type', 'application/json; charset=utf-8');
    res.setHeader('Cache-Control', 'no-store');
    res.setHeader('X-Content-Type-Options', 'nosniff');
    const send = (status, data) => { res.writeHead(status); res.end(JSON.stringify(data)); };
    try {
      if (req.method === 'GET' && req.url === '/health') return send(200, { status: 'ok', version: '0.1.0' });
      ensure(req.method === 'POST' && ['/api/analyze', '/api/plan'].includes(req.url), 'Không tìm thấy API.', 404);
      ensure(authorized(req.headers.authorization, token), 'Không có quyền truy cập backend.', 401);
      const now = Date.now();
      // Do not trust X-Forwarded-For. Configure a trusted proxy before public deployment.
      const ip = req.socket.remoteAddress;
      for (const [key, value] of rates) if (value.reset <= now) rates.delete(key);
      const entry = rates.get(ip) ?? { count: 0, reset: now + 60000 };
      rates.set(ip, entry);
      ensure(++entry.count <= rateLimit, 'Đã hết lượt trong phút này. Vui lòng đợi một chút.', 429);
      const input = await readJson(req);
      ensure(input && typeof input === 'object' && !Array.isArray(input), 'Nội dung yêu cầu không hợp lệ.');
      const result = req.url === '/api/analyze' ? await ai.analyze(input) : await ai.plan(input);
      send(200, result);
    } catch (error) {
      if (!res.headersSent && !res.destroyed) send(error instanceof AppError ? error.status : 500,
        { error: error instanceof AppError ? error.message : 'Lỗi máy chủ. Vui lòng thử lại.' });
    }
  });
}
if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const { AI_BASE_URL, AI_API_KEY, AI_MODEL, APP_TOKEN } = process.env;
  ensure(AI_BASE_URL && AI_API_KEY && AI_MODEL, 'Thiếu cấu hình AI trong .env.', 500);
  const endpoint = new URL(AI_BASE_URL);
  ensure(['http:', 'https:'].includes(endpoint.protocol) && !endpoint.username && !endpoint.password,
    'AI_BASE_URL không hợp lệ.', 500);
  const timeoutMs = Number(process.env.AI_TIMEOUT_MS ?? 90000);
  const rateLimit = Number(process.env.RATE_LIMIT_PER_MINUTE ?? 12);
  ensure(Number.isFinite(timeoutMs) && timeoutMs >= 1000 && Number.isInteger(rateLimit) && rateLimit > 0, 'Cấu hình thời gian chờ/hạn mức không hợp lệ.', 500);
  const server = createServer({ ai: createAI({ baseUrl: AI_BASE_URL, apiKey: AI_API_KEY, model: AI_MODEL, timeoutMs }), token: APP_TOKEN, rateLimit });
  server.requestTimeout = 110000;
  server.headersTimeout = 15000;
  server.listen(Number(process.env.PORT ?? 8787), process.env.HOST ?? '127.0.0.1', () => {
    console.log(`CaloLens API ready on port ${server.address().port}`);
  });
}
