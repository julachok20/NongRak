package com.lullabyai.app.ui.components

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhonelinkLock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lullabyai.app.data.remote.supabase
import com.lullabyai.app.data.repository.AuthRepository
import com.lullabyai.app.data.repository.DeviceRepository
import com.lullabyai.app.data.settings.SettingsManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private sealed interface GateState {
    data object Clear : GateState
    data object Pending : GateState
}

/**
 * Blocks the app behind one check: is THIS device trusted for the CURRENTLY signed-in user?
 *
 * Keyed on `sessionStatus`, not on a one-shot LaunchedEffect(Unit). A single check-at-mount would
 * miss every sign-in that happens after the first: sign out of the pending screen back to Login,
 * then sign into a different account on the same still-untrusted device, and a one-shot check
 * would never run again. Reacting to the SDK's own auth state instead means every sign-in — email,
 * Google, LINE, a fresh sign-up, or switching accounts — re-checks automatically, through the one
 * choke point MainActivity already wraps around AppNavigation. No sign-out state is possible while
 * this is active: an absent user id resolves straight to Clear, which is what shows Welcome/Login.
 *
 * A brand new account's first device is auto-approved server-side, so the common case (fresh
 * sign-up, or the parent's own phone they already use) never sees this screen at all. It only
 * appears the moment a SECOND device signs into an existing account.
 */
@Composable
fun DeviceTrustGate(settingsManager: SettingsManager, content: @Composable () -> Unit) {
    val sessionStatus by supabase.auth.sessionStatus.collectAsState()
    val userId = (sessionStatus as? SessionStatus.Authenticated)?.session?.user?.id

    var state by remember { mutableStateOf<GateState>(GateState.Clear) }

    LaunchedEffect(userId) {
        if (userId == null) {
            // No session: nothing to gate. Welcome/Login must render, not this screen.
            state = GateState.Clear
            return@LaunchedEffect
        }
        val id = settingsManager.getOrCreateDeviceId()
        val name = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
        val status = DeviceRepository.register(id, name).getOrNull()
        // A registration failure (offline, server hiccup) fails OPEN, not closed: a parent who
        // cannot reach Supabase should see their own cached data, not be locked out of an app
        // that already trusted this exact device a minute ago.
        state = if (status == "pending") GateState.Pending else GateState.Clear

        while (state is GateState.Pending) {
            delay(4_000)
            if (DeviceRepository.myStatus(id).getOrNull() == "approved") {
                state = GateState.Clear
            }
        }
    }

    when {
        state is GateState.Pending -> PendingApprovalScreen()
        else -> content()
    }
}

@Composable
private fun PendingApprovalScreen() {
    val s = com.lullabyai.app.ui.localization.LocalStrings.current
    val isThai = s.isThai
    val scope = rememberCoroutineScope()

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Filled.PhonelinkLock, null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Text(
                if (isThai) "อุปกรณ์นี้รอการอนุญาต" else "This device needs approval",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                if (isThai)
                    "เพื่อความปลอดภัยของข้อมูลลูก บัญชีนี้ต้องได้รับอนุญาตจากอุปกรณ์ที่เชื่อมต่ออยู่แล้วก่อน ไปที่ ตั้งค่า > อุปกรณ์ที่เชื่อมต่อ ในเครื่องเดิม แล้วกดอนุญาต"
                else
                    "For your child's data security, this account needs approval from a device you already use. On that device, go to Settings > Trusted Devices and approve this one.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp, bottom = 28.dp)
            )
            CircularProgressIndicator(modifier = Modifier.padding(bottom = 24.dp))
            // Sign-out clears the session; sessionStatus emits NotAuthenticated, userId becomes
            // null, and DeviceTrustGate's own LaunchedEffect resolves that straight to Clear —
            // no separate callback needed to escape this screen.
            OutlinedButton(onClick = { scope.launch { AuthRepository().signOut() } }) {
                Text(if (isThai) "ออกจากระบบ" else "Sign out")
            }
        }
    }
}
