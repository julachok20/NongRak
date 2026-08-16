@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.lullabyai.app.data.model.ChildActivity
import com.lullabyai.app.ui.components.LullabyTopBar
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.screens.child.ChildBottomNav
import com.lullabyai.app.viewmodel.ActivityCategories
import com.lullabyai.app.viewmodel.ActivityScheduleViewModel
import com.lullabyai.app.viewmodel.ScheduleView
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId

/* "Serene Clinical", read from the design's own tailwind config. */
internal val SchBackground = Color(0xFFF8F9FF)
internal val SchSurface = Color(0xFFFFFFFF)
internal val SchSurfaceLow = Color(0xFFEFF4FF)
internal val SchSurfaceContainer = Color(0xFFE5EEFF)
internal val SchSurfaceHigh = Color(0xFFDCE9FF)
internal val SchOnSurface = Color(0xFF0B1C30)
internal val SchOnSurfaceVariant = Color(0xFF444654)
internal val SchOutline = Color(0xFF747685)
internal val SchOutlineVariant = Color(0xFFC4C5D6)
internal val SchPrimary = Color(0xFF173DB8)
internal val SchError = Color(0xFFBA1A1A)

private val ThaiMonths = listOf(
    "มกราคม", "กุมภาพันธ์", "มีนาคม", "เมษายน", "พฤษภาคม", "มิถุนายน",
    "กรกฎาคม", "สิงหาคม", "กันยายน", "ตุลาคม", "พฤศจิกายน", "ธันวาคม"
)
private val ThaiMonthsShort = listOf(
    "ม.ค.", "ก.พ.", "มี.ค.", "เม.ย.", "พ.ค.", "มิ.ย.",
    "ก.ค.", "ส.ค.", "ก.ย.", "ต.ค.", "พ.ย.", "ธ.ค."
)
/** Sunday first, matching the design's อา. … ส. header. */
private val ThaiWeekdays = listOf("อา.", "จ.", "อ.", "พ.", "พฤ.", "ศ.", "ส.")
private val EnWeekdays = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

internal fun monthTitle(d: LocalDate, isThai: Boolean): String =
    if (isThai) "${ThaiMonths[d.monthValue - 1]} ${d.year + 543}"
    else "${d.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${d.year}"

internal fun dayTitle(d: LocalDate, isThai: Boolean): String =
    if (isThai) "${d.dayOfMonth} ${ThaiMonthsShort[d.monthValue - 1]}"
    else "${d.dayOfMonth} ${d.month.name.take(3)}"

/** "09:00 น." for a timed entry, or the all-day label. */
internal fun timeLabel(a: ChildActivity, isThai: Boolean): String {
    if (a.allDay) return if (isThai) "ตลอดวัน" else "All day"
    val t = runCatching {
        OffsetDateTime.parse(a.startsAt).atZoneSameInstant(ZoneId.systemDefault()).toLocalTime()
    }.getOrNull() ?: return ""
    return "%02d:%02d".format(t.hour, t.minute) + if (isThai) " น." else ""
}

/**
 * Design: "Schedule with Family Activities & AI Recommendations" — ตารางนัดหมายและวันสำคัญ.
 *
 * The Day / Week / Month control changes only how the calendar is drawn. The timeline underneath
 * always belongs to the selected day, which is what the design shows and what makes the month view
 * useful: scan the dots, tap a day, read that day.
 */
