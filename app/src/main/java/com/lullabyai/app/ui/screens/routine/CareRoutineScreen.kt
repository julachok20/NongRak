@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.routine

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.lullabyai.app.data.routine.CareRoutine
import com.lullabyai.app.data.routine.CareRoutineCategories
import com.lullabyai.app.data.routine.CareRoutineDuty
import com.lullabyai.app.data.routine.CareRoutinePlan
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.screens.child.AI_GUIDANCE_ROUTE
import com.lullabyai.app.ui.screens.child.ChildBottomNav
import com.lullabyai.app.ui.screens.child.hubAgeMonthsOnly
import com.lullabyai.app.util.AiDailyBriefHelper
import com.lullabyai.app.viewmodel.AccessRoleLabels
import com.lullabyai.app.viewmodel.CareRoutineViewModel

/**
 * "กิจวัตรการดูแล (Daily Care Routine & Schedule)" — the Stitch screen of the same name.
 *
 * The child's repeating day with today's ticks on it. Parents shape the plan; whoever is on duty
 * ticks it. The two are deliberately different rights, so a caregiver sees this same timeline with
 * the edit affordances absent rather than a different screen that drifts out of step.
 *
 * The design shows five kinds of row; the plan produces two more (a deliberate skip, and a slot
 * whose time passed with nothing recorded), which reuse the same card in the design's palette.
 */
