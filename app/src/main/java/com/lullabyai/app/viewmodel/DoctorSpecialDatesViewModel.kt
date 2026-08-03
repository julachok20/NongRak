package com.lullabyai.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lullabyai.app.data.remote.supabase
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * One-off calendar exception for a doctor: either a full closure (holiday / leave) or altered
 * opening hours for that single date. Overrides the recurring weekly `doctor_availability`.
 *
 * Lives here rather than in Models.kt because Models.kt is owned by another concurrent workstream.
 * `specialDate` is "yyyy-MM-dd"; times are "HH:mm" going out, Postgres returns "HH:mm:ss".
 */
@Serializable
data class DoctorSpecialDate(
    val id: String = "",
    @SerialName("doctor_id") val doctorId: String = "",
    @SerialName("special_date") val specialDate: String = "",
    @SerialName("is_closed") val isClosed: Boolean = true,
    @SerialName("start_time") val startTime: String? = null,
    @SerialName("end_time") val endTime: String? = null,
    val note: String? = null
) {
    /** Postgres hands back "HH:mm:ss"; the UI only ever shows/edits "HH:mm". */
    fun startHhMm(): String? = startTime?.take(5)
    fun endHhMm(): String? = endTime?.take(5)
}

data class DoctorSpecialDatesUiState(
    val dates: List<DoctorSpecialDate> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

/**
 * Backs "Special Dates & Hours Management". Table `doctor_special_dates` is keyed
 * (doctor_id, special_date), so saving the same date twice updates rather than duplicates.
 */
class DoctorSpecialDatesViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(DoctorSpecialDatesUiState())
    val uiState: StateFlow<DoctorSpecialDatesUiState> = _uiState

    private fun doctorId() = supabase.auth.currentUserOrNull()?.id

    fun load() {
        val doctorId = doctorId() ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            runCatching {
                supabase.from("doctor_special_dates").select {
                    filter { eq("doctor_id", doctorId) }
                    order("special_date", Order.ASCENDING)
                }.decodeList<DoctorSpecialDate>()
            }.onSuccess {
                _uiState.value = DoctorSpecialDatesUiState(it, false, null)
            }.onFailure {
                _uiState.value = _uiState.value.copy(isLoading = false, error = it.message)
            }
        }
    }

    /**
     * Insert-or-update one date. A closed day stores null times so a doctor can't leave stale
     * hours behind on a day they've since marked closed.
     */
    fun save(date: String, isClosed: Boolean, startTime: String?, endTime: String?, note: String?) {
        val doctorId = doctorId() ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val payload = buildJsonObject {
                put("doctor_id", JsonPrimitive(doctorId))
                put("special_date", JsonPrimitive(date))
                put("is_closed", JsonPrimitive(isClosed))
                put("start_time", if (isClosed || startTime.isNullOrBlank()) JsonNull else JsonPrimitive(startTime))
                put("end_time", if (isClosed || endTime.isNullOrBlank()) JsonNull else JsonPrimitive(endTime))
                put("note", if (note.isNullOrBlank()) JsonNull else JsonPrimitive(note))
            }
            runCatching {
                supabase.from("doctor_special_dates").upsert(payload) {
                    onConflict = "doctor_id,special_date"
                }
            }.onFailure {
                _uiState.value = _uiState.value.copy(isLoading = false, error = it.message)
                return@launch
            }
            load()
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            runCatching {
                supabase.from("doctor_special_dates").delete { filter { eq("id", id) } }
            }.onFailure {
                _uiState.value = _uiState.value.copy(error = it.message)
                return@launch
            }
            load()
        }
    }
}
