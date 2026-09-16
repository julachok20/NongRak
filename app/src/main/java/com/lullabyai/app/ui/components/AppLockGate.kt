package com.lullabyai.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lullabyai.app.R
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.theme.Primary
import com.lullabyai.app.util.AppLock

/**
 * Covers the app until the phone's face / fingerprint / screen lock confirms it's the owner.
 *
 * Asks on a cold start and again after [AppLock.RELOCK_AFTER_MS] in the background. If the phone no
 * longer has a screen lock at all, the gate stands aside: there is nothing to check against, and a
 * lock that can never open would shut the parent out of their child's records.
 */
@Composable
fun AppLockGate(enabled: Boolean, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val isThai = LocalStrings.current.isThai

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> AppLock.backgroundedAt = System.currentTimeMillis()
                Lifecycle.Event.ON_START -> {
                    if (AppLock.shouldRelock(AppLock.backgroundedAt, System.currentTimeMillis())) AppLock.unlocked = false
                    AppLock.backgroundedAt = null
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    content()

    val mustLock = enabled && !AppLock.unlocked && AppLock.isSupported() && AppLock.isDeviceSecure(context)
    if (!mustLock) return

    var error by remember { mutableStateOf<String?>(null) }
    val title = if (isThai) "ปลดล็อก NongRak" else "Unlock NongRak"
    val subtitle = if (isThai) "ยืนยันด้วยใบหน้า ลายนิ้วมือ หรือรหัสล็อกเครื่อง" else "Use your face, fingerprint or screen lock"
    fun prompt() {
        error = null
        AppLock.authenticate(context, title, subtitle) { ok, message ->
            if (ok) AppLock.unlocked = true else error = message
        }
    }
    // Ask straight away when the lock appears; the button is there if they dismiss it.
    LaunchedEffect(Unit) { prompt() }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                Modifier.size(120.dp).clip(CircleShape).background(Primary.copy(alpha = 0.10f)).padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(painterResource(R.drawable.mascot_nongrak), null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            }
            Spacer(Modifier.height(24.dp))
            Text(
                if (isThai) "NongRak ล็อกอยู่" else "NongRak is locked",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (isThai) "ข้อมูลของลูกถูกปกป้องไว้ ยืนยันตัวตนเพื่อเปิดแอป" else "Your child's records are protected. Confirm it's you to open the app.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            error?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(28.dp))
            Button(onClick = { prompt() }, shape = RoundedCornerShape(100.dp), modifier = Modifier.height(52.dp)) {
                Icon(Icons.Filled.Fingerprint, null)
                Spacer(Modifier.width(8.dp))
                Text(if (isThai) "ปลดล็อก" else "Unlock", fontWeight = FontWeight.Bold)
            }
        }
    }
}
