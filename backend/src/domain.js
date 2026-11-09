export class AppError extends Error {
  constructor(status, message) { super(message); this.status = status; }
}
export function ensure(condition, message, status = 400) {
  if (!condition) throw new AppError(status, message);
}
export function bounded(value, min, max, label, status = 400) {
  ensure(typeof value === 'number' && Number.isFinite(value) && value >= min && value <= max,
    `${label} phải nằm trong khoảng ${min}–${max}.`, status);
  return value;
}
export function text(value, max, label, required = false, status = 400) {
  ensure(typeof value === 'string' && value.length <= max && (!required || value.trim().length > 0),
    `${label} không hợp lệ.`, status);
  return value.trim();
}
export function validateProfile(input) {
  ensure(input && typeof input === 'object', 'Thiếu hồ sơ cá nhân.');
  const profile = {
    age: bounded(input.age, 18, 100, 'Tuổi'),
    height: bounded(input.height, 100, 230, 'Chiều cao'),
    weight: bounded(input.weight, 30, 300, 'Cân nặng'),
    dailyCalories: bounded(input.dailyCalories, 1200, 5000, 'Mục tiêu kcal'),
    goal: text(input.goal, 100, 'Mục tiêu', true),
    preferences: text(input.preferences ?? '', 1000, 'Sở thích'),
    allergies: text(input.allergies ?? '', 500, 'Thực phẩm cần tránh'),
    budget: text(input.budget ?? '', 200, 'Ngân sách'),
  };
  return profile;
}
export function validateImage(input) {
  ensure(input && typeof input === 'object', 'Thiếu hình ảnh.');
  ensure(['image/jpeg', 'image/png', 'image/webp'].includes(input.mimeType), 'Chỉ hỗ trợ JPEG, PNG hoặc WebP.');
  ensure(typeof input.base64 === 'string' && input.base64.length > 0 && input.base64.length <= 7_000_000 &&
    /^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$/.test(input.base64), 'Dữ liệu ảnh không hợp lệ.');
  const bytes = Buffer.from(input.base64, 'base64');
  ensure(bytes.length <= 5 * 1024 * 1024, 'Ảnh tối đa 5 MB.');
  const valid = input.mimeType === 'image/jpeg' ? bytes[0] === 0xff && bytes[1] === 0xd8 && bytes[2] === 0xff
    : input.mimeType === 'image/png' ? bytes.subarray(0, 8).equals(Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]))
    : bytes.toString('ascii', 0, 4) === 'RIFF' && bytes.toString('ascii', 8, 12) === 'WEBP';
  ensure(valid, 'Định dạng ảnh không khớp dữ liệu.');
  return { mimeType: input.mimeType, base64: input.base64 };
}
export function nutrition(item, label = 'Món ăn') {
  ensure(item && typeof item === 'object', `${label}: AI trả dữ liệu không hợp lệ.`, 502);
  return {
    name: text(item.name, 160, `${label}: tên`, true, 502),
    grams: bounded(item.grams, 1, 5000, `${label}: khẩu phần`, 502),
    calories: bounded(item.calories, 0, 10000, `${label}: kcal`, 502),
    protein: bounded(item.protein, 0, 1000, `${label}: protein`, 502),
    carbs: bounded(item.carbs, 0, 1000, `${label}: carbohydrate`, 502),
    fat: bounded(item.fat, 0, 1000, `${label}: chất béo`, 502),
  };
}
export function validateAnalysis(data) {
  ensure(data && typeof data.isFood === 'boolean' && Array.isArray(data.items), 'AI chưa trả kết quả phân tích hợp lệ.', 502);
  ensure(data.items.length <= 20 && (data.isFood ? data.items.length > 0 : data.items.length === 0), 'Số lượng món không hợp lệ.', 502);
  const items = data.items.map((item) => nutrition(item));
  return {
    isFood: data.isFood,
    items,
    notes: text(data.notes ?? '', 2000, 'Ghi chú AI', false, 502),
    source: 'ai_estimate',
    totals: items.reduce((sum, item) => {
      for (const key of ['calories', 'protein', 'carbs', 'fat']) sum[key] = Math.round((sum[key] + item[key]) * 10) / 10;
      return sum;
    }, { calories: 0, protein: 0, carbs: 0, fat: 0 }),
  };
}
export function validatePlanRequest(input) {
  const profile = validateProfile(input.profile);
  const count = bounded(input.days, 1, 7, 'Số ngày');
  ensure(Number.isInteger(count), 'Số ngày phải là số nguyên.');
  ensure(typeof input.startDate === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(input.startDate), 'Ngày bắt đầu không hợp lệ.');
  const start = new Date(`${input.startDate}T00:00:00Z`);
  ensure(!Number.isNaN(start.getTime()) && start.toISOString().slice(0, 10) === input.startDate, 'Ngày bắt đầu không hợp lệ.');
  const avoidMeals = input.avoidMeals ?? [];
  ensure(Array.isArray(avoidMeals) && avoidMeals.length <= 100 && avoidMeals.every(x => typeof x === 'string' && x.length <= 160), 'Danh sách món cũ không hợp lệ.');
  return { profile, days: count, startDate: input.startDate, avoidMeals };
}
export function validatePlan(data, request) {
  ensure(data && Array.isArray(data.days) && data.days.length === request.days, 'AI chưa tạo đủ số ngày yêu cầu.', 502);
  return {
    source: 'ai_estimate',
    days: data.days.map((day, index) => {
      ensure(day && typeof day === 'object', 'AI trả ngày không hợp lệ.', 502);
      const date = new Date(`${request.startDate}T00:00:00Z`);
      date.setUTCDate(date.getUTCDate() + index);
      ensure(Array.isArray(day.meals) && day.meals.length === 4, 'Mỗi ngày cần đủ 4 bữa.', 502);
      const slots = ['Sáng', 'Trưa', 'Tối', 'Ăn nhẹ'];
      const meals = day.meals.map((meal, i) => ({ ...nutrition(meal), slot: slots[i] }));
      const total = meals.reduce((sum, meal) => sum + meal.calories, 0);
      ensure(Math.abs(total - request.profile.dailyCalories) <= request.profile.dailyCalories * 0.15,
        'Thực đơn AI lệch mục tiêu quá 15%. Vui lòng tạo lại.', 502);
      return { date: date.toISOString().slice(0, 10), meals };
    }),
  };
}