@Composable
fun CareRoutineScreen(
    childId: String,
    navController: NavController,
    vm: CareRoutineViewModel = viewModel()
) {
    val s = LocalStrings.current
    val isThai = s.isThai
    val c = routineColors()
    val state by vm.uiState.collectAsState()

    var editing by remember { mutableStateOf<CareRoutine?>(null) }
    var adding by remember { mutableStateOf(false) }
    var noting by remember { mutableStateOf<CareRoutinePlan.Slot?>(null) }
    var acting by remember { mutableStateOf<CareRoutinePlan.Slot?>(null) }
    var addSheet by remember { mutableStateOf(false) }

    LaunchedEffect(childId) { vm.load(childId) }

    val nowMinute = remember(state.day) {
        java.time.LocalTime.now().let { it.hour * 60 + it.minute }
    }

    Scaffold(
        containerColor = c.page,
        topBar = {
            RoutineTopBar(
                isThai = isThai,
                c = c,
                childName = state.childName,
                ageLabel = hubAgeMonthsOnly(state.child?.birthdate, isThai),
                dateLabel = routineDateLabel(state.date, isThai),
                onBack = { navController.popBackStack() },
                onNotifications = { navController.navigate(Screen.Notifications.route) }
            )
        },
        bottomBar = { ChildBottomNav(navController = navController, currentTab = "child") }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 12.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            DayProgressCard(day = state.day, isThai = isThai)

            state.onDuty?.let { duty ->
                OnDutyCard(
                    duty = duty,
                    isThai = isThai,
                    c = c,
                    onChat = { navController.navigate(Screen.CareChat.route(duty.person.userId)) }
                )
            }

            state.error?.let { error ->
                RoutineErrorCard(text = routineErrorText(error, isThai), isThai = isThai) { vm.clearError() }
            }

            TimelineHeader(
                isThai = isThai,
                c = c,
                count = state.day.total,
                onSpecialRecord = {
                    state.childId.takeIf { it.isNotBlank() }?.let { id ->
                        navController.navigate(
                            if (state.canEdit) Screen.CareHandoff.route(id)
                            else Screen.CareHandoverChecklist.route(id)
                        )
                    }
                }
            )

            if (state.loading) {
                Box(Modifier.fillMaxWidth().padding(vertical = 28.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(28.dp), color = c.blue, strokeWidth = 3.dp)
                }
            } else if (state.day.total == 0) {
                EmptyRoutineCard(isThai = isThai, c = c, canEdit = state.canEdit) { adding = true }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                state.day.slots.forEach { slot ->
                    RoutineSlotCard(
                        slot = slot,
                        isThai = isThai,
                        c = c,
                        canEdit = state.canEdit,
                        busy = state.saving,
                        nowMinute = nowMinute,
                        isNextUp = slot.entry.id == state.day.next?.entry?.id,
                        onDone = { vm.tick(slot.entry.id, done = true) },
                        onNote = { noting = slot },
                        onEdit = { editing = state.routines.firstOrNull { r -> r.id == slot.entry.id } },
                        onOpenActions = { acting = slot }
                    )
                }
            }

            AiRoutineAdviceCard(
                isThai = isThai,
                c = c,
                child = state.child,
                onOpenFull = { navController.navigate(AI_GUIDANCE_ROUTE) }
            )

            PrimaryRoutineButton(isThai = isThai, c = c) {
                if (state.canEdit) addSheet = true
                else state.childId.takeIf { it.isNotBlank() }
                    ?.let { navController.navigate(Screen.CareHandoverChecklist.route(it)) }
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (addSheet) {
        ModalBottomSheet(onDismissRequest = { addSheet = false }, containerColor = c.card) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
                Text(
                    if (isThai) "เพิ่มกิจวัตรใหม่ / บันทึกการส่งต่อพิเศษ"
                    else "New routine item / special handover",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = c.inkStrong
                )
                Spacer(Modifier.height(12.dp))
                SheetAction(
                    icon = Icons.Filled.Add,
                    label = if (isThai) "เพิ่มกิจวัตรใหม่ในตาราง" else "Add an item to the timeline",
                    c = c
                ) { addSheet = false; adding = true }
                SheetAction(
                    icon = Icons.Filled.Schedule,
                    label = if (isThai) "บันทึกการส่งต่อพิเศษ" else "Record a special handover",
                    c = c
                ) {
                    addSheet = false
                    state.childId.takeIf { it.isNotBlank() }?.let { id ->
                        navController.navigate(
                            if (state.canEdit) Screen.CareHandoff.route(id)
                            else Screen.CareHandoverChecklist.route(id)
                        )
                    }
                }
            }
        }
    }

    acting?.let { slot ->
        SlotActionsSheet(
            slot = slot,
            isThai = isThai,
            c = c,
            canEdit = state.canEdit,
            onDismiss = { acting = null },
            onDone = { acting = null; vm.tick(slot.entry.id, done = true) },
            onSkip = { acting = null; noting = slot },
            onUndo = { acting = null; vm.undo(slot.entry.id) },
            onEdit = {
                acting = null
                editing = state.routines.firstOrNull { r -> r.id == slot.entry.id }
            }
        )
    }

    if (adding || editing != null) {
        RoutineEditorDialog(
            existing = editing,
            isThai = isThai,
            onDismiss = { adding = false; editing = null },
            onSave = { time, category, title, detail, prepNote ->
                val current = editing
                if (current == null) vm.addRoutine(time, category, title, detail, prepNote)
                else vm.updateRoutine(current.id, time, category, title, detail, prepNote)
                adding = false
                editing = null
            },
            onRemove = editing?.let { current ->
                {
                    vm.removeRoutine(current.id)
                    editing = null
                }
            }
        )
    }

    noting?.let { slot ->
        RoutineNoteDialog(
            slot = slot,
            isThai = isThai,
            onDismiss = { noting = null },
            onDone = { note -> vm.tick(slot.entry.id, done = true, note = note); noting = null },
            onSkip = { note -> vm.tick(slot.entry.id, done = false, note = note); noting = null }
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Palette. The design is a light, blue-tinted sheet; in dark mode the same structure is drawn in
// theme colours so the screen does not stay white while the rest of the app turns dark.
// ---------------------------------------------------------------------------------------------

private class RoutineColors(
    val dark: Boolean,
    val page: Color,
    val card: Color,
    val cardBorder: Color,
    val inkStrong: Color,
    val ink: Color,
    val body: Color,
    val muted: Color,
    val hairline: Color,
    val blue: Color,
    val blueInk: Color,
    val blueSoft: Color,
    val blueLine: Color,
    val green: Color
)

@Composable
private fun routineColors(): RoutineColors {
    val dark = com.lullabyai.app.ui.theme.LocalDarkTheme.current
    val scheme = MaterialTheme.colorScheme
    return RoutineColors(
        dark = dark,
        page = if (dark) scheme.background else Color(0xFFF4F8FD),
        card = if (dark) scheme.surface else Color.White,
        cardBorder = if (dark) scheme.outlineVariant else Color(0xFFF1F5F9),
        inkStrong = if (dark) scheme.onSurface else Color(0xFF0F172A),
        ink = if (dark) scheme.onSurface else Color(0xFF1E293B),
        body = if (dark) scheme.onSurfaceVariant else Color(0xFF64748B),
        muted = if (dark) scheme.onSurfaceVariant else Color(0xFF94A3B8),
        hairline = if (dark) scheme.outlineVariant else Color(0xFFF1F5F9),
        blue = Color(0xFF2563EB),
        blueInk = if (dark) Color(0xFF93C5FD) else Color(0xFF1E3A8A),
        blueSoft = if (dark) Color(0xFF2563EB).copy(alpha = 0.18f) else Color(0xFFEFF6FF),
        blueLine = if (dark) Color(0xFF2563EB).copy(alpha = 0.35f) else Color(0xFFDBEAFE),
        green = Color(0xFF16A34A)
    )
}

/** The design's blue-tinted card shadow. */
private fun Modifier.softShadow(shape: Shape) = this.shadow(
    elevation = 5.dp,
    shape = shape,
    ambientColor = Color(0xFF2563EB).copy(alpha = 0.10f),
    spotColor = Color(0xFF2563EB).copy(alpha = 0.10f)
)

// ---------------------------------------------------------------------------------------------
// Header
// ---------------------------------------------------------------------------------------------

@Composable
private fun RoutineTopBar(
    isThai: Boolean,
    c: RoutineColors,
    childName: String,
    ageLabel: String,
    dateLabel: String,
    onBack: () -> Unit,
    onNotifications: () -> Unit
) {
    Surface(color = c.page) {
        // The app draws edge to edge, so this bar owes the status bar its own room — without it the
        // title sits under the clock.
        Column(Modifier.statusBarsPadding()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(c.card)
                        .border(1.dp, c.blueLine.copy(alpha = 0.8f), CircleShape)
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.ChevronLeft, null, Modifier.size(20.dp), tint = c.ink)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (isThai) "กิจวัตรการดูแล (Care Routine)" else "Care Routine",
                            modifier = Modifier.weight(1f, fill = false),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = c.inkStrong,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (ageLabel.isNotBlank()) {
                            Spacer(Modifier.width(6.dp))
                            Pill(
                                text = ageLabel,
                                bg = c.blueSoft,
                                fg = c.blue,
                                border = c.blueLine,
                                fontSize = 9.sp,
                                weight = FontWeight.SemiBold
                            )
                        }
                    }
                    Text(
                        if (childName.isBlank()) {
                            if (isThai) "ตารางกิจวัตรและการส่งต่อการดูแล"
                            else "Routine and handover schedule"
                        } else if (isThai) "ตารางกิจวัตรและการส่งต่อสำหรับน้อง$childName"
                        else "Routine and handover schedule for $childName",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Light,
                        color = c.body,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(8.dp))
                if (dateLabel.isNotBlank()) {
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(100.dp))
                            .background(c.card)
                            .border(1.dp, c.blueLine.copy(alpha = 0.8f), RoundedCornerShape(100.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.CalendarMonth, null, Modifier.size(14.dp), tint = c.blue)
                        Spacer(Modifier.width(4.dp))
                        Text(dateLabel, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = c.ink)
                    }
                    Spacer(Modifier.width(8.dp))
                }
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(c.card)
                        .border(1.dp, c.blueLine.copy(alpha = 0.8f), CircleShape)
                        .clickable(onClick = onNotifications),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Notifications, null, Modifier.size(16.dp), tint = c.ink)
                }
            }
            HorizontalDivider(color = c.blueLine.copy(alpha = 0.6f))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Today's progress
