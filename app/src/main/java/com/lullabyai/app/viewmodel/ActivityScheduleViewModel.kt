package com.lullabyai.app.viewmodel

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Vaccines
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lullabyai.app.data.model.ActivityCategoryCount
import com.lullabyai.app.data.model.ChildActivity
import com.lullabyai.app.data.repository.ChildRepository
import com.lullabyai.app.data.settings.ChildSelection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId

/** Which shape the calendar is drawn in. The timeline below it always shows the selected day. */
enum class ScheduleView { DAY, WEEK, MONTH }

/**
 * Where the ‹ › arrows land, which is one unit of whatever is currently on screen — a day in Day, a
 * week in Week, a month in Month. Stepping by month in every view made Day and Week unusable for
 * moving to tomorrow or next week, which is most of what a calendar is for.
 */
internal fun ScheduleView.step(from: LocalDate, direction: Long): LocalDate = when (this) {
    ScheduleView.DAY -> from.plusDays(direction)
    ScheduleView.WEEK -> from.plusWeeks(direction)
    ScheduleView.MONTH -> from.plusMonths(direction)
}

data class ActivityScheduleUiState(
    val childId: String? = null,
    val childName: String = "",
    val view: ScheduleView = ScheduleView.WEEK,
    val selectedDate: LocalDate = LocalDate.now(),
    /**
     * Everything in the month containing [selectedDate] — not just the visible window.
     *
     * The whole month is loaded whichever view is showing, because the calendar draws a dot on
     * every day that has something on it. Fetching only the visible week would leave the month grid
     * blank the moment you switched to it.
     */
    val activities: List<ChildActivity> = emptyList(),
    val summary: List<ActivityCategoryCount> = emptyList(),
    val categoryFilter: String? = null,
    val query: String = "",
    /** Google Places results for the สถานที่ field, while one is being searched for. */
    val placeResults: List<com.lullabyai.app.data.remote.PlaceSuggestion> = emptyList(),
    val searchingPlaces: Boolean = false,
    /** The AI แนะนำกิจกรรมและที่เที่ยว rail. */
    val recommendations: List<com.lullabyai.app.data.remote.ActivityRecommendation> = emptyList(),
    val loadingRecommendations: Boolean = false,
    /** True once suggestions have been asked for, so "none" reads differently from "not yet". */
    val recommendationsRequested: Boolean = false,
    /** Set when location was declined or unavailable — the rail says why it is home-only. */
    val locationUnavailable: Boolean = false,
    /** When the shown batch was produced, so the rail can say how old it is. */
    val generatedAt: String? = null,
    val loading: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
    /** Google Calendar sync on this phone - per device, so each co-parent turns it on for themselves. */
    val calendarSyncEnabled: Boolean = false,
    val calendarName: String? = null,
    val calendarSyncing: Boolean = false,
    val calendarLastSyncMillis: Long? = null,
    /** What the most recent pass did, shown on the sync card so a partial failure is visible. */
    val calendarReport: com.lullabyai.app.data.calendar.DeviceCalendarSync.Report? = null
) {
    private fun matches(a: ChildActivity): Boolean =
        (categoryFilter == null || a.category == categoryFilter) &&
            (query.isBlank() ||
                a.title.contains(query, ignoreCase = true) ||
                a.locationName.orEmpty().contains(query, ignoreCase = true))

    /** Days in this month that have something on them, and of which kinds — the calendar's dots. */
    val markedDays: Map<LocalDate, List<String>>
        get() = activities.filter(::matches)
            .mapNotNull { a -> a.localDate()?.let { it to a.category } }
            .groupBy({ it.first }, { it.second })

    /** What the timeline shows: the selected day, all-day entries first, then by time. */
    val dayActivities: List<ChildActivity>
        get() = activities.filter { matches(it) && it.localDate() == selectedDate }
            .sortedWith(compareBy({ !it.allDay }, { it.startsAt }))

    /** The week the selected day falls in, Sunday first — the design's header order (อา. … ส.). */
    val weekDays: List<LocalDate>
        get() {
            val sundayOffset = selectedDate.dayOfWeek.value % 7   // Sunday(7) -> 0, Monday(1) -> 1
            val start = selectedDate.minusDays(sundayOffset.toLong())
            return (0..6).map { start.plusDays(it.toLong()) }
        }
}

