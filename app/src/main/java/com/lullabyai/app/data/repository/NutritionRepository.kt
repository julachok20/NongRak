package com.lullabyai.app.data.repository

import com.lullabyai.app.data.model.ExerciseLog
import com.lullabyai.app.data.model.FoodLog
import com.lullabyai.app.data.remote.supabase
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.time.LocalDate

/**
 * Food + exercise logging for the Nutrition/Calorie/Exercise feature.
 * All rows are RLS-scoped to the parent via the children table.
 */
class NutritionRepository {

    private fun today(): String = LocalDate.now().toString()

    // ---------------- MILK ----------------

    suspend fun getMilkLogs(childId: String): List<com.lullabyai.app.data.model.MilkLog> = runCatching {
        supabase.from("milk_logs").select {
            filter { eq("child_id", childId) }
            order("logged_at", Order.DESCENDING)
        }.decodeList<com.lullabyai.app.data.model.MilkLog>()
    }.getOrDefault(emptyList())

    suspend fun addMilkLog(
        childId: String, milkType: String, volumeMl: Int, side: String?, notes: String?,
        loggedAt: String? = null
    ): Result<Unit> = runCatching {
        supabase.from("milk_logs").insert(buildJsonObject {
            put("child_id", JsonPrimitive(childId))
            put("milk_type", JsonPrimitive(milkType))
            put("volume_ml", JsonPrimitive(volumeMl))
            put("side", side?.let { JsonPrimitive(it) } ?: JsonNull)
            put("notes", notes?.takeIf { it.isNotBlank() }?.let { JsonPrimitive(it) } ?: JsonNull)
            if (loggedAt != null) put("logged_at", JsonPrimitive(loggedAt))
        })
    }

    // ---------------- FOOD ----------------

    suspend fun getFoodLogs(childId: String, date: String = today()): List<FoodLog> = runCatching {
        supabase.from("food_logs").select {
            filter { eq("child_id", childId); eq("log_date", date) }
            order("created_at", Order.ASCENDING)
        }.decodeList<FoodLog>()
    }.getOrDefault(emptyList())

    suspend fun addFood(childId: String, mealType: String, itemName: String, calories: Int? = null, date: String = today()): Result<Unit> = runCatching {
        supabase.from("food_logs").insert(buildJsonObject {
            put("child_id", JsonPrimitive(childId))
            put("log_date", JsonPrimitive(date))
            put("meal_type", JsonPrimitive(mealType))
            put("item_name", JsonPrimitive(itemName))
            put("calories", calories?.let { JsonPrimitive(it) } ?: JsonNull)
        })
    }

    suspend fun updateFood(id: String, itemName: String): Result<Unit> = runCatching {
        supabase.from("food_logs").update(buildJsonObject { put("item_name", JsonPrimitive(itemName)) }) {
            filter { eq("id", id) }
        }
    }

    suspend fun setFoodCalories(id: String, calories: Int): Result<Unit> = runCatching {
        supabase.from("food_logs").update(buildJsonObject { put("calories", JsonPrimitive(calories)) }) {
            filter { eq("id", id) }
        }
    }

    suspend fun deleteFood(id: String): Result<Unit> = runCatching {
        supabase.from("food_logs").delete { filter { eq("id", id) } }
    }

    /** Multi-day food history (Statistics "ตารางย้อนหลัง") — one query instead of looping
     *  getFoodLogs() per day, which is what SmartTrendsScreen/StatisticsInsightsScreen do today. */
    suspend fun getFoodLogsRange(childId: String, fromDate: String, toDate: String = today()): List<FoodLog> = runCatching {
        supabase.from("food_logs").select {
            filter { eq("child_id", childId); gte("log_date", fromDate); lte("log_date", toDate) }
            order("log_date", Order.DESCENDING)
        }.decodeList<FoodLog>()
    }.getOrDefault(emptyList())

    // ---------------- EXERCISE ----------------

    suspend fun getExerciseLogs(childId: String, date: String = today()): List<ExerciseLog> = runCatching {
        supabase.from("exercise_logs").select {
            filter { eq("child_id", childId); eq("log_date", date) }
            order("created_at", Order.ASCENDING)
        }.decodeList<ExerciseLog>()
    }.getOrDefault(emptyList())

    /** Exercise logs for the whole current week (for the weekly goal progress ring). */
    suspend fun getWeeklyExercise(childId: String): List<ExerciseLog> = runCatching {
        val weekStart = LocalDate.now().with(java.time.DayOfWeek.MONDAY).toString()
        supabase.from("exercise_logs").select {
            filter { eq("child_id", childId); gte("log_date", weekStart) }
            order("log_date", Order.DESCENDING)
        }.decodeList<ExerciseLog>()
    }.getOrDefault(emptyList())

    suspend fun addExercise(
        childId: String, activityName: String, durationMin: Int?, caloriesBurned: Int?, date: String = today(),
        mood: String? = null, notes: String? = null
    ): Result<Unit> = runCatching {
        supabase.from("exercise_logs").insert(buildJsonObject {
            put("child_id", JsonPrimitive(childId))
            put("log_date", JsonPrimitive(date))
            put("activity_name", JsonPrimitive(activityName))
            put("duration_min", durationMin?.let { JsonPrimitive(it) } ?: JsonNull)
            put("calories_burned", caloriesBurned?.let { JsonPrimitive(it) } ?: JsonNull)
            put("mood", mood?.let { JsonPrimitive(it) } ?: JsonNull)
            put("notes", notes?.let { JsonPrimitive(it) } ?: JsonNull)
        })
    }

