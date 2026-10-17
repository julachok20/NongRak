@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.expense

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.lullabyai.app.data.model.BudgetLine
import com.lullabyai.app.data.model.ChildExpense
import androidx.compose.ui.draw.clip
import com.lullabyai.app.ui.components.LullabyTopBar
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.screens.child.ChildBottomNav
import com.lullabyai.app.viewmodel.ExpenseCategories
import com.lullabyai.app.viewmodel.ExpenseErrors
import com.lullabyai.app.viewmodel.ExpenseViewModel
import java.time.LocalDate

/* ---------------------------------------------------------------------------------------------
 * "Serene Clinical" palette, taken from the tailwind config the 48.Cost designs ship with rather
 * than from the app theme: these screens are matched to that spec directly, and reading the values
 * off the design keeps them from drifting when the shared theme changes for other reasons.
 * ------------------------------------------------------------------------------------------- */
internal val ScBackground = Color(0xFFF8F9FF)
internal val ScSurface = Color(0xFFFFFFFF)
internal val ScSurfaceLow = Color(0xFFEFF4FF)
internal val ScSurfaceContainer = Color(0xFFE5EEFF)
internal val ScSurfaceHigh = Color(0xFFDCE9FF)
internal val ScSurfaceHighest = Color(0xFFD3E4FE)
internal val ScOnSurface = Color(0xFF0B1C30)
internal val ScOnSurfaceVariant = Color(0xFF444654)
internal val ScOutline = Color(0xFF747685)
internal val ScOutlineVariant = Color(0xFFC4C5D6)
internal val ScPrimary = Color(0xFF173DB8)
internal val ScSecondary = Color(0xFF3A59B0)
internal val ScSecondaryContainer = Color(0xFF84A2FE)
internal val ScTertiary = Color(0xFF00554C)
internal val ScTertiaryContainer = Color(0xFF007064)
internal val ScError = Color(0xFFBA1A1A)
internal val ScErrorContainer = Color(0xFFFFDAD6)

private val ThaiMonthsShort = listOf(
    "ม.ค.", "ก.พ.", "มี.ค.", "เม.ย.", "พ.ค.", "มิ.ย.",
    "ก.ค.", "ส.ค.", "ก.ย.", "ต.ค.", "พ.ย.", "ธ.ค."
)
private val ThaiMonthsFull = listOf(
    "มกราคม", "กุมภาพันธ์", "มีนาคม", "เมษายน", "พฤษภาคม", "มิถุนายน",
    "กรกฎาคม", "สิงหาคม", "กันยายน", "ตุลาคม", "พฤศจิกายน", "ธันวาคม"
)

/** "2023-08-24" -> "24 ส.ค. 2566". Thai dates in this app are Buddhist era, as the design shows. */
internal fun thaiDate(iso: String?, isThai: Boolean): String {
    val d = runCatching { LocalDate.parse(iso.orEmpty().take(10)) }.getOrNull() ?: return ""
    return if (isThai) "${d.dayOfMonth} ${ThaiMonthsShort[d.monthValue - 1]} ${d.year + 543}"
    else "${d.dayOfMonth}/${d.monthValue}/${d.year}"
}

internal fun monthLabel(m: LocalDate, isThai: Boolean): String =
    if (isThai) "${ThaiMonthsFull[m.monthValue - 1]} ${m.year + 543}"
    else "${m.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${m.year}"

internal fun shortMonth(iso: String?): String =
    runCatching { ThaiMonthsShort[LocalDate.parse(iso.orEmpty().take(10)).monthValue - 1] }
        .getOrDefault("")

/** Bare baht, no decimals — the design writes "12,450", never "12,450.00". */
internal fun baht(satang: Long): String = "%,d".format(satang / 100)

/**
 * Design: `48.Cost/expense_summary_with_budget_settings` — the Finance home.
 *
 * Budget sits at the top because it is the only number that turns a list of past spending into a
 * decision about the rest of the month. Everything below it explains that one bar: where the money
 * went, how this month compares to the last two, and what was bought most recently.
 */
