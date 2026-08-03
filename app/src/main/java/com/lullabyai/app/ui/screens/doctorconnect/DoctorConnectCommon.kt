package com.lullabyai.app.ui.screens.doctorconnect

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.navigation.NavController
import com.lullabyai.app.data.model.Child
import com.lullabyai.app.data.model.DoctorProfile
import com.lullabyai.app.data.model.Profile
import com.lullabyai.app.data.model.ClinicBookingGate
import com.lullabyai.app.data.repository.PediaConnectLinkRepository
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.Outline
import com.lullabyai.app.ui.theme.Primary

/** Small helpers + composables shared by the parent-side doctor-connect screens
 *  (search / hub / reservation / registration) — kept in one file since each is
 *  only a few lines and used across 2-3 screens, not worth a new abstraction each. */

fun doctorDisplayName(profile: Profile, isThai: Boolean): String =
    profile.fullName?.takeIf { it.isNotBlank() } ?: (if (isThai) "ไม่ระบุชื่อแพทย์" else "Unnamed doctor")

/** Parent clinic flow: child must be on Pedia Connect; each booking is pending until doctor approves. */
suspend fun resolveClinicBookingGate(doctorId: String, childId: String?): ClinicBookingGate =
    PediaConnectLinkRepository().bookingGate(doctorId, childId)

fun NavController.navigateForClinicGate(doctorId: String, @Suppress("UNUSED_PARAMETER") gate: ClinicBookingGate) {
    // Always open reservation — registration prompts live on that screen when Pedia intake is missing.
    navigate(Screen.DoctorReservation.route(doctorId))
}

fun clinicGateSnackbarMessage(gate: ClinicBookingGate, isThai: Boolean): String? = null

fun clinicGateMissingChildMessage(isThai: Boolean): String =
    if (isThai) "กรุณาเลือกเด็กในแอปก่อนจองคิวหรือลงทะเบียน"
    else "Select a child in the app before booking or registering."

private val THAI_MONTHS_SHORT = listOf(
    "ม.ค.", "ก.พ.", "มี.ค.", "เม.ย.", "พ.ค.", "มิ.ย.",
    "ก.ค.", "ส.ค.", "ก.ย.", "ต.ค.", "พ.ย.", "ธ.ค."
)

/** Parses the two date shapes the schema uses — an ISO-8601 instant (`appointments.scheduled_at`,
 *  `doctor_visits.visit_datetime`) or a bare `yyyy-MM-dd` (`doctor_visits.next_appointment_date`). */
fun parseScheduleInstant(raw: String?): java.time.Instant? {
    val v = raw?.takeIf { it.isNotBlank() } ?: return null
    runCatching { return java.time.Instant.parse(v) }
    runCatching { return java.time.LocalDate.parse(v).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant() }
    runCatching { return java.time.LocalDateTime.parse(v).atZone(java.time.ZoneId.systemDefault()).toInstant() }
    return null
}

/** Stitch renders appointment stamps as "25 ส.ค. 2569 | 15:00 น." (Buddhist era). English falls
 *  back to the ISO-ish "25 Aug 2026 | 15:00". [withTime] is off for date-only values. */
fun thaiDateTimeLabel(raw: String?, isThai: Boolean, withTime: Boolean = true): String? {
    val instant = parseScheduleInstant(raw) ?: return null
    val t = instant.atZone(java.time.ZoneId.systemDefault())
    val time = if (withTime) String.format(java.util.Locale.US, "%02d:%02d", t.hour, t.minute) else null
    return if (isThai) {
        val d = "${t.dayOfMonth} ${THAI_MONTHS_SHORT[t.monthValue - 1]} ${t.year + 543}"
        if (time != null) "$d | $time น." else d
    } else {
        val d = "${t.dayOfMonth} ${t.month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.US)} ${t.year}"
        if (time != null) "$d | $time" else d
    }
}

/** Every Connect-tab screen shares the same footer; the bar drives its own tab switching off
 *  LocalNavController, so the lambdas below are only the no-NavController preview fallback. */