/**
 * Why an activity window is not acceptable, or null when it is.
 *
 * A schedule is a plan, so it points forward: recording something that already happened belongs in
 * the care log, not here. Checked as a plain function against an injected [now] so the boundary —
 * "a minute ago" versus "in a minute" — can be tested rather than assumed.
 */
internal fun validateActivityWindow(
    start: OffsetDateTime,
    end: OffsetDateTime?,
    now: OffsetDateTime
): String? = when {
    start.isBefore(now) -> "past_start"
    end != null && !end.isAfter(start) -> "end_before_start"
    else -> null
}

/** Nothing is suggested to start after this — later than an evening out, and the day is gone. */
private val LATEST_SUGGESTED_START = java.time.LocalTime.of(22, 0)

/**
 * What the time pickers open on when a suggestion is scheduled.
 *
 * The model proposes the hour, because it knows whether this is a beach morning or a dinner out and
 * saw the venue's opening hours. This only keeps it usable: an hour already gone today slides to
 * the next whole one, and the end never rolls past midnight into the following day.
 */
internal fun recommendedWindow(
    startHour: Int?,
    durationHours: Int?,
    date: LocalDate,
    now: OffsetDateTime,
    zone: ZoneId
): Pair<java.time.LocalTime, java.time.LocalTime> {
    val suggested = java.time.LocalTime.of((startHour ?: 10).coerceIn(6, 21), 0)
    val here = now.atZoneSameInstant(zone)
    val start = if (date == here.toLocalDate() && !suggested.isAfter(here.toLocalTime())) {
        val nextHour = here.toLocalTime().hour + 1
        // Late enough that no whole hour is left today: keep the suggestion and let the save
        // refuse it, rather than wrapping to 00:00 which is a time that has also passed.
        if (nextHour > LATEST_SUGGESTED_START.hour) suggested else java.time.LocalTime.of(nextHour, 0)
    } else suggested

    val hours = (durationHours ?: 2).coerceIn(1, 8)
    val end = if (start.hour + hours >= 24) java.time.LocalTime.of(23, 30)
    else start.plusHours(hours.toLong())
    return start to end
}

/** When this entry starts, as a local date. Null only if the server sent something unparseable. */
internal fun ChildActivity.localDate(): LocalDate? =
    runCatching {
        OffsetDateTime.parse(startsAt).atZoneSameInstant(ZoneId.systemDefault()).toLocalDate()
    }.getOrNull()

