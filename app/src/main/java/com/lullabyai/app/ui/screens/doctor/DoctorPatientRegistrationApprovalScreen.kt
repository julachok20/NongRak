@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.doctor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.navigation.NavController
import com.lullabyai.app.data.VaccineSetupCatalog
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.screens.doctorconnect.thaiDateTimeLabel
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.DoctorPatientRegistrationApprovalViewModel
import com.lullabyai.app.viewmodel.PendingBookingRow

private enum class ApprovalFilter { ALL, URGENT, AWAITING_DOCS }

/** Stitch: "Patient Registration Approval (Redesign)" — pending booking requests at this clinic. */
@Composable
fun DoctorPatientRegistrationApprovalScreen(navController: NavController) {
    val s = LocalStrings.current
    val vm: DoctorPatientRegistrationApprovalViewModel = viewModel()
    val state by vm.uiState.collectAsState()
    LaunchedEffect(Unit) { vm.load() }

    LifecycleResumeEffect(Unit) {
        vm.load()
        onPauseOrDispose { }
    }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(ApprovalFilter.ALL) }
    var rejectTargetId by remember { mutableStateOf<String?>(null) }

    val enriched = remember(state.pendingBookings) {
        state.pendingBookings.map { row -> row to row.isUrgentBooking() }
    }
    val urgentCount = enriched.count { it.second }

    val filtered = remember(enriched, query, filter) {
        enriched.filter { (row, urgent) ->
            val matchesQuery = query.isBlank() ||
                row.patientName?.contains(query, true) == true ||
                row.displayHn().contains(query, true) ||
                row.appointment.reason?.contains(query, true) == true
            val matchesFilter = when (filter) {
                ApprovalFilter.ALL -> true
                ApprovalFilter.URGENT -> urgent
                ApprovalFilter.AWAITING_DOCS -> row.appointment.reason.isNullOrBlank()
            }
            matchesQuery && matchesFilter
        }
            // ฉุกเฉิน bookings jump the queue; everything else stays first-come, first-served.
            .sortedWith(
                compareByDescending<Pair<PendingBookingRow, Boolean>> { it.first.isEmergencyBooking() }
                    .thenBy { it.first.appointment.createdAt ?: "" }
            )
            .map { it.first }
    }

    Scaffold(
        topBar = {
            com.lullabyai.app.ui.components.LullabyTopBar(
                title = if (s.isThai) "จัดการการลงทะเบียน" else "Registration requests",
                onBack = { navController.popBackStack() },
                navController = navController,
                centerTitle = false,
                isDoctor = true
            )
        },
        bottomBar = { DoctorBottomNav(navController, DoctorTab.PATIENTS) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp)
        ) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SummaryStatCard(
                        modifier = Modifier.weight(1f),
                        label = if (s.isThai) "คำร้องที่รอดำเนินการทั้งหมด" else "All pending",
                        value = "${state.pendingBookings.size}",
                        suffix = if (s.isThai) "รายการ" else "items",
                        icon = Icons.Filled.PendingActions,
                        iconTint = Primary,
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                    SummaryStatCard(
                        modifier = Modifier.weight(1f),
                        label = if (s.isThai) "กรณีเร่งด่วน" else "Urgent",
                        value = "$urgentCount",
                        suffix = if (s.isThai) "เคส" else "cases",
                        icon = Icons.Filled.MedicalInformation,
                        iconTint = Error,
                        containerColor = ErrorContainer.copy(alpha = 0.45f)
                    )
                }
            }
            item {
                // Walk-in patients never send a request, so the doctor needs a way to add them here.
                Button(
                    onClick = { navController.navigate(Screen.DoctorPatientRegistrationForm.route) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.PersonAdd, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (s.isThai) "เพิ่มผู้ป่วยเอง (Walk-in)" else "Add patient manually (walk-in)",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(if (s.isThai) "ค้นหาชื่อผู้ป่วย หรือ HN..." else "Search patient or HN…") },
                    leadingIcon = { Icon(Icons.Filled.Search, null, tint = Outline) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
            }
            item {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = filter == ApprovalFilter.ALL,
                        onClick = { filter = ApprovalFilter.ALL },
                        label = { Text(if (s.isThai) "ทั้งหมด" else "All") },
                        shape = RoundedCornerShape(100.dp)
                    )
                    FilterChip(
                        selected = filter == ApprovalFilter.URGENT,
                        onClick = { filter = ApprovalFilter.URGENT },
                        label = { Text(if (s.isThai) "เร่งด่วน" else "Urgent") },
                        shape = RoundedCornerShape(100.dp)
                    )
                    FilterChip(
                        selected = filter == ApprovalFilter.AWAITING_DOCS,
                        onClick = { filter = ApprovalFilter.AWAITING_DOCS },
                        label = { Text(if (s.isThai) "รอเอกสาร" else "Awaiting docs") },
                        shape = RoundedCornerShape(100.dp)
                    )
                }
            }
            state.error?.let { err ->
                item { Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
            if (state.isLoading) {
                item { Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Primary) } }
            } else if (filtered.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.PendingActions, null, tint = Outline, modifier = Modifier.size(40.dp))
                        Text(if (s.isThai) "ไม่มีคำขอจองคิวที่รอดำเนินการ" else "No pending booking requests", color = Outline)
                    }
                }
            } else {
                items(filtered, key = { it.appointment.id }) { row ->
                    StitchBookingRequestCard(
                        row = row,
                        isThai = s.isThai,
                        urgent = row.isUrgentBooking(),
                        onOpen = { navController.navigate(Screen.DoctorPatientRegistrationDetail.route(row.appointment.id)) },
                        onApprove = { vm.approve(row.appointment.id) },
                        onReject = { rejectTargetId = row.appointment.id }
                    )
                }
            }
        }
    }

    rejectTargetId?.let { id ->
        DoctorBookingRejectDialog(
            isThai = s.isThai,
            onDismiss = { rejectTargetId = null },
            onConfirm = { reason ->
                vm.reject(id, reason)
                rejectTargetId = null
            }
        )
    }
}

