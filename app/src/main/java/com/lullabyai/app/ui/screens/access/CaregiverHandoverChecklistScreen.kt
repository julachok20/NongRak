@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)
package com.lullabyai.app.ui.screens.access

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.ChatBubbleOutline
import com.lullabyai.app.data.model.ChildFavorite
import com.lullabyai.app.data.model.FavoriteCategory
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.lullabyai.app.data.handoff.CareHandoff
import com.lullabyai.app.data.handoff.ParentHandoffBrief
import com.lullabyai.app.data.settings.ChildSelection
import com.lullabyai.app.data.routine.CareRoutinePlan
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.screens.child.ChildBottomNav
import com.lullabyai.app.ui.theme.LocalDarkTheme
import com.lullabyai.app.viewmodel.AccessRoleLabels
import com.lullabyai.app.viewmodel.HandoffViewModel
import kotlinx.coroutines.delay
import java.time.LocalTime

/**
 * "บันทึกการส่งต่อการดูแล" — the caregiver's live shift: today's care list, a complete form on
 * the current item, vitals, and a line to the parent. Distinct from [HandoffScreen], which only
 * owners / co-owners use to assign the day.
 */
@Composable
fun CaregiverHandoverChecklistScreen(
    childId: String,
    navController: NavController,
    vm: HandoffViewModel = viewModel()
) {
    val thai = LocalStrings.current.isThai
    val s by vm.state.collectAsState()
    val c = checklistColors()
    var addingVitals by remember { mutableStateOf(false) }

    LaunchedEffect(childId) {
        vm.load(childId)
        while (true) {
            delay(20000)
            vm.refresh()
        }
    }

    val child = s.child
    val name = child?.name?.ifBlank { s.childName }.orEmpty()
    val me = s.people.firstOrNull { it.userId == s.userId }
    val age = ParentHandoffBrief.spokenAge(child?.birthdate, thai)
    val caution = ParentHandoffBrief.caregiverCaution(
        child?.allergies, child?.noKnownAllergies == true, s.allergies, thai
    )
    val pending = s.handoffs.firstOrNull {
        it.recipientId == s.userId && it.status == "pending"
    }
    val done = s.day.done
    val currentCount = s.day.slots.count {
        it.status == CareRoutinePlan.Status.CURRENT || it.status == CareRoutinePlan.Status.MISSED
    }
    val waiting = s.day.slots.count { it.status == CareRoutinePlan.Status.UPCOMING }
    val percent = s.day.percent
    val parentName = s.parentPeerName
    val parentPeer = s.parentPeerId

    Scaffold(
        containerColor = c.page,
        topBar = {
            ChecklistTopBar(
                thai = thai,
                c = c,
                onBack = { navController.popBackStack() }
            )
        },
        bottomBar = { ChildBottomNav(navController = navController, currentTab = "child") }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 12.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (s.busy && s.day.total == 0 && s.child == null) {
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = c.blue)
            }
            s.error?.let { error ->
                Text(
                    (if (thai) "ดำเนินการไม่สำเร็จ: " else "Request failed: ") + error,
                    color = Color(0xFFBE123C),
                    fontSize = 12.sp
                )
            }

            pending?.let { h ->
                PendingAcceptCard(h = h, thai = thai, c = c, busy = s.busy, onAccept = { vm.act(h, "accept") }, onDecline = { vm.act(h, "decline") })
            }

            IdentityCard(
                c = c,
                thai = thai,
                caregiverName = me?.displayName.orEmpty().ifBlank { if (thai) "ผู้ดูแล" else "Caregiver" },
                roleLabel = AccessRoleLabels.title(me?.role.orEmpty(), thai),
                childName = name,
                age = age,
                caution = caution,
                avatarUrl = me?.avatarUrl
            )

            FavoritesCard(
                c = c,
                thai = thai,
                childName = name,
                favorites = s.favorites,
                soothingNote = child?.soothingNote
            )

            ProgressCard(
                c = c,
                thai = thai,
                done = done,
                current = currentCount,
                waiting = waiting,
                total = s.day.total,
                percent = percent
            )

            TaskListHeader(c = c, thai = thai)

            if (s.day.total == 0 && !s.busy) {
                Text(
                    if (thai) "ยังไม่มีรายการสำหรับวันนี้" else "No tasks on today's list yet",
                    fontSize = 12.sp,
                    color = c.muted
                )
            }

            s.day.slots.forEach { slot ->
                key(slot.entry.id) {
                    ShiftTaskCard(
                        c = c,
                        thai = thai,
                        slot = slot,
                        busy = s.busy,
                        onComplete = { finishedAt, mood, extra ->
                            val bits = listOfNotNull(
                                finishedAt.takeIf { it.isNotBlank() }?.let {
                                    if (thai) "เวลา $it" else "at $it"
                                },
                                mood.takeIf { it.isNotBlank() }?.let { ParentHandoffBrief.moodLabel(it, thai) ?: it },
                                extra.takeIf { it.isNotBlank() }
                            )
                            vm.tick(slot.entry.id, bits.joinToString(" · "))
                        }
                    )
                }
            }

            HealthStrip(
                c = c,
                thai = thai,
                tempC = s.latestTempC,
                urine = s.urineToday,
                stool = s.stoolToday,
                milkMl = s.milkMlToday,
                lastAt = s.lastRecordAt,
                onAdd = { addingVitals = true }
            )

            ParentContactCard(
                c = c,
                thai = thai,
                parentName = parentName,
                chatEnabled = parentPeer != null,
                onChat = { parentPeer?.let { navController.navigate(Screen.CareChat.route(it)) } }
            )
        }
    }

    if (addingVitals) {
        AlertDialog(
            onDismissRequest = { addingVitals = false },
            title = { Text(if (thai) "บันทึกเพิ่มเติม" else "Add a record") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (thai) "การวัดไข้ ปัสสาวะ-อุจจาระ หรือนม ไปที่หน้าบันทึกของรายการนั้น"
                        else "Temperature, diaper, and milk each have their own record screen."
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    addingVitals = false
                    ChildSelection.set(childId)
                    navController.navigate(Screen.WasteRecord.route)
                }) { Text(if (thai) "ผ้าอ้อม / ขับถ่าย" else "Diaper") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        addingVitals = false
                        ChildSelection.set(childId)
                        navController.navigate(Screen.MilkRecord.route)
                    }) { Text(if (thai) "นม" else "Milk") }
                    TextButton(onClick = {
                        addingVitals = false
                        ChildSelection.set(childId)
                        navController.navigate(Screen.AddHealthRecord.route)
                    }) { Text(if (thai) "วัดไข้" else "Temperature") }
                    TextButton(onClick = { addingVitals = false }) {
                        Text(if (thai) "ปิด" else "Close")
                    }
                }
            }
        )
    }
}

