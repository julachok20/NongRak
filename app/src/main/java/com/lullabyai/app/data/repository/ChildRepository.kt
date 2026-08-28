package com.lullabyai.app.data.repository

import com.lullabyai.app.data.model.Child
import com.lullabyai.app.data.model.ChildAccessActivity
import com.lullabyai.app.data.model.ChildActivityEntry
import com.lullabyai.app.data.model.CareLog
import com.lullabyai.app.data.model.CareConversation
import com.lullabyai.app.data.model.CareMessage
import com.lullabyai.app.data.model.ActivityCategoryCount
import com.lullabyai.app.data.model.BudgetLine
import com.lullabyai.app.data.model.CachedRecommendations
import com.lullabyai.app.data.model.ChildActivity
import com.lullabyai.app.data.model.ChildExpense
import com.lullabyai.app.data.model.ExpenseCategoryTotal
import com.lullabyai.app.data.model.ExpenseTrendPoint
import com.lullabyai.app.data.model.InviteCandidate
import com.lullabyai.app.data.model.ChildAccessInvite
import com.lullabyai.app.data.model.ChildAccessMember
import com.lullabyai.app.data.model.ChildRole
import com.lullabyai.app.data.model.ChildSyncMatch
import com.lullabyai.app.data.model.ChildSyncRequest
import com.lullabyai.app.data.model.DevelopmentMilestone
import com.lullabyai.app.data.model.MedicalHistory
import com.lullabyai.app.data.remote.StorageHelper
import com.lullabyai.app.data.remote.ensureRealtimeConnected
import com.lullabyai.app.data.remote.supabase
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.presenceDataFlow
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.time.Duration.Companion.seconds

private val medicalDocUrlTtl = 3600.seconds

class ChildRepository {

    suspend fun createChild(child: Child): Result<Child> = runCatching {
        supabase.from("children").insert(child) {
            select()
        }.decodeSingle<Child>()
    }

    /** All children this account can see — own + synced. Scoping is left entirely to RLS
     *  (`user_id = auth.uid() OR is_child_member(id)`) rather than an extra client-side owner-only
     *  filter, which would silently hide children the user only has synced member access to.
     *
     *  Retries once after a short delay on failure: a cold-start call can race the auth session
     *  finishing its token restore, which makes this query transient-fail (empty/unauthorized)
     *  right after login even though the user genuinely has children — this was surfacing as a
     *  false "please add a child" prompt that reappeared on every screen visit. */
    suspend fun getChildren(): Result<List<Child>> {
        val first = runCatching { supabase.from("children").select().decodeList<Child>() }
        if (first.isSuccess) {
            first.getOrNull()?.let { com.lullabyai.app.data.offline.OfflineCache.put("children", it) }
            return first
        }
        kotlinx.coroutines.delay(600)
        val second = runCatching { supabase.from("children").select().decodeList<Child>() }
        if (second.isSuccess) {
            second.getOrNull()?.let { com.lullabyai.app.data.offline.OfflineCache.put("children", it) }
            return second
        }
        // Both attempts failed (likely offline) — fall back to the last successfully loaded list
        // instead of leaving the dashboard blank/erroring. Stale but present beats empty.
        val cached = com.lullabyai.app.data.offline.OfflineCache.get<List<Child>>("children")
        return if (cached != null) Result.success(cached) else second
    }

    suspend fun getFirstChild(): Result<Child?> = runCatching {
        val list = supabase.from("children").select {
            limit(1)
        }.decodeList<Child>()
        list.firstOrNull()
    }

    /** Fetch a single child by id — used by the Doctor role screens (patient directory/info),
     *  which look up children by id rather than "my children" (RLS covers doctor access). */
    suspend fun getChildById(childId: String): Result<Child> = runCatching {
        supabase.from("children").select { filter { eq("id", childId) } }.decodeSingle<Child>()
    }

    suspend fun updateChild(childId: String, updates: Map<String, Any?>): Result<Unit> = runCatching {
        val json = buildJsonObject {
            updates.forEach { (k, v) ->
                when (v) {
                    null -> put(k, JsonNull)
                    is String -> put(k, JsonPrimitive(v))
                    is Number -> put(k, JsonPrimitive(v))
                    is Boolean -> put(k, JsonPrimitive(v))
                    is JsonElement -> put(k, v)
                    else -> put(k, JsonPrimitive(v.toString()))
                }
            }
        }
        supabase.from("children").update(json) {
            filter { eq("id", childId) }
        }
    }

    suspend fun uploadChildAvatar(childId: String, bytes: ByteArray, ext: String = "jpg"): Result<String> = runCatching {
        val uid = supabase.auth.currentUserOrNull()?.id ?: error("Not signed in")
        val path = "$uid/child_${childId}_${System.currentTimeMillis()}.$ext"
        supabase.storage.from(StorageHelper.AVATAR_BUCKET).upload(path, bytes) {
            upsert = true
        }
        supabase.from("children").update(buildJsonObject {
            put("avatar_url", JsonPrimitive(path))
        }) {
            filter { eq("id", childId) }
        }
        path
    }

    /**
     * The child the user is currently viewing (multi-child). Resolves the persisted selection from
     * [ChildSelection]; falls back to the first child if nothing is selected or the selected child
     * no longer exists, and writes that fallback back so the selection stays consistent.
     */
    suspend fun getSelectedChild(): Result<Child?> = runCatching {
        // Wait (briefly) for the persisted selection to load so we don't overwrite it on cold start.
        kotlinx.coroutines.withTimeoutOrNull(1500) { com.lullabyai.app.data.settings.ChildSelection.awaitLoaded() }
        val children = getChildren().getOrDefault(emptyList())
        val selectedId = com.lullabyai.app.data.settings.ChildSelection.current()
        val chosen = children.find { it.id == selectedId } ?: children.firstOrNull()
        if (chosen != null && chosen.id != selectedId) {
            com.lullabyai.app.data.settings.ChildSelection.set(chosen.id)
        }
        chosen
    }

    // ---- Sync child (multi-parent access) ----------------------------------------------------

    /** Finds another parent's child by exact name + birthdate match (deliberately narrow — see migration). */
    suspend fun searchChildForSync(name: String, birthdate: String): Result<List<ChildSyncMatch>> = runCatching {
        supabase.postgrest.rpc(
            "search_child_for_sync",
            buildJsonObject {
                put("p_name", JsonPrimitive(name))
                put("p_birthdate", JsonPrimitive(birthdate))
            }
        ).decodeList<ChildSyncMatch>()
    }

    /** Owner-only: generates a short-lived invite code for another parent to redeem. */
    suspend fun generateInviteCode(childId: String): Result<String> = runCatching {
        val raw = supabase.postgrest.rpc(
            "generate_child_invite_code",
            buildJsonObject { put("p_child_id", JsonPrimitive(childId)) }
        ).data
        raw.trim().trim('"')
    }

    /** Requests sync access to a child found via search — creates a pending request, notifies the owner. */
    suspend fun requestSyncBySearch(childId: String, role: String? = null): Result<Unit> = runCatching {
        supabase.postgrest.rpc(
            "request_child_sync",
            buildJsonObject {
                put("p_child_id", JsonPrimitive(childId))
                put("p_method", JsonPrimitive("search"))
                // Always send p_role: two request_child_sync overloads exist (3-arg legacy and 4-arg
                // with p_role), and omitting it leaves PostgREST to pick between them.
                put("p_role", JsonPrimitive(role?.ifBlank { null } ?: DEFAULT_REQUEST_ROLE))
            }
        )
        Unit
    }

