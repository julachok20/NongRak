package com.lullabyai.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lullabyai.app.data.model.Child
import com.lullabyai.app.data.model.ExerciseContent
import com.lullabyai.app.data.model.ExerciseLog
import com.lullabyai.app.data.model.FoodLog
import com.lullabyai.app.data.model.RecipeContent
import com.lullabyai.app.data.model.SuggestionItem
import com.lullabyai.app.data.repository.ChildRepository
import com.lullabyai.app.data.repository.NutritionAiService
import com.lullabyai.app.data.repository.NutritionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.Period

data class NutritionUiState(
    val loading: Boolean = false,
    val child: Child? = null,
    val foods: List<FoodLog> = emptyList(),
    val todayMilkMl: Int = 0,
    val summary: NutritionRepository.DailySummary? = null,
    val mealSuggestion: SuggestionItem? = null,
    val recommendedFoods: List<SuggestionItem> = emptyList(),
    val mealPlan: Map<String, List<SuggestionItem>> = emptyMap(),
    val mealPlanLoading: Boolean = false,
    val activities: List<SuggestionItem> = emptyList(),
    val activitiesLoading: Boolean = false,
    // Simple budget-friendly menu ideas for the child (flat list — not split by meal).
    val menuRecs: List<SuggestionItem> = emptyList(),
    val menuRecsLoading: Boolean = false,
    val exerciseLogs: List<ExerciseLog> = emptyList(),
    val weeklyMinutes: Int = 0,
    val calculating: Boolean = false,
    val targetKcal: Int = 0,                       // age-based recommended daily intake
    val macros: NutritionAiService.MacroEstimate? = null,   // total carb/protein/fat (g) + calcium/iron (mg) for today
    val targetMacros: Triple<Int, Int, Int> = Triple(0, 0, 0),  // age-based daily target: carb, protein, fat (grams)
    val targetCalciumMg: Int = 0,
    val targetIronMg: Int = 0,
    val macrosLoading: Boolean = false,
    val microStatus: Map<String, String>? = null,  // qualitative status for A/C/D/B12/Zn/DHA ("ok"|"boost"|"low")
    val microStatusLoading: Boolean = false,
    val photoAddFailed: Boolean = false,  // last camera-add couldn't identify a food in the photo
    // Parent calorie-check tool (standalone): type a dish → kcal + macros + tip.
    val calorieCheck: NutritionAiService.CalorieCheck? = null,
    val checkedFood: String = "",
    val checkLoading: Boolean = false,
    val checkPhotoFailed: Boolean = false,  // last calorie-check photo couldn't identify a food
    val recipe: RecipeContent? = null,
    val recipeLoading: Boolean = false,
    val exercise: ExerciseContent? = null,
    val exerciseLoading: Boolean = false,
    val currentExerciseName: String? = null,  // the activity name requested (for reliable done-matching)
    val error: String? = null
)

class NutritionViewModel : ViewModel() {

    private val childRepo = ChildRepository()
    private val repo = NutritionRepository()

    val weeklyGoalMinutes = 60

    private val _ui = MutableStateFlow(NutritionUiState())
    val ui: StateFlow<NutritionUiState> = _ui

    private fun ageMonths(child: Child?): Int {
        val bd = child?.birthdate ?: return 0
        return runCatching {
            val p = Period.between(LocalDate.parse(bd), LocalDate.now())
            p.years * 12 + p.months
        }.getOrDefault(0)
    }

    /** Rough age-based recommended daily energy intake (kcal) for 0–3y, per standard guidance. */
    private fun dailyTargetKcal(child: Child?): Int = when (ageMonths(child)) {
        in 0..5 -> 550
        in 6..11 -> 750
        in 12..23 -> 1000
        else -> 1200
    }

