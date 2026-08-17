@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.child

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.lullabyai.app.data.model.OralHealthEvent
import com.lullabyai.app.data.model.ToothRecord
import com.lullabyai.app.ui.components.AppBottomNav
import com.lullabyai.app.ui.components.BottomTab
import com.lullabyai.app.ui.components.LullabyTopBar
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.ChildViewModel
import com.lullabyai.app.viewmodel.OralHealthViewModel

// 20 Deciduous Teeth (10 Maxillary A-J, 10 Mandibular K-T)
private val UPPER_TEETH = listOf("A", "B", "C", "D", "E", "F", "G", "H", "I", "J")
private val LOWER_TEETH = listOf("K", "L", "M", "N", "O", "P", "Q", "R", "S", "T")

@Composable
fun OralHealthScreen(
    oralHealthViewModel: OralHealthViewModel,
    childViewModel: ChildViewModel,
    navController: NavController
) {
    val s = LocalStrings.current
    val isThai = s.isThai
    val childState by childViewModel.uiState.collectAsState()
    val state by oralHealthViewModel.uiState.collectAsState()
    val child = childState.child

    LaunchedEffect(child?.id) {
        child?.let { oralHealthViewModel.loadData(it) }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "emergingToothPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Scaffold(
        topBar = {
            LullabyTopBar(
                title = if (isThai) "สุขภาพช่องปาก" else "Oral Health Pro",
                onBack = { navController.popBackStack() },
                navController = navController
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
                onConnect = { navController.navigate(Screen.ConnectServicesSelection.route) { launchSingleTop = true } }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 88.dp)
        ) {
            // Child Snapshot Header
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Primary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!childState.childAvatarSignedUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = childState.childAvatarSignedUrl,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(Icons.Filled.ChildCare, null, tint = Primary, modifier = Modifier.size(28.dp))
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = child?.name ?: (if (isThai) "ข้อมูลลูก" else "Child"),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isThai) "ระบบติดตามพัฒนาการฟันและสุขภาพช่องปาก" else "Pediatric Dental & Oral Health Tracking",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 7-Day Brushing Consistency Widget
            item {
                val (completedDays, totalDays) = state.consistency
                val percentage = if (totalDays > 0) (completedDays * 100 / totalDays) else 0

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Primary.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.Insights, null, tint = Primary, modifier = Modifier.size(22.dp))
                            }
                            Column {
                                Text(
                                    text = if (isThai) "ความสม่ำเสมอใน 7 วันที่ผ่านมา" else "7-Day Consistency",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (isThai) "แปรงฟันครบ 2 ครั้ง/วัน: $completedDays/$totalDays วัน" else "Brushed 2x/day: $completedDays/$totalDays days",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(54.dp)) {
                            CircularProgressIndicator(
                                progress = { (percentage / 100f).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxSize(),
                                color = Primary,
                                strokeWidth = 5.dp,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                            Text(
                                text = "$percentage%",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Primary
                            )
                        }
                    }
                }
            }

            // Interactive Teeth Map Section
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (isThai) "แผนผังฟันอัจฉริยะ" else "Smart Tooth Map",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                LegendDot(Primary, if (isThai) "ปกติ" else "Healthy")
                                LegendDot(Secondary, if (isThai) "กำลังขึ้น" else "Emerging")
                                LegendDot(Error, if (isThai) "ต้องดูแล" else "Needs Care")
                            }
                        }

                        // Upper Teeth (Maxillary)
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = if (isThai) "ฟันบน (Maxillary)" else "Upper Arch",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                UPPER_TEETH.forEach { id ->
                                    val tooth = state.teeth[id]
                                    ToothTile(
                                        toothId = id,
                                        status = tooth?.status ?: "missing",
                                        pulseScale = pulseScale,
                                        onClick = { oralHealthViewModel.openToothModal(id) }
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                        // Lower Teeth (Mandibular)
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = if (isThai) "ฟันล่าง (Mandibular)" else "Lower Arch",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                LOWER_TEETH.forEach { id ->
                                    val tooth = state.teeth[id]
                                    ToothTile(
                                        toothId = id,
                                        status = tooth?.status ?: "missing",
                                        pulseScale = pulseScale,
                                        onClick = { oralHealthViewModel.openToothModal(id) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Daily Routine Tracker Section
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (isThai) "บันทึกกิจวัตรประจำวัน" else "Daily Oral Routine",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Surface(
                                shape = RoundedCornerShape(100.dp),
                                color = Primary.copy(alpha = 0.12f)
                            ) {
                                Text(
                                    text = if (isThai) "วันนี้" else "Today",
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Primary
                                )
                            }
                        }

                        RoutineRow(
                            icon = Icons.Filled.WbSunny,
                            label = if (isThai) "ตอนเช้า" else "Morning",
                            checked = state.todayRoutine.morning,
                            onCheckedChange = { oralHealthViewModel.toggleRoutineMorning(it) }
                        )
                        RoutineRow(
                            icon = Icons.Filled.Restaurant,
                            label = if (isThai) "กลางวัน" else "Lunch",
                            checked = state.todayRoutine.lunch,
                            onCheckedChange = { oralHealthViewModel.toggleRoutineLunch(it) }
                        )
                        RoutineRow(
                            icon = Icons.Filled.Bedtime,
                            label = if (isThai) "ก่อนนอน" else "Bedtime",
                            checked = state.todayRoutine.bedtime,
                            onCheckedChange = { oralHealthViewModel.toggleRoutineBedtime(it) }
                        )
                        RoutineRow(
                            icon = Icons.Filled.CleaningServices,
                            label = if (isThai) "ใช้ไหมขัดฟัน" else "Flossing",
                            checked = state.todayRoutine.flossing,
                            onCheckedChange = { oralHealthViewModel.toggleRoutineFlossing(it) }
                        )
                    }
                }
            }

            // Milestone & Medical History Section
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (isThai) "ประวัติและเหตุการณ์สำคัญ" else "Oral Health Milestones",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            IconButton(onClick = { navController.navigate(Screen.RecordOralHealthEvent.route) }) {
                                Icon(Icons.Filled.AddCircle, contentDescription = "บันทึกเหตุการณ์", tint = Primary, modifier = Modifier.size(28.dp))
                            }
                        }

                        if (state.events.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (isThai) "ยังไม่มีบันทึกเหตุการณ์ กดปุ่ม + เพื่อเพิ่มบันทึกฟันซี่แรกหรือการพบแพทย์" else "No oral events yet. Tap + to record first tooth or dentist checkup.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            state.events.forEach { event ->
                                EventItemCard(event = event, isThai = isThai)
                            }
                        }
                    }
                }
            }

            // AI Pediatric Dental Recommendation Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Primary.copy(alpha = 0.06f)),
                    border = BorderStroke(1.dp, Primary.copy(alpha = 0.20f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Primary.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.AutoAwesome, null, tint = Primary, modifier = Modifier.size(24.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = if (isThai) "คำแนะนำจาก AI" else "AI Dental Guidance",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = Primary,
                                    fontWeight = FontWeight.Bold
                                )
                                Surface(shape = RoundedCornerShape(100.dp), color = Primary) {
                                    Text(
                                        text = "PRO",
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                        fontSize = 9.sp
                                    )
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            if (state.aiAdviceLoading) {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), color = Primary)
                            } else {
                                Text(
                                    text = state.aiAdvice ?: (if (isThai) "ควรช่วยแปรงฟันให้ลูกอย่างน้อยวันละ 2 ครั้งด้วยยาสีฟันผสมฟลูออไรด์ 1000 ppm และพบทันตแพทย์เด็กเป็นประจำค่ะ" else "Help brush your child's teeth at least 2 times daily with 1000 ppm fluoride toothpaste."),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = 22.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Tooth Status Selection Modal
    state.activeToothIdForModal?.let { toothId ->
        AlertDialog(
            onDismissRequest = { oralHealthViewModel.dismissToothModal() },
            title = {
                Text(
                    text = if (isThai) "เลือกสถานะฟัน ($toothId)" else "Tooth Status ($toothId)",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ToothStatusOption(
                        icon = Icons.Filled.CheckCircle,
                        iconColor = Primary,
                        title = if (isThai) "ปกติ (Healthy)" else "Healthy",
                        subtitle = if (isThai) "ฟันสุขภาพดี แข็งแรง" else "Clean, healthy tooth",
                        onClick = { oralHealthViewModel.updateToothStatus(toothId, "healthy") }
                    )
                    ToothStatusOption(
                        icon = Icons.Filled.KeyboardDoubleArrowUp,
                        iconColor = Secondary,
                        title = if (isThai) "กำลังขึ้น (Emerging)" else "Emerging",
                        subtitle = if (isThai) "ฟันกำลังเริ่มงอกทะลุเหงือก" else "Erupting through gum",
                        onClick = { oralHealthViewModel.updateToothStatus(toothId, "emerging") }
                    )
                    ToothStatusOption(
                        icon = Icons.Filled.Warning,
                        iconColor = Error,
                        title = if (isThai) "ต้องดูแล (Needs Care)" else "Needs Care",
                        subtitle = if (isThai) "พบจุดฟันผุ หรือมีความผิดปกติ" else "Cavity or sensitivity noticed",
                        onClick = { oralHealthViewModel.updateToothStatus(toothId, "needs_care") }
                    )
                    ToothStatusOption(
                        icon = Icons.Filled.RemoveCircleOutline,
                        iconColor = MaterialTheme.colorScheme.outline,
                        title = if (isThai) "ยังไม่ขึ้น / หลุดแล้ว (Missing)" else "Missing / Not yet",
                        subtitle = if (isThai) "ยังไม่มีฟันในตำแหน่งนี้" else "No tooth present",
                        onClick = { oralHealthViewModel.updateToothStatus(toothId, "missing") }
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { oralHealthViewModel.dismissToothModal() }) {
                    Text(if (isThai) "ปิด" else "Close")
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ToothTile(
    toothId: String,
    status: String,
    pulseScale: Float,
    onClick: () -> Unit
) {
    val (iconColor, isEmerging) = when (status.lowercase()) {
        "healthy" -> Pair(Primary, false)
        "emerging" -> Pair(Secondary, true)
        "needs_care" -> Pair(Error, false)
        else -> Pair(MaterialTheme.colorScheme.outlineVariant, false)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(2.dp)
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .then(if (isEmerging) Modifier.scale(pulseScale) else Modifier)
                .clip(RoundedCornerShape(6.dp))
                .background(iconColor.copy(alpha = if (status == "missing") 0.08f else 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.HealthAndSafety,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(18.dp)
            )
        }
        Text(
            text = toothId,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = if (status == "missing") MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun RoutineRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        onClick = { onCheckedChange(!checked) },
        shape = RoundedCornerShape(12.dp),
        color = if (checked) Primary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = BorderStroke(1.dp, if (checked) Primary.copy(alpha = 0.3f) else Color.Transparent),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(icon, null, tint = if (checked) Primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (checked) FontWeight.Bold else FontWeight.Normal,
                    color = if (checked) Primary else MaterialTheme.colorScheme.onSurface
                )
            }
            Checkbox(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = CheckboxDefaults.colors(checkedColor = Primary)
            )
        }
    }
}

@Composable
private fun EventItemCard(event: OralHealthEvent, isThai: Boolean) {
    val (icon, color) = when (event.category) {
        "first_tooth" -> Pair(Icons.Filled.ChildCare, Primary)
        "dentist_visit" -> Pair(Icons.Filled.MedicalServices, Secondary)
        "treatment" -> Pair(Icons.Filled.Healing, Error)
        else -> Pair(Icons.Filled.Category, MaterialTheme.colorScheme.onSurfaceVariant)
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = event.eventName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                val notes = event.notes
                if (!notes.isNullOrBlank()) {
                    Text(
                        text = notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                Text(
                    text = event.eventDate,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun ToothStatusOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(iconColor.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = iconColor, modifier = Modifier.size(20.dp))
            }
            Column {
                Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
