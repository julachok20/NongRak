package com.lullabyai.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** One entry in the child diary / special-memories feature (Stitch #10). */
@Serializable
data class DiaryMemory(
    val id: String = "",
    @SerialName("child_id") val childId: String = "",
    val title: String? = null,
    val description: String? = null,
    @SerialName("memory_date") val memoryDate: String = "",
    /** photo | video | audio | note */
    @SerialName("media_type") val mediaType: String = "photo",
    /** Private `diary-media` bucket PATH (not a public URL). */
    @SerialName("media_path") val mediaPath: String? = null,
    @SerialName("mime_type") val mimeType: String? = null,
    @SerialName("duration_sec") val durationSec: Int? = null,
    /** first_step | first_word | first_smile | first_tooth | custom… */
    @SerialName("milestone_tag") val milestoneTag: String? = null,
    @SerialName("is_favorite") val isFavorite: Boolean = false,
    @SerialName("is_milestone") val isMilestone: Boolean = false,
    val category: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)
