@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.access

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.lullabyai.app.ui.components.LullabyTopBar
import com.lullabyai.app.ui.components.PrimaryButton
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.util.TimeUtils
import com.lullabyai.app.viewmodel.CareKinds
import com.lullabyai.app.viewmodel.CaregiverUpdateViewModel

/**
 * Stitch: "Child Update & Caregiver Chat (อัปเดตและแชท)" — the carer's side.
 *
 * Pick one activity, optionally add a note, how the child seemed, and anything unusual, then save.
 * Everything written here lands in the parent's Activity History with this carer's name on it.
 *
 * [onChat] receives the parent to message. The design's "NongRak Suggestion" banner is deliberately
 * absent: the AI's real voice reaches the carer in the chat, where it reads actual messages —
 * a generated line here would have nothing behind it.
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
        topBar = {
            LullabyTopBar(
                title = if (isThai) "อัปเดตข้อมูลลูก" else "Child update",
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
                .padding(horizontal = 20.dp)
                .padding(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                if (isThai) "บันทึกกิจกรรมประจำวันและรายงานให้ผู้ปกครอง"
                else "Log the day's care and report it to the parents",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Real count, not a generated insight.
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Primary.copy(0.08f))
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.EditNote, null, tint = Primary, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            state.childName.ifBlank { if (isThai) "ลูก" else "Child" },
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            if (isThai) "วันนี้บันทึกแล้ว ${state.today.size} รายการ"
                            else "${state.today.size} entries logged today",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = { showSummary = true }) {
                        Icon(Icons.Filled.Summarize, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (isThai) "สรุปรายวัน" else "Summary", fontWeight = FontWeight.Bold)
                    }
                }
            }

            Text(
                if (isThai) "บันทึกอัปเดตด่วน" else "Quick update",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            // 3 x 3, mirroring the Stitch grid. Chunked rather than LazyVerticalGrid because the
            // page already scrolls — a nested lazy grid would need a fixed height here.
            CareKinds.ALL.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { k ->
                        val selected = state.selectedKind == k.kind
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .background(
                                    if (selected) Primary.copy(0.16f) else SurfaceContainerLow,
                                    RoundedCornerShape(16.dp)
                                )
                                .border(
                                    width = if (selected) 2.dp else 0.dp,
                                    color = if (selected) Primary else Color.Transparent,
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .clickable { viewModel.selectKind(k.kind) }
                                .padding(vertical = 14.dp, horizontal = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(k.emoji, fontSize = 26.sp)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                if (isThai) k.th else k.en,
                                style = MaterialTheme.typography.labelMedium,
                                textAlign = TextAlign.Center,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                    // Keep the last row aligned when it is short.
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }

            OutlinedTextField(
                value = state.note,
                onValueChange = viewModel::setNote,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                label = { Text(if (isThai) "รายละเอียดเพิ่มเติม (ถ้ามี)" else "Details (optional)") }
            )

            Text(
                if (isThai) "วันนี้น้องเป็นอย่างไรบ้าง?" else "How was the child today?",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CareKinds.MOODS.forEach { (key, th, en) ->
                    FilterChip(
                        selected = state.mood == key,
                        onClick = { viewModel.setMood(key) },
                        label = { Text(if (isThai) th else en) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Primary,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            OutlinedTextField(
                value = state.abnormal,
                onValueChange = viewModel::setAbnormal,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                label = { Text(if (isThai) "มีอาการผิดปกติหรือไม่?" else "Anything unusual?") },
                supportingText = {
                    Text(
                        if (isThai) "ถ้ากรอก ผู้ปกครองจะเห็นข้อความนี้เป็นหัวข้อหลักของรายการ"
                        else "If filled, the parents see this as the entry's headline"
                    )
                }
            )

            if (onChat != null && state.parents.isNotEmpty()) {
                OutlinedButton(
                    onClick = {
                        // One parent is the common case, so skip the picker entirely there.
                        if (state.parents.size == 1) onChat(state.parents.first().userId)
                        else showParentPicker = true
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Filled.ChatBubbleOutline, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (isThai) "คุยกับผู้ปกครอง" else "Chat with the parents",
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = viewModel::clearForm,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp)
                ) { Text(if (isThai) "ล้างข้อมูล" else "Clear") }
                Box(Modifier.weight(2f)) {
                    PrimaryButton(
                        text = if (isThai) "บันทึกอัปเดต" else "Save update",
                        onClick = viewModel::submit,
                        isLoading = state.saving,
                        enabled = state.canSubmit
                    )
                }
            }

            if (state.savedOk) {
                Text(
                    if (isThai) "บันทึกแล้ว — ผู้ปกครองเห็นในประวัติกิจกรรม"
                    else "Saved — visible to the parents in the activity history",
                    color = HealthGood,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            state.error?.let {
                Text(it, color = Error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    if (showParentPicker && onChat != null) {
        AlertDialog(
            onDismissRequest = { showParentPicker = false },
            title = { Text(if (isThai) "คุยกับใคร?" else "Chat with whom?") },
            text = {
                Column {
                    state.parents.forEach { p ->
                        TextButton(
                            onClick = { showParentPicker = false; onChat(p.userId) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(p.displayName, modifier = Modifier.weight(1f))
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
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Concerns lead. A count of nappies matters less than one "มีไข้ตอนบ่าย".
        if (concerns.isNotEmpty()) {
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Error.copy(0.10f))
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.WarningAmber, null, tint = Error, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (isThai) "สิ่งที่ควรทราบ (${concerns.size})"
                            else "Needs attention (${concerns.size})",
                            fontWeight = FontWeight.Bold,
                            color = Error
                        )
                    }
                    concerns.forEach { (time, text) ->
                        Text("$time  $text", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        if (summary.isEmpty()) {
            Text(
                if (isThai) "ยังไม่มีบันทึกวันนี้" else "Nothing logged today yet",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        summary.forEach { (kind, count) ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(SurfaceContainerLow, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(CareKinds.emoji(kind), fontSize = 20.sp)
                Spacer(Modifier.width(12.dp))
                Text(CareKinds.label(kind, isThai), Modifier.weight(1f))
                Text(
                    if (isThai) "$count ครั้ง" else "x$count",
                    fontWeight = FontWeight.Bold,
                    color = Primary
                )
            }
        }
    }
}
