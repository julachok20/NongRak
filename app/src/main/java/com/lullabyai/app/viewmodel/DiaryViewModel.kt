package com.lullabyai.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lullabyai.app.data.model.Child
import com.lullabyai.app.data.model.DiaryMemory
import com.lullabyai.app.data.remote.ClaudeClient
import com.lullabyai.app.data.repository.ChildRepository
import com.lullabyai.app.data.repository.DiaryRepository
import com.lullabyai.app.data.repository.HealthRepository
import com.lullabyai.app.data.settings.ChildSelection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar

data class YearRewindStats(
    val year: Int,
    val milestoneCount: Int,
    val sweetMoments: Int,
    val videoCount: Int,
    val growthCm: Double?,
    val topFavorites: List<DiaryMemory>,
    val featured: DiaryMemory?,
    val aiInsight: String?
)

data class DiaryUiState(
    val child: Child? = null,
    val memories: List<DiaryMemory> = emptyList(),
    val signedUrls: Map<String, String> = emptyMap(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val selectedYear: Int = Calendar.getInstance().get(Calendar.YEAR),
    val yearStats: YearRewindStats? = null,
    val yearLoading: Boolean = false,
    val saveSuccess: Boolean = false,
    val isChildOwner: Boolean = false
)

class DiaryViewModel(
    private val diaryRepo: DiaryRepository = DiaryRepository(),
    private val childRepo: ChildRepository = ChildRepository(),
    private val healthRepo: HealthRepository = HealthRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(DiaryUiState())
    val uiState: StateFlow<DiaryUiState> = _uiState.asStateFlow()

    fun load(force: Boolean = false) {
        if (_uiState.value.isLoading && !force) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, saveSuccess = false) }
            ChildSelection.awaitLoaded()
            val child = childRepo.getSelectedChild().getOrNull()
            if (child == null) {
                _uiState.update { it.copy(isLoading = false, child = null, memories = emptyList()) }
                return@launch
            }
            val memories = diaryRepo.getMemories(child.id).getOrElse { e ->
                _uiState.update { it.copy(isLoading = false, error = e.message, child = child) }
                return@launch
            }
            val urls = signAll(memories)
            val isOwner = childRepo.isChildOwner(child.id).getOrDefault(false)
            _uiState.update {
                it.copy(isLoading = false, child = child, memories = memories, signedUrls = urls, error = null, isChildOwner = isOwner)
            }
        }
    }

    fun loadYearRewind(year: Int, isThai: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(yearLoading = true, selectedYear = year, error = null) }
            ChildSelection.awaitLoaded()
            val child = _uiState.value.child ?: childRepo.getSelectedChild().getOrNull()
            if (child == null) {
                _uiState.update { it.copy(yearLoading = false) }
                return@launch
            }
            val yearMemories = diaryRepo.getMemoriesForYear(child.id, year).getOrElse { emptyList() }
            val urls = signAll(yearMemories)
            val growthCm = computeGrowthCm(child.id, year)
            val milestones = yearMemories.filter { it.isMilestone || !it.milestoneTag.isNullOrBlank() }
            val videos = yearMemories.filter { it.mediaType == "video" }
            val favorites = yearMemories.filter { it.isFavorite }.ifEmpty {
                yearMemories.filter { it.isMilestone }.take(3)
            }.take(3)
            val featured = favorites.firstOrNull()
                ?: milestones.firstOrNull()
                ?: yearMemories.firstOrNull()
            val insight = generateYearInsight(child.name, year, yearMemories, growthCm, isThai)
            _uiState.update {
                it.copy(
                    child = child,
                    yearLoading = false,
                    signedUrls = it.signedUrls + urls,
                    yearStats = YearRewindStats(
                        year = year,
                        milestoneCount = milestones.size,
                        sweetMoments = yearMemories.size,
                        videoCount = videos.size,
                        growthCm = growthCm,
                        topFavorites = favorites,
                        featured = featured,
                        aiInsight = insight
                    )
                )
            }
        }
    }

    fun addMemory(
        title: String?,
        description: String?,
        memoryDate: String,
        mediaType: String,
        mediaBytes: ByteArray?,
        fileName: String?,
        mimeType: String?,
        milestoneTag: String?,
        isMilestone: Boolean,
        category: String? = null,
        onDone: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            val child = _uiState.value.child ?: childRepo.getSelectedChild().getOrNull()
            if (child == null) {
                onDone(false); return@launch
            }
            _uiState.update { it.copy(isSaving = true, error = null, saveSuccess = false) }
            val path = if (mediaBytes != null && fileName != null) {
                diaryRepo.uploadMedia(child.id, mediaBytes, fileName, mimeType).getOrElse { e ->
                    _uiState.update { it.copy(isSaving = false, error = e.message) }
                    onDone(false); return@launch
                }
            } else null
            val resolvedType = when {
                mediaType.isNotBlank() -> mediaType
                path == null && !description.isNullOrBlank() -> "note"
                else -> "photo"
            }
            val autoTitle = title?.takeIf { it.isNotBlank() }
                ?: milestoneTag?.let { tagLabel(it, true) }
                ?: description?.take(40)
            val result = diaryRepo.saveMemory(
                id = null,
                childId = child.id,
                title = autoTitle,
                description = description,
                memoryDate = memoryDate,
                mediaType = resolvedType,
                mediaPath = path,
                mimeType = mimeType,
                durationSec = null,
                milestoneTag = milestoneTag,
                isFavorite = false,
                isMilestone = isMilestone || !milestoneTag.isNullOrBlank(),
                category = category
            )
            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(isSaving = false, saveSuccess = true) }
                    load(force = true)
                    onDone(true)
                },
                onFailure = { e ->
                    _uiState.update { it.copy(isSaving = false, error = e.message) }
                    onDone(false)
                }
            )
        }
    }

    fun toggleFavorite(memory: DiaryMemory) {
        viewModelScope.launch {
            val next = !memory.isFavorite
            diaryRepo.toggleFavorite(memory.id, next)
            _uiState.update { st ->
                st.copy(memories = st.memories.map {
                    if (it.id == memory.id) it.copy(isFavorite = next) else it
                })
            }
        }
    }

    fun deleteMemory(memory: DiaryMemory) {
        viewModelScope.launch {
            val childId = _uiState.value.child?.id ?: return@launch
            if (!childRepo.isChildOwner(childId).getOrDefault(false)) return@launch
            diaryRepo.deleteMemory(memory.id, memory.mediaPath)
            _uiState.update { st ->
                st.copy(memories = st.memories.filter { it.id != memory.id })
            }
        }
    }

    fun clearSaveFlag() {
        _uiState.update { it.copy(saveSuccess = false) }
    }

    private suspend fun signAll(memories: List<DiaryMemory>): Map<String, String> {
        val map = mutableMapOf<String, String>()
        memories.forEach { m ->
            val path = m.mediaPath ?: return@forEach
            diaryRepo.signedMediaUrl(path)?.let { map[path] = it }
        }
        return map
    }

    private suspend fun computeGrowthCm(childId: String, year: Int): Double? {
        val records = healthRepo.getGrowthRecords(childId).getOrNull() ?: return null
        val inYear = records.filter { it.recordedAt?.startsWith(year.toString()) == true && it.heightCm != null }
        if (inYear.size < 2) {
            val withH = records.filter { it.heightCm != null }.sortedBy { it.recordedAt }
            if (withH.size < 2) return null
            val first = withH.first().heightCm!!
            val last = withH.last().heightCm!!
            return (last - first).takeIf { it > 0 }
        }
        val sorted = inYear.sortedBy { it.recordedAt }
        val first = sorted.first().heightCm!!
        val last = sorted.last().heightCm!!
        return (last - first).takeIf { it >= 0 }
    }

    private suspend fun generateYearInsight(
        childName: String,
        year: Int,
        memories: List<DiaryMemory>,
        growthCm: Double?,
        isThai: Boolean
    ): String? {
        if (memories.isEmpty()) {
            return if (isThai)
                "ยังไม่มีความทรงจำในปี $year — เริ่มบันทึกช่วงเวลาพิเศษของ$childName ได้เลยนะคะ"
            else
                "No memories yet for $year — start capturing special moments for $childName."
        }
        val bullets = memories.take(12).joinToString("\n") { m ->
            "- ${m.memoryDate}: ${m.title ?: m.milestoneTag ?: "note"} — ${(m.description ?: "").take(80)}"
        }
        val prompt = if (isThai) {
            "สรุปความทรงจำปี $year ของเด็กชื่อ $childName จากรายการต่อไปนี้ เป็นภาษาไทยธรรมชาติ 3-5 ประโยค ไม่ใช้สัญลักษณ์ *#- ไม่ใช้ emoji โทนอบอุ่นสำหรับผู้ปกครอง การเติบโตสูงประมาณ ${growthCm?.let { "%.0f ซม.".format(it) } ?: "ไม่ทราบ"}\n$bullets"
        } else {
            "Summarize $childName's memories for year $year from the list below in 3-5 warm natural sentences for parents. No symbols *#- no emoji. Growth ~${growthCm?.let { "%.0f cm".format(it) } ?: "unknown"}.\n$bullets"
        }
        return runCatching {
            ClaudeClient.generateContent(prompt, useSearch = false, maxTokens = 400)
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    companion object {
        fun tagLabel(tag: String, isThai: Boolean): String = when (tag) {
            "first_step" -> if (isThai) "ก้าวแรก" else "First steps"
            "first_word" -> if (isThai) "คำแรก" else "First words"
            "first_smile" -> if (isThai) "ยิ้มแรก" else "First smile"
            "first_tooth" -> if (isThai) "ฟันซี่แรก" else "First tooth"
            else -> tag
        }

        fun mediaLabel(type: String, isThai: Boolean): String = when (type) {
            "video" -> if (isThai) "วิดีโอ" else "Video"
            "audio" -> if (isThai) "เสียง" else "Audio"
            "note" -> if (isThai) "บันทึก" else "Note"
            else -> if (isThai) "รูปภาพ" else "Photo"
        }
    }
}
