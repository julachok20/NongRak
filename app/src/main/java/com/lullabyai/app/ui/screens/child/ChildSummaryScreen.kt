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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.lullabyai.app.ui.components.NoChildDataCard
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

/** Short bold headline for the AI Analysis card, synthesized from the milestone-achieved percentage
 *  (there's no separate AI-generated headline field — the mockup pairs the score ring with a title
 *  line, so this derives one deterministically rather than adding another AI call). */
internal fun devHeadline(devPercent: Int, isThai: Boolean): String = when {
    devPercent >= 70 -> if (isThai) "พัฒนาการโดยรวมดีมาก" else "Overall development is excellent"
    devPercent >= 40 -> if (isThai) "พัฒนาการโดยรวมอยู่ในเกณฑ์ปกติ" else "Overall development is on track"
    else -> if (isThai) "ควรติดตามพัฒนาการเพิ่มเติม" else "Development needs closer tracking"
}

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
        child?.let {
            chatViewModel.loadChildInfoSummary(it, s.isThai)
            chatViewModel.loadChildInfoCardSummary(it, s.isThai)
        }
    }

    // This screen never triggered its own load before (it relied on Dashboard having already
    // populated the shared ChildViewModel) — fine when Dashboard is always visited first, but leaves
    // isLoading stuck at its true default forever if this screen is ever reached directly. Guarded
    // so it doesn't re-fetch on every tab switch once data is already loaded.
    LaunchedEffect(Unit) {
        if (childState.children.isEmpty() && childState.child == null) childViewModel.loadChild()
    }

    // AI daily question — same banner/dialog as Dashboard (ChildViewModel.uiState.dailyQuestion is
    // shared, loadDailyQuestion is idempotent) so it also surfaces here on Child Info.
    val context = LocalContext.current
    LaunchedEffect(child?.id) { child?.let { childViewModel.loadDailyQuestion(context, s.isThai) } }
    var showDailyQuestionDialog by remember { mutableStateOf(false) }
    if (showDailyQuestionDialog) {
        childState.dailyQuestion?.let { question ->
            com.lullabyai.app.ui.components.DailyQuestionDialog(
                question = question, isThai = s.isThai,
                onAnswer = { answer -> childViewModel.answerDailyQuestion(answer); showDailyQuestionDialog = false },
                onSkip = { childViewModel.dismissDailyQuestion(); showDailyQuestionDialog = false },
                onDismiss = { showDailyQuestionDialog = false }
            )
        }
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
        if (childState.isLoading && childState.children.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Primary)
        }
        } else if (childState.children.isEmpty() && !childState.isLoading && childState.error != null) {
        Box(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
            com.lullabyai.app.ui.components.ChildLoadErrorCard(s.isThai) { childViewModel.loadChild() }
        }
        } else if (childState.children.isEmpty() && !childState.isLoading) {
        Box(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
            NoChildDataCard(s.isThai) { navController.navigate(Screen.AddChildChoice.route) }
        }
        } else {
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            val context = LocalContext.current

            // Persistent one-tap emergency line — flagged in the professional-polish audit as
            // needed (a real visual element, not just prose buried in an AI reply). ACTION_DIAL
            // (not ACTION_CALL) so it just opens the dialer pre-filled — no CALL_PHONE permission.
            Card(
                onClick = { context.startActivity(Intent(Intent.ACTION_DIAL, android.net.Uri.parse("tel:1669"))) },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Error.copy(0.08f))
            ) {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 10.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Filled.Call, null, tint = Error, modifier = Modifier.size(18.dp))
                    Text(
                        if (s.isThai) "เหตุฉุกเฉิน โทร 1669" else "Emergency? Call 1669",
                        style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Error,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(Icons.Filled.ChevronRight, null, tint = Error.copy(0.5f), modifier = Modifier.size(18.dp))
                }
            }

            childState.dailyQuestion?.let {
                com.lullabyai.app.ui.components.DailyQuestionBanner(
                    isThai = s.isThai,
                    onClick = { showDailyQuestionDialog = true },
                    onDismiss = { childViewModel.dismissDailyQuestion() }
                )
            }

            // Profile card (row)
            Box(modifier = Modifier.fillMaxWidth()) {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
              Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(80.dp).clip(RoundedCornerShape(18.dp)).background(SecondaryContainer), contentAlignment = Alignment.Center) {
                        val avatar = childState.childAvatarSignedUrl
                        if (!avatar.isNullOrBlank()) AsyncImage(model = avatar, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        else Text("👶", fontSize = 30.sp)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        // Name + switch-child trigger ("สลับบัญชี") — opens the bottom sheet below.
                        var showSwitcherSheet by remember { mutableStateOf(false) }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { showSwitcherSheet = true }
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
                        if (showSwitcherSheet) {
                            ChildSwitcherSheet(
                                isThai = s.isThai,
                                children = childState.children,
                                activeChildId = child?.id,
                                childAvatarSignedUrl = childState.childAvatarSignedUrl,
                                onSelect = { c -> showSwitcherSheet = false; childViewModel.selectChild(c.id) },
                                onDelete = { c -> showSwitcherSheet = false; childToDelete = c },
                                onAddChild = { showSwitcherSheet = false; navController.navigate(Screen.AddChildChoice.route) },
                                onInvite = { c -> showSwitcherSheet = false; childViewModel.generateInviteCode(c.id) },
                                onDismiss = { showSwitcherSheet = false }
                            )
                        }

                        childState.inviteCode?.let { code ->
                            val context = LocalContext.current
                            val forChildName = childState.inviteCodeChildName
                            AlertDialog(
                                onDismissRequest = { childViewModel.clearSyncState() },
                                title = {
                                    Text(
                                        if (forChildName != null) (if (s.isThai) "รหัสเชิญสำหรับ $forChildName" else "Invite code for $forChildName")
                                        else (if (s.isThai) "รหัสเชิญ" else "Invite code")
                                    )
                                },
                                text = {
                                    Column {
                                        Text(
                                            if (forChildName != null) {
                                                if (s.isThai) "รหัสนี้ใช้เชื่อมข้อมูลของ \"$forChildName\" เท่านั้น เด็กแต่ละคนมีรหัสของตัวเอง — ส่งให้ผู้ปกครองอีกท่านเพื่อเชื่อมข้อมูลลูกคนนี้ (ใช้ได้ 7 วัน)"
                                                else "This code syncs \"$forChildName\" only — each child has its own code. Share it with the other parent to sync this child (valid 7 days)"
                                            } else {
                                                if (s.isThai) "ส่งรหัสนี้ให้ผู้ปกครองอีกท่านเพื่อเชื่อมข้อมูลลูก (ใช้ได้ 7 วัน)"
                                                else "Share this code with the other parent to sync this child (valid 7 days)"
                                            },
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        Spacer(Modifier.height(12.dp))
                                        Text(code, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Primary)
                                    }
                                },
                                confirmButton = {
                                    TextButton(onClick = {
                                        val send = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, code)
                                        }
                                        context.startActivity(Intent.createChooser(send, null))
                                    }) { Text(if (s.isThai) "แชร์" else "Share") }
                                },
                                dismissButton = {
                                    TextButton(onClick = { childViewModel.clearSyncState() }) { Text(if (s.isThai) "ปิด" else "Close") }
                                }
                            )
                        }
                        Text((if (s.isThai) "อายุ " else "Age ") + "${childViewModel.calculateAge(s.isThai)} (" + genderLabel(child?.gender, s.isThai) + ")", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text((if (s.isThai) "วันเกิด " else "Born ") + formatDate(child?.birthdate, s.isThai), style = MaterialTheme.typography.labelSmall, color = Outline)
                    }
                }
                // "Check health data" CTA -> new AI-aggregated summary screen. Merged into the same
                // card as the avatar/name/age (mockup keeps these in one continuous card boundary),
                // not a separate sibling button below it.
                Button(
                    onClick = { navController.navigate(Screen.ChildAiSummary.route) },
                    modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary)
                ) {
                    Icon(Icons.Filled.Insights, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (s.isThai) "ตรวจสอบข้อมูลสุขภาพ" else "Check health data", fontWeight = FontWeight.Bold)
                }
              }
            }
            // Floating edit button (mockup: absolute top-right over the card)
            IconButton(
                onClick = { navController.navigate(Screen.EditChild.route) },
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)
            ) {
                Icon(Icons.Filled.Edit, null, tint = Outline, modifier = Modifier.size(20.dp))
            }
            }

            // Growth indicators — moved here (right after the profile card, before AI Analysis) to
            // match the 30.new_dash mockup's order; previously sat lower down the page.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(if (s.isThai) "ตัวชี้วัดการเติบโต" else "Growth indicators", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Surface(onClick = { navController.navigate(Screen.ProfileHistory.route) }, shape = RoundedCornerShape(100.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Text(if (s.isThai) "ประวัติการบันทึก" else "History", modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val ageMonths = childViewModel.getAgeMonths()
                val weightStatus = com.lullabyai.app.util.GrowthReference.weightStatus(child?.weightKg, ageMonths)
                val heightStatus = com.lullabyai.app.util.GrowthReference.heightStatus(child?.heightCm, ageMonths)
                GrowthStatCard(
                    if (s.isThai) "น้ำหนัก" else "Weight", "${child?.weightKg ?: "-"} " + (if (s.isThai) "กก." else "kg"),
                    (if (s.isThai) weightStatus?.labelTh else weightStatus?.labelEn) ?: (if (s.isThai) "ยังไม่มีข้อมูล" else "No data"),
                    Modifier.weight(1f), statusColor = weightStatus?.color ?: Outline, icon = Icons.Filled.MonitorWeight
                ) { navController.navigate(Screen.GrowthHistory.route) }
                GrowthStatCard(
                    if (s.isThai) "ส่วนสูง" else "Height", "${child?.heightCm ?: "-"} " + (if (s.isThai) "ซม." else "cm"),
                    (if (s.isThai) heightStatus?.labelTh else heightStatus?.labelEn) ?: (if (s.isThai) "ยังไม่มีข้อมูล" else "No data"),
                    Modifier.weight(1f), statusColor = heightStatus?.color ?: Outline, icon = Icons.Filled.Straighten
                ) { navController.navigate(Screen.GrowthHistory.route) }
            }

            // AI Analysis card — headline synthesized from the development percentage (no separate
            // AI field for it), short summary from generateChildInfoCardSummary (distinct from the
            // fuller text used on the health-check/monthly-summary pages).
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
                Column {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.AutoAwesome, null, tint = Primary, modifier = Modifier.size(20.dp))
                            Text(if (s.isThai) "การวิเคราะห์จาก AI" else "AI Analysis", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        }
                        Surface(shape = RoundedCornerShape(100.dp), color = Primary.copy(0.08f)) {
                            Text(if (s.isThai) "เรียลไทม์" else "Real-time", modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall, color = Primary, fontWeight = FontWeight.Bold)
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(72.dp)) {
                                CircularProgressIndicator(progress = { devPercent / 100f }, modifier = Modifier.fillMaxSize(), color = Primary, trackColor = MaterialTheme.colorScheme.surfaceVariant, strokeWidth = 7.dp)
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("$devPercent%", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Primary)
                                    Text(if (s.isThai) "คะแนน" else "Score", style = MaterialTheme.typography.labelSmall, color = Outline, fontSize = 8.sp)
                                }
                            }
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    devHeadline(devPercent, s.isThai),
                                    style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold
                                )
                                if (chatState.childInfoCardSummary != null) {
                                    Text(chatState.childInfoCardSummary!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                } else {
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Primary)
                                }
                            }
                        }
                        if (categories.isNotEmpty()) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                categories.forEach { cat ->
                                    Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(0.5f)) {
                                        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Box(Modifier.size(6.dp).clip(CircleShape).background(Primary))
                                            Text(catLabel(cat, s.isThai), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Update weight/height banner -> dedicated add-record form (per 27.Growth mockup).
            Card(
                onClick = { navController.navigate(Screen.GrowthHistory.route) },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = Primary.copy(0.05f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Primary.copy(0.1f))
            ) {
                Row(Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.AddTask, null, tint = Primary, modifier = Modifier.size(24.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(if (s.isThai) "อัปเดตน้ำหนักและส่วนสูง" else "Update weight & height", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        Text(if (s.isThai) "เพื่อให้ AI วิเคราะห์สุขภาพได้อย่างแม่นยำที่สุด" else "So AI can analyze health more accurately", style = MaterialTheme.typography.labelSmall, color = Outline)
                    }
                    Icon(Icons.Filled.ChevronRight, null, tint = Primary.copy(0.4f), modifier = Modifier.size(20.dp))
                }
            }

            // Export-to-doctor PDF — routes to the universal export-selection screen (choose
            // period / data categories / detailed-vs-summary) instead of exporting directly.
            Card(
                onClick = { navController.navigate(Screen.PdfExport.route) },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Row(Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(Error.copy(0.08f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.PictureAsPdf, null, tint = Error, modifier = Modifier.size(24.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(if (s.isThai) "ส่งออกสรุป (PDF)" else "Export summary (PDF)", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        Text(
                            if (s.isThai) "เอกสารสำหรับปรึกษาแพทย์ผู้เชี่ยวชาญ" else "For doctor consultations or your own records",
                            style = MaterialTheme.typography.labelSmall, color = Outline
                        )
                    }
                    Icon(Icons.Filled.ChevronRight, null, tint = Outline.copy(0.4f), modifier = Modifier.size(20.dp))
                }
            }

            if (childState.allergies.isNotEmpty()) {
                // Extra-large radius (mockup gives this card a bigger radius than the rest of the
                // page's cards) and outlined chips instead of solid-filled pills.
                Card(
                    Modifier.fillMaxWidth().clickable { navController.navigate(Screen.AllergyHistory.route) },
                    shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = Error.copy(0.08f))
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.Warning, null, tint = Error, modifier = Modifier.size(18.dp))
                            Text(if (s.isThai) "ประวัติการแพ้" else "Allergies", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = Error)
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            childState.allergies.take(4).forEach { a ->
                                Surface(
                                    shape = RoundedCornerShape(100.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Error.copy(0.4f))
                                ) {
                                    Text(a.name, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = Error)
                                }
                            }
                        }
                    }
                }
            } else {
                InfoRow(Icons.Filled.Warning, if (s.isThai) "การแพ้" else "Allergies", if (s.isThai) "ไม่มีประวัติแพ้" else "None", null) { navController.navigate(Screen.AllergyHistory.route) }
            }

            // Chronic conditions summary — mirrors the allergy card above. Data is already loaded
            // into childState.chronicConditions alongside allergies (see ChildViewModel.loadChild),
            // and stays in sync with onboarding (completeOnboarding seeds this same table) and the
            // Health Hub's add/delete card, so this is read-only here, no separate fetch needed.
            Card(
                Modifier.fillMaxWidth().clickable { navController.navigate(Screen.ChildHealth.route) },
                shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.HealthAndSafety, null, tint = Primary, modifier = Modifier.size(18.dp))
                        Text(if (s.isThai) "โรคประจำตัว" else "Chronic conditions", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(10.dp))
                    if (childState.chronicConditions.isEmpty()) {
                        Text(if (s.isThai) "ไม่มีข้อมูลโรคประจำตัว" else "No chronic conditions recorded", style = MaterialTheme.typography.labelSmall, color = Outline)
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            childState.chronicConditions.take(4).forEach { cc ->
                                Surface(shape = RoundedCornerShape(100.dp), color = Primary.copy(0.06f)) {
                                    Text(cc.name, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = Primary)
                                }
                            }
                        }
                    }
                }
            }

            // Shortcuts — per the 30.new_dash mockup this is a single row, not the old 6-tile grid.
            // "ดูทั้งหมด" opens QuickLogHub (the app's actual "see everything you can do" screen).
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(if (s.isThai) "ทางลัด" else "Shortcuts", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                TextButton(onClick = { navController.navigate(Screen.QuickLogHub.route) }) {
                    Text(if (s.isThai) "ดูทั้งหมด" else "View all", color = Primary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Vaccine Book — same row style as before.
                ShortcutRow(
                    Icons.Filled.MenuBook,
                    if (s.isThai) "สมุดวัคซีน" else "Vaccine Book",
                    if (s.isThai) "ตรวจสอบและบันทึกการฉีดวัคซีน" else "Track and log vaccinations"
                ) { navController.navigate(Screen.VaccineBook.route) }
                // The app's actual standout features (จุดเด่น), not just record-keeping: the
                // agentic AI chatbot, AI disease-risk prediction, and AI-assisted sleep tracking.
                ShortcutRow(
                    Icons.Filled.SmartToy,
                    if (s.isThai) "แชทกับน้องรัก AI" else "Chat with NongRak AI",
                    if (s.isThai) "ผู้ช่วย AI ส่วนตัวสำหรับลูกคุณ" else "Your child's personal AI assistant"
                ) { navController.navigate(Screen.AiChat.route) }
                ShortcutRow(
                    Icons.Filled.MonitorHeart,
                    if (s.isThai) "ความเสี่ยงโรค (AI)" else "AI Disease Risk",
                    if (s.isThai) "วิเคราะห์ความเสี่ยงสุขภาพด้วย AI" else "AI-powered health risk analysis"
                ) { navController.navigate(Screen.DiseaseRisk.route) }
                ShortcutRow(
                    Icons.Filled.Bedtime,
                    if (s.isThai) "การนอนหลับ" else "Sleep Tracker",
                    if (s.isThai) "ติดตามการนอนพร้อมคำแนะนำจาก AI" else "Track sleep with AI recommendations"
                ) { navController.navigate(Screen.SleepTracker.route) }
            }

            Spacer(Modifier.height(8.dp))
        }
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

/** Bottom-sheet child switcher (mockup: replaces the old dropdown). Keeps the same actions the
 *  dropdown had — select, delete, invite another parent, add child — just in sheet form. */
@Composable
private fun ChildSwitcherSheet(
    isThai: Boolean,
    children: List<com.lullabyai.app.data.model.Child>,
    activeChildId: String?,
    childAvatarSignedUrl: String?,
    onSelect: (com.lullabyai.app.data.model.Child) -> Unit,
    onDelete: (com.lullabyai.app.data.model.Child) -> Unit,
    onAddChild: () -> Unit,
    onInvite: (com.lullabyai.app.data.model.Child) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(if (isThai) "เลือกบัญชีลูก" else "Select child", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, null) }
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                children.forEach { c ->
                    val isActive = c.id == activeChildId
                    Card(
                        onClick = { onSelect(c) },
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = if (isActive) Primary.copy(0.06f) else MaterialTheme.colorScheme.surface),
                        border = if (isActive) androidx.compose.foundation.BorderStroke(2.dp, Primary.copy(0.25f)) else null
                    ) {
                        Row(Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(SecondaryContainer), contentAlignment = Alignment.Center) {
                                if (isActive && !childAvatarSignedUrl.isNullOrBlank()) {
                                    AsyncImage(model = childAvatarSignedUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                } else Text("👶", fontSize = 24.sp)
                            }
                            Column(Modifier.weight(1f)) {
                                Text(c.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    if (isActive) (if (isThai) "กำลังใช้งาน" else "Active") else genderLabel(c.gender, isThai),
                                    style = MaterialTheme.typography.labelSmall, color = if (isActive) Primary else Outline, fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                            IconButton(onClick = { onInvite(c) }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Filled.SyncAlt, if (isThai) "เชิญผู้ปกครอง" else "Invite parent", tint = Primary, modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = { onDelete(c) }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Filled.DeleteOutline, if (isThai) "ลบเด็ก" else "Delete child", tint = Error, modifier = Modifier.size(18.dp))
                            }
                            if (isActive) Icon(Icons.Filled.CheckCircle, null, tint = Primary, modifier = Modifier.size(24.dp))
                        }
                    }
                }
                OutlinedButton(
                    onClick = onAddChild,
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Icon(Icons.Filled.Add, null, tint = Primary)
                    Spacer(Modifier.width(8.dp))
                    Text(if (isThai) "เพิ่มบัญชีลูก" else "Add child", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ShortcutRow(icon: ImageVector, title: String, description: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(100.dp)).background(Primary.copy(0.1f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = Primary, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Text(description, style = MaterialTheme.typography.labelSmall, color = Outline)
            }
            Icon(Icons.Filled.ChevronRight, null, tint = Outline, modifier = Modifier.size(20.dp))
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
internal fun GrowthStatCard(
    label: String, value: String, status: String, modifier: Modifier = Modifier,
    statusColor: Color = Color(0xFF2E7D32), icon: ImageVector = Icons.Filled.MonitorWeight, onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Primary.copy(0.08f)), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = Primary, modifier = Modifier.size(20.dp))
                }
                Surface(shape = RoundedCornerShape(100.dp), color = statusColor.copy(0.1f)) {
                    Row(Modifier.padding(horizontal = 8.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        // Only claim "normal" with a checkmark when the status color is actually the
                        // "normal range" green (see GrowthReference) — otherwise it's a warning, not a pass.
                        val isNormal = statusColor == Color(0xFF2E7D32)
                        Icon(if (isNormal) Icons.Filled.Check else Icons.Filled.PriorityHigh, null, tint = statusColor, modifier = Modifier.size(10.dp))
                        Text(status, style = MaterialTheme.typography.labelSmall, color = statusColor, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                    }
                }
            }
            Column {
                Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = Outline, fontWeight = FontWeight.Bold)
                Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
        }
    }
}
