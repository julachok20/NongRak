package com.lullabyai.app.data.repository

import com.lullabyai.app.data.model.ExerciseContent
import com.lullabyai.app.data.model.RecipeContent
import com.lullabyai.app.data.model.SuggestionItem
import com.lullabyai.app.data.remote.ClaudeClient
import kotlinx.serialization.json.*

/**
 * AI-generated nutrition & exercise content. Uses the AI proxy with a JSON-mode system prompt
 * (bypasses the chat persona's "no symbols" rule). All human-readable text is produced in the
 * language passed in [lang] ("th" or "en"), so content auto-switches with the Settings toggle.
 */
object NutritionAiService {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun langName(lang: String) = if (lang.startsWith("en")) "English" else "Thai (ภาษาไทย)"

    private fun sys(lang: String) =
        "You are a pediatric (0-3 years) nutrition and exercise assistant. " +
        "Reply with ONLY valid minified JSON for the requested schema — no markdown, no code fences, no commentary. " +
        "All human-readable string values must be written in ${langName(lang)}."

    /** Pull the JSON substring out of any stray wrapping text. */
    private fun extractJson(raw: String): String {
        val s = raw.trim()
        val start = s.indexOfFirst { it == '{' || it == '[' }
        val end = s.indexOfLast { it == '}' || it == ']' }
        return if (start in 0 until end) s.substring(start, end + 1) else s
    }

    private suspend fun ask(prompt: String, lang: String, maxTokens: Int = 1024, useSearch: Boolean = false): JsonElement? {
        val raw = ClaudeClient.generateContent(prompt, useSearch = useSearch, maxTokens = maxTokens, system = sys(lang))
        return runCatching { json.parseToJsonElement(extractJson(raw)) }.getOrNull()
    }

    // ---------------- Calories ----------------

