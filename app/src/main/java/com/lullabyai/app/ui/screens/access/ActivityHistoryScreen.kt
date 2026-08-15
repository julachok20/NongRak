@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.access

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.lullabyai.app.data.model.CareConversation
import com.lullabyai.app.data.model.ChildActivityEntry
import com.lullabyai.app.ui.components.LullabyTopBar
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.util.TimeUtils
import com.lullabyai.app.viewmodel.ActivityHistoryViewModel
import com.lullabyai.app.viewmodel.ActivityKindLabels
import com.lullabyai.app.viewmodel.ActivitySort
import java.time.LocalDate

/*
 * "Serene Clinical" palette, taken from the design's own tailwind config rather than the app
 * theme, so this screen matches the supplied mock exactly. Move to shared theme tokens once the
 * rest of the app adopts the same scale.
 */
private val ScBackground = Color(0xFFF8F9FF)
private val ScSurfaceLowest = Color(0xFFFFFFFF)
private val ScSurfaceContainer = Color(0xFFE5EEFF)
private val ScSurfaceContainerHigh = Color(0xFFDCE9FF)
private val ScSurfaceContainerHighest = Color(0xFFD3E4FE)
private val ScOnSurface = Color(0xFF0B1C30)
private val ScOnSurfaceVariant = Color(0xFF444654)
private val ScOutline = Color(0xFF747685)
private val ScOutlineVariant = Color(0xFFC4C5D6)
private val ScPrimary = Color(0xFF173DB8)
private val ScError = Color(0xFFBA1A1A)
private val ScTertiary = Color(0xFF00554C)
private val ScTertiaryContainer = Color(0xFF007064)
private val ScTertiaryFixed = Color(0xFF62FAE3)
private val ScSecondary = Color(0xFF3A59B0)

/**
 * Design: "Activity-History page (For Owner Co-owner)".
 *
 * Everything recorded for this child, hung off a timeline rail with a coloured node per entry,
 * and a chat block that leads to the Care Circle rather than listing faces.
 */
@Composable
fun ActivityHistoryScreen(
    viewModel: ActivityHistoryViewModel,
    navController: NavController,
    onBack: () -> Unit,
    onChat: ((String) -> Unit)? = null,
    /** peerId + what the reply is about, so the carer sees which update is being questioned. */
    onReplyToEntry: ((String, String) -> Unit)? = null,
    onOpenHub: (() -> Unit)? = null
) {
    val s = LocalStrings.current
    val isThai = s.isThai
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) { viewModel.load() }

    val showChat = onChat != null && state.canChat

    Scaffold(
        containerColor = ScBackground,
        topBar = {
            LullabyTopBar(
                title = if (isThai) "ประวัติกิจกรรม" else "Activity history",
                onBack = onBack,
                navController = navController,
                centerTitle = true,
                actions = {
                    SortMenu(current = state.sort, isThai = isThai, onPick = viewModel::setSort)
                }
            )
        },
        bottomBar = { AccessPermissionBottomNav(navController) }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                ChildHeaderCard(
                    childName = state.childName.ifBlank { if (isThai) "ลูก" else "Child" },
                    latestIso = state.entries.firstOrNull()?.occurredAt,
                    isThai = isThai
                )
            }

            item {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::setQuery,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Search, null, tint = ScOutline) },
                    placeholder = {
                        Text(
                            if (isThai) "ค้นหากิจกรรม..." else "Search activity...",
                            color = ScOutline.copy(0.7f)
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = ScSurfaceLowest,
                        unfocusedContainerColor = ScSurfaceLowest,
                        focusedBorderColor = ScPrimary,
                        unfocusedBorderColor = ScOutlineVariant
                    )
                )
            }

            item {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ActivityKindLabels.CATEGORIES.forEach { (key, _) ->
                        CategoryChip(
                            label = ActivityKindLabels.categoryLabel(key, isThai),
                            icon = categoryIcon(key),
                            selected = state.category == key,
                            onClick = { viewModel.setCategory(key) }
                        )
                    }
                }
            }

            if (showChat) {
                item {
                    Text(
                        if (isThai) "แชทกับผู้ดูแล" else "Chat with caregivers",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = ScOnSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                item {
                    // Full-width primary action: the hub is the destination, not a per-person
                    // shortcut strip. Unread counts and previews live there.
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(ScPrimary)
                            .clickable(enabled = onOpenHub != null) { onOpenHub?.invoke() }
                            .padding(vertical = 14.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.ChatBubbleOutline, null,
                            tint = Color.White, modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (isThai) "ไปที่ศูนย์แชท" else "Go to chat hub",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                state.topConversation?.let { convo ->
                    item {
                        ConversationPreview(
                            convo = convo,
                            isThai = isThai,
                            onOpen = { onChat?.invoke(convo.peerId) }
                        )
                    }
                }
            }

            if (state.loading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = ScPrimary)
                    }
                }
            }

            state.error?.let { msg ->
                item { Text(msg, color = ScError, style = MaterialTheme.typography.bodySmall) }
            }

            val grouped = state.visible.groupBy { state.groupKeyOf(it) }
            grouped.forEach { (key, groupEntries) ->
                item(key = "group_$key") {
                    Text(
                        groupHeader(key, state.sort, isThai),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = ScOnSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp, top = 8.dp)
                    )
                }
                items(groupEntries, key = { it.kind + it.sourceId }) { e ->
                    TimelineEntry(
                        entry = e,
                        isThai = isThai,
                        // Reply only against someone reachable: a caregiver, not yourself. The
                        // subject line names the entry, so "?" arrives with its context attached.
                        onReply = if (showChat && state.caregivers.any { it.userId == e.authorId })
                            ({
                                val subject = listOfNotNull(
                                    ActivityKindLabels.title(e.kind, isThai),
                                    e.detail?.takeIf { d -> d.isNotBlank() }
                                ).joinToString(" · ")
                                if (onReplyToEntry != null) {
                                    onReplyToEntry(e.authorId.orEmpty(), subject)
                                } else {
                                    onChat?.invoke(e.authorId.orEmpty())
                                }
                            }) else null
                    )
                }
            }

            if (!state.loading && state.visible.isEmpty() && state.error == null) {
                item {
                    Text(
                        if (isThai) "ยังไม่มีบันทึกกิจกรรม" else "No activity recorded yet",
                        color = ScOnSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(32.dp)
                    )
                }
            }

            item { Spacer(Modifier.height(72.dp)) }
        }
    }
}

