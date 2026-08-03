@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.doctor

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.navigation.NavController
import com.lullabyai.app.data.model.Child
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.DoctorPatientDirectoryViewModel
import com.lullabyai.app.viewmodel.PatientDirectoryRow
import com.lullabyai.app.viewmodel.PatientVisitKind

private enum class DirectoryFilter { ALL, ALLERGY }

/** Doctor's patient directory — every child the doctor has ever had an appointment with, with a
 *  name search and a treating/follow-up status badge. Mirrors Stitch "Patient Directory". */
@Composable
fun DoctorPatientDirectoryScreen(navController: NavController) {
    val s = LocalStrings.current
    val vm: DoctorPatientDirectoryViewModel = viewModel()
    val state by vm.uiState.collectAsState()
    LaunchedEffect(Unit) { vm.load() }

    LifecycleResumeEffect(Unit) {
        vm.load()
        onPauseOrDispose { }
    }

    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(DirectoryFilter.ALL) }

    val filtered = remember(state.rows, query, filter) {
        state.rows.filter { row ->
            val child = row.child
            val matchesQuery = query.isBlank() || child.name.contains(query, ignoreCase = true)
            val matchesFilter = filter == DirectoryFilter.ALL || (filter == DirectoryFilter.ALLERGY && !child.allergies.isNullOrBlank())
            matchesQuery && matchesFilter
        }
    }

    Scaffold(
        topBar = {
            com.lullabyai.app.ui.components.LullabyTopBar(
                title = if (s.isThai) "รายชื่อคนไข้" else "Patient Directory",
                onBack = { navController.popBackStack() },
                navController = navController,
                centerTitle = true,
                isDoctor = true
            )
        },
        bottomBar = { DoctorBottomNav(navController, DoctorTab.PATIENTS) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = query, onValueChange = { query = it }, modifier = Modifier.weight(2f),
                        placeholder = { Text(if (s.isThai) "ค้นหาชื่อคนไข้..." else "Search patient name...") },
                        leadingIcon = { Icon(Icons.Filled.Search, null, tint = Outline) },
                        singleLine = true, shape = RoundedCornerShape(100.dp)
                    )
                    Card(modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.padding(10.dp)) {
                            Text(if (s.isThai) "คนไข้ทั้งหมด" else "Total", style = MaterialTheme.typography.labelSmall, color = Outline)
                            Text("${state.rows.size}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Primary)
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = filter == DirectoryFilter.ALL, onClick = { filter = DirectoryFilter.ALL }, label = { Text(if (s.isThai) "คนไข้ทั้งหมด" else "All") })
                    FilterChip(selected = filter == DirectoryFilter.ALLERGY, onClick = { filter = DirectoryFilter.ALLERGY }, label = { Text(if (s.isThai) "ภูมิแพ้" else "Allergies") })
                }
            }
            if (state.isLoading) {
                item { Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Primary) } }
            } else if (filtered.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.Groups, null, tint = Outline, modifier = Modifier.size(40.dp))
                        Text(if (s.isThai) "ยังไม่มีคนไข้" else "No patients yet", color = Outline)
                    }
                }
            } else {
                items(filtered, key = { it.child.id }) { row ->
                    PatientDirectoryCard(row, s.isThai) {
                        navController.navigate(Screen.DoctorPatientInfo.route(row.child.id))
                    }
                }
            }
        }
    }
}

@Composable
private fun PatientDirectoryCard(row: PatientDirectoryRow, isThai: Boolean, onClick: () -> Unit) {
    val child = row.child
    val (badgeColor, badgeText) = when (row.visitKind) {
        PatientVisitKind.UPCOMING -> HealthAttention to (if (isThai) "มีนัดพบ" else "Upcoming visit")
        PatientVisitKind.NEW -> Primary to (if (isThai) "ผู้ป่วยใหม่" else "New patient")
        PatientVisitKind.RETURNING -> Color(0xFFF57C00) to (if (isThai) "ผู้ป่วยเก่า" else "Returning")
    }
    val ageMonths = runCatching { com.lullabyai.app.data.VaccineSetupCatalog.ageMonthsFromBirthdate(child.birthdate) }.getOrDefault(0)
    val ageLabel = if (isThai) "${ageMonths / 12} ปี ${ageMonths % 12} เดือน" else "${ageMonths / 12}y ${ageMonths % 12}m"

    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(52.dp).clip(CircleShape).background(SurfaceContainerHigh), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.ChildCare, null, tint = Primary)
            }
            Column(Modifier.weight(1f)) {
                Text(child.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text("$ageLabel • ${if (child.gender == "male") (if (isThai) "ชาย" else "Male") else if (child.gender == "female") (if (isThai) "หญิง" else "Female") else child.gender}", style = MaterialTheme.typography.labelSmall, color = Outline)
                row.hn?.takeIf { it.isNotBlank() }?.let {
                    Text("HN: $it", style = MaterialTheme.typography.labelSmall, color = Primary, fontWeight = FontWeight.Medium)
                }
                row.nextAppointmentAt?.let {
                    Text(
                        (if (isThai) "นัด: " else "Appt: ") + com.lullabyai.app.ui.screens.doctorconnect.thaiDateTimeLabel(it, isThai).orEmpty(),
                        style = MaterialTheme.typography.labelSmall,
                        color = HealthAttention
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Surface(shape = RoundedCornerShape(100.dp), color = badgeColor.copy(0.12f)) {
                    Text(badgeText, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = badgeColor)
                }
                Icon(Icons.Filled.ChevronRight, null, tint = Outline, modifier = Modifier.size(18.dp))
            }
        }
    }
}