@Composable
fun ActivityScheduleScreen(
    viewModel: ActivityScheduleViewModel,
    navController: NavController,
    onBack: () -> Unit,
    onAdd: () -> Unit
) {
    val s = LocalStrings.current
    val isThai = s.isThai
    val state by viewModel.uiState.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    // Whatever the answer, the suggestions are then loaded: without location the rail is
    // home-activities only rather than empty. Cache-aware, so granting permission does not spend a
    // model call when a batch from the last three days is already stored.
    val requestLocation = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { viewModel.ensureRecommendations(context) }

    /** The suggestion whose time window is being picked, or null when the dialog is closed. */
    var scheduling by remember {
        mutableStateOf<com.lullabyai.app.data.remote.ActivityRecommendation?>(null)
    }

    LaunchedEffect(Unit) { viewModel.load() }

    // Once this family has asked even once, a stored batch exists and the rail simply appears from
    // then on — regenerated in the background if it has passed three days. Nothing is generated
    // here, so a family who has never asked still sees the button rather than a surprise API call.
    LaunchedEffect(state.childId) {
        if (state.childId != null) {
            viewModel.ensureRecommendations(context, generateWhenMissing = false)
        }
    }

    Scaffold(
        containerColor = SchBackground,
        topBar = {
            LullabyTopBar(
                title = if (isThai) "ตารางนัดหมายและวันสำคัญ" else "Schedule",
                onBack = onBack,
                navController = navController
            )
        },
        bottomBar = { ChildBottomNav(navController = navController, currentTab = "child") },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAdd,
                containerColor = SchPrimary,
                contentColor = Color.White,
                shape = CircleShape
            ) { Icon(Icons.Filled.Add, if (isThai) "เพิ่มกิจกรรม" else "Add activity") }
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { ViewToggle(state.view, isThai) { viewModel.setView(it) } }

            item {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::setQuery,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Search, null, tint = SchOutline) },
                    placeholder = {
                        Text(
                            if (isThai) "ค้นหาการนัดหมาย, วัคซีน..." else "Search appointments…",
                            color = SchOutline
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SchPrimary,
                        unfocusedBorderColor = SchOutlineVariant,
                        focusedContainerColor = SchSurface,
                        unfocusedContainerColor = SchSurface,
                        cursorColor = SchPrimary
                    )
                )
            }

            item {
                CalendarCard(
                    view = state.view,
                    selected = state.selectedDate,
                    weekDays = state.weekDays,
                    marked = state.markedDays,
                    isThai = isThai,
                    onPrev = { viewModel.shift(-1) },
                    onNext = { viewModel.shift(1) },
                    onPick = viewModel::selectDate
                )
            }

            item {
                CategoryChips(
                    selected = state.categoryFilter,
                    isThai = isThai,
                    onPick = viewModel::setCategoryFilter
                )
            }

            item { UpcomingSummary(state.summary, isThai) }

            item {
                RecommendationRail(
                    items = state.recommendations,
                    loading = state.loadingRecommendations,
                    requested = state.recommendationsRequested,
                    locationUnavailable = state.locationUnavailable,
                    isThai = isThai,
                    onAsk = {
                        // Tapping the button is what raises the location request — the parent has
                        // just asked for ideas, so the prompt arrives with an obvious reason
                        // instead of ambushing them the moment the screen opened.
                        if (com.lullabyai.app.data.remote.CoarseLocation.granted(context)) {
                            viewModel.ensureRecommendations(context)
                        } else {
                            requestLocation.launch(android.Manifest.permission.ACCESS_COARSE_LOCATION)
                        }
                    },
                    // Deliberately skips the cache; the button above does not.
                    onRefresh = { viewModel.ensureRecommendations(context, force = true) },
                    // Never saved straight from the card: the parent sets when they are going.
                    onSchedule = { scheduling = it }
                )
            }

            item {
                Text(
                    (if (isThai) "นัดหมายและวันสำคัญ (" else "Schedule (") +
                        dayTitle(state.selectedDate, isThai) + ")",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = SchOnSurface,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            if (state.loading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = SchPrimary)
                    }
                }
            }

            state.error?.let { msg ->
                item {
                    Text(
                        when (msg) {
                            "no_child" -> if (isThai) "ยังไม่ได้เลือกเด็ก" else "No child selected"
                            "past_start" -> if (isThai)
                                "ไม่สามารถเพิ่มกิจกรรมย้อนหลังได้ กรุณาเลือกวันและเวลาในอนาคต"
                            else "You cannot add an activity in the past — pick a future date and time"
                            "end_before_start" -> if (isThai)
                                "เวลาสิ้นสุดต้องอยู่หลังเวลาเริ่ม"
                            else "The end time must be after the start time"
                            else -> msg
                        },
                        color = SchError,
                        fontSize = 13.sp
                    )
                }
            }

            items(state.dayActivities, key = { it.id }) { a ->
                TimelineEntry(a, isThai, onDelete = { viewModel.deleteActivity(a.id) })
            }

            if (!state.loading && state.dayActivities.isEmpty() && state.error == null) {
                item {
                    Text(
                        if (isThai) "ไม่มีรายการในวันนี้" else "Nothing scheduled for this day",
                        color = SchOnSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(28.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }

            item { Spacer(Modifier.height(72.dp)) }
        }
    }

    scheduling?.let { rec ->
        ScheduleRecommendationDialog(
            rec = rec,
            date = state.selectedDate,
            saving = state.saving,
            isThai = isThai,
            onDismiss = { scheduling = null },
            onConfirm = { start, end ->
                viewModel.scheduleFromRecommendation(rec, state.selectedDate, start, end) {
                    scheduling = null
                }
            }
        )
    }
}

