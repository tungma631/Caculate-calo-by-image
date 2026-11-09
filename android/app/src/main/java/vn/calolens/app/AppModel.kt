package vn.calolens.app

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import android.net.Uri
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class AppModel(application: Application) : AndroidViewModel(application) {
    private val store = application.getSharedPreferences("calolens", 0)
    private val api = ApiClient()
    var profile by mutableStateOf(Profile()); private set
    var meals by mutableStateOf(emptyList<Meal>()); private set
    var plan by mutableStateOf(emptyList<PlanDay>()); private set
    var profileComplete by mutableStateOf(false); private set
    var consent by mutableStateOf(false); private set
    var busy by mutableStateOf(false); private set
    var progress by mutableStateOf(""); private set
    var error by mutableStateOf<String?>(null)
    var message by mutableStateOf<String?>(null)
    var photo by mutableStateOf<Bitmap?>(null); private set
    var analysis by mutableStateOf<Analysis?>(null); private set
    var tab by mutableIntStateOf(0)
    init {
        try {
            store.getString("profile", null)?.let { profile = Profile.from(JSONObject(it)) }
            store.getString("meals", null)?.let { meals = JSONArray(it).objects().map(Meal::from) }
            store.getString("plan", null)?.let { plan = JSONArray(it).objects().map(PlanDay::from) }
            profileComplete = store.getBoolean("profileComplete", false)
            consent = store.getBoolean("consent", false)
        } catch (_: Exception) { error = "Không đọc được một phần dữ liệu đã lưu. Hãy kiểm tra hồ sơ và nhật ký." }
    }
    private fun persist() {
        store.edit().putString("profile", profile.json().toString())
            .putString("meals", JSONArray(meals.map { it.json() }).toString())
            .putString("plan", JSONArray(plan.map { it.json() }).toString())
            .putBoolean("profileComplete", profileComplete).putBoolean("consent", consent).apply()
    }
    fun saveProfile(value: Profile) { profile = value; profileComplete = true; persist(); message = "Đã lưu hồ sơ cá nhân." }
    fun allowAI() { consent = true; persist() }
    fun revokeAI() { consent = false; persist() }
    fun loadPhoto(uri: Uri) {
        if (busy) return
        busy = true; progress = "Đang chuẩn bị ảnh…"; analysis = null; error = null
        viewModelScope.launch {
            try {
                photo = withContext(Dispatchers.IO) {
                    val resolver = getApplication<Application>().contentResolver
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                    require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Không đọc được ảnh. Hãy chọn JPEG, PNG hoặc WebP." }
                    var sample = 1
                    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1600) sample *= 2
                    val decoded = resolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
                    } ?: error("Không đọc được ảnh.")
                    val orientation = try { resolver.openInputStream(uri)?.use {
                        ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                    } } catch (_: Exception) { null }
                    val matrix = Matrix().apply {
                        when (orientation) {
                            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
                            ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                            ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
                            ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
                            ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                            ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(270f); postScale(-1f, 1f) }
                            ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(270f)
                        }
                    }
                    Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
                }
            } catch (e: Exception) { error = e.message ?: "Không đọc được ảnh."; photo = null }
            finally { busy = false }
        }
    }
    fun analyze(description: String) {
        val bitmap = photo ?: return
        if (busy || !consent) return
        busy = true; error = null; progress = "Gemini đang nhận diện món và ước tính khẩu phần…"
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val bytes = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 82, it) }.toByteArray()
                    api.post("/api/analyze", JSONObject().put("description", description)
                        .put("image", JSONObject().put("mimeType", "image/jpeg").put("base64", Base64.encodeToString(bytes, Base64.NO_WRAP))))
                }
                analysis = Analysis(result.getJSONArray("items").objects().map(Food::from), result.optString("notes"), result.getBoolean("isFood"))
            } catch (e: Exception) { error = e.message ?: "Phân tích thất bại." }
            finally { busy = false }
        }
    }
    fun editFood(index: Int, food: Food) {
        analysis = analysis?.let { it.copy(foods = it.foods.toMutableList().apply { set(index, food) }) }
    }
    fun removeFood(index: Int) { analysis = analysis?.let { it.copy(foods = it.foods.filterIndexed { i, _ -> i != index }) } }
    fun addFood(food: Food) { analysis = (analysis ?: Analysis(emptyList(), "Tự nhập dinh dưỡng cho toàn bộ khẩu phần.", true)).let { it.copy(foods = it.foods + food, isFood = true) } }
    fun saveAnalysis(slot: String, date: LocalDate) {
        val current = analysis ?: return
        if (current.foods.isEmpty() || busy) return
        meals = meals + Meal(slot = slot, date = date.toString(), foods = current.foods,
            source = if (photo == null) "manual" else "ai_estimate")
        analysis = null; photo = null; persist(); tab = 1; message = "Đã lưu bữa ăn vào nhật ký."
    }
    fun deleteMeal(id: String) { meals = meals.filterNot { it.id == id }; persist() }
    fun editSavedFood(id: String, index: Int, food: Food) {
        meals = meals.map { meal -> if (meal.id == id) meal.copy(foods = meal.foods.toMutableList().apply { set(index, food) }) else meal }
        persist(); message = "Đã cập nhật bữa ăn."
    }
    fun logPlanned(day: PlanDay, meal: PlanMeal, actual: Food, actualDate: LocalDate) {
        val key = "${day.date}:${meal.slot}"
        if (meals.any { it.planKey == key }) { message = "Bữa này đã được ghi nhận."; return }
        meals = meals + Meal(date = actualDate.toString(), slot = meal.slot, foods = listOf(actual), planKey = key)
        persist(); message = "Đã ghi nhận bữa ăn thực tế."
    }
    fun generatePlan(period: Int, start: LocalDate) {
        if (busy || !consent) return
        if (!profileComplete) { error = "Hãy lưu hồ sơ cá nhân trước khi lập thực đơn."; tab = 3; return }
        val count = when (period) { 0 -> 1; 1 -> 7; else -> ChronoUnit.DAYS.between(start, start.plusMonths(1)).toInt() }
        val snapshot = profile
        busy = true; error = null
        viewModelScope.launch {
            try {
                val generated = mutableListOf<PlanDay>()
                var offset = 0
                while (offset < count) {
                    val batch = minOf(7, count - offset)
                    progress = "Đang tạo thực đơn ngày ${offset + 1}–${offset + batch}/$count…"
                    val result = withContext(Dispatchers.IO) {
                        api.post("/api/plan", JSONObject().put("profile", snapshot.json()).put("days", batch)
                            .put("startDate", start.plusDays(offset.toLong()).toString())
                            .put("avoidMeals", JSONArray(generated.flatMap { it.meals }.map { it.food.name }.takeLast(100))))
                    }
                    generated.addAll(result.getJSONArray("days").objects().map(PlanDay::from))
                    offset += batch
                }
                val dates = generated.map { it.date }.toSet()
                plan = (plan.filterNot { it.date in dates } + generated).sortedBy { it.date }
                persist(); message = "Đã tạo kế hoạch $count ngày."
            } catch (e: Exception) { error = (e.message ?: "Không tạo được thực đơn.") + " Kế hoạch cũ được giữ nguyên." }
            finally { busy = false }
        }
    }
    fun resetData() {
        if (busy) return
        profile = Profile(); meals = emptyList(); plan = emptyList(); photo = null; analysis = null
        consent = false; profileComplete = false; store.edit().clear().apply()
        java.io.File(getApplication<Application>().cacheDir, "capture.jpg").delete()
        message = "Đã xóa dữ liệu trên thiết bị."
    }
}
