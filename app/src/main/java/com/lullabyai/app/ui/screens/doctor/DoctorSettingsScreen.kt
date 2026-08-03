@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.doctor

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.lullabyai.app.data.doctor.DoctorDriveFeatures
import com.lullabyai.app.data.settings.AppLanguage
import com.lullabyai.app.ui.components.LullabyTopBar
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.AuthViewModel
import com.lullabyai.app.viewmodel.DoctorViewModel
import com.lullabyai.app.viewmodel.SettingsViewModel

/**
 * "Doctor Settings (การตั้งค่าแพทย์)". Mirrors SettingsScreen.kt's grouped-rows structure, filled
 * in with the doctor-specific sections from the Stitch design: profile card, account (profile,
 * security, privacy, delete), general (notifications/language/dark mode), help, sign-out.
 */
@Composable
fun DoctorSettingsScreen(
    authViewModel: AuthViewModel,
    settingsViewModel: SettingsViewModel,
    doctorViewModel: DoctorViewModel,
    navController: NavController,
    onLogout: () -> Unit
) {
    val s = com.lullabyai.app.ui.localization.LocalStrings.current
    val isThai = s.isThai
    val context = LocalContext.current
    val settings by settingsViewModel.settings.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val doctorState by doctorViewModel.uiState.collectAsState()
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var deleteAck by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        authViewModel.loadProfile()
        authViewModel.getCurrentUserId()?.let { doctorViewModel.loadProfile(it) }
        if (DoctorDriveFeatures.IS_READY) doctorViewModel.checkDriveConnection()
    }

    val driveConsentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        doctorViewModel.onDriveConsentHandled(isThai) { ok, msg ->
            Toast.makeText(
                context,
                when {
                    ok -> if (isThai) "เชื่อมต่อ Google Drive แล้ว" else "Google Drive connected"
                    msg != null -> msg
                    else -> doctorViewModel.driveSetupHint(isThai)
                },
                Toast.LENGTH_LONG
            ).show()
        }
    }

    LaunchedEffect(doctorState.driveConsentIntent) {
        val intent = doctorState.driveConsentIntent ?: return@LaunchedEffect
        doctorViewModel.clearDriveConsentIntent()
        driveConsentLauncher.launch(intent)
    }

    doctorState.driveAccountPickerAccounts?.let { accounts ->
        DriveAccountPickerDialog(
            accounts = accounts,
            isThai = isThai,
            onDismiss = { doctorViewModel.dismissDriveAccountPicker() },
            onAccountSelected = { email ->
                doctorViewModel.connectDriveWithAccount(email, isThai) { ok, msg ->
                    Toast.makeText(
                        context,
                        when {
                            ok -> if (isThai) "เชื่อมต่อ Google Drive แล้ว" else "Google Drive connected"
                            msg != null -> msg
                            else -> doctorViewModel.driveSetupHint(isThai)
                        },
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        )
    }

    Scaffold(
        topBar = {
            LullabyTopBar(
                title = if (isThai) "การตั้งค่า" else "Settings",
                navController = navController,
                isDoctor = true
            )
        },
        bottomBar = { DoctorBottomNav(navController, DoctorTab.SETTINGS) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            item {
                Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        val avatarUrl = authState.avatarSignedUrl
                        Box(Modifier.size(56.dp).clip(CircleShape).background(Primary.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                            if (!avatarUrl.isNullOrBlank()) {
                                AsyncImage(model = avatarUrl, contentDescription = null, modifier = Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
                            } else {
                                Icon(Icons.Filled.Person, null, tint = Primary, modifier = Modifier.size(28.dp))
                            }
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                authState.profile?.fullName?.takeIf { it.isNotBlank() } ?: (if (isThai) "คุณหมอ" else "Doctor"),
                                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold
                            )
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF2E7D32)))
                                Text(if (isThai) "ออนไลน์อยู่" else "Active now", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        TextButton(onClick = { navController.navigate(Screen.DoctorEditProfile.route) }) {
                            Icon(Icons.Filled.Edit, null, tint = Primary, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (isThai) "แก้ไขโปรไฟล์" else "Edit profile", color = Primary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            item { SectionLabel(if (isThai) "บัญชี" else "Account") }
            item {
                SettingsGroup {
                    SettingsRow(
                        Icons.Filled.Badge,
                        if (isThai) "ข้อมูลส่วนตัว" else "Personal information",
                        subtitle = if (isThai) "เลขที่ใบอนุญาต, ข้อมูลต่าง ๆ" else "License number, other details",
                        onClick = { navController.navigate(Screen.DoctorEditProfile.route) }
                    )
                    Divider52()
                    SettingsRow(
                        Icons.Filled.Lock,
                        if (isThai) "บัญชีและความปลอดภัย" else "Account & security",
                        subtitle = if (isThai) "เปลี่ยนรหัสผ่าน" else "Change password",
                        onClick = { navController.navigate(Screen.DoctorChangePassword.route) }
                    )
                    Divider52()
                    SettingsRow(
                        Icons.Filled.PrivacyTip,
                        if (isThai) "ความเป็นส่วนตัว (PDPA)" else "Privacy (PDPA)",
                        onClick = { navController.navigate(Screen.DoctorPrivacyPolicy.route) }
                    )
                    Divider52()
                    SettingsRow(
                        Icons.Filled.DeleteForever,
                        if (isThai) "ลบบัญชีแพทย์" else "Delete doctor account",
                        subtitle = if (isThai) "ลบถาวรและออกจากระบบ" else "Permanently delete and sign out",
                        onClick = {
                            deleteAck = false
                            showDeleteConfirm = true
                        }
                    )
                }
            }

            item { SectionLabel(if (isThai) "การเชื่อมต่อ" else "Connections") }
            item {
                SettingsGroup {
                    if (DoctorDriveFeatures.IS_READY) {
                        DriveConnectionRow(
                            isThai = isThai,
                            driveChecking = doctorState.driveChecking,
                            driveConnected = doctorState.driveConnected,
                            driveConnectedAccount = doctorState.driveConnectedAccount,
                            onCheckConnection = { doctorViewModel.checkDriveConnection() },
                            onConnectDrive = {
                                doctorViewModel.connectDrive(isThai) { ok, msg ->
                                    Toast.makeText(
                                        context,
                                        when {
                                            ok -> if (isThai) "เชื่อมต่อ Google Drive แล้ว" else "Google Drive connected"
                                            msg != null -> msg
                                            else -> doctorViewModel.driveSetupHint(isThai)
                                        },
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            },
                            onChangeAccount = {
                                doctorViewModel.connectDrive(isThai, forceAccountPicker = true) { ok, msg ->
                                    if (ok || msg != null) {
                                        Toast.makeText(
                                            context,
                                            when {
                                                ok -> if (isThai) "เปลี่ยนบัญชี Google Drive แล้ว" else "Google Drive account changed"
                                                else -> msg!!
                                            },
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                            }
                        )
                    } else {
                        DriveComingSoonRow(isThai = isThai)
                    }
                }
            }

            item { SectionLabel(if (isThai) "ตั้งค่าทั่วไป" else "General") }
            item {
                SettingsGroup {
                    SettingsRow(
                        Icons.Filled.Notifications,
                        if (isThai) "การแจ้งเตือน" else "Notifications",
                        subtitle = if (isThai) "นัดหมายคนไข้, การแจ้งเตือนด่วน" else "Patient appointments, urgent alerts",
                        onClick = { navController.navigate(Screen.DoctorNotifications.route) }
                    )
                    Divider52()
                    SettingsRow(
                        Icons.Filled.Language, if (isThai) "ภาษา" else "Language",
                        value = if (isThai) "ไทย" else "English",
                        onClick = { settingsViewModel.toggleLanguage() }
                    )
                    Divider52()
                    ToggleRow(Icons.Filled.DarkMode, if (isThai) "โหมดมืด" else "Dark mode", settings.darkMode) { settingsViewModel.setDarkMode(it) }
                }
            }

            item { SectionLabel(if (isThai) "ช่วยเหลือ" else "Help") }
            item {
                SettingsGroup {
                    SettingsRow(Icons.Filled.HelpOutline, if (isThai) "ศูนย์ช่วยเหลือ" else "Help Center", onClick = { navController.navigate(Screen.DoctorHelpFaq.route) })
                    Divider52()
                    SettingsRow(Icons.Filled.Info, if (isThai) "เกี่ยวกับ NongRak AI" else "About NongRak AI", onClick = { navController.navigate(Screen.DoctorAboutNongRak.route) })
                }
            }

            item {
                Card(
                    onClick = { showLogoutDialog = true },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        Icon(Icons.Filled.Logout, null, tint = Error, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (isThai) "ออกจากระบบ" else "Sign out", style = MaterialTheme.typography.labelLarge, color = Error, fontWeight = FontWeight.Bold)
                    }
                }
            }

            item {
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.Center) {
                    Text("2.4.0 (Stable)", style = MaterialTheme.typography.labelSmall, color = Outline)
                }
            }
        }
    }

    // Decision #9 B: Sign out → Delete account now OR Just log out
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            icon = { Icon(Icons.Filled.Logout, null, tint = Error) },
            title = { Text(if (isThai) "ออกจากระบบ" else "Sign out") },
            text = {
                Text(
                    if (isThai)
                        "ออกจากระบบบัญชีแพทย์นี้บนอุปกรณ์นี้? คุณสามารถเข้าสู่ระบบอีกครั้งได้ทุกเมื่อ"
                    else
                        "Sign out of this doctor account on this device? You can sign in again anytime."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        authViewModel.signOut(onLogout)
                    }
                ) {
                    Text(if (isThai) "ออกจากระบบ" else "Sign out", color = Error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) { Text(s.cancel) }
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { if (!deleting) showDeleteConfirm = false },
            icon = { Icon(Icons.Filled.DeleteForever, null, tint = Error) },
            title = { Text(if (isThai) "ลบบัญชีแพทย์ถาวร" else "Delete doctor account") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        if (isThai)
                            "การลบบัญชีจะลบโปรไฟล์แพทย์และข้อมูลที่เกี่ยวข้องในระบบ NongRak การกระทำนี้ย้อนกลับไม่ได้"
                        else
                            "Deleting permanently removes your doctor profile and related data in NongRak. This cannot be undone."
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Checkbox(
                            checked = deleteAck,
                            onCheckedChange = { deleteAck = it },
                            enabled = !deleting,
                            colors = CheckboxDefaults.colors(checkedColor = Error)
                        )
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
                                doctorViewModel.clearDeviceBinding(context)
                                settingsViewModel.setDarkMode(false)
                                settingsViewModel.setLanguage(AppLanguage.TH)
                                showDeleteConfirm = false
                                onLogout()
                            } else {
                                val detail = authState.error
                                Toast.makeText(
                                    context,
                                    detail ?: if (isThai) "ลบบัญชีไม่สำเร็จ กรุณาลองใหม่" else "Couldn't delete account, please try again",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                ) {
                    if (deleting) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Error)
                    else Text(if (isThai) "ลบบัญชีทันที" else "Delete account now", color = Error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }, enabled = !deleting) { Text(s.cancel) }
            }
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
private fun SettingsRow(icon: ImageVector, label: String, subtitle: String? = null, value: String? = null, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth(), color = Color.Transparent) {
        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                subtitle?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = Outline) }
            }
            if (value != null) Text(value, style = MaterialTheme.typography.bodyMedium, color = Primary)
            Icon(Icons.Filled.ChevronRight, null, tint = Outline, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun DriveComingSoonRow(isThai: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(Icons.Filled.Cloud, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (isThai) "การเชื่อมต่อ Google Drive" else "Google Drive connection",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                if (isThai) "ฟีเจอร์นี้กำลังจะเปิดให้ใช้งานเร็วๆ นี้"
                else "This feature is coming soon",
                style = MaterialTheme.typography.labelSmall,
                color = Outline
            )
        }
        Surface(shape = RoundedCornerShape(100.dp), color = Primary.copy(0.12f)) {
            Text(
                if (isThai) "เร็วๆ นี้" else "Soon",
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Primary
            )
        }
    }
}

@Composable
private fun DriveConnectionRow(
    isThai: Boolean,
    driveChecking: Boolean,
    driveConnected: Boolean?,
    driveConnectedAccount: String?,
    onCheckConnection: () -> Unit,
    onConnectDrive: () -> Unit,
    onChangeAccount: () -> Unit
) {
    val statusSubtitle = when {
        driveChecking -> if (isThai) "กำลังตรวจสอบการเชื่อมต่อ..." else "Checking connection..."
        driveConnected == true && !driveConnectedAccount.isNullOrBlank() ->
            if (isThai) "บัญชี: $driveConnectedAccount" else "Account: $driveConnectedAccount"
        driveConnected == true -> if (isThai) "เชื่อมต่อแล้ว (Drive app-data)" else "Connected (Drive app-data)"
        driveConnected == false -> if (isThai) "ยังไม่เชื่อมต่อ — ทางเลือก ไม่บังคับ" else "Not connected — optional, not required"
        else -> if (isThai) "ทางเลือก — สำรองรายละเอียดคนไข้ใน Google Drive" else "Optional — backup patient details to Google Drive"
    }
    val statusValue = when {
        driveChecking -> if (isThai) "กำลังตรวจ..." else "Checking..."
        driveConnected == true -> if (isThai) "เชื่อมต่อแล้ว" else "Connected"
        driveConnected == false -> if (isThai) "ไม่เชื่อมต่อ" else "Not connected"
        else -> null
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Filled.Cloud, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (isThai) "การเชื่อมต่อ Google Drive" else "Google Drive connection",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(statusSubtitle, style = MaterialTheme.typography.labelSmall, color = Outline)
            }
            if (driveChecking) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Primary)
            } else if (statusValue != null) {
                Text(statusValue, style = MaterialTheme.typography.labelMedium, color = if (driveConnected == true) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (!driveChecking) {
            Row(modifier = Modifier.padding(start = 50.dp, end = 8.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (driveConnected != true) {
                    TextButton(onClick = onConnectDrive) {
                        Text(
                            if (isThai) "เชื่อมต่อ Google Drive" else "Connect Google Drive",
                            color = Primary,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                } else {
                    TextButton(onClick = onChangeAccount) {
                        Text(
                            if (isThai) "เปลี่ยนบัญชี" else "Change account",
                            color = Primary,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                TextButton(onClick = onCheckConnection) {
                    Text(
                        if (isThai) "ตรวจสอบการเชื่อมต่อ" else "Check connection",
                        color = Primary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
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