// ---------------------------------------------------------------------------------------------

@Composable
private fun DayProgressCard(day: CareRoutinePlan.Day, isThai: Boolean) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 10.dp,
                shape = shape,
                ambientColor = Color(0xFF2563EB).copy(alpha = 0.35f),
                spotColor = Color(0xFF2563EB).copy(alpha = 0.35f)
            )
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF1D4ED8), Color(0xFF2563EB), Color(0xFF1E40AF))
                )
            )
    ) {
        // The design's soft light bleed in the top-right corner.
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset(x = 24.dp, y = (-24).dp)
                .size(112.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.10f))
        )
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.20f))
                        .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Schedule, null, Modifier.size(16.dp), tint = Color.White)
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (isThai) "ความคืบหน้ากิจวัตรวันนี้" else "Today's routine progress",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    Text(
                        if (isThai) "เสร็จสิ้น ${day.done} จาก ${day.total} กิจกรรม"
                        else "${day.done} of ${day.total} done",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Light,
                        color = Color(0xFFDBEAFE).copy(alpha = 0.9f)
                    )
                }
                Row(
                    Modifier
                        .clip(RoundedCornerShape(100.dp))
                        .background(Color.White.copy(alpha = 0.15f))
                        .border(1.dp, Color.White.copy(alpha = 0.20f), RoundedCornerShape(100.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${day.percent}%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (isThai) "สำเร็จ" else "done",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Light,
                        color = Color(0xFFDBEAFE)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(100.dp))
                    .background(Color.White.copy(alpha = 0.20f))
                    .padding(2.dp)
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth((day.percent / 100f).coerceIn(0f, 1f))
                        .clip(RoundedCornerShape(100.dp))
                        .background(Color.White)
                )
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.15f))
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF34D399))
                )
                Spacer(Modifier.width(4.dp))
                val current = day.current
                Text(
                    text = if (current == null) {
                        dayPeriodLabel(java.time.LocalTime.now().hour * 60, isThai)
                    } else {
                        val category = CareRoutineCategories.label(current.entry.category, isThai)
                        dayPeriodLabel(current.entry.minuteOfDay, isThai) +
                            if (isThai) " ($category กำลังดำเนินการ)" else " ($category in progress)"
                    },
                    modifier = Modifier.weight(1f),
                    fontSize = 10.sp,
                    color = Color(0xFFDBEAFE),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.width(8.dp))
                day.next?.let { next ->
                    Text(
                        (if (isThai) "ถัดไป: " else "Next: ") +
                            timeLabel(next.entry.minuteOfDay, isThai) + " " +
                            CareRoutineCategories.label(next.entry.category, isThai),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Who has the child now
// ---------------------------------------------------------------------------------------------

@Composable
private fun OnDutyCard(
    duty: CareRoutineDuty.OnDuty,
    isThai: Boolean,
    c: RoutineColors,
    onChat: () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .softShadow(shape)
            .clip(shape)
            .background(c.card)
            .border(1.dp, c.blueLine, shape)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(36.dp)) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(Color(0xFFECFDF5))
                    .border(1.dp, Color(0xFFA7F3D0), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (!duty.person.avatarUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = duty.person.avatarUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().clip(CircleShape)
                    )
                } else {
                    Icon(Icons.Filled.Person, null, Modifier.size(18.dp), tint = Color(0xFF047857))
                }
            }
            if (duty.isOnDutyNow) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(c.card)
                        .padding(1.5.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF16A34A))
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    duty.person.name,
                    modifier = Modifier.weight(1f, fill = false),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = c.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.width(6.dp))
                Pill(
                    text = if (duty.isOnDutyNow) (if (isThai) "ผู้ดูแลปัจจุบัน" else "On duty now")
                    else (if (isThai) "ผู้ดูแลที่ได้รับมอบหมาย" else "Assigned caregiver"),
                    bg = Color(0xFFECFDF5),
                    fg = Color(0xFF047857),
                    border = Color(0xFFD1FAE5),
                    fontSize = 9.sp,
                    weight = FontWeight.Medium
                )
            }
            Text(
                onDutySubtitle(duty, isThai),
                fontSize = 10.sp,
                fontWeight = FontWeight.Light,
                color = c.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(6.dp))
        Row(
            Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(c.blueSoft)
                .border(1.dp, c.blueLine, RoundedCornerShape(12.dp))
                .clickable(onClick = onChat)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.ChatBubbleOutline, null, Modifier.size(14.dp), tint = c.blue)
            Spacer(Modifier.width(4.dp))
            Text(
                if (isThai) "แชท/โทร" else "Chat",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = c.blue
            )
        }
    }
}

/** "พี่เลี้ยงเด็ก (Nanny) • กะเวลา: 08:00 - 18:00 น." — the role, then whatever is known about now. */
private fun onDutySubtitle(duty: CareRoutineDuty.OnDuty, isThai: Boolean): String {
    val parts = mutableListOf(AccessRoleLabels.title(duty.person.role, isThai))
    val start = duty.shiftStart
    if (start != null) {
        val end = duty.shiftEnd
        val shift = CareRoutinePlan.formatMinuteOfDay(start) +
            if (end != null) " - " + CareRoutinePlan.formatMinuteOfDay(end) else ""
        parts += (if (isThai) "กะเวลา: $shift น." else "Shift: $shift")
    }
    duty.lastTickMinute?.let { minute ->
        parts += (if (isThai) "บันทึกล่าสุด " else "Last record ") +
            CareRoutinePlan.formatMinuteOfDay(minute) + (if (isThai) " น." else "")
    }
    return parts.filter { it.isNotBlank() }.joinToString(" • ")
}

