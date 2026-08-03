@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.doctor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.lullabyai.app.data.model.VaccineRecord
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.DoctorPatientVaccineHistoryViewModel

/** Read-only, doctor-side view of every vaccine dose a patient has already received. Mirrors
 *  Stitch "Patient Vaccine History (Doctor View)". */
@Composable
fun DoctorPatientVaccineHistoryScreen(childId: String, navController: NavController) {
    val s = LocalStrings.current
    val vm: DoctorPatientVaccineHistoryViewModel = viewModel()
    val state by vm.uiState.collectAsState()
    LaunchedEffect(childId) { vm.load(childId) }

    val ageMonths = state.child?.let { runCatching { com.lullabyai.app.data.VaccineSetupCatalog.ageMonthsFromBirthdate(it.birthdate) }.getOrDefault(0) } ?: 0

    Scaffold(
        topBar = {
            com.lullabyai.app.ui.components.LullabyTopBar(
                title = if (s.isThai) "ประวัติการรับวัคซีน" else "Vaccine History",
                onBack = { navController.popBackStack() },
                navController = navController,
                isDoctor = true
            )
        },
        bottomBar = { DoctorBottomNav(navController, DoctorTab.PATIENTS) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp)
        ) {
            item {
                Surface(shape = RoundedCornerShape(16.dp), color = Primary.copy(0.06f)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Box(Modifier.size(56.dp).clip(CircleShape).background(Primary.copy(0.12f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.ChildCare, null, tint = Primary)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(state.child?.name ?: "-", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Primary)
                            Text(
                                (if (s.isThai) "อายุ: ${ageMonths / 12} ปี ${ageMonths % 12} เดือน" else "Age: ${ageMonths / 12}y ${ageMonths % 12}m"),
                                style = MaterialTheme.typography.labelMedium, color = Outline
                            )
                        }
                    }
                }
            }
            item {
                Text(
                    if (s.isThai) "ข้อมูลจากสมุดวัคซีนผู้ปกครอง (ซิงค์จากแอป)" else "From parent vaccine book (synced from app)",
                    style = MaterialTheme.typography.labelMedium,
                    color = Outline
                )
            }
            if (state.upcomingVaccines.isNotEmpty()) {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(if (s.isThai) "วัคซีนที่รอฉีด" else "Upcoming doses", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Primary)
                        Text("${state.upcomingVaccines.size}", color = HealthAttention, fontWeight = FontWeight.Bold)
                    }
                }
                items(state.upcomingVaccines, key = { "u-${it.id}" }) { v ->
                    UpcomingVaccineRow(v, s.isThai)
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(if (s.isThai) "ประวัติการรับวัคซีน" else "Vaccines received", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Primary)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Filled.CheckCircle, null, tint = Tertiary, modifier = Modifier.size(18.dp))
                        Text("${state.completedVaccines.size}", color = Tertiary, fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (state.isLoading) {
                item { Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Primary) } }
            } else if (state.completedVaccines.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.Vaccines, null, tint = Outline, modifier = Modifier.size(40.dp))
                        Text(if (s.isThai) "ยังไม่มีวัคซีนที่ได้รับ" else "No vaccines recorded yet", color = Outline)
                    }
                }
            } else {
                items(state.completedVaccines, key = { it.id }) { v ->
                    VaccineHistoryRow(
                        v,
                        s.isThai,
                        sending = state.sendingId == v.id,
                        onSendVerification = { vm.sendVerificationToParent(v, s.isThai) }
                    )
                }
            }
            state.sentMessage?.let {
                item {
                    Text(it, color = HealthGood, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
            }
            state.error?.let {
                item { Text(it, color = Error, style = MaterialTheme.typography.labelMedium) }
            }
        }
    }
}

@Composable
private fun UpcomingVaccineRow(v: VaccineRecord, isThai: Boolean) {
    Surface(shape = RoundedCornerShape(14.dp), color = HealthAttention.copy(0.08f), shadowElevation = 1.dp) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(HealthAttention.copy(0.12f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Schedule, null, tint = HealthAttention)
            }
            Column(Modifier.weight(1f)) {
                Text(v.vaccineName, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                v.scheduledDate?.takeIf { it.isNotBlank() }?.let { raw ->
                    val shown = com.lullabyai.app.ui.screens.doctorconnect.thaiDateTimeLabel(raw, isThai, withTime = false) ?: raw
                    Text((if (isThai) "เป้าหมาย: $shown" else "Target: $shown"), style = MaterialTheme.typography.bodySmall, color = Outline)
                }
                v.place?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = Outline)
                }
            }
            Surface(shape = RoundedCornerShape(100.dp), color = HealthAttention.copy(0.18f)) {
                Text(
                    if (isThai) "รอนัดหมาย" else "Scheduled",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = HealthAttention
                )
            }
        }
    }
}

@Composable
private fun VaccineHistoryRow(
    v: VaccineRecord,
    isThai: Boolean,
    sending: Boolean,
    onSendVerification: () -> Unit
) {
    val hasCert = !v.certificateUrl.isNullOrBlank()
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Tertiary.copy(0.12f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Vaccines, null, tint = Tertiary)
            }
            Column(Modifier.weight(1f)) {
                Text(v.vaccineName, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Text((if (isThai) "วันที่ฉีด: " else "Date given: ") + (v.dateGiven ?: "-"), style = MaterialTheme.typography.bodySmall, color = Outline)
                if (hasCert) {
                    Text(
                        if (isThai) "มีใบรับรองแล้ว" else "Certificate on file",
                        style = MaterialTheme.typography.labelSmall,
                        color = HealthGood,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            if (!hasCert) {
                TextButton(onClick = onSendVerification, enabled = !sending) {
                    if (sending) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text(
                        if (isThai) "ส่งใบรับรอง" else "Send cert",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            Icon(Icons.Filled.Verified, null, tint = if (hasCert) HealthGood else Tertiary)
        }
    }
}