    /** kcal estimate per food item, aligned to input order. Empty list on failure. */
    suspend fun estimateCalories(items: List<String>, ageMonths: Int, lang: String): List<Int> {
        if (items.isEmpty()) return emptyList()
        val list = items.mapIndexed { i, it -> "${i + 1}. $it" }.joinToString("\n")
        val prompt = "Estimate calories (kcal) of the SMALL CHILD-SIZED serving that a $ageMonths-month-old " +
            "toddler actually eats for each food item — a child portion is a fraction of an adult portion, " +
            "so each number should be small (a 0-3 year old's whole day is only ~550-1200 kcal total). " +
            "Items in order:\n$list\n" +
            "Return JSON exactly: {\"calories\":[int, ...]} with one integer per item in the SAME order."
        val obj = ask(prompt, lang, useSearch = true)?.objOrNull() ?: return emptyList()
        return (obj["calories"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.intOrNull } ?: emptyList()
    }

    /**
     * Estimate kcal AND assign a meal_type for a single typed food, using the time of day.
     * Returns (kcal, mealType in breakfast|lunch|dinner|snack|other). Null on failure.
     */
    suspend fun estimateFood(item: String, ageMonths: Int, hour: Int, lang: String): Pair<Int, String>? {
        val prompt = "Estimate calories (kcal) of \"$item\" for the SMALL CHILD-SIZED serving that a " +
            "$ageMonths-month-old actually eats — a toddler portion is a fraction of an adult portion, " +
            "so the number should be small (a 0-3 year old's whole day is only ~550-1200 kcal). " +
            "Also assign a meal type given the current hour is $hour (0-23). " +
            "meal must be exactly one of: breakfast, lunch, dinner, snack, other. " +
            "Return JSON exactly: {\"kcal\":int, \"meal\":\"...\"}"
        val o = ask(prompt, lang, useSearch = true)?.objOrNull() ?: return null
        val kcal = (o["kcal"] as? JsonPrimitive)?.intOrNull ?: return null
        val meal = (o["meal"] as? JsonPrimitive)?.contentOrNull?.lowercase()?.takeIf {
            it in setOf("breakfast", "lunch", "dinner", "snack", "other")
        } ?: "other"
        return kcal to meal
    }

    data class CalorieCheck(val kcal: Int, val carb: Int, val protein: Int, val fat: Int, val tip: String)

    /** Parent-facing quick calorie check for any typed dish/meal: total kcal + macros + one tip. */
    suspend fun calorieCheck(food: String, lang: String): CalorieCheck? {
        if (food.isBlank()) return null
        val prompt = "Estimate the TOTAL calories (kcal) and macronutrients in grams for this food or meal: \"$food\". " +
            "Also give ONE short, practical healthy-eating tip. " +
            "Return JSON exactly: {\"kcal\":int,\"carb\":int,\"protein\":int,\"fat\":int,\"tip\":\"...\"}"
        val o = ask(prompt, lang, useSearch = true)?.objOrNull() ?: return null
        val kcal = (o["kcal"] as? JsonPrimitive)?.intOrNull ?: return null
        return CalorieCheck(
            kcal = kcal,
            carb = (o["carb"] as? JsonPrimitive)?.intOrNull ?: 0,
            protein = (o["protein"] as? JsonPrimitive)?.intOrNull ?: 0,
            fat = (o["fat"] as? JsonPrimitive)?.intOrNull ?: 0,
            tip = o.str("tip")
        )
    }

    /** Estimated TOTAL macronutrients (grams) for today's foods: Triple(carb, protein, fat). Null on failure. */
    suspend fun estimateMacros(items: List<String>, ageMonths: Int, lang: String): Triple<Int, Int, Int>? {
        if (items.isEmpty()) return null
        val list = items.joinToString(", ")
        val prompt = "Estimate the TOTAL macronutrients in grams for these foods eaten by a $ageMonths-month-old child: $list. " +
            "Return JSON exactly: {\"carb\":int,\"protein\":int,\"fat\":int}"
        val o = ask(prompt, lang, useSearch = true)?.objOrNull() ?: return null
        val c = (o["carb"] as? JsonPrimitive)?.intOrNull ?: return null
        val p = (o["protein"] as? JsonPrimitive)?.intOrNull ?: 0
        val f = (o["fat"] as? JsonPrimitive)?.intOrNull ?: 0
        return Triple(c, p, f)
    }

    /** Estimated calories burned for an activity of [durationMin] by a child of [ageMonths]. Null on failure. */
    suspend fun estimateBurn(activity: String, durationMin: Int, ageMonths: Int, lang: String): Int? {
        val prompt = "Estimate calories burned by a $ageMonths-month-old child doing \"$activity\" for $durationMin minutes. " +
            "Return JSON exactly: {\"kcal\":int}"
        return (ask(prompt, lang)?.objOrNull()?.get("kcal") as? JsonPrimitive)?.intOrNull
    }

    // ---------------- Suggestions ----------------

    suspend fun suggestMeal(ageMonths: Int, lang: String): SuggestionItem? {
        val prompt = "Suggest ONE recommended meal for a $ageMonths-month-old child today. " +
            "Return JSON: {\"title\":..,\"subtitle\":..}"
        val o = ask(prompt, lang)?.objOrNull() ?: return null
        return SuggestionItem(o.str("title"), o.str("subtitle"))
    }

    /** Recommend ONE dish for a specific meal (breakfast/lunch/dinner) the child hasn't had yet. */
    suspend fun recommendMealDish(meal: String, ageMonths: Int, lang: String): SuggestionItem? {
        val prompt = "Suggest ONE healthy, age-appropriate $meal dish for a $ageMonths-month-old child. " +
            "Return JSON: {\"title\":..,\"subtitle\":..} where subtitle is a short reason it's good for this meal."
        val o = ask(prompt, lang)?.objOrNull() ?: return null
        val title = (o["title"] as? JsonPrimitive)?.contentOrNull ?: return null
        return SuggestionItem(title, o.str("subtitle"))
    }

    suspend fun recommendedFoods(ageMonths: Int, lang: String): List<SuggestionItem> {
        val prompt = "Suggest 3 age-appropriate food groups for a $ageMonths-month-old child. " +
            "Return JSON array: [{\"title\":..,\"subtitle\":..,\"tags\":[..]}]"
        return parseSuggestions(ask(prompt, lang) ?: return emptyList())
    }

    /**
     * A balanced day's food plan grouped by meal (breakfast/lunch/dinner/snack), portioned for
     * healthy calorie intake. Returns meal -> list of foods. Retries once. Empty map on failure.
     */
    suspend fun mealPlan(ageMonths: Int, lang: String): Map<String, List<SuggestionItem>> {
        val prompt = "Plan a balanced day of food for a $ageMonths-month-old child organized by meal, " +
            "with age-appropriate portions for a healthy daily calorie intake. " +
            "Return JSON exactly: {\"breakfast\":[{\"title\":..,\"subtitle\":..}],\"lunch\":[...],\"dinner\":[...],\"snack\":[...]}"
        var o = ask(prompt, lang, maxTokens = 1500)?.objOrNull()
        if (o == null) o = ask(prompt, lang, maxTokens = 1500)?.objOrNull()  // one retry
        if (o == null) return emptyMap()
        val meals = listOf("breakfast", "lunch", "dinner", "snack")
        return meals.associateWith { m -> (o[m] as? JsonArray)?.let { parseSuggestions(it) } ?: emptyList() }
            .filterValues { it.isNotEmpty() }
    }

    suspend fun activities(ageMonths: Int, lang: String): List<SuggestionItem> {
        // EXERCISE page only: physical movement / gross-motor activity (being active & strong).
        // Keep this distinct from the พัฒนาการ developmental-skill tasks (language/cognitive/social).
        val prompt = "Suggest 3 safe, age-appropriate PHYSICAL EXERCISE / movement activities " +
            "(gross-motor — e.g. tummy time, crawling, walking practice, ball play, dancing) that keep a " +
            "$ageMonths-month-old child active and physically strong. " +
            "Do NOT include quiet learning, reading, or talking tasks. " +
            "Return JSON array: [{\"title\":..,\"subtitle\":..,\"tags\":[..]}] where one tag is a duration like \"10 min\"."
        return parseSuggestions(ask(prompt, lang) ?: return emptyList())
    }

    private fun parseSuggestions(el: JsonElement): List<SuggestionItem> {
        val arr = (el as? JsonArray) ?: (el.objOrNull()?.values?.firstOrNull() as? JsonArray) ?: return emptyList()
        return arr.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val title = (o["title"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            SuggestionItem(title, o.str("subtitle"), (o["tags"] as? JsonArray)?.mapNotNull { it.asText() } ?: emptyList())
        }
    }

    // ---------------- Detail content ----------------

    suspend fun generateRecipe(foodName: String, ageMonths: Int, lang: String): RecipeContent? {
        val prompt = "Create a simple, safe recipe of \"$foodName\" for a $ageMonths-month-old child. " +
            "Return JSON: {\"title\":..,\"timeMinutes\":..,\"ageRange\":..,\"servings\":..,\"nutritionNote\":..,\"ingredients\":[..],\"steps\":[..]}"
        val o = ask(prompt, lang, maxTokens = 1500)?.objOrNull() ?: return null
        return RecipeContent(
            title = o.str("title", foodName),
            timeMinutes = o.str("timeMinutes"),
            ageRange = o.str("ageRange"),
            servings = o.str("servings"),
            nutritionNote = o.str("nutritionNote"),
            ingredients = o.strList("ingredients"),
            steps = o.strList("steps")
        )
    }

    suspend fun generateExercise(activityName: String, ageMonths: Int, lang: String): ExerciseContent? {
        val prompt = "Explain how a parent safely does the activity \"$activityName\" with a $ageMonths-month-old child. " +
            "Return JSON: {\"title\":..,\"prepTime\":..,\"duration\":..,\"ageRange\":..,\"difficulty\":..,\"benefit\":..,\"equipment\":[..],\"steps\":[..]}"
        val o = ask(prompt, lang, maxTokens = 1500)?.objOrNull() ?: return null
        return ExerciseContent(
            title = o.str("title", activityName),
            prepTime = o.str("prepTime"),
            duration = o.str("duration"),
            ageRange = o.str("ageRange"),
            difficulty = o.str("difficulty"),
            benefit = o.str("benefit"),
            equipment = o.strList("equipment"),
            steps = o.strList("steps")
        )
    }

    // ---------------- helpers ----------------

    private fun JsonElement.objOrNull(): JsonObject? = this as? JsonObject

    /** Safe string read: never throws on a non-primitive value. */
    private fun JsonObject.str(key: String, default: String = ""): String =
        (this[key] as? JsonPrimitive)?.contentOrNull ?: default

    /**
     * Safe string-list read. The AI sometimes returns array items as OBJECTS (e.g.
     * {"name":"แครอท","amount":"50 กรัม"} or {"step":"..."}) instead of plain strings — calling
     * `.jsonPrimitive` on those throws and crashed the recipe screen. This flattens any shape to a
     * readable line and skips nulls.
     */
    private fun JsonObject.strList(key: String): List<String> =
        (this[key] as? JsonArray)?.mapNotNull { it.asText() } ?: emptyList()

    /** Render any JSON element as a single readable line (primitive → text; object → its values joined). */
    private fun JsonElement.asText(): String? = when (this) {
        is JsonPrimitive -> contentOrNull
        is JsonObject -> values.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            .joinToString(" ").ifBlank { null }
        else -> null
    }
}
