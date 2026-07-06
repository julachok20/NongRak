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
    val macros: Triple<Int, Int, Int>? = null,     // total carb, protein, fat (grams) for today
    val macrosLoading: Boolean = false,
    // Parent calorie-check tool (standalone): type a dish → kcal + macros + tip.
    val calorieCheck: NutritionAiService.CalorieCheck? = null,
    val checkedFood: String = "",
    val checkLoading: Boolean = false,
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
            _ui.value = _ui.value.copy(
                loading = false, child = child, foods = foods,
                exerciseLogs = ex, weeklyMinutes = weekly, summary = summary,
                targetKcal = dailyTargetKcal(child)
            )
            ensureSuggestions(lang)
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

    private var menuRecsKey: String? = null

    /** Loads a flat list of simple, budget-friendly dish ideas for the child's age. */
    fun ensureMenuRecommendations(lang: String) {
        val child = _ui.value.child ?: return
        val months = ageMonths(child)
        val key = "$lang|$months"
        if (key == menuRecsKey) return
        menuRecsKey = key
        viewModelScope.launch {
            _ui.value = _ui.value.copy(menuRecsLoading = true)
            val recs = NutritionAiService.recommendBudgetMenus(months, lang)
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

    /** Search-add flow: AI estimates kcal + assigns a meal from the time of day, then inserts. */
    fun addFoodSmart(name: String, lang: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val child = _ui.value.child ?: return@launch
            _ui.value = _ui.value.copy(calculating = true)
            val hour = java.time.LocalTime.now().hour
            val est = NutritionAiService.estimateFood(name.trim(), ageMonths(child), hour, lang)
            repo.addFood(child.id, est?.second ?: "other", name.trim(), est?.first)
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
        if (foods.isEmpty()) { _ui.value = _ui.value.copy(macros = null); macrosKey = null; return }
        val key = foods.joinToString("|") { it.itemName } + "|$lang"
        if (key == macrosKey) return
        macrosKey = key
        viewModelScope.launch {
            _ui.value = _ui.value.copy(macrosLoading = true)
            val m = NutritionAiService.estimateMacros(foods.map { it.itemName }, ageMonths(child), lang)
            _ui.value = _ui.value.copy(macros = m, macrosLoading = false)
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

    /** Log an exercise session; AI estimates calories burned. */
    fun logExercise(activity: String, durationMin: Int, lang: String) {
        viewModelScope.launch {
            val child = _ui.value.child ?: return@launch
            val burn = NutritionAiService.estimateBurn(activity, durationMin, ageMonths(child), lang)
            repo.addExercise(child.id, activity, durationMin, burn)
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
