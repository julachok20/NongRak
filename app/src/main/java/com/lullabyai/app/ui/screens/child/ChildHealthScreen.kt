@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.child

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.ChildViewModel

/** "ศูนย์สุขภาพ" (Child Health Hub) — per the real "Child Health Hub" Stitch mockup (re-checked
 *  2026-07-26; the mockup was updated since the earlier audit), this is a lightweight navigation
 *  hub: compact profile row, a due-medicine banner, and a 6-tile grid: Vaccine Book / Sickness
 *  History / Doctor Visits / Medication History / Allergy History / Chronic conditions. Waste
 *  Record and Medical Documents are no longer tiles here (still reachable via Quick Action
 *  Search, and Medical Documents also from the AI Summary screen) — the mockup dropped Waste
 *  Record and swapped the old Medical Documents tile for Chronic conditions. The Chronic tile
 *  routes to the existing ChronicRegistryScreen (full CRUD + prevention tips + emergency call)
 *  instead of the old inline add/delete card, which duplicated a subset of that same screen. */
@Composable
fun ChildHealthScreen(
    childViewModel: ChildViewModel,
    chatViewModel: com.lullabyai.app.viewmodel.ChatViewModel,
    navController: NavController,
    onBack: () -> Unit
) {
    val s = com.lullabyai.app.ui.localization.LocalStrings.current
    val childState by childViewModel.uiState.collectAsState()
    val child = childState.child
    val allergies = childState.allergies
    val medicationDoses = childState.medicationDoses
    val schedules = childState.medicationSchedules.associateBy { it.id }
    // No permanent dismissal by design — reappears every time this screen loads until an allergy
    // record exists, since Health/Disease Risk AI both read allergy history (buildChildContext).
    var showMissingInfo by remember { mutableStateOf(true) }
    val missingFields = remember(child, allergies) {
        if (child != null && allergies.isEmpty()) listOf(if (s.isThai) "ประวัติการแพ้" else "Allergy history") else emptyList()
    }

    LaunchedEffect(child?.id) {
        childViewModel.loadMedications()
        childViewModel.loadMedicalDocuments()
    }

    // Earliest actionable dose (due now, within 15 min either side of scheduled time).
    val nextDose = medicationDoses.filter { it.status == "pending" }
        .mapNotNull { d -> com.lullabyai.app.util.TimeUtils.parseInstant(d.scheduledAt)?.let { d to it } }
        .minByOrNull { it.second }
    val nextDoseWaiting = nextDose?.let { java.time.Duration.between(java.time.Instant.now(), it.second).toMinutes() > 15 } ?: false

    ChildScreenScaffold(title = if (s.isThai) "ศูนย์สุขภาพ" else "Health Hub", currentTab = "health", navController = navController) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            // Compact profile row
            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
                    Row(Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Box(modifier = Modifier.size(56.dp).clip(CircleShape).background(SecondaryContainer), contentAlignment = Alignment.Center) {
                            val avatar = childState.childAvatarSignedUrl
                            if (!avatar.isNullOrBlank()) AsyncImage(model = avatar, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            else Text("🙂", fontSize = 26.sp)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(child?.name ?: "", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text((if (s.isThai) "อายุ " else "Age ") + childViewModel.calculateAge(s.isThai), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        VerticalDivider(modifier = Modifier.height(36.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        ProfileStat(if (s.isThai) "น้ำหนัก" else "Weight", child?.weightKg?.let { "$it" } ?: "-", if (s.isThai) "kg" else "kg")
                        ProfileStat(if (s.isThai) "ความสูง" else "Height", child?.heightCm?.let { "$it" } ?: "-", if (s.isThai) "cm" else "cm")
                    }
                }
            }

            // Due-medicine banner
            if (nextDose != null) {
                item {
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.4f))) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(Icons.Filled.NotificationsActive, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                                Text(if (s.isThai) "แจ้งเตือนการทานยา" else "Medication reminder", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            }
                            val schedule = schedules[nextDose.first.scheduleId]
                            val dosageText = schedule?.let { "${it.dosage ?: ""} ${it.dosageUnit}".trim() } ?: ""
                            Text(
                                "${schedule?.medicineName ?: ""} - ${com.lullabyai.app.util.TimeUtils.localTime(nextDose.first.scheduledAt) ?: ""}",
                                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold
                            )
                            if (dosageText.isNotBlank()) {
                                Text(dosageText, style = MaterialTheme.typography.bodySmall, color = Primary)
                            }
                            Button(
                                onClick = { childViewModel.markDoseTaken(nextDose.first.id) },
                                enabled = !nextDoseWaiting,
                                modifier = Modifier.fillMaxWidth().height(44.dp), shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Primary)
                            ) {
                                Text(
                                    if (nextDoseWaiting) (if (s.isThai) "รอถึงเวลา" else "Waiting") else (if (s.isThai) "ยืนยัน" else "Confirm"),
                                    color = Color.White, fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // 6-tile navigation grid, in design order.
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    HealthTile(if (s.isThai) "สมุดวัคซีน" else "Vaccine Book", Icons.Filled.MedicalServices, Modifier.weight(1f)) { navController.navigate(Screen.VaccineBook.route) }
                    HealthTile(if (s.isThai) "ประวัติการเจ็บป่วย" else "Sickness History", Icons.Filled.MonitorHeart, Modifier.weight(1f)) { navController.navigate(Screen.HealthHistory.route) }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    HealthTile(if (s.isThai) "บันทึกการหาหมอ" else "Doctor Visits", Icons.Filled.LocalHospital, Modifier.weight(1f)) { navController.navigate(Screen.DoctorVisitHistory.route) }
                    HealthTile(if (s.isThai) "ประวัติการใช้ยา" else "Medication History", Icons.Filled.Medication, Modifier.weight(1f)) { navController.navigate(Screen.MedicationHistory.route) }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    HealthTile(if (s.isThai) "ประวัติภูมิแพ้" else "Allergy History", Icons.Filled.Warning, Modifier.weight(1f)) { navController.navigate(Screen.AllergyHistory.route) }
                    // Design swapped this tile from "Medical Documents" to "Chronic conditions"
                    // (โรคประจำตัว) — routes to the existing ChronicRegistryScreen, which already
                    // does full CRUD + prevention tips + emergency call for the same data the old
                    // inline card here only partially covered (name + delete, no prevention field).
                    HealthTile(if (s.isThai) "โรคประจำตัว" else "Chronic conditions", Icons.Filled.HealthAndSafety, Modifier.weight(1f)) { navController.navigate(Screen.ChronicRegistry.route) }
                }
            }
        }
    }

    if (showMissingInfo) {
        com.lullabyai.app.ui.components.MissingChildInfoPrompt(
            missingFields = missingFields, isThai = s.isThai,
            onFillIn = { showMissingInfo = false; navController.navigate(Screen.AllergyHistory.route) },
            onSkip = { showMissingInfo = false }
        )
    }
}

@Composable
private fun ProfileStat(label: String, value: String, unit: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(" $unit", style = MaterialTheme.typography.labelSmall, color = Outline)
        }
    }
}

@Composable
private fun HealthTile(label: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(vertical = 20.dp, horizontal = 10.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(48.dp).clip(CircleShape).background(Primary.copy(0.1f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = Primary, modifier = Modifier.size(24.dp))
            }
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 2)
        }
    }
}
