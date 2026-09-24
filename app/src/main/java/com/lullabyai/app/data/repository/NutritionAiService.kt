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

    private suspend fun ask(
        prompt: String, lang: String, maxTokens: Int = 1024, useSearch: Boolean = false,
        imageBase64: String? = null, imageMimeType: String? = null
    ): JsonElement? {
        val raw = ClaudeClient.generateContent(
            prompt, useSearch = useSearch, maxTokens = maxTokens, system = sys(lang),
            imageBase64 = imageBase64, imageMimeType = imageMimeType
        )
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
    suspend fun estimateFood(item: String, ageMonths: Int, hour: Int, lang: String, portion: String? = null): Pair<Int, String>? {
        val portionClause = if (!portion.isNullOrBlank()) {
            "The parent reports the ACTUAL portion eaten as: \"$portion\" — base the kcal estimate on THIS portion size " +
                "(not a generic default), scaled for a $ageMonths-month-old's typical calorie density for this food. "
        } else {
            "No portion was specified, so estimate the SMALL CHILD-SIZED serving that a $ageMonths-month-old " +
                "actually eats — a toddler portion is a fraction of an adult portion, so the number should be small " +
                "(a 0-3 year old's whole day is only ~550-1200 kcal). "
        }
        val prompt = "Estimate calories (kcal) of \"$item\". $portionClause" +
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

    /** Identifies the food in a photo and estimates its kcal + meal type — same "actual portion vs
     *  default toddler serving" logic as [estimateFood], but the AI reads the portion from the image
     *  itself when no [portion] override is given. Null on failure (unreadable image, no food visible). */
    data class FoodPhotoEstimate(val itemName: String, val kcal: Int, val meal: String)

    suspend fun estimateFoodFromPhoto(imageBase64: String, imageMimeType: String, ageMonths: Int, hour: Int, lang: String, portion: String? = null): FoodPhotoEstimate? {
        val portionClause = if (!portion.isNullOrBlank()) {
            "The parent reports the ACTUAL portion eaten as: \"$portion\" — use that portion size, not what the photo alone suggests. "
        } else {
            "Estimate the portion size directly from what's visible in the photo (the actual amount on the plate/bowl, " +
                "not a generic default), scaled for a $ageMonths-month-old's typical calorie density for this food. "
        }
        val prompt = "This photo shows a food or meal a young child (0-3 years old) is eating. Identify the dish and " +
            "estimate its calories (kcal). $portionClause" +
            "Also assign a meal type given the current hour is $hour (0-23). " +
            "meal must be exactly one of: breakfast, lunch, dinner, snack, other. " +
            "If no food is clearly visible, return {\"itemName\":null}. " +
            "Return JSON exactly: {\"itemName\":\"...\", \"kcal\":int, \"meal\":\"...\"}"
        val o = ask(prompt, lang, imageBase64 = imageBase64, imageMimeType = imageMimeType)?.objOrNull() ?: return null
        val name = (o["itemName"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() } ?: return null
        val kcal = (o["kcal"] as? JsonPrimitive)?.intOrNull ?: return null
        val meal = (o["meal"] as? JsonPrimitive)?.contentOrNull?.lowercase()?.takeIf {
            it in setOf("breakfast", "lunch", "dinner", "snack", "other")
        } ?: "other"
        return FoodPhotoEstimate(name, kcal, meal)
    }

    data class CalorieCheck(val kcal: Int, val carb: Int, val protein: Int, val fat: Int, val tip: String, val itemName: String? = null)

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

    /** Same as [calorieCheck] but the dish is identified from a photo instead of typed text.
     *  Null if no food is clearly visible in the photo. */
    suspend fun calorieCheckFromPhoto(imageBase64: String, imageMimeType: String, lang: String): CalorieCheck? {
        val prompt = "This photo shows a food or meal a young child (0-3 years old) is eating. Identify the dish, " +
            "then estimate the TOTAL calories (kcal) and macronutrients in grams for it. " +
            "Also give ONE short, practical healthy-eating tip. " +
            "If no food is clearly visible, return {\"itemName\":null}. " +
            "Return JSON exactly: {\"itemName\":\"...\",\"kcal\":int,\"carb\":int,\"protein\":int,\"fat\":int,\"tip\":\"...\"}"
        val o = ask(prompt, lang, imageBase64 = imageBase64, imageMimeType = imageMimeType)?.objOrNull() ?: return null
        val name = (o["itemName"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() } ?: return null
        val kcal = (o["kcal"] as? JsonPrimitive)?.intOrNull ?: return null
        return CalorieCheck(
            kcal = kcal,
            carb = (o["carb"] as? JsonPrimitive)?.intOrNull ?: 0,
            protein = (o["protein"] as? JsonPrimitive)?.intOrNull ?: 0,
            fat = (o["fat"] as? JsonPrimitive)?.intOrNull ?: 0,
            tip = o.str("tip"),
            itemName = name
        )
    }

    /** Estimated TOTAL nutrients for today's foods: macros in grams + calcium/iron in mg. */
    data class MacroEstimate(val carb: Int, val protein: Int, val fat: Int, val calciumMg: Int, val ironMg: Int)

    /** Estimated TOTAL macronutrients + calcium/iron for today's foods. Null on failure. */
    suspend fun estimateMacros(items: List<String>, ageMonths: Int, lang: String): MacroEstimate? {
        if (items.isEmpty()) return null
        val list = items.joinToString(", ")
        val prompt = "Estimate the TOTAL macronutrients (grams) and calcium/iron (mg) for these foods eaten by a $ageMonths-month-old child: $list. " +
            "Return JSON exactly: {\"carb\":int,\"protein\":int,\"fat\":int,\"calciumMg\":int,\"ironMg\":int}"
        val o = ask(prompt, lang, useSearch = true)?.objOrNull() ?: return null
        val c = (o["carb"] as? JsonPrimitive)?.intOrNull ?: return null
        val p = (o["protein"] as? JsonPrimitive)?.intOrNull ?: 0
        val f = (o["fat"] as? JsonPrimitive)?.intOrNull ?: 0
        val ca = (o["calciumMg"] as? JsonPrimitive)?.intOrNull ?: 0
        val fe = (o["ironMg"] as? JsonPrimitive)?.intOrNull ?: 0
        return MacroEstimate(c, p, f, ca, fe)
    }

    /** Qualitative status ("ok"|"boost"|"low") for micronutrients not tracked with numeric targets:
     *  Vitamin A/C/D/B12, Zinc, DHA. Estimated from today's logged foods — a rough AI read, not a lab value. */
    suspend fun estimateMicronutrients(items: List<String>, ageMonths: Int, lang: String): Map<String, String>? {
        if (items.isEmpty()) return null
        val list = items.joinToString(", ")
        val prompt = "For a $ageMonths-month-old child who today ate: $list — judge whether daily intake of each of " +
            "these nutrients looks sufficient so far, based ONLY on typical nutrient content of these foods: " +
            "Vitamin A, Vitamin C, Vitamin D, Vitamin B12, Zinc, DHA/Omega-3. " +
            "For each, return exactly one of: \"ok\", \"boost\", \"low\". " +
            "Return JSON exactly: {\"A\":\"...\",\"C\":\"...\",\"D\":\"...\",\"B12\":\"...\",\"Zn\":\"...\",\"DHA\":\"...\"}"
        val o = ask(prompt, lang, useSearch = true)?.objOrNull() ?: return null
        val keys = listOf("A", "C", "D", "B12", "Zn", "DHA")
        return keys.associateWith { k -> (o[k] as? JsonPrimitive)?.contentOrNull?.takeIf { it in setOf("ok", "boost", "low") } ?: "ok" }
    }

    /** Keys the nutrition statistics page asks for, same names as assets/nutrition/thai_fct_2561.json. */
    val PER100_KEYS = listOf(
        "kcal", "protein", "fat", "carb", "calcium", "iron", "zinc",
        "vitaminA", "vitaminC", "thiamin", "riboflavin", "niacin", "sodium"
    )

    /**
     * Fallback for foods that are not in the Thai food composition table: typical composition PER
     * 100 g of each named food, in the table's own keys and units, so the page's arithmetic is the
     * same for both sources. A key the model leaves out stays unknown — it is not filled with 0.
     * Returns only the foods it could read; an empty map means the call failed.
     */
    suspend fun estimatePer100g(foods: List<String>, lang: String): Map<String, Map<String, Double>> {
        if (foods.isEmpty()) return emptyMap()
        val list = foods.mapIndexed { i, f -> "${i + 1}. $f" }.joinToString("\n")
        val prompt = "Typical nutrient composition PER 100 g edible portion of each Thai food below, as eaten " +
            "(cooked dishes as served). Use Thai food composition data where you know it.\n$list\n" +
            "Units: kcal; protein/fat/carb in g; calcium/iron/zinc/sodium in mg; vitaminA in mcg RAE; " +
            "vitaminC/thiamin/riboflavin/niacin in mg. Omit a key you cannot estimate instead of guessing 0. " +
            "Return JSON exactly: {\"items\":[{\"name\":\"<as given>\",\"kcal\":n,\"protein\":n,\"fat\":n,\"carb\":n," +
            "\"calcium\":n,\"iron\":n,\"zinc\":n,\"vitaminA\":n,\"vitaminC\":n,\"thiamin\":n,\"riboflavin\":n,\"niacin\":n,\"sodium\":n}]}"
        val items = ask(prompt, lang, maxTokens = 2048)?.objOrNull()?.get("items") as? JsonArray ?: return emptyMap()
        return items.mapIndexedNotNull { i, e ->
            val o = e as? JsonObject ?: return@mapIndexedNotNull null
            val name = (o["name"] as? JsonPrimitive)?.contentOrNull?.takeIf { it in foods } ?: foods.getOrNull(i)
                ?: return@mapIndexedNotNull null
            val values = PER100_KEYS.mapNotNull { k -> (o[k] as? JsonPrimitive)?.doubleOrNull?.takeIf { it >= 0 }?.let { k to it } }.toMap()
            if (values["kcal"] == null) null else name to values
        }.toMap()
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

    // ---------------- Smart Refrigerator & Ingredients Scanner ----------------

    /** Scans a refrigerator / pantry photo and detects available ingredients. Returns list of ingredient names. */
    suspend fun scanFridgeIngredientsFromPhoto(imageBase64: String, imageMimeType: String, lang: String): List<String> {
        val prompt = "This is a photo inside a refrigerator, kitchen pantry, or food preparation area. " +
            "Carefully detect and list all visible raw ingredients, fresh produce, vegetables, fruits, dairy, eggs, meats, or staple foods. " +
            "Return JSON exactly: {\"ingredients\":[\"บรอกโคลี\",\"นมสด\",\"แอปเปิ้ล\",\"แครอท\",\"ไข่ไก่\"]}. " +
            "If no ingredients or foods are identifiable, return {\"ingredients\":[]}."
        val obj = ask(prompt, lang, maxTokens = 1024, imageBase64 = imageBase64, imageMimeType = imageMimeType)?.objOrNull() ?: return emptyList()
        return (obj["ingredients"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { s -> s.isNotBlank() } } ?: emptyList()
    }

    /** Recommend several simple, low-cost home-style dishes (not split by meal), incorporating available fridge ingredients if any. */
    suspend fun recommendBudgetMenus(ageMonths: Int, lang: String, availableIngredients: List<String> = emptyList()): List<SuggestionItem> {
        val fridgeClause = if (availableIngredients.isNotEmpty()) {
            "The family currently has these ingredients at home in their refrigerator: [${availableIngredients.joinToString(", ")}]. " +
            "PRIORITIZE 5 dishes that can be cooked using these available ingredients. " +
            "For dishes matching the fridge ingredients, add the tag \"มีวัตถุดิบครบ\" or \"ใช้วัตถุดิบในบ้าน\" (or \"In Fridge\" in English). "
        } else ""
        val prompt = "Suggest 5 simple, LOW-COST, everyday home-style dishes suitable for a $ageMonths-month-old toddler. $fridgeClause" +
            "Focus on affordable Thai-family staples parents can cook at home (e.g. rice porridge, soft rice, boiled egg, mashed banana/pumpkin, simple vegetable soup, minced pork/rice). " +
            "Do NOT suggest luxury, restaurant, imported, or expensive items (no steak, salmon, cheese boards, fancy desserts, etc.). " +
            "subtitle = one short line on nutrition benefit AND that it is budget-friendly. " +
            "tags = short nutrient symbols (e.g. Fe, Ca, A, P) plus optional cook time like \"15 min\" and difficulty like \"easy\" or \"มีวัตถุดิบครบ\". " +
            "Return JSON array: [{\"title\":\"...\",\"subtitle\":\"...\",\"tags\":[\"Fe\",\"15 min\",\"easy\"]}]"
        return parseSuggestions(ask(prompt, lang) ?: return emptyList())
    }

    /** Recommend dishes targeted at boosting specific nutrients the child is currently short on
     *  (e.g. "iron", "calcium", "protein", "carbohydrates", "fat"), still low-cost/home-style, incorporating available fridge items. */
    suspend fun recommendMenusForNeeds(needs: List<String>, ageMonths: Int, lang: String, availableIngredients: List<String> = emptyList()): List<SuggestionItem> {
        if (needs.isEmpty()) return recommendBudgetMenus(ageMonths, lang, availableIngredients)
        val list = needs.joinToString(", ")
        val fridgeClause = if (availableIngredients.isNotEmpty()) {
            "The family currently has these ingredients in their refrigerator: [${availableIngredients.joinToString(", ")}]. " +
            "PRIORITIZE dishes that can be cooked using these available ingredients to boost the target nutrients. " +
            "For dishes matching the fridge ingredients, add the tag \"มีวัตถุดิบครบ\" or \"ใช้วัตถุดิบในบ้าน\" (or \"In Fridge\" in English). "
        } else ""
        val prompt = "Suggest 5 simple, LOW-COST, everyday home-style dishes suitable for a $ageMonths-month-old toddler, " +
            "specifically chosen to help boost intake of: $list. $fridgeClause" +
            "Focus on affordable Thai-family staples parents can cook at home. " +
            "Do NOT suggest luxury, restaurant, imported, or expensive items. " +
            "subtitle = one short line naming which of these target nutrients this dish helps with, AND that it is budget-friendly. " +
            "tags = short nutrient symbols for the target nutrients (Fe/Ca/A/P) plus optional \"15 min\" and \"easy\" or \"มีวัตถุดิบครบ\". " +
            "Return JSON array: [{\"title\":\"...\",\"subtitle\":\"...\",\"tags\":[\"Fe\",\"Ca\",\"15 min\"]}]"
        return parseSuggestions(ask(prompt, lang) ?: return emptyList())
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
        val isThai = lang.startsWith("th")
        val prompt = "Create a simple, low-cost, safe home recipe of \"$foodName\" for a $ageMonths-month-old child. " +
            "Use common affordable ingredients; keep steps easy for busy parents. " +
            "Estimate nutritional values per child portion: caloriesKcal (integer), proteinG (grams float), carbG (grams float), fatG (grams float), calciumMg (mg int), and ironMg (mg float). " +
            "Also list 2-3 key growth & health benefits for the child in 'benefits', " +
            "and suggest a cooking tutorial search title in 'videoTitle' (e.g. \"วิธีทำ $foodName สำหรับเด็กเล็ก\" or \"How to cook $foodName for toddler\"). " +
            "Return JSON: {\"title\":..,\"timeMinutes\":..,\"ageRange\":..,\"servings\":..,\"nutritionNote\":..,\"ingredients\":[..],\"steps\":[..]," +
            "\"caloriesKcal\":int,\"proteinG\":float,\"carbG\":float,\"fatG\":float,\"calciumMg\":int,\"ironMg\":float,\"benefits\":[..],\"videoTitle\":..}"
        val o = ask(prompt, lang, maxTokens = 1800)?.objOrNull() ?: return null
        val title = o.str("title", foodName)
        val vTitle = o.str("videoTitle").ifBlank { if (isThai) "วิธีทำ $title สำหรับเด็ก" else "How to cook $title for toddlers" }
        val encodedQuery = java.net.URLEncoder.encode(vTitle, "UTF-8")
        val videoUrl = "https://www.youtube.com/results?search_query=$encodedQuery"

        fun parseNum(key: String): Double? {
            val prim = o[key] as? JsonPrimitive ?: return null
            return prim.doubleOrNull ?: prim.contentOrNull?.replace(Regex("[^0-9.]"), "")?.toDoubleOrNull()
        }

        val kcal = parseNum("caloriesKcal")?.toInt() ?: parseNum("kcal")?.toInt()
        val protein = parseNum("proteinG") ?: parseNum("protein")
        val carb = parseNum("carbG") ?: parseNum("carb")
        val fat = parseNum("fatG") ?: parseNum("fat")
        val calcium = parseNum("calciumMg")?.toInt() ?: parseNum("calcium")?.toInt()
        val iron = parseNum("ironMg") ?: parseNum("iron")

        return RecipeContent(
            title = title,
            timeMinutes = o.str("timeMinutes"),
            ageRange = o.str("ageRange"),
            servings = o.str("servings"),
            nutritionNote = o.str("nutritionNote"),
            ingredients = o.strList("ingredients"),
            steps = o.strList("steps"),
            caloriesKcal = kcal,
            proteinG = protein,
            carbG = carb,
            fatG = fat,
            calciumMg = calcium,
            ironMg = iron,
            benefits = o.strList("benefits"),
            videoUrl = videoUrl,
            videoTitle = vTitle
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