@Composable
private fun SummaryStatCard(
    modifier: Modifier,
    label: String,
    value: String,
    suffix: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    containerColor: Color
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = androidx.compose.foundation.BorderStroke(1.dp, OutlineVariant.copy(alpha = 0.35f))
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(label, style = MaterialTheme.typography.labelMedium, color = Outline)
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(value, fontSize = 36.sp, fontWeight = FontWeight.Bold, color = iconTint)
                    Text(suffix, style = MaterialTheme.typography.bodyMedium, color = Outline, modifier = Modifier.padding(bottom = 4.dp))
                }
            }
            Box(Modifier.size(52.dp).clip(RoundedCornerShape(100.dp)).background(iconTint.copy(0.08f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = iconTint, modifier = Modifier.size(32.dp))
            }
        }
    }
}

@Composable
private fun StitchBookingRequestCard(
    row: PendingBookingRow,
    isThai: Boolean,
    urgent: Boolean,
    onOpen: () -> Unit,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    val appt = row.appointment
    val borderMod = if (urgent) {
        Modifier.border(width = 4.dp, color = Error, shape = RoundedCornerShape(12.dp))
    } else {
        Modifier.border(1.dp, OutlineVariant.copy(0.35f), RoundedCornerShape(12.dp))
    }
    Card(
        onClick = onOpen,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = borderMod
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
                    Box(
                        Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(SurfaceContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.ChildCare, null, tint = Primary, modifier = Modifier.size(28.dp))
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                row.patientName?.takeIf { it.isNotBlank() } ?: (if (isThai) "ผู้ป่วย" else "Patient"),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (urgent) {
                                Surface(shape = RoundedCornerShape(4.dp), color = ErrorContainer) {
                                    Row(
                                        Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Icon(Icons.Filled.PriorityHigh, null, tint = Error, modifier = Modifier.size(12.dp))
                                        Text(if (isThai) "เร่งด่วน" else "Urgent", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Error)
                                    }
                                }
                            }
                        }
                        Text(
                            buildString {
                                append("HN: ${row.displayHn()}")
                                row.ageGenderLine(isThai)?.let { append("  |  $it") }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = Outline
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        if (isThai) "วันที่ส่งคำขอ" else "Submitted",
                        fontSize = 10.sp,
                        color = Outline,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        thaiDateTimeLabel(appt.createdAt, isThai)
                            ?: com.lullabyai.app.util.TimeUtils.localDate(appt.createdAt)
                            ?: "-",
                        style = MaterialTheme.typography.labelMedium,
                        color = Outline
                    )
                }
            }
            val reasonBg = if (urgent) ErrorContainer.copy(0.35f) else SurfaceContainerLow
            Surface(shape = RoundedCornerShape(8.dp), color = reasonBg, border = if (urgent) androidx.compose.foundation.BorderStroke(1.dp, Error.copy(0.15f)) else null) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(
                            if (urgent) Icons.Filled.Report else Icons.Filled.MedicalInformation,
                            null,
                            tint = if (urgent) Error else Primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            if (isThai) "อาการ/เหตุผลการลงทะเบียน" else "Reason for visit",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (urgent) Error else Primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        appt.reason?.takeIf { it.isNotBlank() }
                            ?: (if (isThai) "—" else "—"),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            HorizontalDivider(color = OutlineVariant.copy(0.25f))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.Person, null, tint = Outline, modifier = Modifier.size(20.dp))
                    Text(
                        row.guardianDisplay(isThai),
                        style = MaterialTheme.typography.bodySmall,
                        color = Outline,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onReject,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (isThai) "ไม่อนุมัติ" else "Reject", maxLines = 1)
                    }
                    Button(
                        onClick = onApprove,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = HealthGood)
                    ) {
                        Text(if (isThai) "อนุมัติ" else "Approve", maxLines = 1)
                    }
                }
            }
        }
    }
}

