@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.settings

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.lullabyai.app.data.settings.AppLanguage
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.AuthViewModel
import com.lullabyai.app.viewmodel.SettingsViewModel
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch

private val OkGreen = Color(0xFF12805C)
private val WarnOrange = Color(0xFFB45309)

/**
 * Settings — Stitch "ตั้งค่าบัญชีผู้ใช้ (User Settings) - NongRak AI".
 *
 * Every row here does something real; design rows are added as each is built, not drawn first.
 * The design's weekly email summary row is dropped on purpose (owner decision 2026-09-16: no email
 * sending service). 2FA stays visibly unavailable until authenticator-app MFA ships.
 */
@Composable
fun SettingsScreen(
    authViewModel: AuthViewModel,
    settingsViewModel: SettingsViewModel,
    navController: NavController,
    onLogout: () -> Unit
) {
    val s = LocalStrings.current
    val settings by settingsViewModel.settings.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val context = LocalContext.current
    val isThai = settings.language == AppLanguage.TH
    val isGuest = authViewModel.getCurrentUserId() == null || authState.profile == null || authState.profile?.id == "guest"

    // Read on every visit, so an unlink or a confirmation elsewhere never shows stale here.
    var lineLink by remember { mutableStateOf<com.lullabyai.app.data.repository.LineLink?>(null) }
    var googleLinked by remember { mutableStateOf(false) }
    var accountEmail by remember { mutableStateOf<String?>(null) }
    var emailConfirmed by remember { mutableStateOf(false) }
    var biometricLoginOn by remember {
        mutableStateOf(runCatching {
            val me = authViewModel.getCurrentUserId()
            me != null && com.lullabyai.app.data.settings.BiometricLogin.enrolledUserId(context) == me
        }.getOrDefault(false))
    }
    LaunchedEffect(isGuest) {
        if (isGuest) return@LaunchedEffect
        authViewModel.loadProfile()
        googleLinked = com.lullabyai.app.data.repository.AuthRepository().linkedProviders().contains("google")
        val user = runCatching { com.lullabyai.app.data.remote.supabase.auth.currentUserOrNull() }.getOrNull()
        accountEmail = user?.email
        emailConfirmed = user?.emailConfirmedAt != null
        if (com.lullabyai.app.data.repository.LineRepository.isConfigured) {
            lineLink = com.lullabyai.app.data.repository.LineRepository.status().getOrNull()
        }
    }
    val appVersion = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "-"
    }

    val scope = rememberCoroutineScope()
    val childRepo = remember { com.lullabyai.app.data.repository.ChildRepository() }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showExportChoice by remember { mutableStateOf(false) }
    var editingField by remember { mutableStateOf<AccountField?>(null) }
    var showAvatarPicker by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showOwnerBlockDialog by remember { mutableStateOf(false) }
    var deleteAck by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            com.lullabyai.app.ui.components.LullabyTopBar(
                title = s.settingsTitle,
                navController = navController,
                showProfile = false,
                titleContent = {
                    Column {
                        Text(
                            if (isThai) "ตั้งค่าบัญชีผู้ใช้" else "User Settings",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Text(
                            if (isThai) "จัดการข้อมูลส่วนตัว ความปลอดภัย และบัญชีของคุณ" else "Your profile, security and account",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
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
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            if (isGuest) {
                item { GuestCard(isThai, navController) }
            } else {
                // ---------------- Profile card ----------------
                item {
                    val profile = authState.profile
                    val avatarUrl = authState.avatarSignedUrl ?: profile?.avatarUrl
                    val displayName = profile?.fullName?.trim()?.takeIf { it.isNotEmpty() }
                        ?: profile?.username?.trim()?.takeIf { it.isNotEmpty() }
                        ?: (if (isThai) "ผู้ใช้งาน NongRak" else "NongRak User")
                    ProfileCard(
                        isThai = isThai,
                        name = displayName,
                        avatarUrl = avatarUrl,
                        email = accountEmail,
                        phone = profile?.phone,
                        emailConfirmed = emailConfirmed,
                        onEdit = { showAvatarPicker = true }
                    )
                }

                // ---------------- Personal info & account ----------------
                item { SectionHeader(Icons.Outlined.Person, if (isThai) "ข้อมูลส่วนตัวและบัญชี (Personal Info & Account)" else "Personal Info & Account") }
                item {
                    val profile = authState.profile
                    SettingsCard {
                        InfoRow(
                            label = if (isThai) "ชื่อผู้ใช้ (Username)" else "Username",
                            value = profile?.username?.takeIf { it.isNotBlank() } ?: (if (isThai) "ยังไม่ได้ตั้ง" else "Not set"),
                            trailingAction = if (isThai) "แก้ไข" else "Edit",
                            onClick = { editingField = AccountField.USERNAME }
                        )
                        RowDivider()
                        InfoRow(
                            label = if (isThai) "ชื่อ-นามสกุล (Full Name)" else "Full name",
                            value = profile?.fullName?.takeIf { it.isNotBlank() } ?: (if (isThai) "ยังไม่ได้ตั้ง" else "Not set"),
                            trailingAction = if (isThai) "แก้ไข" else "Edit",
                            onClick = { editingField = AccountField.FULL_NAME }
                        )
                        RowDivider()
                        InfoRow(
                            label = if (isThai) "อีเมลที่ลงทะเบียน (Email)" else "Registered email",
                            value = accountEmail ?: "-",
                            badge = {
                                StatusPill(
                                    text = if (emailConfirmed) (if (isThai) "ยืนยันแล้ว" else "Verified") else (if (isThai) "ยังไม่ยืนยัน" else "Not verified"),
                                    color = if (emailConfirmed) OkGreen else WarnOrange
                                )
                            },
                            trailingAction = if (isThai) "แก้ไข" else "Edit",
                            onClick = { editingField = AccountField.EMAIL }
                        )
                        RowDivider()
                        InfoRow(
                            label = if (isThai) "เบอร์โทรศัพท์ (Phone)" else "Phone",
                            value = profile?.phone?.takeIf { it.isNotBlank() } ?: (if (isThai) "ยังไม่ได้เพิ่ม" else "Not added"),
                            trailingAction = if (isThai) "แก้ไข" else "Edit",
                            onClick = { editingField = AccountField.PHONE }
                        )
                    }
                }
                item {
                    Text(
                        if (isThai) "บัญชีภายนอกที่เชื่อมต่อ (Connected Accounts)" else "Connected accounts",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                    )
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ConnectedAccountRow(
                            icon = Icons.Outlined.AccountCircle,
                            title = "Google",
                            subtitle = if (googleLinked) accountEmail else null,
                            action = if (googleLinked) (if (isThai) "เชื่อมต่อแล้ว" else "Connected") else (if (isThai) "ยังไม่เชื่อมต่อ" else "Not connected"),
                            linked = googleLinked,
                            // Read-only on purpose: linking Google to an existing account is an OAuth
                            // round trip that can fail halfway; signing in with Google is the path.
                            onClick = {
                                Toast.makeText(
                                    context,
                                    if (googleLinked) (if (isThai) "บัญชีนี้เชื่อมกับ Google แล้ว" else "This account is linked to Google")
                                    else (if (isThai) "ออกจากระบบแล้วเลือก เข้าสู่ระบบด้วย Google เพื่อเชื่อมบัญชี" else "Sign out, then use Sign in with Google to link this account"),
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        )
                        ConnectedAccountRow(
                            icon = Icons.Outlined.Chat,
                            title = "LINE",
                            subtitle = lineLink.let { l ->
                                when {
                                    l == null -> "LINE Official Account"
                                    !l.notifyEnabled -> if (isThai) "เชื่อมต่อแล้ว • ปิดแจ้งเตือน" else "Linked • muted"
                                    else -> (l.displayName ?: "LINE") + (if (isThai) " • เชื่อมต่อแล้ว" else " • linked")
                                }
                            },
                            action = if (isThai) "ตั้งค่า Line OA" else "LINE OA settings",
                            linked = lineLink != null,
                            showChevron = true,
                            onClick = { navController.navigate(Screen.LineLink.route) }
                        )
                    }
                }

                // ---------------- Display & preferences ----------------
                item { SectionHeader(Icons.Outlined.Tune, if (isThai) "การแสดงผลและการใช้งาน (Display & Preferences)" else "Display & Preferences") }
                item {
                    SettingsCard {
                        SwitchRow(
                            title = if (isThai) "โหมดมืด (Dark Mode)" else "Dark mode",
                            subtitle = if (isThai) "ปรับชุดสีโทนเข้มเพื่อสบายตาในที่แสงน้อย" else "Darker colours for low light",
                            checked = settings.darkMode
                        ) { settingsViewModel.setDarkMode(it) }
                        RowDivider()
                        SwitchRow(
                            title = if (isThai) "บันทึกอัตโนมัติ โดยไม่ต้องกดยืนยัน" else "Auto-record without confirming",
                            subtitle = if (isThai) "AI บันทึกข้อมูลที่เข้าใจได้ทันที โดยไม่ถามก่อน" else "The AI saves what it understood without asking",
                            checked = settings.autoRecord
                        ) { settingsViewModel.setAutoRecord(it) }
                        RowDivider()
                        NavRow(
                            title = if (isThai) "วิดเจ็ตหน้าจอหลัก (Home Screen Widgets)" else "Home screen widgets",
                            subtitle = if (isThai) "เมนูตั้งค่าวิดเจ็ตและลำดับการแสดงผล" else "Set up widgets for your home screen",
                            onClick = { navController.navigate(Screen.WidgetsIntro.route) }
                        )
                    }
                }

                // ---------------- AI & your data ----------------
                item { SectionHeader(Icons.Outlined.Psychology, if (isThai) "การปรับแต่ง AI และข้อมูลของคุณ (AI Customization & Personal Data)" else "AI Customization & Personal Data") }
                item {
                    SettingsCard {
                        IconNavRow(
                            icon = Icons.Outlined.SmartToy,
                            title = if (isThai) "ตั้งค่า AI เอง (Custom AI Preferences)" else "Custom AI preferences",
                            subtitle = if (isThai) "ปรับแต่งน้ำเสียงและสไตล์การแนะนำ" else "Tone and answer style",
                            onClick = { navController.navigate(Screen.AiPreferences.route) }
                        )
                        RowDivider(start = 68.dp)
                        IconNavRow(
                            icon = Icons.Outlined.ManageAccounts,
                            title = if (isThai) "ข้อมูลที่ NongRak รู้จักคุณ (Data NongRak knows)" else "What NongRak knows about you",
                            subtitle = if (isThai) "จัดการบริบทส่วนบุคคลเพื่อความแม่นยำของคำตอบ" else "Manage the context that shapes its answers",
                            onClick = { navController.navigate(Screen.AiParentStyle.route) }
                        )
                        RowDivider(start = 68.dp)
                        IconNavRow(
                            icon = Icons.Outlined.Storage,
                            title = if (isThai) "ข้อมูลที่ AI เก็บไว้" else "Data the AI keeps",
                            subtitle = if (isThai) "ดูและลบความจำการสนทนาของ AI" else "See and clear what the AI remembers",
                            onClick = { navController.navigate(Screen.AiDataRetention.route) }
                        )
                        RowDivider(start = 68.dp)
                        SwitchRow(
                            title = s.aiTrainingConsent,
                            subtitle = s.aiTrainingConsentDesc,
                            checked = authState.profile?.aiTrainingConsent ?: false
                        ) { authViewModel.setAiTrainingConsent(it) }
                    }
                }

                // ---------------- Security & login ----------------
                item { SectionHeader(Icons.Outlined.Shield, if (isThai) "ความปลอดภัยและการเข้าสู่ระบบ (Security & Login)" else "Security & Login") }
                item {
                    SettingsCard {
                        IconNavRow(
                            icon = Icons.Outlined.Key,
                            title = if (isThai) "เปลี่ยนรหัสผ่าน (Change Password)" else "Change password",
                            subtitle = if (isThai) "ตั้งรหัสผ่านใหม่สำหรับเข้าสู่ระบบ" else "Set a new sign-in password",
                            onClick = { navController.navigate(Screen.ChangePassword.route) }
                        )
                        RowDivider(start = 68.dp)
                        IconNavRow(
                            icon = Icons.Outlined.Devices,
                            title = if (isThai) "ประวัติการเข้าสู่ระบบและอุปกรณ์ (Active Sessions)" else "Signed-in devices (Active Sessions)",
                            subtitle = com.lullabyai.app.util.DeviceLabel.parse(com.lullabyai.app.util.DeviceLabel.userAgent())
                                ?.let { it + (if (isThai) " (อุปกรณ์ปัจจุบัน)" else " (this device)") }
                                ?: (if (isThai) "ดูและออกจากระบบอุปกรณ์อื่น" else "See and sign out other devices"),
                            onClick = { navController.navigate(Screen.ActiveSessions.route) }
                        )
                        RowDivider(start = 68.dp)
                        if (com.lullabyai.app.util.AppLock.isSupported()) {
                            IconSwitchRow(
                                icon = Icons.Outlined.Fingerprint,
                                title = if (isThai) "เข้าสู่ระบบด้วยลายนิ้วมือ / ใบหน้า" else "Sign in with fingerprint / face",
                                subtitle = if (isThai) "หลังออกจากระบบ แตะครั้งเดียวก็กลับเข้ามาได้ • 1 บัญชีต่อเครื่อง"
                                else "After logging out, one tap signs you back in • one account per phone",
                                checked = biometricLoginOn
                            ) { turnOn ->
                                val biometricLogin = com.lullabyai.app.data.settings.BiometricLogin
                                val me = authViewModel.getCurrentUserId()
                                when {
                                    !turnOn -> {
                                        runCatching { biometricLogin.clear(context) }
                                        biometricLoginOn = false
                                    }
                                    me == null -> Unit
                                    !com.lullabyai.app.util.AppLock.isDeviceSecure(context) -> Toast.makeText(
                                        context,
                                        if (isThai) "ตั้งรหัสล็อกหน้าจอ หรือลายนิ้วมือ/ใบหน้าในเครื่องก่อน" else "Set up a screen lock, fingerprint or face on this phone first",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    else -> com.lullabyai.app.util.AppLock.authenticate(
                                        context,
                                        if (isThai) "ตั้งค่าเข้าสู่ระบบด้วยลายนิ้วมือ" else "Set up fingerprint sign-in",
                                        if (isThai) "ยืนยันว่าเครื่องนี้สแกนได้" else "Confirm this phone can scan you"
                                    ) { ok, message ->
                                        if (ok) {
                                            runCatching { biometricLogin.enroll(context, me, accountEmail) }
                                            biometricLoginOn = true
                                        } else if (message != null) {
                                            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            }
                            RowDivider(start = 68.dp)
                        }
                        // Visibly unavailable until authenticator-app MFA is built: an ON switch that
                        // protects nothing is worse than an honest OFF (see DeviceTrustGate history).
                        IconSwitchRow(
                            icon = Icons.Outlined.PhonelinkLock,
                            title = if (isThai) "ยืนยันตัวตน 2 ขั้นตอน (2FA)" else "Two-factor authentication (2FA)",
                            subtitle = if (isThai) "ยืนยันด้วยแอป Authenticator • กำลังพัฒนา เปิดใช้ได้เร็วๆ นี้" else "Authenticator app • coming soon",
                            checked = false,
                            enabled = false
                        ) {}
                        if (com.lullabyai.app.util.AppLock.isSupported()) {
                            RowDivider(start = 68.dp)
                            IconSwitchRow(
                                icon = Icons.Outlined.Fingerprint,
                                title = if (isThai) "ล็อกแอปด้วยใบหน้า / ลายนิ้วมือ" else "Lock with face / fingerprint",
                                subtitle = if (isThai) "สแกนใบหน้า ลายนิ้วมือ หรือใส่รหัสเครื่องก่อนเปิดแอป" else "Face, fingerprint or screen lock before the app opens",
                                checked = settings.appLock
                            ) { turnOn ->
                                val appLock = com.lullabyai.app.util.AppLock
                                when {
                                    !turnOn -> settingsViewModel.setAppLock(false)
                                    !appLock.isDeviceSecure(context) -> Toast.makeText(
                                        context,
                                        if (isThai) "ตั้งรหัสล็อกหน้าจอ หรือลายนิ้วมือ/ใบหน้าในเครื่องก่อน" else "Set up a screen lock, fingerprint or face on this phone first",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    // Confirm once before switching on, so the lock is known to open on this phone.
                                    else -> appLock.authenticate(
                                        context,
                                        if (isThai) "เปิดล็อกแอป" else "Turn on app lock",
                                        if (isThai) "ยืนยันว่าเครื่องนี้ปลดล็อกได้" else "Confirm this phone can unlock it"
                                    ) { ok, message ->
                                        if (ok) {
                                            appLock.unlocked = true
                                            settingsViewModel.setAppLock(true)
                                        } else if (message != null) {
                                            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ---------------- Notifications ----------------
                item { SectionHeader(Icons.Outlined.Notifications, if (isThai) "การตั้งค่าการแจ้งเตือนส่วนบุคคล (Personal Notifications)" else "Personal Notifications") }
                item {
                    SettingsCard {
                        SwitchRow(
                            title = if (isThai) "แจ้งเตือนข้อความพุชในมือถือ (Push Notifications)" else "Push notifications",
                            subtitle = if (isThai) "เตือนกิจกรรมและการแจ้งเตือนด่วน" else "Activities and urgent alerts",
                            checked = settings.pushNotifications
                        ) { settingsViewModel.setPushNotifications(it) }
                        RowDivider()
                        SwitchRow(
                            title = s.dailyReport,
                            subtitle = if (isThai) "สรุปรายงานสุขภาพของลูก" else "Your child's health summary",
                            checked = settings.dailyReport && settings.pushNotifications,
                            enabled = settings.pushNotifications
                        ) { settingsViewModel.setDailyReport(it) }
                        RowDivider()
                        SwitchRow(
                            title = s.healthAlert,
                            subtitle = if (isThai) "วัคซีน ยา และอาการที่ต้องติดตาม" else "Vaccines, medicines and symptoms to watch",
                            checked = settings.healthAlert && settings.pushNotifications,
                            enabled = settings.pushNotifications
                        ) { settingsViewModel.setHealthAlert(it) }
                        RowDivider()
                        SwitchRow(
                            title = s.aiTips,
                            subtitle = if (isThai) "เคล็ดลับการดูแลตามวัยจาก AI" else "Age-based care tips from the AI",
                            checked = settings.aiTips && settings.pushNotifications,
                            enabled = settings.pushNotifications
                        ) { settingsViewModel.setAiTips(it) }
                        RowDivider()
                        NavRow(
                            title = if (isThai) "ศูนย์การแจ้งเตือนและประวัติ" else "Notification center",
                            subtitle = if (isThai) "ดูการแจ้งเตือนที่ผ่านมาทั้งหมด" else "Everything you were notified about",
                            onClick = { navController.navigate(Screen.Notifications.route) }
                        )
                    }
                }
            }

            // ---------------- Language ----------------
            item { SectionHeader(Icons.Outlined.Language, if (isThai) "ภาษาและภูมิภาค (Language & Region)" else "Language & Region") }
            item {
                SettingsCard {
                    IconNavRow(
                        icon = Icons.Outlined.Translate,
                        title = if (isThai) "ภาษาของแอป (Language)" else "App language",
                        value = if (settings.language == AppLanguage.TH) "ภาษาไทย (Thai)" else "English",
                        onClick = { settingsViewModel.toggleLanguage() }
                    )
                }
            }

            // ---------------- About & legal ----------------
            item { SectionHeader(Icons.Outlined.Info, if (isThai) "เกี่ยวกับและข้อกฎหมาย (About & Legal)" else "About & Legal") }
            item {
                SettingsCard {
                    IconNavRow(Icons.Outlined.StarBorder, if (isThai) "รีวิวแอปพลิเคชัน (Review Application)" else "Review the app") {
                        navController.navigate(Screen.Feedback.route)
                    }
                    RowDivider(start = 52.dp)
                    IconNavRow(Icons.Outlined.MenuBook, if (isThai) "คู่มือการใช้งาน (User Guide)" else "User guide") {
                        navController.navigate(Screen.HelpFaq.route)
                    }
                    RowDivider(start = 52.dp)
                    IconNavRow(Icons.Outlined.Business, if (isThai) "เกี่ยวกับเรา (About Us)" else "About us") {
                        navController.navigate(Screen.AboutUs.route)
                    }
                    RowDivider(start = 52.dp)
                    IconNavRow(Icons.Outlined.Gavel, if (isThai) "ข้อกำหนดการใช้งาน (Terms of Service)" else "Terms of service") {
                        navController.navigate(Screen.TermsOfUse.route)
                    }
                    RowDivider(start = 52.dp)
                    IconNavRow(Icons.Outlined.PrivacyTip, if (isThai) "นโยบายความเป็นส่วนตัว (Privacy Policy)" else "Privacy policy") {
                        navController.navigate(Screen.PrivacyPolicy.route)
                    }
                    // Debug builds only: pointing the app at a self-hosted model is a developer task.
                    if (com.lullabyai.app.BuildConfig.DEBUG) {
                        RowDivider(start = 52.dp)
                        IconNavRow(Icons.Outlined.Dns, if (isThai) "ตั้งค่า AI Endpoint (dev)" else "AI endpoint (dev)") {
                            navController.navigate(Screen.AiEndpoint.route)
                        }
                    }
                }
            }

            if (!isGuest) {
                // ---------------- Data & account ----------------
                item { SectionHeader(Icons.Outlined.FolderShared, if (isThai) "การจัดการข้อมูลและบัญชี (Data & Account Management)" else "Data & Account Management") }
                item {
                    SettingsCard {
                        IconNavRow(
                            icon = Icons.Outlined.CloudDownload,
                            title = if (isThai) "ดาวน์โหลดสำเนาข้อมูลของคุณ (Export Data)" else "Download a copy of your data",
                            subtitle = if (isThai) "รับไฟล์ข้อมูลส่วนบุคคลและการใช้งาน (JSON/PDF)" else "Your personal and usage data (JSON/PDF)",
                            trailingIcon = Icons.Outlined.Download,
                            onClick = { showExportChoice = true }
                        )
                        RowDivider(start = 68.dp)
                        IconNavRow(
                            icon = Icons.Outlined.Folder,
                            title = if (isThai) "คลังเอกสาร (Document Vault)" else "Document vault",
                            subtitle = if (isThai) "เอกสารทางการแพทย์และใบรับรองของลูก" else "Your child's medical documents",
                            onClick = { navController.navigate(Screen.MedicalDataVault.route) }
                        )
                        RowDivider(start = 68.dp)
                        IconNavRow(
                            icon = Icons.Outlined.DeleteForever,
                            title = if (isThai) "ลบบัญชีผู้ใช้ถาวร (Delete Account)" else "Delete account permanently",
                            subtitle = if (isThai) "ลบข้อมูลบัญชีและประวัติการใช้งานทั้งหมดแบบถาวร" else "Removes your account and all history for good",
                            danger = true,
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

            // ---------------- Log out ----------------
            item {
                OutlinedButton(
                    onClick = { if (isGuest) onLogout() else showLogoutDialog = true },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, if (isGuest) Primary else Error.copy(alpha = 0.5f)),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Icon(
                        if (isGuest) Icons.Filled.ExitToApp else Icons.Filled.Logout,
                        null,
                        tint = if (isGuest) Primary else Error,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (isGuest) (if (isThai) "ออกจากโหมดทดลองใช้งาน" else "Exit demo mode")
                        else (if (isThai) "ออกจากระบบ (Log Out)" else "Log out"),
                        color = if (isGuest) Primary else Error,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            item {
                Text(
                    "NongRak AI v$appVersion • " + (if (isThai) "การปกป้องข้อมูลตามมาตรฐาน PDPA" else "Data protected under PDPA"),
                    style = MaterialTheme.typography.labelSmall,
                    color = Outline,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                )
            }
        }
    }

    if (showOwnerBlockDialog) {
        AlertDialog(
            onDismissRequest = { showOwnerBlockDialog = false },
            icon = { Icon(Icons.Filled.WarningAmber, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(if (isThai) "ไม่สามารถลบบัญชีได้" else "Cannot Delete Account", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    if (isThai)
                        "คุณยังเป็นเจ้าของข้อมูลลูก กรุณาโอนสิทธิ์ความเป็นเจ้าของใน \"การจัดการสิทธิ์เข้าถึง\" หรือลบโปรไฟล์เด็กก่อนดำเนินการลบบัญชี"
                    else
                        "You are still the primary owner of child records. Please transfer ownership in Permission Access or delete the child profile before deleting your account."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showOwnerBlockDialog = false
                    navController.navigate(Screen.PermissionAccess.route)
                }) {
                    Text(if (isThai) "ไปที่การจัดการสิทธิ์เข้าถึง" else "Go to Permission Access", color = Primary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showOwnerBlockDialog = false }) { Text(if (isThai) "ปิด" else "Close") }
            }
        )
    }

    editingField?.let { field ->
        EditAccountFieldDialog(
            field = field,
            current = when (field) {
                AccountField.USERNAME -> authState.profile?.username
                AccountField.FULL_NAME -> authState.profile?.fullName
                AccountField.EMAIL -> accountEmail
                AccountField.PHONE -> authState.profile?.phone
            },
            isThai = isThai,
            onDismiss = { editingField = null },
            onSaved = { message ->
                editingField = null
                authViewModel.loadProfile()
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        )
    }

    if (showAvatarPicker) {
        AvatarPickerSheet(
            isThai = isThai,
            onDismiss = { showAvatarPicker = false },
            onDone = { message ->
                showAvatarPicker = false
                authViewModel.loadProfile()
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showExportChoice) {
        // PDPA export as a file, not share-sheet text: chat history can exceed the Binder limit for EXTRA_TEXT.
        fun export(pdf: Boolean) {
            showExportChoice = false
            authViewModel.exportData { result ->
                result.mapCatching { data ->
                    val today = java.time.LocalDate.now().toString()
                    val file = if (pdf) {
                        com.lullabyai.app.util.DataExportPdf.write(context, data.toReport(today))
                    } else {
                        java.io.File(java.io.File(context.cacheDir, "exports").apply { mkdirs() }, "nongrak_my_data_$today.json")
                            .apply { writeText(data.toJson(today)) }
                    }
                    val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = if (pdf) "application/pdf" else "application/json"
                        putExtra(Intent.EXTRA_SUBJECT, if (isThai) "ข้อมูลส่วนบุคคลของฉัน - NongRak AI" else "My Data - NongRak AI")
                        putExtra(Intent.EXTRA_STREAM, uri)
                        clipData = android.content.ClipData.newRawUri("export", uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(send, if (isThai) "ส่งออกข้อมูล" else "Export data"))
                }.onFailure {
                    Toast.makeText(
                        context,
                        (if (isThai) "ส่งออกข้อมูลไม่สำเร็จ: " else "Export failed: ") + (it.message ?: it::class.simpleName),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
        AlertDialog(
            onDismissRequest = { showExportChoice = false },
            title = { Text(if (isThai) "ดาวน์โหลดสำเนาข้อมูล" else "Download your data", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    if (isThai) "PDF อ่านง่ายสำหรับเก็บไว้หรือพิมพ์ • JSON ข้อมูลดิบครบทุกช่อง สำหรับย้ายไปใช้กับระบบอื่น"
                    else "PDF is easy to read or print. JSON has every raw field, for moving to another service."
                )
            },
            confirmButton = { TextButton(onClick = { export(pdf = true) }) { Text("PDF", fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { export(pdf = false) }) { Text("JSON") } }
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
                    settingsViewModel.setAppLock(false)  // the next person to sign in on this phone chooses their own
                    authViewModel.signOut(onLogout)
                }) { Text(s.logout, color = Error) }
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
                        Text(if (isThai) "ฉันเข้าใจว่าการกระทำนี้ไม่สามารถย้อนกลับได้" else "I understand this cannot be undone", style = MaterialTheme.typography.bodySmall)
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

// ============================ building blocks ============================

@Composable
private fun GuestCard(isThai: Boolean, navController: NavController) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Primary.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, Primary.copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(36.dp).clip(CircleShape).background(Primary.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
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
                    modifier = Modifier.weight(1f).height(40.dp)
                ) { Text(if (isThai) "สมัครสมาชิก" else "Sign up", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                OutlinedButton(
                    onClick = { navController.navigate(Screen.Login.route) },
                    shape = RoundedCornerShape(100.dp),
                    modifier = Modifier.weight(1f).height(40.dp)
                ) { Text(if (isThai) "เข้าสู่ระบบ" else "Sign in", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Primary) }
            }
        }
    }
}

@Composable
private fun ProfileCard(
    isThai: Boolean,
    name: String,
    avatarUrl: String?,
    email: String?,
    phone: String?,
    emailConfirmed: Boolean,
    onEdit: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier
                .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.surface, Primary.copy(alpha = 0.06f))))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.size(84.dp).clickable(onClick = onEdit)) {
                    Box(
                        Modifier
                            .size(84.dp)
                            .clip(CircleShape)
                            .background(PrimaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!avatarUrl.isNullOrBlank()) {
                            coil.compose.AsyncImage(
                                model = avatarUrl,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(Icons.Filled.Person, null, tint = Primary, modifier = Modifier.size(44.dp))
                        }
                    }
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 2.dp,
                        modifier = Modifier.align(Alignment.BottomEnd).size(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.CameraAlt, contentDescription = if (isThai) "เปลี่ยนรูปโปรไฟล์" else "Change photo", tint = Primary, modifier = Modifier.size(15.dp))
                        }
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Surface(shape = RoundedCornerShape(100.dp), color = Primary.copy(alpha = 0.10f)) {
                        Row(Modifier.padding(horizontal = 10.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Box(Modifier.size(6.dp).clip(CircleShape).background(Primary))
                            Text(if (isThai) "บัญชีผู้ใช้ (User Account)" else "User account", style = MaterialTheme.typography.labelMedium, color = Primary, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    if (!email.isNullOrBlank()) ContactLine(Icons.Outlined.Mail, email)
                    if (!phone.isNullOrBlank()) ContactLine(Icons.Outlined.Phone, phone)
                }
            }
            // Account status: only says "verified" when the email really is confirmed.
            Surface(shape = RoundedCornerShape(16.dp), color = Primary.copy(alpha = 0.05f), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) {
                        Icon(
                            if (emailConfirmed) Icons.Outlined.VerifiedUser else Icons.Outlined.GppMaybe,
                            null,
                            tint = if (emailConfirmed) OkGreen else WarnOrange,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(if (isThai) "สถานะบัญชี:" else "Account status:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        if (emailConfirmed) (if (isThai) "ยืนยันตัวตนแล้ว • ปลอดภัย" else "Verified • secure")
                        else (if (isThai) "ยังไม่ยืนยันอีเมล" else "Email not verified"),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = if (emailConfirmed) OkGreen else WarnOrange,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Box(Modifier.size(8.dp).clip(CircleShape).background(if (emailConfirmed) OkGreen else WarnOrange))
                }
            }
        }
    }
}

@Composable
private fun ContactLine(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SectionHeader(icon: ImageVector, title: String) {
    Row(
        Modifier.fillMaxWidth().padding(start = 4.dp, top = 14.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(icon, null, tint = Primary, modifier = Modifier.size(20.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
        Column(content = content)
    }
}

@Composable
private fun RowDivider(start: androidx.compose.ui.unit.Dp = 18.dp) =
    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.padding(start = start))

@Composable
private fun StatusPill(text: String, color: Color) {
    Surface(shape = RoundedCornerShape(100.dp), color = color.copy(alpha = 0.12f)) {
        Text(text, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = color)
    }
}

/** Label above a value (Stitch personal-info rows). */
@Composable
private fun InfoRow(
    label: String,
    value: String,
    badge: (@Composable () -> Unit)? = null,
    trailingAction: String? = null,
    onClick: () -> Unit
) {
    Surface(onClick = onClick, color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    badge?.invoke()
                }
            }
            if (trailingAction != null) {
                Surface(shape = RoundedCornerShape(100.dp), color = Primary.copy(alpha = 0.10f)) {
                    Text(trailingAction, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp), style = MaterialTheme.typography.labelLarge, color = Primary, fontWeight = FontWeight.SemiBold)
                }
            } else {
                Icon(Icons.Filled.ChevronRight, null, tint = Outline)
            }
        }
    }
}

@Composable
private fun ConnectedAccountRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    action: String,
    linked: Boolean,
    showChevron: Boolean = false,
    onClick: () -> Unit
) {
    Surface(onClick = onClick, shape = RoundedCornerShape(16.dp), color = Primary.copy(alpha = 0.05f), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, null, tint = Primary, modifier = Modifier.size(24.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (!subtitle.isNullOrBlank()) {
                    Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Surface(shape = RoundedCornerShape(100.dp), color = if (linked) Primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(action, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = if (linked) Primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    if (showChevron) Icon(Icons.Filled.ChevronRight, null, tint = Primary, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun NavRow(title: String, subtitle: String? = null, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Filled.ChevronRight, null, tint = Outline)
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String? = null, checked: Boolean, enabled: Boolean = true, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = if (enabled) MaterialTheme.colorScheme.onSurface else Outline)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Primary)
        )
    }
}

@Composable
private fun IconBox(icon: ImageVector, danger: Boolean) {
    Box(
        Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(if (danger) Error.copy(alpha = 0.10f) else Primary.copy(alpha = 0.08f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = if (danger) Error else Primary, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun IconNavRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    value: String? = null,
    trailingIcon: ImageVector = Icons.Filled.ChevronRight,
    danger: Boolean = false,
    onClick: () -> Unit
) {
    Surface(onClick = onClick, color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (subtitle != null) IconBox(icon, danger) else Icon(icon, null, tint = if (danger) Error else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = if (danger) Error else MaterialTheme.colorScheme.onSurface)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (value != null) Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(trailingIcon, null, tint = if (danger) Error else Outline, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun IconSwitchRow(icon: ImageVector, title: String, subtitle: String, checked: Boolean, enabled: Boolean = true, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        IconBox(icon, danger = false)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = if (enabled) MaterialTheme.colorScheme.onSurface else Outline)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Primary))
    }
}