    suspend fun updateExercise(
        id: String, activityName: String, durationMin: Int, caloriesBurned: Int?
    ): Result<Unit> = runCatching {
        supabase.from("exercise_logs").update(buildJsonObject {
            put("activity_name", JsonPrimitive(activityName))
            put("duration_min", JsonPrimitive(durationMin))
            put("calories_burned", caloriesBurned?.let { JsonPrimitive(it) } ?: JsonNull)
        }) { filter { eq("id", id) } }
    }

    suspend fun deleteExercise(id: String): Result<Unit> = runCatching {
        supabase.from("exercise_logs").delete { filter { eq("id", id) } }
    }

    /** Multi-day exercise history (Statistics "ตารางย้อนหลัง") — getWeeklyExercise() only covers
     *  since Monday of the current week, not a real look-back window. */
    suspend fun getExerciseLogsRange(childId: String, fromDate: String, toDate: String = today()): List<ExerciseLog> = runCatching {
        supabase.from("exercise_logs").select {
            filter { eq("child_id", childId); gte("log_date", fromDate); lte("log_date", toDate) }
            order("log_date", Order.DESCENDING)
        }.decodeList<ExerciseLog>()
    }.getOrDefault(emptyList())

    // ---------------- SUMMARY (computed, not stored) ----------------

    data class DailySummary(
        val intakeKcal: Int,   // food + milk combined
        val milkKcal: Int,     // milk's share of intakeKcal, broken out for display
        val burnedKcal: Int,
        val perMeal: Map<String, Int>,   // meal_type -> kcal
        val exerciseMinutes: Int
    )

    /** Breast milk / formula energy density: ~20 kcal per fl oz, the figure commonly cited by the
     *  AAP / La Leche League — used uniformly regardless of milk_type since we don't distinguish
     *  brand/composition. A rough estimate, same caveat as the other nutrient targets in this app. */
    private val MILK_KCAL_PER_ML = 0.676f

    suspend fun getDailySummary(childId: String, date: String = today()): DailySummary {
        val foods = getFoodLogs(childId, date)
        val ex = getExerciseLogs(childId, date)
        val milkMl = getMilkLogs(childId)
            .filter { com.lullabyai.app.util.TimeUtils.localDate(it.loggedAt) == date }
            .sumOf { it.volumeMl }
        val milkKcal = (milkMl * MILK_KCAL_PER_ML).toInt()
        val perMeal = foods.groupBy { it.mealType }
            .mapValues { (_, items) -> items.sumOf { it.calories ?: 0 } }
        return DailySummary(
            intakeKcal = foods.sumOf { it.calories ?: 0 } + milkKcal,
            milkKcal = milkKcal,
            burnedKcal = ex.sumOf { it.caloriesBurned ?: 0 },
            perMeal = perMeal,
            exerciseMinutes = ex.sumOf { it.durationMin ?: 0 }
        )
    }

    // ---------------- SMART REFRIGERATOR & INGREDIENTS ----------------

    suspend fun getFridgeIngredients(childId: String): List<com.lullabyai.app.data.model.FridgeIngredient> = runCatching {
        supabase.from("fridge_ingredients").select {
            filter { eq("child_id", childId) }
            order("created_at", Order.DESCENDING)
        }.decodeList<com.lullabyai.app.data.model.FridgeIngredient>()
    }.getOrDefault(emptyList())

    suspend fun addFridgeIngredient(
        childId: String,
        ingredientName: String,
        category: String = "general",
        quantity: String? = null
    ): Result<com.lullabyai.app.data.model.FridgeIngredient> = runCatching {
        val userId = supabase.auth.currentUserOrNull()?.id ?: error("Not authenticated")
        supabase.from("fridge_ingredients").insert(buildJsonObject {
            put("child_id", JsonPrimitive(childId))
            put("user_id", JsonPrimitive(userId))
            put("ingredient_name", JsonPrimitive(ingredientName.trim()))
            put("category", JsonPrimitive(category))
            put("quantity", quantity?.takeIf { it.isNotBlank() }?.let { JsonPrimitive(it) } ?: JsonNull)
        }) {
            select()
        }.decodeSingle<com.lullabyai.app.data.model.FridgeIngredient>()
    }

    suspend fun removeFridgeIngredient(id: String): Result<Unit> = runCatching {
        supabase.from("fridge_ingredients").delete {
            filter { eq("id", id) }
        }
        Unit
    }

    suspend fun setFridgeIngredients(childId: String, ingredientNames: List<String>): Result<Unit> = runCatching {
        val userId = supabase.auth.currentUserOrNull()?.id ?: error("Not authenticated")
        // Clear old list
        supabase.from("fridge_ingredients").delete {
            filter { eq("child_id", childId) }
        }
        // Insert new list
        val distinct = ingredientNames.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (distinct.isNotEmpty()) {
            distinct.forEach { name ->
                supabase.from("fridge_ingredients").insert(buildJsonObject {
                    put("child_id", JsonPrimitive(childId))
                    put("user_id", JsonPrimitive(userId))
                    put("ingredient_name", JsonPrimitive(name))
                    put("category", JsonPrimitive("general"))
                })
            }
        }
        Unit
    }

    suspend fun syncFridgeIngredients(sourceChildId: String, targetChildId: String): Result<Unit> = runCatching {
        supabase.from("fridge_ingredients").postgrest.rpc(
            "sync_fridge_ingredients",
            buildJsonObject {
                put("p_source_child_id", JsonPrimitive(sourceChildId))
                put("p_target_child_id", JsonPrimitive(targetChildId))
            }
        )
        Unit
    }
}

