package com.lullabyai.app.data.repository

import com.lullabyai.app.data.remote.supabase
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

/**
 * The six headings in the group directory (mockup 70).
 *
 * The wire values match the check constraint in 20261003120000_community_groups.sql. A category
 * the server knows and this app does not reads as [HEALTH] rather than crashing an older build.
 */
enum class GroupCategory(val wire: String, val labelTh: String, val labelEn: String) {
    HEALTH("health", "สุขภาพและการแพทย์", "Health and medical"),
    NUTRITION("nutrition", "โภชนาการและ BLW", "Nutrition and BLW"),
    SLEEP("sleep", "การนอนและกิจวัตร", "Sleep and routine"),
    MILESTONES("milestones", "ของเล่นและพัฒนาการ", "Play and milestones"),
    WELLNESS("wellness", "ดูแลตัวเองและจิตใจ", "Looking after yourself"),
    GEAR("gear", "ป้ายยาของใช้ลูก", "Baby gear");

    companion object {
        fun fromWire(value: String?): GroupCategory =
            entries.firstOrNull { it.wire == value?.trim()?.lowercase() } ?: HEALTH
    }
}

/** What the caller may do in a group. Null everywhere means not a member. */
enum class GroupRole {
    MEMBER,
    MODERATOR;

    val wire: String get() = name.lowercase()

    companion object {
        fun fromWire(value: String?): GroupRole =
            entries.firstOrNull { it.wire == value?.trim()?.lowercase() } ?: MEMBER
    }
}