// ---------------------------------------------------------------------------------------------
// Timeline
// ---------------------------------------------------------------------------------------------

@Composable
private fun TimelineHeader(isThai: Boolean, c: RoutineColors, count: Int, onSpecialRecord: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.CalendarMonth, null, Modifier.size(16.dp), tint = c.blue)
        Spacer(Modifier.width(8.dp))
        Text(
            if (isThai) "ตารางกิจวัตรตามช่วงเวลา ($count รายการ)" else "Timeline ($count items)",
            modifier = Modifier.weight(1f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = c.ink
        )
        Text(
            if (isThai) "+ บันทึกพิเศษ" else "+ Special record",
            modifier = Modifier
                .clip(RoundedCornerShape(100.dp))
                .background(c.blueSoft)
                .border(1.dp, c.blueLine, RoundedCornerShape(100.dp))
                .clickable(onClick = onSpecialRecord)
                .padding(horizontal = 8.dp, vertical = 2.dp),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = c.blue
        )
    }
}

@Composable
private fun RoutineSlotCard(
    slot: CareRoutinePlan.Slot,
    isThai: Boolean,
    c: RoutineColors,
    canEdit: Boolean,
    busy: Boolean,
    nowMinute: Int,
    isNextUp: Boolean,
    onDone: () -> Unit,
    onNote: () -> Unit,
    onEdit: () -> Unit,
    onOpenActions: () -> Unit
) {
    val current = slot.status == CareRoutinePlan.Status.CURRENT
    val shape = RoundedCornerShape(16.dp)

    Box(Modifier.fillMaxWidth().padding(top = if (current) 10.dp else 0.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .then(if (slot.status == CareRoutinePlan.Status.DONE) Modifier.alpha(0.9f) else Modifier)
                .softShadow(shape)
                .clip(shape)
                .then(
                    if (!current) Modifier.background(c.card)
                    else if (c.dark) Modifier.background(c.blueSoft)
                    else Modifier.background(
                        Brush.horizontalGradient(listOf(Color(0xFFEFF6FF), Color(0xFFF1F3FF)))
                    )
                )
                .border(
                    width = if (current) 2.dp else 1.dp,
                    color = if (current) c.blue else c.cardBorder,
                    shape = shape
                )
                .then(if (current) Modifier else Modifier.clickable(onClick = onOpenActions))
                .padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            SlotMarker(status = slot.status, c = c)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        timeLabel(slot.entry.minuteOfDay, isThai),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (current) c.blueInk else c.ink
                    )
                    Spacer(Modifier.width(6.dp))
                    val pill = categoryPill(slot.entry.category)
                    Pill(
                        text = CareRoutineCategories.label(slot.entry.category, isThai),
                        bg = pill.bg,
                        fg = pill.fg,
                        border = pill.border,
                        fontSize = 9.sp,
                        weight = if (current) FontWeight.SemiBold else FontWeight.Medium,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (!current) {
                        Spacer(Modifier.width(6.dp))
                        StatusPill(status = slot.status, isThai = isThai)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    slot.entry.title,
                    fontSize = 12.sp,
                    fontWeight = if (current) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (current) c.inkStrong else c.ink
                )
                if (slot.entry.detail.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        slot.entry.detail,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Light,
                        color = if (current) c.ink.copy(alpha = 0.85f) else c.body,
                        lineHeight = 16.sp
                    )
                }

                if (current) {
                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(color = c.blueLine.copy(alpha = 0.8f))
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Row(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(c.blue)
                                .clickable(enabled = !busy, onClick = onDone)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Check, null, Modifier.size(14.dp), tint = Color.White)
                            Spacer(Modifier.width(4.dp))
                            Text(
                                currentActionLabel(slot.entry.category, isThai),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                maxLines = 1
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (isThai) "+ โน้ต" else "+ Note",
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(c.card)
                                .border(1.dp, Color(0xFFBFDBFE), RoundedCornerShape(12.dp))
                                .clickable(enabled = !busy, onClick = onNote)
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = c.ink
                        )
                    }
                } else {
                    val note = slot.log?.note?.takeIf { it.isNotBlank() }
                    val footer = when {
                        slot.log != null -> recordedByLabel(slot.log, isThai)
                        // "เตรียม: …" when the item says what to have ready; otherwise how long
                        // there is left, so the line is never blank.
                        else -> prepLabel(slot.entry.category, slot.entry.prepNote, isThai)
                            .ifBlank { untilLabel(slot.entry.minuteOfDay, nowMinute, isThai) }
                    }
                    val link = when {
                        slot.log != null -> ""
                        isNextUp -> if (isThai) "เริ่มก่อนเวลา" else "Start early"
                        canEdit -> if (isThai) "แก้ไข" else "Edit"
                        else -> ""
                    }
                    if (footer.isNotBlank() || note != null || link.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider(color = c.hairline)
                        Spacer(Modifier.height(6.dp))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                if (note != null) {
                                    Text(
                                        (if (isThai) "บันทึก: " else "Note: ") + note,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Light,
                                        color = c.body
                                    )
                                }
                                if (footer.isNotBlank()) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (slot.log != null) {
                                            Icon(
                                                Icons.Filled.Person,
                                                null,
                                                Modifier.size(12.dp),
                                                tint = c.muted
                                            )
                                            Spacer(Modifier.width(4.dp))
                                        }
                                        Text(
                                            footer,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Light,
                                            color = c.muted
                                        )
                                    }
                                }
                            }
                            if (link.isNotBlank()) {
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    link,
                                    modifier = Modifier.clickable(enabled = !busy) {
                                        if (isNextUp) onDone() else onEdit()
                                    },
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = c.blue
                                )
                            }
                        }
                    }
                }
            }
        }

        if (current) {
            Row(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-12).dp)
                    .clip(RoundedCornerShape(100.dp))
                    .background(c.blue)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(Color.White))
                Spacer(Modifier.width(4.dp))
                Text(
                    if (isThai) "กำลังดำเนินการ" else "In progress",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

/** The leading dot: ticked, skipped, running, still to come, or passed unrecorded. */
@Composable
private fun SlotMarker(status: CareRoutinePlan.Status, c: RoutineColors) {
    when (status) {
        CareRoutinePlan.Status.DONE -> Box(
            Modifier.size(24.dp).clip(CircleShape).background(c.green),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Filled.Check, null, Modifier.size(14.dp), tint = Color.White) }

        CareRoutinePlan.Status.SKIPPED -> Box(
            Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(Color(0xFFFFFBEB))
                .border(1.dp, Color(0xFFFEF3C7), CircleShape),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Filled.Remove, null, Modifier.size(14.dp), tint = Color(0xFFB45309)) }

        CareRoutinePlan.Status.CURRENT -> Box(
            Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(Color(0xFFDBEAFE))
                .border(1.dp, Color(0xFFBFDBFE), CircleShape),
            contentAlignment = Alignment.Center
        ) { CircularProgressIndicator(Modifier.size(14.dp), color = c.blue, strokeWidth = 2.dp) }

        CareRoutinePlan.Status.MISSED -> DashedMarker(ring = Color(0xFFFDA4AF), dot = Color(0xFFFDA4AF))
        CareRoutinePlan.Status.UPCOMING -> DashedMarker(ring = Color(0xFFCBD5E1), dot = Color(0xFFCBD5E1))
    }
}

