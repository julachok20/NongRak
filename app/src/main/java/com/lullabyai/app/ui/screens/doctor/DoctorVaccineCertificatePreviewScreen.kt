@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.doctor

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
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
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.lullabyai.app.ui.components.LullabyTopBar
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.DoctorVaccineCertificateViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Stitch 46 — preview / approve before send (vaccine_verification_hub_doctor_side). */
@Composable
fun DoctorVaccineCertificatePreviewScreen(
    navController: NavController,
    certVm: DoctorVaccineCertificateViewModel = viewModel()
) {
    val s = LocalStrings.current
    val context = LocalContext.current
    val preview by certVm.preview.collectAsState()
    val isWorking by certVm.isWorking.collectAsState()
    val error by certVm.error.collectAsState()

    val data = preview
    if (data == null) {
        Scaffold(
            topBar = {
                LullabyTopBar(
                    title = if (s.isThai) "ยืนยันผลวัคซีน" else "Vaccine verification",
                    onBack = { navController.popBackStack() },
                    navController = navController,
                    isDoctor = true
                )
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                if (isWorking) CircularProgressIndicator()
                else Text(error ?: if (s.isThai) "ไม่พบข้อมูลตัวอย่าง" else "No preview data", color = Outline)
            }
        }
        return
    }

    Scaffold(
        topBar = {
            LullabyTopBar(
                title = if (s.isThai) "ยืนยันผลวัคซีน" else "Vaccine verification",
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
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        Modifier.size(64.dp).clip(CircleShape).background(SecondaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.ChildCare, null, tint = OnSecondaryContainer, modifier = Modifier.size(32.dp))
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(data.childName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(shape = RoundedCornerShape(8.dp), color = SurfaceContainer) {
                                Text(
                                    "HN: ${data.hn}",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Outline
                                )
                            }
                            data.ageLine?.let { age ->
                                Surface(shape = RoundedCornerShape(8.dp), color = Primary.copy(0.1f)) {
                                    Text(
                                        age,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Primary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(Primary)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Vaccine Record",
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(Icons.Filled.VerifiedUser, null, tint = Color.White)
                    }
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(Modifier.fillMaxWidth()) {
                            Column(Modifier.weight(1f)) {
                                DetailLabel(if (s.isThai) "วัคซีน" else "Vaccine")
                                Text(data.vaccineName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            }
                            Column(Modifier.weight(1f)) {
                                DetailLabel(if (s.isThai) "ครั้งที่" else "Dose")
                                Text(data.doseLabel, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        HorizontalDivider(color = OutlineVariant.copy(0.4f))
                        DetailRow(Icons.Filled.CalendarMonth, if (s.isThai) "วันที่ได้รับ" else "Date", formatPreviewDate(data.dateGiven, s.isThai))
                        data.lotNumber?.let {
                            DetailRow(Icons.Filled.QrCodeScanner, if (s.isThai) "หมายเลขล็อต" else "Lot/Batch", it)
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (s.isThai) "เอกสารรับรอง (preview)" else "Certificate (preview)",
                    style = MaterialTheme.typography.labelMedium,
                    color = Outline,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 4.dp)
                )
                Surface(
                    onClick = {
                        if (!data.pdfFile.exists()) {
                            Toast.makeText(
                                context,
                                if (s.isThai) "ไม่พบไฟล์ตัวอย่าง PDF" else "Preview PDF not found",
                                Toast.LENGTH_SHORT
                            ).show()
                            return@Surface
                        }
                        runCatching {
                            val uri = FileProvider.getUriForFile(
                                context, "${context.packageName}.fileprovider", data.pdfFile
                            )
                            val view = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, "application/pdf")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(Intent.createChooser(view, null))
                        }.onFailure {
                            Toast.makeText(
                                context,
                                if (s.isThai) "เปิดไฟล์ PDF ไม่สำเร็จ — ติดตั้งแอปอ่าน PDF" else "Could not open PDF — install a PDF viewer",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    color = SurfaceContainerLow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .border(2.dp, OutlineVariant.copy(0.6f), RoundedCornerShape(16.dp))
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Filled.PictureAsPdf, null, tint = Primary, modifier = Modifier.size(40.dp))
                            Text(
                                if (s.isThai) "แตะเพื่อดูตัวอย่างใบรับรองวัคซีน" else "Tap to preview certificate PDF",
                                style = MaterialTheme.typography.labelMedium,
                                color = Primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color.Transparent, Primary.copy(0.08f))
                                    )
                                )
                        )
                    }
                }
            }

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(TertiaryContainer.copy(0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.PersonPin, null, tint = Tertiary)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(if (s.isThai) "ส่งถึง" else "Recipient", style = MaterialTheme.typography.labelSmall, color = Outline)
                        Text(data.recipientName, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            error?.let {
                Text(it, color = Error, style = MaterialTheme.typography.bodySmall)
            }

            Button(
                onClick = {
                    certVm.approveAndSend(context) {
                        navController.navigate(Screen.DoctorVaccineVerificationSent.route) {
                            popUpTo(Screen.DoctorVaccineCertificatePreview.route) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                },
                enabled = !isWorking,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                if (isWorking) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text(if (s.isThai) "กำลังส่ง..." else "Sending...")
                } else {
                    Text(if (s.isThai) "ส่งข้อมูลยืนยันวัคซีน" else "Approve and send", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.AutoMirrored.Filled.Send, null, modifier = Modifier.size(20.dp))
                }
            }

            OutlinedButton(
                onClick = { navController.popBackStack() },
                enabled = !isWorking,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(if (s.isThai) "แก้ไขข้อมูล" else "Edit details", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun DetailLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = Outline)
}

@Composable
private fun DetailRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        DetailLabel(label)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, tint = Primary, modifier = Modifier.size(16.dp))
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        }
    }
}

private fun formatPreviewDate(iso: String, isThai: Boolean): String = runCatching {
    val d = LocalDate.parse(iso.take(10))
    if (isThai) "${d.dayOfMonth} ${monthTh(d.monthValue)} ${d.year + 543}"
    else DateTimeFormatter.ofPattern("d MMM yyyy", Locale.US).format(d)
}.getOrDefault(iso)

private fun monthTh(m: Int): String = listOf(
    "", "ม.ค.", "ก.พ.", "มี.ค.", "เม.ย.", "พ.ค.", "มิ.ย.", "ก.ค.", "ส.ค.", "ก.ย.", "ต.ค.", "พ.ย.", "ธ.ค."
).getOrElse(m) { "" }
