package com.lullabyai.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class CommunityComment(
    val id: String = UUID.randomUUID().toString(),
    val postId: String,
    val userId: String? = null,
    val authorName: String,
    val authorRole: String = "คุณแม่",
    val authorAvatarUrl: String? = null,
    val content: String,
    val timeAgo: String = "เมื่อสักครู่",
    val likesCount: Int = 0,
    val isLiked: Boolean = false,
    val createdAtMillis: Long = System.currentTimeMillis()
)

@Serializable
data class CommunityPost(
    val id: String = UUID.randomUUID().toString(),
    val userId: String? = null,
    val authorName: String,
    val authorRole: String = "คุณแม่",
    val authorAvatarUrl: String? = null,
    val timeAgo: String = "เมื่อสักครู่",
    val content: String,
    val tags: List<String> = emptyList(),
    val imageUrl: String? = null,
    /** Attached clip; [imageUrl] then holds its first frame, so the card has something to show. */
    val videoUrl: String? = null,
    val likesCount: Int = 0,
    val isLiked: Boolean = false,
    val commentsCount: Int = 0,
    val comments: List<CommunityComment> = emptyList(),
    val createdAtMillis: Long = System.currentTimeMillis()
)

@Serializable
data class CommunityPostDto(
    val id: String = UUID.randomUUID().toString(),
    @SerialName("user_id") val userId: String? = null,
    @SerialName("author_name") val authorName: String,
    @SerialName("author_role") val authorRole: String = "คุณแม่",
    @SerialName("author_avatar_url") val authorAvatarUrl: String? = null,
    val content: String,
    val tags: List<String> = emptyList(),
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("video_url") val videoUrl: String? = null,
    @SerialName("likes_count") val likesCount: Int = 0,
    @SerialName("comments_count") val commentsCount: Int = 0,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    /** Set from the Supabase dashboard when the owner removes a post; only its author can still read it. */
    @SerialName("removed_at") val removedAt: String? = null,
    @SerialName("removed_reason") val removedReason: String? = null
)

/** One of the signed-in person's own posts that was removed, for the warning banner on Community. */
@Serializable
data class RemovedPost(
    val id: String,
    val reason: String?,
    val preview: String
)

@Serializable
data class CommunityCommentDto(
    val id: String = UUID.randomUUID().toString(),
    @SerialName("post_id") val postId: String,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("author_name") val authorName: String,
    @SerialName("author_role") val authorRole: String = "คุณแม่",
    @SerialName("author_avatar_url") val authorAvatarUrl: String? = null,
    val content: String,
    @SerialName("likes_count") val likesCount: Int = 0,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class CommunityPostLikeDto(
    @SerialName("post_id") val postId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class CommunityCommentLikeDto(
    @SerialName("comment_id") val commentId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("created_at") val createdAt: String? = null
)

fun formatRelativeTime(epochMillis: Long, isThai: Boolean): String {
    val now = System.currentTimeMillis()
    val diff = (now - epochMillis).coerceAtLeast(0)
    val minutes = diff / (60 * 1000)
    val hours = diff / (60 * 60 * 1000)
    val days = diff / (24 * 60 * 60 * 1000)

    return when {
        minutes < 1 -> if (isThai) "เมื่อสักครู่" else "Just now"
        minutes < 60 -> if (isThai) "$minutes นาทีที่แล้ว" else "$minutes min ago"
        hours < 24 -> if (isThai) "$hours ชั่วโมงที่แล้ว" else "$hours hr ago"
        days < 7 -> if (isThai) "$days วันที่แล้ว" else "$days days ago"
        else -> {
            val sdf = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
            sdf.format(java.util.Date(epochMillis))
        }
    }
}

fun parseIsoToEpochMillis(isoString: String?): Long {
    if (isoString.isNullOrBlank()) return System.currentTimeMillis()
    return runCatching {
        java.time.Instant.parse(isoString).toEpochMilli()
    }.getOrElse {
        runCatching {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
            sdf.parse(isoString)?.time ?: System.currentTimeMillis()
        }.getOrDefault(System.currentTimeMillis())
    }
}

@Serializable
data class AiArticleTip(
    val id: String,
    val title: String,
    val summary: String,
    val category: String,
    val imageUrl: String = "",
    val readTimeMinutes: Int = 3
)

@Serializable
data class ProfessionalAdviceBlog(
    val id: String,
    val title: String,
    val subtitle: String,
    val summary: String,
    val category: String, // โภชนาการ, การนอน, พัฒนาการ, สุขภาพ, ความปลอดภัย
    val doctorName: String,
    val doctorTitle: String,
    val doctorHospital: String = "โรงพยาบาลเด็ก / Lullaby Pediatric Network",
    val readTimeMinutes: Int = 4,
    val publishedDate: String = "2026-08-20",
    val fullContent: String,
    val keyTakeaways: List<String> = emptyList(),
    val faqs: List<Pair<String, String>> = emptyList(),
    val likesCount: Int = 128,
    val isLiked: Boolean = false
)
