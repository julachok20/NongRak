@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var deleteAck by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(s.settingsTitle, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { navController.navigate(Screen.Notifications.route) }) {
                        Icon(Icons.Filled.Notifications, null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = {
            com.lullabyai.app.ui.components.AppBottomNav(
                selected = com.lullabyai.app.ui.components.BottomTab.SETTINGS,
                onHome = { navController.navigate(Screen.Dashboard.route) { launchSingleTop = true } },
                onChatbot = { navController.navigate(Screen.AiChat.route) { launchSingleTop = true } },
                onChild = { navController.navigate(Screen.ChildSummary.route) { launchSingleTop = true } },
                onStats = { navController.navigate(Screen.StatisticsInsights.route) { launchSingleTop = true } },
                onSettings = {}
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
                SettingsGroup {
                    SettingsRow(Icons.Filled.Person, s.editProfile, onClick = onNavigateToEditProfile)
                    Divider52()
                    SettingsRow(Icons.Filled.Lock, s.changePassword, onClick = { navController.navigate(Screen.ChangePassword.route) })
                }
            }

            item { SectionLabel(s.notificationSection) }
            item {
                SettingsGroup {
                    ToggleRow(Icons.Filled.Description, s.dailyReport, settings.dailyReport) { settingsViewModel.setDailyReport(it) }
                    Divider52()
                    ToggleRow(Icons.Filled.HealthAndSafety, s.healthAlert, settings.healthAlert) { settingsViewModel.setHealthAlert(it) }
                    Divider52()
                    ToggleRow(Icons.Outlined.Lightbulb, s.aiTips, settings.aiTips) { settingsViewModel.setAiTips(it) }
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
                    SettingsRow(
                        Icons.Filled.Widgets,
                        if (isThai) "วิดเจ็ตหน้าจอหลัก" else "Home screen widgets",
                        onClick = { navController.navigate(Screen.WidgetsIntro.route) }
                    )
                    Divider52()
                    SettingsRow(Icons.Filled.Info, s.aboutUs, onClick = { navController.navigate(Screen.AboutUs.route) })
                    Divider52()
                    SettingsRow(Icons.Filled.Security, s.privacyPolicy, onClick = { navController.navigate(Screen.PrivacyPolicy.route) })
                    Divider52()
                    SettingsRow(Icons.Filled.Assignment, s.termsOfUse, onClick = { navController.navigate(Screen.TermsOfUse.route) })
                    Divider52()
                    SettingsRow(Icons.Filled.HelpOutline, s.manual, onClick = { navController.navigate(Screen.HelpFaq.route) })
                }
            }

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
                        Icons.Filled.Storage,
                        if (isThai) "ข้อมูลที่ AI เก็บไว้" else "Data the AI keeps",
                        onClick = { navController.navigate(Screen.MedicalDataVault.route) }
                    )
                    Divider52()
                    SettingsRow(
                        Icons.Filled.DeleteForever,
                        if (isThai) "ลบบัญชีถาวร" else "Delete account",
                        tint = Error,
                        onClick = { deleteAck = false; showDeleteDialog = true }
                    )
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
                    onClick = { showLogoutDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        Icon(Icons.Filled.Logout, null, tint = Error, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(s.logout, style = MaterialTheme.typography.labelLarge, color = Error, fontWeight = FontWeight.Bold)
                    }
                }
            }

            item {
                Text("© 2026 NongRak. All rights reserved.", style = MaterialTheme.typography.labelSmall, color = Outline, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
            }
        }
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