@Composable
private fun ChecklistTopBar(thai: Boolean, c: ChecklistColors, onBack: () -> Unit) {
    Surface(color = c.page) {
        Column(Modifier.statusBarsPadding()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.ChevronLeft, null, Modifier.size(22.dp), tint = c.ink)
                }
                Spacer(Modifier.width(4.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (thai) "บันทึกการส่งต่อการดูแล" else "Handover log",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = c.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        if (thai) "สำหรับพี่เลี้ยง & ผู้ดูแลประจำวัน" else "For the nanny and daily caregiver",
                        fontSize = 11.sp,
                        color = c.muted,
                        maxLines = 1
                    )
                }
                Spacer(Modifier.width(8.dp))
                Row(
                    Modifier
                        .clip(RoundedCornerShape(100.dp))
                        .background(Color(0xFFECFDF5))
                        .border(1.dp, Color(0xFFA7F3D0), RoundedCornerShape(100.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF10B981)))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (thai) "กะปัจจุบัน" else "On shift",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF047857)
                    )
                }
            }
        }
    }
}

/**
 * สิ่งที่น้องชอบ และวิธีปลอบโยน — read-only here, on purpose.
 *
 * A caregiver follows these, they do not rewrite them, and the database enforces that rather
 * than this screen: there is no add control because there is no permission behind one. When the
 * parent turns sharing off the rows simply do not arrive, and the whole card disappears.
 */
