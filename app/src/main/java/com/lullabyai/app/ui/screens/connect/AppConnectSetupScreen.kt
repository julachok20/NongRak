@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.connect

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.PhoneIphone
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.lullabyai.app.data.repository.WearablePlatform
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.Outline
import com.lullabyai.app.ui.theme.Primary
import com.lullabyai.app.ui.theme.Secondary
import com.lullabyai.app.viewmodel.WearableViewModel

private data class PlatformCard(
    val platform: String,
    val title: String,
    val descriptionTh: String,
    val descriptionEn: String,
    val icon: ImageVector,
    val kind: PlatformKind
)

private enum class PlatformKind { APPLE_SOON, IOT }

private val APP_CONNECT_CATEGORIES: List<Pair<String, List<PlatformCard>>> = listOf(
    // Google Fit, Samsung Health, Health Connect, Fitbit and Garmin were all one card each for the
    // SAME bridge: they did not talk to NongRak, they read whatever those apps wrote into Health
    // Connect. With Health Connect removed there is nothing behind them, so they are gone rather
    // than left as five buttons that connect to nothing.
    "Health apps" to listOf(
        PlatformCard(
            WearablePlatform.APPLE_HEALTH, "Apple Health",
            "ซิงค์จาก iPhone — เร็วๆ นี้ (iOS เท่านั้น)",
            "Sync from iPhone — Coming Soon (iOS only)",
            Icons.Filled.PhoneIphone, PlatformKind.APPLE_SOON
        )
    ),
    "Child devices" to listOf(
        PlatformCard(
            WearablePlatform.SMART_CRIB, "Smart Crib",
            "ติดตามอุณหภูมิห้อง / การเคลื่อนไหวของเปลอัจฉริยะ",
            "Track room temp / movement from a smart crib",
            Icons.Filled.ChildCare, PlatformKind.IOT
        ),
        PlatformCard(
            WearablePlatform.BABY_MONITOR, "Baby Monitor",
            "ติดตามชีพจร / อุณหภูมิจากมอนิเตอร์เด็ก",
            "Track heart rate / temp from a baby monitor",
            Icons.Filled.Sensors, PlatformKind.IOT
        )
    )
)

/**
 * Categorized App Connect Setup — Stitch-aligned groups:
 * Health apps · Wearables · Child IoT devices.
 * Apple Health = Coming Soon; Android platforms sync via Health Connect where possible.
 */
@Composable
fun AppConnectSetupScreen(
    navController: NavController,
    onBack: () -> Unit,
    vm: WearableViewModel = viewModel()
) {
    val s = LocalStrings.current
    val state by vm.state.collectAsState()
    val context = LocalContext.current


    LaunchedEffect(Unit) { vm.refresh() }

    val categories = APP_CONNECT_CATEGORIES

    Scaffold(
        topBar = {
            com.lullabyai.app.ui.components.LullabyTopBar(
                title = if (s.isThai) "ตั้งค่า App Connect" else "App Connect Setup",
                onBack = onBack,
                navController = navController
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            item {
                Text(
                    if (s.isThai) "เลือกแอปหรืออุปกรณ์เพื่อซิงค์ข้อมูลสุขภาพของลูกเข้าการวิเคราะห์ของ NongRak"
                    else "Choose apps or devices to sync your child's metrics into NongRak analysis.",
                    style = MaterialTheme.typography.bodyMedium, color = Outline
                )
            }

            // The "Open Health Connect" and sync buttons lived here. Health Connect was removed,
            // so there is no external app to open and no pull to trigger.

            item {
                TextButton(onClick = { navController.navigate(Screen.ChildDeviceHub.route) }) {
                    Text(if (s.isThai) "ดูแดชบอร์ดอุปกรณ์เด็ก →" else "Child device hub →")
                }
            }

            state.message?.let { msg ->
                item {
                    AssistChip(onClick = { vm.clearMessage() }, label = { Text(msg) })
                }
            }
            state.error?.let { err ->
                item {
                    Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }

            categories.forEach { (categoryEn, cards) ->
                item {
                    Text(
                        when (categoryEn) {
                            "Health apps" -> if (s.isThai) "แอปสุขภาพ" else "Health apps"
                            "Wearables" -> if (s.isThai) "นาฬิกา / สายรัดข้อมือ" else "Wearables"
                            else -> if (s.isThai) "อุปกรณ์เด็ก (IoT)" else "Child devices (IoT)"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                items(cards, key = { it.platform }) { card ->
                    val connected = state.connections.any {
                        it.platform == card.platform && it.status == "connected"
                    }
                    val comingSoon = card.kind == PlatformKind.APPLE_SOON ||
                        state.connections.any { it.platform == card.platform && it.status == "coming_soon" }

                    PlatformRow(
                        icon = card.icon,
                        title = card.title,
                        description = if (s.isThai) card.descriptionTh else card.descriptionEn,
                        statusLabel = when {
                            comingSoon -> if (s.isThai) "เร็วๆ นี้" else "Coming soon"
                            connected -> if (s.isThai) "เชื่อมต่อแล้ว" else "Connected"
                            else -> if (s.isThai) "ยังไม่เชื่อมต่อ" else "Not connected"
                        },
                        primaryLabel = when {
                            comingSoon -> if (s.isThai) "เร็วๆ นี้" else "Coming soon"
                            connected -> if (s.isThai) "ตัดการเชื่อมต่อ" else "Disconnect"
                            card.kind == PlatformKind.IOT -> if (s.isThai) "เชื่อมต่อ" else "Connect"
                            else -> if (s.isThai) "เชื่อมต่อ" else "Connect"
                        },
                        primaryEnabled = !comingSoon,
                        onPrimary = {
                            when {
                                comingSoon -> vm.markAppleComingSoon(s.isThai)
                                connected -> vm.disconnectPlatform(card.platform)
                                card.kind == PlatformKind.IOT ->
                                    vm.connectIotDevice(card.platform, card.title, s.isThai)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PlatformRow(
    icon: ImageVector,
    title: String,
    description: String,
    statusLabel: String,
    primaryLabel: String,
    primaryEnabled: Boolean,
    onPrimary: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(
            Modifier.padding(14.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(icon, null, tint = Primary, modifier = Modifier.size(32.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(description, style = MaterialTheme.typography.bodySmall, color = Outline)
                Text(statusLabel, style = MaterialTheme.typography.labelMedium, color = Secondary)
            }
            Button(
                onClick = onPrimary,
                enabled = primaryEnabled,
                shape = RoundedCornerShape(100.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary)
            ) { Text(primaryLabel, style = MaterialTheme.typography.labelLarge) }
        }
    }
}
