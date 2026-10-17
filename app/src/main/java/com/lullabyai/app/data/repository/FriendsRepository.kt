package com.lullabyai.app.data.repository

import com.lullabyai.app.data.remote.StorageHelper
import com.lullabyai.app.data.remote.supabase
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/** Where a friendship stands, seen from the person looking at the profile. */
enum class FriendState {
    /** No row at all. The button reads เพิ่มเพื่อน. */
    NONE,

    /** You asked, they have not answered. The button reads ยกเลิกคำขอ. */
    REQUEST_SENT,

    /** They asked you. The card offers ยอมรับเพื่อน / ปฏิเสธ. */
    REQUEST_RECEIVED,

    /** Accepted, both ways. The button reads เพื่อนกันแล้ว. */
    FRIENDS,

    /** You blocked them. Nothing else is offered until the block is lifted. */
    BLOCKED
}

@Serializable
data class FriendshipDto(
    val id: String = "",
    @SerialName("requester_id") val requesterId: String,
    @SerialName("addressee_id") val addresseeId: String,
    val status: String = "pending",
    val message: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("responded_at") val respondedAt: String? = null
)

@Serializable
data class BlockDto(
    @SerialName("blocker_id") val blockerId: String,
    @SerialName("blocked_id") val blockedId: String
)

/** A profile row as the community shows it - only the fields a stranger is allowed to see. */
@Serializable
data class CommunityProfileDto(
    val id: String,
    val username: String? = null,
    @SerialName("full_name") val fullName: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val bio: String? = null,
    val city: String? = null,
    val discoverable: Boolean = true
)

/** One row in คำขอเป็นเพื่อนใหม่, or in a friends list. */
data class FriendSummary(
    val userId: String,
    val displayName: String,
    val avatarUrl: String?,
    val bio: String?,
    val city: String?,
    /** The note attached to the request, when there was one. */
    val message: String? = null,
    val friendshipId: String? = null
)

/**
 * Friends, requests and blocks.
 *
 * Every rule that matters lives in Postgres (20260927150000_community_friends.sql): only the
 * addressee may accept, only the requester may ask, a block beats a request, and one pair has one
 * row whichever direction it was asked. This class is the thin client over that - it does not get
 * to decide any of it, and it should not try.
 */
class FriendsRepository {

    private fun me(): String? = runCatching { supabase.auth.currentUserOrNull()?.id }.getOrNull()

    /**
     * Asks to be someone's friend.
     *
     * [note] is what mockup 55 shows under the name - why this person is asking. Screened like
     * any other text a stranger can put in front of a parent.
     */
    suspend fun request(targetUserId: String, note: String? = null): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val myId = me() ?: error("friend_request_requires_sign_in")
                require(myId != targetUserId) { "friend_request_self" }

                val trimmed = note?.trim()?.take(200)?.takeIf { it.isNotEmpty() }
                if (trimmed != null &&
                    !com.lullabyai.app.data.remote.SparkChatClient.moderate(trimmed).allowed
                ) {
                    error("friend_request_moderation_blocked")
                }

