@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.child

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.lullabyai.app.data.model.HealthRecord
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.ChildViewModel

/** "ประวัติการเจ็บป่วย" — picks real symptoms off the child's existing illness history
 *  (state.healthRecords: title = main symptom, additionalSymptoms = extra ones), grouped
 *  "กำลังป่วย" (still monitoring) vs older/recovered, and hands the flat selection back to
 *  RecordVisitScreen via ChildViewModel.setSelectedVisitSymptoms (same VM-state hand-off pattern
 *  the rest of this app uses for picker -> form flows, e.g. openHealthRecordId). */
@Composable
fun SymptomPickerScreen(childViewModel: ChildViewModel, navController: NavController, onBack: () -> Unit) {
    val s = LocalStrings.current
    val state by childViewModel.uiState.collectAsState()
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<String>()) }

    data class Group(val record: HealthRecord, val symptoms: List<String>)
    val allGroups = remember(state.healthRecords) {
        state.healthRecords.mapNotNull { r ->
            val syms = (listOf(r.title) + (r.additionalSymptoms ?: emptyList())).filter { it.isNotBlank() }.distinct()
            if (syms.isEmpty()) null else Group(r, syms)
        }
    }
    val filteredGroups = remember(allGroups, query) {
        if (query.isBlank()) allGroups
        else allGroups.mapNotNull { g ->
            val matched = g.symptoms.filter { it.contains(query, ignoreCase = true) }
            if (matched.isEmpty()) null else g.copy(symptoms = matched)
        }
    }
    val monitoring = filteredGroups.filter { it.record.status == "monitoring" }
    val past = filteredGroups.filter { it.record.status != "monitoring" }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (s.isThai) "ประวัติการเจ็บป่วย" else "Illness History", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null, tint = Primary) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = {
            Surface(shadowElevation = 6.dp, color = MaterialTheme.colorScheme.background) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        if (s.isThai) "เลือกแล้ว ${selected.size} รายการ" else "${selected.size} selected",
                        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold
                    )
                    Button(
                        onClick = { childViewModel.setSelectedVisitSymptoms(selected.toList()); onBack() },
                        enabled = selected.isNotEmpty(), shape = RoundedCornerShape(100.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Primary)
                    ) { Text(if (s.isThai) "ยืนยันการเลือก" else "Confirm selection", color = Color.White, fontWeight = FontWeight.Bold) }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            item {
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(if (s.isThai) "ค้นหาอาการ..." else "Search symptoms...", color = Outline) },
                    leadingIcon = { Icon(Icons.Filled.Search, null, tint = Outline) },
                    singleLine = true, shape = RoundedCornerShape(100.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary, unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant)
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(shape = RoundedCornerShape(100.dp), color = Color(0xFFED6C02).copy(0.12f)) {
                        Text(if (s.isThai) "กำลังป่วย ${monitoring.size}" else "Ongoing ${monitoring.size}", modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelSmall, color = Color(0xFFED6C02), fontWeight = FontWeight.Bold)
                    }
                    Surface(shape = RoundedCornerShape(100.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(0.5f)) {
                        Text(if (s.isThai) "ประวัติเก่า ${past.size}" else "Past ${past.size}", modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (filteredGroups.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                        Text(if (s.isThai) "ไม่พบประวัติการเจ็บป่วย" else "No illness history found", color = Outline)
                    }
                }
            } else {
                if (monitoring.isNotEmpty()) {
                    item { GroupHeader(if (s.isThai) "กำลังป่วย" else "Currently sick") }
                    items(monitoring, key = { "m_${it.record.id}" }) { g ->
                        RecordSymptomCard(g.record, g.symptoms, selected, s.isThai) { sym, isSel ->
                            selected = if (isSel) selected + sym else selected - sym
                        }
                    }
                }
                if (past.isNotEmpty()) {
                    item { GroupHeader(if (s.isThai) "ประวัติที่ผ่านมา" else "Past history") }
                    items(past, key = { "p_${it.record.id}" }) { g ->
                        RecordSymptomCard(g.record, g.symptoms, selected, s.isThai) { sym, isSel ->
                            selected = if (isSel) selected + sym else selected - sym
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun GroupHeader(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun RecordSymptomCard(record: HealthRecord, symptoms: List<String>, selected: Set<String>, isThai: Boolean, onToggle: (String, Boolean) -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(record.title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Text(com.lullabyai.app.util.TimeUtils.localDate(record.recordedAt) ?: "", style = MaterialTheme.typography.labelSmall, color = Outline)
            }
            symptoms.forEach { sym ->
                val isSel = sym in selected
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                        .background(if (isSel) Primary.copy(0.08f) else Color.Transparent)
                        .clickable { onToggle(sym, !isSel) }
                        .padding(vertical = 6.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = isSel, onCheckedChange = { onToggle(sym, it) }, colors = CheckboxDefaults.colors(checkedColor = Primary))
                    Text(sym, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
