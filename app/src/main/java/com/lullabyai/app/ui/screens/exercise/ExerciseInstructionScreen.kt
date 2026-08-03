@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.exercise

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.NutritionViewModel

/** First number found in a free-text duration like "10-15 นาที" — used to log a concrete
 *  duration_min when the parent marks the exercise done, since ExerciseContent.duration is a
 *  descriptive range, not a stored value. Falls back to 10 when nothing parses. */
private fun parseDurationMinutes(duration: String): Int = Regex("\\d+").find(duration)?.value?.toIntOrNull() ?: 10

@Composable
fun ExerciseInstructionScreen(vm: NutritionViewModel, lang: String, onBack: () -> Unit) {
    val ui by vm.ui.collectAsState()
    val isThai = lang.startsWith("th")
    val e = ui.exercise
    // Same "done today" check ExerciseScreen already uses for its badge — kept in sync via
    // currentExerciseName (the requested name, not the AI-generated title, per the existing convention).
    val doneToday = ui.currentExerciseName != null && ui.exerciseLogs.any { it.activityName == ui.currentExerciseName }

    Scaffold(
        topBar = {
            com.lullabyai.app.ui.components.LullabyTopBar(
                title = if (isThai) "วิธีฝึกออกกำลังกาย" else "Exercise Guide",
                onBack = onBack
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        when {
            ui.exerciseLoading -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Primary)
                }
            }
            e == null -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(if (isThai) "ไม่พบข้อมูลกิจกรรม" else "Exercise not found", color = Outline)
                }
            }
            else -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ExerciseImage(e.title)
                    Text(e.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip(Icons.Filled.Timer, if (isThai) "เตรียมตัว" else "Prep", e.prepTime)
                        Chip(Icons.Filled.HourglassBottom, if (isThai) "ระยะเวลา" else "Duration", e.duration)
                        Chip(Icons.Filled.ChildCare, if (isThai) "วัย" else "Age", e.ageRange)
                        Chip(Icons.Filled.TrendingUp, if (isThai) "ระดับ" else "Level", e.difficulty)
                    }
                    if (e.benefit.isNotBlank()) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            com.lullabyai.app.ui.screens.nutrition.SectionTitle(if (isThai) "ประโยชน์" else "Benefit")
                            Text(e.benefit, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (e.equipment.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            com.lullabyai.app.ui.screens.nutrition.SectionTitle(if (isThai) "อุปกรณ์" else "Equipment")
                            e.equipment.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        com.lullabyai.app.ui.screens.nutrition.SectionTitle(if (isThai) "ขั้นตอน" else "Steps")
                        e.steps.forEachIndexed { i, step -> com.lullabyai.app.ui.screens.nutrition.StepRow(i + 1, step) }
                    }
                    Button(
                        onClick = { vm.logExercise(ui.currentExerciseName ?: e.title, parseDurationMinutes(e.duration), lang) },
                        enabled = !doneToday,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = if (doneToday) Color(0xFF4CAF50) else Primary, disabledContainerColor = Color(0xFF4CAF50))
                    ) {
                        Text(if (doneToday) (if (isThai) "ทำสำเร็จแล้ววันนี้" else "Done for today") else (if (isThai) "เสร็จสิ้น" else "Mark done"), fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun ExerciseImage(title: String) {
    val url = remember(title) {
        "https://image.pollinations.ai/prompt/" +
            java.net.URLEncoder.encode("$title, a parent gently doing this activity with a baby, safe, bright friendly illustration", "UTF-8") +
            "?width=600&height=400&nologo=true"
    }
    Box(
        Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(Primary, Tertiary, SecondaryContainer)))
    ) {
        AsyncImage(model = url, contentDescription = title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

@Composable
private fun Chip(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.width(150.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, tint = Primary, modifier = Modifier.size(18.dp))
            Column {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
