package com.lullabyai.app.viewmodel

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Toys
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lullabyai.app.data.model.BudgetLine
import com.lullabyai.app.data.model.ChildExpense
import com.lullabyai.app.data.model.ExpenseCategoryTotal
import com.lullabyai.app.data.model.ExpenseTrendPoint
import com.lullabyai.app.data.repository.ChildRepository
import com.lullabyai.app.data.settings.ChildSelection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class ExpenseUiState(
    val childId: String? = null,
    val childName: String = "",
    val expenses: List<ChildExpense> = emptyList(),
    val summary: List<ExpenseCategoryTotal> = emptyList(),
    /** Warranties still live but running out — surfaced above the list, not buried in it. */
    val expiringWarranties: List<ChildExpense> = emptyList(),
    /** Budget beside spend. The null-category line is the month's overall figure. */
    val budget: List<BudgetLine> = emptyList(),
    val trend: List<ExpenseTrendPoint> = emptyList(),
    /** Which month the summary covers, as its first day. */
    val month: LocalDate = LocalDate.now().withDayOfMonth(1),
    val categoryFilter: String? = null,
    val query: String = "",
    val loading: Boolean = false,
    val saving: Boolean = false,
    val savedOk: Boolean = false,
    val error: String? = null
) {
    val monthTotalSatang: Long get() = summary.sumOf { it.totalSatang }

    /** The month's overall budget line, or null when no budget has been set. */
    val overallBudget: BudgetLine? get() = budget.find { it.category == null }?.takeIf { it.hasBudget }

    /** Per-category lines, biggest spend first — the design orders its bars that way. */
    val categoryBudgets: List<BudgetLine>
        get() = budget.filter { it.category != null }.sortedByDescending { it.spentSatang }

    /**
     * Average spend per elapsed day. Divides by days *so far* in the current month rather than by
     * the month's length: on the 3rd, dividing by 31 would report an average nobody recognises.
     */
    val dailyAverageSatang: Long
        get() {
            val today = LocalDate.now()
            val days = if (month == today.withDayOfMonth(1)) today.dayOfMonth
            else month.lengthOfMonth()
            return if (days <= 0) 0 else monthTotalSatang / days
        }

    /** The history list after the chip filter and the search box. */
    val visible: List<ChildExpense>
        get() = expenses
            .filter { categoryFilter == null || it.category == categoryFilter }
            .filter { e ->
                query.isBlank() ||
                    e.note.orEmpty().contains(query, ignoreCase = true) ||
                    e.category.contains(query, ignoreCase = true)
            }
}

