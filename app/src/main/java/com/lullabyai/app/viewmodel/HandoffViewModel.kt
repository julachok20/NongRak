package com.lullabyai.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lullabyai.app.data.handoff.*
import com.lullabyai.app.data.model.ChildAccessMember
import com.lullabyai.app.data.offline.OfflineCache
import com.lullabyai.app.data.repository.ChildRepository
import com.lullabyai.app.data.repository.HandoffRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class HandoffState(
    val childId: String = "", val childName: String = "", val userId: String = "",
    val people: List<ChildAccessMember> = emptyList(), val handoffs: List<CareHandoff> = emptyList(),
    val draft: HandoffDraft = HandoffDraft(), val busy: Boolean = false, val error: String? = null,
    val context:HandoffContext?=null, val contexts:Map<String,HandoffContext> = emptyMap(),
    val returnNotes:Map<String,String> = emptyMap()
)

class HandoffViewModel : ViewModel() {
    private val repo = HandoffRepository()
    private val children = ChildRepository()
    private val _state = MutableStateFlow(HandoffState())
    val state = _state.asStateFlow()
    private val draftMutex = Mutex()
    private fun key(s: HandoffState) = "handoff_draft_${s.userId}_${s.childId}"

    suspend fun load(childId: String) {
        if (childId.isBlank()) return
        val uid = repo.userId
        if (_state.value.childId != childId || _state.value.userId != uid) {
            _state.value = HandoffState(childId = childId, userId = uid)
            try {
                val draft = OfflineCache.get<HandoffDraft>(key(_state.value)) ?: HandoffDraft()
                val notes = OfflineCache.get<Map<String,String>>(key(_state.value)+"_return") ?: emptyMap()
                _state.update { it.copy(draft = draft,returnNotes=notes) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _state.update { it.copy(error = "Draft: ${e.message}") } }
        }
        refresh()
    }

    suspend fun refresh() {
        val before = _state.value
        if (before.childId.isBlank() || before.busy) return
        try {
            val child = children.getChildById(before.childId).getOrThrow()
            val people = children.listAccessMembers(before.childId).getOrThrow()
            val list = repo.list(before.childId)
            val context = repo.context(before.childId)
            val expanded = before.contexts.keys.filter { id -> list.any { it.id==id } }
                .associateWith { repo.context(before.childId,it) }
            if (_state.value.childId == before.childId) _state.update {
                it.copy(childName = child.name, people = people, handoffs = list, context=context, contexts=expanded, error = null)
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            // Do not retain server records after a failed permission refresh.
            _state.update { it.copy(handoffs = emptyList(), people = emptyList(),context=null,contexts=emptyMap(), error = e.message ?: "Request failed") }
        }
    }

    fun draft(value: HandoffDraft) {
        _state.update { it.copy(draft = value) }
        viewModelScope.launch {
            draftMutex.withLock {
                try { val current = _state.value; OfflineCache.put(key(current), current.draft) }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { _state.update { it.copy(error = "Draft was not saved: ${e.message}") } }
            }
        }
    }

    fun send() = mutate {
        val current = _state.value
        repo.send(current.childId, current.draft)
        draftMutex.withLock {
            val empty = HandoffDraft()
            OfflineCache.put(key(current), empty)
            _state.update { it.copy(draft = empty) }
        }
    }
    fun returnNote(id:String,note:String) {
        _state.update { it.copy(returnNotes=it.returnNotes+(id to note)) }
        viewModelScope.launch { draftMutex.withLock {
            try { val current=_state.value; OfflineCache.put(key(current)+"_return",current.returnNotes) }
            catch(e:CancellationException) { throw e }
            catch(e:Exception) { _state.update { it.copy(error="Return draft was not saved: ${e.message}") } }
        } }
    }
    fun loadSummary(h:CareHandoff) { viewModelScope.launch {
        try { val context=repo.context(h.childId,h.id); _state.update { it.copy(contexts=it.contexts+(h.id to context)) } }
        catch(e:CancellationException) { throw e }
        catch(e:Exception) { _state.update { it.copy(contexts=it.contexts-h.id,error=e.message) } }
    } }
    fun act(h: CareHandoff, action: String, note: String = "", index: Int? = null, done: Boolean? = null) =
        mutate { repo.act(h.id, action, note, index, done) }

    private fun mutate(block: suspend () -> Unit) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try { block(); _state.update { it.copy(busy = false) }; refresh() }
            catch (e: CancellationException) { _state.update { it.copy(busy = false) }; throw e }
            catch (e: Exception) { _state.update { it.copy(busy = false, error = e.message ?: "Request failed") } }
        }
    }
}
