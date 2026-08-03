@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.doctor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.lullabyai.app.data.model.Appointment
import com.lullabyai.app.data.model.Child
import com.lullabyai.app.data.settings.SettingsManager
import com.lullabyai.app.ui.components.LocalAvatarUrl
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.DoctorDashboardViewModel
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime

/** Doctor home — Stitch 45.Doctor-Home: header, hero, recent patients, active queue, timeline, management menu. */
@Composable
fun DoctorDashboardScreen(navController: NavController) {
    val s = LocalStrings.current
    val vm: DoctorDashboardViewModel = viewModel()
    val state by vm.uiState.collectAsState()
    LaunchedEffect(Unit) { vm.load() }

    val doctorName = state.profile?.fullName?.takeIf { it.isNotBlank() } ?: (if (s.isThai) "คุณหมอ" else "Doctor")
    val specialty = state.doctorProfile?.specialty
    val nextAppointment = state.todayAppointments.firstOrNull { it.status != "completed" && it.status != "cancelled" }
    val restToday = state.todayAppointments.filter { it.id != nextAppointment?.id && it.status != "cancelled" }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showScheduleSetup by remember { mutableStateOf(false) }
    LaunchedEffect(state.needsInitialScheduleSetup, state.isLoading) {
        if (!state.isLoading && state.needsInitialScheduleSetup) showScheduleSetup = true
        if (!state.isLoading && state.scheduleConfigured) showScheduleSetup = false
    }
    if (showScheduleSetup) {
        AlertDialog(
            onDismissRequest = {
                showScheduleSetup = false
                scope.launch { SettingsManager(context).setDoctorScheduleNudgeDismissed(true) }
            },
            title = { Text(if (s.isThai) "ตั้งค่าเวลาคลินิก" else "Set clinic schedule") },
            text = {
                Text(
                    if (s.isThai) "กรุณาตั้งค่าเวลาทำงานและช่วงเวลานัดหมายก่อนรับคำขอจองคิวจากผู้ปกครอง"
                    else "Configure working hours and appointment slots before parents can book."
                )
            },
            confirmButton = {
                Button(onClick = {
                    showScheduleSetup = false
                    scope.launch { SettingsManager(context).setDoctorScheduleNudgeDismissed(true) }
                    navController.navigate(Screen.DoctorTimeAppointmentHub.route)
                }) { Text(if (s.isThai) "ไปตั้งค่า" else "Set up now") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showScheduleSetup = false
                    scope.launch { SettingsManager(context).setDoctorScheduleNudgeDismissed(true) }
                }) { Text(if (s.isThai) "ภายหลัง" else "Later") }
            }
        )
    }

    Scaffold(
        topBar = {
            DoctorHomeHeader(
                doctorName = doctorName,
                specialty = specialty,
                hasNotifications = state.pendingRegistrationCount > 0,
                onNotifications = {
                    navController.navigate(Screen.DoctorPatientRegistrationApproval.route)
                }
            )
        },
        bottomBar = { DoctorBottomNav(navController, DoctorTab.HOME) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp)
        ) {
            item {
                DoctorHomeHero(
                    doctorName = doctorName,
                    appointmentCount = state.todayAppointments.size,
                    isThai = s.isThai,
                    onClick = { navController.navigate(Screen.DoctorPatientSchedule.route) }
                )
            }

            if (state.pendingRegistrationCount > 0) {
                item {
                    Card(
                        onClick = { navController.navigate(Screen.DoctorPatientRegistrationApproval.route) },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = ErrorContainer.copy(alpha = 0.55f)),
                        elevation = CardDefaults.cardElevation(0.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Filled.PendingActions, null, tint = Error, modifier = Modifier.size(28.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    if (s.isThai) "มีคำขอจองคิวรออนุมัติ ${state.pendingRegistrationCount} รายการ"
                                    else "${state.pendingRegistrationCount} booking request(s) awaiting approval",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Error
                                )
                            }
                            Icon(Icons.Filled.ChevronRight, null, tint = Error)
                        }
                    }
                }
            }

            item {
                RecentPatientsSection(
                    isThai = s.isThai,
                    recentIds = state.recentPatientIds,
                    childrenById = state.childrenById,
                    urgentChildIds = state.todayAppointments
                        .filter { it.status == "pending" }
                        .map { it.childId }
                        .toSet(),
                    onViewAll = { navController.navigate(Screen.DoctorPatientDirectory.route) },
                    onPatient = { navController.navigate(Screen.DoctorPatientInfo.route(it)) }
                )
            }

            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.weight(1f)) {
                        ActiveQueueCard(
                            isThai = s.isThai,
                            next = nextAppointment,
                            child = nextAppointment?.let { state.childrenById[it.childId] },
                            onOpen = {
                                nextAppointment?.id?.let { id ->
                                    navController.navigate(Screen.DoctorClinicalRecordEntry.route(id))
                                } ?: navController.navigate(Screen.DoctorPatientSchedule.route)
                            }
                        )
                    }
                }
            }

            item {
                AfternoonTimelineCard(
                    isThai = s.isThai,
                    appointments = restToday,
                    childrenById = state.childrenById,
                    onViewAll = { navController.navigate(Screen.DoctorPatientSchedule.route) }
                )
            }

            item {
                ManagementMenuSection(
                    isThai = s.isThai,
                    onRegistration = { navController.navigate(Screen.DoctorPatientRegistrationApproval.route) },
                    onSchedule = { navController.navigate(Screen.DoctorTimeAppointmentHub.route) },
                    onAppointments = { navController.navigate(Screen.DoctorPatientSchedule.route) }
                )
            }
        }
    }
}

