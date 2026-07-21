@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.exercise

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.screens.child.ChildScreenScaffold
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.NutritionViewModel

@Composable
fun ExerciseScreen(vm: NutritionViewModel, lang: String, navController: NavController) {
    val ui by vm.ui.collectAsState()
    val isThai = lang.startsWith("th")
    // Load the child once, and always make sure the AI activities get fetched if we don't have them
    // yet (fixes the case where the list stayed empty and the spinner never stopped).
    LaunchedEffect(lang) {
        if (ui.child == null) vm.load(lang) else vm.ensureSuggestions(lang)
    }

    val goal = vm.weeklyGoalMinutes
    val done = ui.weeklyMinutes
    val pct = if (goal > 0) (done * 100 / goal).coerceIn(0, 100) else 0
    // Names of exercises already logged today — used to show a "done" state and block re-submitting.
    val completedNames = ui.exerciseLogs.map { it.activityName }.toSet()

    ChildScreenScaffold(title = if (isThai) "ออกกำลังกาย" else "Exercise", currentTab = "exercise", navController = navController) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            item {
                Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Primary)) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(if (isThai) "เป้าหมายกิจกรรมประจำสัปดาห์" else "Weekly activity goal", color = Color.White.copy(0.9f), style = MaterialTheme.typography.labelMedium)
                        Text("$done / $goal " + (if (isThai) "นาที" else "min"), color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        LinearProgressIndicator(
                            progress = { pct / 100f },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(100.dp)),
                            color = Color.White, trackColor = Color.White.copy(0.3f)
                        )
                        Text("$pct%", color = Color.White.copy(0.9f), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            // Days-this-week-with-activity streak — a real count from ui.exerciseLogs (this week's
            // logs), not a fabricated claim. Matches the mockup's weekly-consistency card.
            item {
                val daysWithActivity = ui.exerciseLogs.map { it.logDate }.distinct().size
                Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Primary.copy(0.08f))) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.CalendarMonth, null, tint = Primary, modifier = Modifier.size(18.dp))
                            Text(if (isThai) "สถิติสัปดาห์นี้" else "This week's consistency", style = MaterialTheme.typography.labelMedium, color = Primary, fontWeight = FontWeight.Bold)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Filled.CheckCircle, null, tint = Color(0xFF2E7D32), modifier = Modifier.size(16.dp))
                            Text(
                                if (isThai) "ทำกิจกรรมแล้ว $daysWithActivity วันจาก 7 วัน" else "Active $daysWithActivity of 7 days this week",
                                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Manual log entry point — ponytail: skipped a separate "AI recommends" insight card
            // here (the mockup has one); the per-activity list below already carries AI-suggested
            // activities with tags, add a standalone insight card if that's not enough signal.
            item {
                Button(
                    onClick = { navController.navigate(Screen.LogExercise.route) },
                    modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(100.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary)
                ) {
                    Icon(Icons.Filled.AddCircle, null, tint = Color.White); Spacer(Modifier.width(8.dp))
                    Text(if (isThai) "บันทึกการออกกำลังกาย" else "Log Exercise", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }

            item {
                Text(if (isThai) "กิจกรรมแนะนำตามวัย" else "Recommended activities", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }

            if (ui.activities.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                        if (ui.activitiesLoading) {
                            CircularProgressIndicator(color = Primary, modifier = Modifier.size(26.dp), strokeWidth = 2.dp)
                        } else {
                            // Not loading and still empty → the AI returned nothing; offer a retry instead of spinning forever.
                            TextButton(onClick = { vm.ensureSuggestions(lang, force = true) }) {
                                Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(18.dp), tint = Primary)
                                Spacer(Modifier.width(6.dp))
                                Text(if (isThai) "โหลดกิจกรรมไม่สำเร็จ แตะเพื่อลองใหม่" else "Couldn't load activities — tap to retry", color = Primary)
                            }
                        }
                    }
                }
            } else {
                items(ui.activities) { act ->
                    val isDone = act.title in completedNames
                    Card(
                        // Still tappable when done — the parent can re-open the how-to guide; they just
                        // can't submit/log it a second time today (handled in the instruction screen).
                        onClick = { vm.loadExercise(act.title, lang); navController.navigate(Screen.ExerciseInstruction.route) },
                        shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.size(44.dp).clip(CircleShape).background(if (isDone) Color(0xFF4CAF50).copy(0.15f) else Primary.copy(0.12f)), contentAlignment = Alignment.Center) {
                                Icon(if (isDone) Icons.Filled.Check else Icons.Filled.FitnessCenter, null, tint = if (isDone) Color(0xFF4CAF50) else Primary, modifier = Modifier.size(22.dp))
                            }
                            Column(Modifier.weight(1f)) {
                                Text(act.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                if (act.subtitle.isNotBlank()) Text(act.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                                if (act.tags.isNotEmpty()) Text(act.tags.joinToString(" • "), style = MaterialTheme.typography.labelSmall, color = Primary)
                            }
                            if (isDone) {
                                Surface(shape = RoundedCornerShape(100.dp), color = Color(0xFF4CAF50).copy(0.15f)) {
                                    Text(if (isThai) "เสร็จแล้ว" else "Done", modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                                }
                            } else {
                                Icon(Icons.Filled.PlayCircle, null, tint = Primary)
                            }
                        }
                    }
                }
            }

            item {
                Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = SecondaryContainer.copy(0.3f))) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (isThai) "เกร็ดความรู้การออกกำลังกาย" else "Did you know?", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (isThai) "การมีกิจกรรมทางกายตั้งแต่วัยเยาว์ช่วยพัฒนาเซลล์สมอง เสริมสร้างความแข็งแรงของหัวใจ และส่งผลดีต่ออารมณ์และพฤติกรรมในอนาคต"
                            else "Physical activity from an early age supports brain cell development, strengthens the heart, and has lasting positive effects on mood and behavior.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