    /** Rough age-based daily macronutrient targets in grams (carb, protein, fat), same age
     *  brackets as [dailyTargetKcal]. NOT equally sourced across the three:
     *  - Protein (13g/day) and carbs (130g/day) for ages 1-3 ARE the official U.S. National
     *    Academies (IOM) Dietary Reference Intake / RDA figures.
     *  - The fat-gram numbers are OUR OWN back-calculation from "30-40% of daily calories" —
     *    official guidance states fat as a %-of-energy range, not a gram target, so these are an
     *    approximation layered on top of a real guideline, not themselves an official figure.
     *  - The 0-11 month bracket (all three numbers) is a rough estimate scaled from breastmilk
     *    composition, not pulled from an official table.
     *  None of this is a per-child clinical prescription — same "rough reference" caveat as the
     *  milk target elsewhere on this screen. */
    private fun dailyTargetMacros(child: Child?): Triple<Int, Int, Int> = when (ageMonths(child)) {
        in 0..5 -> Triple(60, 9, 31)
        in 6..11 -> Triple(95, 11, 30)
        in 12..23 -> Triple(130, 13, 35)
        else -> Triple(150, 13, 40)
    }

    /** Official US NAS/IOM Dietary Reference Intake (calcium AI / iron RDA), in mg/day.
     *  0-6mo iron is the real AI figure (0.27mg, rounds to 0) — breastfed infants aren't expected
     *  to need iron intake before 6mo (body iron stores from birth cover it), not a data gap. */
    private fun dailyTargetMinerals(child: Child?): Pair<Int, Int> = when (ageMonths(child)) {
        in 0..5 -> 200 to 0
        in 6..11 -> 260 to 11
        else -> 700 to 7   // IOM's "1-3 years" DRI band covers 12-36mo as one bracket
    }

    // Tracks the language the AI suggestions were generated in. Suggestions are generated ONCE and
    // then kept (no auto-refresh) per the product decision — we only (re)generate if we don't have
    // them yet, or if the user explicitly switched language.
    private var suggestionsLang: String? = null

