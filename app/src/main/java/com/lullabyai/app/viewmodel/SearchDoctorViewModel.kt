package com.lullabyai.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lullabyai.app.data.model.DoctorProfile
import com.lullabyai.app.data.model.Profile
import com.lullabyai.app.data.repository.DoctorRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class SearchDoctorUiState(
    val query: String = "",
    val results: List<Pair<Profile, DoctorProfile>> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null
)

/** Backs SearchDoctorScreen — thin wrapper over DoctorRepository.searchDoctors. */
class SearchDoctorViewModel : ViewModel() {
    private val repo = DoctorRepository()
    private val _ui = MutableStateFlow(SearchDoctorUiState())
    val ui: StateFlow<SearchDoctorUiState> = _ui

    init { search("") }

    fun search(query: String) {
        _ui.value = _ui.value.copy(query = query, loading = true)
        viewModelScope.launch {
            repo.searchBookableDoctors(query.ifBlank { null })
                .onSuccess { _ui.value = _ui.value.copy(results = it, loading = false, error = null) }
                .onFailure { _ui.value = _ui.value.copy(loading = false, error = it.message) }
        }
    }

    fun refresh() = search(_ui.value.query)
}
