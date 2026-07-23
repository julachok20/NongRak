@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.child

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.ChildViewModel

/** #7 — Health-record detail ("ดูเพิ่มเติม"). Shows temperature, main symptom, additional-symptom
 *  chips, an editable observation note, an attached photo, and one-shot AI care advice. Edits
 *  (symptoms / notes / photo) are saved back to the record via the Save action. */
@Composable
fun HealthDetailScreen(
    childViewModel: ChildViewModel,
    navController: NavController,
    onBack: () -> Unit
) {
    val s = LocalStrings.current
    val state by childViewModel.uiState.collectAsState()
    val context = LocalContext.current
    val record = state.healthRecords.firstOrNull { it.id == state.openHealthRecordId }

    // Load AI advice + signed photo once we know which record is open.
    LaunchedEffect(record?.id) {
        record?.let { childViewModel.loadHealthDetail(it.id, s.isThai) }
    }

    // Editable copies (symptom chips + observation note).
    val symptomOptions = if (s.isThai) listOf("ตัวร้อน", "ไอ", "มีน้ำมูก", "ผื่น", "ซึม", "อาเจียน", "ท้องเสีย")
                         else listOf("Fever", "Cough", "Runny nose", "Rash", "Lethargic", "Vomiting", "Diarrhea")
    var selected by remember(record?.id) { mutableStateOf((record?.additionalSymptoms ?: emptyList()).toSet()) }
    var notes by remember(record?.id) { mutableStateOf(record?.description ?: "") }
    var tempText by remember(record?.id) { mutableStateOf(record?.temperature?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() } ?: "") }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let {
            val bytes = runCatching { context.contentResolver.openInputStream(it)?.use { st -> st.readBytes() } }.getOrNull()
            if (bytes != null && record != null) childViewModel.uploadHealthPhoto(record.id, bytes)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (s.isThai) "รายละเอียดสุขภาพ" else "Health Details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null, tint = Primary) } },
                actions = {
                    TextButton(
                        onClick = {
                            record?.let {
                                childViewModel.saveHealthRecord(
                                    it.id, it.title, notes.ifBlank { null }, selected.toList(), tempText.toDoubleOrNull(), onDone = onBack
                                )
                            }
                        },
                        enabled = record != null && !state.healthDetailSaving
                    ) {
                        if (state.healthDetailSaving) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Primary)
                        else Text(if (s.isThai) "บันทึก" else "Save", color = Primary, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = {
            com.lullabyai.app.ui.components.AppBottomNav(
                selected = com.lullabyai.app.ui.components.BottomTab.CHILD,
                onHome = { navController.navigate(com.lullabyai.app.ui.navigation.Screen.Dashboard.route) { launchSingleTop = true } },
                onChatbot = { navController.navigate(com.lullabyai.app.ui.navigation.Screen.AiChat.route) { launchSingleTop = true } },
                onChild = { navController.navigate(com.lullabyai.app.ui.navigation.Screen.ChildSummary.route) { launchSingleTop = true } },
                onStats = { navController.navigate(com.lullabyai.app.ui.navigation.Screen.StatisticsInsights.route) { launchSingleTop = true } },
                onSettings = { navController.navigate(com.lullabyai.app.ui.navigation.Screen.Settings.route) { launchSingleTop = true } },
                onConnect = { navController.navigate(com.lullabyai.app.ui.navigation.Screen.Maintenance.route) { launchSingleTop = true } }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (record == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(if (s.isThai) "ไม่พบบันทึก" else "Record not found", color = Outline)
            }
            return@Scaffold
        }

        val temp = tempText.toDoubleOrNull()
        val (feverLabel, feverColor) = feverStatus(temp, s.isThai)
        val recordTime = com.lullabyai.app.util.TimeUtils.localTime(record.recordedAt)

        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            // Date + saved badge
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.CalendarToday, null, tint = Outline, modifier = Modifier.size(16.dp))
                    Text(formatHealthDate(com.lullabyai.app.util.TimeUtils.localDate(record.recordedAt), s.isThai), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(shape = RoundedCornerShape(100.dp), color = Primary.copy(0.1f)) {
                    Text(if (s.isThai) "บันทึกแล้ว" else "Saved", modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium, color = Primary, fontWeight = FontWeight.Bold)
                }
            }

            // Recovered/monitoring toggle — feeds the status badge on HealthHistoryScreen's timeline.
            val recovered = record.status == "recovered"
            Surface(
                onClick = { childViewModel.toggleHealthRecordStatus(record.id) },
                shape = RoundedCornerShape(100.dp),
                color = if (recovered) Color(0xFF2E7D32).copy(0.1f) else Color(0xFFED6C02).copy(0.1f)
            ) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        if (recovered) Icons.Filled.CheckCircle else Icons.Filled.Visibility, null,
                        tint = if (recovered) Color(0xFF2E7D32) else Color(0xFFED6C02), modifier = Modifier.size(18.dp)
                    )
                    Text(
                        if (recovered) (if (s.isThai) "หายแล้ว — แตะเพื่อทำเครื่องหมายว่ากำลังติดตาม" else "Recovered — tap to mark as monitoring")
                        else (if (s.isThai) "กำลังติดตาม — แตะเพื่อทำเครื่องหมายว่าหายแล้ว" else "Monitoring — tap to mark as recovered"),
                        style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                        color = if (recovered) Color(0xFF2E7D32) else Color(0xFFED6C02)
                    )
                }
            }

            // Temperature card
            Card(
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(48.dp).clip(CircleShape).background(Primary.copy(0.1f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Thermostat, null, tint = Primary, modifier = Modifier.size(26.dp))
                    }
                    Text(if (s.isThai) "อุณหภูมิร่างกาย" else "Body temperature", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    // Editable temperature: big centered number + °C, with a thin underline to signal input.
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            BasicTextField(
                                value = tempText,
                                onValueChange = { v -> tempText = v.filter { it.isDigit() || it == '.' }.take(5) },
                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 44.sp, fontWeight = FontWeight.Bold, color = feverColor, textAlign = TextAlign.Center),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                cursorBrush = androidx.compose.ui.graphics.SolidColor(Primary),
                                modifier = Modifier.widthIn(min = 60.dp, max = 150.dp),
                                decorationBox = { inner ->
                                    Box(contentAlignment = Alignment.Center) {
                                        if (tempText.isEmpty()) Text("36.5", fontSize = 44.sp, fontWeight = FontWeight.Bold, color = Outline)
                                        inner()
                                    }
                                }
                            )
                            HorizontalDivider(modifier = Modifier.width(120.dp).padding(top = 2.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        }
                        Text("°C", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = feverColor, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
                    }
                    Text(feverLabel, style = MaterialTheme.typography.bodyMedium, color = feverColor, fontWeight = FontWeight.SemiBold)
                }
            }

            // Main symptom card
            Card(
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(Error.copy(0.1f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.MonitorHeart, null, tint = Error, modifier = Modifier.size(22.dp))
                    }
                    Text(if (s.isThai) "อาการหลัก" else "Main symptom", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(record.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    if (recordTime != null) Text((if (s.isThai) "อัปเดตล่าสุด: " else "Last updated: ") + recordTime + (if (s.isThai) " น." else ""), style = MaterialTheme.typography.labelSmall, color = Outline)
                }
            }

            // Additional symptoms (editable chips)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (s.isThai) "อาการที่พบเพิ่มเติม" else "Additional symptoms", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    (symptomOptions + selected.filter { it !in symptomOptions }).forEach { sym ->
                        val isSel = selected.contains(sym)
                        FilterChip(
                            selected = isSel,
                            onClick = { selected = if (isSel) selected - sym else selected + sym },
                            label = { Text(sym) },
                            leadingIcon = if (isSel) { { Icon(Icons.Filled.Check, null, modifier = Modifier.size(16.dp)) } } else null,
                            shape = RoundedCornerShape(100.dp),
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Primary.copy(0.12f), selectedLabelColor = Primary, selectedLeadingIconColor = Primary)
                        )
                    }
                }
            }

            // Observation note (editable)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (s.isThai) "บันทึก / การสังเกตอาการ" else "Notes / Observations", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = notes, onValueChange = { notes = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
                    placeholder = { Text(if (s.isThai) "บันทึกการสังเกตอาการของลูก..." else "Note what you observed...", color = Outline) },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Primary, unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedContainerColor = MaterialTheme.colorScheme.surface, unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }

            // Photo
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (s.isThai) "รูปภาพประกอบ" else "Photo", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val photo = state.healthPhotoSignedUrl
                    if (!photo.isNullOrBlank()) {
                        AsyncImage(model = photo, contentDescription = null, modifier = Modifier.weight(1f).height(120.dp).clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Crop)
                    }
                    Box(
                        modifier = Modifier.weight(1f).height(120.dp).clip(RoundedCornerShape(14.dp))
                            .border(BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant), RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .clickable(enabled = !state.healthPhotoUploading) { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (state.healthPhotoUploading) {
                            CircularProgressIndicator(color = Primary, modifier = Modifier.size(28.dp))
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(Modifier.size(40.dp).clip(CircleShape).background(Primary.copy(0.1f)), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Filled.AddAPhoto, null, tint = Primary, modifier = Modifier.size(22.dp))
                                }
                                Text(if (s.isThai) "เพิ่มรูปภาพ" else "Add photo", style = MaterialTheme.typography.labelMedium, color = Primary)
                            }
                        }
                    }
                }
            }

            // AI advice — Coming Soon
            Card(
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.5f))
            ) {
                Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.size(30.dp).clip(RoundedCornerShape(10.dp)).background(Outline.copy(0.3f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.AutoAwesome, null, tint = Outline, modifier = Modifier.size(18.dp))
                        }
                        Text(if (s.isThai) "คำแนะนำจาก Lullaby AI" else "Advice from Lullaby AI", style = MaterialTheme.typography.titleSmall, color = Outline, fontWeight = FontWeight.Bold)
                    }
                    Surface(shape = RoundedCornerShape(100.dp), color = Outline.copy(0.12f)) {
                        Text(if (s.isThai) "เร็วๆ นี้" else "Coming Soon", modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium, color = Outline, fontWeight = FontWeight.Bold)
                    }
                    Text(if (s.isThai) "ฟีเจอร์นี้กำลังพัฒนา" else "This feature is coming soon", style = MaterialTheme.typography.bodySmall, color = Outline)
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Returns a Thai/EN fever label + tint for a temperature in °C. */
private fun feverStatus(temp: Double?, isThai: Boolean): Pair<String, Color> = when {
    temp == null -> (if (isThai) "ไม่ได้บันทึก" else "Not recorded") to Outline
    temp < 37.5 -> (if (isThai) "ปกติ" else "Normal") to Color(0xFF2E7D32)
    temp <= 38.0 -> (if (isThai) "มีไข้ต่ำ" else "Low fever") to Color(0xFFEF6C00)
    temp <= 39.0 -> (if (isThai) "มีไข้" else "Fever") to Color(0xFFEF6C00)
    else -> (if (isThai) "ไข้สูง" else "High fever") to Error
}

/** "12 มีนาคม 2567" (Buddhist year) / "12 March 2026" from an ISO date/timestamp string.
 *  Named distinctly from ChildSummaryScreen's internal formatDate, and handles full timestamps. */
private fun formatHealthDate(iso: String?, isThai: Boolean): String {
    if (iso.isNullOrBlank() || iso.length < 10) return iso ?: ""
    val y = iso.substring(0, 4).toIntOrNull() ?: return iso
    val m = iso.substring(5, 7).toIntOrNull() ?: return iso
    val d = iso.substring(8, 10).toIntOrNull() ?: return iso
    val monthsTh = listOf("มกราคม", "กุมภาพันธ์", "มีนาคม", "เมษายน", "พฤษภาคม", "มิถุนายน", "กรกฎาคม", "สิงหาคม", "กันยายน", "ตุลาคม", "พฤศจิกายน", "ธันวาคม")
    val monthsEn = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
    val name = (if (isThai) monthsTh else monthsEn).getOrNull(m - 1) ?: return iso
    return if (isThai) "$d $name ${y + 543}" else "$d $name $y"
}