@Composable
fun ConnectBottomNav() {
    com.lullabyai.app.ui.components.AppBottomNav(
        selected = com.lullabyai.app.ui.components.BottomTab.CONNECT,
        onHome = {}, onChatbot = {}, onChild = {}, onStats = {}, onSettings = {}
    )
}

/** Stitch section heading: bold title, optional leading icon, optional trailing count/action. */
@Composable
fun ConnectSectionHeader(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        icon?.let { Icon(it, null, tint = Primary, modifier = Modifier.size(18.dp)) }
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

/** Stitch status pill — "ปกติ" / "ควรเฝ้าระวัง" / "รอการยืนยัน" etc. */
@Composable
fun StatusPill(text: String, color: Color, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, filled: Boolean = false) {
    Surface(shape = RoundedCornerShape(100.dp), color = if (filled) color else color.copy(alpha = 0.12f)) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            icon?.let { Icon(it, null, tint = if (filled) Color.White else color, modifier = Modifier.size(14.dp)) }
            Text(
                text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                color = if (filled) Color.White else color
            )
        }
    }
}

/** Same age-string convention as ChildViewModel.calculateAge, but works on any [Child] rather than
 *  only the currently-selected one (needed for lists of children, e.g. My Doctors Hub / Pedia Connect). */
fun childAgeLabel(child: Child, isThai: Boolean): String {
    val birth = runCatching { java.time.LocalDate.parse(child.birthdate) }.getOrNull()
        ?: return if (isThai) "ไม่ระบุอายุ" else "Age unknown"
    val p = java.time.Period.between(birth, java.time.LocalDate.now())
    return if (isThai) "${p.years} ปี ${p.months} เดือน" else "${p.years}y ${p.months}m"
}

@Composable
fun DoctorAvatar(photoUrl: String?, size: androidx.compose.ui.unit.Dp = 56.dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(Primary.copy(0.1f)),
        contentAlignment = Alignment.Center
    ) {
        if (photoUrl != null) {
            AsyncImage(model = photoUrl, contentDescription = null, modifier = Modifier.size(size).clip(CircleShape))
        } else {
            Icon(Icons.Filled.Person, null, tint = Primary, modifier = Modifier.size(size * 0.5f))
        }
    }
}

/** A doctor row card — used by SearchDoctorScreen (browse results) and MyDoctorsHubScreen (past doctors). */
@Composable
fun DoctorCard(
    profile: Profile,
    doctorProfile: DoctorProfile,
    isThai: Boolean,
    photoUrl: String? = null,
    actionLabel: String,
    onAction: () -> Unit,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick, shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(Modifier.padding(14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DoctorAvatar(photoUrl ?: doctorProfile.clinicPhotoUrl)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(doctorDisplayName(profile, isThai), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1)
                doctorProfile.specialty?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = Primary)
                }
                doctorProfile.clinicName?.takeIf { it.isNotBlank() }?.let {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Filled.LocalHospital, null, tint = Outline, modifier = Modifier.size(13.dp))
                        Text(it, style = MaterialTheme.typography.labelSmall, color = Outline, maxLines = 1)
                    }
                }
            }
            Button(onClick = onAction, shape = RoundedCornerShape(100.dp), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                Text(actionLabel, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/** Horizontal picker for "which child is this appointment for" — reuses the existing
 *  multi-child list from ChildViewModel.uiState.children (same source SiblingCompareScreen
 *  reads) instead of building a new child-selection mechanism. */
@Composable
fun ChildPickerRow(children: List<Child>, selectedId: String?, isThai: Boolean, onSelect: (String) -> Unit) {
    if (children.isEmpty()) return
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(children, key = { it.id }) { child ->
            val selected = child.id == selectedId
            Surface(
                onClick = { onSelect(child.id) },
                shape = RoundedCornerShape(100.dp),
                color = if (selected) Primary else MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        child.name.ifBlank { if (isThai) "ไม่ระบุชื่อ" else "Unnamed" },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
