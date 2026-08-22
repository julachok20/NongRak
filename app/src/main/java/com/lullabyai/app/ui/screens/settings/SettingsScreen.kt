@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import io.github.jan.supabase.auth.auth
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import android.content.Intent
import android.widget.Toast
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.lullabyai.app.data.settings.AppLanguage
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.AuthViewModel
import com.lullabyai.app.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    authViewModel: AuthViewModel,
    settingsViewModel: SettingsViewModel,
    navController: NavController,
    onNavigateToEditProfile: () -> Unit,
    onLogout: () -> Unit
) {
    val s = LocalStrings.current
    val settings by settingsViewModel.settings.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val context = LocalContext.current
    val isThai = settings.language == AppLanguage.TH
    val isGuest = authViewModel.getCurrentUserId() == null || authState.profile == null || authState.profile?.id == "guest"
    // Real linked state for the two account rows. Read on every visit rather than cached, so
    // unlinking on another device shows up here instead of a stale green badge.
    var lineLink by remember { mutableStateOf<com.lullabyai.app.data.repository.LineLink?>(null) }
    var googleLinked by remember { mutableStateOf(false) }
    var googleEmail by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(isGuest) {
        if (isGuest) return@LaunchedEffect
        googleLinked = com.lullabyai.app.data.repository.AuthRepository().linkedProviders().contains("google")
        googleEmail = runCatching { com.lullabyai.app.data.remote.supabase.auth.currentUserOrNull()?.email }.getOrNull()
        if (com.lullabyai.app.data.repository.LineRepository.isConfigured) {
            lineLink = com.lullabyai.app.data.repository.LineRepository.status().getOrNull()
        }
    }
    val scope = rememberCoroutineScope()
    val childRepo = remember { com.lullabyai.app.data.repository.ChildRepository() }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showOwnerBlockDialog by remember { mutableStateOf(false) }
    var deleteAck by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            com.lullabyai.app.ui.components.LullabyTopBar(
                title = s.settingsTitle,
                onBack = null,
                navController = navController,
                showProfile = false
            )
        },
        bottomBar = {
            com.lullabyai.app.ui.components.AppBottomNav(
                selected = com.lullabyai.app.ui.components.BottomTab.SETTINGS,
                onHome = { navController.navigate(Screen.Dashboard.route) { launchSingleTop = true } },
                onChatbot = { navController.navigate(Screen.AiChat.route) { launchSingleTop = true } },
                onChild = { navController.navigate(Screen.ChildSummary.route) { launchSingleTop = true } },
                onStats = { navController.navigate(Screen.StatisticsInsights.route) { launchSingleTop = true } },
                onSettings = {},
                onConnect = { navController.navigate(Screen.Maintenance.route) { launchSingleTop = true } }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            item { SectionLabel(s.accountSection) }
            item {
                if (isGuest) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Primary.copy(alpha = 0.08f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Primary.copy(alpha = 0.25f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Box(Modifier.size(36.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Primary.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Filled.Lock, null, tint = Primary, modifier = Modifier.size(20.dp))
                                    }
                                    Column {
                                        Text(if (isThai) "บัญชีทดลองใช้งาน (Test Account)" else "Test / Demo Account", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge, color = Primary)
                                        Text(if (isThai) "สมัครสมาชิกเพื่อใช้งานได้ครบทุกฟีเจอร์" else "Sign up to unlock all features", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = { navController.navigate(Screen.SignUp.route) },
                                        shape = RoundedCornerShape(100.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Primary),
                                        modifier = Modifier.weight(1f).height(38.dp)
                                    ) {
                                        Text(if (isThai) "สมัครสมาชิก" else "Sign up", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                    OutlinedButton(
                                        onClick = { navController.navigate(Screen.Login.route) },
                                        shape = RoundedCornerShape(100.dp),
                                        modifier = Modifier.weight(1f).height(38.dp)
                                    ) {
                                        Text(if (isThai) "เข้าสู่ระบบ" else "Sign in", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Primary)
                                    }
                                }
                            }
                        }

                        // Graphify: Account Linking placeholder for Google OAuth & Line OA notification integration
                        SettingsGroup {
                            AccountLinkRow(
                                icon = Icons.Filled.AccountCircle,
                                iconColor = Color(0xFF4285F4),
                                title = if (isThai) "เชื่อมต่อบัญชี Google" else "Google Account",
                                subtitle = if (isThai) "ซิงค์ข้อมูลและเข้าสู่ระบบด้วย Google" else "Sync data & sign in with Google",
                                statusText = if (isThai) "ยังไม่เชื่อมต่อ" else "Not Linked",
                                isLinked = false,
                                onClick = { /* Graphify: Google OAuth link action left as no-op */ }
                            )
                            Divider52()
                            AccountLinkRow(
                                icon = Icons.Filled.Chat,
                                iconColor = Color(0xFF06C755),
                                title = if (isThai) "เชื่อมต่อ LINE Official Account (LINE OA)" else "LINE OA Account",
                                subtitle = if (isThai) "รับการแจ้งเตือนและข้อมูลสุขภาพผ่าน LINE" else "Receive notifications & health tips on LINE",
                                statusText = if (isThai) "ยังไม่เชื่อมต่อ" else "Not Linked",
                                isLinked = false,
                                onClick = { /* Graphify: Line OA link action left as no-op */ }
                            )
                        }
                    }
                } else {
                    SettingsGroup {
                        SettingsRow(Icons.Filled.Person, s.editProfile, onClick = onNavigateToEditProfile)
                        Divider52()
                        SettingsRow(Icons.Filled.Lock, s.changePassword, onClick = { navController.navigate(Screen.ChangePassword.route) })
                        Divider52()
                        // Graphify: Account Linking placeholder for Google OAuth & Line OA notification integration
                        AccountLinkRow(
                            icon = Icons.Filled.AccountCircle,
                            iconColor = Color(0xFF4285F4),
                            title = if (isThai) "เชื่อมต่อบัญชี Google" else "Google Account",
                            subtitle = if (isThai) "ซิงค์ข้อมูลและเข้าสู่ระบบด้วย Google" else "Sync data & sign in with Google",
                            statusText = if (googleLinked) (googleEmail ?: (if (isThai) "เชื่อมต่อแล้ว" else "Linked"))
                                         else (if (isThai) "ยังไม่เชื่อมต่อ" else "Not Linked"),
                            isLinked = googleLinked,
                            // Read-only on purpose. Linking Google to an account that already
                            // exists is an OAuth round trip that can fail halfway and leave a
                            // half-linked identity; the supported path is signing in with
                            // Google, which the login screen already offers.
                            onClick = {
                                Toast.makeText(
                                    context,
                                    if (googleLinked) (if (isThai) "บัญชีนี้เชื่อมกับ Google แล้ว" else "This account is linked to Google")
                                    else (if (isThai) "ออกจากระบบแล้วเลือก เข้าสู่ระบบด้วย Google เพื่อเชื่อมบัญชี" else "Sign out, then use Sign in with Google to link this account"),
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        )
                        Divider52()
                        AccountLinkRow(
                            icon = Icons.Filled.Chat,
                            iconColor = Color(0xFF06C755),
                            title = if (isThai) "เชื่อมต่อ LINE Official Account (LINE OA)" else "LINE OA Account",
                            subtitle = if (isThai) "รับการแจ้งเตือนและข้อมูลสุขภาพผ่าน LINE" else "Receive notifications & health tips on LINE",
                            statusText = lineLink.let { l ->
                                when {
                                    l == null -> if (isThai) "ยังไม่เชื่อมต่อ" else "Not Linked"
                                    !l.notifyEnabled -> if (isThai) "ปิดแจ้งเตือน" else "Muted"
                                    else -> l.displayName ?: (if (isThai) "เชื่อมต่อแล้ว" else "Linked")
                                }
                            },
                            isLinked = lineLink != null,
                            onClick = { navController.navigate(Screen.LineLink.route) }
                        )
                    }
                }
            }

            item { SectionLabel(s.notificationSection) }
            item {
                SettingsGroup {
                    SettingsRow(
                        icon = Icons.Filled.Notifications,
                        label = if (isThai) "ศูนย์การแจ้งเตือนและประวัติ" else "Notification Center",
                        value = if (isThai) "เปิดดู" else "View",
                        onClick = { navController.navigate(Screen.Notifications.route) }
                    )
                    Divider52()
                    ToggleRow(
                        icon = Icons.Filled.NotificationsActive,
                        label = if (isThai) "เปิดการแจ้งเตือนทั้งหมด (Push Notifications)" else "Allow Push Notifications",
                        checked = settings.pushNotifications
                    ) { settingsViewModel.setPushNotifications(it) }
                    Divider52()
                    ToggleRow(
                        icon = Icons.Filled.Description,
                        label = s.dailyReport,
                        checked = settings.dailyReport && settings.pushNotifications
                    ) { if (settings.pushNotifications) settingsViewModel.setDailyReport(it) }
                    Divider52()
                    ToggleRow(
                        icon = Icons.Filled.HealthAndSafety,
                        label = s.healthAlert,
                        checked = settings.healthAlert && settings.pushNotifications
                    ) { if (settings.pushNotifications) settingsViewModel.setHealthAlert(it) }
                    Divider52()
                    ToggleRow(
                        icon = Icons.Outlined.Lightbulb,
                        label = s.aiTips,
                        checked = settings.aiTips && settings.pushNotifications
                    ) { if (settings.pushNotifications) settingsViewModel.setAiTips(it) }
                    Divider52()
                    ToggleRow(
                        icon = Icons.Filled.AutoMode,
                        label = if (s.isThai) "บันทึกอัตโนมัติ (ไม่ต้องกดยืนยัน)" else "Auto record (no confirmation)",
                        checked = settings.autoRecord
                    ) { settingsViewModel.setAutoRecord(it) }
                }
            }

            item { SectionLabel(s.preferenceSection) }
            item {
                SettingsGroup {
                    SettingsRow(
                        Icons.Filled.Language, s.language,
                        value = if (settings.language == AppLanguage.TH) "ไทย" else "English",
                        onClick = { settingsViewModel.toggleLanguage() }
                    )
                    Divider52()
                    ToggleRow(Icons.Filled.DarkMode, s.darkMode, settings.darkMode) { settingsViewModel.setDarkMode(it) }
                }
            }

            item { SectionLabel(s.appInfoSection) }
            item {
                SettingsGroup {
                    if (!isGuest) {
                        SettingsRow(
                            Icons.Filled.Widgets,
                            if (isThai) "วิดเจ็ตหน้าจอหลัก" else "Home screen widgets",
                            onClick = { navController.navigate(Screen.WidgetsIntro.route) }
                        )
                        Divider52()
                    }
                    SettingsRow(Icons.Filled.Info, s.aboutUs, onClick = { navController.navigate(Screen.AboutUs.route) })
                    Divider52()
                    SettingsRow(
                        Icons.Filled.RateReview,
                        if (isThai) "ข้อเสนอแนะและรีวิวแอป" else "Feedback & Review",
                        onClick = { navController.navigate(Screen.Feedback.route) }
                    )
                    Divider52()
                    SettingsRow(Icons.Filled.Security, s.privacyPolicy, onClick = { navController.navigate(Screen.PrivacyPolicy.route) })
                    Divider52()
                    SettingsRow(Icons.Filled.Assignment, s.termsOfUse, onClick = { navController.navigate(Screen.TermsOfUse.route) })
                    Divider52()
                    SettingsRow(Icons.Filled.HelpOutline, s.manual, onClick = { navController.navigate(Screen.HelpFaq.route) })
                }
            }

            if (!isGuest) {
                item { SectionLabel(if (isThai) "ความเป็นส่วนตัว (PDPA)" else "Privacy (PDPA)") }
                item {
                    SettingsGroup {
                        SettingsRow(
                            Icons.Filled.Download,
                            if (isThai) "ขอข้อมูลของฉันทั้งหมด" else "Export all my data",
                            onClick = {
                                authViewModel.exportData { json ->
                                    if (json != null) {
                                        val send = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_SUBJECT, if (isThai) "ข้อมูลส่วนบุคคลของฉัน - NongRak AI" else "My Data - NongRak AI")
                                            putExtra(Intent.EXTRA_TEXT, json)
                                        }
                                        context.startActivity(Intent.createChooser(send, if (isThai) "ส่งออกข้อมูล" else "Export data"))
                                    } else {
                                        Toast.makeText(context, if (isThai) "ส่งออกข้อมูลไม่สำเร็จ" else "Export failed", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        )
                        Divider52()
                        ToggleRow(Icons.Filled.Psychology, s.aiTrainingConsent, authState.profile?.aiTrainingConsent ?: false) { authViewModel.setAiTrainingConsent(it) }
                        Text(
                            s.aiTrainingConsentDesc,
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 50.dp, end = 16.dp, bottom = 10.dp)
                        )
                        Divider52()
                        SettingsRow(
                            Icons.Filled.Folder,
                            if (isThai) "คลังเอกสาร (Vault)" else "Document Vault",
                            onClick = { navController.navigate(Screen.MedicalDataVault.route) }
                        )
                        Divider52()
                        SettingsRow(
                            Icons.Filled.Storage,
                            if (isThai) "ข้อมูลที่ AI เก็บไว้" else "Data the AI keeps",
                            onClick = { navController.navigate(Screen.AiDataRetention.route) }
                        )
                        Divider52()
                        SettingsRow(
                            Icons.Filled.DeleteForever,
                            if (isThai) "ลบบัญชีถาวร" else "Delete account",
                            tint = Error,
                            onClick = {
                                scope.launch {
                                    val owned = childRepo.getOwnedChildren().getOrDefault(emptyList())
                                    if (owned.isNotEmpty()) {
                                        showOwnerBlockDialog = true
                                    } else {
                                        deleteAck = false
                                        showDeleteDialog = true
                                    }
                                }
                            }
                        )
                    }
                }
            }

            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(s.appVersion, style = MaterialTheme.typography.bodySmall, color = Outline)
                    Text("v1.0.3", style = MaterialTheme.typography.bodySmall, color = Outline)
                }
            }

            item {
                Card(
                    onClick = {
                        if (isGuest) {
                            onLogout()
                        } else {
                            showLogoutDialog = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        Icon(
                            if (isGuest) Icons.Filled.ExitToApp else Icons.Filled.Logout,
                            null,
                            tint = if (isGuest) Primary else Error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (isGuest) (if (isThai) "ออกจากโหมดทดลองใช้งาน" else "Exit Demo Mode") else s.logout,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isGuest) Primary else Error,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            item {
                Text("© 2026 NongRak. All rights reserved.", style = MaterialTheme.typography.labelSmall, color = Outline, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    if (showOwnerBlockDialog) {
        AlertDialog(
            onDismissRequest = { showOwnerBlockDialog = false },
            icon = { Icon(Icons.Filled.WarningAmber, null, tint = MaterialTheme.colorScheme.error) },
            title = {
                Text(
                    if (isThai) "ไม่สามารถลบบัญชีได้" else "Cannot Delete Account",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    if (isThai)
                        "คุณยังเป็นเจ้าของข้อมูลลูก กรุณาโอนสิทธิ์ความเป็นเจ้าของใน \"การจัดการสิทธิ์เข้าถึง\" หรือลบโปรไฟล์เด็กก่อนดำเนินการลบบัญชี"
                    else
                        "You are still the primary owner of child records. Please transfer ownership in Permission Access or delete the child profile before deleting your account."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showOwnerBlockDialog = false
                        navController.navigate(Screen.PermissionAccess.route)
                    }
                ) {
                    Text(
                        if (isThai) "ไปที่การจัดการสิทธิ์เข้าถึง" else "Go to Permission Access",
                        color = Primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showOwnerBlockDialog = false }) {
                    Text(if (isThai) "ปิด" else "Close")
                }
            }
        )
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text(s.logout) },
            text = { Text(s.logoutConfirm) },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutDialog = false
                    settingsViewModel.setDarkMode(false)  // reset dark mode on sign out
                    settingsViewModel.setLanguage(AppLanguage.TH)  // reset language to Thai on sign out
                    authViewModel.signOut(onLogout)
                }) {
                    Text(s.logout, color = Error)
                }
            },
            dismissButton = { TextButton(onClick = { showLogoutDialog = false }) { Text(s.cancel) } }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { if (!deleting) showDeleteDialog = false },
            icon = { Icon(Icons.Filled.DeleteForever, null, tint = Error) },
            title = { Text(if (isThai) "ลบบัญชีถาวร" else "Delete account") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        if (isThai)
                            "การลบบัญชีจะลบข้อมูลทั้งหมดอย่างถาวร ทั้งข้อมูลเด็ก ประวัติสุขภาพ พัฒนาการ วัคซีน รูปภาพ และประวัติการสนทนากับ AI การกระทำนี้ไม่สามารถย้อนกลับได้ และไม่สามารถกู้คืนได้"
                        else
                            "Deleting your account permanently removes ALL your data — children, health records, development, vaccines, photos, and AI chat history. This cannot be undone or recovered.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.clickable(enabled = !deleting) { deleteAck = !deleteAck }
                    ) {
                        Checkbox(checked = deleteAck, onCheckedChange = { deleteAck = it }, enabled = !deleting, colors = CheckboxDefaults.colors(checkedColor = Error))
                        Text(
                            if (isThai) "ฉันเข้าใจว่าการกระทำนี้ไม่สามารถย้อนกลับได้" else "I understand this cannot be undone",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = deleteAck && !deleting,
                    onClick = {
                        deleting = true
                        authViewModel.deleteAccount { ok ->
                            deleting = false
                            if (ok) {
                                settingsViewModel.setDarkMode(false)
                                settingsViewModel.setLanguage(AppLanguage.TH)  // reset language to Thai on account deletion
                                showDeleteDialog = false
                                onLogout()
                            } else {
                                Toast.makeText(context, if (isThai) "ลบบัญชีไม่สำเร็จ กรุณาลองใหม่" else "Couldn't delete account, please try again", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                ) {
                    if (deleting) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Error)
                    else Text(if (isThai) "ลบบัญชีถาวร" else "Delete forever", color = Error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }, enabled = !deleting) { Text(s.cancel) } }
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 4.dp), fontWeight = FontWeight.SemiBold)
}

@Composable
private fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(content = content)
    }
}

@Composable
private fun Divider52() = HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.padding(start = 52.dp))

@Composable
private fun SettingsRow(icon: ImageVector, label: String, value: String? = null, tint: Color? = null, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth(), color = Color.Transparent) {
        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, null, tint = tint ?: MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), color = tint ?: MaterialTheme.colorScheme.onSurface)
            if (value != null) Text(value, style = MaterialTheme.typography.bodyMedium, color = Primary)
            Icon(Icons.Filled.ChevronRight, null, tint = Outline, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun ToggleRow(icon: ImageVector, label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
        Switch(checked = checked, onCheckedChange = onCheckedChange, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Primary))
    }
}

/**
 * Graphify: Account Linking placeholder for Google OAuth & Line OA notification integration.
 * Left as non-blocking / no-op placeholder as requested.
 */
@Composable
private fun AccountLinkRow(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    subtitle: String,
    statusText: String,
    isLinked: Boolean = false,
    onClick: () -> Unit = {}
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(iconColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = iconColor, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                shape = RoundedCornerShape(100.dp),
                color = if (isLinked) Color(0xFF43A047).copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            ) {
                Text(
                    text = statusText,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = if (isLinked) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