@Composable
private fun ChildHeaderCard(childName: String, latestIso: String?, isThai: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ScSurfaceLowest)
            .border(1.dp, ScOutlineVariant.copy(0.3f), RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AccessAvatar(null, childName)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                childName,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = ScOnSurface
            )
            Text(
                latestIso?.let {
                    val t = TimeUtils.localTime(it) ?: TimeUtils.localDate(it) ?: ""
                    if (isThai) "อัปเดตล่าสุด: $t" else "Last update: $t"
                } ?: if (isThai) "ยังไม่มีการอัปเดต" else "No updates yet",
                style = MaterialTheme.typography.labelSmall,
                color = ScOnSurfaceVariant
            )
        }
        Row(
            Modifier
                .clip(CircleShape)
                .background(ScPrimary.copy(0.10f))
                .padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Sync, null, tint = ScPrimary, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(
                if (isThai) "ซิงค์แล้ว" else "Synced",
                style = MaterialTheme.typography.labelSmall,
                color = ScPrimary
            )
        }
    }
}

/** Preview of the conversation that wants attention, with unread badge and a "แชทเลย" pill. */
@Composable
private fun ConversationPreview(convo: CareConversation, isThai: Boolean, onOpen: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ScSurfaceLowest)
            .border(1.dp, ScOutlineVariant.copy(0.3f), RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            AccessAvatar(convo.peerAvatar, convo.peerName)
            if (convo.unread > 0) {
                Box(
                    Modifier
                        .size(18.dp)
                        .background(ScSurfaceLowest, CircleShape)
                        .padding(2.dp)
                        .background(ScError, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "${convo.unread}",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                convo.peerName,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = ScOnSurface,
                maxLines = 1
            )
            Text(
                if (convo.lastHasImage && convo.lastBody.isNullOrBlank()) {
                    if (isThai) "ส่งรูปภาพ" else "Sent a photo"
                } else convo.lastBody.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = ScOnSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .clip(CircleShape)
                .background(ScPrimary)
                .clickable(onClick = onOpen)
                .padding(horizontal = 16.dp, vertical = 7.dp)
        ) {
            Text(
                if (isThai) "แชทเลย" else "Open",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }
    }
}

/**
 * One entry hung off the timeline: a 2px rail behind a node circle, then the card. The rail is
 * drawn per row rather than once behind the group, so a group of any length lines up without
 * having to measure it first.
 */
@Composable
private fun TimelineEntry(
    entry: ChildActivityEntry,
    isThai: Boolean,
    onReply: (() -> Unit)?
) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(Modifier.width(48.dp).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
            // Runs the full height so consecutive entries read as one thread.
            Box(Modifier.width(2.dp).fillMaxHeight().background(ScSurfaceContainerHigh))
            Box(
                Modifier
                    .padding(top = 4.dp)
                    .size(44.dp)
                    .background(ScSurfaceLowest, CircleShape)
                    .border(2.dp, ScBackground, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    kindIcon(entry.kind), null,
                    tint = kindTint(entry.category),
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(ScSurfaceLowest)
                .border(1.dp, ScOutlineVariant.copy(0.3f), RoundedCornerShape(12.dp))
                .padding(16.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Text(
                    ActivityKindLabels.title(entry.kind, isThai),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = ScOnSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    TimeUtils.localTime(entry.occurredAt).orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = ScOnSurfaceVariant
                )
            }

            entry.detail?.takeIf { it.isNotBlank() }?.let { detail ->
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(ScSurfaceContainer)
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            detail,
                            fontWeight = FontWeight.Bold,
                            color = ScPrimary,
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                    // Growth entries carry the design's teal "normal" chip.
                    if (entry.category == "growth") {
                        Spacer(Modifier.width(10.dp))
                        Row(
                            Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(ScTertiaryFixed.copy(0.20f))
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.TrendingUp, null,
                                tint = ScTertiary, modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                if (isThai) "การเติบโตปกติ" else "Normal growth",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = ScTertiary
                            )
                        }
                    }
                }
            }

            entry.note?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = ScOnSurfaceVariant)
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = ScSurfaceContainerHighest)
            Spacer(Modifier.height(10.dp))

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(24.dp).background(ScSurfaceContainerHighest, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        entry.authorName?.take(1).orEmpty(),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = ScPrimary
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    if (isThai) "บันทึกโดย " else "Recorded by ",
                    style = MaterialTheme.typography.labelSmall,
                    color = ScOutline
                )
                Text(
                    entry.authorName.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = ScOnSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (onReply != null) {
                    Row(
                        Modifier
                            .clip(CircleShape)
                            .clickable(onClick = onReply)
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.ChatBubbleOutline, null,
                            tint = ScPrimary, modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            if (isThai) "ตอบกลับ" else "Reply",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = ScPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryChip(
    label: String,
    icon: ImageVector?,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .clip(CircleShape)
            .background(if (selected) ScPrimary else ScSurfaceLowest)
            .then(if (selected) Modifier else Modifier.border(1.dp, ScOutlineVariant, CircleShape))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                icon, null,
                tint = if (selected) Color.White else ScOnSurfaceVariant,
                modifier = Modifier.size(15.dp)
            )
            Spacer(Modifier.width(5.dp))
        }
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) Color.White else ScOnSurfaceVariant
        )
    }
}