@Composable
private fun FavoritesCard(
    c: ChecklistColors,
    thai: Boolean,
    childName: String,
    favorites: List<ChildFavorite>,
    soothingNote: String?
) {
    if (favorites.isEmpty() && soothingNote.isNullOrBlank()) return
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.card)
            .border(1.dp, c.line, shape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Favorite, null, tint = Color(0xFFF43F5E), modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (thai) "สิ่งที่น้องชอบและวิธีปลอบโยน" else "What they like, and what settles them",
                    fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.ink
                )
                Text(
                    if (thai) "คู่มือพิเศษสำหรับพี่เลี้ยงเพื่อดูแลน้อง $childName"
                    else "A short guide for whoever is looking after $childName",
                    fontSize = 11.sp, color = c.muted, maxLines = 2, overflow = TextOverflow.Ellipsis
                )
            }
        }

        FavoriteCategory.ordered.forEach { category ->
            val items = favorites.filter { it.category == category }
            if (items.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        FavoriteCategory.label(category, thai),
                        fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = c.blue
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items.forEach { fav ->
                            Text(
                                fav.label,
                                fontSize = 11.sp, color = c.ink,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(100.dp))
                                    .background(c.soft)
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }
        }

        if (!soothingNote.isNullOrBlank()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(c.soft)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    if (thai) "ข้อสังเกตจากคุณพ่อคุณแม่" else "A note from the parents",
                    fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = c.muted
                )
                Text(soothingNote, fontSize = 12.sp, color = c.ink)
            }
        }
    }
}

@Composable
private fun PendingAcceptCard(
    h: CareHandoff,
    thai: Boolean,
    c: ChecklistColors,
    busy: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.card)
            .border(1.dp, Color(0xFFBFDBFE), shape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            if (thai) "คุณได้รับการส่งต่อการดูแล" else "A handover was sent to you",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = c.ink
        )
        Text(
            if (thai) "รับช่วงก่อนจึงจะเริ่มกะนี้" else "Accept first to start this shift.",
            fontSize = 12.sp,
            color = c.muted
        )
        if (h.instructions.isNotBlank()) {
            Text(h.instructions, fontSize = 12.sp, color = c.inkSoft)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = !busy, onClick = onAccept, colors = ButtonDefaults.buttonColors(containerColor = c.blue)) {
                Text(if (thai) "รับช่วงดูแล" else "Take over")
            }
            OutlinedButton(enabled = !busy, onClick = onDecline) {
                Text(if (thai) "ไม่สะดวก" else "Decline")
            }
        }
    }
}

@Composable
private fun IdentityCard(
    c: ChecklistColors,
    thai: Boolean,
    caregiverName: String,
    roleLabel: String,
    childName: String,
    age: String,
    caution: String,
    avatarUrl: String?
) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .shadow(8.dp, shape, ambientColor = Color(0xFF3730A3).copy(alpha = 0.35f), spotColor = Color(0xFF3730A3).copy(alpha = 0.35f))
            .clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFF2563EB), Color(0xFF4F46E5), Color(0xFF6D28D9))))
    ) {
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = 8.dp)
                .size(120.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.08f))
        )
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f))
                        .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (!avatarUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = avatarUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text("🙂", fontSize = 22.sp)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        if (roleLabel.isNotBlank()) "$caregiverName ($roleLabel)" else caregiverName,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    val lookingAfter = buildString {
                        append(if (thai) "ดูแล: น้อง " else "Looking after: ")
                        append(childName)
                        if (age.isNotBlank()) append(" ($age)")
                    }
                    Text(lookingAfter, fontSize = 12.sp, color = Color(0xFFDBEAFE))
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x33F59E0B))
                    .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                    .padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Filled.WarningAmber, null, tint = Color(0xFFFDE68A), modifier = Modifier.size(16.dp))
                Text(caution, fontSize = 11.sp, color = Color.White, lineHeight = 16.sp)
            }
        }
    }
}

