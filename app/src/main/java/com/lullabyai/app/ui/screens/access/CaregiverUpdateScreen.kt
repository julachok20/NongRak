@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.access

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.lullabyai.app.ui.components.LullabyTopBar
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.util.TimeUtils
import com.lullabyai.app.viewmodel.AccessRoleLabels
import com.lullabyai.app.viewmodel.CareKinds
import com.lullabyai.app.viewmodel.CaregiverUpdateViewModel

/* "Serene Clinical" tokens, straight from the design's tailwind config. */
private val ScBackground = Color(0xFFF8F9FF)
private val ScSurfaceLowest = Color(0xFFFFFFFF)
private val ScSurfaceContainerLow = Color(0xFFEFF4FF)
private val ScSurfaceContainer = Color(0xFFE5EEFF)
private val ScSurfaceContainerHighest = Color(0xFFD3E4FE)
private val ScOnSurface = Color(0xFF0B1C30)
private val ScOnSurfaceVariant = Color(0xFF444654)
private val ScOutlineVariant = Color(0xFFC4C5D6)
private val ScPrimary = Color(0xFF173DB8)
private val ScPrimaryContainer = Color(0xFF3858D1)
private val ScOnPrimaryContainer = Color(0xFFD9DEFF)
private val ScPrimaryFixed = Color(0xFFDDE1FF)
private val ScError = Color(0xFFBA1A1A)
private val ScHealthGood = Color(0xFF007064)

/**
 * Design: "update info child (Care-taker Role)".
 *
 * The carer's day in one page: what the app noticed, one tap to reach a parent, then the activity
 * tiles — each showing when it last happened, so the grid says what still needs doing.
 */