@Composable
private fun DoctorHomeHeader(
    doctorName: String,
    specialty: String?,
    hasNotifications: Boolean,
    onNotifications: () -> Unit
) {
    val avatarUrl = LocalAvatarUrl.current
    Surface(color = MaterialTheme.colorScheme.surface.copy(0.92f), shadowElevation = 1.dp) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box {
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(Primary.copy(0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!avatarUrl.isNullOrBlank()) {
                            coil.compose.AsyncImage(
                                model = avatarUrl,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize().clip(CircleShape),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                            )
                        } else {
                            Icon(Icons.Filled.Person, null, tint = Primary, modifier = Modifier.size(22.dp))
                        }
                    }
                    Box(
                        Modifier.align(Alignment.BottomEnd).size(10.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(HealthGood))
                    }
                }
                Column {
                    Text(doctorName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Primary)
                    specialty?.takeIf { it.isNotBlank() }?.let {
                        Text(it.uppercase(), style = MaterialTheme.typography.labelSmall, color = Outline)
                    }
                }
            }
            IconButton(onClick = onNotifications) {
                BadgedBox(
                    badge = {
                        if (hasNotifications) {
                            Badge(containerColor = Error, modifier = Modifier.size(8.dp))
                        }
                    }
                ) {
                    Icon(Icons.Filled.Notifications, null, tint = Outline, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}

@Composable
private fun DoctorHomeHero(
    doctorName: String,
    appointmentCount: Int,
    isThai: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = Primary,
        shadowElevation = 4.dp
    ) {
        Box(Modifier.fillMaxWidth()) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 32.dp, y = (-32).dp)
                    .size(192.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(0.1f))
            )
            Row(
                Modifier.fillMaxWidth().padding(24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(shape = RoundedCornerShape(100.dp), color = Color.White.copy(0.2f)) {
                        Text(
                            "Dashboard",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        if (isThai) "สวัสดีตอนเช้า, $doctorName" else "Good morning, $doctorName",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        if (isThai) "วันนี้คุณมีนัดหมายทั้งหมด $appointmentCount รายการ"
                        else "You have $appointmentCount appointments today",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(0.7f)
                    )
                }
                Icon(Icons.Filled.ArrowForward, null, tint = Color.White.copy(0.5f))
            }
        }
    }
}

