@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.doctor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.lullabyai.app.data.model.PediaConnectPatientParentRecord
import com.lullabyai.app.data.model.PediaConnectPatientRecord
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.screens.doctorconnect.DoctorAvatar
import com.lullabyai.app.ui.screens.doctorconnect.StatusPill
import com.lullabyai.app.ui.screens.doctorconnect.childAgeLabel
import com.lullabyai.app.ui.screens.doctorconnect.thaiDateTimeLabel
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.DoctorPatientRegistrationDetailViewModel

/** Stitch: "Patient Registration with Appointment Details" — read-only review + approve/reject booking. */
@Composable
fun DoctorPatientRegistrationDetailScreen(appointmentId: String, navController: NavController) {
    val s = LocalStrings.current
    val vm: DoctorPatientRegistrationDetailViewModel = viewModel()
    val state by vm.uiState.collectAsState()
    LaunchedEffect(appointmentId) { vm.load(appointmentId) }
    var hn by remember { mutableStateOf("") }

    var showRejectDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.actionDone) {
        if (state.actionDone) navController.popBackStack()
    }

    val intake = state.intake
    val appt = state.appointment
    val pending = appt?.status == "pending"

    Scaffold(
        topBar = {
            com.lullabyai.app.ui.components.LullabyTopBar(
                title = if (s.isThai) "ลงทะเบียนผู้ป่วย" else "Patient registration",
                onBack = { navController.popBackStack() },
                navController = navController,
                centerTitle = false,
                isDoctor = true
            )
        },
        bottomBar = { DoctorBottomNav(navController, DoctorTab.PATIENTS) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        when {
            state.isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Primary)
            }
            appt == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(state.error ?: (if (s.isThai) "ไม่พบข้อมูล" else "No data"), color = Outline)
            }
            else -> {
                val child = state.syncedChild
                val displayName = intake?.name?.takeIf { it.isNotBlank() }
                    ?: child?.name?.takeIf { it.isNotBlank() }
                    ?: (if (s.isThai) "ผู้ป่วย" else "Patient")
                Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

                state.syncedChild?.let { child ->
                    StitchSection(
                        title = if (s.isThai) "ข้อมูลเด็ก (ซิงค์จากแอป)" else "Child (synced from app)",
                        titleUppercase = true,
                        trailing = {
                            Surface(shape = RoundedCornerShape(100.dp), color = InversePrimary.copy(alpha = 0.35f)) {
                                Row(
                                    Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(Icons.Filled.Sync, null, tint = Primary, modifier = Modifier.size(14.dp))
                                    Text(
                                        if (s.isThai) "อัปเดตล่าสุด: วันนี้" else "Updated today",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Primary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    ) {
                        StitchSoftCard {
                            Row(
                                Modifier.fillMaxWidth().padding(20.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Box(
                                    Modifier
                                        .size(80.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .border(4.dp, InversePrimary.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                                ) {
                                    DoctorAvatar(child.avatarUrl, size = 80.dp)
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        child.name.ifBlank { displayName },
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        childAgeLabel(child, s.isThai) + " • " + genderLabel(child.gender, intake?.gender, s.isThai),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Outline
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                                        (child.allergies ?: intake?.allergies)?.takeIf { it.isNotBlank() }?.let {
                                            StatusPill(it, Secondary, Icons.Filled.Warning)
                                        }
                                        (child.bloodGroup ?: intake?.bloodGroup)?.takeIf { it.isNotBlank() }?.let {
                                            StatusPill(it, Primary)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                StitchSection(
                    title = if (s.isThai) "ข้อมูลเด็ก (แก้ไขเพิ่มเติม)" else "Additional child info",
                    titleUppercase = true
                ) {
                    StitchSoftCard {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            ReadOnlyField(if (s.isThai) "ชื่อ-นามสกุล" else "Full name", displayName)
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                ReadOnlyField(
                                    if (s.isThai) "วันเกิด" else "Birthdate",
                                    intake?.birthdate ?: child?.birthdate ?: "-",
                                    modifier = Modifier.weight(1f)
                                )
                                ReadOnlyField(
                                    if (s.isThai) "กรุ๊ปเลือด" else "Blood group",
                                    intake?.bloodGroup ?: child?.bloodGroup ?: "-",
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            ReadOnlyField(if (s.isThai) "เพศ" else "Gender", genderLabel(child?.gender, intake?.gender, s.isThai))
                            child?.let { c ->
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    ReadOnlyField(
                                        if (s.isThai) "น้ำหนัก (กก.)" else "Weight (kg)",
                                        c.weightKg?.toString() ?: "-",
                                        modifier = Modifier.weight(1f)
                                    )
                                    ReadOnlyField(
                                        if (s.isThai) "ส่วนสูง (ซม.)" else "Height (cm)",
                                        c.heightCm?.toString() ?: "-",
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                            intake?.allergies?.takeIf { it.isNotBlank() }?.let {
                                ReadOnlyField(
                                    if (s.isThai) "ประวัติการแพ้" else "Allergy history",
                                    it,
                                    leading = { Icon(Icons.Filled.Warning, null, tint = Secondary, modifier = Modifier.size(20.dp)) },
                                    valueColor = Secondary,
                                    valueBold = true
                                )
                            }
                            intake?.notes?.takeIf { it.isNotBlank() }?.let {
                                ReadOnlyField(
                                    if (s.isThai) "โรคประจำตัว / หมายเหตุ" else "Conditions / notes",
                                    it,
                                    leading = { Icon(Icons.Filled.MedicalInformation, null, tint = Outline, modifier = Modifier.size(20.dp)) }
                                )
                            }
                            if (state.vaccineRecords.isNotEmpty()) {
                                Text(
                                    if (s.isThai) "ประวัติวัคซีน (จากสมุดผู้ปกครอง)" else "Vaccine history (parent app book)",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Primary,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                                state.vaccineRecords.filter { it.status == "completed" }.take(5).forEach { v ->
                                    Surface(shape = RoundedCornerShape(14.dp), color = SurfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                                        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Filled.Vaccines, null, tint = Primary, modifier = Modifier.size(20.dp))
                                            Column {
                                                Text(v.vaccineName, fontWeight = FontWeight.SemiBold)
                                                v.dateGiven?.let {
                                                    Text((if (s.isThai) "ได้รับเมื่อ: " else "Given: ") + it, style = MaterialTheme.typography.labelSmall, color = Outline)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                StitchSection(
                    title = if (s.isThai) "รายละเอียดการเข้ารับบริการ" else "Visit & appointment",
                    titleUppercase = true
                ) {
                    StitchSoftCard {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            ReadOnlyField(
                                if (s.isThai) "เหตุผลในการมาพบแพทย์" else "Reason for visit",
                                appt.reason?.takeIf { it.isNotBlank() }
                                    ?: (if (s.isThai) "—" else "—"),
                                multiline = true,
                                leading = { Icon(Icons.Filled.MedicalServices, null, tint = Outline, modifier = Modifier.size(20.dp)) }
                            )
                            ReadOnlyField(
                                if (s.isThai) "วันที่ต้องการนัดหมาย" else "Requested appointment",
                                thaiDateTimeLabel(appt.scheduledAt, s.isThai)
                                    ?: appt.scheduledAt
                                    ?: (if (s.isThai) "รอกำหนดเวลา" else "Awaiting schedule"),
                                leading = { Icon(Icons.Filled.CalendarToday, null, tint = Primary, modifier = Modifier.size(20.dp)) }
                            )
                            ReadOnlyField(
                                if (s.isThai) "สถานะคำขอ" else "Request status",
                                statusLabel(appt.status, s.isThai)
                            )
                        }
                    }
                }

                state.mainParent?.let { parent ->
                    GuardianSection(parent, s.isThai)
                }

                if (pending) {
                    // HN is issued when the practice takes the patient on, so it is captured here at
                    // the approval step rather than as a separate later chore.
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Filled.Badge, null, tint = Primary, modifier = Modifier.size(20.dp))
                                Text(
                                    if (s.isThai) "หมายเลข HN (ถ้ามี)" else "Hospital Number (optional)",
                                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                if (s.isThai) "หมายเลขประจำตัวคนไข้ของสถานพยาบาลคุณ ใช้เฉพาะในคลินิกของคุณเท่านั้น"
                                else "Your practice's patient number — used only within your clinic",
                                style = MaterialTheme.typography.bodySmall, color = Outline
                            )
                            OutlinedTextField(
                                value = hn, onValueChange = { hn = it },
                                modifier = Modifier.fillMaxWidth(), singleLine = true,
                                placeholder = { Text("124-55-90") },
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showRejectDialog = true },
                            enabled = !state.isActing,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(if (s.isThai) "ไม่อนุมัติ" else "Reject")
                        }
                        Button(
                            onClick = { vm.approve(appointmentId, hn.trim().ifBlank { null }) },
                            enabled = !state.isActing,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = HealthGood)
                        ) {
                            if (state.isActing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Filled.HowToReg, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if (s.isThai) "อนุมัติการจอง" else "Approve booking")
                            }
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = when (appt.status) {
                            "confirmed" -> HealthGood.copy(0.12f)
                            "cancelled" -> Error.copy(0.12f)
                            else -> SurfaceContainer
                        },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
                    ) {
                        Text(
                            statusLabel(appt.status, s.isThai),
                            Modifier.padding(16.dp),
                            fontWeight = FontWeight.Bold,
                            color = when (appt.status) {
                                "confirmed" -> HealthGood
                                "cancelled" -> Error
                                else -> Primary
                            }
                        )
                    }
                }
            }
            }
        }
    }

    if (showRejectDialog) {
        DoctorBookingRejectDialog(
            isThai = s.isThai,
            onDismiss = { showRejectDialog = false },
            onConfirm = { reason ->
                showRejectDialog = false
                vm.reject(appointmentId, reason)
            }
        )
    }
}

@Composable
private fun GuardianSection(parent: PediaConnectPatientParentRecord, isThai: Boolean) {
    StitchSection(
        title = if (isThai) "ข้อมูลผู้ปกครอง" else "Guardian",
        titleUppercase = true
    ) {
        StitchSoftCard {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            Modifier.size(40.dp).clip(RoundedCornerShape(100.dp)).background(InversePrimary.copy(0.5f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Person, null, tint = Primary)
                        }
                        Text(
                            if (isThai) "ผู้ปกครองหลัก" else "Main guardian",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Surface(shape = RoundedCornerShape(100.dp), color = HealthGood.copy(0.08f), border = androidx.compose.foundation.BorderStroke(1.dp, HealthGood.copy(0.2f))) {
                        Row(
                            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Filled.CheckCircle, null, tint = HealthGood, modifier = Modifier.size(14.dp))
                            Text(
                                if (isThai) "ติดต่อหลัก" else "Primary",
                                style = MaterialTheme.typography.labelSmall,
                                color = HealthGood,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                ReadOnlyField(if (isThai) "ชื่อ-นามสกุล" else "Full name", parent.fullName)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    parent.relationship?.let {
                        ReadOnlyField(if (isThai) "ความสัมพันธ์" else "Relationship", it, modifier = Modifier.weight(1f))
                    }
                    parent.phone?.let {
                        ReadOnlyField(
                            if (isThai) "เบอร์โทรศัพท์" else "Phone",
                            it,
                            modifier = Modifier.weight(1f),
                            leading = { Icon(Icons.Filled.Call, null, tint = Primary, modifier = Modifier.size(18.dp)) }
                        )
                    }
                }
                parent.email?.takeIf { it.isNotBlank() }?.let {
                    ReadOnlyField(
                        if (isThai) "อีเมล (Email)" else "Email",
                        it,
                        leading = { Icon(Icons.Filled.Mail, null, tint = Outline, modifier = Modifier.size(18.dp)) }
                    )
                }
            }
        }
    }
}

@Composable
private fun StitchSection(
    title: String,
    titleUppercase: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                color = Outline,
                fontWeight = FontWeight.SemiBold
            )
            trailing?.invoke()
        }
        content()
    }
}

@Composable
private fun StitchSoftCard(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        content()
    }
}

@Composable
private fun ReadOnlyField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    multiline: Boolean = false,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    valueBold: Boolean = false,
    leading: (@Composable () -> Unit)? = null
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Outline, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 4.dp))
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, OutlineVariant.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = if (multiline) 14.dp else 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                leading?.invoke()
                Text(
                    value,
                    style = if (multiline) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
                    color = valueColor,
                    fontWeight = if (valueBold) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}

private fun genderLabel(childGender: String?, intakeGender: String?, isThai: Boolean): String {
    val g = childGender?.takeIf { it.isNotBlank() } ?: intakeGender.orEmpty()
    return when (g.lowercase()) {
        "female", "f" -> if (isThai) "เพศหญิง" else "Female"
        "male", "m" -> if (isThai) "เพศชาย" else "Male"
        else -> if (isThai) "ไม่ระบุ" else "Unspecified"
    }
}

private fun statusLabel(status: String, isThai: Boolean): String = when (status) {
    "confirmed" -> if (isThai) "อนุมัติแล้ว" else "Approved"
    "cancelled" -> if (isThai) "ไม่อนุมัติ / ยกเลิก" else "Rejected / cancelled"
    "pending" -> if (isThai) "รออนุมัติการจอง" else "Pending approval"
    else -> status
}
