@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.access

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.lullabyai.app.ui.components.LullabyTopBar
import com.lullabyai.app.ui.components.PrimaryButton
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.util.AccessQrHelper
import com.lullabyai.app.viewmodel.AccessPermissionViewModel
import com.lullabyai.app.viewmodel.AccessRoleLabels
import kotlinx.coroutines.delay

/**
 * Stitch: "Share Access QR Code" — QR + 10-minute countdown + regenerate + security notice.
 */
@Composable
fun ShareAccessQrScreen(
    viewModel: AccessPermissionViewModel,
    navController: NavController,
    onBack: () -> Unit
) {
    val s = LocalStrings.current
    val isThai = s.isThai
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var role by remember { mutableStateOf("co_owner") }
    var remaining by remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {
        viewModel.load()
        viewModel.regenerateQr(role)
    }

    LaunchedEffect(state.invite?.code, state.invite?.expiresAt) {
        while (true) {
            remaining = remainingSeconds(state.invite?.expiresAt)
            if (remaining <= 0L && state.invite != null) break
            delay(1000)
        }
    }

    val qrBitmap = remember(state.invite?.payload, state.invite?.code) {
        val payload = state.invite?.payload?.ifBlank { null }
            ?: state.invite?.code?.let { AccessQrHelper.payloadForCode(it) }
        payload?.let { AccessQrHelper.encodeBitmap(it, 640) }
    }

    Scaffold(
        topBar = {
            LullabyTopBar(
                title = if (isThai) "แชร์ QR Code" else "Share QR Code",
                onBack = onBack,
                navController = navController
            )
        },
        bottomBar = { AccessPermissionBottomNav(navController) }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 88.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                state.childName.ifBlank { if (isThai) "น้องรัก" else "NongRak" },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Primary
            )
            Text(
                if (isThai) "ให้ผู้ที่อยู่ใกล้สแกนเพื่อรับสิทธิ์เข้าถึง"
                else "Have the person nearby scan to receive access permissions",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Text(if (isThai) "บทบาทที่จะมอบ" else "Role to grant", fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                AccessRoleLabels.ROLES.take(3).forEach { r ->
                    FilterChip(
                        selected = role == r,
                        onClick = {
                            role = r
                            viewModel.regenerateQr(r)
                        },
                        label = { Text(AccessRoleLabels.title(r, isThai).substringBefore(" (")) }
                    )
                }
            }

            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Box(
                    Modifier
                        .padding(20.dp)
                        .size(260.dp)
                        .background(androidx.compose.ui.graphics.Color.White, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        state.saving || qrBitmap == null -> CircularProgressIndicator(color = Primary)
                        remaining <= 0L -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.TimerOff, null, tint = Error, modifier = Modifier.size(48.dp))
                            Text(if (isThai) "หมดอายุแล้ว" else "Expired", color = Error, fontWeight = FontWeight.Bold)
                        }
                        else -> Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = "QR",
                            modifier = Modifier.fillMaxSize().padding(8.dp)
                        )
                    }
                }
            }

            Text(
                state.invite?.code ?: "————",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp
            )
            Text(
                if (isThai) "สแกนไม่ได้? พิมพ์รหัส 6 หลักนี้ที่หน้า เชื่อมต่อบัญชี → Access Code"
                else "Scan not working? Type this 6-character code on Sync Child → Access Code",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .background(
                        if (remaining in 1..60) Error.copy(0.12f) else Primary.copy(0.1f),
                        RoundedCornerShape(100.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Filled.Timer, null, tint = if (remaining in 1..60) Error else Primary)
                Text(
                    if (isThai) "หมดอายุใน ${formatCountdown(remaining)}"
                    else "Expires in ${formatCountdown(remaining)}",
                    fontWeight = FontWeight.Bold,
                    color = if (remaining in 1..60) Error else Primary
                )
            }

            OutlinedButton(
                onClick = { viewModel.regenerateQr(role) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(100.dp)
            ) {
                Icon(Icons.Filled.Refresh, null)
                Spacer(Modifier.width(8.dp))
                Text(if (isThai) "สร้างใหม่อีกครั้ง (Regenerate)" else "Regenerate")
            }

            PrimaryButton(
                text = if (isThai) "แชร์รหัส" else "Share code",
                onClick = {
                    val inv = state.invite ?: return@PrimaryButton
                    val text = if (isThai) {
                        "สแกน QR หรือใช้รหัส ${inv.code} ใน NongRak AI (บทบาท ${AccessRoleLabels.title(inv.role, true)}, หมดอายุ 10 นาที)\n${inv.payload}"
                    } else {
                        "Scan QR or use code ${inv.code} in NongRak AI (role ${AccessRoleLabels.title(inv.role, false)}, 10-min TTL)\n${inv.payload}"
                    }
                    context.startActivity(
                        Intent.createChooser(
                            Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, text)
                            },
                            if (isThai) "แชร์" else "Share"
                        )
                    )
                },
                enabled = state.invite != null && remaining > 0
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Security, null, tint = Primary)
                        Text(
                            if (isThai) "ข้อมูลด้านความปลอดภัย (Security Info)" else "Security Info",
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        if (isThai) "รหัสนี้ใช้ได้ครั้งเดียวและหมดอายุภายใน 10 นาที แสดงเฉพาะกับบุคคลที่คุณไว้วางใจเท่านั้น"
                        else "This code is single-use and expires in 10 minutes. Only show it to people you trust.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            state.error?.let {
                Text(it, color = Error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