    /**
     * Redeems a scanned QR payload. Identical to [requestSyncByCode] except the request is tagged
     * `method = 'qr'` so the owner's popup and the access-activity feed can tell a scan apart from a
     * typed code. A scan NEVER grants access: `request_child_sync` only inserts a `pending` row
     * (single-use — it stamps `used_at` on the invite — and rejects codes past `expires_at`).
     */
    suspend fun requestSyncByQr(payloadOrCode: String, role: String? = null): Result<Unit> {
        val trimmed = payloadOrCode.trim()
        if (trimmed.isBlank()) return Result.failure(IllegalArgumentException("empty_code"))
        val codes = buildList {
            add(trimmed)
            val short = extractInviteCode(trimmed)
            if (short.isNotBlank() && !short.equals(trimmed, ignoreCase = true)) add(short)
        }.distinct()
        var last: Result<Unit> = Result.failure(IllegalArgumentException("empty_code"))
        for (code in codes) {
            last = requestSyncByCode(code, role, method = "qr")
            if (last.isSuccess) return last
        }
        for (code in codes) {
            last = requestSyncByCode(code, role, method = "code")
            if (last.isSuccess) return last
        }
        return last
    }

    /** Requests sync access by redeeming an invite code — creates a pending request, notifies the owner. */
    suspend fun requestSyncByCode(code: String, role: String? = null, method: String = "code"): Result<Unit> = runCatching {
        val trimmed = code.trim()
        require(trimmed.isNotBlank()) { "empty_code" }
        // QR scans may carry the full nongrak:// URL or server payload — pass through for RPC resolution.
        val forRpc = if (method == "qr") trimmed else extractInviteCode(trimmed)
        require(forRpc.isNotBlank()) { "empty_code" }
        supabase.postgrest.rpc(
            "request_child_sync",
            buildJsonObject {
                put("p_child_id", JsonNull)
                put("p_method", JsonPrimitive(method))
                put("p_code", JsonPrimitive(forRpc))
                put("p_role", JsonPrimitive(role?.ifBlank { null } ?: DEFAULT_REQUEST_ROLE))
            }
        )
        Unit
    }

    /** Owner/co-owner: generate invite code (7d) or QR token (10 min) with role + optional invitee. */
    suspend fun generateAccessInvite(
        childId: String,
        role: String = "co_owner",
        inviteType: String = "code",
        inviteeEmail: String? = null,
        inviteeName: String? = null
    ): Result<ChildAccessInvite> = runCatching {
        val raw = supabase.postgrest.rpc(
            "generate_child_access_invite",
            buildJsonObject {
                put("p_child_id", JsonPrimitive(childId))
                put("p_role", JsonPrimitive(role))
                put("p_invite_type", JsonPrimitive(inviteType))
                put("p_invitee_email", inviteeEmail?.let { JsonPrimitive(it) } ?: JsonNull)
                put("p_invitee_name", inviteeName?.let { JsonPrimitive(it) } ?: JsonNull)
            }
        ).data
        Json { ignoreUnknownKeys = true }.decodeFromString<ChildAccessInvite>(raw)
    }

    suspend fun listAccessMembers(childId: String): Result<List<ChildAccessMember>> = runCatching {
        supabase.postgrest.rpc(
            "list_child_access_members",
            buildJsonObject { put("p_child_id", JsonPrimitive(childId)) }
        ).decodeList<ChildAccessMember>()
    }

    suspend fun listAccessActivity(childId: String, limit: Int = 50): Result<List<ChildAccessActivity>> = runCatching {
        supabase.postgrest.rpc(
            "list_child_access_activity",
            buildJsonObject {
                put("p_child_id", JsonPrimitive(childId))
                put("p_limit", JsonPrimitive(limit))
            }
        ).decodeList<ChildAccessActivity>()
    }

    /**
     * Sets the "helps care for this child" tick. Kept off [updateMemberPermissions] on purpose:
     * that call is the permission grid, and this flag survives even when the grid is locked —
     * owners and co-owners already hold every permission, but may or may not be the daily carer.
     * The RPC rejects anyone who is not an owner/co-owner and writes its own audit row.
     */
    suspend fun setMemberCaregiver(
        childId: String,
        userId: String,
        isCaregiver: Boolean
    ): Result<Unit> = runCatching {
        supabase.postgrest.rpc(
            "set_child_member_caregiver",
            buildJsonObject {
                put("p_child_id", JsonPrimitive(childId))
                put("p_user_id", JsonPrimitive(userId))
                put("p_is_caregiver", JsonPrimitive(isCaregiver))
            }
        )
        Unit
    }

    /**
     * Every record written for this child — growth, vaccine, milk, food, waste, sleep, symptom,
     * medicine — merged newest-first, each carrying who wrote it.
     */
    suspend fun listActivityFeed(childId: String, limit: Int = 80): Result<List<ChildActivityEntry>> =
        runCatching {
            supabase.postgrest.rpc(
                "list_child_activity_feed",
                buildJsonObject {
                    put("p_child_id", JsonPrimitive(childId))
                    put("p_limit", JsonPrimitive(limit))
                }
            ).decodeList<ChildActivityEntry>()
        }

    /** Records one cost. [amountSatang] is THB × 100 — the caller parses; this never sees a float. */
    suspend fun addExpense(
        childId: String,
        amountSatang: Long,
        category: String,
        spentOn: String,
        name: String? = null,
        note: String? = null,
        hasWarranty: Boolean = false,
        warrantyExpiresOn: String? = null,
        hasExpiration: Boolean = false,
        expiresOn: String? = null,
        alreadyUsed: Boolean = false,
        usageNote: String? = null,
        receiptUrl: String? = null
    ): Result<Unit> = runCatching {
        supabase.postgrest.from("child_expenses").insert(
            buildJsonObject {
                put("child_id", JsonPrimitive(childId))
                put("amount_satang", JsonPrimitive(amountSatang))
                put("category", JsonPrimitive(category))
                put("spent_on", JsonPrimitive(spentOn))
                put("name", name?.ifBlank { null }?.let { JsonPrimitive(it) } ?: JsonNull)
                put("note", note?.ifBlank { null }?.let { JsonPrimitive(it) } ?: JsonNull)
                put("has_warranty", JsonPrimitive(hasWarranty))
                put("has_expiration", JsonPrimitive(hasExpiration))
                put("already_used", JsonPrimitive(alreadyUsed))
                // A switch that is off must not leave its field behind, or the table's own
                // "date needs its switch" checks reject the row.
                put(
                    "expires_on",
                    expiresOn?.ifBlank { null }?.takeIf { hasExpiration }
                        ?.let { JsonPrimitive(it) } ?: JsonNull
                )
                put(
                    "usage_note",
                    usageNote?.ifBlank { null }?.takeIf { alreadyUsed }
                        ?.let { JsonPrimitive(it) } ?: JsonNull
                )
                put(
                    "warranty_expires_on",
                    warrantyExpiresOn?.ifBlank { null }?.takeIf { hasWarranty }
                        ?.let { JsonPrimitive(it) } ?: JsonNull
                )
                put("receipt_url", receiptUrl?.ifBlank { null }?.let { JsonPrimitive(it) } ?: JsonNull)
            }
        )
        Unit
    }

