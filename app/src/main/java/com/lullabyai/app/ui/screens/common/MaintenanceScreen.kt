@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.common

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.*

private const val SUPPORT_EMAIL = "julachok2@gmail.com"

/**
 * Shared "coming soon / under construction" destination for any feature not yet built —
 * replaces the old per-screen inline Coming Soon placeholders (Disease Risk, Exercise
 * Instructions, Recipe Detail) with one consistent screen.
 */
@Composable
fun MaintenanceScreen(navController: NavController? = null, onBack: () -> Unit) {
    val s = com.lullabyai.app.ui.localization.LocalStrings.current
    val context = LocalContext.current

    Scaffold(
        topBar = {
            com.lullabyai.app.ui.components.LullabyTopBar(
                title = "",
                onBack = onBack,
                navController = navController
            )
        },
        bottomBar = {
            if (navController != null) {
                com.lullabyai.app.ui.components.AppBottomNav(
                    selected = com.lullabyai.app.ui.components.BottomTab.HOME,
                    onHome = { navController.navigate(Screen.Dashboard.route) { launchSingleTop = true } },
                    onChatbot = { navController.navigate(Screen.AiChat.route) { launchSingleTop = true } },
                    onChild = { navController.navigate(Screen.ChildSummary.route) { launchSingleTop = true } },
                    onStats = { navController.navigate(Screen.StatisticsInsights.route) { launchSingleTop = true } },
                    onSettings = { navController.navigate(Screen.Settings.route) { launchSingleTop = true } },
                    onConnect = { navController.navigate(Screen.Maintenance.route) { launchSingleTop = true } }
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Box(
                    modifier = Modifier.size(96.dp).clip(CircleShape).background(Primary.copy(0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Construction, null, tint = Primary, modifier = Modifier.size(48.dp))
                }

                Text(
                    if (s.isThai) "เรากำลังปรับปรุงระบบ" else "We're Improving This Feature",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                if (s.isThai) {
                    Text("System Maintenance", style = MaterialTheme.typography.labelLarge, color = Outline)
                }

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Text(
                        if (s.isThai)
                            "ฟีเจอร์นี้กำลังอยู่ระหว่างการพัฒนาเพื่อมอบประสบการณ์ที่ดีที่สุดให้คุณ กรุณาติดตามการอัปเดตในเวอร์ชันถัดไป"
                        else
                            "This feature is currently under development to bring you the best experience. Please check back in a future update.",
                        modifier = Modifier.padding(20.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }

                Button(
                    onClick = {
                        val intent = Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse("mailto:")
                            putExtra(Intent.EXTRA_EMAIL, arrayOf(SUPPORT_EMAIL))
                            putExtra(Intent.EXTRA_SUBJECT, "NongRak AI Support")
                        }
                        runCatching { context.startActivity(intent) }
                    },
                    shape = RoundedCornerShape(100.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary)
                ) {
                    Icon(Icons.Filled.SupportAgent, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (s.isThai) "ติดต่อฝ่ายสนับสนุน" else "Contact Support", color = Color.White)
                }

                Text(
                    "NongRak AI",
                    style = MaterialTheme.typography.labelSmall,
                    color = Outline
                )
            }
        }
    }
}
