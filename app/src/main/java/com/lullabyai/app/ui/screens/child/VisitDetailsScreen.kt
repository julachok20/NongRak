@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.child

import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.lullabyai.app.data.model.MedicalDocument
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.util.VisitReasonTemplates
import com.lullabyai.app.viewmodel.ChildViewModel

/** "รายละเอียดการพบแพทย์" — read view of one doctor_visits row (state.openDoctorVisitId, set by
 *  DoctorVisitHistoryScreen before navigating here). "แก้ไข" hands off to RecordVisitScreen, which
 *  reads the same openDoctorVisitId to prefill. */
@Composable
fun VisitDetailsScreen(childViewModel: ChildViewModel, navController: NavController, onBack: () -> Unit) {
    val s = LocalStrings.current
    val state by childViewModel.uiState.collectAsState()
    val context = LocalContext.current
    val visit = state.doctorVisits.firstOrNull { it.id == state.openDoctorVisitId }

    LaunchedEffect(visit?.id) { visit?.let { childViewModel.loadVisitDocuments(it.id) } }

    Scaffold(
        topBar = {
            com.lullabyai.app.ui.components.LullabyTopBar(
                title = if (s.isThai) "รายละเอียดการพบแพทย์" else "Visit Details",
                onBack = onBack,
                actions = {
                    TextButton(onClick = { navController.navigate(Screen.RecordDoctorVisit.route) }, enabled = visit != null) {
                        Icon(Icons.Filled.Edit, null, tint = Primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (s.isThai) "แก้ไข" else "Edit", color = Primary, fontWeight = FontWeight.Bold)
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (visit == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(if (s.isThai) "ไม่พบข้อมูล" else "Visit not found", color = Outline)
            }
            return@Scaffold
        }

        val (badgeBg, badgeFg, badgeText) = when (visit.status) {
            "completed" -> Triple(Color(0xFFE8F5E9), Color(0xFF2E7D32), if (s.isThai) "เสร็จสิ้น" else "COMPLETED")
            "follow_up" -> Triple(Color(0xFFFFF3E0), Color(0xFFED6C02), if (s.isThai) "ติดตามผล" else "FOLLOW-UP")
            else -> Triple(Primary.copy(0.12f), Primary, if (s.isThai) "นัดหมาย" else "APPOINTMENT")
        }
        val dateText = com.lullabyai.app.util.TimeUtils.localDate(visit.visitDatetime) ?: ""
        val timeText = com.lullabyai.app.util.TimeUtils.localTime(visit.visitDatetime)

        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Filled.CalendarToday, null, tint = Outline, modifier = Modifier.size(16.dp))
                            Text(dateText + (timeText?.let { " • $it น." } ?: ""), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Surface(shape = RoundedCornerShape(100.dp), color = badgeBg) {
                            Text(badgeText, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = badgeFg)
                        }
                    }
                    visit.facilityName?.takeIf { it.isNotBlank() }?.let {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Filled.LocalHospital, null, tint = Outline, modifier = Modifier.size(16.dp))
                            Text(it, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Column {
                        Text(
                            visit.doctorName?.takeIf { it.isNotBlank() } ?: (if (s.isThai) "ไม่ระบุแพทย์" else "Doctor not specified"),
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold
                        )
                        visit.doctorSpecialty?.takeIf { it.isNotBlank() }?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = Outline)
                        }
                    }
                }
            }

            if (!visit.reason.isNullOrBlank() || !visit.diagnosis.isNullOrBlank()) {
                DetailSection(if (s.isThai) "เหตุผล / ผลการวินิจฉัย" else "Reason / Diagnosis") {
                    visit.reason?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    visit.diagnosis?.takeIf { it.isNotBlank() }?.let {
                        Spacer(Modifier.height(6.dp))
                        Text((if (s.isThai) "วินิจฉัย: " else "Diagnosis: ") + it, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            if (visit.weightKg != null || visit.heightCm != null || visit.temperatureC != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile(if (s.isThai) "น้ำหนัก" else "Weight", visit.weightKg?.let { "$it" } ?: "-", "kg", Modifier.weight(1f))
                    StatTile(if (s.isThai) "ส่วนสูง" else "Height", visit.heightCm?.let { "$it" } ?: "-", "cm", Modifier.weight(1f))
                    StatTile(if (s.isThai) "อุณหภูมิ" else "Temp", visit.temperatureC?.let { "$it" } ?: "-", "°C", Modifier.weight(1f))
                }
                visit.bloodPressure?.takeIf { it.isNotBlank() }?.let {
                    Text((if (s.isThai) "ความดันโลหิต: " else "Blood pressure: ") + it, style = MaterialTheme.typography.bodySmall, color = Outline)
                }
            }

            if (!visit.symptoms.isNullOrEmpty()) {
                val symptoms = visit.symptoms.orEmpty()
                DetailSection(if (s.isThai) "อาการ" else "Symptoms") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        symptoms.forEach { sym ->
                            Surface(shape = RoundedCornerShape(100.dp), color = Primary.copy(0.1f)) {
                                Text(sym, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = Primary)
                            }
                        }
                    }
                }
            }

            visit.treatmentPlan?.takeIf { it.isNotBlank() }?.let {
                DetailSection(if (s.isThai) "แผนการรักษาและยา" else "Treatment & medication") { Text(it, style = MaterialTheme.typography.bodyMedium) }
            }

            visit.doctorAdvice?.takeIf { it.isNotBlank() }?.let { adviceRaw ->
                val parsed = VisitReasonTemplates.parseVaccineAdvice(adviceRaw)
                if (parsed != null) {
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Tertiary.copy(0.10f))) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Filled.Vaccines, null, tint = Tertiary, modifier = Modifier.size(20.dp))
                                Text(
                                    if (s.isThai) "สรุปหลังฉีดวัคซีน" else "Post-vaccination summary",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Tertiary
                                )
                            }
                            parsed.vaccineName?.let { DetailLine(if (s.isThai) "วัคซีน" else "Vaccine", it) }
                            parsed.lot?.let { DetailLine(if (s.isThai) "Lot" else "Lot", it) }
                            parsed.site?.let { DetailLine(if (s.isThai) "ตำแหน่งฉีด" else "Site", it) }
                            parsed.reaction?.let { DetailLine(if (s.isThai) "อาการหลังฉีด" else "Reaction", it) }
                            parsed.advice?.let { DetailLine(if (s.isThai) "คำแนะนำ" else "Advice", it) }
                        }
                    }
                } else {
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Primary.copy(0.06f))) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Filled.TipsAndUpdates, null, tint = Primary, modifier = Modifier.size(18.dp))
                                Text(if (s.isThai) "คำแนะนำจากคุณหมอ" else "Doctor's advice", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Primary)
                            }
                            Text(adviceRaw, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            if (state.visitDocuments.isNotEmpty()) {
                DetailSection(if (s.isThai) "เอกสารแนบ" else "Attached documents") {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.height((((state.visitDocuments.size - 1) / 3 + 1) * 76).dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(state.visitDocuments, key = { it.id }) { doc -> DocThumb(doc, childViewModel, context) }
                    }
                }
            }

            visit.nextAppointmentDate?.takeIf { it.isNotBlank() }?.let { nextDate ->
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.EventRepeat, null, tint = Primary, modifier = Modifier.size(20.dp))
                            Text(if (s.isThai) "นัดหมายครั้งต่อไป" else "Next appointment", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            runCatching { formatDate(java.time.LocalDate.parse(nextDate), s.isThai) }.getOrDefault(nextDate),
                            style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = Primary
                        )
                        OutlinedButton(
                            onClick = {
                                val d = runCatching { java.time.LocalDate.parse(nextDate) }.getOrNull() ?: return@OutlinedButton
                                val startMillis = d.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                                val intent = Intent(Intent.ACTION_INSERT).apply {
                                    data = CalendarContract.Events.CONTENT_URI
                                    putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMillis)
                                    putExtra(CalendarContract.EXTRA_EVENT_END_TIME, startMillis + 60 * 60 * 1000)
                                    putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, true)
                                    putExtra(
                                        CalendarContract.Events.TITLE,
                                        (if (s.isThai) "นัดหมายแพทย์" else "Doctor appointment") + (visit.facilityName?.let { " - $it" } ?: "")
                                    )
                                }
                                runCatching { context.startActivity(intent) }
                            },
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(100.dp)
                        ) {
                            Icon(Icons.Filled.EditCalendar, null, tint = Primary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (s.isThai) "บันทึกลงปฏิทิน" else "Save to calendar", color = Primary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Outline)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun DetailSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, unit: String, modifier: Modifier = Modifier) {
    Card(modifier, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(vertical = 14.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = Outline)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Primary)
            Text(unit, style = MaterialTheme.typography.labelSmall, color = Outline)
        }
    }
}

@Composable
private fun DocThumb(doc: MedicalDocument, childViewModel: ChildViewModel, context: android.content.Context) {
    val isPdf = doc.mimeType?.contains("pdf") == true
    Box(
        Modifier.size(72.dp).clip(RoundedCornerShape(12.dp)).background((if (isPdf) Error else Primary).copy(0.1f))
            .clickable {
                childViewModel.signMedicalDocUrl(doc.filePath) { url ->
                    url ?: return@signMedicalDocUrl
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(if (isPdf) Icons.Filled.PictureAsPdf else Icons.Filled.Image, null, tint = if (isPdf) Error else Primary)
    }
}