/** Sort picker, behind the design's filter icon — the chip row is already the busiest thing here. */
@Composable
private fun SortMenu(current: ActivitySort, isThai: Boolean, onPick: (ActivitySort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(
                Icons.Filled.FilterList,
                contentDescription = if (isThai) "เรียงลำดับ" else "Sort",
                tint = ScOnSurfaceVariant
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            ActivitySort.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(ActivityKindLabels.sortLabel(option, isThai)) },
                    leadingIcon = {
                        if (option == current) Icon(Icons.Filled.Check, null, tint = ScPrimary)
                    },
                    onClick = { onPick(option); open = false }
                )
            }
        }
    }
}

private fun categoryIcon(key: String): ImageVector? = when (key) {
    "growth" -> Icons.Filled.MonitorWeight
    "medical" -> Icons.Filled.Vaccines
    "nutrition" -> Icons.Filled.Restaurant
    "daily" -> Icons.Filled.Bedtime
    "care" -> Icons.Filled.ChildCare
    else -> null
}

/** Node colour by category, mirroring the design's primary / tertiary / secondary nodes. */
private fun kindTint(category: String): Color = when (category) {
    "medical" -> ScTertiaryContainer
    "nutrition", "daily" -> ScSecondary
    "care" -> ScTertiary
    else -> ScPrimary
}

private fun kindIcon(kind: String) = when {
    kind.startsWith("care_") -> Icons.Filled.ChildCare
    else -> recordIcon(kind)
}

private fun recordIcon(kind: String) = when (kind) {
    "growth" -> Icons.Filled.MonitorWeight
    "vaccine" -> Icons.Filled.Vaccines
    "milk" -> Icons.Filled.WaterDrop
    "food" -> Icons.Filled.Restaurant
    "waste" -> Icons.Filled.BabyChangingStation
    "sleep" -> Icons.Filled.Bedtime
    "symptom" -> Icons.Filled.Sick
    "medicine" -> Icons.Filled.Medication
    else -> Icons.Filled.History
}

/**
 * Heading text for a group. Time sorts name the day — "วันนี้"/"เมื่อวานนี้" for the two that
 * matter, the plain date beyond. The other sorts already carry their meaning in the key.
 */
private fun groupHeader(key: String, sort: ActivitySort, isThai: Boolean): String = when (sort) {
    ActivitySort.KIND -> ActivityKindLabels.title(key, isThai)
    ActivitySort.AUTHOR -> key.ifBlank { if (isThai) "ไม่ทราบผู้บันทึก" else "Unknown" }
    ActivitySort.NEWEST, ActivitySort.OLDEST -> {
        val today = LocalDate.now().toString()
        val yesterday = LocalDate.now().minusDays(1).toString()
        when (key) {
            today -> if (isThai) "วันนี้" else "Today"
            yesterday -> if (isThai) "เมื่อวานนี้" else "Yesterday"
            else -> key
        }
    }
}
