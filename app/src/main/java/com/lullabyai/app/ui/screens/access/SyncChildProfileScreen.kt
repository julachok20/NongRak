@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.access

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.core.content.ContextCompat
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.lullabyai.app.ui.components.AppBottomNav
import com.lullabyai.app.ui.components.BottomTab
import com.lullabyai.app.ui.components.BirthDatePickerField
import com.lullabyai.app.ui.components.LullabyTopBar
import com.lullabyai.app.ui.components.PrimaryButton
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.AccessRequestStrings
import com.lullabyai.app.viewmodel.AccessRequestViewModel
import com.lullabyai.app.viewmodel.AccessRoleLabels

/**
 * Stitch: "Sync Child Profile (เชื่อมต่อบัญชี)" — three ways in (name search, QR scan, 6-char access
 * code), one confirm button, and the security note that nothing is granted until the owner approves.
 *
 * The owner's Realtime approve/deny popup is hosted here too, so an owner who happens to be on this
 * screen still sees incoming requests (see [PendingAccessRequestDialog]).
 */
@Composable
fun SyncChildProfileScreen(
    viewModel: AccessRequestViewModel,
    navController: NavController,
    onBack: () -> Unit,
    onDone: () -> Unit,
    onSeePendingRequests: () -> Unit = {},
    footerTab: BottomTab = BottomTab.CHILD
) {
    val s = LocalStrings.current
    val isThai = s.isThai
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    var roleMenuOpen by remember { mutableStateOf(false) }
    var cameraDenied by remember { mutableStateOf(false) }

    val qrScanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.trim()?.takeIf { it.isNotBlank() }?.let { viewModel.submitScannedQr(it) }
    }

    fun launchEmbeddedQrScan() {
        qrScanLauncher.launch(
            ScanOptions().apply {
                setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                setPrompt(
                    if (isThai) "จ่อ QR Code ให้อยู่กลางกรอบ"
                    else "Align the QR code within the frame"
                )
                setBeepEnabled(true)
                setBarcodeImageEnabled(false)
                setOrientationLocked(false)
            }
        )
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            cameraDenied = false
            launchEmbeddedQrScan()
        } else {
            cameraDenied = true
        }
    }

    fun openQrScanner() {
        cameraDenied = false
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            launchEmbeddedQrScan()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Owner-side live watch: a scan on someone else's phone pops up here within a second.
    LaunchedEffect(Unit) { viewModel.startWatching() }

    state.popup?.let { req ->
        PendingAccessRequestDialog(
            request = req,
            childName = state.nameFor(req.childId),
            isThai = isThai,
            busy = state.resolving,
            onApprove = { viewModel.resolve(req.id, true) },
            onDeny = { viewModel.resolve(req.id, false) },
            onDismiss = { viewModel.dismissPopup() }
        )
    }

    Scaffold(
        topBar = {
            LullabyTopBar(
                title = if (isThai) "เชื่อมต่อบัญชี" else "Sync child profile",
                onBack = onBack,
                navController = navController
            )
        },
        bottomBar = {
            if (footerTab == BottomTab.CHILD) {
                AccessPermissionBottomNav(navController)
            } else {
                AppBottomNav(
                    selected = footerTab,
                    onHome = {},
                    onChatbot = {},
                    onChild = {},
                    onStats = {},
                    onSettings = {},
                    onConnect = {}
                )
            }
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // --- Introduction header (Stitch: 64dp primary/10 tile + headline + paragraph) --------
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier
                        .padding(top = 8.dp)
                        .size(64.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Primary.copy(0.10f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Sync, null, tint = Primary, modifier = Modifier.size(36.dp))
                }
                Text(
                    if (isThai) "ซิงค์ข้อมูลลูกน้อยของคุณ" else "Sync your child's profile",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    if (isThai)
                        "เชื่อมต่อบัญชีเดิมเพื่อรวมข้อมูลการเจริญเติบโต ประวัติการรักษา และตารางวัคซีนไว้ในที่เดียว ช่วยให้คุณและครอบครัวดูแลลูกน้อยได้อย่างต่อเนื่องและมีประสิทธิภาพมากขึ้น"
                    else
                        "Link an existing account to bring growth data, medical history, and the vaccine schedule together in one place, so you and your family can care for your little one without gaps.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 22.sp
                )
            }

            // --- Sync card (Stitch "glass-card": white, 2xl radius, ambient shadow) ---------------
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceContainerLowest),
                elevation = CardDefaults.cardElevation(3.dp)
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {

                    // Your role. A picker rather than free text: the backend only accepts
                    // co_owner / doctor / nanny / other, so a typed value would be silently discarded.
                    SyncFieldLabel(if (isThai) "ระบุบทบาทของคุณ" else "Your role")
                    ExposedDropdownMenuBox(
                        expanded = roleMenuOpen,
                        onExpandedChange = { roleMenuOpen = it }
                    ) {
                        OutlinedTextField(
                            value = AccessRoleLabels.title(state.role, isThai),
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier.fillMaxWidth().menuAnchor(
                                MenuAnchorType.PrimaryNotEditable, true
                            ),
                            placeholder = {
                                Text(if (isThai) "เช่น มารดา, บิดา, แพทย์" else "e.g. mother, father, doctor")
                            },
                            trailingIcon = { Icon(Icons.Filled.Person, null, tint = Outline) },
                            shape = RoundedCornerShape(12.dp),
                            colors = syncFieldColors()
                        )
                        ExposedDropdownMenu(expanded = roleMenuOpen, onDismissRequest = { roleMenuOpen = false }) {
                            // Before a child is found there is no family to ask, so the starter
                            // roles stand in. Once one is picked this becomes that family's own
                            // list — including roles they renamed or created themselves.
                            val found = state.availableRoles
                            if (found.isEmpty()) {
                                AccessRoleLabels.ROLES.forEach { r ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(
                                                    AccessRoleLabels.title(r, isThai),
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    AccessRoleLabels.subtitle(r, isThai),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        },
                                        onClick = { viewModel.setRole(r); roleMenuOpen = false }
                                    )
                                }
                            } else {
                                found.forEach { role ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(
                                                    if (isThai) role.nameTh else role.nameEn,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                // Only starter roles carry a written description;
                                                // a family's own role has just a name.
                                                AccessRoleLabels.subtitle(role.key, isThai)
                                                    .takeIf { it.isNotBlank() }?.let {
                                                        Text(
                                                            it,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                            }
                                        },
                                        onClick = { viewModel.setRole(role.key); roleMenuOpen = false }
                                    )
                                }
                            }
                        }
                    }

                    // Child lookup. Birthdate sits alongside the name because the lookup matches on
                    // both — name alone would let anyone enumerate other families' children.
                    SyncFieldLabel(
                        if (isThai) "ชื่อผู้ใช้ของลูกน้อย (Searching)" else "Child's name (search)"
                    )
                    OutlinedTextField(
                        value = state.childQuery,
                        onValueChange = viewModel::setChildQuery,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = {
                            Text(if (isThai) "ระบุชื่อผู้ใช้ของลูก..." else "Enter the child's name...")
                        },
                        trailingIcon = { Icon(Icons.Filled.Search, null, tint = Outline) },
                        shape = RoundedCornerShape(12.dp),
                        colors = syncFieldColors()
                    )
                    BirthDatePickerField(
                        value = state.birthdate,
                        onValueChange = viewModel::setBirthdate,
                        placeholder = if (isThai) "แตะเพื่อเลือกวันเกิดของลูก" else "Tap to pick the child's birthdate",
                        isThai = isThai,
                        fieldColors = { syncFieldColors() }
                    )
                    OutlinedButton(
                        onClick = viewModel::search,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !state.searching
                    ) {
                        if (state.searching) {
                            CircularProgressIndicator(Modifier.size(18.dp), color = Primary, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.Search, null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (isThai) "ค้นหา" else "Search")
                        }
                    }

                    if (state.searchRan && state.searchResults.isEmpty() && !state.searching) {
                        Text(
                            if (isThai) "ไม่พบข้อมูลลูกที่ตรงกัน ตรวจสอบชื่อและวันเกิดอีกครั้ง"
                            else "No matching child found. Double-check the name and birthdate.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    state.searchResults.forEach { match ->
                        val picked = state.selectedMatchId == match.childId
                        Card(
                            onClick = { viewModel.selectMatch(match.childId) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (picked) Primary.copy(0.12f) else SurfaceContainerLow
                            )
                        ) {
                            Row(
                                Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                RadioButton(
                                    selected = picked,
                                    onClick = { viewModel.selectMatch(match.childId) },
                                    colors = RadioButtonDefaults.colors(selectedColor = Primary)
                                )
                                Text(match.childName, Modifier.weight(1f), fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    SyncOrDivider(isThai)

                    // Scan QR — Stitch dashed primary/30 border on primary/5
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Primary.copy(0.05f))
                            .border(2.dp, Primary.copy(0.30f), RoundedCornerShape(12.dp))
                            .clickable(enabled = !state.submitting) { openQrScanner() }
                            .padding(vertical = 16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.QrCodeScanner, null, tint = Primary)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            if (isThai) "สแกน QR Code เพื่อเชื่อมต่อ" else "Scan a QR code to connect",
                            color = Primary,
                            fontWeight = FontWeight.Medium,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }

                    SyncOrDivider(isThai)

                    if (cameraDenied) {
                        Text(
                            if (isThai) "ต้องอนุญาตกล้องในการตั้งค่า — หรือพิมพ์รหัส 6 หลักด้านล่าง"
                            else "Camera permission denied — allow camera in Settings, or type the 6-character code below",
                            color = Error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    SyncFieldLabel(if (isThai) "รหัสการเข้าถึง (Access Code)" else "Access code")
                    OutlinedTextField(
                        value = state.accessCode,
                        onValueChange = viewModel::setAccessCode,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text(if (isThai) "ระบุรหัส 6 หลัก..." else "Enter the 6-character code...") },
                        trailingIcon = { Icon(Icons.Filled.Key, null, tint = Outline) },
                        shape = RoundedCornerShape(12.dp),
                        colors = syncFieldColors()
                    )
                }
            }

            // --- Security note --------------------------------------------------------------------
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SecondaryContainer.copy(0.20f))
                    .border(1.dp, SecondaryContainer.copy(0.40f), RoundedCornerShape(12.dp))
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(Icons.Filled.VerifiedUser, null, tint = Secondary)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        if (isThai) "เพื่อความปลอดภัยของข้อมูล" else "For your data's safety",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (isThai)
                            "คำขอการเชื่อมต่อจะต้องได้รับการอนุมัติจากเจ้าของบัญชีหลักก่อน คุณจึงจะสามารถเข้าถึงข้อมูลของลูกน้อยได้"
                        else
                            "A connection request must be approved by the primary account owner before you can access the child's data.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // --- Request sent / error / primary action --------------------------------------------
            if (state.requestSent) {
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Primary.copy(0.08f))
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.HourglassTop, null, tint = Primary)
                            Text(
                                if (isThai) "ส่งคำขอแล้ว" else "Request sent",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Primary
                            )
                        }
                        Text(
                            if (isThai) "รอเจ้าของบัญชีหลักอนุมัติ คุณจะได้รับการแจ้งเตือนเมื่ออนุมัติแล้ว"
                            else "Waiting for the primary account owner to approve. You'll be notified once they do.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        PrimaryButton(
                            text = if (isThai) "เสร็จสิ้น" else "Done",
                            onClick = {
                                viewModel.clearRequestSent()
                                onDone()
                            }
                        )
                    }
                }
            } else {
                AccessRequestStrings.error(state.error, isThai)?.let {
                    Text(it, color = Error, style = MaterialTheme.typography.bodySmall)
                }
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PrimaryButton(
                        text = if (isThai) "ยืนยันการเชื่อมต่อ" else "Confirm connection",
                        onClick = viewModel::submit,
                        isLoading = state.submitting,
                        enabled = state.canSubmit
                    )
                    Text(
                        if (isThai)
                            "โดยการคลิกยืนยัน คุณยอมรับข้อกำหนดการใช้งานและการเข้าถึงข้อมูลส่วนบุคคลตามนโยบายความเป็นส่วนตัว"
                        else
                            "By confirming you accept the terms of use and the handling of personal data described in the privacy policy.",
                        style = MaterialTheme.typography.labelSmall,
                        color = Outline,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    )
                }
            }

            // Owner shortcut: requests that arrived while the app was closed live in the pending list.
            if (state.pending.isNotEmpty()) {
                TextButton(onClick = onSeePendingRequests, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.NotificationsActive, null, tint = Primary)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (isThai) "มีคำขอรออนุมัติ ${state.pending.size} รายการ"
                        else "${state.pending.size} request(s) waiting for your approval",
                        color = Primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun SyncFieldLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp)
    )
}

@Composable
private fun SyncOrDivider(isThai: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        HorizontalDivider(Modifier.weight(1f), color = OutlineVariant)
        Text(
            if (isThai) "หรือ" else "OR",
            style = MaterialTheme.typography.labelSmall,
            color = OutlineVariant,
            fontWeight = FontWeight.SemiBold
        )
        HorizontalDivider(Modifier.weight(1f), color = OutlineVariant)
    }
}

/** Stitch fields are filled (surface-container-low), borderless, 12dp radius. */
@Composable
private fun syncFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Primary,
    unfocusedBorderColor = Color.Transparent,
    focusedContainerColor = SurfaceContainerLow,
    unfocusedContainerColor = SurfaceContainerLow,
    focusedTextColor = Primary,
    unfocusedTextColor = Primary
)
