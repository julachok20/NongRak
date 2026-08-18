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
import com.lullabyai.app.data.repository.HealthRepository
import com.lullabyai.app.data.repository.NutritionAiService
import com.lullabyai.app.data.repository.NutritionRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.Period

data class NutritionUiState(
    val loading: Boolean = false,
    val child: Child? = null,
    /** ISO date (yyyy-MM-dd) for food/exercise/summary loads — defaults to today; changeable on Today's Full Food List. */
    val logDate: String = LocalDate.now().toString(),
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
    val isChildOwner: Boolean = false,
    // Smart Refrigerator & Ingredient Scanner state
    val fridgeIngredients: List<com.lullabyai.app.data.model.FridgeIngredient> = emptyList(),
    val fridgeScanning: Boolean = false,
    val siblingChildren: List<Child> = emptyList(),
    val error: String? = null
)

class NutritionViewModel : ViewModel() {

    private val childRepo = ChildRepository()
    private val healthRepo = HealthRepository()
    private val repo = NutritionRepository()

    val weeklyGoalMinutes = 60

    private val _ui = MutableStateFlow(NutritionUiState())
    val ui: StateFlow<NutritionUiState> = _ui

    fun ageMonths(child: Child? = _ui.value.child): Int {
        val bd = child?.birthdate ?: return 0
        return runCatching {
            val p = Period.between(LocalDate.parse(bd), LocalDate.now())
            p.years * 12 + p.months
        }.getOrDefault(0)
    }

