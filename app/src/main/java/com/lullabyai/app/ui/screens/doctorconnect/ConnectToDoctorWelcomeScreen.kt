@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.doctorconnect

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.HealthGood
import com.lullabyai.app.ui.theme.Outline
import com.lullabyai.app.ui.theme.Primary

/**
 * "ปรึกษาแพทย์ / Access Your Doctor" — the Pedia Connect branch's landing screen.
 * Sits between [ConnectServicesSelectionScreen] (the hub) and [PediaConnectDashboardScreen]:
 * its single CTA goes to the dashboard, where the parent picks which child to connect.
 */
@Composable
fun ConnectToDoctorWelcomeScreen(navController: NavController, onBack: () -> Unit) {
    val s = LocalStrings.current

    Scaffold(
        topBar = {
            com.lullabyai.app.ui.components.LullabyTopBar(
                title = if (s.isThai) "เข้าถึงแพทย์" else "Access Your Doctor",
                onBack = onBack,
                navController = navController
            )
        },
        bottomBar = { ConnectBottomNav() },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Hero band with the "Doctors Online Now" live pill from Stitch.
            Box(
                Modifier.fillMaxWidth().height(200.dp)
                    .background(
                        Brush.verticalGradient(listOf(Primary.copy(alpha = 0.20f), Primary.copy(alpha = 0.05f))),
                        RoundedCornerShape(24.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.MedicalServices, null, tint = Primary, modifier = Modifier.size(72.dp))
                Surface(
                    shape = RoundedCornerShape(100.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Box(Modifier.size(8.dp).background(HealthGood, CircleShape))
                        Text(
                            if (s.isThai) "มีคุณหมอออนไลน์อยู่" else "Doctors Online Now",
                            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    if (s.isThai) "เข้าถึงแพทย์ได้ทุกเวลา" else "Access Your Doctor",
                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center
                )
                if (s.isThai) {
                    Text("Access Your Doctor", style = MaterialTheme.typography.titleSmall, color = Primary)
                }
                Text(
                    if (s.isThai) "ดูตารางนัดหมาย รับแผนการดูแล และซิงค์ข้อมูลกับคุณหมอได้โดยตรง"
                    else "View appointments, receive care plans, and sync data directly with your pediatrician.",
                    style = MaterialTheme.typography.bodyMedium, color = Outline, textAlign = TextAlign.Center
                )
            }

            // Stitch feature bento: two cards, Thai headline + English sub-label.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FeatureTile(
                    Modifier.weight(1f), Icons.Filled.Schedule,
                    if (s.isThai) "เข้าถึงได้ 24 ชม." else "24/7 Access", "24/7 Access", s.isThai
                )
                FeatureTile(
                    Modifier.weight(1f), Icons.Filled.MonitorHeart,
                    if (s.isThai) "กุมารแพทย์เฉพาะทาง" else "Expert Pediatricians", "Expert Pediatricians", s.isThai
                )
            }

            Spacer(Modifier.height(4.dp))

            Button(
                onClick = { navController.navigate(Screen.PediaConnectDashboard.route) },
                modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(100.dp)
            ) {
                Text(if (s.isThai) "ลงทะเบียน / เลือกลูก" else "Register / choose child", fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Filled.ArrowForward, null, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun FeatureTile(
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    isThai: Boolean
) {
    Card(
        modifier = modifier, shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(38.dp).background(Primary.copy(alpha = 0.12f), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = Primary, modifier = Modifier.size(20.dp))
            }
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            // Stitch pairs each Thai headline with its English label; skip the duplicate on EN.
            if (isThai) Text(subtitle, style = MaterialTheme.typography.labelSmall, color = Outline)
        }
    }
}
