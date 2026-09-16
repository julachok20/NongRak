@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.access

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.lullabyai.app.data.handoff.ParentHandoffBrief
import com.lullabyai.app.data.remote.StorageHelper
import com.lullabyai.app.data.routine.CareRoutinePlan
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.screens.child.ChildScreenScaffold
import com.lullabyai.app.ui.screens.child.HubChildAvatar
import com.lullabyai.app.ui.theme.LocalDarkTheme
import com.lullabyai.app.util.AiDailyBriefHelper
import com.lullabyai.app.viewmodel.AccessRoleLabels
import com.lullabyai.app.viewmodel.HandoffViewModel
import kotlinx.coroutines.delay

/**
 * "⚠️ ..." in `care_routines.prep_note` marks a caution item ("ระวังเป็นพิเศษ" in the add sheet); the
 * row shows it in red as คำเตือน.
 * ponytail: there is no priority column, so the flag rides on prep_note. Add a column if priorities
 * ever need sorting or filtering.
 */
private const val WARNING_PREFIX = "⚠️ "

/**
 * Parent "ส่งต่อการดูแล" — Stitch "ส่งต่อการดูแล (Care Handover)": the briefing for whoever looks
 * after the child today. Standard app header; the child photo is the same one Child Info shows
 * (cartoon baby when none is uploaded). Caregiver ticking stays on
 * [CaregiverHandoverChecklistScreen]; here the checklist is read-only.
 */