class ActivityScheduleViewModel(
    private val childRepo: ChildRepository = ChildRepository()
) : ViewModel() {

    private companion object {
        /** Suggestions are regenerated once past this age — the rest of the time the cache serves. */
        const val MAX_RECOMMENDATION_AGE_DAYS = 3

        /** How far ahead activities are mirrored into the phone's calendar. */
        const val CALENDAR_WINDOW_DAYS = 180L

        /**
         * Bounds for finding linked activities that moved out of the window. This loads the child's
         * whole schedule - fine at family scale; add a by-id lookup if schedules reach thousands.
         */
        const val ALL_TIME_FROM = "2000-01-01T00:00:00Z"
        const val ALL_TIME_TO = "2100-01-01T00:00:00Z"
    }

    private val recJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    private val _uiState = MutableStateFlow(ActivityScheduleUiState())
    val uiState: StateFlow<ActivityScheduleUiState> = _uiState.asStateFlow()

    fun load(childId: String? = null) {
        viewModelScope.launch {
            val id = childId ?: ChildSelection.id.value
                ?: childRepo.getSelectedChild().getOrNull()?.id
                ?: run {
                    _uiState.update { it.copy(error = "no_child") }
                    return@launch
                }
            val name = childRepo.getSelectedChild().getOrNull()?.name ?: ""
            _uiState.update { it.copy(loading = true, error = null, childId = id, childName = name) }
            refresh()
        }
    }

    private suspend fun refresh() {
        val s = _uiState.value
        val childId = s.childId ?: return
        val monthStart = s.selectedDate.withDayOfMonth(1)
        val zone = ZoneId.systemDefault()
        val from = monthStart.atStartOfDay(zone).toOffsetDateTime().toString()
        val to = monthStart.plusMonths(1).atStartOfDay(zone).toOffsetDateTime().toString()

        val activities = childRepo.listActivities(childId, from, to).getOrElse { e ->
            _uiState.update { it.copy(loading = false, error = e.message) }
            return
        }
        val summary = childRepo.activitySummary(childId).getOrDefault(emptyList())
        _uiState.update { it.copy(loading = false, activities = activities, summary = summary) }
    }

    fun setView(view: ScheduleView) = _uiState.update { it.copy(view = view) }

    fun setQuery(v: String) = _uiState.update { it.copy(query = v) }

    /** Re-picking the active category clears the filter, matching the chips elsewhere in the app. */
    fun setCategoryFilter(category: String?) = _uiState.update {
        it.copy(categoryFilter = if (it.categoryFilter == category) null else category)
    }

    /** Only refetches when the month changes — moving between days in one month needs no round trip. */
    fun selectDate(date: LocalDate) {
        val previous = _uiState.value.selectedDate
        _uiState.update { it.copy(selectedDate = date) }
        if (previous.withDayOfMonth(1) != date.withDayOfMonth(1)) {
            viewModelScope.launch { refresh() }
        }
    }

    /**
     * Moves one unit of the current view. [selectDate] still decides whether that crossed a month
     * boundary and needs a refetch, so paging through days inside one month costs no round trip.
     */
    fun shift(direction: Long) {
        val s = _uiState.value
        selectDate(s.view.step(s.selectedDate, direction))
    }

    fun addActivity(
        title: String,
        category: String,
        startsAt: OffsetDateTime,
        endsAt: OffsetDateTime? = null,
        allDay: Boolean = false,
        locationName: String? = null,
        placeId: String? = null,
        lat: Double? = null,
        lng: Double? = null,
        notes: String? = null,
        estimatedCostText: String = "",
        onDone: () -> Unit = {}
    ) {
        val childId = _uiState.value.childId ?: return
        if (title.isBlank()) {
            _uiState.update { it.copy(error = "empty_title") }
            return
        }
        validateActivityWindow(startsAt, endsAt, OffsetDateTime.now())?.let { problem ->
            _uiState.update { it.copy(error = problem) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(saving = true, error = null) }
            childRepo.addActivity(
                childId = childId,
                title = title.trim(),
                category = category,
                startsAt = startsAt.toString(),
                endsAt = endsAt?.toString(),
                allDay = allDay,
                locationName = locationName,
                placeId = placeId,
                lat = lat,
                lng = lng,
                notes = notes,
                // Same satang parsing as an expense, so a plan and its eventual cost agree.
                estimatedCostSatang = ExpenseViewModel.parseSatang(estimatedCostText)
            ).onSuccess {
                _uiState.update { it.copy(saving = false) }
                refresh()
                onDone()
            }.onFailure { e ->
                _uiState.update { it.copy(saving = false, error = e.message) }
            }
        }
    }

    fun deleteActivity(id: String) {
        viewModelScope.launch {
            childRepo.deleteActivity(id)
                .onSuccess { refresh() }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }

    // --- Google Calendar sync ---------------------------------------------------------------------

    fun loadCalendarStatus(context: android.content.Context) {
        viewModelScope.launch {
            val status = com.lullabyai.app.data.calendar.DeviceCalendarSync.status(context)
            _uiState.update {
                it.copy(
                    calendarSyncEnabled = status.enabled,
                    calendarName = status.calendarName,
                    calendarLastSyncMillis = status.lastSyncMillis
                )
            }
            // Sync runs each time Activity opens, but only on phones where the parent turned it on:
            // a parent who never connected must not get a permission error on every visit.
            if (status.enabled) syncCalendarNow(context)
        }
    }

    /** The permission dialog was declined: say so instead of leaving the button looking broken. */
    fun reportCalendarPermissionDenied() =
        _uiState.update { it.copy(error = "calendar_permission_denied") }

    /** Turns sync on once permission is granted, then runs the first pass straight away. */
    fun enableCalendarSync(context: android.content.Context) {
        viewModelScope.launch {
            com.lullabyai.app.data.calendar.DeviceCalendarSync.enable(context)
                .onSuccess { name ->
                    _uiState.update {
                        it.copy(calendarSyncEnabled = true, calendarName = name, error = null)
                    }
                    syncCalendarNow(context)
                }
                .onFailure { e ->
                    _uiState.update { it.copy(error = e.message ?: "calendar_not_found") }
                }
        }
    }

    /** Turns sync off on this phone. Events already written stay in the calendar. */
    fun disableCalendarSync(context: android.content.Context) {
        viewModelScope.launch {
            com.lullabyai.app.data.calendar.DeviceCalendarSync.disable(context)
            _uiState.update {
                it.copy(
                    calendarSyncEnabled = false,
                    calendarName = null,
                    calendarReport = null,
                    calendarLastSyncMillis = null
                )
            }
        }
    }

    /** One pass - run when the screen opens and from the sync button. */
    fun syncCalendar(context: android.content.Context) {
        viewModelScope.launch { syncCalendarNow(context) }
    }

    private suspend fun syncCalendarNow(context: android.content.Context) {
        val s = _uiState.value
        val childId = s.childId ?: return
        if (s.calendarSyncing) return
        _uiState.update { it.copy(calendarSyncing = true) }

        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val window = childRepo.listActivities(
            childId,
            today.minusDays(1).atStartOfDay(zone).toOffsetDateTime().toString(),
            today.plusDays(CALENDAR_WINDOW_DAYS).atStartOfDay(zone).toOffsetDateTime().toString()
        ).getOrElse { e ->
            _uiState.update { it.copy(calendarSyncing = false, error = e.message) }
            return
        }

        val result = com.lullabyai.app.data.calendar.DeviceCalendarSync.run(
            context = context,
            childId = childId,
            windowItems = window,
            fetchByIds = { ids ->
                val wanted = ids.toSet()
                childRepo.listActivities(childId, ALL_TIME_FROM, ALL_TIME_TO)
                    .map { all -> all.filter { it.id in wanted } }
            },
            updateActivity = { id, title, startsAt, endsAt, allDay ->
                childRepo.updateActivity(id, title, startsAt, endsAt, allDay)
            },
            deleteActivity = { id -> childRepo.deleteActivity(id) }
        )

        val status = com.lullabyai.app.data.calendar.DeviceCalendarSync.status(context)
        _uiState.update { st ->
            st.copy(
                calendarSyncing = false,
                calendarLastSyncMillis = status.lastSyncMillis,
                calendarReport = result.getOrNull() ?: st.calendarReport,
                error = result.exceptionOrNull()?.message ?: st.error
            )
        }
        // A calendar edit may have changed activities: show what is actually stored now.
        refresh()
    }

    /**
     * Looks the place up in Google Places so the entry carries a real venue — its id, address and
     * coordinates — rather than a typed string that may or may not correspond to anywhere.
     *
     * [nearLat]/[nearLng] only bias the ranking; a named venue elsewhere still matches.
     */
    fun searchPlaces(query: String, nearLat: Double? = null, nearLng: Double? = null) {
        if (query.isBlank()) {
            _uiState.update { it.copy(placeResults = emptyList(), searchingPlaces = false) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(searchingPlaces = true) }
            val results = com.lullabyai.app.data.remote.PlacesClient
                .searchText(query, nearLat, nearLng)
            _uiState.update { s ->
                results.fold(
                    onSuccess = { s.copy(searchingPlaces = false, placeResults = it) },
                    onFailure = { e ->
                        s.copy(
                            searchingPlaces = false,
                            placeResults = emptyList(),
                            // Not configured is a setup fact, not a failure the parent caused.
                            error = if (e.message == "places_not_configured") "places_not_configured"
                            else e.message
                        )
                    }
                )
            }
        }
    }

    fun clearPlaceResults() = _uiState.update { it.copy(placeResults = emptyList()) }

    /**
     * Asks for the suggestion rail.
     *
     * Three signals go in: what this child is logged doing (care-log kinds over the last 90 days),
     * what is left of the month's budget, and real venues near the family. Without location the
     * call still runs and returns home activities only — a rail of things to do at home is more
     * use than an empty one.
     */
    /**
     * Shows the cached batch, and regenerates only when it has aged past the window.
     *
     * Called automatically when the screen opens, so the rail is simply there. [force] is the
     * manual "ขอใหม่", which skips the cache.
     */
    fun ensureRecommendations(
        context: android.content.Context,
        force: Boolean = false,
        /**
         * False on the automatic call when the screen opens: show a stored batch if there is one,
         * but never produce the first one unasked. A stored batch IS the record that this family
         * already opted in, which is why they never see the button again.
         */
        generateWhenMissing: Boolean = true
    ) {
        val s = _uiState.value
        val childId = s.childId ?: return
        if (s.loadingRecommendations) return

        viewModelScope.launch {
            if (!force) {
                val cached = childRepo
                    .getRecommendations(childId, MAX_RECOMMENDATION_AGE_DAYS)
                    .getOrNull()
                // Never asked before: leave the button standing.
                if (cached == null && !generateWhenMissing) return@launch
                if (cached != null) {
                    val items = runCatching {
                        cached.items?.let {
                            recJson.decodeFromJsonElement(
                                kotlinx.serialization.builtins.ListSerializer(
                                    com.lullabyai.app.data.remote.ActivityRecommendation.serializer()
                                ),
                                it
                            )
                        }
                    }.getOrNull().orEmpty()

                    _uiState.update {
                        it.copy(
                            recommendations = items,
                            recommendationsRequested = true,
                            locationUnavailable = cached.homeOnly,
                            generatedAt = cached.generatedAt
                        )
                    }
                    // Still inside the window and holding something: nothing more to spend.
                    if (cached.isFresh && items.isNotEmpty()) return@launch
                }
            }
            generateRecommendations(context)
        }
    }

    private fun generateRecommendations(context: android.content.Context) {
        val s = _uiState.value
        val childId = s.childId ?: return

        viewModelScope.launch {
            _uiState.update {
                it.copy(loadingRecommendations = true, recommendationsRequested = true, error = null)
            }

            val here = com.lullabyai.app.data.remote.CoarseLocation.lastKnown(context)

            // What the child actually does, over a window long enough to have a shape.
            val zone = java.time.ZoneId.systemDefault()
            val today = LocalDate.now()
            val logs = childRepo.listCareLogs(
                childId,
                today.minusDays(90).atStartOfDay(zone).toOffsetDateTime().toString(),
                today.plusDays(1).atStartOfDay(zone).toOffsetDateTime().toString()
            ).getOrDefault(emptyList())
            val likedKinds = logs.groupingBy { it.kind }.eachCount()

            // What is left to spend, so a suggestion cannot ignore the ceiling the parent set.
            val month = today.withDayOfMonth(1).toString()
            val remainingBaht = childRepo.budgetOverview(childId, month).getOrNull()
                ?.find { it.category == null }
                ?.takeIf { it.budgetSatang > 0 }
                ?.let { (it.budgetSatang - it.spentSatang) / 100 }

            com.lullabyai.app.data.remote.ActivityRecommender.recommend(
                childName = s.childName,
                likedKinds = likedKinds,
                remainingBudgetBaht = remainingBaht,
                lat = here?.first,
                lng = here?.second
            ).onSuccess { items ->
                _uiState.update {
                    it.copy(
                        loadingRecommendations = false,
                        recommendations = items,
                        locationUnavailable = here == null
                    )
                }
                // Cached for everyone caring for this child, so the next opener pays nothing.
                if (items.isNotEmpty()) {
                    childRepo.saveRecommendations(childId, items, homeOnly = here == null)
                }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(loadingRecommendations = false, error = e.message ?: "ai_failed")
                }
            }
        }
    }

    /**
     * Puts a suggestion on the calendar at the window the parent picked, keeping its venue and cost.
     *
     * The times come from the dialog rather than being chosen here: a suggested hour is a starting
     * point, and a family that can only go at four should not have to save at ten and then edit.
     */
    fun scheduleFromRecommendation(
        rec: com.lullabyai.app.data.remote.ActivityRecommendation,
        date: LocalDate,
        start: java.time.LocalTime,
        end: java.time.LocalTime,
        onDone: () -> Unit = {}
    ) {
        val childId = _uiState.value.childId ?: return
        val zone = java.time.ZoneId.systemDefault()
        val startsAt = date.atTime(start).atZone(zone).toOffsetDateTime()
        val endsAt = date.atTime(end).atZone(zone).toOffsetDateTime()

        // The same check as a hand-added entry, so an outing cannot be planned into yesterday.
        validateActivityWindow(startsAt, endsAt, OffsetDateTime.now())?.let { problem ->
            _uiState.update { it.copy(error = problem) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(saving = true, error = null) }
            childRepo.addActivity(
                childId = childId,
                title = rec.title,
                category = "family",
                startsAt = startsAt.toString(),
                endsAt = endsAt.toString(),
                allDay = false,
                locationName = if (rec.isPlace) rec.title else null,
                placeId = rec.placeId,
                lat = rec.lat,
                lng = rec.lng,
                notes = rec.reason.takeIf { it.isNotBlank() },
                estimatedCostSatang = rec.costBaht?.toLong()?.times(100)
            ).onSuccess {
                _uiState.update { it.copy(saving = false) }
                refresh()
                onDone()
            }.onFailure { e ->
                _uiState.update { it.copy(saving = false, error = e.message) }
            }
        }
    }
}

/** The four kinds the design colours, plus a catch-all. */
object ActivityCategories {
    data class Category(
        val key: String,
        val th: String,
        val en: String,
        val color: Color,
        val icon: ImageVector
    )

    val ALL = listOf(
        Category("appointment", "นัดหมายแพทย์", "Appointment", Color(0xFF3A59B0), Icons.Filled.LocalHospital),
        Category("vaccine", "วัคซีน", "Vaccine", Color(0xFF007064), Icons.Filled.Vaccines),
        Category("milestone", "วันสำคัญ", "Milestone", Color(0xFFF59E0B), Icons.Filled.Cake),
        Category("family", "กิจกรรมครอบครัว", "Family activity", Color(0xFF173DB8), Icons.Filled.FamilyRestroom),
        Category("other", "อื่นๆ", "Other", Color(0xFF747685), Icons.Filled.Event)
    )

    fun of(key: String): Category = ALL.find { it.key == key } ?: ALL.last()
    fun title(key: String, isThai: Boolean): String = of(key).let { if (isThai) it.th else it.en }
    fun color(key: String): Color = of(key).color
    fun icon(key: String): ImageVector = of(key).icon
}