@Composable
private fun DashedMarker(ring: Color, dot: Color) {
    Box(
        Modifier
            .size(24.dp)
            .drawBehind {
                drawCircle(
                    color = ring,
                    radius = size.minDimension / 2 - 1.dp.toPx(),
                    style = Stroke(
                        width = 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f))
                    )
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
    }
}

@Composable
private fun StatusPill(status: CareRoutinePlan.Status, isThai: Boolean) = when (status) {
    CareRoutinePlan.Status.DONE -> Pill(
        text = if (isThai) "เสร็จสิ้น ✓" else "Done ✓",
        bg = Color(0xFFECFDF5), fg = Color(0xFF16A34A), border = Color(0xFFD1FAE5),
        fontSize = 10.sp, weight = FontWeight.SemiBold
    )
    CareRoutinePlan.Status.SKIPPED -> Pill(
        text = if (isThai) "ข้ามแล้ว" else "Skipped",
        bg = Color(0xFFFFFBEB), fg = Color(0xFFB45309), border = Color(0xFFFEF3C7),
        fontSize = 10.sp, weight = FontWeight.Medium
    )
    CareRoutinePlan.Status.MISSED -> Pill(
        text = if (isThai) "ยังไม่ได้บันทึก" else "Not recorded",
        bg = Color(0xFFFFF1F2), fg = Color(0xFFE11D48), border = Color(0xFFFFE4E6),
        fontSize = 10.sp, weight = FontWeight.Medium
    )
    else -> Pill(
        text = if (isThai) "รอเวลา ⏱️" else "Upcoming ⏱️",
        bg = Color(0xFFF8FAFC), fg = Color(0xFF94A3B8), border = null,
        fontSize = 10.sp, weight = FontWeight.Medium
    )
}