@Composable
fun HandoffScreen(childId: String, navController: NavController, vm: HandoffViewModel = viewModel()) {
    val thai = LocalStrings.current.isThai
    val s by vm.state.collectAsState()
    val c = handoffColors()
    val context = LocalContext.current
    var adding by remember { mutableStateOf(false) }

    LaunchedEffect(childId) {
        vm.load(childId)
        while (true) {
            delay(15000)
            vm.refresh()
        }
    }

    // Owner / co-owner briefing only. A ticked caretaker is sent to the shift checklist;
    // other limited roles (relative, viewer, …) are not assigned this screen.
    LaunchedEffect(s.people, s.userId, childId) {
        val me = s.people.firstOrNull { it.userId == s.userId } ?: return@LaunchedEffect
        when {
            AccessRoleLabels.isFullAccess(me.role) -> Unit
            me.isCaregiver -> navController.navigate(Screen.CareHandoverChecklist.route(childId)) {
                popUpTo(Screen.CareHandoff.route) { inclusive = true }
                launchSingleTop = true
            }
            else -> if (navController.previousBackStackEntry != null) navController.popBackStack()
        }
    }

    val child = s.child
    val avatarUrl by produceState<String?>(null, child?.avatarUrl) {
        value = StorageHelper.signedAvatarUrl(child?.avatarUrl)
    }
    val name = child?.name?.ifBlank { s.childName }.orEmpty()
    val allergy = ParentHandoffBrief.allergyLine(child?.allergies, child?.noKnownAllergies == true, s.allergies, thai)
    val vitals = ParentHandoffBrief.vitals(s.wakeIso, s.mood, s.latestTempC, thai)
    val medical = ParentHandoffBrief.medicalItems(name, s.latestTempC, s.healthRecords, s.chronic, thai)
    val caregivers = ParentHandoffBrief.caregiverCards(
        people = s.people.map {
            com.lullabyai.app.data.routine.CareRoutineDuty.Person(it.userId, it.displayName, it.role, it.avatarUrl)
        },
        caregiverIds = s.people.filter { it.isCaregiver }.map { it.userId }.toSet(),
        excludeUserId = s.userId,
        onDuty = s.onDuty,
        isThai = thai,
        roleTitle = AccessRoleLabels::title
    )
    var selectedId by remember(childId) { mutableStateOf<String?>(null) }
    LaunchedEffect(caregivers.map { it.userId }.joinToString(), s.onDuty?.person?.userId) {
        if (selectedId == null || caregivers.none { it.userId == selectedId }) {
            selectedId = s.onDuty?.person?.userId?.takeIf { id -> caregivers.any { it.userId == id } }
                ?: caregivers.firstOrNull()?.userId
        }
    }
    val selected = caregivers.firstOrNull { it.userId == selectedId }
    val chatPeer = selected?.userId
    val parentCall = ParentHandoffBrief.parentCallLabel(
        s.profile?.fullName ?: s.profile?.username,
        s.profile?.relationship,
        s.profile?.phone,
        thai
    )
    val brief = remember(child?.birthdate, thai) { AiDailyBriefHelper.getZeroDataAiBrief(child, thai) }

    ChildScreenScaffold(
        title = if (thai) "ส่งต่อการดูแล" else "Care handover",
        currentTab = "child",
        navController = navController,
        centerTitle = true
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .background(c.page)
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 12.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ChildBriefCard(
                c = c,
                thai = thai,
                name = name,
                health = ParentHandoffBrief.healthChip(s.latestTempC, s.mood, thai),
                fever = (s.latestTempC ?: 0.0) >= 37.8,
                vitals = vitals.line(thai),
                allergy = allergy,
                avatarUrl = avatarUrl
            )

            AssigneeSection(
                c = c,
                thai = thai,
                caregivers = caregivers,
                selectedId = selectedId,
                onSelect = { selectedId = it },
                onManage = { navController.navigate(Screen.PermissionAccess.route) },
                onAdd = { navController.navigate(Screen.AddUserAccessActivity.route) }
            )

            ChatCaregiverCard(
                c = c,
                thai = thai,
                caregiverName = selected?.name,
                enabled = chatPeer != null,
                onClick = { chatPeer?.let { navController.navigate(Screen.CareChat.route(it)) } }
            )

            DailyRoutineCard(
                c = c,
                thai = thai,
                day = s.day,
                canEdit = s.canEdit,
                onAdd = { if (s.canEdit) adding = true }
            )

            if (medical.isNotEmpty()) {
                MedicalCard(c = c, thai = thai, childName = name, items = medical)
            }

            AiInsightCard(thai = thai, quote = brief.recommendedAction)

            ContactsCard(
                c = c,
                thai = thai,
                parent = parentCall,
                onCallParent = {
                    val phone = s.profile?.phone?.filter { it.isDigit() || it == '+' }
                    if (!phone.isNullOrBlank()) {
                        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))
                    }
                }
            )

            LastRecordRow(
                c = c,
                thai = thai,
                label = ParentHandoffBrief.lastRecordLabel(s.lastRecordAt, java.time.LocalDate.now(), thai),
                onHistory = { navController.navigate(Screen.ActivityHistory.route) }
            )

            s.error?.let { err -> Text(err, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            if (s.busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = c.blue)
        }
    }

    if (adding) {
        AddTodayItemSheet(
            c = c,
            thai = thai,
            onDismiss = { adding = false },
            onSave = { time, category, title, detail, prepNote ->
                vm.addTodayItem(time, category, title, detail, prepNote)
                adding = false
            }
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Child context card
// ---------------------------------------------------------------------------------------------

@Composable
private fun ChildBriefCard(
    c: HandoffColors,
    thai: Boolean,
    name: String,
    health: String,
    fever: Boolean,
    vitals: String,
    allergy: String?,
    avatarUrl: String?
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(6.dp, shape, ambientColor = Color(0x1A2563EB), spotColor = Color(0x1A2563EB))
            .clip(shape)
            .background(c.card)
            .border(1.dp, c.blueTint, shape)
    ) {
        Box(Modifier.fillMaxWidth()) {
            Box(
                Modifier.align(Alignment.TopEnd).offset(28.dp, (-28).dp).size(112.dp)
                    .clip(CircleShape).background(Color(0xFFEFF6FF).copy(alpha = if (c.dark) 0.06f else 0.7f))
            )
            Row(
                Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                HubChildAvatar(avatarUrl = avatarUrl, size = 56.dp, ring = true)
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            if (thai) "น้อง $name" else name,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = c.ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        StatusChip(text = health, fever = fever)
                    }
                    if (vitals.isNotBlank()) {
                        Text(vitals, fontSize = 12.sp, color = c.muted, modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }
        }
        if (allergy != null) {
            val label = allergy.substringBefore(":", "")
            val body = allergy.substringAfter(": ", allergy)
            HorizontalDivider(color = c.soft)
            Row(
                Modifier.fillMaxWidth().background(Color(0xFFFFF1F2).copy(alpha = if (c.dark) 0.12f else 0.8f))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    Modifier.size(24.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFFFFE4E6)),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Filled.WarningAmber, null, tint = Color(0xFFE11D48), modifier = Modifier.size(14.dp)) }
                Text(
                    buildAnnotatedString {
                        if (label.isNotBlank()) {
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color(0xFFBE123C))) { append("$label: ") }
                        }
                        withStyle(SpanStyle(fontWeight = FontWeight.Medium, color = if (c.dark) Color(0xFFFDA4AF) else Color(0xFF881337))) {
                            append(body)
                        }
                    },
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    if (thai) "อันตราย" else "Danger",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFE11D48),
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Color.White.copy(alpha = 0.8f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun StatusChip(text: String, fever: Boolean) {
    val fg = if (fever) Color(0xFFBE123C) else Color(0xFF047857)
    val dot = if (fever) Color(0xFFF43F5E) else Color(0xFF10B981)
    Row(
        Modifier.clip(CircleShape)
            .background(if (fever) Color(0xFFFFF1F2) else Color(0xFFECFDF5))
            .border(1.dp, if (fever) Color(0xFFFECDD3) else Color(0xFFA7F3D0), CircleShape)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(dot))
        Text(text, fontSize = 10.sp, fontWeight = FontWeight.Medium, color = fg, maxLines = 1)
    }
}

// ---------------------------------------------------------------------------------------------
// Chat + caregivers
// ---------------------------------------------------------------------------------------------

@Composable
private fun ChatCaregiverCard(c: HandoffColors, thai: Boolean, caregiverName: String?, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .shadow(4.dp, shape, ambientColor = Color(0x142563EB), spotColor = Color(0x142563EB))
            .clip(shape)
            .background(c.card)
            .border(1.dp, c.blueLine, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(c.blueTint),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Filled.ChatBubbleOutline, null, tint = c.blue, modifier = Modifier.size(20.dp)) }
        Column(Modifier.weight(1f)) {
            Text(
                if (thai) "แชทกับผู้ดูแล (ส่งข้อความด่วน)" else "Chat with caregiver",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = c.ink
            )
            Text(
                if (caregiverName.isNullOrBlank()) {
                    if (thai) "ยังไม่มีผู้ดูแลในกะนี้" else "No caregiver on this shift"
                } else if (thai) "สอบถาม$caregiverName หรือแจ้งข้อมูลเพิ่มเติม"
                else "Ask $caregiverName or send an update",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = c.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            if (thai) "ติดต่อด่วน" else "Contact",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.clip(CircleShape).background(if (enabled) c.blue else c.faint)
                .padding(horizontal = 10.dp, vertical = 4.dp)
        )
        Icon(Icons.Filled.ChevronRight, null, tint = c.faint, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun AssigneeSection(
    c: HandoffColors,
    thai: Boolean,
    caregivers: List<ParentHandoffBrief.CaregiverCard>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onManage: () -> Unit,
    onAdd: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (thai) "ผู้รับมอบหมายดูแลวันนี้" else "ON DUTY TODAY",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = c.inkSoft,
                modifier = Modifier.weight(1f)
            )
            Text(
                if (thai) "จัดการสิทธิ์" else "Manage access",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = c.blue,
                modifier = Modifier.clip(CircleShape).clickable(onClick = onManage).padding(4.dp)
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 2.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            caregivers.forEach { card ->
                AssigneeCard(
                    c = c,
                    card = card,
                    selected = card.userId == selectedId,
                    modifier = Modifier.width(200.dp),
                    onClick = { onSelect(card.userId) }
                )
            }
            AddCaregiverButton(c = c, thai = thai, onClick = onAdd)
        }
        if (caregivers.isEmpty()) {
            Text(
                if (thai) "ยังไม่มีผู้รับมอบหมาย — กดเพิ่มผู้ดูแล หรือเปิด \"จัดการสิทธิ์\""
                else "Nobody assigned yet — add a caregiver or open Manage access",
                fontSize = 11.sp,
                color = c.muted,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

@Composable
private fun AssigneeCard(
    c: HandoffColors,
    card: ParentHandoffBrief.CaregiverCard,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    val on = selected
    Row(
        modifier
            .shadow(1.dp, shape)
            .clip(shape)
            .background(if (on) Color(0xFFEFF6FF) else c.card)
            .border(if (on) 2.dp else 1.dp, if (on) c.blue else c.line, shape)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(if (on) Color(0xFFDBEAFE) else Color(0xFFFEF3C7)),
                contentAlignment = Alignment.Center
            ) {
                if (!card.avatarUrl.isNullOrBlank()) {
                    AsyncImage(model = card.avatarUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Text(
                        card.avatarLabel,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (on) Color(0xFF1D4ED8) else Color(0xFF92400E),
                        maxLines = 1
                    )
                }
            }
            if (card.onDuty) {
                Box(
                    Modifier.align(Alignment.TopEnd).size(12.dp).clip(CircleShape).background(Color.White)
                        .padding(2.dp).clip(CircleShape).background(Color(0xFF10B981))
                )
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                card.name,
                fontSize = 12.sp,
                fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold,
                color = c.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(card.roleLabel, fontSize = 10.sp, fontWeight = FontWeight.Medium, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (card.shift.isNotBlank()) {
                Text(card.shift, fontSize = 10.sp, fontWeight = FontWeight.Medium, color = if (on) c.blue700 else c.muted, maxLines = 1)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (card.onDuty) Box(Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF10B981)))
                Text(
                    card.status,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (card.onDuty) Color(0xFF059669) else c.muted,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun AddCaregiverButton(
    c: HandoffColors,
    thai: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val radius = 12.dp
    Row(
        modifier
            .clip(RoundedCornerShape(radius))
            .background(c.card.copy(alpha = 0.5f))
            .dashedBorder(Color(0xFFCBD5E1), radius)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Filled.Add, null, tint = c.muted, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(if (thai) "เพิ่มผู้ดูแล" else "Add caregiver", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = c.muted, maxLines = 1)
    }
}

// ---------------------------------------------------------------------------------------------
// Daily routine (read-only for the parent)
// ---------------------------------------------------------------------------------------------

@Composable
private fun DailyRoutineCard(
    c: HandoffColors,
    thai: Boolean,
    day: CareRoutinePlan.Day,
    canEdit: Boolean,
    onAdd: () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(4.dp, shape, ambientColor = Color(0x140F172A), spotColor = Color(0x140F172A))
            .clip(shape)
            .background(c.card)
            .border(1.dp, c.soft, shape)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (thai) "📋 สิ่งที่ต้องทำวันนี้ (Daily Routine)" else "📋 Today's care tasks",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = c.ink
                )
                Text(
                    if (thai) "รายการดูแลและบันทึกกิจกรรมประจำวัน" else "The day's care list and activity log",
                    fontSize = 11.sp,
                    color = c.muted
                )
            }
            Spacer(Modifier.width(6.dp))
            Text(
                if (thai) "สำเร็จ ${day.done}/${day.total} ข้อ" else "Done ${day.done}/${day.total}",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = c.blue700,
                maxLines = 1,
                modifier = Modifier.clip(CircleShape).background(c.blueTint)
                    .border(1.dp, c.blueLine, CircleShape)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
        HorizontalDivider(Modifier.padding(top = 12.dp), color = c.soft)
        Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (day.total == 0) {
                Text(if (thai) "ยังไม่มีรายการสำหรับวันนี้" else "No tasks on today's list yet", fontSize = 12.sp, color = c.muted)
            }
            day.slots.forEach { RoutineRow(c = c, thai = thai, slot = it) }
        }
        if (canEdit) {
            val radius = 12.dp
            Row(
                Modifier
                    .padding(top = 14.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(radius))
                    .dashedBorder(Color(0xFFBFDBFE), radius, 2.dp)
                    .clickable(onClick = onAdd)
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Add, null, tint = c.blue, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    if (thai) "เพิ่มสิ่งที่ต้องทำ / โน้ตพิเศษสำหรับวันนี้" else "Add a task / note for today",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = c.blue
                )
            }
        }
    }
}

@Composable
private fun RoutineRow(c: HandoffColors, thai: Boolean, slot: CareRoutinePlan.Slot) {
    val done = slot.status == CareRoutinePlan.Status.DONE
    val current = !done && slot.status == CareRoutinePlan.Status.CURRENT
    val warning = slot.entry.prepNote.startsWith(WARNING_PREFIX)
    val shape = RoundedCornerShape(12.dp)
    val bg = when {
        done -> if (c.dark) c.soft.copy(alpha = 0.3f) else Color(0xFFF8FAFC)
        current -> c.blueTint.copy(alpha = if (c.dark) 0.5f else 0.4f)
        else -> c.card
    }
    val border = when {
        done -> c.soft
        current -> Color(0xFFBFDBFE)
        else -> c.line
    }
    val strike = if (done) TextDecoration.LineThrough else null
    val pill = categoryTint(slot.entry.category)

    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min).clip(shape).background(bg).border(1.dp, border, shape)
    ) {
        if (current) Box(Modifier.width(4.dp).fillMaxHeight().background(c.blue))
        Row(
            Modifier.weight(1f).padding(start = if (current) 8.dp else 10.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ReadOnlyCheck(checked = done, highlight = current, c = c)
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        CareRoutinePlan.formatMinuteOfDay(slot.entry.minuteOfDay) + if (thai) " น." else "",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            done -> c.muted
                            current -> c.blue700
                            else -> c.inkSoft
                        },
                        textDecoration = strike
                    )
                    Text(
                        ParentHandoffBrief.shortCategory(slot.entry.category, thai),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = pill.fg,
                        modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(pill.bg).padding(horizontal = 6.dp, vertical = 1.dp)
                    )
                    val recorded = slot.log?.recordedMinute
                    if (done && recorded != null) {
                        Text(
                            (if (thai) "✓ เสร็จเมื่อ " else "✓ done ") + CareRoutinePlan.formatMinuteOfDay(recorded) + if (thai) " น." else "",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF059669),
                            maxLines = 1
                        )
                    }
                    if (current) {
                        Spacer(Modifier.weight(1f))
                        UpNextPill(thai)
                    }
                }
                Text(
                    slot.entry.title,
                    fontSize = 12.sp,
                    fontWeight = if (current) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (done) c.muted else c.ink,
                    textDecoration = strike,
                    modifier = Modifier.padding(top = 2.dp)
                )
                if (warning) {
                    Row(
                        Modifier.padding(top = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Filled.WarningAmber, null, tint = Color(0xFFE11D48), modifier = Modifier.size(12.dp))
                        Text(
                            (if (thai) "คำเตือน: " else "Caution: ") + slot.entry.prepNote.removePrefix(WARNING_PREFIX),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFE11D48)
                        )
                    }
                }
                val sub = slot.entry.detail.ifBlank { if (warning) "" else slot.entry.prepNote }
                if (sub.isNotBlank()) {
                    Text(sub, fontSize = 11.sp, color = if (current) c.inkSoft else c.muted, modifier = Modifier.padding(top = 2.dp))
                }
                val note = slot.log?.note.orEmpty()
                if (note.isNotBlank()) {
                    Text(
                        (if (thai) "บันทึก: " else "Note: ") + note,
                        fontSize = 11.sp,
                        color = c.muted,
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(c.card)
                            .border(1.dp, c.line.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 5.dp)
                    )
                }
            }
        }
    }
}

