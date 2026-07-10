package com.lullabyai.app.data.repository

import com.lullabyai.app.data.model.Child
import com.lullabyai.app.data.model.ChildSyncMatch
import com.lullabyai.app.data.model.ChildSyncRequest
import com.lullabyai.app.data.model.DevelopmentMilestone
import com.lullabyai.app.data.model.MedicalHistory
import com.lullabyai.app.data.remote.StorageHelper
import com.lullabyai.app.data.remote.supabase
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

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
        if (first.isSuccess) return first
        kotlinx.coroutines.delay(600)
        return runCatching { supabase.from("children").select().decodeList<Child>() }
    }

    suspend fun getFirstChild(): Result<Child?> = runCatching {
        val list = supabase.from("children").select {
            limit(1)
        }.decodeList<Child>()
        list.firstOrNull()
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
    suspend fun requestSyncBySearch(childId: String): Result<Unit> = runCatching {
        supabase.postgrest.rpc(
            "request_child_sync",
            buildJsonObject {
                put("p_child_id", JsonPrimitive(childId))
                put("p_method", JsonPrimitive("search"))
            }
        )
        Unit
    }

    /** Requests sync access by redeeming an invite code — creates a pending request, notifies the owner. */
    suspend fun requestSyncByCode(code: String): Result<Unit> = runCatching {
        supabase.postgrest.rpc(
            "request_child_sync",
            buildJsonObject {
                put("p_child_id", JsonNull)
                put("p_method", JsonPrimitive("code"))
                put("p_code", JsonPrimitive(code.trim().uppercase()))
            }
        )
        Unit
    }

    /** Pending sync requests waiting on the current user's (owner) confirmation. */
    suspend fun getPendingSyncRequests(): Result<List<ChildSyncRequest>> = runCatching {
        val userId = supabase.auth.currentUserOrNull()?.id ?: error("Not logged in")
        supabase.from("child_sync_requests").select {
            filter { eq("owner_user_id", userId); eq("status", "pending") }
            order("created_at", Order.DESCENDING)
        }.decodeList<ChildSyncRequest>()
    }

    /** Owner confirms or rejects a pending sync request. Approving grants the requester member access. */
    suspend fun confirmSyncRequest(requestId: String, approve: Boolean): Result<Unit> = runCatching {
        supabase.postgrest.rpc(
            "confirm_child_sync_request",
            buildJsonObject {
                put("p_request_id", JsonPrimitive(requestId))
                put("p_approve", JsonPrimitive(approve))
            }
        )
        Unit
    }

    suspend fun updateChild(child: Child): Result<Unit> = runCatching {
        supabase.from("children").update(child) {
            filter { eq("id", child.id) }
        }
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

    /** Uploads a medical document to the PRIVATE 'medical-docs' bucket and records its metadata. */
    suspend fun uploadMedicalDocument(childId: String, fileName: String, mime: String?, bytes: ByteArray): Result<Unit> = runCatching {
        val userId = supabase.auth.currentUserOrNull()?.id ?: error("Not logged in")
        val safe = fileName.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "document" }
        val path = "$userId/$childId/${System.currentTimeMillis()}_$safe"
        supabase.storage.from("medical-docs").upload(path, bytes) { upsert = true }
        supabase.from("medical_documents").insert(buildJsonObject {
            put("child_id", JsonPrimitive(childId))
            put("file_name", JsonPrimitive(fileName))
            put("file_path", JsonPrimitive(path))
            put("mime_type", mime?.let { JsonPrimitive(it) } ?: JsonNull)
        })
    }

    suspend fun getMedicalDocuments(childId: String): Result<List<com.lullabyai.app.data.model.MedicalDocument>> = runCatching {
        supabase.from("medical_documents").select {
            filter { eq("child_id", childId) }
            order("created_at", Order.DESCENDING)
        }.decodeList()
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
