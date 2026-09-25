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
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.Period
import kotlin.math.abs
import kotlin.math.roundToInt

data class NutritionUiState(
    val loading: Boolean = false,
    val child: Child? = null,
    /** A signed link to the child's photo. children.avatar_url is a private bucket path, not a URL. */
    val childAvatarUrl: String? = null,
    /** ISO date (yyyy-MM-dd) for food/exercise/summary loads — defaults to today; changeable on Today's Full Food List. */
    val logDate: String = LocalDate.now().toString(),
    val foods: List<FoodLog> = emptyList(),
    val todayMilkMl: Int = 0,
    /** Boiled water logged on the milk screen (type "water"); never part of the milk figures. */
    val todayWaterMl: Int = 0,
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
    /** The day's milk logs (type + volume/minutes); [todayMilkMl] is only their measured-volume sum. */
    val todayMilk: List<com.lullabyai.app.data.model.MilkLog> = emptyList(),
    /** Thai DRI 2563 targets for this child's age and sex; null outside 0-12 years. */
    val dri: com.lullabyai.app.util.DriReference.Targets? = null,
    /** The day's nutrient totals from the Thai food composition table (AI only for unmatched foods). */
    val dayTotals: com.lullabyai.app.util.DayNutrition.Totals? = null,
    val dayTotalsLoading: Boolean = false,
    val photoAddFailed: Boolean = false,  // last camera-add couldn't identify a food in the photo
    /** food_logs ids added since the record screen last cleared — "this meal", not the whole day. */
    val mealFoodIds: Set<String> = emptySet(),
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
    /** The HOUSEHOLD's pantry: shared by every child of this child's owner, and by their carers. */
    val fridgeIngredients: List<com.lullabyai.app.data.model.FridgeIngredient> = emptyList(),
    val fridgeScanning: Boolean = false,
    /** This child's own food trials (the 3-4 day wait rule); a sibling's result never applies here. */
    val foodTrials: List<com.lullabyai.app.data.model.FoodTrial> = emptyList(),
    val allergies: List<com.lullabyai.app.data.model.Allergy> = emptyList(),
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
            m == null && freshTotals() == null -> if (isThai) "ยังไม่มีข้อมูลอาหารวันนี้ บันทึกมื้ออาหารเพื่อรับคำแนะนำที่ตรงจุดค่ะ"
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

    /** Thai DRI 2563 for this child — the one source for every target on the nutrition screens. */
    private fun driFor(child: Child?): com.lullabyai.app.util.DriReference.Targets? =
        child?.let { runCatching { com.lullabyai.app.util.DriReference.forChild(ageMonths(it), it.gender) }.getOrNull() }

    /** Daily energy target (kcal) from Thai DRI 2563, by age and sex. */
    private fun dailyTargetKcal(child: Child?): Int = driFor(child)?.energyKcal ?: 0

    /** Daily (carb, protein, fat) in grams. Protein is the DRI figure; the DRI gives carbohydrate
     *  and fat only as a share of energy, so these are the MIDPOINT of that range in grams — the
     *  statistics page shows the full range. 0-5 months: breast milk covers it (DRI gives none). */
    private fun dailyTargetMacros(child: Child?): Triple<Int, Int, Int> {
        val d = driFor(child) ?: return Triple(0, 0, 0)
        fun mid(r: ClosedFloatingPointRange<Double>?) = r?.let { ((it.start + it.endInclusive) / 2).toInt() } ?: 0
        return Triple(mid(d.carbG), d.proteinG?.toInt() ?: 0, mid(d.fatG))
    }

    /** Daily (calcium, iron) in mg from Thai DRI 2563. */
    private fun dailyTargetMinerals(child: Child?): Pair<Int, Int> {
        val d = driFor(child) ?: return 0 to 0
        return (d.values["calcium"]?.toInt() ?: 0) to (d.values["iron"]?.let { kotlin.math.round(it).toInt() } ?: 0)
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
            val (dayWater, dayMilk) = (child?.let { c ->
                repo.getMilkLogs(c.id).filter { com.lullabyai.app.util.TimeUtils.localDate(it.loggedAt) == date }
            } ?: emptyList()).partition { it.isWater }
            val milkMl = dayMilk.sumOf { it.volumeMl }
            val (targetCa, targetFe) = dailyTargetMinerals(child)
            val isOwner = child?.let { childRepo.isChildOwner(it.id).getOrDefault(false) } ?: false
            val fridgeItems = child?.let { repo.getFridgeIngredients(it.userId) } ?: emptyList()
            val allChildren = childRepo.getChildren().getOrDefault(emptyList())
            val siblings = allChildren.filter { it.id != child?.id }
            val avatar = child?.avatarUrl?.let {
                runCatching { com.lullabyai.app.data.remote.StorageHelper.signedAvatarUrl(it) }.getOrNull()
            }

            _ui.value = _ui.value.copy(
                loading = false, child = child, childAvatarUrl = avatar, logDate = date, foods = foods, todayMilkMl = milkMl,
                todayMilk = dayMilk, todayWaterMl = dayWater.sumOf { it.volumeMl }, dri = driFor(child),
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
            val items = repo.getFridgeIngredients(child.userId)
            val trials = repo.getFoodTrials(child.id)
            val allergyList = healthRepo.getAllergies(child.id).getOrDefault(emptyList())
            val allChildren = childRepo.getChildren().getOrDefault(emptyList())
            // Siblings = the other children of this SAME household. Another family a carer works
            // for is not a sibling and must never share this pantry.
            val siblings = allChildren.filter { it.id != child.id && it.userId == child.userId }
            _ui.value = _ui.value.copy(
                fridgeIngredients = items, foodTrials = trials, allergies = allergyList, siblingChildren = siblings
            )
        }
    }

    private suspend fun refreshPantry(lang: String?) {
        val child = _ui.value.child ?: return
        _ui.value = _ui.value.copy(
            fridgeIngredients = repo.getFridgeIngredients(child.userId),
            foodTrials = repo.getFoodTrials(child.id)
        )
        menuRecsKey = null
        lang?.let { ensureMenuRecommendations(it) }
    }

    fun addFridgeIngredient(
        name: String, quantity: String? = null, category: String = "general", lang: String? = null,
        expiresOn: String? = null, storage: String? = null, storageNote: String? = null, note: String? = null
    ) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val child = _ui.value.child ?: return@launch
            repo.addFridgeIngredient(
                child.id, name.trim(), category, quantity,
                boughtOn = java.time.LocalDate.now().toString(),
                expiresOn = expiresOn, storage = storage, storageNote = storageNote, note = note
            )
            refreshPantry(lang)
        }
    }

    fun editFridgeIngredient(
        id: String, name: String? = null, quantity: String? = null, expiresOn: String? = null,
        storage: String? = null, storageNote: String? = null, note: String? = null, lang: String? = null
    ) {
        viewModelScope.launch {
            repo.updateFridgeIngredient(id, name, quantity, expiresOn, storage, storageNote, note)
            refreshPantry(lang)
        }
    }

    fun removeFridgeIngredient(id: String, lang: String? = null) {
        viewModelScope.launch {
            repo.removeFridgeIngredient(id)
            refreshPantry(lang)
        }
    }

    /** Starts the 3-4 day watch on a food for the CHILD being viewed, not for the household. */
    fun startFoodTrial(foodName: String) {
        viewModelScope.launch {
            val child = _ui.value.child ?: return@launch
            repo.startFoodTrial(child.id, foodName)
            _ui.value = _ui.value.copy(foodTrials = repo.getFoodTrials(child.id))
        }
    }

    /** [result] is "safe" or "allergic". An allergic result also joins the child's allergy list. */
    fun decideFoodTrial(trialId: String, foodName: String, result: String, reaction: String? = null) {
        viewModelScope.launch {
            val child = _ui.value.child ?: return@launch
            repo.setFoodTrialResult(trialId, result, reaction)
            if (result == "allergic") {
                healthRepo.addAllergy(child.id, foodName.trim(), "food", "mild", reaction)
            }
            _ui.value = _ui.value.copy(
                foodTrials = repo.getFoodTrials(child.id),
                allergies = healthRepo.getAllergies(child.id).getOrDefault(_ui.value.allergies)
            )
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
                    repo.addFridgeIngredient(child.id, name, boughtOn = java.time.LocalDate.now().toString())
                }
                _ui.value = _ui.value.copy(fridgeScanning = false)
                refreshPantry(lang)
            } else {
                _ui.value = _ui.value.copy(fridgeScanning = false)
            }
            onDone(detected)
        }
    }

    // Copying the fridge to a sibling used to be a button here. Children of one household now read
    // the same pantry rows, so there is nothing left to copy.

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
        val t = freshTotals()
        if (t != null && _ui.value.dri?.breastMilkCovers == false) {
            val (tc, tp, tf) = _ui.value.targetMacros
            fun low(key: String, target: Int) = target > 0 &&
                com.lullabyai.app.util.DayNutrition.status(t.amount[key] ?: 0.0, target.toDouble(), t.missing[key] ?: 0)
                    .let { it == com.lullabyai.app.util.DayNutrition.Status.FAIRLY_LOW || it == com.lullabyai.app.util.DayNutrition.Status.LOW }
            return listOfNotNull(
                "protein".takeIf { low("protein", tp) }, "carbohydrates".takeIf { low("carb", tc) },
                "fat".takeIf { low("fat", tf) }, "calcium".takeIf { low("calcium", _ui.value.targetCalciumMg) },
                "iron".takeIf { low("iron", _ui.value.targetIronMg) }
            )
        }
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
        val (dayWater, dayMilk) = repo.getMilkLogs(child.id)
            .filter { com.lullabyai.app.util.TimeUtils.localDate(it.loggedAt) == date }
            .partition { it.isWater }
        _ui.value = _ui.value.copy(
            foods = foods, exerciseLogs = ex, summary = summary,
            todayMilkMl = dayMilk.sumOf { it.volumeMl }, todayMilk = dayMilk,
            todayWaterMl = dayWater.sumOf { it.volumeMl }
        )
    }

    private fun reloadLogs() {
        viewModelScope.launch { reloadLogsNow() }
    }

    /** Switch the logging day (Today's Full Food List "เปลี่ยนวันที่"). Resets macro cache for the new day. */
    fun setLogDate(dateIso: String, lang: String) {
        if (dateIso == _ui.value.logDate) return
        macrosKey = null
        microKey = null
        dayKey = null
        viewModelScope.launch {
            _ui.value = _ui.value.copy(logDate = dateIso, macros = null, microStatus = null, dayTotals = null)
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
            val spill = spillFraction.coerceIn(0f, 1f)
            val kcal = est?.first?.let { (it * (1f - spill)).toInt() }
            val id = repo.addFood(
                child.id, est?.second ?: "other", name.trim(), kcal, _ui.value.logDate,
                eatenPct = eatenPctOf(spill)
            ).getOrNull()
            macrosKey = null
            reloadLogs()
            _ui.value = _ui.value.copy(calculating = false, mealFoodIds = _ui.value.mealFoodIds + listOfNotNull(id))
        }
    }

    /**
     * Logs a cooked recipe straight into today's food, using the recipe's own kcal rather than
     * asking the AI again. Returns through [onDone] so the screen can confirm and close.
     */
    fun logRecipeAsMeal(title: String, kcal: Int?, onDone: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val child = _ui.value.child ?: return@launch onDone(false)
            val hour = java.time.LocalTime.now().hour
            val meal = when {
                hour < 10 -> "breakfast"
                hour < 14 -> "lunch"
                hour < 17 -> "snack"
                else -> "dinner"
            }
            val id = repo.addFood(child.id, meal, title.trim(), kcal, _ui.value.logDate).getOrNull()
            macrosKey = null
            reloadLogs()
            _ui.value = _ui.value.copy(mealFoodIds = _ui.value.mealFoodIds + listOfNotNull(id))
            onDone(id != null)
        }
    }

    /** The spill question turned into "how much of it actually went in", for the food row. */
    private fun eatenPctOf(spillFraction: Float): Int = ((1f - spillFraction.coerceIn(0f, 1f)) * 100).toInt()

    /** Clears "this meal" after the parent presses save; the rows stay in the day's log. */
    fun clearMealSession() {
        _ui.value = _ui.value.copy(mealFoodIds = emptySet())
    }

    // Signed care-media URLs for food photos, kept for the session: each one is a network round
    // trip and the same row is drawn again on every scroll.
    private val photoUrls = mutableMapOf<String, String>()

    /** A short-lived link for a food photo's care-media [path]. Null when it cannot be signed. */
    suspend fun foodPhotoUrl(path: String): String? =
        photoUrls[path] ?: childRepo.signedCareMediaUrl(path)?.also { photoUrls[path] = it }

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
            // Kept only once the AI has read it: a photo of nothing recognisable would otherwise
            // sit in storage with no row pointing at it.
            val photoPath = childRepo.uploadCareMedia(child.id, imageBytes, "jpg", "image/jpeg").getOrNull()
            val spill = spillFraction.coerceIn(0f, 1f)
            val kcal = (est.kcal * (1f - spill)).toInt()
            val id = repo.addFood(
                child.id, est.meal, est.itemName, kcal, _ui.value.logDate,
                photoPath = photoPath, eatenPct = eatenPctOf(spill)
            ).getOrNull()
            macrosKey = null
            reloadLogs()
            _ui.value = _ui.value.copy(calculating = false, mealFoodIds = _ui.value.mealFoodIds + listOfNotNull(id))
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

    // Per-100 g AI estimates for foods the table doesn't have, kept per name for the session so the
    // same unmatched dish isn't re-asked every time the page opens.
    private val aiPer100 = mutableMapOf<String, Map<String, Double>>()
    private val aiGroups = mutableMapOf<String, Set<Int>>()
    private var dayKey: String? = null
    /** The foods and milk [NutritionUiState.dayTotals] was computed from; totals for an older list are stale. */
    private var totalsFor: Pair<List<FoodLog>, List<com.lullabyai.app.data.model.MilkLog>>? = null

    private fun freshTotals(): com.lullabyai.app.util.DayNutrition.Totals? =
        _ui.value.dayTotals?.takeIf { totalsFor == (_ui.value.foods to _ui.value.todayMilk) }

    /** Today's energy: kcal of every portioned food and milk, plus the logged kcal of foods that could
     *  not be portioned. Shared by the nutrition hub and the statistics page. Null until computed. */
    fun dayIntakeKcal(): Int? {
        val t = freshTotals() ?: return null
        val uncounted = _ui.value.foods.filter { f -> t.uncounted.any { it.label == f.itemName } }.sumOf { it.calories ?: 0 }
        return ((t.amount["kcal"] ?: 0.0) + uncounted).roundToInt()
    }

    /** WHO BMI-for-age of the profile weight and height at today's age; null when either is missing
     *  or the value is implausible (a typo must not raise an obesity flag). */
    fun bmiStatus(): com.lullabyai.app.util.GrowthReference.GrowthStatus? {
        val c = _ui.value.child ?: return null
        return com.lullabyai.app.util.GrowthReference.weightForHeightStatus(
            c.weightKg, c.heightCm, com.lullabyai.app.util.GrowthReference.ageDaysAt(c.birthdate), c.gender
        )?.takeIf { it.z == null || abs(it.z) <= 5 }
    }

    /**
     * The statistics page's numbers. Each logged food is looked up in the Thai food composition
     * table; only foods it doesn't have go to the AI (one batched call). Portion weight comes from
     * the food's logged kcal. Milk: breast milk by volume uses the table's human-milk row; formula
     * uses an AI estimate; a direct breastfeed logged in minutes has no volume and is listed as
     * uncounted rather than guessed.
     */
    fun loadDayNutrition(lang: String) {
        val child = _ui.value.child ?: return
        val foods = _ui.value.foods
        val milk = _ui.value.todayMilk
        val key = foods.joinToString("|") { "${it.itemName}:${it.calories}" } + "#" +
            milk.joinToString("|") { "${it.milkType}:${it.volumeMl}:${it.durationMin}" } + "#$lang"
        if (key == dayKey) return
        dayKey = key
        if (foods.isEmpty() && milk.isEmpty()) { _ui.value = _ui.value.copy(dayTotals = null); return }
        viewModelScope.launch {
            _ui.value = _ui.value.copy(dayTotalsLoading = true)
            val isThai = !lang.startsWith("en")
            val formulaName = if (isThai) "นมผงสำหรับเด็ก ชงแล้ว" else "Infant/toddler formula, prepared"
            val tableMatch = foods.associate { f -> f.id to runCatching { com.lullabyai.app.util.ThaiFoodTable.match(f.itemName) }.getOrNull() }
            val wantAi = (foods.filter { tableMatch[it.id] == null }.map { it.itemName } +
                if (milk.any { it.milkType == "formula" && it.volumeMl > 0 }) listOf(formulaName) else emptyList())
                .distinct().filter { it !in aiPer100 }
            // Thai 5 food groups: the table's group for plain foods; dishes and unmatched foods go to
            // the AI, in parallel with the per-100 g call.
            val needGroups = foods.filter { com.lullabyai.app.util.DayNutrition.thaiGroups(tableMatch[it.id]?.food?.group) == null }
                .map { it.itemName }.distinct()
            val askGroups = needGroups.filter { it !in aiGroups }
            val groupsCall = async { if (askGroups.isEmpty()) emptyMap() else NutritionAiService.classifyThaiGroups(askGroups, lang) }
            if (wantAi.isNotEmpty()) aiPer100 += NutritionAiService.estimatePer100g(wantAi, lang)

            val portions = mutableListOf<com.lullabyai.app.util.DayNutrition.Portion>()
            foods.forEach { f ->
                val m = tableMatch[f.id]
                portions += if (m != null) {
                    val food = m.food
                    val grams = com.lullabyai.app.util.DayNutrition.gramsFromKcal(f.calories, food.kcalPer100g())
                        ?: food.servingGrams
                    com.lullabyai.app.util.DayNutrition.Portion(
                        label = f.itemName,
                        source = if (grams != null) com.lullabyai.app.util.DayNutrition.Source.TABLE else com.lullabyai.app.util.DayNutrition.Source.NO_PORTION,
                        matchedName = food.th, grams = grams,
                        per100 = food.n + (food.kcalPer100g()?.let { mapOf("kcal" to it) } ?: emptyMap()),
                        logId = f.id
                    )
                } else {
                    val est = aiPer100[f.itemName]
                    val grams = est?.let { com.lullabyai.app.util.DayNutrition.gramsFromKcal(f.calories, it["kcal"]) }
                    com.lullabyai.app.util.DayNutrition.Portion(
                        label = f.itemName,
                        source = when {
                            est == null -> com.lullabyai.app.util.DayNutrition.Source.NO_DATA
                            grams == null -> com.lullabyai.app.util.DayNutrition.Source.NO_PORTION
                            else -> com.lullabyai.app.util.DayNutrition.Source.AI
                        },
                        grams = grams, per100 = est.orEmpty(), logId = f.id
                    )
                }
            }
            milk.forEach { m ->
                val breast = m.milkType == "breast"
                val label = when {
                    breast && m.durationMin != null -> if (isThai) "นมแม่เข้าเต้า ${m.durationMin} นาที" else "Breastfed ${m.durationMin} min"
                    breast -> if (isThai) "นมแม่ ${m.volumeMl} มล." else "Breast milk ${m.volumeMl} ml"
                    else -> if (isThai) "นมผง ${m.volumeMl} มล." else "Formula ${m.volumeMl} ml"
                }
                val human = com.lullabyai.app.util.ThaiFoodTable.byId("09011")
                portions += when {
                    m.volumeMl <= 0 -> com.lullabyai.app.util.DayNutrition.Portion(label, com.lullabyai.app.util.DayNutrition.Source.NO_PORTION)
                    // ponytail: 1 ml ≈ 1 g for milk (density ~1.03); fine at this precision.
                    breast && human != null -> com.lullabyai.app.util.DayNutrition.Portion(
                        label, com.lullabyai.app.util.DayNutrition.Source.TABLE, human.th, m.volumeMl.toDouble(), human.n
                    )
                    else -> aiPer100[formulaName]?.let {
                        com.lullabyai.app.util.DayNutrition.Portion(label, com.lullabyai.app.util.DayNutrition.Source.AI, null, m.volumeMl.toDouble(), it)
                    } ?: com.lullabyai.app.util.DayNutrition.Portion(label, com.lullabyai.app.util.DayNutrition.Source.NO_DATA)
                }
            }
            aiGroups += groupsCall.await()
            val groups = foods.flatMap { f ->
                com.lullabyai.app.util.DayNutrition.thaiGroups(tableMatch[f.id]?.food?.group) ?: aiGroups[f.itemName].orEmpty()
            }.toSet() + (if (milk.isNotEmpty()) setOf(1) else emptySet())   // breast milk / formula: หมู่ 1
            val totals = com.lullabyai.app.util.DayNutrition.total(portions, NutritionAiService.PER100_KEYS)
                .copy(groups = groups, groupsUnknown = needGroups.filter { it !in aiGroups })
            if (dayKey == key) {
                totalsFor = foods to milk
                _ui.value = _ui.value.copy(dayTotals = totals, dayTotalsLoading = false)
            }
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
            val r = NutritionAiService.calorieCheck(food.trim(), lang)?.let { fromTableIfKnown(it) }
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
            _ui.value = _ui.value.copy(
                calorieCheck = fromTableIfKnown(r), checkedFood = r.itemName ?: "", checkLoading = false
            )
        }
    }

    /**
     * Replaces the AI's nutrient guesses with the กรมอนามัย food table whenever the dish is one the
     * table actually has, scaled to the portion the AI read. The AI is then only doing what it is
     * good at — naming the dish and judging how much is on the plate.
     */
    private fun fromTableIfKnown(check: NutritionAiService.CalorieCheck): NutritionAiService.CalorieCheck {
        val name = check.itemName ?: return check
        val match = runCatching { com.lullabyai.app.util.ThaiFoodTable.match(name) }.getOrNull() ?: return check
        val food = match.food
        val per100 = food.n + (food.kcalPer100g()?.let { mapOf("kcal" to it) } ?: emptyMap())
        val grams = check.portionGrams?.toDouble()
            ?: com.lullabyai.app.util.DayNutrition.gramsFromKcal(check.kcal, food.kcalPer100g())
            ?: food.servingGrams
            ?: return check
        fun at(key: String): Double? = per100[key]?.let { it * grams / 100.0 }
        return check.copy(
            kcal = at("kcal")?.roundToInt() ?: check.kcal,
            carb = at("carb")?.roundToInt() ?: check.carb,
            protein = at("protein")?.roundToInt() ?: check.protein,
            fat = at("fat")?.roundToInt() ?: check.fat,
            fibreG = at("fibre") ?: check.fibreG,
            ironMg = at("iron") ?: check.ironMg,
            calciumMg = at("calcium") ?: check.calciumMg,
            sodiumMg = at("sodium") ?: check.sodiumMg,
            portionGrams = grams.roundToInt(),
            fromTable = food.th
        )
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
