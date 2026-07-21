@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.exercise

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.NutritionViewModel
import java.time.LocalDate

private val MOODS = listOf("tired" to ("เหนื่อย" to "Tired"), "happy" to ("ร่าเริง" to "Happy"), "energized" to ("ตื่นตัว" to "Energized"))
private fun moodIcon(key: String) = when (key) { "tired" -> Icons.Filled.SentimentDissatisfied; "energized" -> Icons.Filled.Bolt; else -> Icons.Filled.SentimentSatisfied }

/** "บันทึกการออกกำลังกาย" — manual exercise log, matches Child-Project/Update/35.Exercise/record_exercise.
 *  Distinct from the AI-suggested-activity flow (ExerciseInstructionScreen's "mark done"): this is for
 *  logging an activity the parent did on their own, any day, with an optional mood/notes reflection. */
@Composable
fun LogExerciseScreen(vm: NutritionViewModel, lang: String, navController: NavController, onBack: () -> Unit) {
    val ui by vm.ui.collectAsState()
    val isThai = lang.startsWith("th")

    var date by remember { mutableStateOf(LocalDate.now()) }
    var activityName by remember { mutableStateOf("") }
    var customQuery by remember { mutableStateOf("") }
    var durationMin by remember { mutableStateOf(15) }
    var mood by remember { mutableStateOf<String?>("happy") }
    var notes by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }

    // Reuse whatever AI-suggested activities are already loaded as the quick-pick chips; a few
    // generic fallbacks if none have loaded yet, so the form is never empty on first open.
    val quickPicks = (ui.activities.map { it.title }.take(3).ifEmpty {
        listOf(if (isThai) "คลาน (Crawling)" else "Crawling", if (isThai) "เดิน (Walking)" else "Walking", "Tummy Time")
    })

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isThai) "บันทึกการออกกำลังกาย" else "Log Exercise", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null, tint = Primary) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SectionCard(if (isThai) "วันที่ทำกิจกรรม" else "Activity date") {
                Text(
                    "%02d/%02d/%04d".format(date.monthValue, date.dayOfMonth, date.year),
                    style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold
                )
                // Simple prev/next-day steppers — no calendar dialog needed for a same-week log.
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { date = date.minusDays(1) }, shape = RoundedCornerShape(100.dp)) { Text(if (isThai) "ก่อนหน้า" else "Previous day") }
                    OutlinedButton(onClick = { if (date.isBefore(LocalDate.now())) date = date.plusDays(1) }, shape = RoundedCornerShape(100.dp)) { Text(if (isThai) "ถัดไป" else "Next day") }
                }
            }

            SectionCard(if (isThai) "ประเภทการออกกำลังกาย" else "Activity type") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    quickPicks.forEach { name ->
                        ChoiceRow(name, activityName == name) { activityName = name; customQuery = "" }
                    }
                    OutlinedTextField(
                        value = customQuery,
                        onValueChange = { customQuery = it; activityName = it },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        placeholder = { Text(if (isThai) "ค้นหาหรือระบุประเภทอื่น..." else "Search or enter another type...", color = Outline) },
                        leadingIcon = { Icon(Icons.Filled.Search, null, tint = Outline) },
                        shape = RoundedCornerShape(12.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary)
                    )
                }
            }

            SectionCard(if (isThai) "ระยะเวลา (นาที)" else "Duration (minutes)") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(Modifier.weight(1f), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(0.5f)) {
                        Text(
                            "$durationMin", modifier = Modifier.padding(vertical = 14.dp).fillMaxWidth(),
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(onClick = { durationMin = (durationMin + 5).coerceAtMost(180) }, modifier = Modifier.size(32.dp)) { Icon(Icons.Filled.KeyboardArrowUp, null, tint = Primary) }
                        IconButton(onClick = { durationMin = (durationMin - 5).coerceAtLeast(5) }, modifier = Modifier.size(32.dp)) { Icon(Icons.Filled.KeyboardArrowDown, null, tint = Primary) }
                    }
                }
            }

            SectionCard(if (isThai) "ความรู้สึกหลังทำ" else "Feeling after") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    MOODS.forEach { (key, label) ->
                        val selected = mood == key
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {
                                mood = if (selected) null else key
                            }
                        ) {
                            Box(Modifier.size(44.dp).background(if (selected) Primary.copy(0.12f) else Color.Transparent, RoundedCornerShape(100.dp)), contentAlignment = Alignment.Center) {
                                Icon(moodIcon(key), null, tint = if (selected) Primary else Outline, modifier = Modifier.size(24.dp))
                            }
                            Text(if (isThai) label.first else label.second, style = MaterialTheme.typography.labelSmall, color = if (selected) Primary else Outline)
                        }
                    }
                }
            }

            SectionCard(if (isThai) "บันทึกเพิ่มเติมหรือสิ่งที่สังเกตเห็น" else "Notes or things you noticed") {
                OutlinedTextField(
                    value = notes, onValueChange = { notes = it },
                    modifier = Modifier.fillMaxWidth().height(90.dp),
                    placeholder = { Text(if (isThai) "เช่น ลูกยกหัวได้นานขึ้น, เริ่มส่งเสียงอ้อแอ้ระหว่างเล่น..." else "e.g. Held their head up longer, started babbling while playing...", color = Outline) },
                    shape = RoundedCornerShape(12.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary)
                )
            }

            Button(
                onClick = {
                    if (activityName.isNotBlank() && !saving) {
                        saving = true
                        vm.logExercise(activityName.trim(), durationMin, lang, date.toString(), mood, notes.ifBlank { null })
                        navController.popBackStack()
                    }
                },
                enabled = activityName.isNotBlank() && !saving,
                modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(100.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary)
            ) {
                Icon(Icons.Filled.Save, null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (isThai) "บันทึกข้อมูล" else "Save entry", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Primary)
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(100.dp),
        color = if (selected) Primary.copy(0.1f) else Color.Transparent,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) Primary else MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = if (selected) Primary else MaterialTheme.colorScheme.onSurface)
        }
    }
}