@Composable
private fun Pill(
    text: String,
    bg: Color,
    fg: Color,
    border: Color?,
    fontSize: androidx.compose.ui.unit.TextUnit,
    weight: FontWeight,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(100.dp)
    Text(
        text,
        modifier = modifier
            .clip(shape)
            .background(bg)
            .then(if (border != null) Modifier.border(1.dp, border, shape) else Modifier)
            .padding(horizontal = 6.dp, vertical = 1.dp),
        fontSize = fontSize,
        fontWeight = weight,
        color = fg,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

private class PillStyle(val bg: Color, val fg: Color, val border: Color?)

/** The design's per-category chip colours, keyed to [CareRoutineCategories]. */
private fun categoryPill(category: String): PillStyle = when (category) {
    "health" -> PillStyle(Color(0xFFEFF6FF), Color(0xFF1D4ED8), Color(0xFFDBEAFE))
    "feeding" -> PillStyle(Color(0xFFFFF1F2), Color(0xFFBE123C), Color(0xFFFFE4E6))
    "solid" -> PillStyle(Color(0xFFFEF3C7), Color(0xFFC2410C), Color(0xFFFFEDD5))
    "activity" -> PillStyle(Color(0xFFFFFBEB), Color(0xFF92400E), Color(0xFFFEF3C7))
    "sleep" -> PillStyle(Color(0xFFDBEAFE), Color(0xFF2563EB), null)
    "hygiene" -> PillStyle(Color(0xFFECFEFF), Color(0xFF0284C7), Color(0xFFCFFAFE))
    "night" -> PillStyle(Color(0xFFEDE9FE), Color(0xFF4F46E5), Color(0xFFC7D2FE))
    else -> PillStyle(Color(0xFFF1F5F9), Color(0xFF475569), Color(0xFFE2E8F0))
}

// ---------------------------------------------------------------------------------------------
// AI routine advice, and the page's primary action
// ---------------------------------------------------------------------------------------------

@Composable
private fun AiRoutineAdviceCard(
    isThai: Boolean,
    c: RoutineColors,
    child: com.lullabyai.app.data.model.Child?,
    onOpenFull: () -> Unit
) {
    // Age-based routine advice, generated on the device. It is labelled as such rather than
    // presented as a reading of today's ticks, which it is not.
    val brief = remember(child?.birthdate, isThai) { AiDailyBriefHelper.getZeroDataAiBrief(child, isThai) }
    val generatedAt = remember { java.time.LocalTime.now().let { it.hour * 60 + it.minute } }
    val shape = RoundedCornerShape(16.dp)

    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .then(
                if (c.dark) Modifier.background(c.blueSoft)
                else Modifier.background(
                    Brush.horizontalGradient(listOf(Color(0xFFEBF3FE), Color(0xFFEFF6FF)))
                )
            )
            .border(1.dp, Color(0xFFBFDBFE).copy(alpha = 0.8f), shape)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(28.dp).clip(RoundedCornerShape(12.dp)).background(c.blue),
                contentAlignment = Alignment.Center
            ) { Text("✨", fontSize = 12.sp) }
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    if (isThai) "คำแนะนำจาก AI กุมารแพทย์ NongRak" else "NongRak AI paediatric guidance",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = c.blueInk
                )
                val age = hubAgeMonthsOnly(child?.birthdate, isThai)
                Text(
                    if (age.isBlank()) (if (isThai) "วิเคราะห์จากช่วงวัยของลูก" else "Based on your child's age")
                    else if (isThai) "วิเคราะห์จากบันทึกช่วงอายุ $age" else "Based on records at $age",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium,
                    color = c.blue
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            listOf(brief.recommendedAction, brief.sleepScheduleTip)
                .filter { it.isNotBlank() }
                .joinToString(" "),
            fontSize = 11.sp,
            fontWeight = FontWeight.Light,
            color = c.body,
            lineHeight = 17.sp
        )
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = c.blueLine)
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                (if (isThai) "อัปเดตคำแนะนำล่าสุด: วันนี้ " else "Last updated: today ") +
                    CareRoutinePlan.formatMinuteOfDay(generatedAt) + (if (isThai) " น." else ""),
                modifier = Modifier.weight(1f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Light,
                color = c.muted
            )
            Row(
                Modifier.clickable(onClick = onOpenFull),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (isThai) "อ่านผลวิเคราะห์เต็ม" else "Read the full analysis",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = c.blue
                )
                Icon(Icons.Filled.ChevronRight, null, Modifier.size(12.dp), tint = c.blue)
            }
        }
    }
}

@Composable
private fun PrimaryRoutineButton(isThai: Boolean, c: RoutineColors, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 10.dp,
                shape = shape,
                ambientColor = c.blue.copy(alpha = 0.35f),
                spotColor = c.blue.copy(alpha = 0.35f)
            )
            .clip(shape)
            .background(c.blue)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Add, null, Modifier.size(16.dp), tint = Color.White)
        Spacer(Modifier.width(8.dp))
        Text(
            if (isThai) "เพิ่มกิจวัตรใหม่ / บันทึกการส่งต่อพิเศษ"
            else "New routine item / special handover",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Supporting states and sheets
// ---------------------------------------------------------------------------------------------

@Composable
private fun EmptyRoutineCard(isThai: Boolean, c: RoutineColors, canEdit: Boolean, onAdd: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .softShadow(shape)
            .clip(shape)
            .background(c.card)
            .border(1.dp, c.cardBorder, shape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            if (isThai) "ยังไม่มีกิจวัตรสำหรับลูก" else "No routine yet",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = c.ink
        )
        Text(
            if (isThai) "เพิ่มช่วงเวลาสำคัญ เช่น ตื่นนอน มื้อนม นอนกลางวัน อาบน้ำ เพื่อให้ผู้ดูแลทำตามได้"
            else "Add the day's key moments — waking, milk, naps, bath — so a caregiver can follow them.",
            fontSize = 11.sp,
            fontWeight = FontWeight.Light,
            color = c.body,
            lineHeight = 16.sp
        )
        if (canEdit) {
            Text(
                if (isThai) "เพิ่มกิจวัตรแรก" else "Add the first item",
                modifier = Modifier
                    .clip(RoundedCornerShape(100.dp))
                    .background(c.blueSoft)
                    .border(1.dp, c.blueLine, RoundedCornerShape(100.dp))
                    .clickable(onClick = onAdd)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = c.blue
            )
        } else {
            Text(
                if (isThai) "ผู้ปกครองเป็นผู้กำหนดกิจวัตร" else "The parent sets the routine.",
                fontSize = 10.sp,
                fontWeight = FontWeight.Light,
                color = c.muted
            )
        }
    }
}

@Composable
private fun RoutineErrorCard(text: String, isThai: Boolean, onDismiss: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color(0xFFFFF1F2))
            .border(1.dp, Color(0xFFFFE4E6), shape)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text,
            modifier = Modifier.weight(1f),
            fontSize = 11.sp,
            color = Color(0xFF9F1239),
            lineHeight = 16.sp
        )
        Spacer(Modifier.width(8.dp))
        Text(
            if (isThai) "ปิด" else "Dismiss",
            modifier = Modifier.clickable(onClick = onDismiss),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFFBE123C)
        )
    }
}

@Composable
private fun SheetAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    c: RoutineColors,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(c.blueSoft),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, Modifier.size(16.dp), tint = c.blue) }
        Spacer(Modifier.width(12.dp))
        Text(label, modifier = Modifier.weight(1f), fontSize = 13.sp, color = c.ink)
        Icon(Icons.Filled.ChevronRight, null, Modifier.size(16.dp), tint = c.muted)
    }
}

/**
 * What can be done to a row that is not the one in progress. The design gives these rows no
 * buttons, so they open here on tap rather than growing controls the design does not have.
 */