    /** Loads child, today's logs, summary, and (once) the AI suggestions. */
    fun load(lang: String) {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(loading = true)
            val child = childRepo.getSelectedChild().getOrNull()
            // If the active child changed, drop the previous child's AI content so it doesn't show stale.
            if (child?.id != _ui.value.child?.id) {
                suggestionsLang = null
                menuRecsKey = null
                _ui.value = _ui.value.copy(
                    activities = emptyList(), menuRecs = emptyList(),
                    mealSuggestion = null, mealPlan = emptyMap()
                )
            }
            val foods = child?.let { repo.getFoodLogs(it.id) } ?: emptyList()
            val ex = child?.let { repo.getExerciseLogs(it.id) } ?: emptyList()
            val weekly = child?.let { repo.getWeeklyExercise(it.id).sumOf { e -> e.durationMin ?: 0 } } ?: 0
            val summary = child?.let { repo.getDailySummary(it.id) }
            val today = LocalDate.now().toString()
            val milkMl = child?.let { c ->
                repo.getMilkLogs(c.id).filter { com.lullabyai.app.util.TimeUtils.localDate(it.loggedAt) == today }.sumOf { it.volumeMl }
            } ?: 0
            val (targetCa, targetFe) = dailyTargetMinerals(child)
            _ui.value = _ui.value.copy(
                loading = false, child = child, foods = foods, todayMilkMl = milkMl,
                exerciseLogs = ex, weeklyMinutes = weekly, summary = summary,
                targetKcal = dailyTargetKcal(child), targetMacros = dailyTargetMacros(child),
                targetCalciumMg = targetCa, targetIronMg = targetFe
            )
            ensureSuggestions(lang)
            loadMacros(lang)
        }
    }

    /**
     * Generates the exercise "activities" list exactly ONCE and keeps it (no refresh on every open).
     * Re-runs only if we don't have it yet (so a failed/empty fetch retries next time, fixing the
     * "stuck loading forever" case), on an explicit retry [force], or when the UI language changed.
     * The Nutrition page is now driven purely by the user's logged food, so we no longer generate an
     * AI meal plan / meal suggestion here.
     */
    fun ensureSuggestions(lang: String, force: Boolean = false) {
        val months = ageMonths(_ui.value.child)
        val langChanged = suggestionsLang != null && suggestionsLang != lang
        val refetch = force || langChanged
        suggestionsLang = lang

        if (refetch || _ui.value.activities.isEmpty()) {
            viewModelScope.launch {
                _ui.value = _ui.value.copy(activitiesLoading = true)
                val acts = NutritionAiService.activities(months, lang)
                _ui.value = _ui.value.copy(activities = acts, activitiesLoading = false)
            }
        }
    }

    /** Which of the 5 tracked nutrients are currently under 90% of the child's daily target. */
    fun deficientNutrients(): List<String> {
        val m = _ui.value.macros ?: return emptyList()
        val (targetCarb, targetProtein, targetFat) = _ui.value.targetMacros
        val list = mutableListOf<String>()
        if (m.protein < targetProtein.coerceAtLeast(1) * 0.9f) list += "protein"
        if (m.carb < targetCarb.coerceAtLeast(1) * 0.9f) list += "carbohydrates"
        if (m.fat < targetFat.coerceAtLeast(1) * 0.9f) list += "fat"
        if (m.calciumMg < _ui.value.targetCalciumMg.coerceAtLeast(1) * 0.9f) list += "calcium"
        if (m.ironMg < _ui.value.targetIronMg.coerceAtLeast(1) * 0.9f) list += "iron"
        return list
    }

    private var menuRecsKey: String? = null

    /** Loads dish ideas for the child's age — targeted at whichever nutrients are currently under
     *  target (calcium/iron/protein/carbs/fat), or a generic budget-friendly list if none are (or
     *  today's macros haven't loaded yet). Re-fetches when the deficiency set actually changes. */
    fun ensureMenuRecommendations(lang: String) {
        val child = _ui.value.child ?: return
        val months = ageMonths(child)
        val needs = deficientNutrients()
        val key = "$lang|$months|${needs.joinToString(",")}"
        if (key == menuRecsKey) return
        menuRecsKey = key
        viewModelScope.launch {
            _ui.value = _ui.value.copy(menuRecsLoading = true)
            val recs = if (needs.isNotEmpty()) NutritionAiService.recommendMenusForNeeds(needs, months, lang)
                       else NutritionAiService.recommendBudgetMenus(months, lang)
            _ui.value = _ui.value.copy(menuRecs = recs, menuRecsLoading = false)
        }
    }

    private fun reloadLogs() {
        viewModelScope.launch {
            val child = _ui.value.child ?: return@launch
            val foods = repo.getFoodLogs(child.id)
            val summary = repo.getDailySummary(child.id)
            _ui.value = _ui.value.copy(foods = foods, summary = summary)
        }
    }

    fun addFood(mealType: String, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            _ui.value.child?.let { repo.addFood(it.id, mealType, name.trim()) }
            reloadLogs()
        }
    }

    /** Search-add flow: AI estimates kcal (from [portion] if given, else a default toddler serving)
     *  + assigns a meal from the time of day, then inserts. [spillFraction] (0f-1f) scales the
     *  estimated kcal down when the parent reports the child spilled/spat out part of the food. */
    fun addFoodSmart(name: String, lang: String, portion: String = "", spillFraction: Float = 0f) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val child = _ui.value.child ?: return@launch
            _ui.value = _ui.value.copy(calculating = true)
            val hour = java.time.LocalTime.now().hour
            val est = NutritionAiService.estimateFood(name.trim(), ageMonths(child), hour, lang, portion.takeIf { it.isNotBlank() })
            val kcal = est?.first?.let { (it * (1f - spillFraction.coerceIn(0f, 1f))).toInt() }
            repo.addFood(child.id, est?.second ?: "other", name.trim(), kcal)
            val foods = repo.getFoodLogs(child.id)
            val summary = repo.getDailySummary(child.id)
            _ui.value = _ui.value.copy(foods = foods, summary = summary, calculating = false)
        }
    }

    /** Camera-add flow: AI identifies the food from a photo (base64 JPEG) and estimates kcal —
     *  same [portion]/[spillFraction] handling as [addFoodSmart]. Sets [NutritionUiState.photoAddFailed]
     *  if no food could be identified in the photo, instead of silently doing nothing. */
    fun addFoodFromPhoto(imageBytes: ByteArray, lang: String, portion: String = "", spillFraction: Float = 0f) {
        viewModelScope.launch {
            val child = _ui.value.child ?: return@launch
            _ui.value = _ui.value.copy(calculating = true, photoAddFailed = false)
            val hour = java.time.LocalTime.now().hour
            val imageBase64 = android.util.Base64.encodeToString(imageBytes, android.util.Base64.NO_WRAP)
            val est = NutritionAiService.estimateFoodFromPhoto(imageBase64, "image/jpeg", ageMonths(child), hour, lang, portion.takeIf { it.isNotBlank() })
            if (est == null) {
                _ui.value = _ui.value.copy(calculating = false, photoAddFailed = true)
                return@launch
            }
            val kcal = (est.kcal * (1f - spillFraction.coerceIn(0f, 1f))).toInt()
            repo.addFood(child.id, est.meal, est.itemName, kcal)
            val foods = repo.getFoodLogs(child.id)
            val summary = repo.getDailySummary(child.id)
            _ui.value = _ui.value.copy(foods = foods, summary = summary, calculating = false)
        }
    }

    // Macros are AI-estimated for the day's logged foods, cached by the food set so we don't re-call
    // the AI on every screen open.
    private var macrosKey: String? = null
    fun loadMacros(lang: String) {
        val child = _ui.value.child ?: return
        val foods = _ui.value.foods
        val milkMl = _ui.value.todayMilkMl
        if (foods.isEmpty() && milkMl <= 0) { _ui.value = _ui.value.copy(macros = null); macrosKey = null; return }
        // Milk isn't a food_logs row, so it has to be folded in here as a synthetic item, or the AI
        // never sees it and a milk-only day reports zero protein/calcium/etc — same bug class as the
        // old foods.isEmpty() early-return above.
        val items = foods.map { it.itemName } + if (milkMl > 0) listOf("นมแม่/นมผง ${milkMl}ml") else emptyList()
        val key = items.joinToString("|") + "|$lang"
        if (key == macrosKey) return
        macrosKey = key
        viewModelScope.launch {
            _ui.value = _ui.value.copy(macrosLoading = true)
            val m = NutritionAiService.estimateMacros(items, ageMonths(child), lang)
            _ui.value = _ui.value.copy(macros = m, macrosLoading = false)
        }
    }

    // Micronutrient status is only needed on the detailed stats page — loaded separately from the
    // macros above so the main Nutrition page doesn't pay for an AI call it doesn't show.
    private var microKey: String? = null
    fun loadMicronutrients(lang: String) {
        val child = _ui.value.child ?: return
        val foods = _ui.value.foods
        val milkMl = _ui.value.todayMilkMl
        if (foods.isEmpty() && milkMl <= 0) { _ui.value = _ui.value.copy(microStatus = null); microKey = null; return }
        val items = foods.map { it.itemName } + if (milkMl > 0) listOf("นมแม่/นมผง ${milkMl}ml") else emptyList()
        val key = items.joinToString("|") + "|$lang"
        if (key == microKey) return
        microKey = key
        viewModelScope.launch {
            _ui.value = _ui.value.copy(microStatusLoading = true)
            val m = NutritionAiService.estimateMicronutrients(items, ageMonths(child), lang)
            _ui.value = _ui.value.copy(microStatus = m, microStatusLoading = false)
        }
    }

    /** Parent calorie-check: estimate kcal + macros + tip for any typed dish. */
    fun runCalorieCheck(food: String, lang: String) {
        if (food.isBlank()) return
        viewModelScope.launch {
            _ui.value = _ui.value.copy(checkLoading = true, checkedFood = food.trim())
            val r = NutritionAiService.calorieCheck(food.trim(), lang)
            _ui.value = _ui.value.copy(calorieCheck = r, checkLoading = false)
        }
    }

    /** Same as [runCalorieCheck] but the dish is identified from a camera photo (base64 JPEG). */
    fun runCalorieCheckFromPhoto(imageBytes: ByteArray, lang: String) {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(checkLoading = true, checkPhotoFailed = false)
            val imageBase64 = android.util.Base64.encodeToString(imageBytes, android.util.Base64.NO_WRAP)
            val r = NutritionAiService.calorieCheckFromPhoto(imageBase64, "image/jpeg", lang)
            if (r == null) {
                _ui.value = _ui.value.copy(checkLoading = false, checkPhotoFailed = true)
                return@launch
            }
            _ui.value = _ui.value.copy(calorieCheck = r, checkedFood = r.itemName ?: "", checkLoading = false)
        }
    }

    /** Recompute the summary (picks up newly-logged exercise burned calories). */
    fun refreshSummary() {
        viewModelScope.launch {
            val child = _ui.value.child ?: return@launch
            _ui.value = _ui.value.copy(summary = repo.getDailySummary(child.id))
        }
    }

    fun editFood(id: String, name: String) {
        viewModelScope.launch { repo.updateFood(id, name.trim()); reloadLogs() }
    }

    fun deleteFood(id: String) {
        viewModelScope.launch { repo.deleteFood(id); reloadLogs() }
    }

    /** AI-estimate kcal for every logged food, persist, then refresh the summary. */
    fun calculateCalories(lang: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            val child = _ui.value.child ?: return@launch
            val foods = _ui.value.foods
            if (foods.isEmpty()) { onDone(); return@launch }
            _ui.value = _ui.value.copy(calculating = true)
            val kcal = NutritionAiService.estimateCalories(foods.map { it.itemName }, ageMonths(child), lang)
            kcal.forEachIndexed { i, c -> foods.getOrNull(i)?.let { repo.setFoodCalories(it.id, c) } }
            val foodsNew = repo.getFoodLogs(child.id)
            val summary = repo.getDailySummary(child.id)
            _ui.value = _ui.value.copy(foods = foodsNew, summary = summary, calculating = false)
            onDone()
        }
    }

    /** Log an exercise session; AI estimates calories burned. date/mood/notes are for the manual
     *  "Log Exercise" form — AI-suggested-activity logging (same day, no mood/notes) omits them. */
    fun logExercise(
        activity: String, durationMin: Int, lang: String,
        date: String? = null, mood: String? = null, notes: String? = null
    ) {
        viewModelScope.launch {
            val child = _ui.value.child ?: return@launch
            val burn = NutritionAiService.estimateBurn(activity, durationMin, ageMonths(child), lang)
            if (date != null) repo.addExercise(child.id, activity, durationMin, burn, date, mood, notes)
            else repo.addExercise(child.id, activity, durationMin, burn)
            val ex = repo.getExerciseLogs(child.id)
            val weekly = repo.getWeeklyExercise(child.id).sumOf { it.durationMin ?: 0 }
            val summary = repo.getDailySummary(child.id)
            _ui.value = _ui.value.copy(exerciseLogs = ex, weeklyMinutes = weekly, summary = summary)
        }
    }

    // Cache generated tutorials so reopening the same recipe/exercise doesn't refetch (and doesn't flash "not found").
    private val recipeCache = mutableMapOf<String, com.lullabyai.app.data.model.RecipeContent>()
    private val exerciseCache = mutableMapOf<String, com.lullabyai.app.data.model.ExerciseContent>()

    fun deleteExercise(id: String) {
        viewModelScope.launch {
            val child = _ui.value.child ?: return@launch
            repo.deleteExercise(id)
            val ex = repo.getExerciseLogs(child.id)
            val weekly = repo.getWeeklyExercise(child.id).sumOf { it.durationMin ?: 0 }
            val summary = repo.getDailySummary(child.id)
            _ui.value = _ui.value.copy(exerciseLogs = ex, weeklyMinutes = weekly, summary = summary)
        }
    }

    fun loadRecipe(foodName: String, lang: String) {
        val key = "$foodName|$lang"
        recipeCache[key]?.let { _ui.value = _ui.value.copy(recipe = it, recipeLoading = false); return }
        viewModelScope.launch {
            _ui.value = _ui.value.copy(recipeLoading = true, recipe = null)
            val months = ageMonths(_ui.value.child)
            // one retry — the AI sometimes returns non-JSON on the first try
            var r = NutritionAiService.generateRecipe(foodName, months, lang)
            if (r == null) r = NutritionAiService.generateRecipe(foodName, months, lang)
            if (r != null) recipeCache[key] = r
            _ui.value = _ui.value.copy(recipeLoading = false, recipe = r)
        }
    }

    fun loadExercise(activityName: String, lang: String) {
        val key = "$activityName|$lang"
        // Remember the requested activity name so "done" matching is exact regardless of the title
        // the AI returns, and so logExercise records the same name shown in the list.
        exerciseCache[key]?.let { _ui.value = _ui.value.copy(exercise = it, exerciseLoading = false, currentExerciseName = activityName); return }
        viewModelScope.launch {
            _ui.value = _ui.value.copy(exerciseLoading = true, exercise = null, currentExerciseName = activityName)
            val months = ageMonths(_ui.value.child)
            var e = NutritionAiService.generateExercise(activityName, months, lang)
            if (e == null) e = NutritionAiService.generateExercise(activityName, months, lang)
            if (e != null) exerciseCache[key] = e
            _ui.value = _ui.value.copy(exerciseLoading = false, exercise = e)
        }
    }
}