@Composable
fun ExpenseScreen(
    viewModel: ExpenseViewModel,
    navController: NavController,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onSeeAll: (() -> Unit)? = null
) {
    val s = LocalStrings.current
    val isThai = s.isThai
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) { viewModel.load() }

    var showBudgetSheet by remember { mutableStateOf(false) }
    var showMonthMenu by remember { mutableStateOf(false) }
    /** The expense whose receipt sheet is open — view it, or attach one if it has none. */
    var receiptTarget by remember { mutableStateOf<ChildExpense?>(null) }

    Scaffold(
        containerColor = ScBackground,
        // Standard app header (back — title — bell — avatar), same as every other screen. The
        // month filter moved onto the budget card's own month chip, which is where the design
        // puts it anyway.
        topBar = {
            LullabyTopBar(
                title = if (isThai) "สรุปค่าใช้จ่าย" else "Expense summary",
                onBack = onBack,
                navController = navController
            )
        },
        bottomBar = { ChildBottomNav(navController = navController, currentTab = "child") },
        floatingActionButton = {
            // Hidden on ทั้งหมด: a cost belongs to one child, and the roll-up names none. Offering
            // it there is what made it possible to record against the wrong child.
            if (!state.showTotal) {
                FloatingActionButton(
                    onClick = onAdd,
                    containerColor = ScPrimary,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(12.dp)
                ) { Icon(Icons.Filled.Add, if (isThai) "เพิ่มรายการ" else "Add expense") }
            }
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Two chips only: this child, and the ทั้งหมด roll-up. Other children are not offered
            // here at all — each child's record is reached from that child.
            item {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ScopeChip(
                        label = state.childName.ifBlank { if (isThai) "ลูก" else "This child" },
                        selected = !state.showTotal,
                        onClick = { viewModel.setShowTotal(false) }
                    )
                    ScopeChip(
                        label = if (isThai) "ทั้งหมด" else "All children",
                        selected = state.showTotal,
                        onClick = { viewModel.setShowTotal(true) }
                    )
                }
            }

            item {
                BudgetCard(
                    budget = state.overallBudget,
                    spentSatang = state.monthTotalSatang,
                    month = state.month,
                    isThai = isThai,
                    onEdit = { showBudgetSheet = true },
                    onPickMonth = { showMonthMenu = true },
                    menuOpen = showMonthMenu,
                    onDismissMenu = { showMonthMenu = false },
                    onPickMonthValue = { showMonthMenu = false; viewModel.setMonth(it) }
                )
            }

            if (state.expiringProducts.isNotEmpty()) {
                item {
                    ExpiringProductsAlertCard(
                        products = state.expiringProducts,
                        isThai = isThai,
                        onToggleUsed = { e -> viewModel.toggleUsed(e) }
                    )
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    StatCard(
                        icon = Icons.Filled.Today,
                        label = if (isThai) "เฉลี่ยต่อวัน" else "Daily average",
                        value = "${baht(state.dailyAverageSatang)} ฿",
                        valueColor = ScOnSurface,
                        modifier = Modifier.weight(1f)
                    )
                    // The design's second tile is "เงินออมรวม" (total savings). Nothing in the app
                    // models savings, so rather than invent a number this reports what the month
                    // actually contains.
                    StatCard(
                        icon = Icons.Filled.Receipt,
                        label = if (isThai) "รายการเดือนนี้" else "Entries this month",
                        value = "${state.summary.sumOf { it.entries }}",
                        valueColor = ScSecondary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (state.trend.isNotEmpty()) {
                item { TrendCard(state.trend, isThai) }
            }

            if (state.categoryBudgets.isNotEmpty()) {
                item { CategoryBreakdown(state.categoryBudgets, isThai) }
            }

            item {
                Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (isThai) "รายการล่าสุด" else "Recent",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = ScOnSurface,
                        modifier = Modifier.weight(1f)
                    )
                    onSeeAll?.let {
                        Text(
                            if (isThai) "ดูทั้งหมด" else "See all",
                            color = ScPrimary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable(onClick = it)
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

            // Shown here too: ticking "used" on a frozen row fails from the list, not the sheet.
            ExpenseErrors.message(state.error, isThai)?.let { msg ->
                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(ScErrorContainer, RoundedCornerShape(10.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(Icons.Filled.Warning, null, tint = ScError, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(msg, color = ScError, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Text(
                            if (isThai) "ปิด" else "Dismiss",
                            color = ScError,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            modifier = Modifier.clickable { viewModel.clearSaved() }
                        )
                    }
                }
            }

            items(state.expenses.take(5), key = { it.id }) { e ->
                ExpenseRow(
                    e = e,
                    isThai = isThai,
                    childName = if (!state.showTotal) null
                    else state.children.find { c -> c.id == e.childId }?.name,
                    onToggleUsed = { viewModel.toggleUsed(e) },
                    onReceipt = { receiptTarget = e }
                )
            }

            if (!state.loading && state.expenses.isEmpty() && state.error == null) {
                item {
                    Text(
                        if (isThai) "ยังไม่มีรายการค่าใช้จ่าย" else "No expenses recorded yet",
                        color = ScOnSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(32.dp)
                    )
                }
            }

            item { Spacer(Modifier.height(72.dp)) }
        }
    }

    receiptTarget?.let { target ->
        ReceiptSheet(
            expense = target,
            isThai = isThai,
            saving = state.saving,
            resolveUrl = { viewModel.receiptUrl(it) },
            onAttach = { bytes -> viewModel.attachReceipt(target, bytes); receiptTarget = null },
            onDismiss = { receiptTarget = null }
        )
    }

    // Sits above the sheet: the numbers being compared are the whole point, so they are stated
    // rather than summarised as "too big".
    state.overTotalPrompt?.let { p ->
        AlertDialog(
            onDismissRequest = viewModel::dismissOverTotal,
            containerColor = ScSurface,
            icon = { Icon(Icons.Filled.Warning, null, tint = ScError) },
            title = {
                Text(
                    if (isThai) "งบรวมต่ำกว่างบของลูก" else "Total goal is below the child goals",
                    fontWeight = FontWeight.Bold,
                    color = ScOnSurface
                )
            },
            text = {
                Text(
                    if (isThai)
                        "งบของลูกทุกคนรวมกันคือ ${ExpenseErrors.bahtText(p.allocatedSatang)} ฿ " +
                            "เทียบกับงบรวมที่ตั้งใหม่ ${ExpenseErrors.bahtText(p.attemptedSatang)} ฿\n\n" +
                            "ต้องการล้างงบของลูกทุกคนในเดือนนี้ แล้วใช้งบรวมใหม่หรือไม่? " +
                            "(รายการค่าใช้จ่ายจะไม่ถูกลบ)"
                    else
                        "The children's goals total ${ExpenseErrors.bahtText(p.allocatedSatang)} ฿, " +
                            "compared with the new total goal of ${ExpenseErrors.bahtText(p.attemptedSatang)} ฿.\n\n" +
                            "Clear every child goal for this month and use the new total? " +
                            "(Recorded expenses are not deleted.)",
                    color = ScOnSurfaceVariant,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.confirmOverTotal { showBudgetSheet = false } }
                ) {
                    Text(
                        if (isThai) "ล้างงบของลูก" else "Clear child goals",
                        color = ScError,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissOverTotal) {
                    Text(if (isThai) "ยกเลิก" else "Cancel", color = ScOnSurfaceVariant)
                }
            }
        )
    }

    if (showBudgetSheet) {
        BudgetSheet(
            current = state.budget,
            month = state.month,
            isThai = isThai,
            saving = state.saving,
            error = state.error,
            scopeLabel = if (state.showTotal) (if (isThai) "ทั้งหมด" else "All children")
            else state.childName,
            isTotal = state.showTotal,
            showTotal = state.showTotal,
            onPickScope = viewModel::setShowTotal,
            onClearError = viewModel::clearSaved,
            onDismiss = { showBudgetSheet = false; viewModel.clearSaved() },
            onSave = { total, cats ->
                viewModel.saveBudget(total, cats) { showBudgetSheet = false }
            }
        )
    }
}

@Composable
private fun MonthMenu(
    expanded: Boolean,
    current: LocalDate,
    isThai: Boolean,
    onDismiss: () -> Unit,
    onPick: (LocalDate) -> Unit
) {
    val months = (0..5).map { LocalDate.now().withDayOfMonth(1).minusMonths(it.toLong()) }
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        months.forEach { m ->
            DropdownMenuItem(
                text = {
                    Text(
                        monthLabel(m, isThai),
                        fontWeight = if (m == current) FontWeight.Bold else FontWeight.Normal,
                        color = if (m == current) ScPrimary else ScOnSurface
                    )
                },
                onClick = { onPick(m) }
            )
        }
    }
}

/** The headline card: a 4px primary accent on the left, exactly as the design's `border-l-4`. */
@Composable
private fun BudgetCard(
    budget: BudgetLine?,
    spentSatang: Long,
    month: LocalDate,
    isThai: Boolean,
    onEdit: () -> Unit,
    onPickMonth: () -> Unit,
    menuOpen: Boolean,
    onDismissMenu: () -> Unit,
    onPickMonthValue: (LocalDate) -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ScSurface)
            .border(1.dp, ScOutlineVariant.copy(0.5f), RoundedCornerShape(12.dp))
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(4.dp).fillMaxHeight().background(ScPrimary))
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (isThai) "งบประมาณเดือนนี้" else "This month's budget",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = ScOnSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                baht(spentSatang),
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (budget?.overBudget == true) ScError else ScOnSurface
                            )
                            Text(
                                if (budget != null) " / ${baht(budget.budgetSatang)} ฿" else " ฿",
                                fontSize = 16.sp,
                                color = ScOnSurfaceVariant,
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                        }
                    }
                    // The menu is anchored here rather than in the app bar, so it drops from the
                    // control that opens it.
                    Box {
                        Surface(
                            onClick = onPickMonth,
                            shape = RoundedCornerShape(8.dp),
                            color = ScPrimary.copy(0.08f)
                        ) {
                            Row(
                                Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    monthLabel(month, isThai).substringBefore(' '),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = ScPrimary
                                )
                                Icon(
                                    Icons.Filled.ExpandMore, null,
                                    tint = ScPrimary, modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        MonthMenu(
                            expanded = menuOpen,
                            current = month,
                            isThai = isThai,
                            onDismiss = onDismissMenu,
                            onPick = onPickMonthValue
                        )
                    }
                }

                if (budget != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        LinearProgressIndicator(
                            progress = { budget.progress },
                            modifier = Modifier.fillMaxWidth().height(12.dp).clip(CircleShape),
                            color = if (budget.overBudget) ScError else ScPrimary,
                            trackColor = ScSurfaceHigh,
                            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                            gapSize = 0.dp,
                            drawStopIndicator = {}
                        )
                        Row(Modifier.fillMaxWidth()) {
                            Text(
                                (if (isThai) "ใช้ไปแล้ว " else "Used ") +
                                    "${(budget.progress * 100).toInt()}%",
                                fontSize = 12.sp,
                                color = ScOnSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                if (budget.overBudget)
                                    (if (isThai) "เกินงบ " else "Over by ") +
                                        "${baht(-budget.remainingSatang)} ฿"
                                else (if (isThai) "เหลือ " else "Left ") +
                                    "${baht(budget.remainingSatang)} ฿",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (budget.overBudget) ScError else ScTertiary
                            )
                        }
                    }
                }

                Surface(
                    onClick = onEdit,
                    shape = RoundedCornerShape(8.dp),
                    color = ScSecondary.copy(0.10f)
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Filled.Settings, null,
                            tint = ScSecondary, modifier = Modifier.size(16.dp)
                        )
                        Text(
                            if (isThai) "ตั้งค่างบประมาณ" else "Set budget",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = ScSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(ScSurface)
            .border(1.dp, ScOutlineVariant.copy(0.5f), RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = ScOnSurfaceVariant, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(label, fontSize = 12.sp, color = ScOnSurfaceVariant, maxLines = 1)
        }
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = valueColor)
    }
}

