@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.doctorconnect

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.lullabyai.app.data.model.Appointment
import com.lullabyai.app.data.repository.PediaConnectRepository
import com.lullabyai.app.data.settings.ChildSelection
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.HealthAttention
import com.lullabyai.app.ui.theme.HealthGood
import com.lullabyai.app.ui.theme.Outline
import com.lullabyai.app.ui.theme.Primary
import com.lullabyai.app.viewmodel.ChildViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * "คุณหมอของฉัน" — the Pedia Connect hub for one parent, in Stitch's four bands:
 *  1. header + search
 *  2. "เพิ่มคุณหมอท่านใหม่" shortcut into [Screen.SearchDoctor]
 *  3. **Recently Visited** ("คุณหมอที่เคยปรึกษา") — a horizontal rail ordered by last visit
 *  4. "ติดตามการจองคิว" booking tracker, then the "รายชื่อคุณหมอ (n)" board
 *
 * The board's CTAs follow [DoctorBoardEntry.cta]: pending/confirmed upcoming -> **ดูรายละเอียด**;
 * ended + doctor follow-up date -> **ยืนยันนัดหมาย / แก้ไข/ยกเลิก** and **ดูผลตรวจ** (within 5 days of last visit).
 */
@Composable
fun MyDoctorsHubScreen(childViewModel: ChildViewModel, navController: NavController, onBack: () -> Unit) {
    val s = LocalStrings.current
    val vm: MyDoctorsBoardViewModel = viewModel()
    val childState by childViewModel.uiState.collectAsState()
    val ui by vm.ui.collectAsState()
    var query by remember { mutableStateOf("") }
    val pediaRepo = remember { PediaConnectRepository() }
    val selectedChildId by ChildSelection.id.collectAsState()
    val activeChild = remember(selectedChildId, childState.children) {
        selectedChildId?.let { id -> childState.children.firstOrNull { it.id == id } }
    }
    var pediaRegistered by remember { mutableStateOf<Boolean?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    /** Hub lists doctors/bookings for the single child chosen on [PediaConnectDashboardScreen]. */
    fun refreshHubBoard(force: Boolean) {
        val childId = selectedChildId ?: return
        vm.load(listOf(childId), force = force)
    }

    LaunchedEffect(selectedChildId) {
        val id = selectedChildId
        if (id.isNullOrBlank()) {
            pediaRegistered = null
            snackbarHostState.showSnackbar(pediaConnectMissingChildMessage(s.isThai))
            return@LaunchedEffect
        }
        pediaRegistered = withContext(Dispatchers.IO) {
            pediaRepo.hasRegistrationForChild(id).getOrDefault(false)
        }
        refreshHubBoard(force = false)
    }

    // Re-fetch board when returning from Search Doctor / Doctor Reservation / appointment details
    // (e.g. after a new booking) so pending bookings and doctor rows stay current.
    LifecycleResumeEffect(selectedChildId) {
        selectedChildId?.let { id ->
            scope.launch {
                pediaRegistered = withContext(Dispatchers.IO) {
                    pediaRepo.hasRegistrationForChild(id).getOrDefault(false)
                }
            }
            refreshHubBoard(force = true)
        }
        onPauseOrDispose { }
    }

    val filtered = remember(ui.entries, query) {
        if (query.isBlank()) ui.entries
        else ui.entries.filter { e ->
            (e.profile.fullName ?: "").contains(query, ignoreCase = true) ||
                (e.doctorProfile.specialty ?: "").contains(query, ignoreCase = true) ||
                (e.doctorProfile.clinicName ?: "").contains(query, ignoreCase = true)
        }
    }

    fun openClinicFlow(doctorId: String) {
        scope.launch {
            val childId = selectedChildId
            if (childId.isNullOrBlank()) {
                snackbarHostState.showSnackbar(pediaConnectMissingChildMessage(s.isThai))
                return@launch
            }
            val gate = withContext(Dispatchers.IO) { resolveClinicBookingGate(doctorId, childId) }
            clinicGateSnackbarMessage(gate, s.isThai)?.let { snackbarHostState.showSnackbar(it) }
            navController.navigateForClinicGate(doctorId, gate)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            com.lullabyai.app.ui.components.LullabyTopBar(
                title = "Pedia Connect",
                onBack = onBack,
                navController = navController
            )
        },
        bottomBar = { ConnectBottomNav() },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        androidx.compose.material3.pulltorefresh.PullToRefreshBox(
            isRefreshing = ui.loading,
            onRefresh = {
                selectedChildId?.let { id ->
                    scope.launch {
                        pediaRegistered = withContext(Dispatchers.IO) {
                            pediaRepo.hasRegistrationForChild(id).getOrDefault(false)
                        }
                    }
                    refreshHubBoard(force = true)
                }
            },
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        if (s.isThai) "คุณหมอของฉัน" else "My Doctors",
                        style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (s.isThai) "จัดการนัดหมายและปรึกษาแพทย์ที่คุณไว้วางใจ"
                        else "Manage appointments and consult the doctors you trust",
                        style = MaterialTheme.typography.bodyMedium, color = Outline
                    )
                    activeChild?.let { child ->
                        HubSelectedChildChip(child, s.isThai)
                    }
                }
            }

            if (selectedChildId.isNullOrBlank()) {
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            pediaConnectMissingChildMessage(s.isThai),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Button(
                            onClick = { navController.navigate(Screen.PediaConnectDashboard.route) },
                            shape = RoundedCornerShape(100.dp)
                        ) {
                            Text(if (s.isThai) "ไปเลือกลูกที่ Pedia Connect" else "Choose child on Pedia Connect")
                        }
                    }
                }
            } else if (pediaRegistered == false) {
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            if (s.isThai) "ยังไม่มีข้อมูล Pedia Connect สำหรับลูกคนที่เลือก"
                            else "No Pedia Connect information for the selected child.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Outline
                        )
                        Button(
                            onClick = { navController.navigate(Screen.PatientRegistration.route(PATIENT_REGISTRATION_NO_DOCTOR)) },
                            shape = RoundedCornerShape(100.dp)
                        ) {
                            Text(if (s.isThai) "ลงทะเบียน Pedia Connect" else "Register for Pedia Connect")
                        }
                    }
                }
            } else if (pediaRegistered == null && !selectedChildId.isNullOrBlank()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    }
                }
            } else {

            item {
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    placeholder = {
                        Text(
                            if (s.isThai) "ค้นหารายชื่อคุณหมอหรือความเชี่ยวชาญ..." else "Search doctors or specialties...",
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    leadingIcon = { Icon(Icons.Filled.Search, null, tint = Outline) },
                    singleLine = true,
                    shape = RoundedCornerShape(100.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // 2. Add a new doctor
            item {
                Card(
                    onClick = { navController.navigate(Screen.SearchDoctor.route) },
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Primary.copy(alpha = 0.07f)),
                    elevation = CardDefaults.cardElevation(0.dp)
                ) {
                    Row(
                        Modifier.padding(16.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                if (s.isThai) "เพิ่มคุณหมอท่านใหม่" else "Add a new doctor",
                                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Primary
                            )
                            Text(
                                if (s.isThai) "ค้นหาและเพิ่มแพทย์จากเครือข่ายของเรา"
                                else "Search and add a doctor from our network",
                                style = MaterialTheme.typography.bodySmall, color = Outline
                            )
                            Button(
                                onClick = { navController.navigate(Screen.SearchDoctor.route) },
                                shape = RoundedCornerShape(100.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Icon(Icons.Filled.AddCircle, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if (s.isThai) "ค้นหาคุณหมอ" else "Search doctors", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        Icon(Icons.Filled.MedicalServices, null, tint = Primary.copy(alpha = 0.35f), modifier = Modifier.size(56.dp))
                    }
                }
            }

            // 3. Recently visited
            if (ui.recentlyVisited.isNotEmpty()) {
                item { ConnectSectionHeader(if (s.isThai) "คุณหมอที่เคยปรึกษา" else "Recently visited") }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(ui.recentlyVisited, key = { it.profile.id }) { entry ->
                            RecentlyVisitedCard(
                                name = doctorDisplayName(entry.profile, s.isThai),
                                specialty = entry.doctorProfile.specialty,
                                photoUrl = entry.doctorProfile.clinicPhotoUrl,
                                lastSeen = thaiDateTimeLabel(
                                    entry.lastSeenAt?.toString(), s.isThai, withTime = false
                                ),
                                actionLabel = if (s.isThai) "จองคิวอีกครั้ง" else "Book again",
                                onAction = { openClinicFlow(entry.profile.id) }
                            )
                        }
                    }
                }
            }

            // 4a. Booking tracker — pending vs confirmed (Stitch 42.Hub)
            val pendingBookings = ui.bookings.filter { it.status == "pending" }
            val confirmedBookings = ui.bookings.filter { it.status == "confirmed" }
            if (pendingBookings.isNotEmpty()) {
                item { ConnectSectionHeader(if (s.isThai) "ติดตามการจองคิว · รอยืนยัน" else "Booking tracker · awaiting confirmation") }
                items(pendingBookings, key = { "p-${it.id}" }) { appt ->
                    BookingCard(
                        appointment = appt,
                        doctorName = ui.bookingDoctorNames[appt.doctorId],
                        isThai = s.isThai,
                        onClick = { navController.navigate(Screen.AppointmentDetails.route(appt.id)) }
                    )
                }
            }
            if (confirmedBookings.isNotEmpty()) {
                item { ConnectSectionHeader(if (s.isThai) "ติดตามการจองคิว · ยืนยันแล้ว" else "Booking tracker · confirmed") }
                items(confirmedBookings, key = { "c-${it.id}" }) { appt ->
                    BookingCard(
                        appointment = appt,
                        doctorName = ui.bookingDoctorNames[appt.doctorId],
                        isThai = s.isThai,
                        onClick = { navController.navigate(Screen.AppointmentDetails.route(appt.id)) }
                    )
                }
            }

            val cancelledBookings = ui.bookings.filter { it.status == "cancelled" }
            if (cancelledBookings.isNotEmpty()) {
                item { ConnectSectionHeader(if (s.isThai) "ติดตามการจองคิว · ยกเลิกแล้ว" else "Booking tracker · cancelled") }
                items(cancelledBookings, key = { "x-${it.id}" }) { appt ->
                    BookingCard(
                        appointment = appt,
                        doctorName = ui.bookingDoctorNames[appt.doctorId],
                        isThai = s.isThai,
                        onClick = { navController.navigate(Screen.AppointmentDetails.route(appt.id)) }
                    )
                }
            }

            // 4b. Doctor board
            item {
                ConnectSectionHeader(
                    (if (s.isThai) "รายชื่อคุณหมอ" else "Doctor list") + " (${filtered.size})"
                )
            }

            if (ui.loading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
            } else if (filtered.isEmpty()) {
                item {
                    Text(
                        if (query.isNotBlank()) {
                            if (s.isThai) "ไม่พบคุณหมอที่ตรงกับคำค้นหา" else "No doctors match your search."
                        } else {
                            if (s.isThai) "ยังไม่มีคุณหมอที่เคยพบ ลองค้นหาคุณหมอเพื่อเริ่มการนัดหมาย"
                            else "No doctors yet — search to book your first appointment."
                        },
                        style = MaterialTheme.typography.bodyMedium, color = Outline,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
            } else {
                items(filtered, key = { it.profile.id }) { entry ->
                    DoctorBoardCard(
                        entry = entry,
                        isThai = s.isThai,
                        onDetails = {
                            entry.appointment?.let { navController.navigate(Screen.AppointmentDetails.route(it.id)) }
                                ?: openClinicFlow(entry.profile.id)
                        },
                        onConfirm = { openClinicFlow(entry.profile.id) },
                        onManage = {
                            // Raw route string: Screen.kt is owned by the orchestrator, which registers
                            // this destination separately (see appointmentManagementRoute).
                            entry.appointment?.let { navController.navigate(appointmentManagementRoute(it.id)) }
                        },
                        onLabResults = { navController.navigate(Screen.LabResults.route) }
                    )
                }
            }
            }
        }
        }
    }
}

private fun pediaConnectMissingChildMessage(isThai: Boolean): String =
    if (isThai) "กรุณากลับไปเลือกลูกที่หน้า Pedia Connect"
    else "Go back to Pedia Connect and select a child."

@Composable
private fun HubSelectedChildChip(child: com.lullabyai.app.data.model.Child, isThai: Boolean) {
    Surface(
        shape = RoundedCornerShape(100.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.padding(top = 4.dp)
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DoctorAvatar(child.avatarUrl, size = 26.dp)
            Text(
                child.name.ifBlank { if (isThai) "ไม่ระบุชื่อ" else "Unnamed" },
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun RecentlyVisitedCard(
    name: String,
    specialty: String?,
    photoUrl: String?,
    lastSeen: String?,
    actionLabel: String,
    onAction: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp),
        modifier = Modifier.width(160.dp)
    ) {
        Column(
            Modifier.padding(14.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            DoctorAvatar(photoUrl, size = 54.dp)
            Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1)
            specialty?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = Primary, maxLines = 1)
            }
            lastSeen?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = Outline, maxLines = 1) }
            Button(
                onClick = onAction, shape = RoundedCornerShape(100.dp), modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 6.dp)
            ) { Text(actionLabel, style = MaterialTheme.typography.labelSmall) }
        }
    }
}