/** Display-only: ticking belongs to the caregiver's checklist screen. */
@Composable
private fun ReadOnlyCheck(checked: Boolean, highlight: Boolean, c: HandoffColors) {
    val shape = RoundedCornerShape(4.dp)
    Box(
        Modifier
            .padding(top = 2.dp)
            .size(16.dp)
            .clip(shape)
            .background(if (checked) c.blue else c.card)
            .border(1.5.dp, if (checked) c.blue else if (highlight) Color(0xFF60A5FA) else Color(0xFFCBD5E1), shape),
        contentAlignment = Alignment.Center
    ) {
        if (checked) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(12.dp))
    }
}

@Composable
private fun UpNextPill(thai: Boolean) {
    Row(
        Modifier.clip(CircleShape).background(Color(0xFFFFFBEB)).border(1.dp, Color(0xFFFDE68A), CircleShape)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(Color(0xFFF59E0B)))
        Text(if (thai) "กำลังจะถึง" else "Up next", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB45309), maxLines = 1)
    }
}

// ---------------------------------------------------------------------------------------------
// Medical, AI, contacts, last record
// ---------------------------------------------------------------------------------------------

@Composable
private fun MedicalCard(c: HandoffColors, thai: Boolean, childName: String, items: List<ParentHandoffBrief.MedicalItem>) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                if (c.dark) Brush.linearGradient(listOf(Color(0x33F59E0B), Color(0x22F43F5E)))
                else Brush.linearGradient(listOf(Color(0xFFFFFBEB), Color(0xFFFFF1F2)))
            )
            .border(1.dp, Color(0xFFFDE68A).copy(alpha = 0.8f), shape)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFFF59E0B)),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.WarningAmber, null, tint = Color.White, modifier = Modifier.size(16.dp)) }
            Column {
                Text(
                    if (thai) "คำเตือน & ข้อควรระวังทางการแพทย์" else "MEDICAL CAUTIONS",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = c.ink
                )
                Text(
                    if (thai) "แนวทางปฏิบัติฉุกเฉินเฉพาะบุคคลสำหรับน้อง $childName"
                    else "What to do for $childName, from this child's own records",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (c.dark) Color(0xFFFCD34D) else Color(0xFF78350F)
                )
            }
        }
        HorizontalDivider(Modifier.padding(top = 10.dp), color = Color(0xFFFDE68A).copy(alpha = 0.6f))
        Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items.forEach { item ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(c.card.copy(alpha = 0.9f))
                        .border(1.dp, Color(0xFFFDE68A), RoundedCornerShape(12.dp))
                        .padding(10.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        when (item.kind) {
                            ParentHandoffBrief.MedicalItem.Kind.FEVER -> "🌡️"
                            ParentHandoffBrief.MedicalItem.Kind.DIARRHEA -> "💩"
                            ParentHandoffBrief.MedicalItem.Kind.CHRONIC -> "🩺"
                        },
                        fontSize = 14.sp
                    )
                    Column {
                        Text(item.title, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = c.ink)
                        Text(item.body, fontSize = 11.sp, color = c.inkSoft, lineHeight = 17.sp, modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun AiInsightCard(thai: Boolean, quote: String) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .shadow(8.dp, shape, ambientColor = Color(0x4D2563EB), spotColor = Color(0x4D2563EB))
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(Color(0xFF1D4ED8), Color(0xFF2563EB), Color(0xFF4338CA))))
    ) {
        Box(Modifier.align(Alignment.TopEnd).offset(8.dp, (-8).dp).size(96.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.1f)))
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier.clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.2f)).padding(6.dp),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Filled.Star, null, tint = Color(0xFFFCD34D), modifier = Modifier.size(16.dp)) }
                Text(
                    "NONGRAK AI HANDOVER INSIGHT",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFDBEAFE),
                    letterSpacing = 0.5.sp,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    if (thai) "อัปเดตรอบใหม่" else "Fresh",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    maxLines = 1,
                    modifier = Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.2f)).padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
            Text("“$quote”", fontSize = 12.sp, color = Color(0xFFEFF6FF), lineHeight = 19.sp)
        }
    }
}

