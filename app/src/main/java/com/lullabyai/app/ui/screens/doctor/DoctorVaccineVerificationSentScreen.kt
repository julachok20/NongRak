@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.doctor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.lullabyai.app.ui.components.LullabyTopBar
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.DoctorVaccineCertificateViewModel
import com.lullabyai.app.viewmodel.DoctorVaccineVerificationHubViewModel
import com.lullabyai.app.viewmodel.VaccineCertificateSentData
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Confirmation after doctor sends ใบรับรองวัคซีน (Stitch 46) or resolves a hub request. */
@Composable
fun DoctorVaccineVerificationSentScreen(
    navController: NavController,
    hubVm: DoctorVaccineVerificationHubViewModel,
    certVm: DoctorVaccineCertificateViewModel
) {
    val s = LocalStrings.current
    val hubState by hubVm.uiState.collectAsState()
    val certSent by certVm.lastSent.collectAsState()

    val hubResolved = hubState.lastResolved
    val hubChild = hubResolved?.let { hubState.childrenById[it.childId] }
    val hubRecord = hubResolved?.let { hubState.vaccineRecordsById[it.vaccineRecordId] }
    val fromCertificate = certSent != null

    val approved = when {
        fromCertificate -> certSent?.approved != false
        hubResolved != null -> hubResolved.status == "verified"
        else -> true
    }

    val display = when {
        certSent != null -> certSent
        hubResolved != null -> VaccineCertificateSentData(
            childName = hubChild?.name ?: "-",
            vaccineName = hubRecord?.vaccineName ?: "-",
            doseLabel = hubRecord?.ageOrder?.let { ord ->
                if (s.isThai) "ครั้งที่ ${ord + 1}" else "Dose ${ord + 1}"
            },
            dateGiven = hubRecord?.dateGiven ?: "-",
            providerName = "-",
            certificatePath = hubRecord?.certificateUrl,
            approved = approved
        )
        else -> null
    }

    Scaffold(
        topBar = {
            LullabyTopBar(
                title = if (s.isThai) "การยืนยันวัคซีน" else "Vaccine Verification",
                onBack = { navController.popBackStack() },
                navController = navController,
                isDoctor = true
            )
        },
        bottomBar = { DoctorBottomNav(navController, DoctorTab.PATIENTS) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(140.dp)
                        .border(2.dp, Primary.copy(0.25f), CircleShape)
                )
                Box(
                    Modifier
                        .size(128.dp)
                        .clip(CircleShape)
                        .background(PrimaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (approved) Icons.Filled.VerifiedUser else Icons.Filled.Cancel,
                        contentDescription = null,
                        tint = OnPrimaryContainer,
                        modifier = Modifier.size(64.dp)
                    )
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    if (approved) {
                        if (s.isThai) "ส่งเอกสารรับรองวัคซีนแล้ว" else "Vaccine certificate sent"
                    } else {
                        if (s.isThai) "ปฏิเสธคำขอยืนยันวัคซีนแล้ว" else "Vaccine verification rejected"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                display?.childName?.let {
                    Text(it, style = MaterialTheme.typography.bodyLarge, color = Outline)
                }
            }

            if (display != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (s.isThai) "สรุป" else "Summary",
                                style = MaterialTheme.typography.labelMedium,
                                color = Outline,
                                fontWeight = FontWeight.Bold
                            )
                            Surface(
                                shape = RoundedCornerShape(100.dp),
                                color = if (approved) TertiaryContainer.copy(0.12f) else ErrorContainer
                            ) {
                                Text(
                                    if (approved) (if (s.isThai) "สำเร็จ" else "Completed") else (if (s.isThai) "ปฏิเสธ" else "Rejected"),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (approved) Tertiary else Error,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        HorizontalDivider(color = SurfaceContainer)
                        Row(Modifier.fillMaxWidth()) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                SummaryLabel(if (s.isThai) "ชื่อวัคซีน" else "Vaccine name")
                                Text(display.vaccineName, fontWeight = FontWeight.SemiBold)
                            }
                            display.doseLabel?.let { dose ->
                                Column(
                                    Modifier.weight(1f),
                                    horizontalAlignment = Alignment.End,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    SummaryLabel(if (s.isThai) "ครั้งที่" else "Dose")
                                    Text(dose, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End)
                                }
                            }
                        }
                        Row(Modifier.fillMaxWidth()) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                SummaryLabel(if (s.isThai) "วันที่ฉีด" else "Date given")
                                Text(formatCertDate(display.dateGiven, s.isThai), fontWeight = FontWeight.SemiBold)
                            }
                            Column(
                                Modifier.weight(1f),
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                SummaryLabel(if (s.isThai) "ผู้ให้บริการ" else "Provider")
                                Text(
                                    display.providerName,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = TextAlign.End
                                )
                            }
                        }
                    }
                }

                if (approved && !display.certificatePath.isNullOrBlank()) {
                    CertificatePreviewCard(isThai = s.isThai)
                }
            }

            Button(
                onClick = {
                    certVm.clear()
                    navController.popBackStack()
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(if (s.isThai) "กลับไปบันทึกการตรวจ" else "Back to clinical record", fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Filled.EditNote, null, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun SummaryLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = Outline)
}

@Composable
private fun CertificatePreviewCard(isThai: Boolean) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(160.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, OutlineVariant.copy(0.35f), RoundedCornerShape(16.dp))
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(InversePrimary.copy(0.35f), SurfaceContainerLow, Color.White)
                    )
                )
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.55f)))),
            contentAlignment = Alignment.BottomStart
        ) {
            Row(
                Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Filled.Visibility, null, tint = Color.White, modifier = Modifier.size(18.dp))
                Text(
                    if (isThai) "ตัวอย่างเอกสารส่งมอบ" else "Delivered document preview",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

private fun formatCertDate(iso: String, isThai: Boolean): String = runCatching {
    val d = LocalDate.parse(iso.take(10))
    if (isThai) "${d.dayOfMonth}/${d.monthValue}/${d.year + 543}"
    else DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US).format(d)
}.getOrDefault(iso)