@Composable
private fun BookingCard(appointment: Appointment, doctorName: String?, isThai: Boolean, onClick: () -> Unit) {
    val confirmed = appointment.status == "confirmed"
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(
            Modifier.padding(14.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    doctorName?.takeIf { it.isNotBlank() } ?: (if (isThai) "ไม่ระบุชื่อแพทย์" else "Unnamed doctor"),
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold
                )
                Text(
                    thaiDateTimeLabel(appointment.scheduledAt, isThai)
                        ?: (if (isThai) "รอกำหนดเวลา" else "Awaiting scheduling"),
                    style = MaterialTheme.typography.bodySmall, color = Outline
                )
            }
            if (appointment.status == "cancelled") {
                StatusPill(
                    if (isThai) "ยกเลิกแล้ว" else "Cancelled",
                    com.lullabyai.app.ui.theme.Error,
                    Icons.Filled.Cancel
                )
            } else if (confirmed) {
                StatusPill(if (isThai) "ยืนยันแล้ว" else "Confirmed", HealthGood, Icons.Filled.CheckCircle)
            } else if (appointment.status == "completed") {
                StatusPill(if (isThai) "เสร็จสิ้น" else "Completed", HealthGood, Icons.Filled.CheckCircle)
            } else {
                StatusPill(if (isThai) "รอการยืนยัน" else "Pending", HealthAttention, Icons.Filled.Schedule)
            }
        }
    }
}

