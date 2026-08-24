package com.lullabyai.app.ui.screens.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lullabyai.app.data.repository.LineLink
import com.lullabyai.app.data.repository.LineRepository
import com.lullabyai.app.ui.components.LullabyTopBar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Pairing this account with the NongRak LINE OA.
 *
 * The app asks the server for a code, the parent sends that code to the OA, and the webhook
 * writes the link. Deliberately not LINE Login: the parent is already signed in here, so the
 * only missing fact is which LINE account is theirs — and the only party who can prove that is
 * LINE itself, talking to our webhook.
 */
@Composable
fun LineLinkScreen(
    navController: androidx.navigation.NavController,
    onBack: () -> Unit
) {
    val s = com.lullabyai.app.ui.localization.LocalStrings.current
    val isThai = s.isThai
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var link by remember { mutableStateOf<LineLink?>(null) }
    var code by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    // The 8-digit code for linking from a device that is not this phone.
    var otp by remember { mutableStateOf<com.lullabyai.app.data.repository.LineLinkCode?>(null) }
    // Somebody has entered a code and is waiting to be let in. Approving is the second factor.
    var request by remember { mutableStateOf<com.lullabyai.app.data.repository.LineLinkRequest?>(null) }

    suspend fun refresh() {
        link = LineRepository.status().getOrNull()
        loading = false
    }
    LaunchedEffect(Unit) { refresh() }

    // Polled for as long as this screen is open, not only while a code is on show. The parent
    // may issue a code, leave, then come back while the other device is still waiting.
    LaunchedEffect(Unit) {
        while (true) {
            request = LineRepository.pendingLinkRequest().getOrNull()
            delay(3_000)
        }
    }

    // While a code is on screen the parent is over in LINE sending it. Poll so that coming back
    // to the app simply shows "connected" — asking them to press refresh after they already did
    // the thing is the kind of small friction that makes a feature feel broken.
    LaunchedEffect(code) {
        if (code == null) return@LaunchedEffect
        while (link == null) {
            delay(3_000)
            link = LineRepository.status().getOrNull()
        }
        code = null
    }

    request?.let { req ->
        AlertDialog(
            // Deliberately not dismissible by tapping outside: this grants another device access
            // to a child's health record, so it should take an explicit yes or no.
            onDismissRequest = { },
            icon = { Icon(Icons.Filled.Chat, null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text(if (isThai) "อนุญาตการเชื่อมต่อ?" else "Allow this connection?") },
            text = {
                Text(
                    if (isThai)
                        "มีคนกรอกรหัสของคุณจากอุปกรณ์อื่น" +
                            (req.displayName?.let { "\n\nบัญชี LINE: " + it } ?: "") +
                            "\n\nถ้าไม่ใช่คุณ ให้กดปฏิเสธ"
                    else
                        "Someone entered your code on another device." +
                            (req.displayName?.let { "\n\nLINE account: " + it } ?: "") +
                            "\n\nIf this was not you, tap Reject."
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        scope.launch {
                            busy = true
                            LineRepository.confirmLinkCode(req.id)
                                .onSuccess { ok ->
                                    message = if (ok) {
                                        if (isThai) "เชื่อมต่อสำเร็จแล้ว" else "Connected."
                                    } else {
                                        if (isThai) "คำขอหมดอายุแล้ว" else "That request expired."
                                    }
                                }
                                .onFailure {
                                    message = if (isThai) "ยืนยันไม่สำเร็จ" else "Could not approve."
                                }
                            request = null
                            otp = null
                            refresh()
                            busy = false
                        }
                    }
                ) { Text(if (isThai) "อนุญาต" else "Allow") }
            },
            dismissButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        scope.launch {
                            busy = true
                            LineRepository.cancelLinkCode(req.id)
                            request = null
                            otp = null
                            message = if (isThai) "ปฏิเสธคำขอแล้ว" else "Request rejected."
                            busy = false
                        }
                    }
                ) { Text(if (isThai) "ปฏิเสธ" else "Reject") }
            }
        )
    }

    Scaffold(
        topBar = {
            LullabyTopBar(
                title = if (isThai) "เชื่อมต่อ LINE" else "Connect LINE",
                onBack = onBack,
                navController = navController
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when {
                loading -> CircularProgressIndicator(Modifier.padding(top = 48.dp))

                link != null -> {
                    val l = link!!
                    Icon(
                        Icons.Filled.CheckCircle, null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        if (isThai) "เชื่อมต่อแล้ว" else "Connected",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    l.displayName?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(24.dp))
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                        Row(
                            Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (isThai) "รับการแจ้งเตือนทาง LINE" else "Notifications via LINE",
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    if (isThai) "ปิดได้โดยไม่ต้องยกเลิกการเชื่อมต่อ" else "Turn off without unlinking",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = l.notifyEnabled,
                                enabled = !busy,
                                onCheckedChange = { on ->
                                    scope.launch {
                                        busy = true
                                        // Optimistic: the switch answers instantly, and a failed
                                        // write is corrected by the refresh below.
                                        link = l.copy(notifyEnabled = on)
                                        LineRepository.setNotifyEnabled(on)
                                        refresh()
                                        busy = false
                                    }
                                }
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    TextButton(
                        enabled = !busy,
                        onClick = {
                            scope.launch {
                                busy = true
                                LineRepository.unlink()
                                refresh()
                                busy = false
                            }
                        }
                    ) {
                        Text(
                            if (isThai) "ยกเลิกการเชื่อมต่อ" else "Unlink",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                code != null -> {
                    Text(
                        if (isThai) "ส่งรหัสนี้ให้น้องรักใน LINE" else "Send this code to NongRak on LINE",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(16.dp))
                    Card(shape = RoundedCornerShape(16.dp)) {
                        Text(
                            code!!,
                            modifier = Modifier.padding(horizontal = 28.dp, vertical = 18.dp),
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 6.sp
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (isThai) "รหัสหมดอายุใน 10 นาที" else "Code expires in 10 minutes",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = {
                            // Opens the OA chat with the code already typed — the parent only
                            // has to press send.
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(LineRepository.pairDeepLink(code!!)))
                                )
                            }.onFailure {
                                message = if (isThai)
                                    "เปิด LINE ไม่ได้ กรุณาพิมพ์รหัสเองในแชทน้องรัก"
                                else
                                    "Could not open LINE — send the code in the NongRak chat instead"
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Filled.Chat, null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (isThai) "เปิด LINE แล้วส่งรหัส" else "Open LINE and send")
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (isThai) "กำลังรอการเชื่อมต่อ..." else "Waiting for the link...",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                else -> {
                    Icon(
                        Icons.Filled.Chat, null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        if (isThai) "รับการแจ้งเตือนทาง LINE" else "Get notifications on LINE",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (isThai)
                            "เชื่อมบัญชีน้องรักกับ LINE แล้วรับแจ้งเตือนตารางกิจกรรม วัคซีน และเรื่องสำคัญของลูก แม้ไม่ได้เปิดแอป"
                        else
                            "Link your NongRak account to LINE and get schedule, vaccine and other important reminders even when the app is closed.",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(28.dp))
                    Button(
                        enabled = !busy,
                        onClick = {
                            scope.launch {
                                busy = true
                                LineRepository.issuePairCode()
                                    .onSuccess { code = it }
                                    .onFailure {
                                        message = if (isThai)
                                            "ขอรหัสไม่สำเร็จ กรุณาลองใหม่"
                                        else
                                            "Could not get a code — please try again"
                                    }
                                busy = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) { Text(if (isThai) "ขอรหัสเชื่อมต่อ" else "Get a linking code") }

                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        enabled = !busy,
                        onClick = {
                            scope.launch {
                                busy = true
                                LineRepository.issueLinkCode()
                                    .onSuccess { otp = it }
                                    .onFailure {
                                        message = if (isThai)
                                            "ขอรหัสไม่สำเร็จ กรุณาลองใหม่"
                                        else
                                            "Could not get a code — please try again"
                                    }
                                busy = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(if (isThai) "เชื่อมจากอุปกรณ์อื่น" else "Link from another device")
                    }

                    otp?.let { c ->
                        Spacer(Modifier.height(20.dp))
                        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                            Column(
                                Modifier.fillMaxWidth().padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // Grouped 4 + 4: eight unbroken digits is what people mistype.
                                Text(
                                    c.code.chunked(4).joinToString(" "),
                                    style = MaterialTheme.typography.headlineLarge,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 4.sp
                                )
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    if (isThai)
                                        "พิมพ์รหัสนี้ในแชท LINE น้องรักจากอุปกรณ์อีกเครื่อง แล้วกลับมากดอนุญาตที่นี่"
                                    else
                                        "Type this in the NongRak LINE chat on the other device, then come back here to approve.",
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    if (isThai) "หมดอายุใน 10 นาที" else "Expires in 10 minutes",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            message?.let {
                Spacer(Modifier.height(16.dp))
                Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            }
        }
    }
}
