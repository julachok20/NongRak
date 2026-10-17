package com.lullabyai.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * What a row in community_posts is. One feed, three shapes - see the migration
 * 20260927120000_community_post_kinds_and_polls.sql.
 */
enum class PostKind {
    /** An ordinary share. Title optional, body up to 2,000 characters. */
    POST,

    /** กระทู้ถาม-ตอบ: a titled question for the volunteer doctors. Title 80, body 1,000, 2 tags. */
    THREAD,

    /** โพลสำรวจ: a titled question with 3-6 options and its own settings. */
    POLL;

    /** The value stored in the database. Lowercase there, upper here, matched in one place only. */
    val wire: String get() = name.lowercase()

    companion object {
        /** Unknown values read as POST rather than throwing: a newer server must not crash an older app. */
        fun fromWire(value: String?): PostKind =
            entries.firstOrNull { it.wire == value?.trim()?.lowercase() } ?: POST
    }
}

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
    /**
     * Written by NongRak AI, not by a person. The card must carry the "AI ไม่ใช่แพทย์" notice
     * wherever this is true - a parent has to be able to tell an AI reply from a doctor's.
     */
    val isAi: Boolean = false,
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
    /** This device's own reaction, or null when they have not reacted. */
    val myReaction: Reaction? = null,
    /** How many of each reaction the post has, for the little emoji cluster on the card. */
    val reactionCounts: Map<Reaction, Int> = emptyMap(),
    val commentsCount: Int = 0,
    val comments: List<CommunityComment> = emptyList(),
    val createdAtMillis: Long = System.currentTimeMillis(),

    // --- Added with the three post kinds ---

    val kind: PostKind = PostKind.POST,
    /** Required for THREAD and POLL, optional for POST. */
    val title: String? = null,
    /** Up to 10 pictures/clips. [imageUrl] stays the cover, so older cards still render. */
    val mediaUrls: List<String> = emptyList(),
    val commentsEnabled: Boolean = true,
    /**
     * ตั้งกระทู้แบบไม่ระบุตัวตน. The server nulls [userId] for these before the row ever reaches a
     * client, so a post being anonymous is all the app can know about who wrote it - including
     * when the reader is the author. Do not try to match [userId] to decide "is this mine".
     */
    val isAnonymous: Boolean = false,
    /** กระทู้ด่วน. Sorts to the top of the doctor queue. Never means the app handles emergencies. */
    val isUrgent: Boolean = false,
    /** The author asked NongRak AI for a first pass under the thread. */
    val aiAssist: Boolean = false,
    /** Present only when [kind] is POLL. */
    val poll: CommunityPoll? = null,

    // --- Added with groups (20261003120000_community_groups.sql) ---

    /**
     * The group this post was written in, or null when it belongs to the open feed.
     *
     * The open feed filters on this, so a post written inside a group never appears beside the
     * open posts - whether or not that group is public.
     */
    val groupId: String? = null,
    /** When a moderator pinned it. Null for everything else. Pinned posts sort first in a group. */
    val pinnedAtMillis: Long? = null
)

/** โพลสำรวจ settings, as drawn in composer mockup 73. */
@Serializable
data class CommunityPoll(
    val postId: String,
    val description: String? = null,
    val closesAtMillis: Long,
    val allowMultiple: Boolean = false,
    /** false: nobody sees the tally until the poll closes, so early votes cannot steer later ones. */
    val showResultsAfterVote: Boolean = true,
    val allowUserOptions: Boolean = false,
    val options: List<CommunityPollOption> = emptyList(),
    /** Option ids this device's user has picked. Empty means they have not voted. */
    val myVotes: Set<String> = emptySet()
) {
    val isClosed: Boolean get() = System.currentTimeMillis() >= closesAtMillis
    val totalVotes: Int get() = options.sumOf { it.voteCount }

    /**
     * Whether the tally may be drawn yet. Presentation only - the vote rows are readable by anyone
     * who can query, so this hides the number from the screen, not from a determined reader. Never
     * word the UI as though the result were secret.
     */
    fun resultsVisible(): Boolean = isClosed || (showResultsAfterVote && myVotes.isNotEmpty())

    /** Whole percent of the total, 0 when nobody has voted. Rounded for display only. */
    fun sharePercent(option: CommunityPollOption): Int {
        val total = totalVotes
        return if (total <= 0) 0 else Math.round(option.voteCount * 100f / total)
    }
}

