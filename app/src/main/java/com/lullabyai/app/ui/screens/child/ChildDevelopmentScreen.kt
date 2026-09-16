@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.child

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.lullabyai.app.data.model.DevelopmentMilestone
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.ChildViewModel

internal fun catLabel(key: String, isThai: Boolean) = when (key) {
    "gross_motor" -> if (isThai) "กล้ามเนื้อมัดใหญ่" else "Gross motor"
    "fine_motor" -> if (isThai) "กล้ามเนื้อมัดเล็ก" else "Fine motor"
    "communication" -> if (isThai) "การสื่อสาร" else "Communication"
    "self_help" -> if (isThai) "ช่วยเหลือตัวเอง" else "Self-help"
    "social" -> if (isThai) "สังคม" else "Social"
    else -> key
}

internal fun skipReasons(isThai: Boolean) =
    if (isThai) listOf("ยังไม่ถึงวัย", "น้องยังไม่พร้อม", "อื่นๆ")
    else listOf("Not old enough yet", "Not ready yet", "Other")

@Composable
fun ChildDevelopmentScreen(
    childViewModel: ChildViewModel,
    navController: NavController,
    onBack: () -> Unit
) {
    val s = com.lullabyai.app.ui.localization.LocalStrings.current
    val childState by childViewModel.uiState.collectAsState()
    val child = childState.child
    val milestones = childState.milestones
    val ageMonths = childViewModel.getAgeMonths()

    var selectedCategory by remember { mutableStateOf<String?>(null) }   // null = ทั้งหมด
    var expandedSkipId by remember { mutableStateOf<String?>(null) }     // which row's skip menu is open

    val categories = remember(milestones) { milestones.map { it.category }.distinct() }
    // Recommended handbook skills (CDC/DSPM) = age-appropriate skills the child hasn't achieved or
    // skipped yet. The shown subset REFRESHES EVERY 8 HOURS — when the pool has more than we display,
    // it rotates to a different batch each 8h window. When the child has done them all, we show a
    // "finished" card instead of the list. The full checklist stays on "ดูทั้งหมด".
    val recommendedPool = milestones
        .filter { selectedCategory == null || it.category == selectedCategory }
        .filter { !it.isAchieved && it.skipReason == null && (it.ageMonthsExpected ?: 0) <= ageMonths }
        .sortedWith(compareBy({ it.ageMonthsExpected ?: 0 }, { it.id }))
    val shownCount = 8
    val epoch8h = System.currentTimeMillis() / (8L * 60 * 60 * 1000)   // changes once every 8 hours
    val shown = if (recommendedPool.size <= shownCount) recommendedPool else {
        val offset = (epoch8h % recommendedPool.size).toInt()
        (recommendedPool.drop(offset) + recommendedPool.take(offset)).take(shownCount)
    }
    // "Finished" only when the child actually HAS age-appropriate skills in view and has cleared them all.
    val hasAgeAppropriate = milestones.any {
        (selectedCategory == null || it.category == selectedCategory) && (it.ageMonthsExpected ?: 0) <= ageMonths
    }
    val allRecommendedDone = hasAgeAppropriate && recommendedPool.isEmpty()
    val achieved = milestones.count { it.isAchieved }
    val total = milestones.size.coerceAtLeast(1)
    val percent = (achieved * 100 / total)

    LaunchedEffect(child?.id) {
        child?.let { childViewModel.loadTasks(it) }
        // Backfill the full 0–3y milestone checklist for children seeded with the old sparse list.
        childViewModel.ensureDefaultMilestones()
    }

    ChildScreenScaffold(title = if (s.isThai) "พัฒนาการ" else "Development", currentTab = "development", navController = navController) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            // Child profile mini-card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(1.dp)
                ) {
                    Row(modifier = Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(56.dp).clip(CircleShape).background(SecondaryContainer), contentAlignment = Alignment.Center) {
                            val avatarUrl = childState.childAvatarSignedUrl
                            if (!avatarUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = avatarUrl, contentDescription = if (s.isThai) "รูปลูก" else "Child photo",
                                    modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop
                                )
                            } else {
                                Text("👶", fontSize = 26.sp)
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(child?.name ?: "", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("${childViewModel.calculateAge(s.isThai)} ($ageMonths " + (if (s.isThai) "เดือน" else "mo") + ")", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Surface(shape = RoundedCornerShape(100.dp), color = MaterialTheme.colorScheme.primary.copy(0.1f)) {
                            Text(if (s.isThai) "ปกติ" else "Normal", modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Filter chips
            item {
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CategoryChip(label = if (s.isThai) "ทั้งหมด" else "All", selected = selectedCategory == null) { selectedCategory = null }
                    categories.forEach { cat ->
                        CategoryChip(label = catLabel(cat, s.isThai), selected = selectedCategory == cat) { selectedCategory = cat }
                    }
                }
            }

            // Overview card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(1.dp)
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text(if (s.isThai) "ภาพรวมพัฒนาการ" else "Development overview", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(if (s.isThai) "อัปเดตล่าสุด: วันนี้" else "Last updated: today", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            // Circular percent
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(80.dp)) {
                                CircularProgressIndicator(
                                    progress = { percent / 100f },
                                    modifier = Modifier.fillMaxSize(),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                    strokeWidth = 8.dp
                                )
                                Text("$percent%", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(if (s.isThai) "แนวโน้มการเรียนรู้" else "Learning trend", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                listOf(0.35f to 0.25f, 0.6f to 0.45f, 0.5f to 0.6f, 0.8f to 0.8f, 1f to 1f).forEach { (alpha, h) ->
                                    Box(modifier = Modifier.width(8.dp).height((34 * h).dp).clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha)))
                                }
                            }
                        }
                    }
                }
            }

            // Skills header
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text((if (s.isThai) "ทักษะประจำวัย ($ageMonths เดือน)" else "Age skills ($ageMonths mo)"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { navController.navigate(Screen.DevelopmentHistory.route) }, contentPadding = PaddingValues(0.dp)) {
                        Text(if (s.isThai) "ดูทั้งหมด" else "View all", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            // Milestone skill rows — finished card when the child cleared all age-appropriate skills.
            if (allRecommendedDone) {
                item { FinishedSkillsCard(s.isThai) }
            } else if (shown.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                        Text(if (s.isThai) "ยังไม่ถึงวัยสำหรับทักษะในหมวดนี้" else "No age-appropriate skills here yet", color = MaterialTheme.colorScheme.outline, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            } else {
                items(shown, key = { it.id }) { m ->
                    SkillRow(
                        milestone = m,
                        expanded = expandedSkipId == m.id,
                        notYetAge = (m.ageMonthsExpected ?: 0) > ageMonths,
                        onToggleAchieved = {
                            childViewModel.setMilestoneAchieved(m.id, !m.isAchieved)
                            expandedSkipId = null
                        },
                        onOpenSkip = { expandedSkipId = if (expandedSkipId == m.id) null else m.id },
                        onConfirmSkip = { reason ->
                            childViewModel.setMilestoneSkipReason(m.id, reason)
                            expandedSkipId = null
                        }
                    )
                }
            }

            // AI daily activities (max 7; completed reset after 10h)
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(if (s.isThai) "กิจกรรมแนะนำวันนี้" else "Today's activities", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { navController.navigate(Screen.TaskHistory.route) }, contentPadding = PaddingValues(0.dp)) {
                        Text(if (s.isThai) "ดูประวัติ" else "History", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            if (childState.aiTasks.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().height(56.dp), contentAlignment = Alignment.Center) {
                        if (childState.tasksLoading) CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        else Text(if (s.isThai) "ยังไม่มีกิจกรรม" else "No activities yet", color = MaterialTheme.colorScheme.outline, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            } else {
                items(childState.aiTasks.take(5), key = { it.id }) { task ->
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(
                                modifier = Modifier.size(36.dp).clip(CircleShape)
                                    .background(if (task.isCompleted) MaterialTheme.colorScheme.primary.copy(0.1f) else MaterialTheme.colorScheme.surfaceVariant)
                                    .selectable(selected = task.isCompleted, onClick = { childViewModel.toggleTask(task.id, task.isCompleted) }),
                                contentAlignment = Alignment.Center
                            ) {
                                if (task.isCompleted) Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                                else Icon(Icons.Filled.RadioButtonUnchecked, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(22.dp))
                            }
                            Text(task.taskText, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = if (task.isCompleted) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface)
                            IconButton(onClick = { childViewModel.notAvailableTask(task.id) }, modifier = Modifier.size(34.dp)) {
                                Icon(Icons.Filled.Close, if (s.isThai) "ไม่สะดวก" else "Not available", tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }

            // Activity suggestion card — gradient art background + personalized text
            item {
                val childName = child?.name?.takeIf { it.isNotBlank() }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Primary, Tertiary, SecondaryContainer)
                            )
                        )
                ) {
                    // Decorative translucent circles ("art")
                    Box(
                        modifier = Modifier
                            .size(160.dp)
                            .align(Alignment.CenterEnd)
                            .offset(x = 60.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(0.10f))
                    )
                    Box(
                        modifier = Modifier
                            .size(90.dp)
                            .align(Alignment.BottomEnd)
                            .offset(x = 10.dp, y = 20.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(0.12f))
                    )
                    Icon(
                        Icons.Filled.ChildCare, null,
                        tint = Color.White.copy(0.30f),
                        modifier = Modifier.size(72.dp).align(Alignment.BottomEnd).padding(16.dp)
                    )
                    Column(
                        modifier = Modifier.fillMaxSize().padding(20.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Surface(shape = RoundedCornerShape(100.dp), color = Color.White.copy(0.22f)) {
                            Text(
                                if (s.isThai) "กิจกรรมแนะนำวันนี้" else "Today's suggested activity",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelMedium, color = Color.White, fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (s.isThai) (if (childName != null) "ชวนน้อง$childName ทำกิจกรรมเสริมทักษะตามวัยด้วยกันทุกวัน" else "ชวนน้องทำกิจกรรมเสริมทักษะตามวัยด้วยกันทุกวัน")
                            else (if (childName != null) "Do age-appropriate activities with $childName every day" else "Do age-appropriate activities with your child every day"),
                            style = MaterialTheme.typography.titleSmall, color = Color.White, fontWeight = FontWeight.Bold,
                            modifier = Modifier.fillMaxWidth(0.8f)
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            if (s.isThai) "เพื่อพัฒนาการที่สมวัย" else "for healthy, on-track development",
                            style = MaterialTheme.typography.bodySmall, color = Color.White.copy(0.9f)
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

/** Shown in place of the skill list when the child has achieved every age-appropriate handbook skill. */
@Composable
private fun FinishedSkillsCard(isThai: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF4CAF50).copy(0.10f))
    ) {
        Row(
            Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier.size(48.dp).clip(CircleShape).background(Color(0xFF4CAF50).copy(0.18f)),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.EmojiEvents, null, tint = (if (com.lullabyai.app.ui.theme.LocalDarkTheme.current) Color(0xFF81C784) else Color(0xFF2E7D32)), modifier = Modifier.size(28.dp)) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    if (isThai) "เก่งมาก! ทำครบทุกทักษะตามวัยแล้ว" else "Great job! All age skills completed",
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = (if (com.lullabyai.app.ui.theme.LocalDarkTheme.current) Color(0xFF81C784) else Color(0xFF2E7D32))
                )
                Text(
                    if (isThai) "ทักษะแนะนำชุดใหม่จะปรากฏเมื่อถึงวัยถัดไป หรือดูทั้งหมดได้ที่ \"ดูทั้งหมด\""
                    else "New recommended skills appear as your child grows — or see everything in \"View all\".",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(100.dp),
        color = if (selected) Primary else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun SkillRow(
    milestone: DevelopmentMilestone,
    expanded: Boolean,
    notYetAge: Boolean,
    onToggleAchieved: () -> Unit,
    onOpenSkip: () -> Unit,
    onConfirmSkip: (String) -> Unit
) {
    val s = com.lullabyai.app.ui.localization.LocalStrings.current
    val done = milestone.isAchieved
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = if (notYetAge) MaterialTheme.colorScheme.surfaceVariant.copy(0.35f) else MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(if (notYetAge) 0.dp else 1.dp)
    ) {
        Column {
            Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // Tap circle to toggle achieved (locked when the age hasn't been reached)
                Box(
                    modifier = Modifier.size(40.dp).clip(CircleShape)
                        .background(if (done) MaterialTheme.colorScheme.primary.copy(0.1f) else MaterialTheme.colorScheme.surfaceVariant)
                        .then(if (notYetAge) Modifier else Modifier.selectable(selected = done, onClick = onToggleAchieved)),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        notYetAge -> Icon(Icons.Filled.Schedule, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(22.dp))
                        done -> Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        else -> Icon(Icons.Filled.RadioButtonUnchecked, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(24.dp))
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(milestone.milestoneName, style = MaterialTheme.typography.bodyMedium, color = if (notYetAge) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface)
                    if (!done && milestone.skipReason != null) {
                        Text((if (s.isThai) "ข้าม: " else "Skipped: ") + milestone.skipReason, style = MaterialTheme.typography.labelSmall, color = Color(0xFFFF9800))
                    }
                }
                when {
                    notYetAge -> Surface(shape = RoundedCornerShape(100.dp), color = MaterialTheme.colorScheme.outline.copy(0.12f)) {
                        Text(if (s.isThai) "ยังไม่ถึงวัย" else "Not age yet", modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    }
                    done -> Icon(Icons.Filled.Verified, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                    else -> IconButton(onClick = onOpenSkip, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Filled.MoreVert, if (s.isThai) "ตัวเลือก" else "Options", tint = MaterialTheme.colorScheme.outline)
                    }
                }
            }
            // Inline skip-reason menu
            if (expanded && !done && !notYetAge) {
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                var selectedReason by remember { mutableStateOf(milestone.skipReason ?: "") }
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (s.isThai) "ระบุเหตุผลที่ข้าม:" else "Reason for skipping:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    skipReasons(s.isThai).forEach { reason ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(0.4f))
                                .selectable(selected = selectedReason == reason, onClick = { selectedReason = reason })
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            RadioButton(selected = selectedReason == reason, onClick = { selectedReason = reason }, colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary))
                            Text(reason, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Button(
                        onClick = { if (selectedReason.isNotBlank()) onConfirmSkip(selectedReason) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(100.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Primary)
                    ) { Text(if (s.isThai) "ยืนยัน" else "Confirm", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}
