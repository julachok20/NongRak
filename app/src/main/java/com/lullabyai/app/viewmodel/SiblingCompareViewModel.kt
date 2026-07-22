package com.lullabyai.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lullabyai.app.data.model.GrowthRecord
import com.lullabyai.app.data.repository.HealthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class SiblingCompareUiState(
    val recordsA: List<GrowthRecord> = emptyList(),
    val recordsB: List<GrowthRecord> = emptyList(),
    val loading: Boolean = false
)

/** Loads growth records for two children directly by id — bypasses the single "active child"
 *  scoping in ChildViewModel since sibling comparison needs both at once. */
class SiblingCompareViewModel : ViewModel() {
    private val repo = HealthRepository()
    private val _ui = MutableStateFlow(SiblingCompareUiState())
    val ui: StateFlow<SiblingCompareUiState> = _ui

    private var loadedKey: String? = null

    fun load(childIdA: String, childIdB: String) {
        val key = "$childIdA|$childIdB"
        if (key == loadedKey) return
        loadedKey = key
        viewModelScope.launch {
            _ui.value = _ui.value.copy(loading = true)
            val a = repo.getGrowthRecords(childIdA).getOrDefault(emptyList())
            val b = repo.getGrowthRecords(childIdB).getOrDefault(emptyList())
            _ui.value = SiblingCompareUiState(a, b, false)
        }
    }
}