@Composable
fun CaregiverUpdateScreen(
    viewModel: CaregiverUpdateViewModel,
    navController: NavController,
    onBack: () -> Unit,
    onChat: ((String) -> Unit)? = null
) {
    val s = LocalStrings.current
    val isThai = s.isThai
    val state by viewModel.uiState.collectAsState()
    var showSummary by remember { mutableStateOf(false) }
    var showParentPicker by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.load() }

    Scaffold(
        containerColor = ScBackground,
        topBar = {
            // Bell only — the design keeps chat in the body as a card, not a header icon.
            LullabyTopBar(
                title = "NongRak AI",
                onBack = onBack,
                navController = navController
            )
        },
        bottomBar = { AccessPermissionBottomNav(navController) }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Column(Modifier.padding(top = 8.dp)) {
                Text(
                    if (isThai) "อัปเดตและสื่อสารเกี่ยวกับบุตร" else "Update & communicate",
                    fontSize = 26.sp,
                    lineHeight = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = ScOnSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (isThai) "บันทึกกิจกรรมประจำวันและพูดคุยกับผู้ปกครอง"
                    else "Log the day's care and talk to the parents",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ScOnSurfaceVariant
                )
            }

            // Concierge alert. Reads today's own log — a real observation, not an invented line.
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(ScSurfaceContainerLow)
                    .drawLeftAccent(ScPrimary)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    Modifier.size(40.dp).background(ScPrimaryFixed, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.SmartToy, null,
                        tint = ScPrimary, modifier = Modifier.size(22.dp)
                    )
                }
                Column {
                    Text(
                        "NongRak Suggestion",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = ScOnSurface
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        suggestionText(state.today.size, state.concerns.size, isThai),
                        style = MaterialTheme.typography.bodySmall,
                        color = ScOnSurfaceVariant
                    )
                }
            }

            if (onChat != null && state.parents.isNotEmpty()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(ScPrimaryContainer)
                        .clickable { showParentPicker = true }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(40.dp).background(ScPrimaryFixed, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Group, null,
                            tint = ScPrimary, modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (isThai) "แชทกับเจ้าของร่วม" else "Chat with the parents",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            if (isThai) "ปรึกษาหรือแจ้งข้อมูลเพิ่มเติมกับผู้ปกครอง"
                            else "Ask or report something to the parents",
                            style = MaterialTheme.typography.bodySmall,
                            color = ScOnPrimaryContainer
                        )
                    }
                    if (state.totalUnread > 0) {
                        Box(
                            Modifier
                                .background(ScError, CircleShape)
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(
                                "${state.totalUnread}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                    }
                    Icon(Icons.Filled.ChevronRight, null, tint = Color.White)
                }
            }

            // Activity dashboard: one white container holding the header, the tiles and the form.
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(ScSurfaceLowest)
                    .border(1.dp, ScOutlineVariant.copy(0.3f), RoundedCornerShape(16.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Column {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Dashboard, null,
                            tint = ScPrimary, modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (isThai) "บันทึกกิจกรรม" else "Log activity",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = ScOnSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Row(
                            Modifier
                                .clip(CircleShape)
                                .background(ScPrimary)
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.size(7.dp).background(Color.White, CircleShape))
                            Spacer(Modifier.width(7.dp))
                            Text(
                                if (isThai) "กำลังอัปเดต" else "Live",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider(color = ScOutlineVariant.copy(0.2f))
                }

                CareKinds.ALL.chunked(2).forEach { row ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        row.forEach { k ->
                            ActivityTile(
                                emoji = k.emoji,
                                label = if (isThai) k.th else k.en,
                                isOther = k.kind == "other",
                                subtitle = state.lastByKind[k.kind]?.let {
                                    val t = TimeUtils.localTime(it.loggedAt).orEmpty()
                                    if (isThai) "ล่าสุด: $t น." else "Last: $t"
                                } ?: if (k.kind == "other") {
                                    if (isThai) "ระบุกิจกรรม" else "Name it"
                                } else {
                                    if (isThai) "ยังไม่มีกิจกรรม" else "Not yet today"
                                },
                                selected = state.selectedKind == k.kind,
                                isThai = isThai,
                                modifier = Modifier.weight(1f),
                                onClick = { viewModel.selectKind(k.kind) },
                                // "อื่นๆ" reveals its own field, per the design — naming the
                                // activity IS the record, so it belongs on the tile rather than
                                // buried in the notes box further down.
                                customValue = if (k.kind == "other") state.otherLabel else null,
                                onCustomChange = viewModel::setOtherLabel
                            )
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }

                // Guided form, in the design's tinted panel.
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(ScSurfaceContainerLow)
                        .border(1.dp, ScOutlineVariant.copy(0.2f), RoundedCornerShape(12.dp))
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedTextField(
                        value = state.note,
                        onValueChange = viewModel::setNote,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        label = { Text(if (isThai) "รายละเอียดเพิ่มเติม" else "Details (optional)") }
                    )

                    Text(
                        if (isThai) "วันนี้น้องเป็นอย่างไรบ้าง?" else "How was the child today?",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = ScOnSurface
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CareKinds.MOODS.forEach { (key, th, en) ->
                            val selected = state.mood == key
                            Box(
                                Modifier
                                    .weight(1f)
                                    .clip(CircleShape)
                                    .background(if (selected) ScPrimary else ScSurfaceLowest)
                                    .then(
                                        if (selected) Modifier
                                        else Modifier.border(1.dp, ScOutlineVariant, CircleShape)
                                    )
                                    .clickable { viewModel.setMood(key) }
                                    .padding(vertical = 9.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    if (isThai) th else en,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (selected) Color.White else ScOnSurfaceVariant
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = state.abnormal,
                        onValueChange = viewModel::setAbnormal,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        label = { Text(if (isThai) "มีอาการผิดปกติหรือไม่?" else "Anything unusual?") },
                        supportingText = {
                            Text(
                                if (isThai) "ถ้ากรอก ผู้ปกครองจะเห็นข้อความนี้เป็นหัวข้อหลัก"
                                else "If filled, the parents see this as the headline"
                            )
                        }
                    )

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .border(1.dp, ScOutlineVariant, RoundedCornerShape(10.dp))
                                .clickable { viewModel.clearForm() }
                                .padding(vertical = 13.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                if (isThai) "ล้างข้อมูล" else "Clear",
                                style = MaterialTheme.typography.labelLarge,
                                color = ScOnSurfaceVariant
                            )
                        }
                        Box(
                            Modifier
                                .weight(2f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (state.canSubmit) ScPrimary else ScOutlineVariant)
                                .clickable(enabled = state.canSubmit) { viewModel.submit() }
                                .padding(vertical = 13.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (state.saving) {
                                CircularProgressIndicator(
                                    Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White
                                )
                            } else {
                                Text(
                                    if (isThai) "บันทึกอัปเดต" else "Save update",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Row(
                        Modifier
                            .clip(CircleShape)
                            .clickable { showSummary = true }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Summarize, null,
                            tint = ScPrimary, modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (isThai) "สรุปรายวัน (${state.today.size})"
                            else "Daily summary (${state.today.size})",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = ScPrimary
                        )
                    }
                }
            }

            if (state.savedOk) {
                Text(
                    if (isThai) "บันทึกแล้ว — ผู้ปกครองเห็นในประวัติกิจกรรมทันที"
                    else "Saved — the parents see it in the activity history immediately",
                    color = ScHealthGood,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            state.error?.let {
                Text(it, color = ScError, style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    if (showParentPicker && onChat != null) {
        AlertDialog(
            onDismissRequest = { showParentPicker = false },
            title = { Text(if (isThai) "คุยกับใคร?" else "Chat with whom?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    state.parents.forEach { p ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { showParentPicker = false; onChat(p.userId) }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AccessAvatar(p.avatarUrl, p.displayName)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(p.displayName, fontWeight = FontWeight.Bold)
                                // Owner and co-owner are different people to tell about a fever.
                                Text(
                                    AccessRoleLabels.title(p.role, isThai),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ScOnSurfaceVariant
                                )
                            }
                            val unread = state.unreadByPeer[p.userId] ?: 0
                            if (unread > 0) {
                                Box(
                                    Modifier
                                        .background(ScError, CircleShape)
                                        .padding(horizontal = 7.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        "$unread",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                                Spacer(Modifier.width(6.dp))
                            }
                            Icon(Icons.Filled.ChevronRight, null, tint = ScOutlineVariant)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showParentPicker = false }) {
                    Text(if (isThai) "ยกเลิก" else "Cancel")
                }
            }
        )
    }

    if (showSummary) {
        ModalBottomSheet(onDismissRequest = { showSummary = false }) {
            DailySummarySheet(
                concerns = state.concerns.mapNotNull { c ->
                    c.abnormal?.let { TimeUtils.localTime(c.loggedAt).orEmpty() to it }
                },
                summary = state.summary,
                total = state.today.size,
                isThai = isThai
            )
        }
    }
}

/**
 * One activity tile. The selected one takes the design's filled treatment plus a "กำลังทำ" chip,
 * so what is in progress is obvious without reading a single label.
 */
@Composable
private fun ActivityTile(
    emoji: String,
    label: String,
    subtitle: String,
    isOther: Boolean,
    selected: Boolean,
    isThai: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    /** Non-null only on the "อื่นๆ" tile; shows an inline field once the tile is armed. */
    customValue: String? = null,
    onCustomChange: (String) -> Unit = {}
) {
    Box(modifier) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(if (selected) ScSurfaceContainerHighest else ScSurfaceContainer)
                .then(
                    if (selected) Modifier.border(2.dp, ScPrimary, RoundedCornerShape(16.dp))
                    else Modifier.border(1.dp, ScOutlineVariant.copy(0.3f), RoundedCornerShape(16.dp))
                )
                .clickable(onClick = onClick)
                .padding(18.dp)
        ) {
            Box(
                Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(ScSurfaceLowest),
                contentAlignment = Alignment.Center
            ) {
                if (isOther) {
                    Icon(
                        Icons.Filled.AddCircle, null,
                        tint = ScPrimary, modifier = Modifier.size(30.dp)
                    )
                } else {
                    Text(emoji, fontSize = 28.sp)
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                label,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = ScOnSurface,
                maxLines = 1
            )
            Spacer(Modifier.height(4.dp))
            // Once "อื่นๆ" is armed the subtitle gives way to the field it was prompting for.
            if (customValue != null && selected) {
                OutlinedTextField(
                    value = customValue,
                    onValueChange = onCustomChange,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(6.dp),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall,
                    placeholder = {
                        Text(
                            if (isThai) "พิมพ์กิจกรรมที่นี่..." else "Type the activity...",
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = ScSurfaceLowest,
                        unfocusedContainerColor = ScSurfaceLowest,
                        focusedBorderColor = ScPrimary,
                        unfocusedBorderColor = ScOutlineVariant.copy(0.4f)
                    )
                )
            } else {
                Text(subtitle, fontSize = 13.sp, color = ScOnSurfaceVariant, maxLines = 1)
            }
        }
        if (selected) {
            Row(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(ScSurfaceLowest.copy(0.85f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(6.dp).background(ScPrimary, CircleShape))
                Spacer(Modifier.width(5.dp))
                Text(
                    if (isThai) "กำลังทำ" else "Doing",
                    style = MaterialTheme.typography.labelSmall,
                    color = ScPrimary
                )
            }
        }
    }
}

/** The design's 4px primary rule down the left edge of the concierge alert. */
private fun Modifier.drawLeftAccent(color: Color): Modifier =
    this.drawWithContent {
        drawContent()
        drawRect(color = color, size = Size(4.dp.toPx(), size.height))
    }

/** Reads today's own log, so the line is an observation rather than a generated claim. */
private fun suggestionText(total: Int, concerns: Int, isThai: Boolean): String = when {
    concerns > 0 ->
        if (isThai) "มี $concerns รายการที่ควรแจ้งผู้ปกครอง อย่าลืมกดส่งอัปเดตนะคะ"
        else "$concerns entries need the parents' attention — remember to send the update"
    total == 0 ->
        if (isThai) "ยังไม่มีบันทึกวันนี้ เริ่มจากกิจกรรมล่าสุดที่ทำไปได้เลยค่ะ"
        else "Nothing logged yet today — start with whatever just happened"
    else ->
        if (isThai) "วันนี้บันทึกไปแล้ว $total รายการ ผู้ปกครองเห็นทั้งหมดแบบเรียลไทม์ค่ะ"
        else "$total entries logged today — the parents see all of them in real time"
}

@Composable
private fun DailySummarySheet(
    concerns: List<Pair<String, String>>,
    summary: List<Pair<String, Int>>,
    total: Int,
    isThai: Boolean
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            if (isThai) "สรุปวันนี้" else "Today's summary",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            if (isThai) "ทั้งหมด $total รายการ" else "$total entries in total",
            style = MaterialTheme.typography.bodySmall,
            color = ScOnSurfaceVariant
        )

        // Concerns lead. A count of naps matters less than one "มีไข้ตอนบ่าย".
        if (concerns.isNotEmpty()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(ScError.copy(0.10f))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.WarningAmber, null,
                        tint = ScError, modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (isThai) "สิ่งที่ควรทราบ (${concerns.size})"
                        else "Needs attention (${concerns.size})",
                        fontWeight = FontWeight.Bold,
                        color = ScError
                    )
                }
                concerns.forEach { (time, text) ->
                    Text("$time  $text", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        if (summary.isEmpty()) {
            Text(
                if (isThai) "ยังไม่มีบันทึกวันนี้" else "Nothing logged today yet",
                color = ScOnSurfaceVariant
            )
        }

        summary.forEach { (kind, count) ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(ScSurfaceContainerLow)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(CareKinds.emoji(kind), fontSize = 20.sp)
                Spacer(Modifier.width(12.dp))
                Text(CareKinds.label(kind, isThai), Modifier.weight(1f))
                Text(
                    if (isThai) "$count ครั้ง" else "x$count",
                    fontWeight = FontWeight.Bold,
                    color = ScPrimary
                )
            }
        }
    }
}
