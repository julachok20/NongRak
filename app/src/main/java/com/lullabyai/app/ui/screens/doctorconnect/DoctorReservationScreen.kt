@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)
package com.lullabyai.app.ui.screens.doctorconnect

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.lullabyai.app.data.model.Child
import com.lullabyai.app.data.settings.ChildSelection
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.data.model.ClinicBookingGate
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.Outline
import com.lullabyai.app.ui.theme.Primary
import com.lullabyai.app.data.repository.DoctorRepository
import com.lullabyai.app.viewmodel.ChildViewModel
import com.lullabyai.app.viewmodel.DoctorReservationViewModel
import com.lullabyai.app.util.AppointmentBookingMeta
import com.lullabyai.app.util.AppointmentBookingType

/** "จองคิวคุณหมอ" — doctor profile + real open slots (doctor_appointment_slots), the child chosen
 *  on [PediaConnectDashboardScreen] (read-only here), and a reason field. Booking calls
 *  DoctorRepository.requestAppointment, which always creates the row with status "pending" for the
 *  doctor to confirm. */
@Composable
fun DoctorReservationScreen(doctorId: String, childViewModel: ChildViewModel, navController: NavController, onBack: () -> Unit) {
    val s = LocalStrings.current
    val vm: DoctorReservationViewModel = viewModel()
    val ui by vm.ui.collectAsState()
    val childState by childViewModel.uiState.collectAsState()
    val selectedChildId by ChildSelection.id.collectAsState()
    val selectedChild = remember(selectedChildId, childState.children) {
        selectedChildId?.let { id -> childState.children.firstOrNull { it.id == id } }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    var selectedDate by remember { mutableStateOf<String?>(null) }
    var selectedSlotId by remember { mutableStateOf<String?>(null) }
    var reason by remember { mutableStateOf("") }
    // No default: the parent must state why they are coming in, so the doctor can triage the queue.
    var bookingType by remember { mutableStateOf<AppointmentBookingType?>(null) }
    val isEmergency = bookingType == AppointmentBookingType.EMERGENCY

    // Vaccine selection state (when bookingType == VACCINE)
    var isCustomVaccine by remember { mutableStateOf(false) }
    var selectedVaccineRecord by remember { mutableStateOf<com.lullabyai.app.data.model.VaccineRecord?>(null) }
    var customVaccineName by remember { mutableStateOf("") }

    val healthRepo = remember { com.lullabyai.app.data.repository.HealthRepository() }
    var loadedVaccines by remember { mutableStateOf<List<com.lullabyai.app.data.model.VaccineRecord>>(emptyList()) }

    LaunchedEffect(selectedChildId) {
        val cid = selectedChildId
        if (!cid.isNullOrBlank()) {
            loadedVaccines = healthRepo.getVaccineRecords(cid).getOrDefault(emptyList())
        } else {
            loadedVaccines = emptyList()
        }
    }

    val childVaccines = remember(loadedVaccines, childState.vaccines, selectedChildId) {
        if (loadedVaccines.isNotEmpty()) loadedVaccines
        else selectedChildId?.let { id -> childState.vaccines.filter { it.childId == id } } ?: emptyList()
    }
    val duplicateOrSimilarMatch = remember(customVaccineName, childVaccines, isCustomVaccine) {
        if (isCustomVaccine && customVaccineName.trim().isNotBlank()) {
            com.lullabyai.app.util.VaccineSimilarityMatcher.findSimilarVaccine(customVaccineName, childVaccines)
        } else null
    }

    LaunchedEffect(doctorId) {
        vm.load(doctorId)
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, doctorId) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                vm.refreshSlots(doctorId)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(doctorId, selectedChildId) { vm.refreshLinkGate(doctorId, selectedChildId) }
    LaunchedEffect(selectedChildId) {
        if (selectedChildId.isNullOrBlank()) {
            snackbarHostState.showSnackbar(pediaConnectMissingChildMessage(s.isThai))
        }
    }
    LaunchedEffect(ui.bookedAppointment) {
        ui.bookedAppointment?.let { appt ->
            navController.navigate(Screen.AppointmentDetails.route(appt.id)) {
                popUpTo(Screen.DoctorReservation.route) { inclusive = true }
            }
        }
    }

    val datesAvailable = remember(ui.slots) { ui.slots.map { it.slotDate }.distinct().sorted() }
    LaunchedEffect(datesAvailable) { if (selectedDate == null) selectedDate = datesAvailable.firstOrNull() }
    val slotsForDate = remember(ui.slots, selectedDate) { ui.slots.filter { it.slotDate == selectedDate } }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            com.lullabyai.app.ui.components.LullabyTopBar(
                title = if (s.isThai) "จองคิวคุณหมอ" else "Doctor Reservation",
                onBack = onBack,
                navController = navController
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (ui.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Doctor header
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DoctorAvatar(ui.photoUrl ?: ui.doctorProfile?.clinicPhotoUrl, size = 64.dp)
                    Column {
                        Text(
                            ui.profile?.let { doctorDisplayName(it, s.isThai) } ?: (if (s.isThai) "ไม่ระบุชื่อแพทย์" else "Unnamed doctor"),
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold
                        )
                        ui.doctorProfile?.specialty?.takeIf { it.isNotBlank() }?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = Primary)
                        }
                        ui.doctorProfile?.clinicName?.takeIf { it.isNotBlank() }?.let {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Filled.LocalHospital, null, tint = Outline, modifier = Modifier.size(13.dp))
                                Text(it, style = MaterialTheme.typography.labelSmall, color = Outline)
                            }
                        }
                    }
                }
            }

            when (ui.linkGate) {
                ClinicBookingGate.NEED_REGISTRATION -> {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f))
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                if (s.isThai) "ยังไม่ได้ลงทะเบียน Pedia Connect" else "Not on Pedia Connect yet",
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                if (s.isThai) "ลงทะเบียนลูกในระบบ Pedia Connect ก่อนส่งคำขอจองคิว"
                                else "Register your child on Pedia Connect before requesting a slot.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Outline
                            )
                            OutlinedButton(
                                onClick = { navController.navigate(Screen.PatientRegistration.route(PATIENT_REGISTRATION_NO_DOCTOR)) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(if (s.isThai) "ไปลงทะเบียน Pedia Connect" else "Register on Pedia Connect", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                else -> Unit
            }

            // Visit type — required, and ฉุกเฉิน is one of the choices rather than a separate checkbox
            // so that picking it visibly removes the time step.
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (s.isThai) "ประเภทการนัดหมาย" else "Appointment type", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BOOKING_TYPE_CHOICES.forEach { type ->
                        FilterChip(
                            selected = bookingType == type,
                            onClick = {
                                bookingType = type
                                if (type == AppointmentBookingType.EMERGENCY) selectedSlotId = null
                            },
                            label = { Text(type.label(s.isThai), style = MaterialTheme.typography.labelMedium) },
                            shape = RoundedCornerShape(100.dp)
                        )
                    }
                }
                when {
                    bookingType == null -> Text(
                        if (s.isThai) "กรุณาเลือกประเภทก่อนส่งคำขอ" else "Pick a type before submitting",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    isEmergency -> Text(
                        if (s.isThai) "ฉุกเฉิน: ไม่ต้องเลือกเวลา — คิวลำดับแรก แต่ยังต้องรอคุณหมออนุมัติ"
                        else "Emergency: no time slot needed — first in queue, still needs doctor approval",
                        style = MaterialTheme.typography.labelSmall,
                        color = Outline
                    )
                    else -> Unit
                }
            }

            // Vaccine Selection Section (When Vaccine type is selected)
            if (bookingType == AppointmentBookingType.VACCINE) {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Filled.LocalHospital,
                                contentDescription = null,
                                tint = Primary
                            )
                            Text(
                                if (s.isThai) "เลือกวัคซีนที่ต้องการนัดฉีด" else "Select Vaccine for Appointment",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Mode switcher: จากสมุดวัคซีน vs เพิ่มวัคซีนใหม่
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = !isCustomVaccine,
                                onClick = { isCustomVaccine = false },
                                label = {
                                    Text(
                                        if (s.isThai) "จากสมุดวัคซีน (${childVaccines.size})"
                                        else "From Vaccine Book (${childVaccines.size})"
                                    )
                                },
                                shape = RoundedCornerShape(100.dp),
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = isCustomVaccine,
                                onClick = { isCustomVaccine = true },
                                label = {
                                    Text(
                                        if (s.isThai) "+ เพิ่มวัคซีนใหม่ / อื่นๆ"
                                        else "+ Add New / Other"
                                    )
                                },
                                shape = RoundedCornerShape(100.dp),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        if (!isCustomVaccine) {
                            if (childVaccines.isEmpty()) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        if (s.isThai) "ยังไม่มีข้อมูลในสมุดวัคซีนของน้อง"
                                        else "No vaccines found in child's book",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Outline
                                    )
                                    OutlinedButton(
                                        onClick = { isCustomVaccine = true },
                                        shape = RoundedCornerShape(100.dp)
                                    ) {
                                        Text(if (s.isThai) "พิมพ์ระบุวัคซีนใหม่" else "Enter new vaccine name")
                                    }
                                }
                            } else {
                                BookingDropdown(
                                    label = if (s.isThai) "เลือกวัคซีนจากสมุด" else "Select vaccine from book",
                                    display = selectedVaccineRecord?.let {
                                        "${it.vaccineName} (${vaccineStatusLabel(it.status, s.isThai)})"
                                    } ?: (if (s.isThai) "กรุณาเลือกวัคซีน..." else "Please select a vaccine..."),
                                    options = childVaccines,
                                    optionLabel = {
                                        val ageStr = it.ageGroup?.let { g -> " • $g" }.orEmpty()
                                        "${it.vaccineName}$ageStr [${vaccineStatusLabel(it.status, s.isThai)}]"
                                    },
                                    onSelect = { selectedVaccineRecord = it }
                                )
                                if (selectedVaccineRecord == null) {
                                    Text(
                                        if (s.isThai) "กรุณาเลือกรายการวัคซีนที่ต้องการนัดหมาย"
                                        else "Please pick a vaccine to book",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        } else {
                            OutlinedTextField(
                                value = customVaccineName,
                                onValueChange = { customVaccineName = it },
                                label = { Text(if (s.isThai) "ชื่อวัคซีนใหม่ / เพิ่มเติม" else "New / Other Vaccine Name") },
                                placeholder = {
                                    Text(
                                        if (s.isThai) "เช่น วัคซีนอีสุกอีใส, ไข้เลือดออก, HPV, หรืออื่นๆ"
                                        else "e.g. Varicella, Dengue, HPV..."
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp)
                            )

                            // Duplicate / Similarity Warning
                            if (duplicateOrSimilarMatch != null) {
                                Card(
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.28f)
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                                    )
                                ) {
                                    Column(
                                        Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = androidx.compose.material.icons.Icons.Filled.LocalHospital,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Text(
                                                if (s.isThai) "พบวัคซีนที่ตรงหรือใกล้เคียงในสมุดวัคซีน!"
                                                else "Similar vaccine found in book!",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        }
                                        Text(
                                            text = if (s.isThai)
                                                "พบรายการ: \"${duplicateOrSimilarMatch.matchedRecord.vaccineName}\" (${duplicateOrSimilarMatch.matchedRecord.ageGroup ?: "ไม่ระบุกลุ่มอายุ"})\nสถานะปัจจุบัน: ${vaccineStatusLabel(duplicateOrSimilarMatch.matchedRecord.status, true)}\nข้อสังเกต: ${duplicateOrSimilarMatch.matchReason}"
                                            else
                                                "Found: \"${duplicateOrSimilarMatch.matchedRecord.vaccineName}\" (${duplicateOrSimilarMatch.matchedRecord.ageGroup ?: "No age group"})\nStatus: ${vaccineStatusLabel(duplicateOrSimilarMatch.matchedRecord.status, false)}\nNote: ${duplicateOrSimilarMatch.matchReason}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Button(
                                            onClick = {
                                                isCustomVaccine = false
                                                selectedVaccineRecord = duplicateOrSimilarMatch.matchedRecord
                                                customVaccineName = ""
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.error
                                            ),
                                            shape = RoundedCornerShape(100.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                if (s.isThai) "สลับไปเลือกรายการนี้จากสมุดวัคซีน"
                                                else "Switch to select from vaccine book",
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Date + time — dropdowns, hidden for emergency
            if (!isEmergency) {
                if (datesAvailable.isEmpty()) {
                    Text(if (s.isThai) "คุณหมอยังไม่เปิดคิวว่าง" else "No open slots yet", color = Outline)
                } else {
                    BookingDropdown(
                        label = if (s.isThai) "เลือกวันที่" else "Select date",
                        display = selectedDate?.let { thaiDateTimeLabel(it, s.isThai, withTime = false) ?: it }
                            ?: (if (s.isThai) "เลือกวันที่" else "Select date"),
                        options = datesAvailable,
                        optionLabel = { thaiDateTimeLabel(it, s.isThai, withTime = false) ?: it },
                        onSelect = { selectedDate = it; selectedSlotId = null }
                    )

                    val bookableSlots = slotsForDate.filter { DoctorRepository.isFutureSlot(it) }
                    if (bookableSlots.isEmpty()) {
                        Text(
                            if (s.isThai) "วันนี้ไม่มีเวลาว่างเหลือ — เลือกวันอื่น" else "No time left on this date — pick another day",
                            style = MaterialTheme.typography.bodySmall,
                            color = Outline
                        )
                    } else {
                        BookingDropdown(
                            label = if (s.isThai) "เลือกเวลา" else "Select time",
                            display = bookableSlots.firstOrNull { it.id == selectedSlotId }
                                ?.let { formatReservationSlotTime(it.startTime) }
                                ?: (if (s.isThai) "เลือกเวลา" else "Select time"),
                            options = bookableSlots,
                            optionLabel = { formatReservationSlotTime(it.startTime) },
                            onSelect = { selectedSlotId = it.id }
                        )
                    }
                }
            }

            // Appointment details: read-only child from Pedia Connect dashboard + reason.
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (s.isThai) "นัดหมายสำหรับ" else "Appointment for", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                when {
                    selectedChild != null -> ReadOnlySelectedChildChip(selectedChild, s.isThai)
                    selectedChildId.isNullOrBlank() -> Text(
                        pediaConnectMissingChildMessage(s.isThai),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    else -> Text(
                        if (s.isThai) "ไม่พบข้อมูลลูกที่เลือก — กลับไปเลือกใหม่ที่หน้า Pedia Connect"
                        else "Selected child not found — go back to Pedia Connect and pick again.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            // Reason
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (s.isThai) "เหตุผลการนัดหมาย" else "Reason for visit", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = reason, onValueChange = { reason = it }, modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(if (s.isThai) "เช่น ปรึกษาพัฒนาการเด็ก" else "e.g. developmental consultation") },
                    minLines = 2
                )
            }

            ui.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

            val isVaccineValid = bookingType != AppointmentBookingType.VACCINE ||
                (!isCustomVaccine && selectedVaccineRecord != null) ||
                (isCustomVaccine && customVaccineName.trim().isNotBlank())

            Button(
                onClick = {
                    val type = bookingType ?: return@Button
                    selectedChildId?.let { childId ->
                        val vacName = if (type == AppointmentBookingType.VACCINE) {
                            if (isCustomVaccine) customVaccineName.trim() else selectedVaccineRecord?.vaccineName
                        } else null
                        val vacRecordId = if (type == AppointmentBookingType.VACCINE && !isCustomVaccine) {
                            selectedVaccineRecord?.id
                        } else null

                        val encodedReason = AppointmentBookingMeta.encodeReason(
                            type = type,
                            detail = reason.ifBlank { null },
                            vaccineName = vacName,
                            vaccineRecordId = vacRecordId
                        )
                        vm.book(doctorId, childId, selectedSlotId, encodedReason, isEmergency, s.isThai)
                    }
                },
                enabled = !ui.booking && selectedChildId != null && bookingType != null &&
                    ui.linkGate == ClinicBookingGate.CAN_BOOK &&
                    isVaccineValid &&
                    (isEmergency || datesAvailable.isEmpty() || selectedSlotId != null),
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(100.dp)
            ) {
                if (ui.booking) CircularProgressIndicator(modifier = Modifier.size(18.dp), color = androidx.compose.ui.graphics.Color.White)
                else Text(
                    if (s.isThai) "ส่งคำขอจองคิว" else "Submit booking request",
                    fontWeight = FontWeight.Bold
                )
            }
            if (ui.linkGate == ClinicBookingGate.CAN_BOOK) {
                Text(
                    if (s.isThai) "คำขอจะอยู่สถานะรอยืนยันจนกว่าคุณหมอจะอนุมัติ"
                    else "Your request stays pending until the doctor approves it.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Outline
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

private fun vaccineStatusLabel(status: String?, isThai: Boolean): String = when (status) {
    "completed" -> if (isThai) "ฉีดแล้ว" else "Completed"
    "overdue" -> if (isThai) "เลยกำหนด" else "Overdue"
    "appointment" -> if (isThai) "มีนัดแล้ว" else "Appointment"
    "skipped" -> if (isThai) "ข้าม" else "Skipped"
    else -> if (isThai) "รอฉีด" else "Upcoming"
}

/** The four types the parent picks from. [AppointmentBookingType.HEALTH_CHECK] stays in the enum so
 *  older rows still decode, but it is no longer offered. */
private val BOOKING_TYPE_CHOICES = listOf(
    AppointmentBookingType.NORMAL,
    AppointmentBookingType.VACCINE,
    AppointmentBookingType.FOLLOW_UP,
    AppointmentBookingType.EMERGENCY
)

/** Single time value, never a range: `18:30:00` renders as `18.30`. */
private fun formatReservationSlotTime(time: String): String =
    time.take(5).replace(':', '.')

/** Read-only dropdown field used for both the date and the time step. */
@Composable
private fun <T> BookingDropdown(
    label: String,
    display: String,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = display,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = { onSelect(option); expanded = false }
                )
            }
        }
    }
}

private fun pediaConnectMissingChildMessage(isThai: Boolean): String =
    if (isThai) "กรุณากลับไปเลือกลูกที่หน้า Pedia Connect ก่อนจองคิว"
    else "Go back to Pedia Connect and select a child before booking."

@Composable
private fun ReadOnlySelectedChildChip(child: Child, isThai: Boolean) {
    Surface(
        shape = RoundedCornerShape(100.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DoctorAvatar(child.avatarUrl, size = 28.dp)
            Text(
                child.name.ifBlank { if (isThai) "ไม่ระบุชื่อ" else "Unnamed" },
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