/**
 * Picks when an AI suggestion actually happens.
 *
 * Opens on the model's suggested window — which it chose knowing the venue's opening hours — so the
 * common answer is one tap, and a family going at a different time changes it here rather than
 * saving and editing.
 */
@Composable
private fun ScheduleRecommendationDialog(
    rec: com.lullabyai.app.data.remote.ActivityRecommendation,
    date: LocalDate,
    saving: Boolean,
    isThai: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (LocalTime, LocalTime) -> Unit
) {
    val zone = remember { ZoneId.systemDefault() }
    val suggested = remember(rec, date) {
        com.lullabyai.app.viewmodel.recommendedWindow(
            startHour = rec.startHour,
            durationHours = rec.durationHours,
            date = date,
            now = OffsetDateTime.now(),
            zone = zone
        )
    }
    var start by remember(rec, date) { mutableStateOf(suggested.first) }
    var end by remember(rec, date) { mutableStateOf(suggested.second) }
    var endTouched by remember(rec, date) { mutableStateOf(false) }
    var picking by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SchSurface,
        title = { Text(rec.title, color = SchOnSurface, fontSize = 17.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    (if (isThai) "วันที่ " else "On ") + dayTitle(date, isThai) + " " +
                        (if (isThai) (date.year + 543).toString() else date.year.toString()),
                    fontSize = 13.sp,
                    color = SchOnSurfaceVariant
                )
                // Google's opening hours, so the chosen window can be judged against them.
                rec.hoursToday?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        (if (isThai) "เปิดวันนี้ " else "Open today ") + it,
                        fontSize = 12.sp,
                        color = SchOutline
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TimeField(
                        label = if (isThai) "เริ่ม" else "Start",
                        time = start,
                        isThai = isThai,
                        onClick = { picking = "start" },
                        modifier = Modifier.weight(1f)
                    )
                    TimeField(
                        label = if (isThai) "สิ้นสุด" else "End",
                        time = end,
                        isThai = isThai,
                        onClick = { picking = "end" },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(start, end) }, enabled = !saving) {
                Text(
                    if (isThai) "เพิ่มลงปฏิทิน" else "Add to calendar",
                    color = SchPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (isThai) "ยกเลิก" else "Cancel", color = SchOnSurfaceVariant)
            }
        }
    )

    picking?.let { which ->
        SchTimePickerDialog(
            title = if (which == "start") {
                if (isThai) "เวลาเริ่ม" else "Start time"
            } else {
                if (isThai) "เวลาสิ้นสุด" else "End time"
            },
            initial = if (which == "start") start else end,
            onDismiss = { picking = null },
            onPick = { picked ->
                if (which == "start") {
                    val length = java.time.Duration.between(start, end)
                    start = picked
                    // The end keeps the same length until it is set by hand, so moving the start
                    // cannot silently leave the end behind it.
                    if (!endTouched) {
                        end = picked.plusMinutes(length.toMinutes().coerceAtLeast(60))
                    }
                } else {
                    end = picked
                    endTouched = true
                }
                picking = null
            }
        )
    }
}

