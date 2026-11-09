// Explicit opt-in smoke test: one real provider request, synthetic profile or sample image only.
import { createAI } from './src/ai.js';
import { readFile } from 'node:fs/promises';
const ai = createAI({ baseUrl: process.env.AI_BASE_URL, apiKey: process.env.AI_API_KEY,
  model: process.env.AI_MODEL, timeoutMs: 90000 });
try {
  if (process.argv[2] && !process.argv[2].startsWith('--days=')) {
    const bytes = await readFile(process.argv[2]);
    const result = await ai.analyze({ image: { mimeType: 'image/jpeg', base64: bytes.toString('base64') }, description: 'Ảnh món ăn thử nghiệm. Ước tính một khẩu phần.' });
    console.log(JSON.stringify({ test: 'image', isFood: result.isFood, items: result.items.map(x => x.name), totalCalories: result.totals.calories }));
  } else {
    const result = await ai.plan({ profile: { age: 25, height: 165, weight: 60, dailyCalories: 2000,
      goal: 'Duy trì cân nặng', preferences: 'Món Việt dễ nấu', allergies: '', budget: '100.000đ/ngày' },
      days: Number(process.argv[2]?.split('=')[1] ?? 1), startDate: '2026-11-09' });
    console.log(JSON.stringify({ test: 'plan', days: result.days.length, mealsPerDay: result.days.map(x => x.meals.length),
      dailyCalories: result.days.map(day => day.meals.reduce((s, x) => s + x.calories, 0)) }));
  }
} catch (error) { console.error(error.message); process.exitCode = 1; }