    /** Recorded costs, newest spend first. */
    /**
     * A null [childId] is the design's "ทั้งหมด" — every child at once. Dropping the filter is safe
     * rather than broad: the table's read policy is already `is_child_member(child_id)`, so the
     * unfiltered select returns exactly the children this user could have asked for one by one.
     */
    suspend fun listExpenses(childId: String?, limit: Int = 200): Result<List<ChildExpense>> =
        runCatching {
            supabase.postgrest.from("child_expenses").select {
                if (childId != null) filter { eq("child_id", childId) }
                order("spent_on", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                limit(limit.toLong())
            }.decodeList<ChildExpense>()
        }

    /**
     * Ticks or un-ticks "used". The 30-minute lock lives in a database trigger, so a request that
     * arrives too late fails here rather than being quietly dropped by the UI.
     */
    suspend fun setExpenseUsed(expenseId: String, used: Boolean): Result<Unit> = runCatching {
        supabase.postgrest.from("child_expenses").update(
            buildJsonObject { put("already_used", JsonPrimitive(used)) }
        ) { filter { eq("id", expenseId) } }
        Unit
    }

    /** Attaches a receipt to an expense that was saved without one. */
    suspend fun attachReceipt(expenseId: String, path: String): Result<Unit> = runCatching {
        supabase.postgrest.from("child_expenses").update(
            buildJsonObject { put("receipt_url", JsonPrimitive(path)) }
        ) { filter { eq("id", expenseId) } }
        Unit
    }

    /** Category totals over `[from, to)`. Dates are plain `yyyy-MM-dd`. */
    suspend fun expenseSummary(
        childId: String?,
        from: String,
        to: String
    ): Result<List<ExpenseCategoryTotal>> = runCatching {
        supabase.postgrest.rpc(
            "child_expense_summary",
            buildJsonObject {
                put("p_child_id", childId?.let { JsonPrimitive(it) } ?: JsonNull)
                put("p_from", JsonPrimitive(from))
                put("p_to", JsonPrimitive(to))
            }
        ).decodeList<ExpenseCategoryTotal>()
    }

    /** Live warranties expiring inside the window — the reason to record one at all. */
    suspend fun expiringWarranties(childId: String, withinDays: Int = 60): Result<List<ChildExpense>> =
        runCatching {
            supabase.postgrest.rpc(
                "child_expiring_warranties",
                buildJsonObject {
                    put("p_child_id", JsonPrimitive(childId))
                    put("p_within_days", JsonPrimitive(withinDays))
                }
            ).decodeList<ChildExpense>()
        }

    /** One parent↔caregiver conversation about this child, oldest first. */
    suspend fun listCareMessages(
        childId: String,
        peerId: String,
        limit: Int = 200
    ): Result<List<CareMessage>> = runCatching {
        supabase.postgrest.rpc(
            "list_care_messages",
            buildJsonObject {
                put("p_child_id", JsonPrimitive(childId))
                put("p_peer_id", JsonPrimitive(peerId))
                put("p_limit", JsonPrimitive(limit))
            }
        ).decodeList<CareMessage>()
    }

    /** The RPC orders the pair and checks both sides really belong to this child. */
    suspend fun sendCareMessage(
        childId: String,
        peerId: String,
        body: String,
        replyTo: String? = null,
        imageUrl: String? = null,
        /**
         * Quoting an activity-history entry rather than a message. The feed is assembled from
         * eight record tables, so there is no message id to point [replyTo] at — the quoted line
         * and its author are snapshotted instead. The two are mutually exclusive; the RPC rejects
         * a message that tries to carry both.
         */
        quoteText: String? = null,
        quoteAuthor: String? = null
    ): Result<Unit> = runCatching {
        supabase.postgrest.rpc(
            "send_care_message",
            buildJsonObject {
                put("p_child_id", JsonPrimitive(childId))
                put("p_peer_id", JsonPrimitive(peerId))
                put("p_body", JsonPrimitive(body))
                put("p_reply_to", replyTo?.let { JsonPrimitive(it) } ?: JsonNull)
                put("p_image_url", imageUrl?.let { JsonPrimitive(it) } ?: JsonNull)
                put("p_quote_text", quoteText?.let { JsonPrimitive(it) } ?: JsonNull)
                put("p_quote_author", quoteAuthor?.let { JsonPrimitive(it) } ?: JsonNull)
            }
        )
        Unit
    }

    /**
     * Uploads a chat picture and returns its bucket path. The path starts with the child id
     * because that is exactly what the storage policy checks — membership in that child.
     */
    suspend fun uploadChatImage(
        childId: String,
        bytes: ByteArray,
        ext: String = "jpg"
    ): Result<String> = runCatching {
        val uid = supabase.auth.currentUserOrNull()?.id ?: error("not signed in")
        val path = "$childId/$uid/${java.util.UUID.randomUUID()}.$ext"
        supabase.storage.from(CHAT_IMAGE_BUCKET).upload(path, bytes)
        path
    }

    /** Chat pictures live in a private bucket, so they are only ever read through a signed URL. */
    suspend fun signedChatImageUrl(path: String?): String? {
        if (path.isNullOrBlank()) return null
        return runCatching {
            supabase.storage.from(CHAT_IMAGE_BUCKET).createSignedUrl(path, 3600.seconds)
        }.getOrNull()
    }

    /**
     * Budget beside spend for [month] (any day in it). The last row, with a null category, is the
     * month's overall figure.
     */
    suspend fun budgetOverview(childId: String?, month: String): Result<List<BudgetLine>> =
        runCatching {
            supabase.postgrest.rpc(
                "child_budget_overview",
                buildJsonObject {
                    put("p_child_id", childId?.let { JsonPrimitive(it) } ?: JsonNull)
                    put("p_month", JsonPrimitive(month))
                }
            ).decodeList<BudgetLine>()
        }

    /**
     * Replaces the whole month's budget in one call. [categories] maps a category key to satang;
     * a category left out of the map is cleared, which is how the sheet deletes one.
     */
    /**
     * A null [childId] sets the "ทั้งหมด" goal, which is a separate figure owned by this user —
     * not the children's goals added together. The database refuses a child goal that would push
     * the children past that total, and refuses a total below what the children already hold, so
     * either rejection arrives here as a failed Result rather than a silently clamped number.
     */
    suspend fun setBudget(
        childId: String?,
        month: String,
        totalSatang: Long,
        categories: Map<String, Long> = emptyMap(),
        /**
         * Only meaningful for the total goal, and only ever true because the user confirmed the
         * prompt: it clears that month's per-child goals so the new total can stand.
         */
        force: Boolean = false
    ): Result<Unit> = runCatching {
        val cats = buildJsonObject { categories.forEach { (k, v) -> put(k, JsonPrimitive(v)) } }
        if (childId == null) {
            supabase.postgrest.rpc(
                "set_total_budget",
                buildJsonObject {
                    put("p_month", JsonPrimitive(month))
                    put("p_total_satang", JsonPrimitive(totalSatang))
                    put("p_categories", cats)
                    put("p_force", JsonPrimitive(force))
                }
            )
        } else {
            supabase.postgrest.rpc(
                "set_child_budget",
                buildJsonObject {
                    put("p_child_id", JsonPrimitive(childId))
                    put("p_month", JsonPrimitive(month))
                    put("p_total_satang", JsonPrimitive(totalSatang))
                    put("p_categories", cats)
                }
            )
        }
        Unit
    }

    /** Monthly totals for the trend chart, oldest first. Empty months come back as zero. */
    suspend fun expenseTrend(childId: String?, months: Int = 3): Result<List<ExpenseTrendPoint>> =
        runCatching {
            supabase.postgrest.rpc(
                "child_expense_trend",
                buildJsonObject {
                    put("p_child_id", childId?.let { JsonPrimitive(it) } ?: JsonNull)
                    put("p_months", JsonPrimitive(months))
                }
            ).decodeList<ExpenseTrendPoint>()
        }

    /** Same private-bucket shape as chat pictures — a receipt can carry a name and a card tail. */
    suspend fun uploadReceipt(
        childId: String,
        bytes: ByteArray,
        ext: String = "jpg"
    ): Result<String> = runCatching {
        val uid = supabase.auth.currentUserOrNull()?.id ?: error("not signed in")
        val path = "$childId/$uid/${java.util.UUID.randomUUID()}.$ext"
        supabase.storage.from(RECEIPT_BUCKET).upload(path, bytes)
        path
    }

    suspend fun signedReceiptUrl(path: String?): String? {
        if (path.isNullOrBlank()) return null
        return runCatching {
            supabase.storage.from(RECEIPT_BUCKET).createSignedUrl(path, 3600.seconds)
        }.getOrNull()
    }

    /* ----------------------------------------------------------------------------------------
     * ตารางนัดหมายและวันสำคัญ — the activity schedule.
     * -------------------------------------------------------------------------------------- */

    /**
     * Everything inside the window currently on screen. The caller passes the window rather than a
     * "day/week/month" mode, so adding another view needs no change here or on the server.
     * [from] and [to] are ISO instants; [to] is exclusive.
     */
    suspend fun listActivities(
        childId: String,
        from: String,
        to: String
    ): Result<List<ChildActivity>> = runCatching {
        supabase.postgrest.rpc(
            "list_child_activities",
            buildJsonObject {
                put("p_child_id", JsonPrimitive(childId))
                put("p_from", JsonPrimitive(from))
                put("p_to", JsonPrimitive(to))
            }
        ).decodeList<ChildActivity>()
    }

    /** Counts per category for the "สรุปภาพรวม 7 วันข้างหน้า" panel. */
    suspend fun activitySummary(
        childId: String,
        days: Int = 7
    ): Result<List<ActivityCategoryCount>> = runCatching {
        supabase.postgrest.rpc(
            "child_activity_summary",
            buildJsonObject {
                put("p_child_id", JsonPrimitive(childId))
                put("p_days", JsonPrimitive(days))
            }
        ).decodeList<ActivityCategoryCount>()
    }

    suspend fun addActivity(
        childId: String,
        title: String,
        category: String,
        startsAt: String,
        endsAt: String? = null,
        allDay: Boolean = false,
        locationName: String? = null,
        placeId: String? = null,
        lat: Double? = null,
        lng: Double? = null,
        notes: String? = null,
        estimatedCostSatang: Long? = null
    ): Result<Unit> = runCatching {
        supabase.postgrest.from("child_activities").insert(
            buildJsonObject {
                put("child_id", JsonPrimitive(childId))
                put("title", JsonPrimitive(title))
                put("category", JsonPrimitive(category))
                put("starts_at", JsonPrimitive(startsAt))
                put("ends_at", endsAt?.let { JsonPrimitive(it) } ?: JsonNull)
                put("all_day", JsonPrimitive(allDay))
                put("location_name", locationName?.ifBlank { null }?.let { JsonPrimitive(it) } ?: JsonNull)
                put("place_id", placeId?.ifBlank { null }?.let { JsonPrimitive(it) } ?: JsonNull)
                put("lat", lat?.let { JsonPrimitive(it) } ?: JsonNull)
                put("lng", lng?.let { JsonPrimitive(it) } ?: JsonNull)
                put("notes", notes?.ifBlank { null }?.let { JsonPrimitive(it) } ?: JsonNull)
                put(
                    "estimated_cost_satang",
                    estimatedCostSatang?.let { JsonPrimitive(it) } ?: JsonNull
                )
            }
        )
        Unit
    }

    /**
     * The cached suggestion batch and whether it is still inside the refresh window.
     *
     * Freshness is decided by Postgres rather than by comparing timestamps on the device: the cache
     * is shared between everyone caring for the child, and a phone with a wrong clock would
     * otherwise regenerate constantly or never.
     */
    suspend fun getRecommendations(
        childId: String,
        maxAgeDays: Int = 3
    ): Result<CachedRecommendations?> = runCatching {
        supabase.postgrest.rpc(
            "get_activity_recommendations",
            buildJsonObject {
                put("p_child_id", JsonPrimitive(childId))
                put("p_max_age_days", JsonPrimitive(maxAgeDays))
            }
        ).decodeList<CachedRecommendations>().firstOrNull()
    }

    suspend fun saveRecommendations(
        childId: String,
        items: List<com.lullabyai.app.data.remote.ActivityRecommendation>,
        homeOnly: Boolean
    ): Result<Unit> = runCatching {
        supabase.postgrest.rpc(
            "save_activity_recommendations",
            buildJsonObject {
                put("p_child_id", JsonPrimitive(childId))
                put(
                    "p_items",
                    kotlinx.serialization.json.Json.encodeToJsonElement(
                        kotlinx.serialization.builtins.ListSerializer(
                            com.lullabyai.app.data.remote.ActivityRecommendation.serializer()
                        ),
                        items
                    )
                )
                put("p_home_only", JsonPrimitive(homeOnly))
            }
        )
        Unit
    }

    suspend fun deleteActivity(activityId: String): Result<Unit> = runCatching {
        supabase.postgrest.from("child_activities").delete { filter { eq("id", activityId) } }
        Unit
    }

    /** Every person I can talk to about this child, newest conversation first. */
    suspend fun careConversations(childId: String): Result<List<CareConversation>> = runCatching {
        supabase.postgrest.rpc(
            "care_conversations",
            buildJsonObject { put("p_child_id", JsonPrimitive(childId)) }
        ).decodeList<CareConversation>()
    }

    /**
     * Announces me as present on this child's care channel and emits the current set of online
     * user ids.
     *
     * Presence is per-connection: it vanishes when the socket drops, which is exactly how a green
     * dot should behave. Nothing is persisted, so a crashed app cannot leave a stale "online".
     */
    fun watchPresence(childId: String): Flow<Set<String>> = flow {
        ensureRealtimeConnected()
        val me = supabase.auth.currentUserOrNull()?.id ?: return@flow
        val channel = supabase.channel("presence:care:$childId")
        // presenceDataFlow re-emits the WHOLE current roster on every join and leave, so the
        // green dots are derived from one authoritative list rather than accumulated deltas.
        val roster = channel.presenceDataFlow<PresenceUser>()
        channel.subscribe(blockUntilSubscribed = true)
        runCatching { channel.track(buildJsonObject { put("user_id", JsonPrimitive(me)) }) }
        try {
            roster.collect { list -> emit(list.map { it.userId }.filter { it.isNotBlank() }.toSet()) }
        } finally {
            withContext(NonCancellable) {
                runCatching { channel.untrack() }
                supabase.realtime.removeChannel(channel)
            }
        }
    }

    /** What each client publishes about itself on a presence channel. */
    @kotlinx.serialization.Serializable
    data class PresenceUser(
        @kotlinx.serialization.SerialName("user_id") val userId: String = ""
    )

    /** Marks everything the peer sent me in this conversation as seen. */
    suspend fun markCareMessagesRead(childId: String, peerId: String): Result<Unit> = runCatching {
        supabase.postgrest.rpc(
            "mark_care_messages_read",
            buildJsonObject {
                put("p_child_id", JsonPrimitive(childId))
                put("p_peer_id", JsonPrimitive(peerId))
            }
        )
        Unit
    }

    /** Puts a conversation back to unread on purpose — the long-press affordance in the hub. */
    suspend fun markCareConversationUnread(childId: String, peerId: String): Result<Unit> =
        runCatching {
            supabase.postgrest.rpc(
                "mark_care_conversation_unread",
                buildJsonObject {
                    put("p_child_id", JsonPrimitive(childId))
                    put("p_peer_id", JsonPrimitive(peerId))
                }
            )
            Unit
        }

    /** Unread counts keyed by the person who sent them — drives the header badge and picker rows. */
    suspend fun careUnreadByPeer(childId: String): Result<Map<String, Int>> = runCatching {
        supabase.postgrest.rpc(
            "care_unread_by_peer",
            buildJsonObject { put("p_child_id", JsonPrimitive(childId)) }
        ).decodeList<PeerUnread>().associate { it.peerId to it.unread }
    }

    @kotlinx.serialization.Serializable
    private data class PeerUnread(
        @kotlinx.serialization.SerialName("peer_id") val peerId: String = "",
        val unread: Int = 0
    )

    /** Emits whenever this conversation changes, so the thread updates without polling. */
    fun watchCareMessages(childId: String, peerId: String): Flow<Unit> = flow {
        ensureRealtimeConnected()
        val channel = supabase.channel("care-messages:$childId:$peerId")
        // Filtered on child only — Realtime allows one filter per subscription, and the pair
        // columns get checked by the reload that follows anyway.
        val inserts = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
            table = "care_messages"
            filter("child_id", FilterOperator.EQ, childId)
        }
        val updates = channel.postgresChangeFlow<PostgresAction.Update>(schema = "public") {
            table = "care_messages"
            filter("child_id", FilterOperator.EQ, childId)
        }
        channel.subscribe()
        try {
            coroutineScope {
                launch { inserts.collect { emit(Unit) } }
                launch { updates.collect { emit(Unit) } }
            }
        } finally {
            withContext(NonCancellable) { supabase.realtime.removeChannel(channel) }
        }
    }

