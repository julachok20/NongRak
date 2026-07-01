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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import android.content.Intent
import android.widget.Toast
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.ChatViewModel
import com.lullabyai.app.viewmodel.ChildViewModel
import com.lullabyai.app.viewmodel.OnboardingViewModel

private val THAI_MONTHS = listOf("ม.ค.", "ก.พ.", "มี.ค.", "เม.ย.", "พ.ค.", "มิ.ย.", "ก.ค.", "ส.ค.", "ก.ย.", "ต.ค.", "พ.ย.", "ธ.ค.")
private val ENG_MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
internal fun formatDate(iso: String?, isThai: Boolean): String = try {
    val p = iso!!.split("-")
    val day = p[2].toInt(); val month = p[1].toInt() - 1; val year = p[0].toInt()
    if (isThai) "$day ${THAI_MONTHS[month]} ${year + 543}" else "$day ${ENG_MONTHS[month]} $year"
} catch (e: Exception) { iso ?: "" }

/** Human label for a child's sex. Anything that isn't explicitly male/female is shown as "not told"
 *  rather than guessing a gender. Shared across the child screens. */
internal fun genderLabel(gender: String?, isThai: Boolean): String = when (gender) {
    "male" -> if (isThai) "ชาย" else "Male"
    "female" -> if (isThai) "หญิง" else "Female"
    else -> if (isThai) "ไม่ได้ระบุ" else "Not specified"
}

