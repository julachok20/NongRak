@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.expense

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterList
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
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.viewmodel.ExpenseCategories
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

    Scaffold(
        containerColor = ScBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isThai) "สรุปค่าใช้จ่าย" else "Expense summary",
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
                actions = {
                    Box {
                        IconButton(onClick = { showMonthMenu = true }) {
                            Icon(Icons.Filled.FilterList, null, tint = ScOnSurfaceVariant)
                        }
                        MonthMenu(
                            expanded = showMonthMenu,
                            current = state.month,
                            isThai = isThai,
                            onDismiss = { showMonthMenu = false },
                            onPick = { showMonthMenu = false; viewModel.setMonth(it) }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ScBackground)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAdd,
                containerColor = ScPrimary,
                contentColor = Color.White,
                shape = RoundedCornerShape(12.dp)
            ) { Icon(Icons.Filled.Add, if (isThai) "เพิ่มรายการ" else "Add expense") }
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                BudgetCard(
                    budget = state.overallBudget,
                    spentSatang = state.monthTotalSatang,
                    month = state.month,
                    isThai = isThai,
                    onEdit = { showBudgetSheet = true },
                    onPickMonth = { showMonthMenu = true }
                )
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

            state.error?.let { msg ->
                item { Text(msg, color = ScError, style = MaterialTheme.typography.bodySmall) }
            }

            items(state.expenses.take(5), key = { it.id }) { e ->
                ExpenseRow(e, isThai)
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

    if (showBudgetSheet) {
        BudgetSheet(
            current = state.budget,
            month = state.month,
            isThai = isThai,
            saving = state.saving,
            onDismiss = { showBudgetSheet = false },
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
    onPickMonth: () -> Unit
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

/** One purchase. The chips below the title are the three switches, read back. */
@Composable
internal fun ExpenseRow(e: ChildExpense, isThai: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ScSurface)
            .border(1.dp, ScOutlineVariant.copy(0.3f), RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(44.dp).background(ScSurfaceHighest, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(ExpenseCategories.icon(e.category), null, tint = ScPrimary)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                e.note?.takeIf { it.isNotBlank() } ?: ExpenseCategories.label(e.category, isThai),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = ScOnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(thaiDate(e.spentOn, isThai), fontSize = 12.sp, color = ScOnSurfaceVariant)

            val chips = buildList {
                if (e.hasWarranty) {
                    add(Triple(Icons.Filled.VerifiedUser, warrantyChipText(e, isThai), e.warrantyExpiringSoon))
                }
                if (e.hasExpiration && !e.expiresOn.isNullOrBlank()) {
                    add(
                        Triple(
                            Icons.Filled.Warning,
                            (if (isThai) "หมดอายุ " else "Expires ") + thaiDate(e.expiresOn, isThai),
                            e.productExpiringSoon
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
        Text(
            "${baht(e.amountSatang)} ฿",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = ScOnSurface
        )
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

private fun daysUntil(iso: String?): Long? {
    val d = runCatching { LocalDate.parse(iso.orEmpty().take(10)) }.getOrNull() ?: return null
    return java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), d)
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
    onDismiss: () -> Unit,
    onSave: (String, Map<String, String>) -> Unit
) {
    // Seeded from what is already stored, so opening the sheet and saving without editing is a
    // no-op rather than a way to silently wipe the month's budget.
    var total by remember {
        mutableStateOf(
            current.find { it.category == null }?.takeIf { it.hasBudget }
                ?.let { (it.budgetSatang / 100).toString() } ?: ""
        )
    }
    val perCategory = remember {
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
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                if (isThai) "ตั้งค่างบประมาณ" else "Set budget",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = ScPrimary
            )
            Text(
                if (isThai)
                    "กำหนดงบประมาณสำหรับ ${monthLabel(month, true)} เพื่อให้คุณควบคุมค่าใช้จ่ายได้ดีขึ้น"
                else "Set a budget for ${monthLabel(month, false)} to keep spending in view.",
                fontSize = 14.sp,
                color = ScOnSurfaceVariant
            )

            OutlinedTextField(
                value = total,
                onValueChange = { total = it },
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
                        onValueChange = { perCategory[c.key] = it },
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
                    Text(if (isThai) "บันทึก" else "Save")
                }
            }
        }
    }
}