@Serializable
data class CommunityPollOption(
    val id: String,
    val label: String,
    val position: Int,
    /** Set when a voter added this option rather than the author. Null for the author's own. */
    val createdBy: String? = null,
    val voteCount: Int = 0
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
    @SerialName("removed_reason") val removedReason: String? = null,
    val kind: String = "post",
    val title: String? = null,
    @SerialName("media_urls") val mediaUrls: List<String> = emptyList(),
    @SerialName("comments_enabled") val commentsEnabled: Boolean = true,
    @SerialName("is_anonymous") val isAnonymous: Boolean = false,
    @SerialName("is_urgent") val isUrgent: Boolean = false,
    @SerialName("ai_assist") val aiAssist: Boolean = false,
    /** The group this post belongs to, or null when it is in the open feed. */
    @SerialName("group_id") val groupId: String? = null,
    /** Set by a moderator of that group. Pinned posts sort above everything else on a group page. */
    @SerialName("pinned_at") val pinnedAt: String? = null
)

/**
 * Rows of community_polls. closes_at is an ISO-8601 instant from Postgres, e.g.
 * "2026-09-29T14:32:00Z" - parsed with [parseIsoToEpochMillis] like every other timestamp here.
 */
@Serializable
data class CommunityPollDto(
    @SerialName("post_id") val postId: String,
    val description: String? = null,
    @SerialName("closes_at") val closesAt: String,
    @SerialName("allow_multiple") val allowMultiple: Boolean = false,
    @SerialName("show_results_after_vote") val showResultsAfterVote: Boolean = true,
    @SerialName("allow_user_options") val allowUserOptions: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class CommunityPollOptionDto(
    val id: String = UUID.randomUUID().toString(),
    @SerialName("post_id") val postId: String,
    val label: String,
    val position: Int,
    @SerialName("created_by") val createdBy: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class CommunityPollVoteDto(
    @SerialName("post_id") val postId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("option_id") val optionId: String,
    @SerialName("created_at") val createdAt: String? = null
)

/** Why a parent flagged something. The wire values are checked by the database, so keep them exact. */
enum class ReportReason(val wire: String, val labelTh: String) {
    SPAM("spam", "โฆษณา / สแปม"),
    WRONG_INFO("wrong_info", "ข้อมูลผิด อาจเป็นอันตราย"),
    HARASSMENT("harassment", "คุกคาม / ไม่สุภาพ"),
    PRIVACY("privacy", "เปิดเผยข้อมูลส่วนตัวของเด็กหรือผู้อื่น"),
    UNSAFE_CHILD("unsafe_child", "เนื้อหาที่ไม่ปลอดภัยต่อเด็ก"),
    OTHER("other", "อื่นๆ")
}

@Serializable
data class CommunityReportDto(
    @SerialName("reporter_id") val reporterId: String,
    @SerialName("target_type") val targetType: String,
    @SerialName("target_id") val targetId: String,
    val reason: String,
    val note: String? = null
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
    @SerialName("is_ai") val isAi: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null
)

/**
 * How a parent reacted to a post.
 *
 * One per person per post - picking รักเลย REPLACES ถูกใจ rather than adding to it, which is what
 * the (post_id, user_id) primary key on community_post_likes already enforces.
 *
 * Deliberately no angry face. This is a community of parents asking about their children at 2am;
 * a one-tap way to express anger at one of them is not a feature worth having.
 */
enum class Reaction(val wire: String, val emoji: String, val labelTh: String, val labelEn: String) {
    LIKE("like", "👍", "ถูกใจ", "Like"),
    LOVE("love", "❤️", "รักเลย", "Love"),
    LAUGH("laugh", "😂", "ฮาา", "Haha"),
    SAD("sad", "😢", "เสียใจด้วย", "Sad");

    fun label(isThai: Boolean): String = if (isThai) labelTh else labelEn

    companion object {
        /** Unknown values read as LIKE: a newer server must not crash an older app. */
        fun fromWire(value: String?): Reaction =
            entries.firstOrNull { it.wire == value?.trim()?.lowercase() } ?: LIKE
    }
}

@Serializable
data class CommunityPostLikeDto(
    @SerialName("post_id") val postId: String,
    @SerialName("user_id") val userId: String,
    val reaction: String = "like",
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