/** The design's three-bar trend. Heights are relative to the tallest month, never to the budget. */
@Composable
private fun TrendCard(
    points: List<com.lullabyai.app.data.model.ExpenseTrendPoint>,
    isThai: Boolean
) {
    val peak = points.maxOfOrNull { it.totalSatang }?.coerceAtLeast(1L) ?: 1L
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ScSurface)
            .border(1.dp, ScOutlineVariant.copy(0.5f), RoundedCornerShape(12.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            if (isThai) "แนวโน้มย้อนหลัง ${points.size} เดือน" else "Last ${points.size} months",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = ScOnSurfaceVariant
        )
        Row(
            Modifier.fillMaxWidth().height(140.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom
        ) {
            points.forEachIndexed { index, p ->
                val newest = index == points.lastIndex
                val fraction = (p.totalSatang.toFloat() / peak).coerceIn(0.02f, 1f)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    Text(
                        baht(p.totalSatang),
                        fontSize = 11.sp,
                        fontWeight = if (newest) FontWeight.Bold else FontWeight.Normal,
                        color = if (newest) ScPrimary else ScOnSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    Box(
                        Modifier
                            .width(40.dp)
                            .fillMaxHeight(fraction)
                            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                            .background(if (newest) ScPrimary else ScSecondaryContainer.copy(0.6f))
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        shortMonth(p.month),
                        fontSize = 12.sp,
                        fontWeight = if (newest) FontWeight.Medium else FontWeight.Normal,
                        color = ScOnSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryBreakdown(lines: List<BudgetLine>, isThai: Boolean) {
    val peak = lines.maxOfOrNull { maxOf(it.spentSatang, it.budgetSatang) }?.coerceAtLeast(1L) ?: 1L
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ScSurface)
            .border(1.dp, ScOutlineVariant.copy(0.5f), RoundedCornerShape(12.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            if (isThai) "แยกตามหมวดหมู่" else "By category",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = ScOnSurfaceVariant
        )
        lines.forEach { line ->
            val key = line.category ?: return@forEach
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        ExpenseCategories.icon(key), null,
                        tint = ScTertiary, modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        ExpenseCategories.label(key, isThai),
                        fontSize = 12.sp,
                        color = ScOnSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "${baht(line.spentSatang)} ฿",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (line.overBudget) ScError else ScOnSurfaceVariant
                    )
                }
                // Bars are scaled against the biggest category so they compare with each other;
                // a category's own budget shows through colour, not by rescaling its bar.
                LinearProgressIndicator(
                    progress = { (line.spentSatang.toFloat() / peak).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                    color = if (line.overBudget) ScError else ScTertiaryContainer,
                    trackColor = ScSurfaceHigh,
                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                    gapSize = 0.dp,
                    drawStopIndicator = {}
                )
                if (line.hasBudget) {
                    Text(
                        (if (isThai) "งบ " else "Budget ") + "${baht(line.budgetSatang)} ฿",
                        fontSize = 11.sp,
                        color = ScOutline
                    )
                }
            }
        }
    }
}

/**
 * How long an expense stays editable after it is ticked as used. Mirrors the database trigger; the
 * UI only greys the controls, Postgres is what actually refuses the write.
 */
private const val USED_EDIT_WINDOW_MINUTES = 30L

/** True once the 30-minute window has closed. Unticked rows are never locked. */
internal fun ChildExpense.isLocked(): Boolean {
    val stamped = runCatching { java.time.OffsetDateTime.parse(usedAt.orEmpty()) }.getOrNull()
        ?: return false
    return stamped.plusMinutes(USED_EDIT_WINDOW_MINUTES).isBefore(java.time.OffsetDateTime.now())
}

/** Whole minutes left in the edit window, or null when the row is not counting down. */
internal fun ChildExpense.minutesLeft(): Long? {
    val stamped = runCatching { java.time.OffsetDateTime.parse(usedAt.orEmpty()) }.getOrNull()
        ?: return null
    val left = java.time.temporal.ChronoUnit.MINUTES.between(
        java.time.OffsetDateTime.now(),
        stamped.plusMinutes(USED_EDIT_WINDOW_MINUTES)
    )
    return left.takeIf { it >= 0 }
}

/**
 * One purchase. The leading checkbox is "used?" — ticking it starts the 30-minute window, after
 * which the row freezes and the checkbox goes read-only with a padlock.
 */
@Composable
internal fun ExpenseRow(
    e: ChildExpense,
    isThai: Boolean,
    childName: String? = null,
    onToggleUsed: (() -> Unit)? = null,
    onReceipt: (() -> Unit)? = null
) {
    val locked = e.isLocked()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ScSurface)
            .border(1.dp, ScOutlineVariant.copy(0.3f), RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onToggleUsed != null) {
            Box(contentAlignment = Alignment.Center) {
                Checkbox(
                    checked = e.alreadyUsed,
                    onCheckedChange = { if (!locked) onToggleUsed() },
                    enabled = !locked,
                    colors = CheckboxDefaults.colors(
                        checkedColor = ScPrimary,
                        uncheckedColor = ScOutlineVariant,
                        disabledCheckedColor = ScOutline
                    )
                )
                if (locked) {
                    Icon(
                        Icons.Filled.Lock, null,
                        tint = ScOutline,
                        modifier = Modifier.size(12.dp).offset(x = 12.dp, y = 12.dp)
                    )
                }
            }
            Spacer(Modifier.width(4.dp))
        }
        Box(
            Modifier.size(44.dp).background(ScSurfaceHighest, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(ExpenseCategories.icon(e.category), null, tint = ScPrimary)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                // Product name first — that is the design's row title. Note, then category, are
                // only fallbacks for rows saved before the name field existed.
                e.name?.takeIf { it.isNotBlank() }
                    ?: e.note?.takeIf { it.isNotBlank() }
                    ?: ExpenseCategories.label(e.category, isThai),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = ScOnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                thaiDate(e.spentOn, isThai) + (childName?.let { " • $it" } ?: ""),
                fontSize = 12.sp,
                color = ScOnSurfaceVariant
            )
            // Only while it is actually ticking — a countdown on a row nobody is editing is noise.
            e.minutesLeft()?.takeIf { e.alreadyUsed && !locked }?.let { left ->
                Text(
                    if (isThai) "แก้ไขได้อีก $left นาที" else "editable for $left more min",
                    fontSize = 11.sp,
                    color = ScTertiary
                )
            }

            val chips = buildList {
                if (e.hasWarranty) {
                    add(Triple(Icons.Filled.VerifiedUser, warrantyChipText(e, isThai), e.warrantyExpiringSoon))
                }
                if (e.hasExpiration && !e.expiresOn.isNullOrBlank()) {
                    val expired = e.productExpired
                    val label = when {
                        expired -> (if (isThai) "หมดอายุแล้ว " else "Expired ") + thaiDate(e.expiresOn, isThai)
                        else -> (if (isThai) "หมดอายุ " else "Expires ") + thaiDate(e.expiresOn, isThai)
                    }
                    add(
                        Triple(
                            Icons.Filled.Warning,
                            label,
                            e.productExpiringSoon || expired
                        )
                    )
                }
                if (e.alreadyUsed) {
                    add(Triple(Icons.Filled.EventBusy, if (isThai) "ใช้งานแล้ว" else "Used", false))
                }
            }
            if (chips.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    chips.forEach { (icon, text, urgent) ->
                        Row(
                            Modifier
                                .background(
                                    if (urgent) ScErrorContainer else ScTertiary.copy(0.10f),
                                    CircleShape
                                )
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                icon, null,
                                tint = if (urgent) ScError else ScTertiary,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(Modifier.width(3.dp))
                            Text(
                                text,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (urgent) ScError else ScTertiary
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                "${baht(e.amountSatang)} ฿",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = ScOnSurface
            )
            // Always offered: with a receipt it opens one, without it invites you to add one.
            onReceipt?.let { open ->
                Spacer(Modifier.height(4.dp))
                Row(
                    Modifier.clickable(onClick = open),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Receipt, null,
                        tint = if (e.receiptUrl.isNullOrBlank()) ScOutline else ScSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        when {
                            !e.receiptUrl.isNullOrBlank() && isThai -> "ดูใบเสร็จ"
                            !e.receiptUrl.isNullOrBlank() -> "View receipt"
                            isThai -> "แนบใบเสร็จ"
                            else -> "Add receipt"
                        },
                        fontSize = 11.sp,
                        color = if (e.receiptUrl.isNullOrBlank()) ScOutline else ScSecondary
                    )
                }
            }
        }
    }
}

/** The design's avatar strip: each child, plus "ทั้งหมด" for everyone at once. */
@Composable
private fun ChildScopeStrip(
    children: List<com.lullabyai.app.data.model.Child>,
    selected: String?,
    isThai: Boolean,
    onPick: (String?) -> Unit
) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ScopeChip(
            label = if (isThai) "ทั้งหมด" else "All",
            selected = selected == null,
            onClick = { onPick(null) }
        )
        children.forEach { c ->
            ScopeChip(
                label = c.name,
                selected = selected == c.id,
                onClick = { onPick(c.id) }
            )
        }
    }
}

@Composable
private fun ScopeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(CircleShape)
            .background(if (selected) ScPrimary else ScSurfaceContainer)
            .border(
                if (selected) 2.dp else 1.dp,
                if (selected) ScPrimary else ScOutlineVariant.copy(0.4f),
                CircleShape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            label,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) Color.White else ScOnSurfaceVariant,
            maxLines = 1
        )
    }
}

