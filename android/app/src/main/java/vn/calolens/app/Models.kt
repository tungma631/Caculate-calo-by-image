package vn.calolens.app

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID

data class Food(
    val name: String, val grams: Double, val calories: Double,
    val protein: Double, val carbs: Double, val fat: Double,
) {
    fun json() = JSONObject().put("name", name).put("grams", grams).put("calories", calories)
        .put("protein", protein).put("carbs", carbs).put("fat", fat)
    fun scaled(newGrams: Double): Food {
        val ratio = newGrams / grams
        return copy(grams = newGrams, calories = calories * ratio, protein = protein * ratio,
            carbs = carbs * ratio, fat = fat * ratio)
    }
    companion object {
        fun from(j: JSONObject) = Food(j.getString("name"), j.getDouble("grams"),
            j.getDouble("calories"), j.getDouble("protein"), j.getDouble("carbs"), j.getDouble("fat"))
    }
}
data class Profile(
    val age: Int = 25, val height: Double = 165.0, val weight: Double = 60.0,
    val dailyCalories: Int = 2000, val goal: String = "Duy trì cân nặng",
    val preferences: String = "Món Việt, dễ nấu", val allergies: String = "", val budget: String = "100.000đ/ngày",
) {
    fun json() = JSONObject().put("age", age).put("height", height).put("weight", weight)
        .put("dailyCalories", dailyCalories).put("goal", goal).put("preferences", preferences)
        .put("allergies", allergies).put("budget", budget)
    companion object {
        fun from(j: JSONObject) = Profile(j.getInt("age"), j.getDouble("height"), j.getDouble("weight"),
            j.getInt("dailyCalories"), j.getString("goal"), j.optString("preferences"), j.optString("allergies"), j.optString("budget"))
    }
}
data class Meal(
    val id: String = UUID.randomUUID().toString(), val date: String = LocalDate.now().toString(),
    val slot: String, val foods: List<Food>, val source: String = "ai_estimate", val planKey: String = "",
) {
    val calories get() = foods.sumOf { it.calories }
    fun json() = JSONObject().put("id", id).put("date", date).put("slot", slot)
        .put("foods", JSONArray(foods.map { it.json() })).put("source", source).put("planKey", planKey)
    companion object {
        fun from(j: JSONObject) = Meal(j.getString("id"), j.getString("date"), j.getString("slot"),
            j.getJSONArray("foods").objects().map(Food::from), j.optString("source", "ai_estimate"), j.optString("planKey"))
    }
}
data class PlanMeal(val slot: String, val food: Food) {
    fun json() = food.json().put("slot", slot)
}
data class PlanDay(val date: String, val meals: List<PlanMeal>) {
    fun json() = JSONObject().put("date", date).put("meals", JSONArray(meals.map { it.json() }))
    companion object {
        fun from(j: JSONObject) = PlanDay(j.getString("date"), j.getJSONArray("meals").objects().map {
            PlanMeal(it.getString("slot"), Food.from(it))
        })
    }
}
data class Analysis(val foods: List<Food>, val notes: String, val isFood: Boolean)
fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
val mealSlots = listOf("Sáng", "Trưa", "Tối", "Ăn nhẹ")