@Composable
private fun ProgressCard(
    c: ChecklistColors,
    thai: Boolean,
    done: Int,
    current: Int,
    waiting: Int,
    total: Int,
    percent: Int
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(2.dp, shape, ambientColor = Color(0x140F172A), spotColor = Color(0x140F172A))
            .clip(shape)
            .background(c.card)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFEEF2FF)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Check, null, Modifier.size(16.dp), tint = c.blue)
            }
            Spacer(Modifier.width(8.dp))
            Text(
                if (thai) "ความคืบหน้ารายการวันนี้" else "Today's progress",
                modifier = Modifier.weight(1f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = c.ink
            )
            Text(
                if (thai) "ทำแล้ว $done / $total รายการ ($percent%)"
                else "$done / $total done ($percent%)",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = c.blue
            )
        }
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(100.dp))
                .background(Color(0xFFE2E8F0))
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth((percent / 100f).coerceIn(0f, 1f))
                    .clip(RoundedCornerShape(100.dp))
                    .background(Brush.horizontalGradient(listOf(Color(0xFF6366F1), Color(0xFF2563EB))))
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            CountBit(Color(0xFF059669), if (thai) "เสร็จแล้ว: $done" else "Done: $done")
            CountBit(Color(0xFFF59E0B), if (thai) "กำลังดำเนินการ: $current" else "In progress: $current")
            CountBit(c.faint, if (thai) "รอดำเนินการ: $waiting" else "Waiting: $waiting")
        }
    }
}

@Composable
private fun CountBit(dot: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 11.sp, color = Color(0xFF475569))
    }
}

@Composable
private fun TaskListHeader(c: ChecklistColors, thai: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("📋", fontSize = 14.sp)
        Spacer(Modifier.width(6.dp))
        Text(
            if (thai) "รายการงานที่ต้องปฏิบัติ (ติ๊กเพื่อบันทึกผล)" else "Tasks to complete (tick to log)",
            modifier = Modifier.weight(1f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = c.ink
        )
    }
}