    /** Short bilingual AI insight line for logging screens (not charts) — derived from today's macros. */
    fun loggingInsightText(isThai: Boolean): String {
        val m = _ui.value.macros
        val needs = deficientNutrients()
        val labels = mapOf(
            "protein" to (if (isThai) "โปรตีน" else "protein"),
            "carbohydrates" to (if (isThai) "คาร์โบไฮเดรต" else "carbs"),
            "fat" to (if (isThai) "ไขมัน" else "fat"),
            "calcium" to (if (isThai) "แคลเซียม" else "calcium"),
            "iron" to (if (isThai) "ธาตุเหล็ก" else "iron")
        )
        return when {
            m == null -> if (isThai) "ยังไม่มีข้อมูลอาหารวันนี้ บันทึกมื้ออาหารเพื่อรับคำแนะนำที่ตรงจุดค่ะ"
            else "No food logged yet — log meals to get tailored tips."
            needs.isEmpty() -> if (isThai) "วันนี้ได้รับสารอาหารครบตามเป้าหมายแล้วค่ะ เก่งมาก!"
            else "Today's logged nutrients look on track for your child's age."
            else -> {
                val names = needs.mapNotNull { labels[it] }.joinToString(if (isThai) " และ " else " and ")
                if (isThai) "วันนี้ลูกน้อยของคุณอาจต้องการ${names}เพิ่มเติม จากมื้อที่บันทึกไว้"
                else "From today's logs, your child could use more $names."
            }
        }
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
            val rawChild = childRepo.getSelectedChild().getOrNull()
            // Allergy Watch reads `child.allergies` — prefer the structured allergies table so this
            // card never drifts from Allergy History / Child Summary after add/edit/delete.
            val child = rawChild?.let { c ->
                val names = healthRepo.getAllergies(c.id).getOrNull()
                    ?.map { it.name }?.filter { it.isNotBlank() }?.distinct().orEmpty()
                val summary = names.joinToString(", ").ifBlank { null }
                if (summary != c.allergies) c.copy(allergies = summary) else c
            }
            // If the active child changed, drop the previous child's AI content so it doesn't show stale.
            if (child?.id != _ui.value.child?.id) {
                suggestionsLang = null
                menuRecsKey = null
                _ui.value = _ui.value.copy(
                    activities = emptyList(), menuRecs = emptyList(),
                    mealSuggestion = null, mealPlan = emptyMap()
                )
            }
            val date = _ui.value.logDate.ifBlank { LocalDate.now().toString() }
            val foods = child?.let { repo.getFoodLogs(it.id, date) } ?: emptyList()
            val ex = child?.let { repo.getExerciseLogs(it.id, date) } ?: emptyList()
            val weekly = child?.let { repo.getWeeklyExercise(it.id).sumOf { e -> e.durationMin ?: 0 } } ?: 0
            val summary = child?.let { repo.getDailySummary(it.id, date) }
            val milkMl = child?.let { c ->
                repo.getMilkLogs(c.id).filter { com.lullabyai.app.util.TimeUtils.localDate(it.loggedAt) == date }.sumOf { it.volumeMl }
            } ?: 0
            val (targetCa, targetFe) = dailyTargetMinerals(child)
            val isOwner = child?.let { childRepo.isChildOwner(it.id).getOrDefault(false) } ?: false
            val fridgeItems = child?.let { repo.getFridgeIngredients(it.id) } ?: emptyList()
            val allChildren = childRepo.getChildren().getOrDefault(emptyList())
            val siblings = allChildren.filter { it.id != child?.id }

            _ui.value = _ui.value.copy(
                loading = false, child = child, logDate = date, foods = foods, todayMilkMl = milkMl,
                exerciseLogs = ex, weeklyMinutes = weekly, summary = summary,
                targetKcal = dailyTargetKcal(child), targetMacros = dailyTargetMacros(child),
                targetCalciumMg = targetCa, targetIronMg = targetFe,
                isChildOwner = isOwner,
                fridgeIngredients = fridgeItems,
                siblingChildren = siblings
            )
            ensureSuggestions(lang)
            loadMacros(lang)
            ensureMenuRecommendations(lang)
        }
    }

    fun loadFridgeIngredients() {
        viewModelScope.launch {
            val child = _ui.value.child ?: return@launch
            val items = repo.getFridgeIngredients(child.id)
            val allChildren = childRepo.getChildren().getOrDefault(emptyList())
            val siblings = allChildren.filter { it.id != child.id }
            _ui.value = _ui.value.copy(fridgeIngredients = items, siblingChildren = siblings)
        }
    }

    fun addFridgeIngredient(name: String, quantity: String? = null, category: String = "general", lang: String? = null) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val child = _ui.value.child ?: return@launch
            repo.addFridgeIngredient(child.id, name.trim(), category, quantity)
            val updated = repo.getFridgeIngredients(child.id)
            _ui.value = _ui.value.copy(fridgeIngredients = updated)
            menuRecsKey = null
            lang?.let { ensureMenuRecommendations(it) }
        }
    }

    fun removeFridgeIngredient(id: String, lang: String? = null) {
        viewModelScope.launch {
            val child = _ui.value.child ?: return@launch
            repo.removeFridgeIngredient(id)
            val updated = repo.getFridgeIngredients(child.id)
            _ui.value = _ui.value.copy(fridgeIngredients = updated)
            menuRecsKey = null
            lang?.let { ensureMenuRecommendations(it) }
        }
    }

    fun scanFridgePhoto(imageBytes: ByteArray, lang: String, onDone: (List<String>) -> Unit = {}) {
        viewModelScope.launch {
            val child = _ui.value.child ?: return@launch
            _ui.value = _ui.value.copy(fridgeScanning = true)
            val imageBase64 = android.util.Base64.encodeToString(imageBytes, android.util.Base64.NO_WRAP)
            val detected = NutritionAiService.scanFridgeIngredientsFromPhoto(imageBase64, "image/jpeg", lang)
            if (detected.isNotEmpty()) {
                val currentNames = _ui.value.fridgeIngredients.map { it.ingredientName.lowercase().trim() }.toSet()
                detected.filterNot { currentNames.contains(it.lowercase().trim()) }.forEach { name ->
                    repo.addFridgeIngredient(child.id, name)
                }
                val updated = repo.getFridgeIngredients(child.id)
                _ui.value = _ui.value.copy(fridgeIngredients = updated, fridgeScanning = false)
                menuRecsKey = null
                ensureMenuRecommendations(lang)
            } else {
                _ui.value = _ui.value.copy(fridgeScanning = false)
            }
            onDone(detected)
        }
    }

    fun syncFridgeToSibling(targetChildId: String, lang: String? = null, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            val child = _ui.value.child ?: return@launch
            repo.syncFridgeIngredients(child.id, targetChildId)
            onDone()
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
     *  today's macros haven't loaded yet). Re-fetches when the deficiency set or fridge inventory changes. */
    fun ensureMenuRecommendations(lang: String) {
        val child = _ui.value.child ?: return
        val months = ageMonths(child)
        val needs = deficientNutrients()
        val available = _ui.value.fridgeIngredients.map { it.ingredientName }
        val key = "$lang|$months|${needs.joinToString(",")}|${available.sorted().joinToString(",")}"
        if (key == menuRecsKey) return
        menuRecsKey = key
        viewModelScope.launch {
            _ui.value = _ui.value.copy(menuRecsLoading = true)
            val recs = if (needs.isNotEmpty()) NutritionAiService.recommendMenusForNeeds(needs, months, lang, available)
                       else NutritionAiService.recommendBudgetMenus(months, lang, available)
            _ui.value = _ui.value.copy(menuRecs = recs, menuRecsLoading = false)
        }
    }

    private suspend fun reloadLogsNow() {
        val child = _ui.value.child ?: return
        val date = _ui.value.logDate
        val foods = repo.getFoodLogs(child.id, date)
        val ex = repo.getExerciseLogs(child.id, date)
        val summary = repo.getDailySummary(child.id, date)
        val milkMl = repo.getMilkLogs(child.id)
            .filter { com.lullabyai.app.util.TimeUtils.localDate(it.loggedAt) == date }
            .sumOf { it.volumeMl }
        _ui.value = _ui.value.copy(foods = foods, exerciseLogs = ex, summary = summary, todayMilkMl = milkMl)
    }

    private fun reloadLogs() {
        viewModelScope.launch { reloadLogsNow() }
    }

    /** Switch the logging day (Today's Full Food List "เปลี่ยนวันที่"). Resets macro cache for the new day. */
    fun setLogDate(dateIso: String, lang: String) {
        if (dateIso == _ui.value.logDate) return
        macrosKey = null
        microKey = null
        viewModelScope.launch {
            _ui.value = _ui.value.copy(logDate = dateIso, macros = null, microStatus = null)
            reloadLogsNow()
            loadMacros(lang)
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
            repo.addFood(child.id, est?.second ?: "other", name.trim(), kcal, _ui.value.logDate)
            macrosKey = null
            reloadLogs()
            _ui.value = _ui.value.copy(calculating = false)
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
            repo.addFood(child.id, est.meal, est.itemName, kcal, _ui.value.logDate)
            macrosKey = null
            reloadLogs()
            _ui.value = _ui.value.copy(calculating = false)
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

    // Tracks the in-flight typed/photo check so the Cancel button can abort it rather than
    // just leaving checkLoading spinning forever once the user backs out of the request.
    private var checkJob: Job? = null

    /** Parent calorie-check: estimate kcal + macros + tip for any typed dish. */
    fun runCalorieCheck(food: String, lang: String) {
        if (food.isBlank()) return
        checkJob?.cancel()
        checkJob = viewModelScope.launch {
            _ui.value = _ui.value.copy(checkLoading = true, checkedFood = food.trim())
            val r = NutritionAiService.calorieCheck(food.trim(), lang)
            _ui.value = _ui.value.copy(calorieCheck = r, checkLoading = false)
        }
    }

    /** Same as [runCalorieCheck] but the dish is identified from a camera photo (base64 JPEG). */
    fun runCalorieCheckFromPhoto(imageBytes: ByteArray, lang: String) {
        checkJob?.cancel()
        checkJob = viewModelScope.launch {
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

    /** Cancel button on the Calorie Check screen — aborts an in-flight photo/AI check and
     *  resets the loading state so the user lands back on the plain form, nothing saved. */
    fun cancelCalorieCheck() {
        checkJob?.cancel()
        checkJob = null
        _ui.value = _ui.value.copy(checkLoading = false, checkPhotoFailed = false)
    }

    /** Recompute the summary (picks up newly-logged exercise burned calories). */
    fun refreshSummary() {
        viewModelScope.launch {
            val child = _ui.value.child ?: return@launch
            _ui.value = _ui.value.copy(summary = repo.getDailySummary(child.id, _ui.value.logDate))
        }
    }

    fun editFood(id: String, name: String) {
        viewModelScope.launch { repo.updateFood(id, name.trim()); macrosKey = null; reloadLogs() }
    }

    fun editFoodCalories(id: String, calories: Int) {
        viewModelScope.launch { repo.setFoodCalories(id, calories); macrosKey = null; reloadLogs() }
    }

    fun deleteFood(id: String) {
        viewModelScope.launch {
            val child = _ui.value.child ?: return@launch
            if (!childRepo.isChildOwner(child.id).getOrDefault(false)) return@launch
            repo.deleteFood(id)
            macrosKey = null
            reloadLogs()
        }
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
            reloadLogsNow()
            _ui.value = _ui.value.copy(calculating = false)
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
            val logDate = date ?: _ui.value.logDate
            repo.addExercise(child.id, activity, durationMin, burn, logDate, mood, notes)
            val ex = repo.getExerciseLogs(child.id, _ui.value.logDate)
            val weekly = repo.getWeeklyExercise(child.id).sumOf { it.durationMin ?: 0 }
            val summary = repo.getDailySummary(child.id, _ui.value.logDate)
            _ui.value = _ui.value.copy(exerciseLogs = ex, weeklyMinutes = weekly, summary = summary)
        }
    }

    // Cache generated tutorials so reopening the same recipe/exercise doesn't refetch (and doesn't flash "not found").
    private val recipeCache = mutableMapOf<String, com.lullabyai.app.data.model.RecipeContent>()
    private val exerciseCache = mutableMapOf<String, com.lullabyai.app.data.model.ExerciseContent>()

    /** Stitch calorie-calculator exercise edit popup — rename + duration; re-estimates burn. */
    fun editExercise(id: String, activity: String, durationMin: Int, lang: String) {
        if (activity.isBlank() || durationMin <= 0) return
        viewModelScope.launch {
            val child = _ui.value.child ?: return@launch
            val burn = NutritionAiService.estimateBurn(activity.trim(), durationMin, ageMonths(child), lang)
            repo.updateExercise(id, activity.trim(), durationMin, burn)
            val ex = repo.getExerciseLogs(child.id, _ui.value.logDate)
            val weekly = repo.getWeeklyExercise(child.id).sumOf { it.durationMin ?: 0 }
            val summary = repo.getDailySummary(child.id, _ui.value.logDate)
            _ui.value = _ui.value.copy(exerciseLogs = ex, weeklyMinutes = weekly, summary = summary)
        }
    }

    fun deleteExercise(id: String) {
        viewModelScope.launch {
            val child = _ui.value.child ?: return@launch
            if (!childRepo.isChildOwner(child.id).getOrDefault(false)) return@launch
            repo.deleteExercise(id)
            val ex = repo.getExerciseLogs(child.id, _ui.value.logDate)
            val weekly = repo.getWeeklyExercise(child.id).sumOf { it.durationMin ?: 0 }
            val summary = repo.getDailySummary(child.id, _ui.value.logDate)
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