    /**
     * Emits whenever a caregiver records, edits or removes a care log for this child — what makes
     * the parent's activity history update the moment the nanny saves.
     */
    fun watchCareLogs(childId: String): Flow<Unit> = flow {
        ensureRealtimeConnected()
        val channel = supabase.channel("care-logs:$childId")
        val inserts = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
            table = "care_logs"
            filter("child_id", FilterOperator.EQ, childId)
        }
        val updates = channel.postgresChangeFlow<PostgresAction.Update>(schema = "public") {
            table = "care_logs"
            filter("child_id", FilterOperator.EQ, childId)
        }
        val deletes = channel.postgresChangeFlow<PostgresAction.Delete>(schema = "public") {
            table = "care_logs"
            filter("child_id", FilterOperator.EQ, childId)
        }
        channel.subscribe()
        try {
            coroutineScope {
                launch { inserts.collect { emit(Unit) } }
                launch { updates.collect { emit(Unit) } }
                launch { deletes.collect { emit(Unit) } }
            }
        } finally {
            withContext(NonCancellable) { supabase.realtime.removeChannel(channel) }
        }
    }

    /**
     * Withdraws a message. Sender-only, and the original text stays readable by design — see
     * [CareMessage.deletedAt]. Whoever calls this must have told the user that first.
     */
    suspend fun unsendCareMessage(messageId: String): Result<Unit> = runCatching {
        supabase.postgrest.rpc(
            "unsend_care_message",
            buildJsonObject { put("p_message_id", JsonPrimitive(messageId)) }
        )
        Unit
    }

    /** When I accepted the "this chat is not private" notice for this child, or null if never. */
    suspend fun chatAiConsentAt(childId: String): Result<String?> = runCatching {
        val raw = supabase.postgrest.rpc(
            "my_chat_ai_consent",
            buildJsonObject { put("p_child_id", JsonPrimitive(childId)) }
        ).data
        raw.trim().trim('"').takeIf { it.isNotBlank() && it != "null" }
    }

    suspend fun acceptChatAiPolicy(childId: String): Result<Unit> = runCatching {
        supabase.postgrest.rpc(
            "accept_chat_ai_policy",
            buildJsonObject { put("p_child_id", JsonPrimitive(childId)) }
        )
        Unit
    }

    /** Records one caregiver activity. `logged_at` and `created_by` are filled server-side. */
    suspend fun addCareLog(
        childId: String,
        kind: String,
        note: String? = null,
        mood: String? = null,
        abnormal: String? = null
    ): Result<Unit> = runCatching {
        supabase.postgrest.from("care_logs").insert(
            buildJsonObject {
                put("child_id", JsonPrimitive(childId))
                put("kind", JsonPrimitive(kind))
                put("note", note?.ifBlank { null }?.let { JsonPrimitive(it) } ?: JsonNull)
                put("mood", mood?.ifBlank { null }?.let { JsonPrimitive(it) } ?: JsonNull)
                put("abnormal", abnormal?.ifBlank { null }?.let { JsonPrimitive(it) } ?: JsonNull)
            }
        )
        Unit
    }

    /** Care log inside a half-open window — `[fromIso, toIso)` — newest first. */
    suspend fun listCareLogs(childId: String, fromIso: String, toIso: String): Result<List<CareLog>> =
        runCatching {
            supabase.postgrest.rpc(
                "list_care_logs",
                buildJsonObject {
                    put("p_child_id", JsonPrimitive(childId))
                    put("p_from", JsonPrimitive(fromIso))
                    put("p_to", JsonPrimitive(toIso))
                }
            ).decodeList<CareLog>()
        }

    /**
     * People who could be invited. The RPC refuses anyone who is not an owner/co-owner and
     * returns nothing under 3 characters, so this is safe to call on every keystroke.
     */
    suspend fun searchInviteCandidates(childId: String, query: String): Result<List<InviteCandidate>> =
        runCatching {
            supabase.postgrest.rpc(
                "search_invite_candidates",
                buildJsonObject {
                    put("p_child_id", JsonPrimitive(childId))
                    put("p_query", JsonPrimitive(query))
                }
            ).decodeList<InviteCandidate>()
        }

    suspend fun updateMemberPermissions(
        childId: String,
        userId: String,
        role: String? = null,
        canViewHealth: Boolean? = null,
        canLogSymptoms: Boolean? = null,
        canEditGrowth: Boolean? = null,
        canViewAppointments: Boolean? = null,
        canBookDoctor: Boolean? = null,
        canCancelAppointments: Boolean? = null,
        canViewDocuments: Boolean? = null,
        canUploadDocuments: Boolean? = null,
        canEditProfile: Boolean? = null
    ): Result<Unit> = runCatching {
        supabase.postgrest.rpc(
            "update_child_member_permissions",
            buildJsonObject {
                put("p_child_id", JsonPrimitive(childId))
                put("p_user_id", JsonPrimitive(userId))
                put("p_role", role?.let { JsonPrimitive(it) } ?: JsonNull)
                put("p_can_view_health", canViewHealth?.let { JsonPrimitive(it) } ?: JsonNull)
                put("p_can_log_symptoms", canLogSymptoms?.let { JsonPrimitive(it) } ?: JsonNull)
                put("p_can_edit_growth", canEditGrowth?.let { JsonPrimitive(it) } ?: JsonNull)
                put("p_can_view_appointments", canViewAppointments?.let { JsonPrimitive(it) } ?: JsonNull)
                put("p_can_book_doctor", canBookDoctor?.let { JsonPrimitive(it) } ?: JsonNull)
                put("p_can_cancel_appointments", canCancelAppointments?.let { JsonPrimitive(it) } ?: JsonNull)
                put("p_can_view_documents", canViewDocuments?.let { JsonPrimitive(it) } ?: JsonNull)
                put("p_can_upload_documents", canUploadDocuments?.let { JsonPrimitive(it) } ?: JsonNull)
                put("p_can_edit_profile", canEditProfile?.let { JsonPrimitive(it) } ?: JsonNull)
            }
        )
        Unit
    }

    suspend fun removeAccessMember(childId: String, userId: String): Result<Unit> = runCatching {
        supabase.postgrest.rpc(
            "remove_child_member",
            buildJsonObject {
                put("p_child_id", JsonPrimitive(childId))
                put("p_user_id", JsonPrimitive(userId))
            }
        )
        Unit
    }

    // ---------------------------------------------------------------- custom roles (#5)
    // All four RPCs are owner-gated server-side; these wrappers add no checks of their own on
    // purpose, so the UI can never be the thing that authorises a role change.

    suspend fun listChildRoles(childId: String): Result<List<ChildRole>> = runCatching {
        supabase.postgrest.rpc(
            "list_child_roles",
            buildJsonObject { put("p_child_id", JsonPrimitive(childId)) }
        ).decodeList<ChildRole>()
    }

    /** Creates or renames a custom role. Unknown capability strings are dropped server-side. */
    suspend fun upsertChildRole(
        childId: String,
        key: String,
        nameTh: String,
        nameEn: String,
        capabilities: List<String>
    ): Result<Unit> = runCatching {
        supabase.postgrest.rpc(
            "upsert_child_role",
            buildJsonObject {
                put("p_child_id", JsonPrimitive(childId))
                put("p_key", JsonPrimitive(key))
                put("p_name_th", JsonPrimitive(nameTh))
                put("p_name_en", JsonPrimitive(nameEn))
                put("p_capabilities", JsonArray(capabilities.map { JsonPrimitive(it) }))
            }
        )
        Unit
    }

    /** Saves capability changes for a built-in preset (Doctor, Nanny, …) for this child only. */
    suspend fun upsertChildRoleOverride(
        childId: String,
        baseRoleKey: String,
        nameTh: String,
        nameEn: String,
        capabilities: List<String>
    ): Result<Unit> = runCatching {
        supabase.postgrest.rpc(
            "upsert_child_role_override",
            buildJsonObject {
                put("p_child_id", JsonPrimitive(childId))
                put("p_base_role_key", JsonPrimitive(baseRoleKey))
                put("p_name_th", JsonPrimitive(nameTh))
                put("p_name_en", JsonPrimitive(nameEn))
                put("p_capabilities", JsonArray(capabilities.map { JsonPrimitive(it) }))
            }
        )
        Unit
    }

    /**
     * The roles a child's family offers, readable before you are a member — the "เลือกบทบาทของคุณ"
     * step of a join request. Names only; [listChildRoles] stays the member-only full view.
     */
    suspend fun listRolesForRequest(childId: String): Result<List<ChildRole>> = runCatching {
        supabase.postgrest.rpc(
            "list_child_roles_for_request",
            buildJsonObject { put("p_child_id", JsonPrimitive(childId)) }
        ).decodeList<ChildRole>()
    }

    suspend fun deleteChildRole(roleId: String): Result<Unit> = runCatching {
        supabase.postgrest.rpc(
            "delete_child_role",
            buildJsonObject { put("p_role_id", JsonPrimitive(roleId)) }
        )
        Unit
    }

    /** Applies a role's capabilities to one member. Server refuses self-edits and the owner row. */
    suspend fun assignChildRole(childId: String, userId: String, roleId: String): Result<Unit> = runCatching {
        supabase.postgrest.rpc(
            "assign_child_role",
            buildJsonObject {
                put("p_child_id", JsonPrimitive(childId))
                put("p_user_id", JsonPrimitive(userId))
                put("p_role_id", JsonPrimitive(roleId))
            }
        )
        Unit
    }

    companion object {
        /** Roles `request_child_sync` accepts; anything else is clamped to co_owner server-side. */
        const val DEFAULT_REQUEST_ROLE = "co_owner"

        /** Private bucket for chat pictures; read only through short-lived signed URLs. */
        const val CHAT_IMAGE_BUCKET = "chat-images"
        const val RECEIPT_BUCKET = "expense-receipts"

        /** Accepts raw 6-char codes or nongrak://access?code=XXXXXX / URL payloads from QR. */
        fun extractInviteCode(raw: String): String {
            val trimmed = raw.trim()
            val fromQuery = Regex("[?&]code=([A-Za-z0-9]+)").find(trimmed)?.groupValues?.getOrNull(1)
            return (fromQuery ?: trimmed).uppercase().filter { it.isLetterOrDigit() }.take(6)
        }
    }

    /**
     * Emits once for every access request that lands on the current user (owner) while the app is
     * open, so the caller can re-read [getPendingSyncRequests] and raise the approve/deny popup.
     *
     * Deliberately emits a bare signal instead of the row: the payload would have to be trusted and
     * kept in sync with the table's columns, whereas a re-read goes back through the RLS-scoped
     * select. Realtime is only the doorbell here, never the source of truth.
     *
     * Postgres-changes rows are additionally filtered by the existing `select own sync requests`
     * RLS policy, so a leaked channel name can't surface another family's requests.
     */
    fun watchPendingSyncRequests(ownerUserId: String): Flow<Unit> = flow {
        ensureRealtimeConnected()
        val channel = supabase.channel("child-sync-requests:$ownerUserId")
        val inserts = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
            table = "child_sync_requests"
            filter("owner_user_id", FilterOperator.EQ, ownerUserId)
        }
        val updates = channel.postgresChangeFlow<PostgresAction.Update>(schema = "public") {
            table = "child_sync_requests"
            filter("owner_user_id", FilterOperator.EQ, ownerUserId)
        }
        channel.subscribe()
        try {
            coroutineScope {
                launch { inserts.collect { emit(Unit) } }
                launch { updates.collect { emit(Unit) } }
            }
        } finally {
            withContext(NonCancellable) { supabase.realtime.removeChannel(channel) }
        }
    }

    /**
     * Emits when the current user is granted membership on a child (sync request approved).
     * Lets the requester's device refresh its children list without a manual pull-to-refresh.
     */
    fun watchMembershipGranted(userId: String): Flow<Unit> = flow {
        ensureRealtimeConnected()
        val channel = supabase.channel("child-members:$userId")
        val inserts = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
            table = "child_members"
            filter("user_id", FilterOperator.EQ, userId)
        }
        channel.subscribe()
        try {
            inserts.collect { emit(Unit) }
        } finally {
            withContext(NonCancellable) { supabase.realtime.removeChannel(channel) }
        }
    }

    /**
     * Emits when shared child data changes on the server — vaccines, health rows, vitals cache, etc.
     * Scoped to one child id so co-parent devices stay in sync while viewing the same profile.
     */
    fun watchChildDataChanges(childId: String): Flow<Unit> = flow {
        ensureRealtimeConnected()
        val channel = supabase.channel("child-data:$childId")
        val childTables = listOf("health_records", "vaccine_records", "growth_records", "allergies")
        val flows = buildList {
            childTables.forEach { table ->
                add(
                    channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                        this.table = table
                        filter("child_id", FilterOperator.EQ, childId)
                    }
                )
            }
            add(
                channel.postgresChangeFlow<PostgresAction.Update>(schema = "public") {
                    table = "children"
                    filter("id", FilterOperator.EQ, childId)
                }
            )
        }
        channel.subscribe()
        try {
            coroutineScope {
                flows.forEach { changes ->
                    launch { changes.collect { emit(Unit) } }
                }
            }
        } finally {
            withContext(NonCancellable) { supabase.realtime.removeChannel(channel) }
        }
    }

    /** Current user's membership row for [childId], or null if not a member. */
    suspend fun getMyMembership(childId: String): Result<ChildAccessMember?> = runCatching {
        val uid = supabase.auth.currentUserOrNull()?.id ?: return@runCatching null
        listAccessMembers(childId).getOrThrow().find { it.userId == uid }
    }

    /** True when the signed-in user created the child or holds member role `owner`. */
    suspend fun isChildOwner(childId: String): Result<Boolean> = runCatching {
        val uid = supabase.auth.currentUserOrNull()?.id ?: return@runCatching false
        val child = getChildById(childId).getOrThrow()
        if (child.userId == uid) return@runCatching true
        getMyMembership(childId).getOrThrow()?.role == "owner"
    }

    /** Transfer child ownership to an existing member. The current owner is demoted to co-owner. */
    suspend fun transferChildOwnership(childId: String, newOwnerUserId: String): Result<Unit> = runCatching {
        supabase.postgrest.rpc(
            "transfer_child_ownership",
            buildJsonObject {
                put("p_child_id", JsonPrimitive(childId))
                put("p_new_owner_id", JsonPrimitive(newOwnerUserId))
            }
        )
        Unit
    }

    /** Checks if any child has previous owner email matching the current user's email. */
    suspend fun checkReclaimableChildren(): Result<List<com.lullabyai.app.data.model.ReclaimableChild>> = runCatching {
        supabase.postgrest.rpc("check_reclaimable_children").decodeList<com.lullabyai.app.data.model.ReclaimableChild>()
    }

    /** Reclaims ownership of a child previously owned by the user. */
    suspend fun reclaimChildOwnership(childId: String): Result<Unit> = runCatching {
        supabase.postgrest.rpc(
            "reclaim_child_ownership",
            buildJsonObject {
                put("p_child_id", JsonPrimitive(childId))
            }
        )
        Unit
    }

    /** Returns list of children where the current user is owner. */
    suspend fun getOwnedChildren(): Result<List<Child>> = runCatching {
        val uid = supabase.auth.currentUserOrNull()?.id ?: return@runCatching emptyList()
        val all = getChildren().getOrDefault(emptyList())
        all.filter { it.userId == uid }
    }

    /** Pending sync requests waiting on the current user's (owner) confirmation. */
    suspend fun getPendingSyncRequests(): Result<List<ChildSyncRequest>> = runCatching {
        val userId = supabase.auth.currentUserOrNull()?.id ?: error("Not logged in")
        supabase.from("child_sync_requests").select {
            filter { eq("owner_user_id", userId); eq("status", "pending") }
            order("created_at", Order.DESCENDING)
        }.decodeList<ChildSyncRequest>()
    }

    /** Owner confirms or rejects a pending sync request with optional role assignment. Approving grants the requester member access. */
    suspend fun confirmSyncRequest(requestId: String, approve: Boolean, role: String? = null): Result<Unit> = runCatching {
        supabase.postgrest.rpc(
            "confirm_child_sync_request",
            buildJsonObject {
                put("p_request_id", JsonPrimitive(requestId))
                put("p_approve", JsonPrimitive(approve))
                put("p_role", role?.let { JsonPrimitive(it) } ?: JsonNull)
            }
        )
        Unit
    }

    suspend fun updateChild(child: Child): Result<Unit> = runCatching {
        supabase.from("children").update(child) {
            filter { eq("id", child.id) }
        }
    }

    /** Partial vitals write — keeps `children.weight_kg` / `height_cm` as a denormalized cache of the
     *  latest growth reading. Only patches the fields that are non-null so a weight-only update
     *  never blanks height (and vice versa). Callers that own growth history should prefer
     *  [HealthRepository.addGrowthRecord], which syncs here automatically. */
    suspend fun updateVitals(childId: String, weightKg: Double?, heightCm: Double?): Result<Unit> = runCatching {
        if (weightKg == null && heightCm == null) return@runCatching
        supabase.from("children").update(
            buildJsonObject {
                weightKg?.let { put("weight_kg", JsonPrimitive(it)) }
                heightCm?.let { put("height_cm", JsonPrimitive(it)) }
            }
        ) { filter { eq("id", childId) } }
    }

    /** Keeps the denormalized `children.allergies` summary string in lockstep with the structured
     *  `allergies` table (Nutrition allergy-watch + chat context still read the string). */
    suspend fun syncAllergySummary(childId: String, names: List<String>): Result<Unit> = runCatching {
        val summary = names.map { it.trim() }.filter { it.isNotBlank() }.distinct().joinToString(", ")
        supabase.from("children").update(
            buildJsonObject {
                put("allergies", if (summary.isBlank()) JsonNull else JsonPrimitive(summary))
            }
        ) { filter { eq("id", childId) } }
    }

    suspend fun updateChildNotes(childId: String, notes: String): Result<Unit> = runCatching {
        supabase.from("children").update(buildJsonObject { put("notes", JsonPrimitive(notes)) }) {
            filter { eq("id", childId) }
        }
    }

    suspend fun setNoKnownAllergies(childId: String, value: Boolean): Result<Unit> = runCatching {
        supabase.from("children").update(buildJsonObject { put("no_known_allergies", JsonPrimitive(value)) }) {
            filter { eq("id", childId) }
        }
    }

    /** Delete a child (cascade removes its vaccines, milestones, health/growth/allergy/doc rows). */
    suspend fun deleteChild(childId: String): Result<Unit> = runCatching {
        supabase.from("children").delete { filter { eq("id", childId) } }
    }

    /**
     * Uploads a child's photo to the PRIVATE 'avatars' bucket, stores the bucket PATH on the child
     * row (never a public URL), and returns a short-lived signed URL for immediate display.
     */
    suspend fun uploadChildAvatar(childId: String, bytes: ByteArray): Result<String> = runCatching {
        val userId = supabase.auth.currentUserOrNull()?.id ?: error("Not logged in")
        val path = "$userId/child_$childId.jpg"
        supabase.storage.from(StorageHelper.AVATAR_BUCKET).upload(path, bytes) { upsert = true }
        // Store the stable PATH (not a public URL) — photos are private, served via signed URLs.
        supabase.from("children").update(buildJsonObject { put("avatar_url", JsonPrimitive(path)) }) {
            filter { eq("id", childId) }
        }
        StorageHelper.signedAvatarUrl(path) ?: error("Could not sign avatar URL")
    }

    suspend fun saveMedicalHistory(history: MedicalHistory): Result<Unit> = runCatching {
        supabase.from("medical_history").insert(history)
    }

    /** Uploads a medical document to the PRIVATE 'medical-docs' bucket and records its metadata.
     *  [doctorVisitId] tags the row so RecordVisitScreen/VisitDetailsScreen can pull just its own
     *  attachments (e.g. a scanned prescription) instead of the child's whole document vault.
     *  [category] is a Stitch Document Vault chip: identity | certs | lab | other. */
    suspend fun uploadMedicalDocument(
        childId: String,
        fileName: String,
        mime: String?,
        bytes: ByteArray,
        doctorVisitId: String? = null,
        category: String = "other",
        verified: Boolean = category == "identity"
    ): Result<Unit> = runCatching {
        val userId = supabase.auth.currentUserOrNull()?.id ?: error("Not logged in")
        val safeCat = when (category) {
            "identity", "certs", "lab", "other" -> category
            else -> "other"
        }
        val safe = fileName.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "document" }
        val path = "$userId/$childId/$safeCat/${System.currentTimeMillis()}_$safe"
        supabase.storage.from("medical-docs").upload(path, bytes) { upsert = true }
        supabase.from("medical_documents").insert(buildJsonObject {
            put("child_id", JsonPrimitive(childId))
            put("file_name", JsonPrimitive(fileName))
            put("file_path", JsonPrimitive(path))
            put("mime_type", mime?.let { JsonPrimitive(it) } ?: JsonNull)
            put("doctor_visit_id", doctorVisitId?.let { JsonPrimitive(it) } ?: JsonNull)
            put("category", JsonPrimitive(safeCat))
            put("file_size_bytes", JsonPrimitive(bytes.size.toLong()))
            put("verified", JsonPrimitive(verified))
        })
    }

    /** Download raw bytes from the private medical-docs bucket (for save-to-Downloads). */
    suspend fun downloadMedicalDocumentBytes(filePath: String): Result<ByteArray> = runCatching {
        supabase.storage.from("medical-docs").downloadAuthenticated(filePath)
    }

    suspend fun getMedicalDocuments(childId: String): Result<List<com.lullabyai.app.data.model.MedicalDocument>> = runCatching {
        supabase.from("medical_documents").select {
            filter { eq("child_id", childId) }
            order("created_at", Order.DESCENDING)
        }.decodeList()
    }

    /** Documents attached to one doctor visit (VisitDetailsScreen's attachments section). */
    suspend fun getVisitDocuments(visitId: String): Result<List<com.lullabyai.app.data.model.MedicalDocument>> = runCatching {
        supabase.from("medical_documents").select {
            filter { eq("doctor_visit_id", visitId) }
            order("created_at", Order.DESCENDING)
        }.decodeList()
    }

    /** Short-lived signed URL to view/open a private medical-docs bucket file (system viewer). */
    suspend fun signedMedicalDocUrl(filePath: String): Result<String> = runCatching {
        supabase.storage.from("medical-docs").createSignedUrl(filePath, medicalDocUrlTtl)
    }

    /** Remove a medical document: delete the file from the bucket and its metadata row. */
    suspend fun deleteMedicalDocument(id: String, filePath: String): Result<Unit> = runCatching {
        runCatching { supabase.storage.from("medical-docs").delete(filePath) }
        supabase.from("medical_documents").delete { filter { eq("id", id) } }
    }

    suspend fun getMedicalHistory(childId: String): Result<MedicalHistory?> = runCatching {
        val list = supabase.from("medical_history").select {
            filter { eq("child_id", childId) }
            limit(1)
        }.decodeList<MedicalHistory>()
        list.firstOrNull()
    }

    suspend fun getMilestones(childId: String): Result<List<DevelopmentMilestone>> = runCatching {
        supabase.from("development_milestones").select {
            filter { eq("child_id", childId) }
            order("age_months_expected", Order.ASCENDING)
        }.decodeList<DevelopmentMilestone>()
    }

    suspend fun updateMilestone(milestone: DevelopmentMilestone): Result<Unit> = runCatching {
        supabase.from("development_milestones").update(milestone) {
            filter { eq("id", milestone.id) }
        }
        // Only reaching it counts, not un-ticking it. Keyed on the milestone row, so a parent who
        // tries it, unticks and ticks again is credited once — and never loses what they had.
        if (milestone.isAchieved) {
            MascotRewards.award(XpKind.MILESTONE_ACHIEVED, milestone.id)
        }
    }

    /** Mark a milestone achieved/not — explicit JSON so false/null values are always written. */
    suspend fun setMilestoneAchieved(id: String, achieved: Boolean): Result<Unit> = runCatching {
        supabase.from("development_milestones").update(
            buildJsonObject {
                put("is_achieved", JsonPrimitive(achieved))
                put("achieved_at", if (achieved) JsonPrimitive(java.time.Instant.now().toString()) else JsonNull)
                put("skip_reason", JsonNull)   // achieving clears any skip reason
            }
        ) { filter { eq("id", id) } }
    }

    /** Record (or clear) the reason a milestone was skipped. */
    suspend fun setMilestoneSkipReason(id: String, reason: String?): Result<Unit> = runCatching {
        supabase.from("development_milestones").update(
            buildJsonObject {
                put("skip_reason", reason?.let { JsonPrimitive(it) } ?: JsonNull)
                put("is_achieved", JsonPrimitive(false))
            }
        ) { filter { eq("id", id) } }
    }

    suspend fun insertDefaultMilestones(childId: String, ageMonths: Int) {
        val milestones = defaultMilestones(childId)
        if (milestones.isNotEmpty()) {
            supabase.from("development_milestones").insert(milestones)
        }
    }

    /**
     * The full 0–3y developmental milestone checklist, anchored to the standard well-child
     * checkpoints (1, 2, 4, 6, 9, 12, 15, 18, 24, 30, 36 months) across the 5 domains the app shows
     * (gross_motor, fine_motor, communication, social, self_help).
     *
     * Sources: primary = CDC 2022 "Learn the Signs. Act Early." milestones; cross-checked against
     * Thailand's official DSPM (คู่มือเฝ้าระวังและส่งเสริมพัฒนาการเด็กปฐมวัย, กรมอนามัย 2560).
     * The two agree on the major motor/language anchors (เดินได้เอง 15mo, วิ่ง/เตะบอล 24mo,
     * กระโดดสองเท้า 30mo, ขึ้นบันไดสลับเท้า 36mo). DSPM's official SCREENING checkpoints are
     * 9, 18, 30 (and 42) months — present here. DSPM also begins at 1 month, so a 1-month row is
     * included. DSPM splits language into Receptive (RL) + Expressive (EL); the app merges both
     * under "communication".
     *
     * The [ageMonths] param is accepted for API compatibility but the full list is always seeded so
     * the child has every checkpoint to grow into.
     */
    fun defaultMilestones(childId: String): List<DevelopmentMilestone> {
        fun m(cat: String, name: String, age: Int) =
            DevelopmentMilestone(childId = childId, category = cat, milestoneName = name, ageMonthsExpected = age)
        return listOf(
            // 1 month (DSPM first checkpoint)
            m("gross_motor", "ท่านอนคว่ำ ยกและหันศีรษะไปข้างใดข้างหนึ่งได้", 1),
            m("social", "มองจ้องหน้าและสบตา", 1),
            // 2 months
            m("gross_motor", "ชันคอขึ้นได้ขณะนอนคว่ำ", 2),
            m("social", "ยิ้มตอบเมื่อพ่อแม่ยิ้มหรือพูดด้วย", 2),
            m("communication", "ส่งเสียงอ้อแอ้ นอกเหนือจากการร้องไห้", 2),
            // 4 months
            m("gross_motor", "ชันคอตั้งตรงได้มั่นคง", 4),
            m("fine_motor", "คว้าและถือของเล่นที่ยื่นให้", 4),
            m("social", "หัวเราะเสียงดัง", 4),
            m("communication", "หันหาเสียงพ่อแม่", 4),
            // 6 months
            m("gross_motor", "พลิกคว่ำพลิกหงายได้เอง", 6),
            m("fine_motor", "เอื้อมมือคว้าของเล่น", 6),
            m("communication", "ส่งเสียงโต้ตอบสลับกับผู้ใหญ่", 6),
            m("social", "รู้จักและยิ้มให้คนคุ้นเคย", 6),
            // 9 months
            m("gross_motor", "นั่งได้เองโดยไม่ต้องพยุง", 9),
            m("fine_motor", "ใช้นิ้วหยิบอาหารชิ้นเล็กเข้าปาก", 9),
            m("communication", "หันมาเมื่อถูกเรียกชื่อ", 9),
            m("social", "ติดพ่อแม่และกลัวคนแปลกหน้า", 9),
            // 12 months
            m("gross_motor", "เกาะยืนและเกาะเดิน", 12),
            m("fine_motor", "หยิบของชิ้นเล็กด้วยนิ้วหัวแม่มือและนิ้วชี้", 12),
            m("communication", "เรียกพ่อแม่ เช่น มามา ปาปา", 12),
            m("communication", "โบกมือบ๊ายบาย", 12),
            m("social", "เล่นจ๊ะเอ๋และตบมือ", 12),
            // 15 months
            m("gross_motor", "เดินได้เองสองสามก้าว", 15),
            m("fine_motor", "ต่อก้อนไม้ได้ 2 ก้อน", 15),
            m("communication", "พูดคำที่มีความหมายได้ 1-2 คำ", 15),
            m("social", "เลียนแบบท่าทางของผู้อื่น", 15),
            // 18 months
            m("gross_motor", "เดินได้คล่อง", 18),
            m("fine_motor", "ขีดเขียนเส้นยุ่งๆ บนกระดาษ", 18),
            m("self_help", "ใช้ช้อนตักอาหารได้", 18),
            m("communication", "พูดคำที่มีความหมายได้อย่างน้อย 3 คำ", 18),
            m("social", "ชี้ชวนให้ดูสิ่งที่สนใจ", 18),
            // 24 months (2 years)
            m("gross_motor", "วิ่งได้", 24),
            m("gross_motor", "เตะลูกบอลได้", 24),
            m("fine_motor", "ต่อก้อนไม้เป็นหอสูง 6 ก้อน", 24),
            m("communication", "พูดต่อคำเป็นวลี 2 คำ เช่น กินนม", 24),
            m("self_help", "ถอดเสื้อผ้าที่ไม่มีกระดุมได้เอง", 24),
            // 30 months (2.5 years)
            m("gross_motor", "กระโดดสองเท้าพ้นพื้นได้", 30),
            m("communication", "พูดได้ประมาณ 50 คำ", 30),
            m("communication", "ทำตามคำสั่ง 2 ขั้นตอนได้", 30),
            m("self_help", "ล้างมือและเช็ดมือได้เอง", 30),
            // 36 months (3 years)
            m("gross_motor", "ขึ้นบันไดสลับเท้าได้", 36),
            m("fine_motor", "วาดวงกลมตามแบบได้", 36),
            m("communication", "พูดเป็นประโยค 3 คำได้", 36),
            m("communication", "บอกชื่อตัวเองได้", 36),
            m("social", "เล่นร่วมกับเด็กคนอื่นได้", 36),
            m("self_help", "ใส่เสื้อผ้าบางชิ้นเองได้", 36)
        )
    }

    /**
     * Idempotently tops up a child's milestones: inserts any default milestone (matched by name)
     * the child doesn't already have. Lets existing children (seeded with an older, sparser list)
     * pick up the full checklist without duplicating rows or disturbing achieved/skip state.
     */
    suspend fun ensureDefaultMilestones(childId: String): Result<Unit> = runCatching {
        val existing = getMilestones(childId).getOrDefault(emptyList()).map { it.milestoneName }.toSet()
        val missing = defaultMilestones(childId).filter { it.milestoneName !in existing }
        if (missing.isNotEmpty()) supabase.from("development_milestones").insert(missing)
    }
}