@Composable
private fun ShiftTaskCard(
    c: ChecklistColors,
    thai: Boolean,
    slot: CareRoutinePlan.Slot,
    busy: Boolean,
    onComplete: (finishedAt: String, mood: String, extra: String) -> Unit
) {
    val done = slot.status == CareRoutinePlan.Status.DONE
    val current = slot.status == CareRoutinePlan.Status.CURRENT || slot.status == CareRoutinePlan.Status.MISSED
    val skipped = slot.status == CareRoutinePlan.Status.SKIPPED
    val shape = RoundedCornerShape(16.dp)
    val border = when {
        done || skipped -> Color(0xFFD1FAE5)
        current -> Color(0xFF93C5FD)
        else -> c.line
    }
    val bg = when {
        done || skipped -> Color(0xFFF0FDF4)
        current -> Color.White
        else -> c.card
    }
    var finished by remember(slot.entry.id) { mutableStateOf(LocalTime.now()) }
    var mood by remember(slot.entry.id) { mutableStateOf("happy") }
    var extra by remember(slot.entry.id) { mutableStateOf("") }
    var pickingTime by remember { mutableStateOf(false) }
    var moodOpen by remember { mutableStateOf(false) }
    val timePicker = rememberTimePickerState(
        initialHour = LocalTime.now().hour,
        initialMinute = LocalTime.now().minute,
        is24Hour = true
    )
    val timeLabel = CareRoutinePlan.formatMinuteOfDay(slot.entry.minuteOfDay) + if (thai) " น." else ""
    val recorded = slot.log?.recordedMinute?.let {
        CareRoutinePlan.formatMinuteOfDay(it) + if (thai) " น." else ""
    }
    val pill = ParentHandoffBrief.shortCategory(slot.entry.category, thai)

    Column(
        Modifier
            .fillMaxWidth()
            .shadow(1.dp, shape, ambientColor = Color(0x140F172A), spotColor = Color(0x140F172A))
            .clip(shape)
            .background(bg)
            .border(if (current && !done) 1.5.dp else 1.dp, border, shape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            TaskMark(done = done || skipped, current = current && !done, c = c)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        when {
                            done -> if (thai) "เสร็จสิ้นแล้ว" else "Done"
                            skipped -> if (thai) "ข้าม" else "Skipped"
                            current -> (if (thai) "กำหนด: " else "Due: ") + timeLabel
                            else -> if (thai) "รอดำเนินการ" else "Waiting"
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = when {
                            done || skipped -> Color(0xFF059669)
                            current -> c.blue
                            else -> c.muted
                        }
                    )
                    if (!done && !current) {
                        Text(timeLabel, fontSize = 11.sp, color = c.muted)
                    }
                    if (done && recorded != null) {
                        Spacer(Modifier.weight(1f))
                        Text(
                            (if (thai) "✓ บันทึกแล้ว " else "✓ logged ") + recorded,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF059669)
                        )
                    }
                    if (current && !done) {
                        Spacer(Modifier.weight(1f))
                        Text(
                            if (thai) "กำลังดำเนินการ" else "In progress",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier
                                .clip(RoundedCornerShape(100.dp))
                                .background(c.blue)
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    if (!done && !current) {
                        Spacer(Modifier.weight(1f))
                        Text(
                            pill,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = c.muted,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(c.soft)
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
                if (done) {
                    Text(
                        (if (thai) "กำหนด: " else "Due: ") + timeLabel,
                        fontSize = 11.sp,
                        color = c.muted
                    )
                }
                Text(
                    slot.entry.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = c.ink,
                    modifier = Modifier.padding(top = 2.dp)
                )
                val sub = slot.entry.detail.ifBlank { slot.entry.prepNote.removePrefix("⚠️ ").takeIf { slot.entry.prepNote.isNotBlank() }.orEmpty() }
                if (sub.isNotBlank()) {
                    Text(sub, fontSize = 12.sp, color = c.inkSoft, modifier = Modifier.padding(top = 2.dp))
                }
                val note = slot.log?.note.orEmpty()
                val actor = slot.log?.actorName.orEmpty()
                if (note.isNotBlank()) {
                    Row(
                        Modifier
                            .padding(top = 8.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White)
                            .border(1.dp, c.line, RoundedCornerShape(10.dp))
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("📄", fontSize = 12.sp)
                        Text(
                            (if (thai) "บันทึกจาก${actor.ifBlank { "ผู้ดูแล" }}: " else "Note from ${actor.ifBlank { "caregiver" }}: ") + note,
                            fontSize = 11.sp,
                            color = c.inkSoft
                        )
                    }
                }
            }
        }

        if (current && !done) {
            HorizontalDivider(color = c.line)
            Text(
                if (thai) "กรอกข้อมูลเมื่อทำเสร็จ:" else "Fill this in when finished:",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = c.inkSoft
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = CareRoutinePlan.formatMinuteOfDay(finished.hour * 60 + finished.minute),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(if (thai) "เวลาสิ้นสุดจริง" else "Finished at", fontSize = 11.sp) },
                    trailingIcon = {
                        Icon(
                            Icons.Filled.Schedule, null,
                            Modifier.size(18.dp).clickable(enabled = !busy) { pickingTime = true },
                            tint = c.muted
                        )
                    },
                    modifier = Modifier.weight(1f).clickable(enabled = !busy) { pickingTime = true },
                    shape = RoundedCornerShape(12.dp),
                    colors = fieldColors(c),
                    enabled = !busy
                )
                ExposedDropdownMenuBox(
                    expanded = moodOpen,
                    onExpandedChange = { if (!busy) moodOpen = it },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = moodMenuLabel(mood, thai),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(if (thai) "อารมณ์ตอนนั้น" else "Mood", fontSize = 11.sp) },
                        trailingIcon = { Icon(Icons.Filled.KeyboardArrowDown, null) },
                        modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = fieldColors(c),
                        enabled = !busy
                    )
                    ExposedDropdownMenu(
                        expanded = moodOpen,
                        onDismissRequest = { moodOpen = false }
                    ) {
                        listOf("happy", "calm", "fussy").forEach { key ->
                            DropdownMenuItem(
                                text = { Text(moodMenuLabel(key, thai)) },
                                onClick = { mood = key; moodOpen = false }
                            )
                        }
                    }
                }
            }
            OutlinedTextField(
                value = extra,
                onValueChange = { if (it.length <= 300) extra = it },
                label = { Text(if (thai) "บันทึกเพิ่มเติมถึงคุณแม่" else "Note for the parent") },
                placeholder = { Text(if (thai) "เช่น หลับยาวไม่สะดุ้ง, เปิดเสียงฝนหลังบ่าย…" else "e.g. slept through, white noise on…") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = fieldColors(c),
                enabled = !busy
            )
            Button(
                onClick = {
                    onComplete(
                        CareRoutinePlan.formatMinuteOfDay(finished.hour * 60 + finished.minute),
                        mood,
                        extra
                    )
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = c.blue)
            ) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.Check, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (thai) "ยืนยันว่าทำเสร็จแล้ว (Mark Complete)" else "Mark complete",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    if (pickingTime) {
        AlertDialog(
            onDismissRequest = { pickingTime = false },
            confirmButton = {
                TextButton(onClick = {
                    finished = LocalTime.of(timePicker.hour, timePicker.minute)
                    pickingTime = false
                }) { Text(if (thai) "ตกลง" else "OK") }
            },
            dismissButton = {
                TextButton(onClick = { pickingTime = false }) { Text(if (thai) "ยกเลิก" else "Cancel") }
            },
            text = { TimePicker(state = timePicker) }
        )
    }
}

@Composable
private fun TaskMark(done: Boolean, current: Boolean, c: ChecklistColors) {
    val fill = when {
        done -> Color(0xFF10B981)
        current -> Color.White
        else -> Color.White
    }
    val stroke = when {
        done -> Color(0xFF10B981)
        current -> Color(0xFF93C5FD)
        else -> Color(0xFFCBD5E1)
    }
    Box(
        Modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(fill)
            .border(2.dp, stroke, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (done) Icon(Icons.Filled.Check, null, Modifier.size(14.dp), tint = Color.White)
    }
}

@Composable
private fun HealthStrip(
    c: ChecklistColors,
    thai: Boolean,
    tempC: Double?,
    urine: Int,
    stool: Int,
    milkMl: Int,
    lastAt: String?,
    onAdd: () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    val totalWaste = urine + stool
    val tempOk = tempC != null && tempC < 37.8
    val updated = ParentHandoffBrief.lastRecordLabel(lastAt, java.time.LocalDate.now(), thai)
        ?.removePrefix(if (thai) "บันทึกล่าสุด: " else "Last record: ")
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(2.dp, shape, ambientColor = Color(0x140F172A), spotColor = Color(0x140F172A))
            .clip(shape)
            .background(c.card)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("💗", fontSize = 14.sp)
            Spacer(Modifier.width(6.dp))
            Text(
                if (thai) "บันทึกตรวจสุขภาพด่วนระหว่างกะ" else "Shift health snapshot",
                modifier = Modifier.weight(1f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFE11D48)
            )
            if (!updated.isNullOrBlank()) {
                Text(
                    (if (thai) "อัปเดตล่าสุด: " else "Updated: ") + updated,
                    fontSize = 10.sp,
                    color = c.muted
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VitalCell(
                modifier = Modifier.weight(1f),
                c = c,
                label = if (thai) "อุณหภูมิกาย" else "Temperature",
                value = tempC?.let { String.format("%.1f°C", it) } ?: "—",
                sub = when {
                    tempC == null -> if (thai) "ยังไม่บันทึก" else "Not logged"
                    tempOk -> if (thai) "ปกติ" else "Normal"
                    else -> if (thai) "มีไข้" else "Fever"
                },
                subGreen = tempOk
            )
            VitalCell(
                modifier = Modifier.weight(1f),
                c = c,
                label = if (thai) "ผ้าอ้อม / ปัสสาวะ" else "Diaper / urine",
                value = if (thai) "$totalWaste ครั้ง" else "$totalWaste",
                sub = if (thai) "ฉี่ $urine / อึ $stool" else "$urine wet / $stool dirty"
            )
            VitalCell(
                modifier = Modifier.weight(1f),
                c = c,
                label = if (thai) "นมรวมวันนี้" else "Milk today",
                value = if (thai) "$milkMl มล." else "$milkMl ml",
                sub = if (thai) "วันนี้" else "today"
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onAdd)
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Add, null, Modifier.size(16.dp), tint = c.blue)
            Spacer(Modifier.width(4.dp))
            Text(
                if (thai) "บันทึกการวัดไข้ / ปัสสาวะ-อุจจาระ เพิ่มเติม" else "Log temperature / diaper",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = c.blue
            )
        }
    }
}

@Composable
private fun VitalCell(
    modifier: Modifier,
    c: ChecklistColors,
    label: String,
    value: String,
    sub: String,
    subGreen: Boolean = false
) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(c.soft)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, fontSize = 10.sp, color = c.muted, maxLines = 1)
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = c.ink, modifier = Modifier.padding(top = 4.dp))
        Text(
            sub,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = if (subGreen) Color(0xFF059669) else c.muted,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
private fun ParentContactCard(
    c: ChecklistColors,
    thai: Boolean,
    parentName: String?,
    chatEnabled: Boolean,
    onChat: () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    val who = parentName?.takeIf { it.isNotBlank() } ?: if (thai) "ผู้ปกครอง" else "parent"
    Row(
        Modifier
            .fillMaxWidth()
            .shadow(2.dp, shape, ambientColor = Color(0x140F172A), spotColor = Color(0x140F172A))
            .clip(shape)
            .background(Color(0xFFEFF6FF))
            .border(1.dp, Color(0xFFBFDBFE), shape)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(c.blue),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.ChatBubbleOutline, null, Modifier.size(18.dp), tint = Color.White)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (thai) "ติดต่อผู้ปกครอง ($who)" else "Contact parent ($who)",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = c.ink
            )
            Text(
                if (thai) "แจ้งเรื่องด่วน หรือส่งรูปภาพกิจกรรม" else "Urgent updates or photos from today",
                fontSize = 11.sp,
                color = c.muted
            )
        }
        Spacer(Modifier.width(6.dp))
        Button(
            onClick = onChat,
            enabled = chatEnabled,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.height(40.dp),
            colors = ButtonDefaults.buttonColors(containerColor = c.blue)
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, null, Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(if (thai) "แชท" else "Chat")
        }
    }
}

