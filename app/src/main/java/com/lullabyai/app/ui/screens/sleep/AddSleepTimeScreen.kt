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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.ChildViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun AddSleepTimeScreen(
    childViewModel: ChildViewModel,
    onBack: () -> Unit
) {
    val s = LocalStrings.current
    val state by childViewModel.uiState.collectAsState()
    val today = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) }

    var sleepType by remember { mutableStateOf("main") }
    var startTime by remember { mutableStateOf("20:00") }
    var endTime by remember { mutableStateOf("06:00") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (s.isThai) "เพิ่มเวลาการนอน" else "Add Sleep Time", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null, tint = Primary) } },
                actions = {
                    TextButton(
                        onClick = {
                            val startIso = "${today}T$startTime:00"
                            // Naps/night sleep that end the next morning cross midnight — advance the end date.
                            val endDate = if (endTime < startTime) {
                                val cal = Calendar.getInstance().apply {
                                    time = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(today)!!
                                    add(Calendar.DAY_OF_MONTH, 1)
                                }
                                SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
                            } else today
                            val endIso = "${endDate}T$endTime:00"
                            childViewModel.addManualSleepLog(sleepType, startIso, endIso)
                            onBack()
                        },
                        enabled = !state.sleepSaving
                    ) {
                        if (state.sleepSaving) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Primary)
                        else Text(if (s.isThai) "บันทึก" else "Save", color = Primary, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            Text(if (s.isThai) "ประเภทการนอน" else "Sleep Type", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SleepTypeButton(Icons.Filled.Bedtime, if (s.isThai) "เวลานอนหลัก" else "Main bedtime", sleepType == "main", Modifier.weight(1f)) { sleepType = "main" }
                SleepTypeButton(Icons.Filled.WbSunny, if (s.isThai) "นอนกลางวัน" else "Nap", sleepType == "nap", Modifier.weight(1f)) { sleepType = "nap" }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TimeField(if (s.isThai) "เวลาเริ่ม" else "Start", startTime, Modifier.weight(1f)) { startTime = it }
                TimeField(if (s.isThai) "เวลาจบ" else "End", endTime, Modifier.weight(1f)) { endTime = it }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SleepTypeButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick, modifier = modifier, shape = RoundedCornerShape(14.dp),
        color = if (selected) Primary else MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) Primary else MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(vertical = 16.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, null, tint = if (selected) Color.White else Outline, modifier = Modifier.size(22.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun TimeField(label: String, value: String, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = value, onValueChange = onChange, modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("HH:mm", color = Outline) }, singleLine = true, shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary, unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant)
        )
    }
}