@Composable
private fun ContactsCard(
    c: HandoffColors,
    thai: Boolean,
    parent: Pair<String, String>?,
    onCallParent: () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(4.dp, shape, ambientColor = Color(0x140F172A), spotColor = Color(0x140F172A))
            .clip(shape)
            .background(c.card)
            .border(1.dp, c.soft, shape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (thai) "ผู้ติดต่อด่วน" else "EMERGENCY CONTACT",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = c.inkSoft,
                modifier = Modifier.weight(1f)
            )
            Text(if (thai) "แตะเพื่อโทรทันที" else "Tap to call", fontSize = 10.sp, color = c.faint)
        }
        ContactTile(
            c = c,
            icon = Icons.Filled.Phone,
            iconBg = Color(0xFFFCE7F3),
            iconTint = Color(0xFFDB2777),
            title = parent?.first ?: if (thai) "ผู้ปกครอง" else "Parent",
            sub = parent?.second ?: if (thai) "ยังไม่มีเบอร์ในโปรไฟล์" else "No phone on file",
            subColor = if (parent != null) Color(0xFF059669) else c.muted,
            onlineDot = false,
            modifier = Modifier.fillMaxWidth(),
            onClick = onCallParent
        )
    }
}

@Composable
private fun ContactTile(
    c: HandoffColors,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconBg: Color,
    iconTint: Color,
    title: String,
    sub: String,
    subColor: Color,
    onlineDot: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier
            .clip(shape)
            .background(if (c.dark) c.soft.copy(alpha = 0.3f) else Color(0xFFF8FAFC))
            .border(1.dp, c.line.copy(alpha = 0.8f), shape)
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box {
            Box(Modifier.size(32.dp).clip(CircleShape).background(iconBg), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = iconTint, modifier = Modifier.size(16.dp))
            }
            if (onlineDot) {
                Box(
                    Modifier.align(Alignment.TopEnd).size(10.dp).clip(CircleShape).background(Color.White)
                        .padding(2.dp).clip(CircleShape).background(Color(0xFF10B981))
                )
            }
        }
        Column {
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = subColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun LastRecordRow(c: HandoffColors, thai: Boolean, label: String?, onHistory: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.card)
            .border(1.dp, c.line.copy(alpha = 0.8f), shape)
            .clickable(onClick = onHistory)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF10B981)))
        Spacer(Modifier.width(6.dp))
        Text(
            label ?: if (thai) "ยังไม่มีบันทึกวันนี้" else "No record yet today",
            fontSize = 12.sp,
            color = c.muted,
            modifier = Modifier.weight(1f)
        )
        Text(if (thai) "ดูประวัติย้อนหลัง" else "Full history", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = c.blue)
        Icon(Icons.Filled.ChevronRight, null, tint = c.blue, modifier = Modifier.size(14.dp))
    }
}