@Composable
private fun SlotActionsSheet(
    slot: CareRoutinePlan.Slot,
    isThai: Boolean,
    c: RoutineColors,
    canEdit: Boolean,
    onDismiss: () -> Unit,
    onDone: () -> Unit,
    onSkip: () -> Unit,
    onUndo: () -> Unit,
    onEdit: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.card) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text(
                timeLabel(slot.entry.minuteOfDay, isThai) + "  " + slot.entry.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = c.inkStrong
            )
            Spacer(Modifier.height(12.dp))
            if (slot.log == null) {
                SheetAction(Icons.Filled.Check, if (isThai) "บันทึกว่าทำแล้ว" else "Mark as done", c, onDone)
                SheetAction(Icons.Filled.Remove, if (isThai) "ข้ามรายการนี้" else "Skip this item", c, onSkip)
            } else {
                SheetAction(
                    Icons.Filled.Remove,
                    if (isThai) "ลบบันทึกของวันนี้" else "Clear today's record",
                    c,
                    onUndo
                )
            }
            if (canEdit) {
                SheetAction(
                    Icons.Filled.Schedule,
                    if (isThai) "แก้ไขกิจวัตรนี้" else "Edit this routine item",
                    c,
                    onEdit
                )
            }
        }
    }
}

/** Add a note to the item in progress — recorded as done, or as a deliberate skip with a reason. */
@Composable
private fun RoutineNoteDialog(
    slot: CareRoutinePlan.Slot,
    isThai: Boolean,
    onDismiss: () -> Unit,
    onDone: (String) -> Unit,
    onSkip: (String) -> Unit
) {
    var note by remember { mutableStateOf(slot.log?.note.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(slot.entry.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (isThai) "เขียนสิ่งที่เกิดขึ้นสั้นๆ เพื่อให้คนถัดไปรู้ เช่น หลับเวลา 13:08 น."
                    else "A short note tells the next person what happened — e.g. fell asleep at 13:08."
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { if (it.length <= 500) note = it },
                    label = { Text(if (isThai) "โน้ต" else "Note") },
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(onClick = { onSkip(note) }) {
                    Text(
                        if (isThai) "ข้ามรายการนี้ (ไม่ได้ทำ)" else "Skip this item (not done)",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onDone(note) }) {
                Text(if (isThai) "บันทึกว่าทำแล้ว" else "Mark as done")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(if (isThai) "ยกเลิก" else "Cancel") } }
    )
}

/** Add or change one routine item. Times are picked in 5-minute steps — enough for a day plan. */
@Composable
private fun RoutineEditorDialog(
    existing: CareRoutine?,
    isThai: Boolean,
    onDismiss: () -> Unit,
    onSave: (time: String, category: String, title: String, detail: String, prepNote: String) -> Unit,
    onRemove: (() -> Unit)?
) {
    val startMinute = CareRoutinePlan.parseMinuteOfDay(existing?.timeOfDay) ?: (7 * 60)
    var hour by remember { mutableStateOf(startMinute / 60) }
    var minute by remember { mutableStateOf((startMinute % 60) / 5 * 5) }
    var category by remember { mutableStateOf(existing?.category ?: "feeding") }
    var title by remember { mutableStateOf(existing?.title.orEmpty()) }
    var detail by remember { mutableStateOf(existing?.detail.orEmpty()) }
    var prepNote by remember { mutableStateOf(existing?.prepNote.orEmpty()) }
    var hourOpen by remember { mutableStateOf(false) }
    var minuteOpen by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Schedule, null) },
        title = {
            Text(
                if (existing == null) (if (isThai) "เพิ่มกิจวัตร" else "Add routine item")
                else (if (isThai) "แก้ไขกิจวัตร" else "Edit routine item")
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (isThai) "เวลา" else "Time")
                    Box {
                        OutlinedButton(onClick = { hourOpen = true }) { Text("%02d".format(hour)) }
                        DropdownMenu(expanded = hourOpen, onDismissRequest = { hourOpen = false }) {
                            (0..23).forEach { h ->
                                DropdownMenuItem(
                                    text = { Text("%02d".format(h)) },
                                    onClick = { hour = h; hourOpen = false }
                                )
                            }
                        }
                    }
                    Text(":")
                    Box {
                        OutlinedButton(onClick = { minuteOpen = true }) { Text("%02d".format(minute)) }
                        DropdownMenu(expanded = minuteOpen, onDismissRequest = { minuteOpen = false }) {
                            (0..55 step 5).forEach { m ->
                                DropdownMenuItem(
                                    text = { Text("%02d".format(m)) },
                                    onClick = { minute = m; minuteOpen = false }
                                )
                            }
                        }
                    }
                }

                Text(if (isThai) "หมวดหมู่" else "Category", style = MaterialTheme.typography.labelMedium)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    CareRoutineCategories.ALL.chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            pair.forEach { item ->
                                FilterChip(
                                    selected = category == item.key,
                                    onClick = { category = item.key },
                                    label = { Text(if (isThai) item.th else item.en, fontSize = 11.sp) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { if (it.length <= 200) title = it },
                    label = { Text(if (isThai) "ชื่อกิจวัตร" else "Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = detail,
                    onValueChange = { if (it.length <= 1000) detail = it },
                    label = { Text(if (isThai) "รายละเอียด / วิธีทำ" else "Details") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = prepNote,
                    onValueChange = { if (it.length <= 300) prepNote = it },
                    label = {
                        Text(
                            if (category == "sleep" || category == "night") {
                                if (isThai) "เป้าหมาย" else "Goal"
                            } else if (isThai) "เตรียม" else "Prepare"
                        )
                    },
                    supportingText = {
                        Text(
                            if (isThai) "แสดงใต้รายการก่อนถึงเวลา เช่น สบู่สูตรอ่อนโยน & ผ้าเช็ดตัวอุ่น"
                            else "Shown under the item before its time — e.g. gentle soap & a warm towel.",
                            fontSize = 11.sp
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (onRemove != null) {
                    TextButton(onClick = onRemove) {
                        Text(
                            if (isThai) "นำออกจากตาราง" else "Remove from the timeline",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank(),
                onClick = {
                    onSave(
                        CareRoutinePlan.formatMinuteOfDay(hour * 60 + minute),
                        category,
                        title,
                        detail,
                        prepNote
                    )
                }
            ) { Text(if (isThai) "บันทึก" else "Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(if (isThai) "ยกเลิก" else "Cancel") }
        }
    )
}

// ---------------------------------------------------------------------------------------------
// Labels
// ---------------------------------------------------------------------------------------------

private val THAI_MONTHS_SHORT = listOf(
    "ม.ค.", "ก.พ.", "มี.ค.", "เม.ย.", "พ.ค.", "มิ.ย.",
    "ก.ค.", "ส.ค.", "ก.ย.", "ต.ค.", "พ.ย.", "ธ.ค."
)

/** "วันนี้, 24 พ.ค." for the header's date chip. */
internal fun routineDateLabel(date: String, isThai: Boolean): String {
    val day = runCatching { java.time.LocalDate.parse(date) }.getOrNull() ?: return ""
    val isToday = day == java.time.LocalDate.now()
    return if (isThai) {
        val label = "${day.dayOfMonth} ${THAI_MONTHS_SHORT[day.monthValue - 1]}"
        if (isToday) "วันนี้, $label" else label
    } else {
        val month = day.month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH)
        val label = "${day.dayOfMonth} $month"
        if (isToday) "Today, $label" else label
    }
}

internal fun timeLabel(minuteOfDay: Int, isThai: Boolean): String =
    CareRoutinePlan.formatMinuteOfDay(minuteOfDay) + if (isThai) " น." else ""

/** "ช่วงบ่าย" — which part of the day a slot falls in. */
internal fun dayPeriodLabel(minuteOfDay: Int, isThai: Boolean): String = when {
    minuteOfDay < 12 * 60 -> if (isThai) "ช่วงเช้า" else "Morning"
    minuteOfDay < 16 * 60 -> if (isThai) "ช่วงบ่าย" else "Afternoon"
    minuteOfDay < 19 * 60 -> if (isThai) "ช่วงเย็น" else "Evening"
    else -> if (isThai) "ช่วงค่ำ" else "Night"
}

/** "บันทึกโดย พี่เลี้ยงใจ (08:45 น.)", falling back to the time alone for an unknown actor. */
internal fun recordedByLabel(log: CareRoutinePlan.Log, isThai: Boolean): String {
    val at = log.recordedMinute?.let {
        " (" + CareRoutinePlan.formatMinuteOfDay(it) + (if (isThai) " น.)" else ")")
    }.orEmpty()
    return when {
        log.actorName.isNotBlank() ->
            (if (isThai) "บันทึกโดย " else "Recorded by ") + log.actorName + at
        at.isNotBlank() ->
            (if (isThai) "บันทึกเมื่อ" else "Recorded at") + at
        else -> if (isThai) "บันทึกแล้ว" else "Recorded"
    }
}

/** "อีก 3 ชม. 12 นาที" under a slot that has not come round yet. */
internal fun untilLabel(minuteOfDay: Int, nowMinute: Int, isThai: Boolean): String {
    val left = minuteOfDay - nowMinute
    if (left <= 0) return ""
    val hours = left / 60
    val minutes = left % 60
    val parts = buildList {
        if (hours > 0) add(if (isThai) "$hours ชม." else "${hours}h")
        if (minutes > 0) add(if (isThai) "$minutes นาที" else "${minutes}m")
    }
    return (if (isThai) "อีก " else "in ") + parts.joinToString(" ")
}

/**
 * "เตรียม: สบู่สูตรอ่อนโยน" — or "เป้าหมาย:" for sleep, where the line is what the nap is aiming
 * at rather than something to fetch. The stored text stays the caregiver's own words; only the
 * word in front of it is decided here.
 */
internal fun prepLabel(category: String, prepNote: String, isThai: Boolean): String = when {
    prepNote.isBlank() -> ""
    category == "sleep" || category == "night" ->
        (if (isThai) "เป้าหมาย: " else "Goal: ") + prepNote
    else -> (if (isThai) "เตรียม: " else "Prepare: ") + prepNote
}

/**
 * The primary button on the row in progress. Sleep is the one case where finishing the item means
 * recording a different fact — the time the child woke — which is what the design labels it.
 */
internal fun currentActionLabel(category: String, isThai: Boolean): String = when (category) {
    "sleep", "night" -> if (isThai) "บันทึกเวลาตื่นนอน" else "Record waking time"
    else -> if (isThai) "บันทึกว่าทำแล้ว" else "Mark as done"
}

/** Server refusals arrive as named errors; anything else is shown as received. */
private fun routineErrorText(raw: String, isThai: Boolean): String = when {
    raw.startsWith("routine_update_denied") || raw.startsWith("routine_insert_denied") ->
        if (isThai) "คุณไม่มีสิทธิ์แก้ไขกิจวัตรของเด็กคนนี้ (ผู้ปกครองเป็นผู้กำหนด)"
        else "You do not have permission to change this child's routine — the parent sets it."
    raw.startsWith("routine_remove_denied") ->
        if (isThai) "นำรายการออกไม่ได้ เพราะคุณไม่มีสิทธิ์แก้ไขกิจวัตร"
        else "Could not remove the item: you do not have permission to change the routine."
    raw.startsWith("routine_log_signed_out") ->
        if (isThai) "เซสชันหมดอายุ กรุณาเข้าสู่ระบบใหม่" else "Session expired — please sign in again."
    else -> (if (isThai) "ดำเนินการไม่สำเร็จ ข้อมูลตอบกลับ: " else "Request failed. Received: ") + raw
}
