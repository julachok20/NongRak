@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.child

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.ChildViewModel

private val SYMPTOM_KEYS = listOf("fever", "cough", "runny_nose", "rash", "lethargic", "vomiting", "diarrhea")
private fun symptomLabel(key: String, isThai: Boolean) = when (key) {
    "fever" -> if (isThai) "ตัวร้อน" else "Fever"
    "cough" -> if (isThai) "ไอ" else "Cough"
    "runny_nose" -> if (isThai) "มีน้ำมูก" else "Runny nose"
    "rash" -> if (isThai) "ผื่น" else "Rash"
    "lethargic" -> if (isThai) "ซึม" else "Lethargic"
    "vomiting" -> if (isThai) "อาเจียน" else "Vomiting"
    else -> if (isThai) "ท้องเสีย" else "Diarrhea"
}
private val SEVERITIES = listOf("none" to ("ไม่มี" to "None"), "mild" to ("เล็กน้อย" to "Mild"), "moderate" to ("ปานกลาง" to "Moderate"), "severe" to ("รุนแรง" to "Severe"))
private val FEELINGS = listOf("cold" to ("หนาว" to "Cold"), "normal" to ("ปกติ" to "Normal"), "hot" to ("ตัวร้อน" to "Hot"))
private val DAY_STATUSES = listOf("started" to ("เริ่มมีอาการ" to "Just started"), "active" to ("กำลังมีอาการ" to "Ongoing"), "recovered" to ("หายแล้ว" to "Recovered"))

/** "บันทึกอาการวันนี้" — one day-log entry under a sickness episode (a health_records row).
 *  Reuses the record's title/id from state.openHealthRecordId, set by whoever navigated here
 *  (HealthHistoryScreen / SicknessFollowUpScreen), same pattern as HealthDetailScreen. */
@Composable
fun LogSicknessSymptomsScreen(childViewModel: ChildViewModel, navController: NavController, onBack: () -> Unit) {
    val s = LocalStrings.current
    val state by childViewModel.uiState.collectAsState()
    val record = state.healthRecords.firstOrNull { it.id == state.openHealthRecordId }

    var severities by remember(record?.id) { mutableStateOf(mapOf<String, String>()) }
    var tempText by remember(record?.id) { mutableStateOf("") }
    var feeling by remember(record?.id) { mutableStateOf<String?>(null) }
    var dayStatus by remember(record?.id) { mutableStateOf("active") }
    var notes by remember(record?.id) { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (s.isThai) "บันทึกอาการวันนี้" else "Log Today's Symptoms", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null, tint = Primary) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            record?.let {
                Text(it.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            SectionCard(if (s.isThai) "อุณหภูมิ" else "Temperature") {
                OutlinedTextField(
                    value = tempText, onValueChange = { tempText = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    placeholder = { Text(if (s.isThai) "°C (ไม่บังคับ)" else "°C (optional)", color = Outline) },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                    shape = RoundedCornerShape(12.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary)
                )
                Spacer(Modifier.height(10.dp))
                Text(if (s.isThai) "หรือความรู้สึกตัว" else "Or how they feel", style = MaterialTheme.typography.labelSmall, color = Outline)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FEELINGS.forEach { (key, label) ->
                        ChoiceChip(if (s.isThai) label.first else label.second, feeling == key, Modifier.weight(1f)) {
                            feeling = if (feeling == key) null else key
                        }
                    }
                }
            }

            SectionCard(if (s.isThai) "อาการ" else "Symptoms") {
                SYMPTOM_KEYS.forEach { key ->
                    Column(Modifier.padding(vertical = 6.dp)) {
                        Text(symptomLabel(key, s.isThai), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            SEVERITIES.forEach { (sevKey, label) ->
                                val selected = (severities[key] ?: "none") == sevKey
                                ChoiceChip(if (s.isThai) label.first else label.second, selected, Modifier.weight(1f)) {
                                    severities = severities + (key to sevKey)
                                }
                            }
                        }
                    }
                }
            }

            SectionCard(if (s.isThai) "สถานะอาการปัจจุบัน" else "Current status") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    DAY_STATUSES.forEach { (key, label) ->
                        Row(
                            Modifier.fillMaxWidth().clickable { dayStatus = key },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = dayStatus == key, onClick = { dayStatus = key }, colors = RadioButtonDefaults.colors(selectedColor = Primary))
                            Text(if (s.isThai) label.first else label.second, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            SectionCard(if (s.isThai) "บันทึกเพิ่มเติม" else "Notes") {
                OutlinedTextField(
                    value = notes, onValueChange = { notes = it }, modifier = Modifier.fillMaxWidth().height(96.dp),
                    placeholder = { Text(if (s.isThai) "รายละเอียดเพิ่มเติม (ไม่บังคับ)" else "Additional details (optional)", color = Outline) },
                    shape = RoundedCornerShape(12.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary)
                )
            }

            Button(
                onClick = {
                    record?.let {
                        val symptomsToSave = severities.filterValues { v -> v != "none" }
                        childViewModel.addSymptomLog(it.id, symptomsToSave, tempText.toDoubleOrNull(), feeling, dayStatus, notes.ifBlank { null }) {
                            if (dayStatus == "recovered") childViewModel.toggleHealthRecordStatus(it.id)
                            onBack()
                        }
                    }
                },
                enabled = record != null && !state.symptomLogSaving,
                modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(100.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary)
            ) {
                if (state.symptomLogSaving) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = androidx.compose.ui.graphics.Color.White)
                else Text(if (s.isThai) "บันทึกข้อมูล" else "Save entry", fontWeight = FontWeight.Bold, color = androidx.compose.ui.graphics.Color.White)
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun ChoiceChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick, modifier = modifier, shape = RoundedCornerShape(10.dp),
        color = if (selected) Primary else MaterialTheme.colorScheme.surfaceVariant.copy(0.5f),
        border = if (selected) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Box(Modifier.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = if (selected) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}