@Serializable
data class CommunityGroupDto(
    val id: String,
    val name: String,
    val description: String? = null,
    val category: String,
    @SerialName("cover_url") val coverUrl: String? = null,
    val rules: String? = null,
    @SerialName("is_public") val isPublic: Boolean = true,
    @SerialName("created_by") val createdBy: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class GroupMemberDto(
    @SerialName("group_id") val groupId: String,
    @SerialName("user_id") val userId: String,
    val role: String = "member",
    @SerialName("notifications_on") val notificationsOn: Boolean = true
)

@Serializable
private data class GroupStatsRow(
    @SerialName("group_id") val groupId: String,
    @SerialName("member_count") val memberCount: Int = 0,
    @SerialName("posts_today") val postsToday: Int = 0,
    @SerialName("has_doctor") val hasDoctor: Boolean = false
)

/**
 * A group as a card or a page header shows it.
 *
 * [memberCount], [postsToday] and [hasDoctor] come from community_group_stats and stay at 0/false
 * when that call fails. A card showing no number is honest; one showing an invented number is not.
 */
data class CommunityGroup(
    val id: String,
    val name: String,
    val description: String?,
    val category: GroupCategory,
    val coverUrl: String?,
    val rules: String?,
    val isPublic: Boolean,
    val memberCount: Int = 0,
    /** Posts in the last 24 hours. NOT an online count - no presence data exists in this app. */
    val postsToday: Int = 0,
    /**
     * มีกุมารแพทย์ร่วมดูแล (mockup 66). True only when a moderator of this group actually holds the
     * doctor role. Derived server-side, never a flag somebody can switch on.
     */
    val hasDoctor: Boolean = false,
    /** Null when the caller has not joined. */
    val myRole: GroupRole? = null,
    val notificationsOn: Boolean = true
) {
    val isMember: Boolean get() = myRole != null
    val canModerate: Boolean get() = myRole == GroupRole.MODERATOR
}

/**
 * One line in กระทู้ฮอตข้ามกลุ่ม (mockup 71) - the busiest threads from every group at once.
 *
 * Deliberately not a full [com.lullabyai.app.data.model.CommunityPost]: this list shows a title,
 * which group it came from and how busy it is, and tapping it opens the real post. Loading whole
 * posts with their comments and polls to render one line each would be several times the work.
 */
data class GroupHotThread(
    val postId: String,
    val groupId: String,
    val groupName: String,
    val title: String,
    val commentsCount: Int,
    val likesCount: Int,
    val createdAtMillis: Long
)

/** The two lists on the directory screen (mockups 70-71). */
data class GroupDirectory(
    val myGroups: List<CommunityGroup> = emptyList(),
    val recommended: List<CommunityGroup> = emptyList()
)

/**
 * Groups and group membership.
 *
 * Everything that matters is enforced in Postgres (20261003120000_community_groups.sql):
 *
 *   * a client cannot join as a moderator - the insert policy forces role = member
 *   * the creator is made a moderator by trigger, so a group always has one
 *   * a private group and its posts are invisible to non-members
 *   * only a moderator may pin, and anything else raises pin_requires_moderator
 *
 * This class is the thin client over those rules. It does not re-decide any of them, and a failure
 * here means a rule fired - not that the network blipped.
 */
class GroupsRepository {

    private fun me(): String? = runCatching { supabase.auth.currentUserOrNull()?.id }.getOrNull()

    /**
     * My groups, and a handful worth joining.
     *
     * Recommended is simply the busiest public groups I am not in. Nothing about my children is
     * read to build it: a recommendation drawn from a child health record would be sorting parents
     * by their baby medical history, which is not something this app does.
     */
    suspend fun directory(recommendedLimit: Int = 6): Result<GroupDirectory> =
        withContext(Dispatchers.IO) {
            runCatching {
                val memberships = myMemberships()
                val all = selectGroups()
                val stats = statsFor(all.map { it.id })

                val groups = all.map { it.toGroup(memberships[it.id], stats[it.id]) }
                GroupDirectory(
                    myGroups = groups.filter { it.isMember }.sortedByDescending { it.postsToday },
                    recommended = groups
                        .filter { !it.isMember && it.isPublic }
                        .sortedWith(
                            compareByDescending<CommunityGroup> { it.postsToday }
                                .thenByDescending { it.memberCount }
                        )
                        .take(recommendedLimit)
                )
            }
        }

    /** One category heading, opened from the directory. */
    suspend fun byCategory(category: GroupCategory): Result<List<CommunityGroup>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val memberships = myMemberships()
                val rows = selectGroups(category)
                val stats = statsFor(rows.map { it.id })
                rows.map { it.toGroup(memberships[it.id], stats[it.id]) }
                    .sortedByDescending { it.memberCount }
            }
        }

    /**
     * One group, for its page header.
     *
     * Null when it does not exist, or when it is private and the caller is not a member - RLS
     * returns no row for either, and the screen cannot tell those apart. It should not try to.
     */
    suspend fun group(groupId: String): Result<CommunityGroup?> = withContext(Dispatchers.IO) {
        runCatching {
            val row = supabase.from("community_groups")
                .select { filter { eq("id", groupId) } }
                .decodeList<CommunityGroupDto>()
                .firstOrNull() ?: return@runCatching null
            row.toGroup(myMemberships()[groupId], statsFor(listOf(groupId))[groupId])
        }
    }

    /**
     * Joins a group.
     *
     * Always as a plain member. The role is not a parameter here because it is not a parameter in
     * the database either - the insert policy refuses anything else.
     */
    suspend fun join(groupId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val myId = me() ?: error("join_group_requires_sign_in")
            supabase.from("community_group_members").insert(
                GroupMemberDto(groupId = groupId, userId = myId, role = GroupRole.MEMBER.wire)
            )
            Unit
        }
    }

    suspend fun leave(groupId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val myId = me() ?: error("leave_group_requires_sign_in")
            supabase.from("community_group_members").delete {
                filter {
                    eq("group_id", groupId)
                    eq("user_id", myId)
                }
            }
            Unit
        }
    }

    /** The bell on the group header. */
    suspend fun setNotifications(groupId: String, on: Boolean): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val myId = me() ?: error("group_notifications_requires_sign_in")
                supabase.from("community_group_members").update(
                    buildJsonObject { put("notifications_on", JsonPrimitive(on)) }
                ) {
                    filter {
                        eq("group_id", groupId)
                        eq("user_id", myId)
                    }
                }
                Unit
            }
        }

    /**
     * ปักหมุด / เลิกปักหมุด (mockup 67).
     *
     * Only a moderator of that group may do this; the trigger raises pin_requires_moderator for
     * anybody else. A failure here is that rule firing, so it is surfaced, never swallowed.
     *
     * The timestamp sent here is a placeholder. community_post_pin_guard overwrites pinned_at with
     * the server clock and pinned_by with auth.uid(), so a phone with a wrong clock cannot pin a
     * post into the future - which is what the group page sorts on - and no client can name
     * somebody else as the person who pinned it.
     */
    suspend fun setPinned(postId: String, pinned: Boolean): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val myId = me() ?: error("pin_requires_sign_in")
                supabase.from("community_posts").update(
                    buildJsonObject {
                        if (pinned) {
                            put("pinned_at", JsonPrimitive(java.time.Instant.now().toString()))
                            put("pinned_by", JsonPrimitive(myId))
                        } else {
                            put("pinned_at", JsonNull)
                            put("pinned_by", JsonNull)
                        }
                    }
                ) { filter { eq("id", postId) } }
                Unit
            }
        }

    /**
     * Creates a group. The creator becomes its moderator by trigger, not by anything written here.
     */
    suspend fun create(
        name: String,
        category: GroupCategory,
        description: String? = null,
        rules: String? = null,
        isPublic: Boolean = true
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val myId = me() ?: error("create_group_requires_sign_in")
            val trimmedName = name.trim()
            require(trimmedName.length in 2..80) { "group_name_length" }

            // Screened like any other text a stranger can put in front of a parent. A group name
            // sits on the directory screen, the most public surface the community has.
            listOfNotNull(trimmedName, description?.trim(), rules?.trim())
                .filter { it.isNotEmpty() }
                .forEach { text ->
                    if (!com.lullabyai.app.data.remote.SparkChatClient.moderate(text).allowed) {
                        error("create_group_moderation_blocked")
                    }
                }

            supabase.from("community_groups").insert(
                buildJsonObject {
                    put("name", JsonPrimitive(trimmedName))
                    put("category", JsonPrimitive(category.wire))
                    put("is_public", JsonPrimitive(isPublic))
                    put("created_by", JsonPrimitive(myId))
                    description?.trim()?.takeIf { it.isNotEmpty() }
                        ?.let { put("description", JsonPrimitive(it.take(500))) }
                    rules?.trim()?.takeIf { it.isNotEmpty() }
                        ?.let { put("rules", JsonPrimitive(it.take(1000))) }
                }
            ) { select() }
                .decodeList<CommunityGroupDto>()
                .firstOrNull()?.id ?: error("create_group_no_row_returned")
        }
    }

    /**
     * กระทู้ฮอตข้ามกลุ่ม - the busiest threads across every group the caller can see.
     *
     * Ranked the same way the hub ranks Hot, so one list does not call something hot that the other
     * would not: engagement over the age of the post, so a busy thread from this morning beats a
     * busier one from last month.
     *
     * RLS does the filtering, so a private group the caller is not in contributes nothing - this
     * cannot be used to see what is being discussed behind a closed door.
     */
    suspend fun hotThreads(limit: Int = 5): Result<List<GroupHotThread>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val rows = supabase.from("community_posts_public")
                    .select {
                        filter {
                            filterNot(
                                "group_id",
                                io.github.jan.supabase.postgrest.query.filter.FilterOperator.IS,
                                null
                            )
                            exact("removed_at", null)
                        }
                        order("created_at", Order.DESCENDING)
                        limit(60)
                    }
                    .decodeList<com.lullabyai.app.data.model.CommunityPostDto>()
                if (rows.isEmpty()) return@runCatching emptyList()

                val names = supabase.from("community_groups")
                    .select { filter { isIn("id", rows.mapNotNull { it.groupId }.distinct()) } }
                    .decodeList<CommunityGroupDto>()
                    .associate { it.id to it.name }

                val now = System.currentTimeMillis()
                rows.mapNotNull { dto ->
                    val groupId = dto.groupId ?: return@mapNotNull null
                    val created = com.lullabyai.app.data.model.parseIsoToEpochMillis(dto.createdAt)
                    GroupHotThread(
                        postId = dto.id,
                        groupId = groupId,
                        groupName = names[groupId] ?: return@mapNotNull null,
                        title = dto.title?.takeIf { it.isNotBlank() } ?: dto.content.take(80),
                        commentsCount = dto.commentsCount,
                        likesCount = dto.likesCount,
                        createdAtMillis = created
                    )
                }
                    .sortedByDescending { thread ->
                        val score = thread.likesCount + thread.commentsCount * 2
                        val ageHours = ((now - thread.createdAtMillis) / 3_600_000.0).coerceAtLeast(0.0)
                        score / Math.pow(ageHours + 2.0, 1.5)
                    }
                    .take(limit)
            }
        }

    // --- internals ----------------------------------------------------------------------------

    private suspend fun selectGroups(category: GroupCategory? = null): List<CommunityGroupDto> =
        supabase.from("community_groups")
            .select {
                if (category != null) filter { eq("category", category.wire) }
                order("created_at", Order.DESCENDING)
                limit(100)
            }
            .decodeList<CommunityGroupDto>()

    /** group id to my row in it. Empty when signed out - RLS returns nothing then anyway. */
    private suspend fun myMemberships(): Map<String, GroupMemberDto> = runCatching {
        val myId = me() ?: return@runCatching emptyMap()
        supabase.from("community_group_members")
            .select { filter { eq("user_id", myId) } }
            .decodeList<GroupMemberDto>()
            .associateBy { it.groupId }
    }.getOrDefault(emptyMap())

    /**
     * Member and activity counts for a page of groups, in one call.
     *
     * Through an RPC because RLS - correctly - shows a client only the membership rows of groups it
     * belongs to, so a directory card for a group I have not joined cannot count its members here.
     * The function returns counts and never the list of who is in a group.
     */
    private suspend fun statsFor(groupIds: List<String>): Map<String, GroupStatsRow> {
        if (groupIds.isEmpty()) return emptyMap()
        return runCatching {
            supabase.postgrest.rpc(
                "community_group_stats",
                buildJsonObject {
                    put(
                        "group_ids",
                        buildJsonArray { groupIds.distinct().forEach { add(JsonPrimitive(it)) } }
                    )
                }
            ).decodeList<GroupStatsRow>().associateBy { it.groupId }
        }.getOrDefault(emptyMap())
    }

    private fun CommunityGroupDto.toGroup(
        membership: GroupMemberDto?,
        stats: GroupStatsRow?
    ) = CommunityGroup(
        id = id,
        name = name,
        description = description,
        category = GroupCategory.fromWire(category),
        coverUrl = coverUrl,
        rules = rules,
        isPublic = isPublic,
        memberCount = stats?.memberCount ?: 0,
        postsToday = stats?.postsToday ?: 0,
        hasDoctor = stats?.hasDoctor ?: false,
        myRole = membership?.let { GroupRole.fromWire(it.role) },
        notificationsOn = membership?.notificationsOn ?: true
    )

    companion object {
        /** Database keys, turned into something a parent can read. */
        fun groupWriteError(key: String?, isThai: Boolean): String? {
            if (key == null) return null
            val raw = key.lowercase()
            return when {
                "pin_requires_moderator" in raw ->
                    if (isThai) "ปักหมุดได้เฉพาะผู้ดูแลกลุ่มค่ะ"
                    else "Only a group moderator can pin a post."
                "create_group_moderation_blocked" in raw ->
                    if (isThai) "ข้อความยังไม่ผ่านการตรวจค่ะ ลองแก้ถ้อยคำอีกครั้งนะคะ"
                    else "That wording did not pass screening. Please try again."
                "group_name_length" in raw ->
                    if (isThai) "ชื่อกลุ่มต้องมี 2-80 ตัวอักษรค่ะ"
                    else "A group name needs 2 to 80 characters."
                "requires_sign_in" in raw ->
                    if (isThai) "เข้าสู่ระบบก่อนนะคะ" else "Please sign in first."
                "duplicate key" in raw ->
                    if (isThai) "อยู่ในกลุ่มนี้แล้วค่ะ" else "You are already in this group."
                "row-level security" in raw || "violates row-level" in raw ->
                    if (isThai) "กลุ่มนี้เป็นกลุ่มส่วนตัวค่ะ" else "This group is private."
                else ->
                    if (isThai) "บันทึกไม่สำเร็จ ลองอีกครั้งนะคะ" else "Could not save. Please try again."
            }
        }
    }
}
