package com.lullabyai.app.ui.screens.access

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.lullabyai.app.ui.screens.child.ChildBottomNav
import com.lullabyai.app.ui.theme.Primary
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun AccessAvatar(url: String?, name: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(48.dp).clip(CircleShape).background(Primary.copy(0.12f)),
        contentAlignment = Alignment.Center
    ) {
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = null,
                modifier = Modifier.size(48.dp).clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            val initial = name.trim().firstOrNull()?.uppercaseChar()?.toString()
            if (initial != null) {
                Text(initial, color = Primary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            } else {
                Icon(Icons.Filled.Person, null, tint = Primary)
            }
        }
    }
}

internal fun formatAccessTime(iso: String?, isThai: Boolean): String {
    if (iso.isNullOrBlank()) return ""
    return try {
        val instant = Instant.parse(iso)
        val zoned = instant.atZone(ZoneId.systemDefault())
        val fmt = if (isThai) DateTimeFormatter.ofPattern("d MMM HH:mm")
        else DateTimeFormatter.ofPattern("MMM d, HH:mm")
        zoned.format(fmt)
    } catch (_: Exception) {
        iso.take(16).replace('T', ' ')
    }
}

internal fun remainingSeconds(expiresAtIso: String?): Long {
    if (expiresAtIso.isNullOrBlank()) return 0L
    return try {
        val end = Instant.parse(expiresAtIso).epochSecond
        (end - Instant.now().epochSecond).coerceAtLeast(0L)
    } catch (_: Exception) {
        0L
    }
}

internal fun formatCountdown(totalSec: Long): String {
    val m = totalSec / 60
    val s = totalSec % 60
    return "%d:%02d".format(m, s)
}

/** Shared footer for permission / access screens (reached from the child tab). Uses [LocalNavController] for re-tap → tab root. */
@Composable
internal fun AccessPermissionBottomNav(navController: NavController) {
    ChildBottomNav(navController, currentTab = "")
}

internal fun permissionSectionIcon(id: String): androidx.compose.ui.graphics.vector.ImageVector =
    when (id) {
        "health" -> Icons.Filled.HealthAndSafety
        "visits" -> Icons.Filled.CalendarMonth
        "meds" -> Icons.Filled.LocalHospital
        else -> Icons.Filled.Description
    }