private fun PendingBookingRow.displayHn(): String {
    val raw = appointment.childId.replace("-", "").take(8).uppercase()
    return if (raw.length >= 5) "${raw.take(2)}-${raw.drop(2).take(5)}" else raw
}

private fun PendingBookingRow.ageGenderLine(isThai: Boolean): String? {
    val agePart = birthdate?.let { bd ->
        runCatching {
            val m = VaccineSetupCatalog.ageMonthsFromBirthdate(bd)
            if (isThai) "อายุ ${m / 12} ปี ${m % 12} เดือน" else "${m / 12}y ${m % 12}m"
        }.getOrNull()
    }
    val genderPart = gender?.let { g ->
        when (g.lowercase()) {
            "female", "f" -> if (isThai) "หญิง" else "Female"
            "male", "m" -> if (isThai) "ชาย" else "Male"
            else -> null
        }
    }
    return when {
        agePart != null && genderPart != null -> "$agePart  |  $genderPart"
        agePart != null -> agePart
        genderPart != null -> genderPart
        else -> null
    }
}

private fun PendingBookingRow.guardianDisplay(isThai: Boolean): String {
    val name = guardianName?.takeIf { it.isNotBlank() } ?: return if (isThai) "ผู้ปกครอง" else "Guardian"
    val rel = guardianRelationship?.takeIf { it.isNotBlank() }
    return if (rel != null) "$name ($rel)" else name
}

/** True when the parent picked ฉุกเฉิน on the booking screen — the only explicit urgency signal. */
private fun PendingBookingRow.isEmergencyBooking(): Boolean =
    com.lullabyai.app.util.AppointmentBookingMeta.isEmergency(appointment.notes) ||
        com.lullabyai.app.util.AppointmentBookingMeta.parseType(appointment.reason, appointment.notes) ==
        com.lullabyai.app.util.AppointmentBookingType.EMERGENCY

/** Explicit ฉุกเฉิน flag first, then the keyword heuristic for free-text reasons. */
private fun PendingBookingRow.isUrgentBooking(): Boolean {
    if (isEmergencyBooking()) return true
    val text = (appointment.reason.orEmpty() + " " + appointment.notes.orEmpty()).lowercase()
    val keywords = listOf(
        "เร่งด่วน", "หายใจ", "แพ้", "ลมพิษ", "ไข้สูง", "urgent", "allerg", "breath", "anaphyl",
        "38.", "39.", "40."
    )
    return keywords.any { text.contains(it) }
}

@Composable
internal fun DoctorBookingRejectDialog(
    isThai: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var reason by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isThai) "ไม่อนุมัติคำขอจอง" else "Reject booking request") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    if (isThai) "กรุณาระบุเหตุผลที่ไม่อนุมัติ (จำเป็น) — ผู้ปกครองจะเห็นข้อความนี้"
                    else "Please enter a reason (required). The parent will see this message.",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (isThai) "เหตุผล" else "Reason") },
                    minLines = 2
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(reason) },
                enabled = reason.isNotBlank()
            ) {
                Text(if (isThai) "ยืนยันไม่อนุมัติ" else "Confirm reject", color = Error, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (isThai) "ยกเลิก" else "Cancel")
            }
        }
    )
}
