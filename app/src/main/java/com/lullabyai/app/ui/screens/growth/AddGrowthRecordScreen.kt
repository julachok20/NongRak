@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.growth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.lullabyai.app.data.model.GrowthRecord
import com.lullabyai.app.ui.components.LullabyTopBar
import com.lullabyai.app.ui.components.formatBirthDateDisplay
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.ChildViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Standalone "add a growth record" form — Stitch: Update Child Growth Info.
 *  Weight/height + calendar date picker, child avatar header, AI measurement tip. */
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
        topBar = { LullabyTopBar(title = if (isThai) "อัปเดตการเติบโต" else "Update growth", onBack = onBack, navController = navController) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            // Child profile card with avatar (Stitch)
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(contentAlignment = Alignment.BottomEnd) {
                        Box(
                            Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)).background(Primary.copy(0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            val avatar = childState.childAvatarSignedUrl
                            if (!avatar.isNullOrBlank()) {
                                AsyncImage(
                                    model = avatar,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Text("👶", fontSize = 28.sp)
                            }
                        }
                        Box(
                            Modifier.size(22.dp).clip(CircleShape).background(Color(0xFF2E7D32))
                                .padding(2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(12.dp))
                        }
                    }
                    Column {
                        Text(
                            child?.name ?: (if (isThai) "ลูกของคุณ" else "Your child"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            (if (isThai) "อายุ " else "Age ") + childViewModel.calculateAge(isThai),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            OutlinedTextField(
                value = weightText, onValueChange = { weightText = it },
                label = { Text(if (isThai) "น้ำหนัก (กก.)" else "Weight (kg)") },
                placeholder = { Text("0.0") },
                trailingIcon = {
                    Text(
                        if (isThai) "กิโลกรัม" else "kg",
                        style = MaterialTheme.typography.labelMedium,
                        color = Primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                },
                modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(16.dp),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal)
            )
            OutlinedTextField(
                value = heightText, onValueChange = { heightText = it },
                label = { Text(if (isThai) "ส่วนสูง (ซม.)" else "Height (cm)") },
                placeholder = { Text("0.0") },
                trailingIcon = {
                    Text(
                        if (isThai) "เซนติเมตร" else "cm",
                        style = MaterialTheme.typography.labelMedium,
                        color = Primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                },
                modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(16.dp),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal)
            )

            GrowthRecordDatePickerField(
                value = dateText,
                onValueChange = { dateText = it },
                minIso = child?.birthdate?.take(10),
                isThai = isThai,
                label = if (isThai) "วันที่บันทึก" else "Date recorded",
                placeholder = if (isThai) "แตะเพื่อเลือกวันที่จากปฏิทิน" else "Tap to pick a date"
            )

            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Primary.copy(0.08f))
            ) {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            if (isThai) "คำแนะนำจาก AI" else "AI tip",
                            style = MaterialTheme.typography.labelSmall,
                            color = Primary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            if (isThai) "การวัดส่วนสูงที่แม่นยำควรให้ลูกยืนตัวตรง ส้นเท้าชิดผนัง สายตามองตรงไปข้างหน้า และไม่สวมรองเท้า"
                            else "For an accurate height measurement, have your child stand straight with heels against the wall, looking forward, without shoes",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            error?.let { Text(it, color = Error, style = MaterialTheme.typography.bodySmall) }

            Spacer(Modifier.height(8.dp))

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
                        error = if (isThai) "กรุณาเลือกวันที่จากปฏิทิน" else "Please pick a date from the calendar"
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

/** Calendar date picker for growth records — range = birthdate (or 4y ago) … today. */
@Composable
private fun GrowthRecordDatePickerField(
    value: String,
    onValueChange: (String) -> Unit,
    minIso: String?,
    isThai: Boolean,
    label: String,
    placeholder: String
) {
    val zone = remember { ZoneId.systemDefault() }
    val today = remember { LocalDate.now() }
    val maxMillis = remember { today.atStartOfDay(zone).toInstant().toEpochMilli() }
    val minMillis = remember(minIso) {
        val minDate = runCatching { LocalDate.parse(minIso) }.getOrNull()
            ?: today.minusYears(4)
        minDate.atStartOfDay(zone).toInstant().toEpochMilli().coerceAtMost(maxMillis)
    }
    val initialMillis = remember(value) {
        runCatching { LocalDate.parse(value).atStartOfDay(zone).toInstant().toEpochMilli() }
            .getOrNull() ?: maxMillis
    }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialMillis,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                utcTimeMillis in minMillis..maxMillis
        }
    )
    var showPicker by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = if (value.isNotBlank()) formatBirthDateDisplay(value, isThai) else "",
            onValueChange = {},
            readOnly = true,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(interactionSource = interactionSource, indication = null) { showPicker = true },
            placeholder = { Text(placeholder, color = Outline) },
            trailingIcon = {
                IconButton(onClick = { showPicker = true }) {
                    Icon(Icons.Filled.CalendarToday, contentDescription = label, tint = Outline)
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp)
        )
    }

    if (showPicker) {
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { ms ->
                        onValueChange(Instant.ofEpochMilli(ms).atZone(zone).toLocalDate().toString())
                    }
                    showPicker = false
                }) { Text(if (isThai) "ตกลง" else "OK") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) {
                    Text(if (isThai) "ยกเลิก" else "Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
