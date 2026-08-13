@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.access

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.lullabyai.app.data.model.ChildActivityEntry
import com.lullabyai.app.ui.components.LullabyTopBar
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.util.TimeUtils
import com.lullabyai.app.viewmodel.ActivityHistoryUiState
import com.lullabyai.app.viewmodel.ActivityHistoryViewModel
import com.lullabyai.app.viewmodel.ActivityKindLabels
import com.lullabyai.app.viewmodel.ActivitySort
import java.time.LocalDate

/**
 * Stitch: "Activity History & Caregiver Chat (ประวัติกิจกรรมและแชท)".
 *
 * Every record written for this child, newest first, each showing who wrote it. The caregiver
 * chat row and the per-entry "ตอบกลับ" only appear when [onChat] is supplied AND the viewer is an
 * owner/co-owner — a nanny sees the same history with no way to open a conversation from it.
 *
 * ponytail: [onChat] stays null until the caregiver chat ships; passing a real lambda is the only
 * change needed to light both entry points up.
 */
@Composable
fun ActivityHistoryScreen(
    viewModel: ActivityHistoryViewModel,
    navController: NavController,
    onBack: () -> Unit,
    onChat: ((String) -> Unit)? = null
) {
    val s = LocalStrings.current
    val isThai = s.isThai
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) { viewModel.load() }

    val showChat = onChat != null && state.canChat && state.caregivers.isNotEmpty()

    Scaffold(
        topBar = {
            LullabyTopBar(
                title = if (isThai) "ประวัติกิจกรรม" else "Activity history",
                onBack = onBack,
                navController = navController
            )
        },
        bottomBar = { AccessPermissionBottomNav(navController) }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
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
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Search, null, tint = Outline) },
                    placeholder = { Text(if (isThai) "ค้นหากิจกรรม…" else "Search activity…") }
                )
            }

            item {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ActivityKindLabels.CATEGORIES.forEach { (key, _) ->
                            FilterChip(
                                selected = state.category == key,
                                onClick = { viewModel.setCategory(key) },
                                label = { Text(ActivityKindLabels.categoryLabel(key, isThai)) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Primary,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                    SortMenu(
                        current = state.sort,
                        isThai = isThai,
                        onPick = viewModel::setSort
                    )
                }
            }

            if (showChat) {
                item {
                    CaregiverChatRow(state, isThai) { onChat?.invoke(it) }
                }
            }

            if (state.loading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Primary)
                    }
                }
            }

            state.error?.let { msg ->
                item { Text(msg, color = Error, style = MaterialTheme.typography.bodySmall) }
            }

            // groupBy preserves encounter order, so the headings follow whatever the sort chose.
            val grouped = state.visible.groupBy { state.groupKeyOf(it) }
            grouped.forEach { (key, groupEntries) ->
                item(key = "group_$key") {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            groupHeader(key, state.sort, isThai),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "${groupEntries.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Outline
                        )
                    }
                }
                items(groupEntries, key = { it.kind + it.sourceId }) { e ->
                    ActivityEntryCard(
                        entry = e,
                        isThai = isThai,
                        // Reply only makes sense against someone reachable: a caregiver, not you.
                        onReply = if (showChat && state.caregivers.any { it.userId == e.authorId })
                            ({ onChat?.invoke(e.authorId.orEmpty()) }) else null
                    )
                }
            }

            if (!state.loading && state.visible.isEmpty() && state.error == null) {
                item {
                    Text(
                        if (isThai) "ยังไม่มีบันทึกกิจกรรม" else "No activity recorded yet",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(32.dp)
                    )
                }
            }

            item { Spacer(Modifier.height(72.dp)) }
        }
    }
}

/** Sort picker. A menu rather than more chips — the chip row is already the busiest thing here. */
@Composable
private fun SortMenu(
    current: ActivitySort,
    isThai: Boolean,
    onPick: (ActivitySort) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }, contentPadding = PaddingValues(horizontal = 8.dp)) {
            Icon(Icons.Filled.SwapVert, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(2.dp))
            Text(
                ActivityKindLabels.sortLabel(current, isThai),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            ActivitySort.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(ActivityKindLabels.sortLabel(option, isThai)) },
                    leadingIcon = {
                        if (option == current) Icon(Icons.Filled.Check, null, tint = Primary)
                    },
                    onClick = { onPick(option); open = false }
                )
            }
        }
    }
}

@Composable
private fun ChildHeaderCard(childName: String, latestIso: String?, isThai: Boolean) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            AccessAvatar(null, childName)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(childName, fontWeight = FontWeight.Bold)
                Text(
                    latestIso?.let {
                        val t = TimeUtils.localTime(it) ?: TimeUtils.localDate(it) ?: ""
                        if (isThai) "อัปเดตล่าสุด: $t" else "Last update: $t"
                    } ?: if (isThai) "ยังไม่มีการอัปเดต" else "No updates yet",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(
                Modifier
                    .background(HealthGood.copy(0.14f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Sync, null, tint = HealthGood, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    if (isThai) "ซิงค์แล้ว" else "Synced",
                    style = MaterialTheme.typography.labelSmall,
                    color = HealthGood
                )
            }
        }
    }
}

@Composable
private fun CaregiverChatRow(
    state: ActivityHistoryUiState,
    isThai: Boolean,
    onChat: (String) -> Unit
) {
    Column {
        Text(
            if (isThai) "แชทกับผู้ดูแล" else "Chat with caregivers",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            state.caregivers.forEach { m ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(64.dp)
                ) {
                    Box(
                        Modifier
                            .clip(CircleShape)
                            .background(Primary.copy(0.10f))
                            .clickable { onChat(m.userId) }
                    ) { AccessAvatar(m.avatarUrl, m.displayName) }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        m.displayName,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun ActivityEntryCard(
    entry: ChildActivityEntry,
    isThai: Boolean,
    onReply: (() -> Unit)?
) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(kindIcon(entry.kind), null, tint = Primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    ActivityKindLabels.title(entry.kind, isThai),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    TimeUtils.localTime(entry.occurredAt) ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            entry.detail?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.titleSmall, color = Primary)
            }
            entry.note?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider(color = Outline.copy(0.25f))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    (if (isThai) "บันทึกโดย " else "Recorded by ") + entry.authorName.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                if (onReply != null) {
                    TextButton(onClick = onReply, contentPadding = PaddingValues(horizontal = 8.dp)) {
                        Icon(Icons.Filled.ChatBubbleOutline, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (isThai) "ตอบกลับ" else "Reply", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private fun kindIcon(kind: String) = when {
    // Caregiver quick-records all share one mark; their kind is already in the title.
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
 * The heading text for a group. Time sorts name the day — "วันนี้"/"เมื่อวานนี้" for the two that
 * matter, the plain date beyond. The other sorts already carry their own meaning in the key.
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