// ---------------------------------------------------------------------------------------------
// Add-task sheet (Stitch "เพิ่มรายการดูแล / กิจกรรม")
// ---------------------------------------------------------------------------------------------

private data class SheetCategory(val key: String, val emoji: String, val th: String, val en: String)

private val SHEET_CATEGORIES = listOf(
    SheetCategory("feeding", "🍼", "การกิน", "Feeding"),
    SheetCategory("activity", "🪀", "กิจกรรม", "Activity"),
    SheetCategory("solid", "🥣", "อาหารเสริม", "Solids"),
    SheetCategory("sleep", "💤", "นอน", "Sleep"),
    SheetCategory("hygiene", "🛁", "สุขอนามัย", "Hygiene"),
    SheetCategory("other", "📝", "โน้ตพิเศษ", "Special note")
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddTodayItemSheet(
    c: HandoffColors,
    thai: Boolean,
    onDismiss: () -> Unit,
    onSave: (timeOfDay: String, category: String, title: String, detail: String, prepNote: String) -> Unit
) {
    var category by remember { mutableStateOf("feeding") }
    var priority by remember { mutableIntStateOf(0) } // 0 normal, 1 high, 2 caution
    var title by remember { mutableStateOf("") }
    var detail by remember { mutableStateOf("") }
    var pickTime by remember { mutableStateOf(false) }
    var priorityOpen by remember { mutableStateOf(false) }
    val now = remember { java.time.LocalTime.now() }
    val timeState = rememberTimePickerState(initialHour = now.hour, initialMinute = (now.minute / 5) * 5, is24Hour = true)
    val priorities = if (thai) listOf("ปกติ (Normal)", "สำคัญมาก (High)", "ระวังเป็นพิเศษ ⚠️")
    else listOf("Normal", "High", "Take extra care ⚠️")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.card
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding()
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(32.dp).clip(CircleShape).background(c.blueTint), contentAlignment = Alignment.Center) {
                    Text("+", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.blue)
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (thai) "เพิ่มรายการดูแล / กิจกรรม" else "Add a care item", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.ink)
                    Text(
                        if (thai) "กำหนดสิ่งที่ต้องทำสำหรับผู้ดูแลวันนี้" else "Set what the caregiver should do today",
                        fontSize = 11.sp,
                        color = c.muted
                    )
                }
                Box(
                    Modifier.size(32.dp).clip(CircleShape).background(c.soft).clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Filled.Close, if (thai) "ปิด" else "Close", tint = c.muted, modifier = Modifier.size(16.dp)) }
            }
            HorizontalDivider(color = c.soft)

            SheetLabel(c, if (thai) "หมวดหมู่กิจกรรม" else "Category")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SHEET_CATEGORIES.forEach { cat ->
                    val selected = category == cat.key
                    Text(
                        "${cat.emoji} ${if (thai) cat.th else cat.en}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (selected) c.blue700 else c.inkSoft,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (selected) Color(0xFFDBEAFE) else c.soft)
                            .border(1.dp, if (selected) Color(0xFFBFDBFE) else c.line, CircleShape)
                            .clickable { category = cat.key }
                            .padding(horizontal = 12.dp, vertical = 5.dp)
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SheetLabel(c, if (thai) "เวลา" else "Time")
                    SheetFieldBox(c, onClick = { pickTime = true }) {
                        Text("%02d:%02d".format(timeState.hour, timeState.minute), fontSize = 12.sp, color = c.ink)
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SheetLabel(c, if (thai) "ความสำคัญ" else "Priority")
                    Box {
                        SheetFieldBox(c, onClick = { priorityOpen = true }) {
                            Text(priorities[priority], fontSize = 12.sp, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        DropdownMenu(expanded = priorityOpen, onDismissRequest = { priorityOpen = false }) {
                            priorities.forEachIndexed { i, label ->
                                DropdownMenuItem(text = { Text(label, fontSize = 13.sp) }, onClick = { priority = i; priorityOpen = false })
                            }
                        }
                    }
                }
            }

            SheetLabel(c, if (thai) "ชื่อกิจกรรม / งานที่ต้องทำ" else "Task")
            SheetTextField(
                c = c,
                value = title,
                onValueChange = { if (it.length <= 80) title = it },
                placeholder = if (thai) "เช่น เสริมผลไม้มื้อบ่าย, ล้างขวดนม" else "e.g. afternoon fruit, wash bottles",
                singleLine = true
            )
            SheetLabel(c, if (thai) "คำแนะนำ / บันทึกเพิ่มเติมสำหรับพี่เลี้ยง" else "Instructions for the caregiver")
            SheetTextField(
                c = c,
                value = detail,
                onValueChange = { if (it.length <= 290) detail = it },
                placeholder = if (thai) "เช่น สังเกตการเคี้ยวกลืน, เก็บของเล่นหลังใช้" else "e.g. watch chewing, tidy toys after",
                singleLine = false
            )
            if (priority == 2) {
                Text(
                    if (thai) "ข้อความนี้จะแสดงเป็นคำเตือนสีแดงให้ผู้ดูแลเห็น" else "This will show to the caregiver as a red caution",
                    fontSize = 11.sp,
                    color = Color(0xFFE11D48)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) { Text(if (thai) "ยกเลิก" else "Cancel", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = c.inkSoft) }
                Button(
                    onClick = {
                        val time = "%02d:%02d".format(timeState.hour, timeState.minute)
                        val cleanTitle = title.trim()
                        val cleanDetail = detail.trim()
                        if (priority == 2) {
                            val caution = cleanDetail.ifBlank { if (thai) "ระวังเป็นพิเศษ" else "Take extra care" }
                            onSave(time, category, cleanTitle, "", WARNING_PREFIX + caution)
                        } else {
                            onSave(time, category, cleanTitle, cleanDetail, "")
                        }
                    },
                    enabled = title.isNotBlank(),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = c.blue)
                ) { Text(if (thai) "เพิ่มรายการ" else "Add", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
            }
        }
    }

    if (pickTime) {
        AlertDialog(
            onDismissRequest = { pickTime = false },
            confirmButton = { TextButton(onClick = { pickTime = false }) { Text(if (thai) "ตกลง" else "OK") } },
            text = { TimePicker(state = timeState) }
        )
    }
}

@Composable
private fun SheetLabel(c: HandoffColors, text: String) {
    Text(text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = c.inkSoft)
}

@Composable
private fun SheetFieldBox(c: HandoffColors, onClick: () -> Unit, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (c.dark) c.soft.copy(alpha = 0.3f) else Color(0xFFF8FAFC))
            .border(1.dp, c.line, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) { content() }
}

