@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.child

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.util.AiDailyBriefHelper
import com.lullabyai.app.util.AiDailyBriefInfo
import com.lullabyai.app.viewmodel.AiGuidanceCard
import com.lullabyai.app.viewmodel.ChildViewModel

/** Route for this screen. */
const val AI_GUIDANCE_ROUTE = "ai_guidance"

/**
 * AI Suggestions & Daily Guidance Screen.
 * Contains the comprehensive AI Daily Brief, developmental focus, actionable tips for parents,
 * sleep & routine expectations, real-time developmental metric tiles, and tailored Claude-powered insights.
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
    val weightStatus = com.lullabyai.app.util.GrowthReference.current(child, state.growthRecords).first
    val dailyBrief = remember(child?.birthdate, isThai) {
        AiDailyBriefHelper.getZeroDataAiBrief(child, isThai)
    }

    Scaffold(
        topBar = {
            com.lullabyai.app.ui.components.LullabyTopBar(
                title = if (isThai) "คำแนะนำ AI" else "AI Guidance",
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
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            // 1. Standout AI Daily Brief Banner (Primary Guidance Feature)
            AiDailyBriefSection(
                brief = dailyBrief,
                onNavigateToChat = {
                    navController.navigate(Screen.AiChat.route) {
                        launchSingleTop = true
                    }
                }
            )

            // 2. Developmental Overview Hero: Score ring + Status + Dynamic Headline
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(76.dp)) {
                            CircularProgressIndicator(
                                progress = { devPercent / 100f },
                                modifier = Modifier.fillMaxSize(),
                                color = Primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                strokeWidth = 7.dp
                            )
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("$devPercent%", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Primary)
                                Text(if (isThai) "พัฒนาการ" else "Progress", style = MaterialTheme.typography.labelSmall, color = Outline, fontSize = 9.sp)
                            }
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                listOfNotNull(child?.name, childViewModel.calculateAge(isThai)).joinToString(", "),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Surface(shape = RoundedCornerShape(100.dp), color = Primary.copy(0.1f)) {
                                Text(
                                    devHeadline(devPercent, isThai),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    if (state.aiGuidanceLoading && guidance == null) {
                        LinearProgressIndicator(Modifier.fillMaxWidth(), color = Primary)
                    } else if (guidance != null) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(guidance.headline, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            if (guidance.summary.isNotBlank()) {
                                Text(guidance.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            // 3. Three Metric Tiles
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricTile(
                    Icons.Filled.Bedtime,
                    if (isThai) "การนอน" else "Sleep",
                    sleepHours?.let { String.format(java.util.Locale.US, "%.1f", it) + (if (isThai) " ชม." else " hrs") } ?: "-",
                    Modifier.weight(1f)
                )
                MetricTile(
                    Icons.Filled.Restaurant,
                    if (isThai) "โภชนาการ" else "Nutrition",
                    (if (isThai) weightStatus?.labelTh else weightStatus?.labelEn) ?: "-",
                    Modifier.weight(1f)
                )
                MetricTile(
                    Icons.Filled.DirectionsRun,
                    if (isThai) "กิจกรรม" else "Activity",
                    when {
                        state.milestones.isEmpty() -> "-"
                        devPercent >= 70 -> if (isThai) "สูง" else "High"
                        devPercent >= 40 -> if (isThai) "ปกติ" else "Steady"
                        else -> if (isThai) "ต้องเสริม" else "Low"
                    },
                    Modifier.weight(1f)
                )
            }

            // 4. Detailed AI Recommendations / Suggestions
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    if (isThai) "คำแนะนำเฉพาะจุดจาก AI" else "Personalized Suggestions",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Icon(Icons.Filled.AutoAwesome, null, tint = Primary, modifier = Modifier.size(18.dp))
            }

            if (guidance == null && state.aiGuidanceLoading) {
                repeat(3) {
                    Card(
                        Modifier.fillMaxWidth().height(92.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Primary)
                        }
                    }
                }
            } else {
                guidance?.cards?.forEach { SuggestionCard(it) }
                if (guidance?.cards.isNullOrEmpty() && !state.aiGuidanceLoading) {
                    Card(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            Modifier.padding(20.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Filled.TipsAndUpdates, null, tint = Primary, modifier = Modifier.size(28.dp))
                            Text(
                                if (isThai) "พร้อมวิเคราะห์เมื่อมีข้อมูลบันทึกเพิ่มเติม" else "Ready to provide tailored advice as more logs arrive",
                                style = MaterialTheme.typography.bodySmall,
                                color = Outline
                            )
                        }
                    }
                }
            }

            guidance?.tip?.let {
                MiniInsightCard(Icons.Filled.Lightbulb, if (isThai) "เคล็ดลับวันนี้" else "Tip of the day", it, Color(0xFFFFA000))
            }
            guidance?.insight?.let {
                MiniInsightCard(Icons.Filled.Psychology, if (isThai) "ข้อสังเกตประจำสัปดาห์" else "Weekly insight", it, Secondary)
            }

            // 5. Ask AI Assistant CTA Card
            Card(
                onClick = {
                    navController.navigate(Screen.AiChat.route) {
                        launchSingleTop = true
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Primary.copy(alpha = 0.08f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Primary.copy(alpha = 0.25f))
            ) {
                Row(
                    Modifier.padding(16.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        Modifier.size(44.dp).clip(CircleShape).background(Primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Chat, null, tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            if (isThai) "ต้องการปรึกษา AI เพิ่มเติม?" else "Have more questions for AI?",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = Primary
                        )
                        Text(
                            if (isThai) "สอบถามเรื่องการเลี้ยงดู สุขภาพ และพัฒนาการลูกได้ตลอด 24 ชม."
                            else "Ask about parenting, health, and development 24/7",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(Icons.Filled.ChevronRight, null, tint = Primary)
                }
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun AiDailyBriefSection(brief: AiDailyBriefInfo, onNavigateToChat: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp),
        border = androidx.compose.foundation.BorderStroke(1.2.dp, Primary.copy(alpha = 0.22f))
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.AutoAwesome, null, tint = Primary, modifier = Modifier.size(22.dp))
                    }
                    Column {
                        Text(
                            text = brief.title,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Primary
                        )
                        Text(
                            text = brief.badge,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(100.dp),
                    color = Primary.copy(alpha = 0.10f)
                ) {
                    Text(
                        text = "✨ Daily Guide",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontSize = 11.sp
                    )
                }
            }

            Text(
                text = brief.headline,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Developmental Focus & Parent Action Details
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Filled.TrendingUp, null, tint = Primary, modifier = Modifier.size(18.dp).padding(top = 2.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = if (brief.isThai) "พัฒนาการเด่นในสัปดาห์นี้:" else "Key Milestones This Week:",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = brief.developmentalFocus,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Filled.Lightbulb, null, tint = Color(0xFFE65100), modifier = Modifier.size(18.dp).padding(top = 2.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = if (brief.isThai) "คำแนะนำสำหรับคุณพ่อคุณแม่:" else "Actionable Tip for Parents:",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = brief.recommendedAction,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Filled.Bedtime, null, tint = Color(0xFF3949AB), modifier = Modifier.size(18.dp).padding(top = 2.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = if (brief.isThai) "การนอนและกิจวัตร:" else "Sleep & Routine Guide:",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = brief.sleepScheduleTip,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Quick Ask AI Action
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onNavigateToChat() }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (brief.isThai) "ถามข้อสงสัยเพิ่มเติมกับ AI ผู้ช่วย →" else "Ask AI assistant more about this stage →",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Primary
                )
                Icon(Icons.Filled.ArrowForward, null, tint = Primary, modifier = Modifier.size(16.dp))
            }
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
        modifier,
        shape = RoundedCornerShape(18.dp),
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
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
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
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
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
