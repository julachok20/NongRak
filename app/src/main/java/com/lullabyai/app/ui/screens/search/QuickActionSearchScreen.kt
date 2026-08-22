@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.lullabyai.app.data.QuickAction
import com.lullabyai.app.data.QuickActionCatalog
import com.lullabyai.app.data.QuickActionCategory
import com.lullabyai.app.ui.theme.*

/**
 * Modern Universal Search over all reachable in-app destinations (QuickActionCatalog).
 * Filterable by category and keyword.
 */
@Composable
fun QuickActionSearchScreen(navController: NavController, isThai: Boolean, onBack: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(QuickActionCategory.ALL) }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    val results by remember(query, selectedCategory) {
        derivedStateOf { QuickActionCatalog.search(query, selectedCategory) }
    }

    Scaffold(
        topBar = {
            com.lullabyai.app.ui.components.LullabyTopBar(
                title = if (isThai) "ค้นหาการใช้งานทั้งหมด" else "Search All Features",
                onBack = onBack,
                navController = navController
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .padding(top = 8.dp),
                placeholder = {
                    Text(
                        if (isThai) "ค้นหาการใช้งาน เช่น นม, นอน, อาหาร, วัคซีน..."
                        else "Search features… milk, sleep, food, vaccine...",
                        color = Outline,
                        fontSize = 13.5.sp
                    )
                },
                leadingIcon = { Icon(Icons.Filled.Search, null, tint = Primary) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Filled.Clear, if (isThai) "ล้าง" else "Clear", tint = Outline)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(100.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(0.7f),
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                )
            )

            // Category Filter Chips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(QuickActionCategory.entries.toTypedArray()) { cat ->
                    val isSelected = selectedCategory == cat
                    val count = remember(cat) {
                        if (cat == QuickActionCategory.ALL) QuickActionCatalog.all.size
                        else QuickActionCatalog.all.count { it.category == cat }
                    }
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedCategory = cat },
                        leadingIcon = {
                            Icon(
                                cat.icon,
                                null,
                                modifier = Modifier.size(15.dp),
                                tint = if (isSelected) Color.White else Primary
                            )
                        },
                        label = {
                            Text(
                                "${if (isThai) cat.labelTh else cat.labelEn} ($count)",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        shape = RoundedCornerShape(100.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Primary,
                            selectedLabelColor = Color.White,
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                }
            }

            Text(
                if (query.isBlank()) {
                    if (isThai) "การใช้งานทั้งหมด (${results.size})" else "All actions (${results.size})"
                } else {
                    if (isThai) "พบ ${results.size} รายการ" else "${results.size} matches"
                },
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (results.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Filled.SearchOff, null, tint = Outline, modifier = Modifier.size(36.dp))
                        Text(
                            if (isThai) "ไม่พบสิ่งที่ค้นหา ลองคำอื่น เช่น สุขภาพ วัคซีน นอน"
                            else "No matches — try health, vaccine, sleep…",
                            color = Outline
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(results, key = { it.route }) { action ->
                        QuickActionRow(action, isThai) { navController.navigate(action.route) }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickActionRow(action: QuickAction, isThai: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(action.iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(action.icon, null, tint = action.iconTint, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    if (isThai) action.labelTh else action.labelEn,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                action.subtitleTh?.let { subTh ->
                    Text(
                        if (isThai) subTh else (action.subtitleEn ?: subTh),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), modifier = Modifier.size(18.dp))
        }
    }
}

