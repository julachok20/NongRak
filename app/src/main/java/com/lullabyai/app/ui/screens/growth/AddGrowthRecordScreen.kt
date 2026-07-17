@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.growth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.lullabyai.app.data.model.GrowthRecord
import com.lullabyai.app.ui.components.LullabyTopBar
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.ChildViewModel
import java.time.LocalDate

/** Standalone "add a growth record" form (mockup: update_child_growth_info) — weight, height,
 *  and date, saved via the existing ChildViewModel.addGrowthRecord. Replaces the old flow where
 *  "Add data" on GrowthHistory routed to the GrowthMetrics health-status screen instead of an
 *  actual entry form. */
@Composable
fun AddGrowthRecordScreen(childViewModel: ChildViewModel, navController: NavController, onBack: () -> Unit) {
    val s = com.lullabyai.app.ui.localization.LocalStrings.current
    val isThai = s.isThai
    val childState by childViewModel.uiState.collectAsState()
    val child = childState.child

    var weightText by remember { mutableStateOf("") }
    var heightText by remember { mutableStateOf("") }
    var dateText by remember { mutableStateOf(LocalDate.now().toString()) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = { LullabyTopBar(title = if (isThai) "อัปเดตการเติบโต" else "Update growth", onBack = onBack) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(child?.name ?: (if (isThai) "ลูกของคุณ" else "Your child"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }

            OutlinedTextField(
                value = weightText, onValueChange = { weightText = it },
                label = { Text(if (isThai) "น้ำหนัก (กก.)" else "Weight (kg)") },
                placeholder = { Text("0.0") },
                modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(16.dp),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal)
            )
            OutlinedTextField(
                value = heightText, onValueChange = { heightText = it },
                label = { Text(if (isThai) "ส่วนสูง (ซม.)" else "Height (cm)") },
                placeholder = { Text("0.0") },
                modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(16.dp),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal)
            )
            OutlinedTextField(
                value = dateText, onValueChange = { dateText = it },
                label = { Text(if (isThai) "วันที่บันทึก" else "Date recorded") },
                placeholder = { Text("YYYY-MM-DD") },
                modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(16.dp)
            )

            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Primary.copy(0.06f))) {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Filled.AutoAwesome, null, tint = Primary, modifier = Modifier.size(20.dp))
                    Text(
                        if (isThai) "การวัดส่วนสูงที่แม่นยำควรให้ลูกยืนตัวตรง ส้นเท้าชิดผนัง สายตามองตรงไปข้างหน้า และไม่สวมรองเท้า"
                        else "For an accurate height measurement, have your child stand straight with heels against the wall, looking forward, without shoes",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            error?.let { Text(it, color = Error, style = MaterialTheme.typography.bodySmall) }

            Spacer(Modifier.weight(1f))

            Button(
                onClick = {
                    val weight = weightText.toDoubleOrNull()
                    val height = heightText.toDoubleOrNull()
                    if (weight == null && height == null) {
                        error = if (isThai) "กรุณากรอกน้ำหนักหรือส่วนสูงอย่างน้อยหนึ่งค่า" else "Enter at least weight or height"
                        return@Button
                    }
                    val recordedAt = runCatching { LocalDate.parse(dateText) }.getOrNull()
                    if (recordedAt == null) {
                        error = if (isThai) "รูปแบบวันที่ไม่ถูกต้อง (YYYY-MM-DD)" else "Invalid date format (YYYY-MM-DD)"
                        return@Button
                    }
                    error = null
                    saving = true
                    childViewModel.addGrowthRecord(
                        GrowthRecord(weightKg = weight, heightCm = height, recordedAt = recordedAt.toString(), recordType = "manual")
                    )
                    onBack()
                },
                modifier = Modifier.fillMaxWidth().height(56.dp).padding(bottom = 16.dp),
                shape = RoundedCornerShape(16.dp),
                enabled = !saving,
                colors = ButtonDefaults.buttonColors(containerColor = Primary)
            ) {
                Icon(Icons.Filled.Save, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (isThai) "บันทึกข้อมูล" else "Save", fontWeight = FontWeight.Bold)
            }
        }
    }
}