class ExpenseViewModel(
    private val childRepo: ChildRepository = ChildRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExpenseUiState())
    val uiState: StateFlow<ExpenseUiState> = _uiState.asStateFlow()

    fun load(childId: String? = null) {
        viewModelScope.launch {
            val id = childId ?: ChildSelection.id.value
                ?: childRepo.getSelectedChild().getOrNull()?.id
                ?: run {
                    _uiState.update { it.copy(error = "no_child") }
                    return@launch
                }
            val name = childRepo.getSelectedChild().getOrNull()?.name ?: ""
            _uiState.update { it.copy(loading = true, error = null, childId = id, childName = name) }
            refresh(id)
        }
    }

    private suspend fun refresh(childId: String) {
        val month = _uiState.value.month
        val expenses = childRepo.listExpenses(childId).getOrElse { e ->
            _uiState.update { it.copy(loading = false, error = e.message) }
            return
        }
        val summary = childRepo.expenseSummary(
            childId,
            month.toString(),
            month.plusMonths(1).toString()
        ).getOrDefault(emptyList())
        val warranties = childRepo.expiringWarranties(childId).getOrDefault(emptyList())
        val budget = childRepo.budgetOverview(childId, month.toString()).getOrDefault(emptyList())
        val trend = childRepo.expenseTrend(childId).getOrDefault(emptyList())
        _uiState.update {
            it.copy(
                loading = false,
                expenses = expenses,
                summary = summary,
                expiringWarranties = warranties,
                budget = budget,
                trend = trend
            )
        }
    }

    fun setQuery(v: String) = _uiState.update { it.copy(query = v) }

    /**
     * Saves the month's budget. Amounts arrive as typed text and are parsed to satang here, at the
     * same boundary as an expense, so money never becomes a Double on the way in.
     *
     * A blank or non-positive total clears the overall budget; a category left blank is dropped.
     */
    fun saveBudget(
        totalText: String,
        categoryTexts: Map<String, String> = emptyMap(),
        onDone: () -> Unit = {}
    ) {
        val childId = _uiState.value.childId ?: return
        val month = _uiState.value.month
        val total = parseSatang(totalText) ?: 0
        val categories = categoryTexts
            .mapNotNull { (key, text) -> parseSatang(text)?.let { key to it } }
            .toMap()
        viewModelScope.launch {
            _uiState.update { it.copy(saving = true, error = null) }
            childRepo.setBudget(childId, month.toString(), total, categories)
                .onSuccess {
                    _uiState.update { it.copy(saving = false) }
                    refresh(childId)
                    onDone()
                }
                .onFailure { e -> _uiState.update { it.copy(saving = false, error = e.message) } }
        }
    }

    fun setMonth(first: LocalDate) {
        _uiState.update { it.copy(month = first.withDayOfMonth(1)) }
        val id = _uiState.value.childId ?: return
        viewModelScope.launch { refresh(id) }
    }

    fun setCategoryFilter(category: String?) = _uiState.update {
        it.copy(categoryFilter = if (it.categoryFilter == category) null else category)
    }

    /**
     * Saves one expense. [amountText] is whatever the user typed; it is parsed to satang here so
     * the rounding happens exactly once, at the boundary, instead of drifting through a Double.
     */
    fun addExpense(
        amountText: String,
        category: String,
        spentOn: LocalDate,
        note: String,
        hasWarranty: Boolean = false,
        warrantyExpiresOn: LocalDate? = null,
        hasExpiration: Boolean = false,
        expiresOn: LocalDate? = null,
        alreadyUsed: Boolean = false,
        usageNote: String? = null,
        /** Raw picture bytes; uploaded first so the row never points at a missing object. */
        receipt: ByteArray? = null,
        receiptExt: String = "jpg",
        onDone: () -> Unit = {}
    ) {
        val childId = _uiState.value.childId ?: return
        val satang = parseSatang(amountText)
        if (satang == null || satang <= 0) {
            _uiState.update { it.copy(error = "invalid_amount") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(saving = true, error = null, savedOk = false) }
            // Upload before insert: a row pointing at a failed upload renders as a permanently
            // broken receipt link, and nothing in the app can repair it afterwards.
            val receiptPath = receipt?.let { bytes ->
                childRepo.uploadReceipt(childId, bytes, receiptExt).getOrElse { e ->
                    _uiState.update { it.copy(saving = false, error = e.message) }
                    return@launch
                }
            }
            childRepo.addExpense(
                childId = childId,
                amountSatang = satang,
                category = category,
                spentOn = spentOn.toString(),
                note = note,
                hasWarranty = hasWarranty,
                warrantyExpiresOn = warrantyExpiresOn?.toString(),
                hasExpiration = hasExpiration,
                expiresOn = expiresOn?.toString(),
                alreadyUsed = alreadyUsed,
                usageNote = usageNote,
                receiptUrl = receiptPath
            ).onSuccess {
                _uiState.update { it.copy(saving = false, savedOk = true) }
                refresh(childId)
                onDone()
            }.onFailure { e ->
                _uiState.update { it.copy(saving = false, error = e.message) }
            }
        }
    }

    fun clearSaved() = _uiState.update { it.copy(savedOk = false, error = null) }

    companion object {
        /**
         * "1,250.50" -> 125050. Returns null for anything that is not a positive amount, so the
         * caller refuses rather than silently recording zero.
         */
        fun parseSatang(text: String): Long? {
            val cleaned = text.replace(",", "").replace("฿", "").trim()
            if (cleaned.isEmpty()) return null
            val value = cleaned.toDoubleOrNull() ?: return null
            if (value.isNaN() || value.isInfinite() || value <= 0) return null
            return Math.round(value * 100)
        }

        /** 125050 -> "1,250.50" — display only; never feed this back into arithmetic. */
        fun formatBaht(satang: Long): String = "%,.2f".format(satang / 100.0)
    }
}

/** The six categories from the Stitch "Add Expense" design. */
object ExpenseCategories {
    data class Category(val key: String, val th: String, val en: String)

    val ALL = listOf(
        Category("medical", "การแพทย์", "Medical"),
        Category("nutrition", "โภชนาการ", "Nutrition"),
        Category("education", "การศึกษา", "Education"),
        Category("supplies", "ของใช้", "Supplies"),
        Category("insurance", "ประกัน", "Insurance"),
        Category("other", "อื่นๆ", "Other")
    )

    fun label(key: String, isThai: Boolean): String =
        ALL.find { it.key == key }?.let { if (isThai) it.th else it.en } ?: key

    /** The design's per-category icon, so tile, list row and budget bar all agree. */
    fun icon(key: String): ImageVector = when (key) {
        "medical" -> Icons.Filled.MedicalServices
        "nutrition" -> Icons.Filled.Restaurant
        "education" -> Icons.Filled.School
        "supplies" -> Icons.Filled.Toys
        "insurance" -> Icons.Filled.HealthAndSafety
        else -> Icons.Filled.MoreHoriz
    }
}
