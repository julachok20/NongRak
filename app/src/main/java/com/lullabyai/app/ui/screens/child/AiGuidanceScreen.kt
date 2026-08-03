@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.child

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.AiGuidanceCard
import com.lullabyai.app.viewmodel.ChildViewModel

/** Route for this screen. Declared here (not in Screen.kt, which this agent does not own) so the
 *  caller and the nav graph agree on one string; fold into Screen.kt when the route is registered. */
const val AI_GUIDANCE_ROUTE = "ai_guidance"

/**
 * Stitch "AI Suggestions & Guidance" (4c56086646bb49f188130284b46bcd93) — reached from the
 * ChildSummary "ดูคำแนะนำจาก AI" suggestion button.
 *
 * Score ring + the three metric tiles are computed from data already in ChildUiState (milestones,
 * growth, sleep logs); only the narrative and the three suggestion cards come from the AI chain
 * (ChildViewModel.loadAiGuidance -> the same ClaudeClient used by every other insight in the app).
 */
@Composable
fun AiGuidanceScreen(childViewModel: ChildViewModel, navController: NavController, onBack: () -> Unit) {
    val s = com.lullabyai.app.ui.localization.LocalStrings.current
    val isThai = s.isThai
    val state by childViewModel.uiState.collectAsState()
    val child = state.child

    LaunchedEffect(child?.id) { if (child != null) childViewModel.loadAiGuidance(isThai) }

    val guidance = state.aiGuidance
    val devPercent = remember(state.milestones) {
        if (state.milestones.isEmpty()) 0 else state.milestones.count { it.isAchieved } * 100 / state.milestones.size
    }
    val ageMonths = childViewModel.getAgeMonths()
    val sleepHours = remember(state.sleepLogs) { childViewModel.avgSleepHours() }
    val weightStatus = com.lullabyai.app.util.GrowthReference.weightStatus(child?.weightKg, ageMonths)

    Scaffold(
        topBar = {
            com.lullabyai.app.ui.components.LullabyTopBar(
                title = if (isThai) "คำแนะนำจาก AI" else "AI Guidance",
                onBack = onBack,
                navController = navController,
                actions = {
                    IconButton(
                        enabled = !state.aiGuidanceLoading,
                        onClick = { childViewModel.loadAiGuidance(isThai, force = true) }
                    ) { Icon(Icons.Filled.Refresh, null, tint = Primary) }
                }
            )
        },
        bottomBar = { ChildBottomNav(navController = navController, currentTab = "child") },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            // Hero: score ring + name/age + status badge + AI headline & narrative
            Card(
                Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(84.dp)) {
                            CircularProgressIndicator(
                                progress = { devPercent / 100f }, modifier = Modifier.fillMaxSize(),
                                color = Primary, trackColor = MaterialTheme.colorScheme.surfaceVariant, strokeWidth = 8.dp
                            )
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("$devPercent", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Primary)
                                Text(if (isThai) "คะแนน" else "Score", style = MaterialTheme.typography.labelSmall, color = Outline, fontSize = 9.sp)
                            }
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                listOfNotNull(child?.name, childViewModel.calculateAge(isThai)).joinToString(", "),
                                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold
                            )
                            Surface(shape = RoundedCornerShape(100.dp), color = Primary.copy(0.1f)) {
                                Text(
                                    devHeadline(devPercent, isThai),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall, color = Primary, fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    when {
                        state.aiGuidanceLoading && guidance == null -> {
                            LinearProgressIndicator(Modifier.fillMaxWidth(), color = Primary)
                        }
                        guidance != null -> {
                            Text(guidance.headline, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            if (guidance.summary.isNotBlank()) {
                                Text(guidance.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        else -> Text(
                            if (isThai) "ยังสร้างคำแนะนำไม่ได้ ลองกดรีเฟรชอีกครั้ง"
                            else "Couldn't generate guidance — try refresh.",
                            style = MaterialTheme.typography.bodySmall, color = Outline
                        )
                    }
                }
            }

            // Three metric tiles — real data only; "-" when we genuinely have none.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricTile(
                    Icons.Filled.Bedtime, if (isThai) "การนอน" else "Sleep",
                    sleepHours?.let { String.format(java.util.Locale.US, "%.1f", it) + (if (isThai) " ชม." else " hrs") } ?: "-",
                    Modifier.weight(1f)
                )
                MetricTile(
                    Icons.Filled.Restaurant, if (isThai) "โภชนาการ" else "Nutrition",
                    (if (isThai) weightStatus?.labelTh else weightStatus?.labelEn) ?: "-",
                    Modifier.weight(1f)
                )
                MetricTile(
                    Icons.Filled.DirectionsRun, if (isThai) "กิจกรรม" else "Activity",
                    when {
                        state.milestones.isEmpty() -> "-"
                        devPercent >= 70 -> if (isThai) "สูง" else "High"
                        devPercent >= 40 -> if (isThai) "ปกติ" else "Steady"
                        else -> if (isThai) "ต้องเสริม" else "Low"
                    },
                    Modifier.weight(1f)
                )
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (isThai) "คำแนะนำประจำวันนี้" else "Today's suggestions",
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)
                )
                Icon(Icons.Filled.AutoAwesome, null, tint = Primary, modifier = Modifier.size(18.dp))
            }

            if (guidance == null && state.aiGuidanceLoading) {
                repeat(3) {
                    Card(
                        Modifier.fillMaxWidth().height(96.dp), shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Primary) } }
                }
            } else {
                guidance?.cards?.forEach { SuggestionCard(it) }
                if (guidance?.cards.isNullOrEmpty() && !state.aiGuidanceLoading) {
                    Text(
                        if (isThai) "ยังไม่มีคำแนะนำ" else "No suggestions yet",
                        style = MaterialTheme.typography.bodySmall, color = Outline
                    )
                }
            }

            guidance?.tip?.let {
                MiniInsightCard(Icons.Filled.Lightbulb, if (isThai) "เคล็ดลับวันนี้" else "Tip of the day", it, Color(0xFFFFC107))
            }
            guidance?.insight?.let {
                MiniInsightCard(Icons.Filled.Psychology, if (isThai) "ข้อสังเกตประจำสัปดาห์" else "Weekly insight", it, Secondary)
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

private fun guidanceIcon(key: String): ImageVector = when (key) {
    "nutrition" -> Icons.Filled.Restaurant
    "social" -> Icons.Filled.MenuBook
    "motor" -> Icons.Filled.FitnessCenter
    "sleep" -> Icons.Filled.Bedtime
    else -> Icons.Filled.Favorite
}

@Composable
private fun MetricTile(icon: ImageVector, label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier, shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(
            Modifier.padding(vertical = 14.dp, horizontal = 8.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, null, tint = Primary, modifier = Modifier.size(20.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = Outline, maxLines = 1)
            Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Composable
private fun SuggestionCard(card: AiGuidanceCard) {
    Card(
        Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Primary.copy(0.1f)),
                contentAlignment = Alignment.Center
            ) { Icon(guidanceIcon(card.iconKey), null, tint = Primary, modifier = Modifier.size(22.dp)) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(card.title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f, false))
                    Surface(shape = RoundedCornerShape(100.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(0.6f)) {
                        Text(
                            card.category, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(card.body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun MiniInsightCard(icon: ImageVector, label: String, text: String, tint: Color) {
    Card(
        Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = tint.copy(0.08f))
    ) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(tint.copy(0.18f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = Outline, fontWeight = FontWeight.Bold)
                Text(text, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
