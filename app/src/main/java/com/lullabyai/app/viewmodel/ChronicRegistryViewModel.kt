package com.lullabyai.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lullabyai.app.data.model.ChronicCondition
import com.lullabyai.app.data.repository.ChildRepository
import com.lullabyai.app.data.repository.HealthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChronicRegistryUiState(
    val conditions: List<ChronicCondition> = emptyList(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val lastSavedOk: Boolean = false
)

/**
 * ViewModel for Stitch **Chronic Conditions Registry** (#14) —
 * named conditions + prevention tips against `chronic_conditions` (no AI risk %).
 */
class ChronicRegistryViewModel(
    private val healthRepo: HealthRepository = HealthRepository(),
    private val childRepo: ChildRepository = ChildRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChronicRegistryUiState())
    val uiState: StateFlow<ChronicRegistryUiState> = _uiState.asStateFlow()

    private var childId: String? = null

    fun load(childId: String) {
        this.childId = childId
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, lastSavedOk = false) }
            healthRepo.getChronicConditions(childId)
                .onSuccess { list ->
                    _uiState.update { it.copy(conditions = list, isLoading = false) }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(isLoading = false, error = e.message ?: "Failed to load")
                    }
                }
        }
    }

    fun add(name: String, prevention: String?, onSynced: (() -> Unit)? = null) {
        val id = childId ?: return
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null, lastSavedOk = false) }
            healthRepo.addChronicCondition(id, trimmed, notes = null, prevention = prevention?.trim()?.ifBlank { null })
                .onSuccess {
                    reload(onSynced)
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(isSaving = false, error = e.message ?: "Failed to add")
                    }
                }
        }
    }

    fun update(conditionId: String, name: String, prevention: String?, onSynced: (() -> Unit)? = null) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null, lastSavedOk = false) }
            healthRepo.updateChronicCondition(
                conditionId,
                trimmed,
                notes = null,
                prevention = prevention?.trim()?.ifBlank { null }
            )
                .onSuccess { reload(onSynced) }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(isSaving = false, error = e.message ?: "Failed to update")
                    }
                }
        }
    }

    fun delete(conditionId: String, onSynced: (() -> Unit)? = null) {
        val id = childId ?: return
        viewModelScope.launch {
            if (!childRepo.isChildOwner(id).getOrDefault(false)) {
                _uiState.update { it.copy(error = ChildViewModel.OWNER_ONLY_DELETE_ERROR) }
                return@launch
            }
            _uiState.update { it.copy(isSaving = true, error = null, lastSavedOk = false) }
            healthRepo.deleteChronicCondition(conditionId)
                .onSuccess { reload(onSynced) }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(isSaving = false, error = e.message ?: "Failed to delete")
                    }
                }
        }
    }

    fun clearFeedback() {
        _uiState.update { it.copy(error = null, lastSavedOk = false) }
    }

    private suspend fun reload(onSynced: (() -> Unit)?) {
        val id = childId ?: return
        healthRepo.getChronicConditions(id)
            .onSuccess { list ->
                _uiState.update {
                    it.copy(conditions = list, isLoading = false, isSaving = false, lastSavedOk = true)
                }
                onSynced?.invoke()
            }
            .onFailure { e ->
                _uiState.update {
                    it.copy(isSaving = false, error = e.message ?: "Failed to reload")
                }
            }
    }
}
