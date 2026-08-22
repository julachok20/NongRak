package com.lullabyai.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lullabyai.app.data.repository.NotifCategory
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The in-app half of a notification.
 *
 * An Android system notification is easy to miss while the parent is already looking at the app —
 * on most launchers it lands as a silent heads-up that is gone before they glance up, and on some
 * OEM skins it is suppressed entirely for the foreground app. This is the copy they cannot miss.
 *
 * Process-wide state rather than a ViewModel because the callers are background workers
 * (VaccineReminderWorker, MedicineReminderWorker, DeviceSyncWorker) and NotificationRepository,
 * none of which hold a UI scope.
 */
object InAppAlerts {

    data class Alert(
        val category: String,
        val title: String,
        val body: String?,
        val at: Long = System.currentTimeMillis(),
    )

    private val _current = MutableStateFlow<Alert?>(null)
    val current: StateFlow<Alert?> = _current

    /** Latest wins: a queue would stack banners over each other and hide the newest one. */
    fun post(category: String, title: String, body: String?) {
        _current.value = Alert(category, title, body)
    }

    fun dismiss() {
        _current.value = null
    }
}

private const val VISIBLE_MS = 6_000L

/**
 * Drops an alert posted while the app was in the background.
 *
 * A worker firing at 03:00 sets the state whether or not anything is drawing. Without this the
 * parent opens the app at breakfast and gets a banner about something that already fired hours
 * ago as a system notification.
 */
private const val STALE_MS = 30_000L

/**
 * Slides the current alert in from the top. Overlay only — it draws above the nav host and takes
 * no layout space, so no screen has to make room for it.
 */
@Composable
fun InAppAlertHost(modifier: Modifier = Modifier, onOpen: () -> Unit = {}) {
    val alert by InAppAlerts.current.collectAsState()

    LaunchedEffect(alert) {
        val a = alert ?: return@LaunchedEffect
        val age = System.currentTimeMillis() - a.at
        if (age >= STALE_MS) {
            InAppAlerts.dismiss()
            return@LaunchedEffect
        }
        delay(VISIBLE_MS - age)
        InAppAlerts.dismiss()
    }

    // The exit animation still needs content to draw after the state has already gone null.
    var shown by remember { androidx.compose.runtime.mutableStateOf<InAppAlerts.Alert?>(null) }
    LaunchedEffect(alert) { alert?.let { shown = it } }

    Box(modifier = modifier.fillMaxWidth()) {
        AnimatedVisibility(
            visible = alert != null,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        ) {
            val current = shown ?: return@AnimatedVisibility
            val warning = current.category == NotifCategory.WARNING
            val container = if (warning) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.primaryContainer
            }
            val onContainer = if (warning) {
                MaterialTheme.colorScheme.onErrorContainer
            } else {
                MaterialTheme.colorScheme.onPrimaryContainer
            }

            Surface(
                shadowElevation = 8.dp,
                color = container,
                shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .statusBarsPadding()
                        .clickable {
                            InAppAlerts.dismiss()
                            onOpen()
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    Icon(
                        imageVector = if (warning) Icons.Default.Warning else Icons.Default.Notifications,
                        contentDescription = null,
                        tint = onContainer,
                        modifier = Modifier.size(22.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = current.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = onContainer,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (!current.body.isNullOrBlank()) {
                            Text(
                                text = current.body,
                                style = MaterialTheme.typography.bodySmall,
                                color = onContainer,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = onContainer,
                        modifier = Modifier
                            .size(20.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { InAppAlerts.dismiss() },
                    )
                }
            }
        }
    }
}