/**
 * "ดูใบเสร็จ" — the receipt for one expense, or the way to add one when it has none.
 *
 * The path is resolved to a signed URL on open rather than stored: the bucket is private and the
 * signature expires, so a URL held in state would eventually render a broken image.
 */
@Composable
private fun ReceiptSheet(
    expense: ChildExpense,
    isThai: Boolean,
    saving: Boolean,
    resolveUrl: suspend (String?) -> String?,
    onAttach: (ByteArray) -> Unit,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var url by remember(expense.id) { mutableStateOf<String?>(null) }
    var tooBig by remember { mutableStateOf(false) }
    val locked = expense.isLocked()

    LaunchedEffect(expense.id, expense.receiptUrl) {
        url = expense.receiptUrl?.let { resolveUrl(it) }
    }

    val pick = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val bytes = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull()
        if (bytes != null && bytes.size <= 5 * 1024 * 1024) { tooBig = false; onAttach(bytes) }
        else tooBig = bytes != null
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = ScBackground) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                if (isThai) "ใบเสร็จ" else "Receipt",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = ScPrimary
            )

            if (!expense.receiptUrl.isNullOrBlank()) {
                if (url == null) {
                    CircularProgressIndicator(color = ScPrimary)
                } else {
                    coil.compose.AsyncImage(
                        model = url,
                        contentDescription = if (isThai) "ใบเสร็จ" else "Receipt",
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 460.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                }
            } else {
                Text(
                    if (isThai) "ยังไม่มีใบเสร็จสำหรับรายการนี้"
                    else "No receipt on this expense yet",
                    fontSize = 14.sp,
                    color = ScOnSurfaceVariant
                )
                if (locked) {
                    // Honest about why the button is missing rather than just hiding it.
                    Text(
                        if (isThai)
                            "รายการนี้ถูกล็อกแล้ว (ทำเครื่องหมายว่าใช้แล้วเกิน 30 นาที) จึงแนบใบเสร็จไม่ได้"
                        else "This expense is locked (marked used over 30 minutes ago), so a receipt can no longer be attached.",
                        fontSize = 12.sp,
                        color = ScError
                    )
                } else {
                    Button(
                        onClick = { pick.launch("image/*") },
                        enabled = !saving,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ScPrimary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.AddAPhoto, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (isThai) "อัปโหลดใบเสร็จ" else "Upload a receipt")
                    }
                    Text(
                        if (isThai) "PNG, JPG สูงสุด 5MB" else "PNG, JPG up to 5MB",
                        fontSize = 12.sp,
                        color = ScOutline
                    )
                }
                if (tooBig) {
                    Text(
                        if (isThai) "ไฟล์ใหญ่เกิน 5MB" else "That file is larger than 5MB",
                        color = ScError,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

private fun warrantyChipText(e: ChildExpense, isThai: Boolean): String =
    if (e.warrantyExpiresOn.isNullOrBlank()) (if (isThai) "มีประกัน" else "Warranty")
    else (if (isThai) "ประกันถึง " else "Warranty to ") + thaiDate(e.warrantyExpiresOn, isThai)

/** Inside a week counts as urgent — long enough to act on, short enough to matter. */
private val ChildExpense.warrantyExpiringSoon: Boolean
    get() = daysUntil(warrantyExpiresOn)?.let { it in 0..7 } == true

private val ChildExpense.productExpiringSoon: Boolean
    get() = daysUntil(expiresOn)?.let { it in 0..7 } == true

private val ChildExpense.productExpired: Boolean
    get() = daysUntil(expiresOn)?.let { it < 0 } == true

private fun daysUntil(iso: String?): Long? {
    val d = runCatching { LocalDate.parse(iso.orEmpty().take(10)) }.getOrNull() ?: return null
    return java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), d)
}

/**
 * Alert card for nearly expired or expired products in the cost record.
 * Displays items with expiration status, days left, and a one-tap "Mark as used" button.
 */
@Composable
private fun ExpiringProductsAlertCard(
    products: List<ChildExpense>,
    isThai: Boolean,
    onToggleUsed: (ChildExpense) -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF4ED)),
        border = BorderStroke(1.dp, Color(0xFFFFD8C2)),
        elevation = CardDefaults.cardElevation(2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(Color(0xFFFFE0CC), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Warning,
                        contentDescription = null,
                        tint = Color(0xFFD9531E),
                        modifier = Modifier.size(18.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isThai) "แจ้งเตือน: สินค้าใกล้หมดอายุ (${products.size} รายการ)"
                        else "Alert: Products Expiring Soon (${products.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color(0xFF942F00)
                    )
                    Text(
                        text = if (isThai) "ตรวจสอบก่อนนำไปใช้งาน หรือแตะเมื่อใช้แล้ว"
                        else "Check before use, or mark as used when opened",
                        fontSize = 11.sp,
                        color = Color(0xFFB54710)
                    )
                }
            }

            HorizontalDivider(color = Color(0xFFFFE4D6), thickness = 1.dp)

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                products.take(5).forEach { product ->
                    val days = daysUntil(product.expiresOn)
                    val isExpired = days != null && days < 0
                    val isToday = days != null && days == 0L
                    val badgeText = when {
                        isExpired -> if (isThai) "หมดอายุแล้ว" else "Expired"
                        isToday -> if (isThai) "หมดอายุวันนี้" else "Expires today"
                        days != null -> if (isThai) "อีก $days วัน" else "In $days days"
                        else -> if (isThai) "ใกล้หมดอายุ" else "Expiring"
                    }
                    val badgeColor = if (isExpired || isToday) ScError else Color(0xFFD9531E)
                    val badgeBg = if (isExpired || isToday) ScErrorContainer else Color(0xFFFFE0CC)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White, RoundedCornerShape(12.dp))
                            .border(1.dp, Color(0xFFFFE4D6), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = product.name?.takeIf { it.isNotBlank() }
                                    ?: product.note?.takeIf { it.isNotBlank() }
                                    ?: (if (isThai) "สินค้า" else "Product"),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = ScOnSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = (if (isThai) "หมดอายุ: " else "Expires: ") + thaiDate(product.expiresOn, isThai),
                                    fontSize = 11.sp,
                                    color = ScOnSurfaceVariant
                                )
                                Text(
                                    text = "•",
                                    fontSize = 11.sp,
                                    color = ScOutlineVariant
                                )
                                Text(
                                    text = "${baht(product.amountSatang)} ฿",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = ScOnSurfaceVariant
                                )
                            }
                        }

                        // Badge
                        Text(
                            text = badgeText,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = badgeColor,
                            modifier = Modifier
                                .background(badgeBg, CircleShape)
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        )

                        // Quick action button: "ใช้แล้ว" (Mark as used)
                        OutlinedButton(
                            onClick = { onToggleUsed(product) },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, ScSecondary),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text(
                                text = if (isThai) "ใช้แล้ว" else "Used",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = ScSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * "ตั้งค่างบประมาณ" — the design's modal, as a bottom sheet because that is what this app already
 * uses everywhere else for the same job.
 */
@Composable
private fun BudgetSheet(
    current: List<BudgetLine>,
    month: LocalDate,
    isThai: Boolean,
    saving: Boolean,
    error: String?,
    scopeLabel: String,
    isTotal: Boolean,
    /** Which of the two goals is being edited: this child's, or the ทั้งหมด overall goal. */
    showTotal: Boolean,
    onPickScope: (Boolean) -> Unit,
    onClearError: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (String, Map<String, String>) -> Unit
) {
    // Seeded from what is already stored, so opening the sheet and saving without editing is a
    // no-op rather than a way to silently wipe the month's budget.
    //
    // Keyed on both the scope and the loaded figures. Without the keys the fields keep whatever
    // they were first seeded with, so switching child and reopening showed the previous child's
    // goal — the goals were always separate on the server, the form just never re-read them. The
    // data key matters too: the scope changes the moment the strip is tapped, while the new
    // child's budget arrives a round trip later.
    var total by remember(scopeLabel, current) {
        mutableStateOf(
            current.find { it.category == null }?.takeIf { it.hasBudget }
                ?.let { (it.budgetSatang / 100).toString() } ?: ""
        )
    }
    val perCategory = remember(scopeLabel, current) {
        mutableStateMapOf<String, String>().apply {
            ExpenseCategories.ALL.forEach { c ->
                put(
                    c.key,
                    current.find { it.category == c.key }?.takeIf { it.hasBudget }
                        ?.let { (it.budgetSatang / 100).toString() } ?: ""
                )
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = ScBackground) {
        Column(
            Modifier
                .fillMaxWidth()
                // Nine fields plus a wrapped error line outgrow the sheet. Without this the lower
                // categories and the Save button are pushed off the bottom and cannot be reached —
                // which looks exactly like the form refusing further input.
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                if (isThai) "ตั้งค่างบประมาณ · $scopeLabel" else "Set budget · $scopeLabel",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = ScPrimary
            )

            // Switch between this child's goal and the overall one without closing the sheet. The
            // fields re-seed from whichever is chosen as its figures arrive.
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ScopeChip(
                    label = scopeLabel.takeIf { !showTotal }
                        ?: (if (isThai) "ลูกคนนี้" else "This child"),
                    selected = !showTotal,
                    onClick = { onPickScope(false) }
                )
                ScopeChip(
                    label = if (isThai) "ทั้งหมด" else "All children",
                    selected = showTotal,
                    onClick = { onPickScope(true) }
                )
            }
            // Naming the scope matters here: the total goal and each child's goal are separate
            // figures, and the sheet looks identical either way.
            Text(
                when {
                    isTotal && isThai ->
                        "งบรวมของ ${monthLabel(month, true)} ใช้กับมุมมองทั้งหมดเท่านั้น ไม่ใช่ผลรวมของงบลูกแต่ละคน"
                    isTotal ->
                        "The overall goal for ${monthLabel(month, false)}. It applies to the all-children view only — it is not the sum of each child's goal."
                    isThai ->
                        "งบของ $scopeLabel สำหรับ ${monthLabel(month, true)} รวมกันทุกคนต้องไม่เกินงบรวม"
                    else ->
                        "$scopeLabel's goal for ${monthLabel(month, false)}. All children's goals together cannot exceed the overall goal."
                },
                fontSize = 14.sp,
                color = ScOnSurfaceVariant
            )
            ExpenseErrors.message(error, isThai)?.let { msg ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(ScErrorContainer, RoundedCornerShape(10.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        Icons.Filled.Warning, null,
                        tint = ScError, modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(msg, fontSize = 13.sp, color = ScError, fontWeight = FontWeight.Medium)
                }
            }

            OutlinedTextField(
                value = total,
                // Editing is the user answering the complaint, so the complaint goes away.
                onValueChange = { total = it; onClearError() },
                label = { Text(if (isThai) "งบประมาณรวม (฿)" else "Total budget (฿)") },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                if (isThai) "แยกตามหมวดหมู่ (ระบุหรือไม่ก็ได้)" else "Per category (optional)",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = ScOnSurfaceVariant
            )
            ExpenseCategories.ALL.forEach { c ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        ExpenseCategories.icon(c.key), null,
                        tint = ScSecondary, modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    OutlinedTextField(
                        value = perCategory[c.key].orEmpty(),
                        onValueChange = { perCategory[c.key] = it; onClearError() },
                        placeholder = { Text(if (isThai) c.th else c.en) },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text(if (isThai) "ยกเลิก" else "Cancel")
                }
                Button(
                    onClick = { onSave(total, perCategory.toMap()) },
                    enabled = !saving,
                    colors = ButtonDefaults.buttonColors(containerColor = ScPrimary),
                    modifier = Modifier.weight(1f)
                ) {
                    // Names the target on the button itself. A goal saved to the wrong child is
                    // invisible until you go looking for it, so it is stated before you commit.
                    Text(
                        (if (isThai) "บันทึกให้ " else "Save for ") + scopeLabel,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