private fun moodMenuLabel(key: String, thai: Boolean): String = when (key) {
    "happy" -> if (thai) "😊 ร่าเริง ยิ้มแย้ม" else "😊 Cheerful"
    "calm" -> if (thai) "😌 อารมณ์ดี" else "😌 Calm"
    "fussy" -> if (thai) "😣 งอแง" else "😣 Fussy"
    else -> key
}

@Composable
private fun fieldColors(c: ChecklistColors) = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = c.blue,
    unfocusedBorderColor = c.line,
    focusedContainerColor = Color.White,
    unfocusedContainerColor = Color.White
)

private data class ChecklistColors(
    val page: Color,
    val card: Color,
    val ink: Color,
    val inkSoft: Color,
    val muted: Color,
    val faint: Color,
    val blue: Color,
    val line: Color,
    val soft: Color
)

@Composable
private fun checklistColors(): ChecklistColors {
    val dark = LocalDarkTheme.current
    return if (dark) ChecklistColors(
        page = Color(0xFF0F172A),
        card = Color(0xFF1E293B),
        ink = Color(0xFFF8FAFC),
        inkSoft = Color(0xFFCBD5E1),
        muted = Color(0xFF94A3B8),
        faint = Color(0xFF64748B),
        blue = Color(0xFF60A5FA),
        line = Color(0xFF334155),
        soft = Color(0xFF1E293B)
    ) else ChecklistColors(
        page = Color(0xFFF4F7FC),
        card = Color.White,
        ink = Color(0xFF0F172A),
        inkSoft = Color(0xFF334155),
        muted = Color(0xFF64748B),
        faint = Color(0xFF94A3B8),
        blue = Color(0xFF2563EB),
        line = Color(0xFFE2E8F0),
        soft = Color(0xFFF8FAFC)
    )
}
