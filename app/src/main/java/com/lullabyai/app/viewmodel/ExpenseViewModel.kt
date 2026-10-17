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
import java.time.temporal.ChronoUnit

/**
 * "Your children's goals add up to more than this total — clear them?" Carries both figures so the
 * dialog can show the comparison, and the pending input so confirming re-sends exactly what was
 * typed rather than whatever the fields hold by then.
 */
data class OverTotalPrompt(
    val attemptedSatang: Long,
    val allocatedSatang: Long,
    val totalText: String,
    val categoryTexts: Map<String, String>
)

data class ExpenseUiState(
    /** The child selected app-wide; the fallback scope and the target for new expenses. */
    val childId: String? = null,
    val childName: String = "",
    /** Every child, used only to label rows in the ทั้งหมด view. Never a switcher. */
    val children: List<com.lullabyai.app.data.model.Child> = emptyList(),
    /**
     * False = this child's own record; true = "ทั้งหมด", the read-only roll-up across children.
     *
     * Deliberately a boolean and not a child id. Children are completely separate: a Cost Record
     * opened on Giga can only ever write to Giga, so there is no second child for the screen to
     * drift onto — which is exactly what an id here made possible.
     */
    val showTotal: Boolean = false,
    val expenses: List<ChildExpense> = emptyList(),
    val summary: List<ExpenseCategoryTotal> = emptyList(),
    /** Warranties still live but running out — surfaced above the list, not buried in it. */
    val expiringWarranties: List<ChildExpense> = emptyList(),
    /** Products expiring soon or already expired — surfaced above the list. */
    val expiringProducts: List<ChildExpense> = emptyList(),
    /** Budget beside spend. The null-category line is the month's overall figure. */
    val budget: List<BudgetLine> = emptyList(),
    val trend: List<ExpenseTrendPoint> = emptyList(),
    /** Which month the summary covers, as its first day. */
    val month: LocalDate = LocalDate.now().withDayOfMonth(1),
    val categoryFilter: String? = null,
    val query: String = "",
    /** Set when a new total goal would strand the children's goals; drives the confirm dialog. */
    val overTotalPrompt: OverTotalPrompt? = null,
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
            // Never swallowed: if this fails the switcher has nothing to offer, and the screen must
            // say so rather than quietly booking everything against the account-wide child.
            val kids = childRepo.getChildren().getOrElse { e ->
                _uiState.update { it.copy(error = e.message ?: "children_unavailable") }
                emptyList()
            }
            _uiState.update {
                it.copy(
                    loading = true, error = null, childId = id, childName = name, children = kids
                )
            }
            refresh()
        }
    }

    /** Switches between this child's own record and the ทั้งหมด roll-up. */
    fun setShowTotal(total: Boolean) {
        _uiState.update { it.copy(showTotal = total) }
        viewModelScope.launch { refresh() }
    }

    private suspend fun refresh() {
        val s = _uiState.value
        val month = s.month
        // null means every child — the repository and RPCs both read it that way.
        val scope = if (s.showTotal) null else s.childId
        val expenses = childRepo.listExpenses(scope).getOrElse { e ->
            _uiState.update { it.copy(loading = false, error = e.message) }
            return
        }
        val summary = childRepo.expenseSummary(
            scope,
            month.toString(),
            month.plusMonths(1).toString()
        ).getOrDefault(emptyList())
        // Warranties stay per-child: the banner names one thing to act on.
        val warranties = (scope ?: s.childId)
            ?.let { childRepo.expiringWarranties(it).getOrDefault(emptyList()) } ?: emptyList()
        val expiring = childRepo.listExpiringProducts(scope, withinDays = 7).getOrDefault(emptyList())
        val budget = childRepo.budgetOverview(scope, month.toString()).getOrDefault(emptyList())
        val trend = childRepo.expenseTrend(scope).getOrDefault(emptyList())
        _uiState.update {
            it.copy(
                loading = false,
                expenses = expenses,
                summary = summary,
                expiringWarranties = warranties,
                expiringProducts = expiring,
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
        /** True only after the user confirmed clearing the children's goals. */
        force: Boolean = false,
        onDone: () -> Unit = {}
    ) {
        val s = _uiState.value
        val month = s.month
        // This child's own goal, or the ทั้งหมด total. Separate figures — one never rewrites the
        // other, and only these two are reachable from this screen.
        val scope = if (s.showTotal) null else s.childId
        if (!s.showTotal && scope == null) return
        val total = parseSatang(totalText) ?: 0
        val categories = categoryTexts
            .mapNotNull { (key, text) -> parseSatang(text)?.let { key to it } }
            .toMap()
        viewModelScope.launch {
            _uiState.update { it.copy(saving = true, error = null) }
            childRepo.setBudget(scope, month.toString(), total, categories, force)
                .onSuccess {
                    _uiState.update { it.copy(saving = false, overTotalPrompt = null) }
                    refresh()
                    onDone()
                }
                .onFailure { e ->
                    // A total below the children's goals is a question, not a dead end: offer to
                    // clear them rather than making the user go and lower each one by hand.
                    val prompt = ExpenseErrors.parseOverTotal(e.message)
                    _uiState.update {
                        if (prompt != null) it.copy(
                            saving = false,
                            error = null,
                            overTotalPrompt = OverTotalPrompt(
                                attemptedSatang = prompt.first,
                                allocatedSatang = prompt.second,
                                totalText = totalText,
                                categoryTexts = categoryTexts
                            )
                        ) else it.copy(saving = false, error = e.message)
                    }
                }
        }
    }

    fun setMonth(first: LocalDate) {
        _uiState.update { it.copy(month = first.withDayOfMonth(1)) }
        val id = _uiState.value.childId ?: return
        viewModelScope.launch { refresh() }
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
        name: String = "",
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
        // Always this child. There is no other child reachable from here, by design.
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
                name = name,
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
                refresh()
                if (hasExpiration && !alreadyUsed && expiresOn != null) {
                    val productName = name.takeIf { it.isNotBlank() }
                        ?: note.takeIf { it.isNotBlank() }
                        ?: "สินค้า"
                    checkAndNotifyExpiringProduct(productName, expiresOn)
                }
                onDone()
            }.onFailure { e ->
                _uiState.update { it.copy(saving = false, error = e.message) }
            }
        }
    }

    private fun checkAndNotifyExpiringProduct(productName: String, expiresOn: LocalDate) {
        val days = ChronoUnit.DAYS.between(LocalDate.now(), expiresOn).toInt()
        if (days in -7..7) {
            viewModelScope.launch {
                val childName = _uiState.value.childName
                val childSuffix = if (childName.isNotBlank()) " ของน้อง$childName" else ""
                val formattedDate = com.lullabyai.app.work.ProductExpirationWorker.formatThaiDate(expiresOn)
                val title: String
                val body: String
                when {
                    days < 0 -> {
                        title = "สินค้าหมดอายุแล้ว!"
                        body = "$productName$childSuffix หมดอายุแล้วเมื่อ $formattedDate กรุณาตรวจสอบและหลีกเลี่ยงการใช้งาน"
                    }
                    days == 0 -> {
                        title = "สินค้าหมดอายุวันนี้!"
                        body = "$productName$childSuffix จะหมดอายุในวันนี้ ($formattedDate) กรุณาตรวจสอบก่อนนำไปใช้งาน"
                    }
                    days == 1 -> {
                        title = "สินค้าจะหมดอายุพรุ่งนี้!"
                        body = "$productName$childSuffix จะหมดอายุในวันพรุ่งนี้ ($formattedDate) รีบนำมาใช้งานก่อนหมดอายุ"
                    }
                    else -> {
                        title = "สินค้าใกล้หมดอายุ (อีก $days วัน)"
                        body = "$productName$childSuffix จะหมดอายุในอีก $days วัน ($formattedDate)"
                    }
                }
                com.lullabyai.app.data.repository.NotificationRepository().notify(
                    category = com.lullabyai.app.data.repository.NotifCategory.WARNING,
                    title = title,
                    body = body
                )
            }
        }
    }

    fun clearSaved() = _uiState.update { it.copy(savedOk = false, error = null) }

    /** Confirms the dialog: re-sends exactly what was typed, this time clearing the child goals. */
    fun confirmOverTotal(onDone: () -> Unit = {}) {
        val p = _uiState.value.overTotalPrompt ?: return
        saveBudget(p.totalText, p.categoryTexts, force = true, onDone = onDone)
    }

    fun dismissOverTotal() = _uiState.update { it.copy(overTotalPrompt = null) }

    /**
     * Ticks or un-ticks "used". The database refuses the change once the row has been frozen for
     * 30 minutes, so a failure here is reported rather than swallowed — the checkbox must not show
     * a state the record does not actually hold.
     */
    fun toggleUsed(expense: ChildExpense) {
        viewModelScope.launch {
            childRepo.setExpenseUsed(expense.id, !expense.alreadyUsed)
                .onSuccess { refresh() }
                .onFailure { e -> _uiState.update { it.copy(error = e.message ?: "locked") } }
        }
    }

    /** Uploads a receipt for an expense saved without one, then points the row at it. */
    fun attachReceipt(expense: ChildExpense, bytes: ByteArray, ext: String = "jpg") {
        val childId = expense.childId ?: _uiState.value.childId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(saving = true, error = null) }
            val path = childRepo.uploadReceipt(childId, bytes, ext).getOrElse { e ->
                _uiState.update { it.copy(saving = false, error = e.message) }
                return@launch
            }
            childRepo.attachReceipt(expense.id, path)
                .onSuccess { _uiState.update { it.copy(saving = false) }; refresh() }
                .onFailure { e -> _uiState.update { it.copy(saving = false, error = e.message) } }
        }
    }

    /** Resolves the private-bucket path into a URL the viewer can actually load. */
    suspend fun receiptUrl(path: String?): String? = childRepo.signedReceiptUrl(path)

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
