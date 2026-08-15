package com.lullabyai.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lullabyai.app.data.model.CareConversation
import com.lullabyai.app.data.repository.ChildRepository
import com.lullabyai.app.data.settings.ChildSelection
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CareCircleUiState(
    val childId: String? = null,
    val conversations: List<CareConversation> = emptyList(),
    /** User ids currently connected to this child's presence channel. */
    val online: Set<String> = emptySet(),
    val query: String = "",
    val loading: Boolean = false,
    val error: String? = null
) {
    /** Matches on the person's name or on what was last said — the design's single search box. */
    val visible: List<CareConversation>
        get() = if (query.isBlank()) conversations else conversations.filter { c ->
            c.peerName.contains(query, true) || c.lastBody?.contains(query, true) == true
        }

    /** The avatar strip only shows people actually connected right now. */
    val onlinePeers: List<CareConversation>
        get() = conversations.filter { it.peerId in online }

    val totalUnread: Int get() = conversations.sumOf { it.unread }
}

class CareCircleViewModel(
    private val childRepo: ChildRepository = ChildRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(CareCircleUiState())
    val uiState: StateFlow<CareCircleUiState> = _uiState.asStateFlow()

    private var watchJob: Job? = null
    private var presenceJob: Job? = null

    fun load(childId: String? = null) {
        viewModelScope.launch {
            val id = childId ?: ChildSelection.id.value
                ?: childRepo.getSelectedChild().getOrNull()?.id
                ?: run {
                    _uiState.update { it.copy(error = "no_child") }
                    return@launch
                }
            _uiState.update { it.copy(loading = true, error = null, childId = id) }
            refresh()
            startWatching()
        }
    }

    fun refresh() {
        val id = _uiState.value.childId ?: return
        viewModelScope.launch {
            val convos = childRepo.careConversations(id).getOrElse { e ->
                _uiState.update { it.copy(loading = false, error = e.message) }
                return@launch
            }
            _uiState.update { it.copy(loading = false, conversations = convos) }
        }
    }

    /**
     * Two subscriptions: message changes keep the previews and unread counts current, presence
     * keeps the green dots honest. Separate channels because they have different lifetimes —
     * presence must drop the moment the socket does, message history must not.
     */
    private fun startWatching() {
        val id = _uiState.value.childId ?: return
        if (watchJob?.isActive != true) {
            // Peer is irrelevant here: the hub cares about every conversation for this child,
            // and the subscription filters on child_id alone.
            watchJob = viewModelScope.launch {
                runCatching { childRepo.watchCareMessages(id, "hub").collect { refresh() } }
            }
        }
        if (presenceJob?.isActive != true) {
            presenceJob = viewModelScope.launch {
                runCatching {
                    childRepo.watchPresence(id).collect { ids ->
                        _uiState.update { it.copy(online = ids) }
                    }
                }
            }
        }
    }

    fun setQuery(v: String) = _uiState.update { it.copy(query = v) }

    override fun onCleared() {
        watchJob?.cancel()
        presenceJob?.cancel()
        super.onCleared()
    }
}