@Composable
private fun DoctorBoardCard(
    entry: DoctorBoardEntry,
    isThai: Boolean,
    onDetails: () -> Unit,
    onConfirm: () -> Unit,
    onManage: () -> Unit,
    onLabResults: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(Modifier.padding(14.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DoctorAvatar(entry.doctorProfile.clinicPhotoUrl, size = 52.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        doctorDisplayName(entry.profile, isThai),
                        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1
                    )
                    entry.doctorProfile.specialty?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = Primary, maxLines = 1)
                    }
                    entry.doctorProfile.clinicName?.takeIf { it.isNotBlank() }?.let {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Filled.LocationOn, null, tint = Outline, modifier = Modifier.size(13.dp))
                            Text(it, style = MaterialTheme.typography.labelSmall, color = Outline, maxLines = 1)
                        }
                    }
                }
                if (entry.isHappeningNow) {
                    StatusPill(if (isThai) "กำลังเข้าพบ" else "In progress", Primary, filled = true)
                } else if (entry.isUpcomingPending) {
                    StatusPill(if (isThai) "รอคุณหมออนุมัติ" else "Awaiting approval", HealthAttention, Icons.Filled.Schedule)
                } else if (entry.isVisitCompleted) {
                    StatusPill(if (isThai) "เสร็จสิ้น" else "Completed", HealthGood, Icons.Filled.CheckCircle)
                }
            }

            if (entry.isUpcomingPending) {
                Text(
                    if (isThai) "คำขอจองคิวของคุณรอการอนุมัติจากคุณหมอ"
                    else "Your booking request is waiting for the doctor to approve.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Outline
                )
            }

            // Date row: "วันที่นัดหมาย" normally, "นัดหมายครั้งถัดไป" once a follow-up is needed.
            val dateLabel = if (entry.showsNextDate) {
                if (isThai) "นัดหมายครั้งถัดไป" else "Next appointment"
            } else {
                if (isThai) "วันที่นัดหมาย" else "Appointment date"
            }
            val dateValue = if (entry.showsNextDate) {
                thaiDateTimeLabel(entry.nextAppointmentDate, isThai, withTime = false)
            } else {
                thaiDateTimeLabel(entry.appointment?.scheduledAt, isThai)
            }
            if (dateValue != null) {
                Row(
                    Modifier.fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Filled.CalendarToday, null, tint = Primary, modifier = Modifier.size(18.dp))
                    Column {
                        Text(dateLabel, style = MaterialTheme.typography.labelSmall, color = Outline)
                        Text(dateValue, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            when {
                entry.isVisitCompleted && entry.showLabResultsOnHub -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onLabResults, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(100.dp)
                    ) { Text(if (isThai) "ดูผลตรวจ" else "Lab results", fontWeight = FontWeight.Bold) }
                    OutlinedButton(
                        onClick = onDetails, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(100.dp)
                    ) { Text(if (isThai) "ดูรายละเอียด" else "View details", fontWeight = FontWeight.Bold) }
                }
                // Confirming must not strip the escape hatch: an already-confirmed appointment keeps
                // แก้ไข/ยกเลิก until it is completed or already cancelled.
                entry.cta == ApptCta.DETAILS -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onDetails, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(100.dp)
                    ) { Text(if (isThai) "ดูรายละเอียด" else "View details", fontWeight = FontWeight.Bold) }
                    if (entry.appointment?.status in listOf("pending", "confirmed")) {
                        OutlinedButton(
                            onClick = onManage, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(100.dp)
                        ) { Text(if (isThai) "แก้ไข/ยกเลิก" else "Edit / cancel", fontWeight = FontWeight.Bold) }
                    }
                }

                entry.cta == ApptCta.FOLLOW_UP -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onConfirm, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(100.dp)
                    ) { Text(if (isThai) "ยืนยันนัดหมาย" else "Confirm appointment", fontWeight = FontWeight.Bold) }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = onManage, modifier = Modifier.weight(1f), shape = RoundedCornerShape(100.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                        ) { Text(if (isThai) "แก้ไข/ยกเลิก" else "Edit / cancel", style = MaterialTheme.typography.labelMedium) }
                        if (entry.showLabResultsOnHub) {
                            OutlinedButton(
                                onClick = onLabResults, modifier = Modifier.weight(1f), shape = RoundedCornerShape(100.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                            ) { Text(if (isThai) "ดูผลตรวจ" else "Lab results", style = MaterialTheme.typography.labelMedium) }
                        }
                    }
                }
            }
        }
    }
}