@Composable
private fun SheetTextField(
    c: HandoffColors,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    singleLine: Boolean
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, fontSize = 12.sp, color = c.faint) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 2,
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = c.ink),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = c.line,
            focusedBorderColor = c.blue,
            unfocusedContainerColor = if (c.dark) c.soft.copy(alpha = 0.3f) else Color(0xFFF8FAFC),
            focusedContainerColor = if (c.dark) c.soft.copy(alpha = 0.3f) else Color(0xFFF8FAFC)
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

// ---------------------------------------------------------------------------------------------
// Tokens
// ---------------------------------------------------------------------------------------------

private fun Modifier.dashedBorder(color: Color, radius: Dp, width: Dp = 1.5.dp) = drawBehind {
    drawRoundRect(
        color = color,
        cornerRadius = CornerRadius(radius.toPx()),
        style = Stroke(width = width.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx())))
    )
}

private data class HandoffColors(
    val dark: Boolean,
    val page: Color,
    val card: Color,
    val ink: Color,
    val inkSoft: Color,
    val muted: Color,
    val faint: Color,
    val blue: Color,
    val blue700: Color,
    val blueTint: Color,
    val blueLine: Color,
    val line: Color,
    val soft: Color
)

@Composable
private fun handoffColors(): HandoffColors {
    val dark = LocalDarkTheme.current
    val scheme = MaterialTheme.colorScheme
    return if (dark) HandoffColors(
        dark = true,
        page = scheme.background,
        card = scheme.surface,
        ink = scheme.onSurface,
        inkSoft = scheme.onSurface.copy(alpha = 0.85f),
        muted = scheme.onSurfaceVariant,
        faint = scheme.outline,
        blue = Color(0xFF60A5FA),
        blue700 = Color(0xFF93C5FD),
        blueTint = Color(0xFF60A5FA).copy(alpha = 0.16f),
        blueLine = Color(0xFF60A5FA).copy(alpha = 0.3f),
        line = scheme.outline.copy(alpha = 0.4f),
        soft = scheme.outlineVariant.copy(alpha = 0.4f)
    ) else HandoffColors(
        dark = false,
        page = Color(0xFFF4F7FC),
        card = Color.White,
        ink = Color(0xFF0F172A),
        inkSoft = Color(0xFF334155),
        muted = Color(0xFF64748B),
        faint = Color(0xFF94A3B8),
        blue = Color(0xFF2563EB),
        blue700 = Color(0xFF1D4ED8),
        blueTint = Color(0xFFEFF6FF),
        blueLine = Color(0xFFDBEAFE),
        line = Color(0xFFE2E8F0),
        soft = Color(0xFFF1F5F9)
    )
}

