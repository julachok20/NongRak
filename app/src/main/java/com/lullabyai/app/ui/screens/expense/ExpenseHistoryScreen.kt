@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.expense

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.viewmodel.ExpenseCategories
import com.lullabyai.app.viewmodel.ExpenseViewModel
import java.time.LocalDate

/**
 * Design: `48.Cost/expense_history` — the full list behind the summary's "ดูทั้งหมด".
 *
 * The month chips move the whole screen's window (the same month the summary reports on), while
 * the search box only narrows what that window already loaded. Keeping the two jobs separate is
 * why searching does not silently pull in another month's spending.
 */
@Composable
fun ExpenseHistoryScreen(
    viewModel: ExpenseViewModel,
    navController: NavController,
    onBack: () -> Unit
) {
    val s = LocalStrings.current
    val isThai = s.isThai
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) { viewModel.load() }

    val months = remember {
        (0..2).map { LocalDate.now().withDayOfMonth(1).minusMonths(it.toLong()) }
    }

    Scaffold(
        containerColor = ScBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isThai) "สรุปค่าใช้จ่าย" else "Expenses",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = ScPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, null, tint = ScPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ScBackground)
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::setQuery,
                    modifier = Modifier.fillMaxWidth(),
                    shape = CircleShape,
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Search, null, tint = ScOutline) },
                    placeholder = {
                        Text(if (isThai) "ค้นหารายการ..." else "Search expenses…", color = ScOutline)
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ScPrimary,
                        unfocusedBorderColor = ScOutlineVariant,
                        focusedContainerColor = ScSurfaceLow,
                        unfocusedContainerColor = ScSurfaceLow,
                        cursorColor = ScPrimary
                    )
                )
            }

            item {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    months.forEach { m ->
                        FilterChipPill(
                            label = monthLabel(m, isThai),
                            selected = m == state.month,
                            onClick = { viewModel.setMonth(m) }
                        )
                    }
                }
            }

            item {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ExpenseCategories.ALL.forEach { c ->
                        FilterChipPill(
                            label = if (isThai) c.th else c.en,
                            selected = state.categoryFilter == c.key,
                            // The view model treats re-picking the active category as "clear".
                            onClick = { viewModel.setCategoryFilter(c.key) }
                        )
                    }
                }
            }

            if (state.loading) {
                item {
                    Box(
                        Modifier.fillMaxWidth().padding(40.dp),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator(color = ScPrimary) }
                }
            }

            state.error?.let { msg ->
                item { Text(msg, color = ScError, style = MaterialTheme.typography.bodySmall) }
            }

            items(state.visible, key = { it.id }) { e -> ExpenseRow(e, isThai) }

            if (!state.loading && state.visible.isEmpty() && state.error == null) {
                item {
                    Text(
                        if (isThai) "ไม่พบรายการที่ตรงกับการค้นหา" else "Nothing matches that search",
                        color = ScOnSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(32.dp)
                    )
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun FilterChipPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(CircleShape)
            .background(if (selected) ScPrimary else ScSurfaceContainer)
            .border(
                1.dp,
                if (selected) ScPrimary else ScOutlineVariant.copy(0.5f),
                CircleShape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            label,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) Color.White else ScOnSurfaceVariant
        )
    }
}
