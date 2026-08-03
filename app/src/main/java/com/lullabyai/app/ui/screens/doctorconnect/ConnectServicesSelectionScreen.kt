@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.doctorconnect

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeviceHub
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.lullabyai.app.data.AppConnectFeatures
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.HealthAttention
import com.lullabyai.app.ui.theme.Outline
import com.lullabyai.app.ui.theme.Primary
import com.lullabyai.app.ui.theme.Secondary

/**
 * "เชื่อมต่อบริการ (Connect Services)" — the MAIN Connect hub and the CONNECT tab root.
 *
 * The two branches of the Connect tree start here:
 *  - **App Connect** -> [Screen.AppConnectSetup] (categorised health-app / wearable / IoT sync)
 *  - **Pedia Connect** -> [Screen.ConnectToDoctorWelcome], which in turn leads to the Pedia Connect
 *    dashboard. It deliberately does *not* jump straight to the dashboard: the welcome screen is the
 *    documented parent of the dashboard in the Connect hierarchy.
 */
@Composable
fun ConnectServicesSelectionScreen(navController: NavController, onBack: () -> Unit) {
    val s = LocalStrings.current

    Scaffold(
        topBar = {
            com.lullabyai.app.ui.components.LullabyTopBar(
                title = if (s.isThai) "เลือกการเชื่อมต่อ" else "Connect Services",
                // Tab root — the footer is how you leave, so no back arrow (#6).
                onBack = null,
                navController = navController
            )
        },
        bottomBar = { ConnectBottomNav() },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    if (s.isThai) "เชื่อมต่อบริการ" else "Connect Services",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    if (s.isThai) "เลือกช่องทางการเชื่อมต่อเพื่อซิงค์ข้อมูลสุขภาพของลูกน้อย ให้ทุกข้อมูลแม่นยำและเข้าถึงง่าย"
                    else "Choose how you'd like to connect and sync your child's health data — accurate and always at hand.",
                    style = MaterialTheme.typography.bodyMedium, color = Outline, textAlign = TextAlign.Center
                )
            }

            ServiceCard(
                icon = Icons.Filled.DeviceHub,
                tint = if (AppConnectFeatures.IS_READY) Primary else Outline,
                badge = if (AppConnectFeatures.IS_READY) "App Connect"
                else if (s.isThai) "ยังไม่พร้อม" else "Not ready",
                title = "App Connect",
                description = if (AppConnectFeatures.IS_READY) {
                    if (s.isThai) "เชื่อมต่อกับแอปสุขภาพอื่น ๆ เช่น Apple Health หรือ Google Fit"
                    else "Sync with other health apps like Apple Health or Google Fit"
                } else {
                    if (s.isThai) "การซิงค์แอปสุขภาพและ wearable ยังไม่พร้อมใช้งาน — เรากำลังพัฒนาอยู่"
                    else "Health app and wearable sync is not ready yet — we're still building it."
                },
                buttonLabel = if (AppConnectFeatures.IS_READY) {
                    if (s.isThai) "เชื่อมต่อแอป" else "Connect apps"
                } else {
                    if (s.isThai) "ดูรายละเอียด" else "Learn more"
                },
                enabled = true,
                onClick = { navController.navigate(Screen.AppConnectSetup.route) }
            )
            ServiceCard(
                icon = Icons.Filled.MedicalServices, tint = Secondary,
                badge = "Pedia Connect",
                title = "Pedia Connect",
                description = if (s.isThai) "เชื่อมต่อข้อมูลโดยตรงกับโรงพยาบาลและคุณหมอผู้เชี่ยวชาญ"
                else "Connect directly with hospitals and specialist doctors",
                buttonLabel = if (s.isThai) "เชื่อมต่อโรงพยาบาล" else "Connect Hospital",
                onClick = { navController.navigate(Screen.ConnectToDoctorWelcome.route) }
            )

            Row(
                Modifier.fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                    .padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Filled.Info, null, tint = HealthAttention, modifier = Modifier.size(20.dp))
                Text(
                    if (s.isThai) "ข้อมูลของคุณจะถูกเก็บเป็นความลับสูงสุดและจะแชร์เฉพาะข้อมูลที่คุณอนุญาตเท่านั้น"
                    else "Your data stays private — only what you authorize is ever shared.",
                    style = MaterialTheme.typography.labelMedium, color = Outline
                )
            }
        }
    }
}

/** Stitch's "glass card": tinted illustration band with a floating pill badge, then copy + CTA. */
@Composable
private fun ServiceCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    badge: String,
    title: String,
    description: String,
    buttonLabel: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.fillMaxWidth()) {
            Box(
                Modifier.fillMaxWidth().height(120.dp)
                    .background(Brush.verticalGradient(listOf(tint.copy(alpha = 0.22f), tint.copy(alpha = 0.06f)))),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(48.dp))
                Surface(
                    shape = RoundedCornerShape(100.dp),
                    color = Color.White.copy(alpha = 0.92f),
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp)
                ) {
                    Row(
                        Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(icon, null, tint = tint, modifier = Modifier.size(15.dp))
                        Text(badge, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = tint)
                    }
                }
            }
            Column(
                Modifier.padding(18.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(description, style = MaterialTheme.typography.bodySmall, color = Outline, textAlign = TextAlign.Center)
                Button(
                    onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = tint)
                ) { Text(buttonLabel, fontWeight = FontWeight.Bold) }
            }
        }
    }
}
