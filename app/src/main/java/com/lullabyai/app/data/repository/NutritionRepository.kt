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
        loggedAt: String? = null, durationMin: Int? = null,
        subtype: String? = null, leftMin: Int? = null, rightMin: Int? = null,
        temperature: String? = null, reaction: String? = null, method: String? = null
    ): Result<Unit> = runCatching {
        supabase.from("milk_logs").insert(buildJsonObject {
            put("child_id", JsonPrimitive(childId))
            put("milk_type", JsonPrimitive(milkType))
            put("volume_ml", JsonPrimitive(volumeMl))
            if (durationMin != null) put("duration_min", JsonPrimitive(durationMin))
            put("side", side?.let { JsonPrimitive(it) } ?: JsonNull)
            put("notes", notes?.takeIf { it.isNotBlank() }?.let { JsonPrimitive(it) } ?: JsonNull)
            if (loggedAt != null) put("logged_at", JsonPrimitive(loggedAt))
            put("subtype", subtype?.let { JsonPrimitive(it) } ?: JsonNull)
            put("left_min", leftMin?.let { JsonPrimitive(it) } ?: JsonNull)
            put("right_min", rightMin?.let { JsonPrimitive(it) } ?: JsonNull)
            put("temperature", temperature?.let { JsonPrimitive(it) } ?: JsonNull)
            put("reaction", reaction?.let { JsonPrimitive(it) } ?: JsonNull)
            put("method", method?.let { JsonPrimitive(it) } ?: JsonNull)
        })
        MascotRewards.noteCareToday()
    }

    // ---------------- FOOD ----------------

    suspend fun getFoodLogs(childId: String, date: String = today()): List<FoodLog> = runCatching {
        supabase.from("food_logs").select {
            filter { eq("child_id", childId); eq("log_date", date) }
            order("created_at", Order.ASCENDING)
        }.decodeList<FoodLog>()
    }.getOrDefault(emptyList())

    /** Returns the new row's id, so the record screen can show exactly what it just logged. */
    suspend fun addFood(
        childId: String, mealType: String, itemName: String, calories: Int? = null, date: String = today(),
        photoPath: String? = null, eatenPct: Int? = null
    ): Result<String> = runCatching {
        val row = supabase.from("food_logs").insert(buildJsonObject {
            put("child_id", JsonPrimitive(childId))
            put("log_date", JsonPrimitive(date))
            put("meal_type", JsonPrimitive(mealType))
            put("item_name", JsonPrimitive(itemName))
            put("calories", calories?.let { JsonPrimitive(it) } ?: JsonNull)
            put("photo_path", photoPath?.let { JsonPrimitive(it) } ?: JsonNull)
            put("eaten_pct", eatenPct?.let { JsonPrimitive(it) } ?: JsonNull)
        }) { select() }.decodeSingle<com.lullabyai.app.data.model.FoodLog>()
        MascotRewards.noteCareToday()
        row.id
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
            .filter { !it.isWater && com.lullabyai.app.util.TimeUtils.localDate(it.loggedAt) == date }
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

    /**
     * The household's pantry — one fridge shared by every child of [ownerUserId] and by everyone
     * who cares for any of them. Row-level security still decides what comes back, so a carer who
     * works for two families gets this family's list only.
     */
    suspend fun getFridgeIngredients(ownerUserId: String): List<com.lullabyai.app.data.model.FridgeIngredient> = runCatching {
        supabase.from("fridge_ingredients").select {
            filter { eq("owner_user_id", ownerUserId) }
            order("created_at", Order.DESCENDING)
        }.decodeList<com.lullabyai.app.data.model.FridgeIngredient>()
    }.getOrDefault(emptyList())

    /** [childId] says which household this lands in; the database derives the household from it. */
    suspend fun addFridgeIngredient(
        childId: String,
        ingredientName: String,
        category: String = "general",
        quantity: String? = null,
        boughtOn: String? = null,
        expiresOn: String? = null,
        storage: String? = null,
        storageNote: String? = null,
        note: String? = null
    ): Result<com.lullabyai.app.data.model.FridgeIngredient> = runCatching {
        val userId = supabase.auth.currentUserOrNull()?.id ?: error("Not authenticated")
        supabase.from("fridge_ingredients").insert(buildJsonObject {
            put("child_id", JsonPrimitive(childId))
            put("user_id", JsonPrimitive(userId))
            put("ingredient_name", JsonPrimitive(ingredientName.trim()))
            put("category", JsonPrimitive(category))
            put("quantity", quantity?.takeIf { it.isNotBlank() }?.let { JsonPrimitive(it) } ?: JsonNull)
            put("bought_on", boughtOn?.let { JsonPrimitive(it) } ?: JsonNull)
            put("expires_on", expiresOn?.let { JsonPrimitive(it) } ?: JsonNull)
            put("storage", storage?.takeIf { it.isNotBlank() }?.let { JsonPrimitive(it) } ?: JsonNull)
            put("storage_note", storageNote?.takeIf { it.isNotBlank() }?.let { JsonPrimitive(it) } ?: JsonNull)
            put("note", note?.takeIf { it.isNotBlank() }?.let { JsonPrimitive(it) } ?: JsonNull)
        }) {
            select()
        }.decodeSingle<com.lullabyai.app.data.model.FridgeIngredient>()
    }

    suspend fun updateFridgeIngredient(
        id: String,
        ingredientName: String? = null,
        quantity: String? = null,
        expiresOn: String? = null,
        storage: String? = null,
        storageNote: String? = null,
        note: String? = null
    ): Result<Unit> = runCatching {
        supabase.from("fridge_ingredients").update(buildJsonObject {
            ingredientName?.let { put("ingredient_name", JsonPrimitive(it.trim())) }
            quantity?.let { put("quantity", JsonPrimitive(it)) }
            expiresOn?.let { put("expires_on", JsonPrimitive(it)) }
            storage?.let { put("storage", JsonPrimitive(it)) }
            storageNote?.let { put("storage_note", JsonPrimitive(it)) }
            note?.let { put("note", JsonPrimitive(it)) }
            put("updated_at", JsonPrimitive(java.time.Instant.now().toString()))
        }) {
            filter { eq("id", id) }
        }
        Unit
    }

    // ---------------- FOOD TRIALS (the 3-4 day wait rule, per child) ----------------

    suspend fun getFoodTrials(childId: String): List<com.lullabyai.app.data.model.FoodTrial> = runCatching {
        supabase.from("food_trials").select {
            filter { eq("child_id", childId) }
            order("started_on", Order.DESCENDING)
        }.decodeList<com.lullabyai.app.data.model.FoodTrial>()
    }.getOrDefault(emptyList())

    /** Starts the watch on a food for ONE child. Starting a food already listed resets its watch. */
    suspend fun startFoodTrial(childId: String, foodName: String): Result<Unit> = runCatching {
        val existing = getFoodTrials(childId).firstOrNull {
            it.foodName.trim().equals(foodName.trim(), ignoreCase = true)
        }
        val body = buildJsonObject {
            put("child_id", JsonPrimitive(childId))
            put("food_name", JsonPrimitive(foodName.trim()))
            put("started_on", JsonPrimitive(java.time.LocalDate.now().toString()))
            put("result", JsonPrimitive("testing"))
            put("reaction", JsonNull)
            put("decided_on", JsonNull)
            put("updated_at", JsonPrimitive(java.time.Instant.now().toString()))
        }
        if (existing == null) supabase.from("food_trials").insert(body)
        else supabase.from("food_trials").update(body) { filter { eq("id", existing.id) } }
        Unit
    }

    /** [result] is "safe" or "allergic"; [reaction] is what the parent saw, for an allergic one. */
    suspend fun setFoodTrialResult(id: String, result: String, reaction: String? = null): Result<Unit> = runCatching {
        require(result in setOf("testing", "safe", "allergic")) { "unknown trial result: $result" }
        supabase.from("food_trials").update(buildJsonObject {
            put("result", JsonPrimitive(result))
            put("reaction", reaction?.takeIf { it.isNotBlank() }?.let { JsonPrimitive(it) } ?: JsonNull)
            put("decided_on", if (result == "testing") JsonNull else JsonPrimitive(java.time.LocalDate.now().toString()))
            put("updated_at", JsonPrimitive(java.time.Instant.now().toString()))
        }) {
            filter { eq("id", id) }
        }
        Unit
    }

    suspend fun removeFridgeIngredient(id: String): Result<Unit> = runCatching {
        supabase.from("fridge_ingredients").delete {
            filter { eq("id", id) }
        }
        Unit
    }

}