/** A labelled read-only time, opening a picker. */
@Composable
private fun TimeField(
    label: String,
    time: LocalTime,
    isThai: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, fontSize = 12.sp, color = SchOnSurfaceVariant)
        Text(
            "%02d:%02d".format(time.hour, time.minute) + if (isThai) " น." else "",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = SchOnSurface,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, SchOutlineVariant, RoundedCornerShape(8.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        )
    }
}

/** The one clock dialog, shared by Add Activity and the suggestion scheduler. */
@Composable
internal fun SchTimePickerDialog(
    title: String,
    initial: LocalTime,
    onDismiss: () -> Unit,
    onPick: (LocalTime) -> Unit
) {
    val picker = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = true
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SchSurface,
        title = { Text(title, color = SchOnSurface) },
        text = {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TimePicker(state = picker)
            }
        },
        confirmButton = {
            TextButton(onClick = { onPick(LocalTime.of(picker.hour, picker.minute)) }) {
                Text("OK", color = SchPrimary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = SchOutline) }
        }
    )
}

/** วัน / สัปดาห์ / เดือน — the design's segmented control. */
@Composable
private fun ViewToggle(current: ScheduleView, isThai: Boolean, onPick: (ScheduleView) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SchSurfaceLow)
            .border(1.dp, SchOutlineVariant.copy(0.3f), RoundedCornerShape(10.dp))
            .padding(4.dp)
    ) {
        ScheduleView.entries.forEach { v ->
            val selected = v == current
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (selected) SchSurface else Color.Transparent)
                    .clickable { onPick(v) }
                    .padding(vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    when (v) {
                        ScheduleView.DAY -> if (isThai) "วัน" else "Day"
                        ScheduleView.WEEK -> if (isThai) "สัปดาห์" else "Week"
                        ScheduleView.MONTH -> if (isThai) "เดือน" else "Month"
                    },
                    fontSize = 14.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) SchPrimary else SchOnSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CalendarCard(
    view: ScheduleView,
    selected: LocalDate,
    weekDays: List<LocalDate>,
    marked: Map<LocalDate, List<String>>,
    isThai: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onPick: (LocalDate) -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SchSurface)
            .border(1.dp, SchOutlineVariant.copy(0.4f), RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                // Stepping by day means the month alone no longer says where you are, so Day view
                // names the date itself.
                if (view == ScheduleView.DAY) {
                    dayTitle(selected, isThai) + " " +
                        (if (isThai) (selected.year + 543).toString() else selected.year.toString())
                } else monthTitle(selected, isThai),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = SchOnSurface,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onPrev) {
                Icon(Icons.Filled.ChevronLeft, null, tint = SchOnSurfaceVariant)
            }
            IconButton(onClick = onNext) {
                Icon(Icons.Filled.ChevronRight, null, tint = SchOnSurfaceVariant)
            }
        }

        // The Day view needs no grid: one date, stated plainly, with its dots.
        if (view == ScheduleView.DAY) {
            DayCell(
                date = selected,
                selected = true,
                inMonth = true,
                categories = marked[selected].orEmpty(),
                onPick = onPick,
                modifier = Modifier.fillMaxWidth()
            )
            return@Column
        }

        Row(Modifier.fillMaxWidth()) {
            (if (isThai) ThaiWeekdays else EnWeekdays).forEach { d ->
                Text(
                    d,
                    fontSize = 12.sp,
                    color = SchOnSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        val rows: List<List<LocalDate?>> = if (view == ScheduleView.WEEK) {
            listOf(weekDays)
        } else {
            // Month grid: pad the first row so the 1st lands under its weekday, Sunday-first.
            val first = selected.withDayOfMonth(1)
            val lead = first.dayOfWeek.value % 7
            val cells = buildList<LocalDate?> {
                repeat(lead) { add(null) }
                (1..selected.lengthOfMonth()).forEach { add(first.withDayOfMonth(it)) }
                while (size % 7 != 0) add(null)
            }
            cells.chunked(7)
        }

        rows.forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { d ->
                    if (d == null) {
                        Spacer(Modifier.weight(1f))
                    } else {
                        DayCell(
                            date = d,
                            selected = d == selected,
                            inMonth = d.month == selected.month,
                            categories = marked[d].orEmpty(),
                            onPick = onPick,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

/** One date. The dots are the categories present, so a day's shape is readable without opening it. */
@Composable
private fun DayCell(
    date: LocalDate,
    selected: Boolean,
    inMonth: Boolean,
    categories: List<String>,
    onPick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .padding(2.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) SchPrimary else Color.Transparent)
            .clickable { onPick(date) }
            .padding(vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            date.dayOfMonth.toString(),
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = when {
                selected -> Color.White
                !inMonth -> SchOnSurfaceVariant.copy(0.4f)
                else -> SchOnSurface
            }
        )
        if (categories.isNotEmpty()) {
            Spacer(Modifier.height(3.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                // Three dots is the ceiling: beyond that they stop being countable anyway.
                categories.distinct().take(3).forEach { c ->
                    Box(
                        Modifier
                            .size(4.dp)
                            .background(
                                if (selected) Color.White else ActivityCategories.color(c),
                                CircleShape
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryChips(selected: String?, isThai: Boolean, onPick: (String?) -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Chip(
            label = if (isThai) "ทั้งหมด" else "All",
            dot = null,
            selected = selected == null,
            onClick = { onPick(null) }
        )
        ActivityCategories.ALL.forEach { c ->
            Chip(
                label = if (isThai) c.th else c.en,
                dot = c.color,
                selected = selected == c.key,
                onClick = { onPick(c.key) }
            )
        }
    }
}

@Composable
private fun Chip(label: String, dot: Color?, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) SchSurfaceHigh else SchSurface)
            .border(
                1.dp,
                if (selected) SchPrimary.copy(0.4f) else SchOutlineVariant.copy(0.5f),
                RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        dot?.let {
            Box(Modifier.size(8.dp).background(it, CircleShape))
            Spacer(Modifier.width(6.dp))
        }
        Text(
            label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) SchOnSurface else SchOnSurfaceVariant
        )
    }
}

/** "สรุปภาพรวม 7 วันข้างหน้า" — every category listed, including the empty ones. */
@Composable
private fun UpcomingSummary(
    summary: List<com.lullabyai.app.data.model.ActivityCategoryCount>,
    isThai: Boolean
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SchSurfaceLow)
            .border(1.dp, SchOutlineVariant.copy(0.3f), RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Insights, null, tint = SchPrimary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                if (isThai) "สรุปภาพรวม 7 วันข้างหน้า" else "Next 7 days",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = SchOnSurface
            )
        }
        ActivityCategories.ALL.filter { it.key != "other" }.forEach { c ->
            val n = summary.find { it.category == c.key }?.entries ?: 0
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(c.icon, null, tint = c.color, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (isThai) c.th else c.en,
                    fontSize = 13.sp,
                    color = SchOnSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    if (isThai) "$n รายการ" else "$n",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = SchOnSurface
                )
            }
        }
    }
}

/**
 * "AI แนะนำกิจกรรมและที่เที่ยว" — the design's horizontal card rail.
 *
 * Nothing is fetched until asked for: it costs a Places call and a model call, and a rail nobody
 * looks at should not spend either.
 */
@Composable
private fun RecommendationRail(
    items: List<com.lullabyai.app.data.remote.ActivityRecommendation>,
    loading: Boolean,
    requested: Boolean,
    locationUnavailable: Boolean,
    isThai: Boolean,
    onAsk: () -> Unit,
    onRefresh: () -> Unit,
    onSchedule: (com.lullabyai.app.data.remote.ActivityRecommendation) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.AutoAwesome, null,
                tint = SchPrimary, modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (isThai) "AI แนะนำกิจกรรมและที่เที่ยว" else "AI activity ideas",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = SchOnSurface,
                modifier = Modifier.weight(1f)
            )
            if (requested && !loading) {
                Text(
                    if (isThai) "ขอใหม่" else "Refresh",
                    fontSize = 12.sp,
                    color = SchPrimary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable(onClick = onRefresh)
                )
            }
        }

        when {
            loading -> Row(
                Modifier.fillMaxWidth().padding(vertical = 18.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    color = SchPrimary, strokeWidth = 2.dp, modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    if (isThai) "กำลังหาไอเดีย…" else "Finding ideas…",
                    fontSize = 13.sp, color = SchOnSurfaceVariant
                )
            }

            !requested -> AskCard(isThai, onAsk)

            items.isEmpty() -> Text(
                if (isThai) "ยังไม่มีคำแนะนำ ลองขอใหม่อีกครั้ง"
                else "No suggestions yet — try again",
                fontSize = 13.sp,
                color = SchOnSurfaceVariant
            )

            else -> {
                if (locationUnavailable) {
                    // Explains the absence of named venues. Home and outdoor ideas are still here,
                    // so this is a narrower statement than "no suggestions".
                    Text(
                        if (isThai) "ไม่ได้เปิดตำแหน่ง จึงยังไม่แนะนำสถานที่เฉพาะเจาะจง"
                        else "Location is off, so no specific venues are suggested",
                        fontSize = 12.sp,
                        color = SchOutline
                    )
                } else if (items.none { it.isPlace }) {
                    // Location was available and still nothing came back, which means the venue
                    // lookup itself failed. Saying so beats a rail of home ideas with no
                    // explanation — that silence is indistinguishable from the AI simply having
                    // preferred staying in.
                    Text(
                        if (isThai) "ยังดึงข้อมูลสถานที่ใกล้บ้านไม่ได้ จึงแนะนำได้เฉพาะกิจกรรมที่บ้าน — ลองกด ขอใหม่"
                        else "Nearby places could not be fetched, so only at-home ideas are shown — try Refresh",
                        fontSize = 12.sp,
                        color = SchOutline
                    )
                }
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items.forEach { rec -> RecommendationCard(rec, isThai, onSchedule) }
                    AddIdeaCard(isThai, onRefresh)
                }
            }
        }
    }
}