@Composable
private fun RecentPatientsSection(
    isThai: Boolean,
    recentIds: List<String>,
    childrenById: Map<String, Child>,
    urgentChildIds: Set<String>,
    onViewAll: () -> Unit,
    onPatient: (String) -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (isThai) "รายชื่อเด็กล่าสุด" else "Recent patients",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = onViewAll) {
                    Text(if (isThai) "ดูทั้งหมด" else "View all", color = Primary, fontWeight = FontWeight.Bold)
                    Icon(Icons.Filled.ArrowForward, null, modifier = Modifier.size(14.dp), tint = Primary)
                }
            }
            if (recentIds.isEmpty()) {
                Text(if (isThai) "ยังไม่มีคนไข้" else "No patients yet", color = Outline, style = MaterialTheme.typography.bodySmall)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    recentIds.chunked(2).forEach { rowIds ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            rowIds.forEach { id ->
                                val child = childrenById[id]
                                if (child != null) {
                                    RecentPatientCard(
                                        modifier = Modifier.weight(1f),
                                        child = child,
                                        isUrgent = id in urgentChildIds,
                                        isThai = isThai,
                                        onClick = { onPatient(id) }
                                    )
                                } else {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                            if (rowIds.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentPatientCard(
    modifier: Modifier = Modifier,
    child: Child,
    isUrgent: Boolean,
    isThai: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, OutlineVariant.copy(0.25f))
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box {
                Box(
                    Modifier.size(48.dp).clip(CircleShape).background(SecondaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.ChildCare, null, tint = OnSecondaryContainer, modifier = Modifier.size(24.dp))
                }
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(if (isUrgent) Error else HealthGood)
                        .border(2.dp, Color.White, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isUrgent) Icons.Filled.PriorityHigh else Icons.Filled.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(10.dp)
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                Text(child.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    buildString {
                        append("HN ${clinicalHn(child.id)}")
                        dashboardAgeLine(child.birthdate, isThai)?.let { append(" • $it") }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = Outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(Icons.Filled.ChevronRight, null, tint = Outline, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun ActiveQueueCard(
    isThai: Boolean,
    next: Appointment?,
    child: Child?,
    onOpen: () -> Unit
) {
    Card(
        onClick = onOpen,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Primary)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Active Queue",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(0.7f),
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (isThai) "คิวต่อไป" else "Next in queue",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                next?.let { appt ->
                    queueEtaBadge(appt.scheduledAt, isThai)?.let { eta ->
                        Surface(shape = RoundedCornerShape(100.dp), color = Color.White.copy(0.2f)) {
                            Text(
                                eta,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (next == null || child == null) {
                    Text(
                        if (isThai) "ไม่มีนัดหมายถัดไปวันนี้" else "No upcoming appointment today",
                        color = Outline,
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    val time = com.lullabyai.app.util.TimeUtils.localTime(next.scheduledAt) ?: "-"
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Box(
                            Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(InversePrimary.copy(0.35f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.ChildCare, null, tint = Primary)
                        }
                        Column {
                            Text("${child.name} ($time)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            next.reason?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = Outline, maxLines = 2)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AfternoonTimelineCard(
    isThai: Boolean,
    appointments: List<Appointment>,
    childrenById: Map<String, Child>,
    onViewAll: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (isThai) "นัดหมายที่เหลือวันนี้" else "Rest of today",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Icon(Icons.Filled.CalendarToday, null, tint = Outline, modifier = Modifier.size(20.dp))
            }
            if (appointments.isEmpty()) {
                Text(
                    if (isThai) "ไม่มีนัดหมายเพิ่มเติม" else "Nothing else scheduled",
                    color = Outline,
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                appointments.forEach { appt ->
                    val child = childrenById[appt.childId]
                    val time = com.lullabyai.app.util.TimeUtils.localTime(appt.scheduledAt) ?: "-"
                    val isVaccine = appt.reason?.contains("วัคซี", ignoreCase = true) == true ||
                        appt.notes?.contains("vaccine", ignoreCase = true) == true
                    TimelineRow(
                        time = time,
                        name = child?.name ?: "-",
                        subtitle = appt.reason ?: appt.notes ?: "",
                        tag = if (isVaccine) (if (isThai) "วัคซีน" else "Vaccination") else (if (isThai) "ทั่วไป" else "Regular"),
                        isVaccine = isVaccine
                    )
                }
            }
            OutlinedButton(onClick = onViewAll, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Text(if (isThai) "ดูตารางทั้งหมด" else "View full schedule", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun TimelineRow(
    time: String,
    name: String,
    subtitle: String,
    tag: String,
    isVaccine: Boolean
) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(if (isVaccine) InversePrimary.copy(0.45f) else SecondaryContainer)
                .border(2.dp, Color.White, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (isVaccine) Icons.Filled.Vaccines else Icons.Filled.Event,
                null,
                tint = Primary,
                modifier = Modifier.size(16.dp)
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(time, style = MaterialTheme.typography.labelSmall, color = Primary, fontWeight = FontWeight.Bold)
                Surface(shape = RoundedCornerShape(6.dp), color = SurfaceContainer) {
                    Text(
                        tag.uppercase(),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = Outline
                    )
                }
            }
            Text(name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
            if (subtitle.isNotBlank()) {
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = Outline, maxLines = 2)
            }
        }
    }
}

@Composable
private fun ManagementMenuSection(
    isThai: Boolean,
    onRegistration: () -> Unit,
    onSchedule: () -> Unit,
    onAppointments: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                if (isThai) "เมนูการจัดการ" else "Management",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            ManagementRow(
                icon = Icons.Filled.HowToReg,
                title = if (isThai) "ระบบลงทะเบียน" else "Registration",
                onClick = onRegistration
            )
            ManagementRow(
                icon = Icons.Filled.Schedule,
                title = if (isThai) "จัดการเวลาของสถานพยาบาล" else "Clinic schedule",
                onClick = onSchedule
            )
            ManagementRow(
                icon = Icons.Filled.Event,
                title = if (isThai) "นัดหมาย" else "Appointments",
                onClick = onAppointments
            )
        }
    }
}

@Composable
private fun ManagementRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, OutlineVariant.copy(0.25f))
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(InversePrimary.copy(0.35f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = Primary, modifier = Modifier.size(20.dp))
            }
            Text(title, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
            Icon(Icons.Filled.ChevronRight, null, tint = Outline)
        }
    }
}

private fun dashboardAgeLine(birthdate: String?, isThai: Boolean): String? = birthdate?.takeIf { it.isNotBlank() }?.let { bd ->
    runCatching {
        val m = com.lullabyai.app.data.VaccineSetupCatalog.ageMonthsFromBirthdate(bd)
        if (isThai) "${m / 12} ปี ${m % 12} เดือน" else "${m / 12}y ${m % 12}m"
    }.getOrNull()
}

private fun queueEtaBadge(scheduledAt: String?, isThai: Boolean): String? {
    val dt = scheduledAt?.let { com.lullabyai.app.util.TimeUtils.parseInstant(it) } ?: return null
    val apptLocal = LocalDateTime.ofInstant(dt, java.time.ZoneId.systemDefault())
    val now = LocalDateTime.now()
    if (apptLocal.isBefore(now)) return null
    val mins = Duration.between(now, apptLocal).toMinutes()
    return when {
        mins <= 0 -> null
        mins < 60 -> if (isThai) "ในอีก $mins นาที" else "In $mins min"
        else -> if (isThai) "ในอีก ${mins / 60} ชม." else "In ${mins / 60} hr"
    }
}
