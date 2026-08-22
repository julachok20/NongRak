package com.lullabyai.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.lullabyai.app.R
import com.lullabyai.app.data.repository.LineRepository
import com.lullabyai.app.data.settings.dataStore
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Shown once per install. Bumping the suffix re-announces after a redesign. */
private val SEEN_KEY = booleanPreferencesKey("line_oa_banner_seen_v1")

/**
 * One-time "NongRak is on LINE now" announcement.
 *
 * Self-contained: it reads its own dismissed flag and renders nothing when it has already been
 * seen, when no OA is configured, or while the flag is still loading. So the caller is a single
 * unconditional line at the top of the app rather than state threaded through a screen.
 *
 * Dismissal is written before the dialog closes, not after the user acts on it. A parent who
 * taps outside has still seen it, and re-announcing on every cold start is how a helpful
 * banner turns into the thing people learn to swipe away without reading.
 */
@Composable
fun LineOaBanner(onOpenLink: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val s = com.lullabyai.app.ui.localization.LocalStrings.current
    val isThai = s.isThai

    // null = not read yet. Distinct from false so the banner never flashes on before the stored
    // answer arrives.
    var show by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(Unit) {
        if (!LineRepository.isConfigured) { show = false; return@LaunchedEffect }
        // Signed-out users must never see this: status() would return null for them, which reads
        // as "not linked yet" and would pop the announcement over the login screen.
        val signedIn = runCatching {
            com.lullabyai.app.data.remote.supabase.auth.currentUserOrNull() != null
        }.getOrDefault(false)
        if (!signedIn) { show = false; return@LaunchedEffect }
        val seen = context.dataStore.data.first()[SEEN_KEY] ?: false
        // Already linked means the announcement is stale — they clearly know.
        val linked = LineRepository.status().getOrNull() != null
        show = !seen && !linked
    }

    if (show != true) return

    fun dismiss(then: () -> Unit = {}) {
        show = false
        scope.launch { context.dataStore.edit { it[SEEN_KEY] = true } }
        then()
    }

    Dialog(onDismissRequest = { dismiss() }) {
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    painter = painterResource(R.drawable.line_oa_banner),
                    contentDescription = if (isThai)
                        "น้องรัก AI เปิดให้บริการทาง LINE แล้ว"
                    else
                        "NongRak AI is now available on LINE",
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                        .clickable { dismiss(onOpenLink) },
                    contentScale = ContentScale.FillWidth
                )
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Button(
                        onClick = { dismiss(onOpenLink) },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) { Text(if (isThai) "เชื่อมต่อ LINE เลย" else "Connect LINE now") }
                    TextButton(onClick = { dismiss() }) {
                        Text(
                            if (isThai) "ไว้ทีหลัง" else "Later",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