@Composable
private fun AskCard(isThai: Boolean, onAsk: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SchPrimary.copy(0.06f))
            .border(1.dp, SchPrimary.copy(0.2f), RoundedCornerShape(14.dp))
            .clickable(onClick = onAsk)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.AutoAwesome, null, tint = SchPrimary)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (isThai) "ขอไอเดียกิจกรรม" else "Get activity ideas",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = SchPrimary
            )
            Text(
                if (isThai) "จากสิ่งที่ลูกชอบ งบที่เหลือ และสถานที่ใกล้คุณ"
                else "From what your child enjoys, your remaining budget and places near you",
                fontSize = 12.sp,
                color = SchOnSurfaceVariant
            )
        }
    }
}

@Composable
private fun RecommendationCard(
    rec: com.lullabyai.app.data.remote.ActivityRecommendation,
    isThai: Boolean,
    onSchedule: (com.lullabyai.app.data.remote.ActivityRecommendation) -> Unit
) {
    Column(
        Modifier
            .width(220.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(SchSurface)
            .border(1.dp, SchOutlineVariant.copy(0.4f), RoundedCornerShape(14.dp))
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(72.dp)
                .background(if (rec.isPlace) SchSurfaceContainer else SchPrimary.copy(0.08f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (rec.isPlace) Icons.Filled.Place else Icons.Filled.Home,
                null,
                tint = SchPrimary,
                modifier = Modifier.size(30.dp)
            )
        }
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                if (rec.isPlace) (if (isThai) "นอกบ้าน" else "GOING OUT")
                else (if (isThai) "ที่บ้าน" else "AT HOME"),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = SchPrimary
            )
            Text(
                rec.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = SchOnSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (rec.reason.isNotBlank()) {
                Text(
                    rec.reason,
                    fontSize = 11.sp,
                    color = SchOnSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            // Opening hours as Google phrases them: an outing card that does not say when the
            // place is open is asking the parent to go and look it up anyway.
            rec.hoursToday?.takeIf { it.isNotBlank() }?.let {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Schedule, null,
                        tint = SchOutline, modifier = Modifier.size(12.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        it,
                        fontSize = 10.sp,
                        color = SchOnSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            rec.costBaht?.let {
                Text(
                    if (it <= 0) (if (isThai) "ฟรี" else "Free")
                    else (if (isThai) "ประมาณ " else "About ") + "%,d ฿".format(it),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = SchPrimary
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                if (isThai) "+ เพิ่มลงปฏิทิน" else "+ Add to calendar",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = SchPrimary,
                modifier = Modifier.clickable { onSchedule(rec) }
            )
        }
    }
}

@Composable
private fun AddIdeaCard(isThai: Boolean, onAsk: () -> Unit) {
    Column(
        Modifier
            .width(120.dp)
            .height(180.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(SchSurfaceLow)
            .border(1.dp, SchOutlineVariant.copy(0.6f), RoundedCornerShape(14.dp))
            .clickable(onClick = onAsk),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Filled.AddCircle, null, tint = SchPrimary)
        Spacer(Modifier.height(6.dp))
        Text(
            if (isThai) "ขอไอเดียเพิ่ม" else "More ideas",
            fontSize = 12.sp,
            color = SchPrimary,
            textAlign = TextAlign.Center
        )
    }
}

/** One row of the day's timeline: a coloured node, then the card. */
@Composable
private fun TimelineEntry(a: ChildActivity, isThai: Boolean, onDelete: () -> Unit) {
    val cat = ActivityCategories.of(a.category)
    var confirmDelete by remember { mutableStateOf(false) }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(44.dp).background(cat.color.copy(0.12f), CircleShape),
            contentAlignment = Alignment.Center
        ) { Icon(cat.icon, null, tint = cat.color, modifier = Modifier.size(22.dp)) }

        Spacer(Modifier.width(12.dp))

        Column(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(SchSurface)
                .border(1.dp, SchOutlineVariant.copy(0.3f), RoundedCornerShape(12.dp))
                .clickable { confirmDelete = true }
                .padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    timeLabel(a, isThai),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = cat.color,
                    modifier = Modifier
                        .background(cat.color.copy(0.10f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                )
                Spacer(Modifier.weight(1f))
                Text(
                    (if (isThai) cat.th else cat.en).uppercase(),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = cat.color
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                a.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = SchOnSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            a.locationName?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.LocationOn, null,
                        tint = SchOutline, modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(it, fontSize = 12.sp, color = SchOnSurfaceVariant, maxLines = 1)
                }
            }
            a.estimatedCostSatang?.takeIf { it > 0 }?.let {
                Spacer(Modifier.height(3.dp))
                Text(
                    (if (isThai) "ประมาณ " else "About ") + "%,d ฿".format(it / 100),
                    fontSize = 12.sp,
                    color = SchOnSurfaceVariant
                )
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = SchSurface,
            title = {
                Text(if (isThai) "ลบรายการนี้?" else "Delete this entry?", color = SchOnSurface)
            },
            text = { Text(a.title, color = SchOnSurfaceVariant) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) {
                    Text(
                        if (isThai) "ลบ" else "Delete",
                        color = SchError,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(if (isThai) "ยกเลิก" else "Cancel", color = SchOnSurfaceVariant)
                }
            }
        )
    }
}
