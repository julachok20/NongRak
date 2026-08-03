@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.sleep

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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.lullabyai.app.ui.components.AppBottomNav
import com.lullabyai.app.ui.components.BottomTab
import com.lullabyai.app.ui.components.LullabyTopBar
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.ChildViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Route constant — registered by AppNavigation (screens don't touch the nav graph). */
const val RECORD_SLEEP_HISTORY_ROUTE = "record_sleep_history"

/**
 * Stitch "Record Sleep History (บันทึกการนอนย้อนหลัง)" — the Pro Sleep Tracker's `history` button.
 *
 * Distinct from [AddSleepTimeScreen], which hardcodes today's date and so structurally cannot log a
 * past night. This one carries a date picker so a parent can back-fill a night they forgot to
 * track. Both funnel into `ChildViewModel.addManualSleepLog`, keeping one write path for manual
 * sleep entries.
 */
@Composable
fun RecordSleepHistoryScreen(
    childViewModel: ChildViewModel,
    navController: NavController,
    onBack: () -> Unit
) {
    val s = LocalStrings.current
    val isThai = s.isThai
    val state by childViewModel.uiState.collectAsState()

    var sleepType by remember { mutableStateOf("main") }
    var recordDate by remember { mutableStateOf(LocalDate.now().minusDays(1)) }
    var startTime by remember { mutableStateOf("20:00") }
    var endTime by remember { mutableStateOf("06:30") }
    var showDatePicker by remember { mutableStateOf(false) }
    var formError by remember { mutableStateOf<String?>(null) }

    // "HH:MM" -> minutes since midnight; null while the text isn't a valid 24h time.
    fun toMinutes(t: String): Int? {
        val parts = t.split(":")
        if (parts.size != 2) return null
        val h = parts[0].trim().toIntOrNull() ?: return null
        val m = parts[1].trim().toIntOrNull() ?: return null
        return if (h in 0..23 && m in 0..59) h * 60 + m else null
    }

    val durationMinutes = remember(startTime, endTime) {
        val a = toMinutes(startTime)
        val b = toMinutes(endTime)
        if (a == null || b == null) null else (b - a).let { if (it <= 0) it + 24 * 60 else it }
    }

    if (showDatePicker) {
        val initial = recordDate.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = initial,
            // "ย้อนหลัง" means backwards — a future night cannot already have happened.
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    utcTimeMillis <= System.currentTimeMillis()
            }
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dateState.selectedDateMillis?.let { ms ->
                        recordDate = java.time.Instant.ofEpochMilli(ms)
                            .atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text(if (isThai) "ตกลง" else "OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(if (isThai) "ยกเลิก" else "Cancel") }
            }
        ) { DatePicker(state = dateState) }
    }

    Scaffold(
        topBar = {
            LullabyTopBar(
                title = if (isThai) "บันทึกการนอนย้อนหลัง" else "Record Sleep History",
                onBack = onBack,
                navController = navController
            )
        },
        // The lambdas are dead when LocalNavController is present (the footer navigates itself),
        // but AppBottomNav still declares them as required params — see the pending cleanup task.
        bottomBar = {
            AppBottomNav(
                selected = BottomTab.CHILD,
                onHome = {}, onChatbot = {}, onChild = {}, onStats = {}, onSettings = {}
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            // --- วันที่บันทึก ---
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel(if (isThai) "วันที่บันทึก" else "Record date")
                Surface(
                    onClick = { showDatePicker = true },
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 15.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Filled.CalendarToday, null, tint = Primary, modifier = Modifier.size(20.dp))
                        Text(
                            recordDate.format(DateTimeFormatter.ofPattern("d MMM yyyy")),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(Icons.Filled.ExpandMore, null, tint = Outline)
                    }
                }
            }

            // --- ประเภทการนอน ---
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel(if (isThai) "ประเภทการนอน" else "Sleep type")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SleepTypeCard(
                        icon = Icons.Filled.Bedtime,
                        label = if (isThai) "เวลานอนหลัก" else "Main sleep",
                        selected = sleepType == "main",
                        modifier = Modifier.weight(1f)
                    ) { sleepType = "main" }
                    SleepTypeCard(
                        icon = Icons.Filled.WbSunny,
                        label = if (isThai) "นอนกลางวัน" else "Nap",
                        selected = sleepType == "nap",
                        modifier = Modifier.weight(1f)
                    ) { sleepType = "nap" }
                }
            }

            // --- เวลาเริ่ม / เวลาจบ ---
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TimeField(
                    icon = Icons.Filled.Login,
                    label = if (isThai) "เวลาเริ่ม" else "Start time",
                    value = startTime,
                    onValueChange = { startTime = it; formError = null },
                    modifier = Modifier.weight(1f)
                )
                TimeField(
                    icon = Icons.Filled.Logout,
                    label = if (isThai) "เวลาจบ" else "End time",
                    value = endTime,
                    onValueChange = { endTime = it; formError = null },
                    modifier = Modifier.weight(1f)
                )
            }

            // --- ระยะเวลาทั้งหมด ---
            Surface(shape = RoundedCornerShape(14.dp), color = Primary.copy(alpha = 0.10f), modifier = Modifier.fillMaxWidth()) {
                Row(
                    Modifier.padding(16.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Filled.Bedtime, null, tint = Primary, modifier = Modifier.size(22.dp))
                    Text(
                        if (isThai) "ระยะเวลาทั้งหมด" else "Total duration",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        durationMinutes?.let {
                            if (isThai) "${it / 60} ชม. ${it % 60} นาที" else "${it / 60}h ${it % 60}m"
                        } ?: "—",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Primary
                    )
                }
            }

            // --- AI note (Stitch auto_awesome card) ---
            Surface(shape = RoundedCornerShape(14.dp), color = Secondary.copy(alpha = 0.10f), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Filled.AutoAwesome, null, tint = Secondary, modifier = Modifier.size(20.dp))
                    Text(
                        if (isThai)
                            "การบันทึกข้อมูลย้อนหลังที่ถูกต้อง จะช่วยให้ AI แนะนำตารางนอนในวันถัดไปได้แม่นยำยิ่งขึ้น"
                        else
                            "Accurate back-filled records help the AI suggest a more precise schedule for tomorrow.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            formError?.let { Text(it, color = Error, style = MaterialTheme.typography.bodySmall) }

            // --- Save (bottom button, per design) ---
            Button(
                onClick = {
                    val startMin = toMinutes(startTime)
                    val endMin = toMinutes(endTime)
                    if (startMin == null || endMin == null || durationMinutes == null) {
                        formError = if (isThai) "กรุณากรอกเวลาให้ถูกต้อง (HH:MM)" else "Enter a valid time (HH:MM)"
                        return@Button
                    }
                    if (state.child == null) {
                        formError = if (isThai) "ยังไม่ได้เลือกข้อมูลลูก" else "No child selected"
                        return@Button
                    }
                    // A night ending no later than it began has crossed midnight onto the next day.
                    val endDate = if (endMin <= startMin) recordDate.plusDays(1) else recordDate
                    childViewModel.addManualSleepLog(
                        sleepType,
                        "${recordDate}T$startTime:00",
                        "${endDate}T$endTime:00"
                    )
                    onBack()
                },
                enabled = !state.sleepSaving,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary)
            ) {
                if (state.sleepSaving) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.Save, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (isThai) "บันทึกประวัติการนอน" else "Save sleep history", fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun SleepTypeCard(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) Primary.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surfaceVariant,
        border = if (selected) androidx.compose.foundation.BorderStroke(2.dp, Primary) else null,
        modifier = modifier
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, null, tint = if (selected) Primary else Outline, modifier = Modifier.size(26.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (selected) Primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TimeField(
    icon: ImageVector,
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = { Icon(icon, null, tint = Primary, modifier = Modifier.size(18.dp)) },
        placeholder = { Text("HH:MM") },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier
    )
}
