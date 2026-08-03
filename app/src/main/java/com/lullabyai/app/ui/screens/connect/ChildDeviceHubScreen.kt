@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.connect

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.lullabyai.app.data.repository.WearableMetric
import com.lullabyai.app.data.repository.WearablePlatform
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.theme.HealthRisk
import com.lullabyai.app.ui.theme.Outline
import com.lullabyai.app.ui.theme.Primary
import com.lullabyai.app.viewmodel.WearableViewModel

/**
 * Child IoT device hub — recent synced metrics + quick log for crib/monitor readings.
 * Feeds `device_readings` used by AI chat context and DeviceSyncWorker warnings.
 */
@Composable
fun ChildDeviceHubScreen(
    navController: NavController,
    onBack: () -> Unit,
    vm: WearableViewModel = viewModel()
) {
    val s = LocalStrings.current
    val state by vm.state.collectAsState()
    var roomTemp by remember { mutableStateOf("") }
    var heartRate by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { vm.refresh() }

    val iotConnected = state.connections.any {
        it.platform in setOf(WearablePlatform.SMART_CRIB, WearablePlatform.BABY_MONITOR) &&
            it.status == "connected"
    }

    Scaffold(
        topBar = {
            com.lullabyai.app.ui.components.LullabyTopBar(
                title = if (s.isThai) "อุปกรณ์เด็ก (IoT)" else "Child devices",
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
                    if (s.isThai) "ค่าจากเปลอัจฉริยะ / มอนิเตอร์เด็ก จะถูกเก็บและใช้ในการวิเคราะห์ของ AI"
                    else "Smart crib / baby monitor metrics are stored and included in AI analysis.",
                    style = MaterialTheme.typography.bodyMedium, color = Outline
                )
            }

            if (!iotConnected) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                if (s.isThai) "ยังไม่ได้เชื่อมต่ออุปกรณ์เด็ก" else "No child IoT device linked",
                                fontWeight = FontWeight.Bold
                            )
                            Button(
                                onClick = { vm.connectIotDevice(WearablePlatform.SMART_CRIB, "Smart Crib", s.isThai) },
                                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                                shape = RoundedCornerShape(100.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) { Text(if (s.isThai) "เชื่อมต่อ Smart Crib" else "Connect Smart Crib") }
                            OutlinedButton(
                                onClick = { vm.connectIotDevice(WearablePlatform.BABY_MONITOR, "Baby Monitor", s.isThai) },
                                shape = RoundedCornerShape(100.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) { Text(if (s.isThai) "เชื่อมต่อ Baby Monitor" else "Connect Baby Monitor") }
                        }
                    }
                }
            }

            item {
                Text(
                    if (s.isThai) "บันทึกค่าจากอุปกรณ์" else "Log a device reading",
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold
                )
            }
            item {
                Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = roomTemp,
                            onValueChange = { roomTemp = it.filter { c -> c.isDigit() || c == '.' } },
                            label = { Text(if (s.isThai) "อุณหภูมิห้อง (°C)" else "Room temp (°C)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = {
                                roomTemp.toDoubleOrNull()?.let {
                                    vm.logIotReading(
                                        WearableMetric.ROOM_TEMP_C, it, "celsius",
                                        WearablePlatform.SMART_CRIB, s.isThai
                                    )
                                    roomTemp = ""
                                }
                            },
                            enabled = roomTemp.toDoubleOrNull() != null,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(100.dp)
                        ) { Text(if (s.isThai) "บันทึกอุณหภูมิห้อง" else "Save room temp") }

                        OutlinedTextField(
                            value = heartRate,
                            onValueChange = { heartRate = it.filter { c -> c.isDigit() } },
                            label = { Text(if (s.isThai) "ชีพจร (bpm)" else "Heart rate (bpm)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = {
                                heartRate.toDoubleOrNull()?.let {
                                    vm.logIotReading(
                                        WearableMetric.HEART_RATE, it, "bpm",
                                        WearablePlatform.BABY_MONITOR, s.isThai
                                    )
                                    heartRate = ""
                                }
                            },
                            enabled = heartRate.toDoubleOrNull() != null,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(100.dp)
                        ) { Text(if (s.isThai) "บันทึกชีพจร" else "Save heart rate") }
                    }
                }
            }

            item {
                Text(
                    if (s.isThai) "ค่าล่าสุด" else "Recent readings",
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold
                )
            }

            if (state.readings.isEmpty()) {
                item {
                    Text(
                        if (s.isThai) "ยังไม่มีข้อมูลจากอุปกรณ์" else "No device readings yet",
                        color = Outline, style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            items(state.readings.take(30), key = { it.id }) { row ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (row.isWarning) HealthRisk.copy(alpha = 0.08f)
                        else MaterialTheme.colorScheme.surface
                    )
                ) {
                    Row(
                        Modifier.padding(12.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (row.isWarning) {
                            Icon(Icons.Filled.Warning, null, tint = HealthRisk, modifier = Modifier.size(20.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                metricLabel(row.metric, s.isThai) + ": ${formatValue(row.value, row.unit)}",
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "${row.source} · ${row.recordedAt?.take(19)?.replace('T', ' ') ?: "-"}",
                                style = MaterialTheme.typography.labelMedium, color = Outline
                            )
                            row.warningReason?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = HealthRisk)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun metricLabel(metric: String, isThai: Boolean): String = when (metric) {
    WearableMetric.HEART_RATE -> if (isThai) "ชีพจร" else "Heart rate"
    WearableMetric.SLEEP_MINUTES -> if (isThai) "การนอน" else "Sleep"
    WearableMetric.BODY_TEMP_C -> if (isThai) "อุณหภูมิร่างกาย" else "Body temp"
    WearableMetric.ROOM_TEMP_C -> if (isThai) "อุณหภูมิห้อง" else "Room temp"
    WearableMetric.SPO2 -> "SpO2"
    WearableMetric.STEPS -> if (isThai) "ก้าว" else "Steps"
    WearableMetric.MOVEMENT -> if (isThai) "การเคลื่อนไหว" else "Movement"
    else -> metric
}

private fun formatValue(value: Double, unit: String?): String = when (unit) {
    "bpm", "steps", "%" -> "${value.toInt()}${unit?.let { " $it" } ?: ""}"
    "min" -> "${(value / 60.0).let { "%.1f".format(it) }} h"
    else -> "%.1f".format(value) + (unit?.let { " $it" } ?: "")
}
