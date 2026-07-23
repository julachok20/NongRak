@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.child

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.navigation.NavController
import com.lullabyai.app.data.model.HealthRecord
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.components.AppBottomNav
import com.lullabyai.app.ui.components.BottomTab
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.ChildViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun AddHealthRecordScreen(
    childViewModel: ChildViewModel,
    navController: NavController,
    onBack: () -> Unit
) {
    val s = LocalStrings.current
    val today = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) }
    var date by remember { mutableStateOf(today) }
    var celsius by remember { mutableStateOf(true) }
    var temperature by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    val symptoms = if (s.isThai) listOf("ตัวร้อน", "ไอ", "มีน้ำมูก", "ผื่น", "ซึม")
                   else listOf("Fever", "Cough", "Runny nose", "Rash", "Lethargic")
    var selected by remember { mutableStateOf(setOf<String>()) }

    // A typo like "395" instead of "39.5" was previously saved as-is, silently polluting the
    // health history. Validate against a plausible human body-temperature range instead.
    val tempC = remember(temperature, celsius) {
        temperature.toDoubleOrNull()?.let { if (celsius) it else (it - 32) * 5 / 9 }
    }
    val tempOutOfRange = tempC != null && (tempC < 35.0 || tempC > 43.0)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (s.isThai) "บันทึกสุขภาพ" else "Health Record", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null, tint = Primary) }
                },
                actions = {
                    Button(
                        enabled = !tempOutOfRange,
                        onClick = {
                            // First selected symptom = main (title); the rest = additional symptoms.
                            val selectedList = selected.toList()
                            val title = selectedList.firstOrNull() ?: (if (s.isThai) "บันทึกสุขภาพ" else "Health Record")
                            childViewModel.addHealthRecord(
                                HealthRecord(
                                    eventType = "log",
                                    title = title,
                                    description = description.ifBlank { null },
                                    severity = "mild",
                                    temperature = tempC,
                                    additionalSymptoms = selectedList.drop(1).ifEmpty { null },
                                    recordedAt = date
                                )
                            )
                            onBack()
                        },
                        shape = RoundedCornerShape(100.dp),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Primary)
                    ) { Text(if (s.isThai) "บันทึก" else "Save", color = Color.White, style = MaterialTheme.typography.labelLarge) }
                    Spacer(Modifier.width(8.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = {
            AppBottomNav(
                selected = BottomTab.CHILD,
                onHome = { navController.navigate(Screen.Dashboard.route) { launchSingleTop = true } },
                onChatbot = { navController.navigate(Screen.AiChat.route) { launchSingleTop = true } },
                onChild = { navController.navigate(Screen.ChildSummary.route) { launchSingleTop = true } },
                onStats = { navController.navigate(Screen.StatisticsInsights.route) { launchSingleTop = true } },
                onSettings = { navController.navigate(Screen.Settings.route) { launchSingleTop = true } },
                onConnect = { navController.navigate(Screen.Maintenance.route) { launchSingleTop = true } }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            SectionLabel(if (s.isThai) "วันที่บันทึก" else "Record date")
            OutlinedTextField(
                value = date, onValueChange = { date = it },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Filled.CalendarToday, null, tint = Primary) },
                placeholder = { Text("YYYY-MM-DD", color = Outline) },
                singleLine = true, shape = RoundedCornerShape(12.dp),
                colors = hrFieldColors()
            )

            Spacer(Modifier.height(4.dp))
            SectionLabel((if (s.isThai) "อุณหภูมิร่างกาย" else "Body temperature") + " (°${if (celsius) "C" else "F"})")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = temperature, onValueChange = { temperature = it },
                    modifier = Modifier.weight(1f),
                    leadingIcon = { Icon(Icons.Filled.Thermostat, null, tint = Primary) },
                    placeholder = { Text(if (celsius) "36.5" else "97.7", color = Outline) },
                    singleLine = true, shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = hrFieldColors()
                )
                // °C / °F toggle
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    UnitToggle("°C", celsius) { celsius = true }
                    UnitToggle("°F", !celsius) { celsius = false }
                }
            }
            if (tempOutOfRange) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Filled.WarningAmber, null, tint = Error, modifier = Modifier.size(14.dp))
                    Text(
                        if (s.isThai) "อุณหภูมิดูผิดปกติ ตรวจสอบอีกครั้ง" else "Temperature looks wrong — check again",
                        style = MaterialTheme.typography.labelSmall, color = Error
                    )
                }
            }

            Spacer(Modifier.height(4.dp))
            SectionLabel(if (s.isThai) "อาการทั่วไป" else "Common symptoms")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                symptoms.forEach { sym ->
                    val isSel = selected.contains(sym)
                    FilterChip(
                        selected = isSel,
                        onClick = { selected = if (isSel) selected - sym else selected + sym },
                        label = { Text(sym) },
                        shape = RoundedCornerShape(100.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Primary.copy(0.12f),
                            selectedLabelColor = Primary
                        )
                    )
                }
            }

            Spacer(Modifier.height(4.dp))
            SectionLabel(if (s.isThai) "อาการหรือสิ่งที่บันทึก" else "Symptoms or notes")
            OutlinedTextField(
                value = description, onValueChange = { description = it },
                modifier = Modifier.fillMaxWidth().height(140.dp),
                placeholder = { Text(if (s.isThai) "พิมพ์รายละเอียดเพิ่มเติมที่นี่..." else "Type more details here...", color = Outline) },
                trailingIcon = {
                    Box(modifier = Modifier.size(36.dp).clip(RoundedCornerShape(100.dp)).background(Primary), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Mic, if (s.isThai) "บันทึกเสียง" else "Record voice", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                },
                shape = RoundedCornerShape(12.dp),
                colors = hrFieldColors()
            )

            Spacer(Modifier.height(4.dp))
            SectionLabel(if (s.isThai) "แนบรูปภาพ (ถ้ามี)" else "Attach photo (optional)")
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.AddAPhoto, null, tint = Outline, modifier = Modifier.size(28.dp))
                    Text(if (s.isThai) "เพิ่มรูป" else "Add photo", style = MaterialTheme.typography.labelSmall, color = Outline)
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun UnitToggle(label: String, active: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (active) MaterialTheme.colorScheme.surface else Color.Transparent,
        shadowElevation = if (active) 1.dp else 0.dp
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            color = if (active) Primary else Outline,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun hrFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
    focusedContainerColor = MaterialTheme.colorScheme.surface,
    unfocusedContainerColor = MaterialTheme.colorScheme.surface
)
