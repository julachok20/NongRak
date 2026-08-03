@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.search

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.lullabyai.app.data.QuickAction
import com.lullabyai.app.data.QuickActionCatalog
import com.lullabyai.app.ui.theme.*

/** Keyword search over every real in-app destination (QuickActionCatalog). Matches against the
 *  Thai label, English label, and hidden synonym keywords — whichever the user typed in. */
@Composable
fun QuickActionSearchScreen(navController: NavController, isThai: Boolean, onBack: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    val results by remember {
        derivedStateOf { QuickActionCatalog.search(query) }
    }

    Scaffold(
        topBar = {
            com.lullabyai.app.ui.components.LullabyTopBar(
                title = if (isThai) "ค้นหาการใช้งาน" else "Quick Action Search",
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
                .padding(horizontal = 16.dp)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .padding(top = 8.dp, bottom = 12.dp),
                placeholder = {
                    Text(
                        if (isThai) "ค้นหาเพื่อดูสิ่งที่ทำได้เพิ่มเติม เช่น วัคซีน นอน อาหาร"
                        else "Search to see more you can do… e.g. vaccine, sleep, food",
                        color = Outline
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
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                )
            )

            Text(
                if (query.isBlank()) {
                    if (isThai) "การใช้งานทั้งหมด (${results.size})" else "All actions (${results.size})"
                } else {
                    if (isThai) "พบ ${results.size} รายการ" else "${results.size} matches"
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            if (results.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (isThai) "ไม่พบสิ่งที่ค้นหา ลองคำอื่น เช่น สุขภาพ วัคซีน นอน"
                        else "No matches — try health, vaccine, sleep…",
                        color = Outline
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
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
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(action.icon, null, tint = Primary, modifier = Modifier.size(22.dp))
            Text(
                if (isThai) action.labelTh else action.labelEn,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Icon(Icons.Filled.ChevronRight, null, tint = Outline, modifier = Modifier.size(18.dp))
        }
    }
}