@Composable
fun ChildSummaryScreen(
    childViewModel: ChildViewModel,
    chatViewModel: ChatViewModel,
    navController: NavController,
    onboardingViewModel: OnboardingViewModel,
    onBack: () -> Unit
) {
    val s = com.lullabyai.app.ui.localization.LocalStrings.current
    val childState by childViewModel.uiState.collectAsState()
    val chatState by chatViewModel.uiState.collectAsState()
    val child = childState.child
    var childToDelete by remember { mutableStateOf<com.lullabyai.app.data.model.Child?>(null) }
    val milestones = childState.milestones
    val devPercent = if (milestones.isNotEmpty()) milestones.count { it.isAchieved } * 100 / milestones.size else 0
    val categories = remember(milestones) { milestones.map { it.category }.distinct().take(2) }

    LaunchedEffect(child) {
        child?.let { chatViewModel.loadChildInfoSummary(it) }
    }

    // If the active child changed elsewhere (added a child, or switched on another screen), reload.
    val selectedId by com.lullabyai.app.data.settings.ChildSelection.id.collectAsState()
    LaunchedEffect(selectedId) {
        val current = childState.child?.id
        if (selectedId != null && current != null && selectedId != current) {
            childViewModel.selectChild(selectedId!!)
        }
    }

    ChildScreenScaffold(title = if (s.isThai) "ข้อมูลของเด็ก" else "Child Info", currentTab = "overview", navController = navController) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            // Profile card (row)
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
                Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(64.dp).clip(CircleShape).background(SecondaryContainer), contentAlignment = Alignment.Center) {
                        val avatar = childState.childAvatarSignedUrl
                        if (!avatar.isNullOrBlank()) AsyncImage(model = avatar, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        else Text("👶", fontSize = 30.sp)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        // Name + switch-child dropdown ("สลับบัญชี")
                        var switchOpen by remember { mutableStateOf(false) }
                        Box {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { switchOpen = true }
                            ) {
                                Text(child?.name ?: (if (s.isThai) "ลูกของคุณ" else "Your child"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.width(4.dp))
                                Surface(shape = RoundedCornerShape(100.dp), color = Primary.copy(0.1f)) {
                                    Row(Modifier.padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(if (s.isThai) "สลับบัญชี" else "Switch", style = MaterialTheme.typography.labelSmall, color = Primary, fontWeight = FontWeight.Bold)
                                        Icon(Icons.Filled.ArrowDropDown, null, tint = Primary, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                            DropdownMenu(expanded = switchOpen, onDismissRequest = { switchOpen = false }) {
                                childState.children.forEach { c ->
                                    DropdownMenuItem(
                                        text = { Text(c.name + if (c.id == child?.id) "  ✓" else "") },
                                        onClick = { switchOpen = false; childViewModel.selectChild(c.id) },
                                        trailingIcon = {
                                            IconButton(onClick = { switchOpen = false; childToDelete = c }, modifier = Modifier.size(28.dp)) {
                                                Icon(Icons.Filled.DeleteOutline, if (s.isThai) "ลบเด็ก" else "Delete child", tint = Error, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    )
                                }
                                HorizontalDivider()
                                DropdownMenuItem(
                                    leadingIcon = { Icon(Icons.Filled.Add, null, tint = Primary) },
                                    text = { Text(if (s.isThai) "เพิ่มเด็ก" else "Add child", color = Primary, fontWeight = FontWeight.Bold) },
                                    onClick = {
                                        switchOpen = false
                                        onboardingViewModel.startAddChild()
                                        navController.navigate(Screen.OnboardingStep1.route)
                                    }
                                )
                            }
                        }
                        Text((if (s.isThai) "อายุ " else "Age ") + "${childViewModel.calculateAge(s.isThai)} (" + genderLabel(child?.gender, s.isThai) + ")", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text((if (s.isThai) "วันเกิด " else "Born ") + formatDate(child?.birthdate, s.isThai), style = MaterialTheme.typography.labelSmall, color = Outline)
                    }
                    IconButton(onClick = { navController.navigate(Screen.EditChild.route) }) {
                        Icon(Icons.Filled.Edit, null, tint = Primary, modifier = Modifier.size(20.dp))
                    }
                }
            }

            // AI insight card
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Primary.copy(0.07f))) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.AutoAwesome, null, tint = Primary, modifier = Modifier.size(20.dp))
                            Text(if (s.isThai) "ข้อมูลเชิงลึกจาก AI" else "AI Insights", style = MaterialTheme.typography.titleSmall, color = Primary, fontWeight = FontWeight.Bold)
                        }
                        Surface(shape = RoundedCornerShape(100.dp), color = Primary.copy(0.15f)) {
                            Text(if (s.isThai) "อัปเดตวันนี้" else "Updated today", modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall, color = Primary)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(72.dp)) {
                            CircularProgressIndicator(progress = { devPercent / 100f }, modifier = Modifier.fillMaxSize(), color = Primary, trackColor = MaterialTheme.colorScheme.surfaceVariant, strokeWidth = 7.dp)
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("$devPercent%", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(if (s.isThai) "พัฒนาการ" else "Development", style = MaterialTheme.typography.labelSmall, color = Outline, fontSize = 8.sp)
                            }
                        }
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(if (s.isThai) "สรุปภาพรวม" else "Overview", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            if (chatState.childInfoSummary != null) {
                                Text(chatState.childInfoSummary!!, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                            } else {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Primary)
                            }
                        }
                    }
                    if (categories.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            categories.forEach { cat ->
                                Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface) {
                                    Text("#${catLabel(cat, s.isThai)}", modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = Primary)
                                }
                            }
                        }
                    }
                }
            }

            // Export-to-doctor PDF
            val context = LocalContext.current
            OutlinedButton(
                onClick = {
                    childViewModel.exportDoctorReport(context, s.isThai) { file, err ->
                        if (file == null) {
                            Toast.makeText(context, (if (s.isThai) "สร้าง PDF ไม่สำเร็จ" else "Couldn't create PDF") + (err?.let { ": $it" } ?: ""), Toast.LENGTH_LONG).show()
                        } else {
                            try {
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/pdf"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    clipData = android.content.ClipData.newRawUri("report", uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(send, if (s.isThai) "ส่งออกข้อมูลสรุป" else "Export summary"))
                            } catch (e: Exception) {
                                Toast.makeText(context, (if (s.isThai) "แชร์ไม่สำเร็จ: " else "Share failed: ") + e.message, Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(100.dp),
                enabled = !childState.reportLoading
            ) {
                if (childState.reportLoading) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Primary)
                    Spacer(Modifier.width(8.dp))
                    Text(if (s.isThai) "กำลังสร้าง PDF..." else "Generating PDF...")
                } else {
                    Icon(Icons.Filled.PictureAsPdf, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (s.isThai) "ส่งออกข้อมูลสรุป (PDF)" else "Export summary (PDF)", fontWeight = FontWeight.Bold)
                }
            }
            Text(
                if (s.isThai) "เหมาะสำหรับการปรึกษาแพทย์หรือเก็บเป็นประวัติส่วนตัว" else "For doctor consultations or your own records",
                style = MaterialTheme.typography.labelSmall, color = Outline, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
            )

            // Key info header — ประวัติ opens the full profile-history screen
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(if (s.isThai) "ข้อมูลสำคัญ" else "Key info", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(onClick = { }, shape = RoundedCornerShape(100.dp), color = Primary) {
                        Text(if (s.isThai) "ล่าสุด" else "Latest", modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp), style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Surface(onClick = { navController.navigate(Screen.ProfileHistory.route) }, shape = RoundedCornerShape(100.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                        Text(if (s.isThai) "ประวัติ" else "History", modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GrowthStatCard(if (s.isThai) "น้ำหนัก" else "Weight", "${child?.weightKg ?: "-"} " + (if (s.isThai) "กก." else "kg"), if (s.isThai) "อยู่ในเกณฑ์ปกติ" else "Normal range", Modifier.weight(1f)) { navController.navigate(Screen.GrowthMetrics.route) }
                GrowthStatCard(if (s.isThai) "ส่วนสูง" else "Height", "${child?.heightCm ?: "-"} " + (if (s.isThai) "ซม." else "cm"), if (s.isThai) "อยู่ในเกณฑ์ปกติ" else "Normal range", Modifier.weight(1f)) { navController.navigate(Screen.GrowthMetrics.route) }
            }
            InfoRow(Icons.Filled.Warning, if (s.isThai) "การแพ้" else "Allergies", if (childState.allergies.isNotEmpty()) ("${childState.allergies.size} " + (if (s.isThai) "รายการ" else "items")) else (if (s.isThai) "ไม่มีประวัติแพ้" else "None"), null) { navController.navigate(Screen.AllergyHistory.route) }

            // Main-service quick links (same services as the home dashboard) — #5
            Text(if (s.isThai) "บริการหลัก" else "Main services", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ChildQuickLink(if (s.isThai) "วัคซีน" else "Vaccine", Icons.Filled.Vaccines, Modifier.weight(1f)) { navController.navigate(Screen.VaccineBook.route) }
                ChildQuickLink(if (s.isThai) "พัฒนาการ" else "Dev", Icons.Filled.TrendingUp, Modifier.weight(1f)) { navController.navigate(Screen.ChildDevelopment.route) }
                ChildQuickLink(if (s.isThai) "สุขภาพ" else "Health", Icons.Filled.HealthAndSafety, Modifier.weight(1f)) { navController.navigate(Screen.ChildHealth.route) }
                ChildQuickLink(if (s.isThai) "โภชนาการ" else "Nutrition", Icons.Filled.Restaurant, Modifier.weight(1f)) { navController.navigate(Screen.Nutrition.route) }
            }

            Spacer(Modifier.height(8.dp))
        }
    }

    // Delete-child confirmation
    childToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { childToDelete = null },
            icon = { Icon(Icons.Filled.DeleteOutline, null, tint = Error) },
            title = { Text(if (s.isThai) "ลบข้อมูลเด็ก?" else "Delete child?", fontWeight = FontWeight.Bold) },
            text = { Text(if (s.isThai) "ต้องการลบ \"${target.name}\" หรือไม่? ข้อมูลทั้งหมด (วัคซีน พัฒนาการ สุขภาพ เอกสาร) จะถูกลบถาวรและไม่สามารถย้อนกลับได้" else "Permanently delete \"${target.name}\"? All their data (vaccines, milestones, health, documents) will be removed and cannot be undone.") },
            confirmButton = { TextButton(onClick = { childViewModel.deleteChild(target.id); childToDelete = null }) { Text(if (s.isThai) "ลบ" else "Delete", color = Error, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { childToDelete = null }) { Text(if (s.isThai) "ยกเลิก" else "Cancel") } }
        )
    }
}

@Composable
private fun ChildQuickLink(label: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(vertical = 14.dp, horizontal = 6.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(38.dp).clip(CircleShape).background(Primary.copy(0.1f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = Primary, modifier = Modifier.size(20.dp))
            }
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
        }
    }
}

@Composable
internal fun ProfileCard(content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
internal fun CardHeader(icon: ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, null, tint = Primary, modifier = Modifier.size(20.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun CardHeaderInline(icon: ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, null, tint = Primary, modifier = Modifier.size(20.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun InfoRow(icon: ImageVector, label: String, value: String, status: String?, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(Primary.copy(0.1f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = Primary, modifier = Modifier.size(20.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (status != null) {
                        Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(Color(0xFF4CAF50)))
                        Text(status, style = MaterialTheme.typography.labelSmall, color = Color(0xFF2E7D32))
                    }
                }
            }
            Icon(Icons.Filled.ChevronRight, null, tint = Outline, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
internal fun GrowthStatCard(label: String, value: String, status: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Primary)
            Text(status, style = MaterialTheme.typography.labelSmall, color = Color(0xFF2E7D32))
        }
    }
}