private class Tint(val bg: Color, val fg: Color)

/** Stitch pill colours per category. */
private fun categoryTint(category: String): Tint = when (category) {
    "feeding" -> Tint(Color(0xFFDBEAFE), Color(0xFF1D4ED8))
    "activity" -> Tint(Color(0xFFE0E7FF), Color(0xFF4338CA))
    "solid" -> Tint(Color(0xFFD1FAE5), Color(0xFF065F46))
    "sleep" -> Tint(Color(0xFFF3E8FF), Color(0xFF7E22CE))
    "hygiene" -> Tint(Color(0xFFCFFAFE), Color(0xFF155E75))
    "health" -> Tint(Color(0xFFEFF6FF), Color(0xFF1D4ED8))
    "night" -> Tint(Color(0xFFEDE9FE), Color(0xFF4F46E5))
    else -> Tint(Color(0xFFF1F5F9), Color(0xFF475569))
}

fun handoffLabel(value: String, thai: Boolean): String = if (!thai) when (value) {
    "pending" -> "Waiting for acknowledgment"
    "active" -> "Care acknowledged"
    "accept" -> "I'm taking over"
    "complete" -> "Finish and send summary"
    "acknowledge" -> "Acknowledge return"
    "closed" -> "Return acknowledged"
    else -> value.replaceFirstChar { it.uppercase() }
} else when (value) {
    "pending" -> "รอผู้ดูแลตอบรับ"
    "active" -> "รับช่วงดูแลแล้ว"
    "accept" -> "รับช่วงดูแลแล้ว"
    "decline" -> "ไม่สะดวกรับช่วง"
    "declined" -> "ปฏิเสธแล้ว"
    "cancel" -> "ยกเลิกคำขอ"
    "cancelled" -> "ยกเลิกแล้ว"
    "complete" -> "จบการดูแลและส่งสรุป"
    "completed" -> "รอรับทราบสรุป"
    "acknowledge" -> "รับทราบสรุปแล้ว"
    "closed" -> "รับทราบสรุปแล้ว"
    else -> value
}

fun displayHandoffTime(value: String): String = runCatching {
    java.time.OffsetDateTime.parse(value).atZoneSameInstant(java.time.ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofPattern("d MMM HH:mm"))
}.getOrDefault(value)