                supabase.from("community_friendships").insert(
                    buildJsonObject {
                        put("requester_id", JsonPrimitive(myId))
                        put("addressee_id", JsonPrimitive(targetUserId))
                        put("status", JsonPrimitive("pending"))
                        if (trimmed != null) put("message", JsonPrimitive(trimmed))
                    }
                )
                Unit
            }
        }

    /**
     * Answers a request. Only the addressee can do this - the database enforces it, so a failure
     * here means somebody tried to accept on the wrong account, not that the network blipped.
     */
    suspend fun respond(friendshipId: String, accept: Boolean): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                supabase.from("community_friendships").update(
                    buildJsonObject {
                        put("status", JsonPrimitive(if (accept) "accepted" else "declined"))
                    }
                ) { filter { eq("id", friendshipId) } }
                Unit
            }
        }

    /** Cancels a request you sent, or unfriends someone. Either side may do this. */
    suspend fun remove(friendshipId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            supabase.from("community_friendships").delete { filter { eq("id", friendshipId) } }
            Unit
        }
    }

    /**
     * Blocks someone.
     *
     * A trigger deletes any friendship in either direction, and further requests from them are
     * refused. They are not told - being told is how blocking turns into an argument.
     */
    suspend fun block(targetUserId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val myId = me() ?: error("block_requires_sign_in")
            require(myId != targetUserId) { "block_self" }
            supabase.from("community_blocks").insert(BlockDto(blockerId = myId, blockedId = targetUserId))
            Unit
        }
    }

    suspend fun unblock(targetUserId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val myId = me() ?: error("block_requires_sign_in")
            supabase.from("community_blocks").delete {
                filter {
                    eq("blocker_id", myId)
                    eq("blocked_id", targetUserId)
                }
            }
            Unit
        }
    }

    /** Pending requests waiting on ME to answer - the คำขอเป็นเพื่อนใหม่ list. */
    suspend fun incomingRequests(): Result<List<FriendSummary>> = withContext(Dispatchers.IO) {
        runCatching {
            val myId = me() ?: return@runCatching emptyList()
            val rows = supabase.from("community_friendships")
                .select {
                    filter {
                        eq("addressee_id", myId)
                        eq("status", "pending")
                    }
                    order("created_at", Order.DESCENDING)
                }
                .decodeList<FriendshipDto>()

            val profiles = profilesById(rows.map { it.requesterId })
            rows.mapNotNull { row ->
                profiles[row.requesterId]?.copy(message = row.message, friendshipId = row.id)
            }
        }
    }

    /** Everyone whose friendship with me is accepted, whichever of us asked. */
    suspend fun friends(): Result<List<FriendSummary>> = withContext(Dispatchers.IO) {
        runCatching {
            val myId = me() ?: return@runCatching emptyList()
            val rows = supabase.from("community_friendships")
                .select { filter { eq("status", "accepted") } }
                .decodeList<FriendshipDto>()
                // RLS already limits this to rows I am part of; filtering again keeps the intent
                // readable here rather than depending on a policy three files away.
                .filter { it.requesterId == myId || it.addresseeId == myId }

            val otherIds = rows.map { if (it.requesterId == myId) it.addresseeId else it.requesterId }
            val profiles = profilesById(otherIds)
            rows.mapNotNull { row ->
                val other = if (row.requesterId == myId) row.addresseeId else row.requesterId
                profiles[other]?.copy(friendshipId = row.id)
            }
        }
    }

    /** How I stand with one person, and the row id needed to act on it. */
    suspend fun stateWith(targetUserId: String): Result<Pair<FriendState, String?>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val myId = me() ?: return@runCatching FriendState.NONE to null

                val blocked = supabase.from("community_blocks")
                    .select {
                        filter {
                            eq("blocker_id", myId)
                            eq("blocked_id", targetUserId)
                        }
                    }
                    .decodeList<BlockDto>()
                    .isNotEmpty()
                if (blocked) return@runCatching FriendState.BLOCKED to null

                // RLS returns only rows I am part of, so this is at most a handful of rows.
                val row = supabase.from("community_friendships")
                    .select()
                    .decodeList<FriendshipDto>()
                    .firstOrNull { row ->
                        row.status != "declined" &&
                            ((row.requesterId == myId && row.addresseeId == targetUserId) ||
                                (row.addresseeId == myId && row.requesterId == targetUserId))
                    } ?: return@runCatching FriendState.NONE to null

                val state = when {
                    row.status == "accepted" -> FriendState.FRIENDS
                    row.requesterId == myId -> FriendState.REQUEST_SENT
                    else -> FriendState.REQUEST_RECEIVED
                }
                state to row.id
            }
        }

    /**
     * Parents who might be worth knowing.
     *
     * Only profiles with `discoverable` set are offered, and anyone already connected, already
     * asked, or blocked is excluded by the caller passing their ids in [excludeUserIds]. This
     * function never reads another family's child rows - the age band the mockup matches on is
     * the caller's own, and nothing about a stranger's child is read here.
     */
    suspend fun suggestions(
        excludeUserIds: Set<String>,
        limit: Int = 10
    ): Result<List<FriendSummary>> = withContext(Dispatchers.IO) {
        runCatching {
            val myId = me() ?: return@runCatching emptyList()
            supabase.from("profiles")
                .select {
                    filter { eq("discoverable", true) }
                    limit(limit.toLong() + excludeUserIds.size + 1)
                }
                .decodeList<CommunityProfileDto>()
                .filter { it.id != myId && it.id !in excludeUserIds }
                .take(limit)
                .map { it.toSummary() }
        }
    }

    suspend fun profile(userId: String): Result<FriendSummary?> = withContext(Dispatchers.IO) {
        runCatching { profilesById(listOf(userId))[userId] }
    }

    /**
     * How many friends [userId] has.
     *
     * Through an RPC because RLS - correctly - shows a client only the friendships it is part of,
     * so the number cannot be counted here. The function returns a count and never the list.
     */
    suspend fun friendCount(userId: String): Int = withContext(Dispatchers.IO) {
        runCatching {
            supabase.postgrest.rpc(
                "community_friend_count",
                buildJsonObject { put("target", JsonPrimitive(userId)) }
            ).decodeAs<Int>()
        }.getOrDefault(0)
    }

    /**
     * Friends you and [userId] have in common.
     *
     * The RPC returns only people the caller is already friends with, so nothing is learned about
     * strangers - only that someone you already know also knows this parent.
     */
    suspend fun mutualFriends(userId: String): List<FriendSummary> = withContext(Dispatchers.IO) {
        runCatching {
            val ids = supabase.postgrest.rpc(
                "community_mutual_friends",
                buildJsonObject { put("target", JsonPrimitive(userId)) }
            ).decodeList<MutualFriendRow>().map { it.userId }
            profilesById(ids).values.toList()
        }.getOrDefault(emptyList())
    }

    @Serializable
    private data class MutualFriendRow(@SerialName("user_id") val userId: String)

    /**
     * Everyone already connected to me in any way, for excluding from suggestions.
     *
     * Includes declined requests and blocks: suggesting someone who already said no - or who I
     * said no to - is the most unwelcome thing a "people you may know" list can do.
     */
    suspend fun connectedUserIds(): Set<String> = withContext(Dispatchers.IO) {
        runCatching {
            val myId = me() ?: return@runCatching emptySet()
            val fromFriendships = supabase.from("community_friendships")
                .select()
                .decodeList<FriendshipDto>()
                .flatMap { listOf(it.requesterId, it.addresseeId) }

            val blocked = supabase.from("community_blocks")
                .select { filter { eq("blocker_id", myId) } }
                .decodeList<BlockDto>()
                .map { it.blockedId }

            (fromFriendships + blocked).toSet() - myId
        }.getOrDefault(emptySet())
    }

    private suspend fun profilesById(ids: List<String>): Map<String, FriendSummary> {
        if (ids.isEmpty()) return emptyMap()
        return runCatching {
            supabase.from("profiles")
                .select { filter { isIn("id", ids.distinct()) } }
                .decodeList<CommunityProfileDto>()
                .associate { it.id to it.toSummary() }
        }.getOrDefault(emptyMap())
    }

    /**
     * avatar_url is a path in a PRIVATE bucket, not a URL. Handing it straight to Coil shows
     * nothing - the same bug that hid every child photo on the nutrition pages.
     */
    private suspend fun CommunityProfileDto.toSummary(): FriendSummary = FriendSummary(
        userId = id,
        displayName = username?.trim()?.takeIf { it.isNotEmpty() }
            ?: fullName?.trim()?.takeIf { it.isNotEmpty() }
            ?: "คุณแม่น้องรัก",
        avatarUrl = runCatching { StorageHelper.signedAvatarUrl(avatarUrl) }.getOrNull(),
        bio = bio,
        city = city
    )
}
