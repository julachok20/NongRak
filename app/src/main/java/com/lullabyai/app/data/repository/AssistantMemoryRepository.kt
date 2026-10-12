package com.lullabyai.app.data.repository

import com.lullabyai.app.data.remote.ClaudeClient
import com.lullabyai.app.data.remote.supabase
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/** Where a remembered fact came from. Recorded on every row and never guessed at read time. */
enum class MemorySource {
    /** The parent told the assistant directly. */
    SAID,

    /** Derived from a row the parent entered herself. */
    LOGGED,

    /** The model worked it out. Must never be replayed as something she said. */
    INFERRED;

    val wire: String get() = name.lowercase()

    companion object {
        fun fromWire(value: String?): MemorySource =
            entries.firstOrNull { it.wire == value?.trim()?.lowercase() } ?: INFERRED
    }
}

@Serializable
data class AssistantMemoryDto(
    val id: String = "",
    @SerialName("family_id") val familyId: String = "",
    @SerialName("child_id") val childId: String? = null,
    val key: String = "",
    val value: String = "",
    val source: String = "inferred",
    val confidence: Float = 0.5f,
    val muted: Boolean = false,
    @SerialName("updated_at") val updatedAt: String? = null
)

/** One remembered fact, as the memory screen and the prompt builder see it. */
data class MemoryFact(
    val id: String,
    val childId: String?,
    val key: String,
    val value: String,
    val source: MemorySource,
    val confidence: Float,
    val muted: Boolean
)

/**
 * Tier 2 of the assistant memory: what น้องรัก knows about this family between conversations.
 *
 * Three rules this class exists to hold, all load-bearing:
 *
 *  * **Family-keyed, never session-keyed.** The app chat, the voice call, LINE and the home card
 *    read the same rows. Four surfaces with four memories is four bots wearing one mascot.
 *  * **Provenance travels with the fact.** An INFERRED fact is rendered differently from a SAID one
 *    everywhere it appears, including in the prompt. Replaying a guess as something the parent told
 *    you is the fastest way to lose her trust, and it does not come back.
 *  * **Muted means muted.** A topic she asked it to drop is a stored row, not a sentence in a prompt
 *    that the next model call forgets.
 */
class AssistantMemoryRepository {

    private fun me(): String? = runCatching { supabase.auth.currentUserOrNull()?.id }.getOrNull()

    /**
     * Everything remembered about this family, newest first.
     *
     * [childId] null returns the whole family memory, including facts about every child, which is
     * what the memory screen shows. Pass a child to narrow it for a prompt.
     */
    suspend fun recall(
        childId: String? = null,
        includeMuted: Boolean = false
    ): Result<List<MemoryFact>> = withContext(Dispatchers.IO) {
        runCatching {
            val familyId = me() ?: return@runCatching emptyList()
            supabase.from("assistant_memory")
                .select {
                    filter {
                        eq("family_id", familyId)
                        if (!includeMuted) eq("muted", false)
                    }
                    order("updated_at", Order.DESCENDING)
                    limit(200)
                }
                .decodeList<AssistantMemoryDto>()
                .filter { childId == null || it.childId == null || it.childId == childId }
                .map { it.toFact() }
        }
    }

    /**
     * The block of remembered facts to put in front of a model.
     *
     * Inferred facts are labelled as guesses IN THE PROMPT, not only in the UI, so the assistant
     * hedges them in its own words instead of stating them back as established. Returns an empty
     * string when nothing is remembered: an empty "what I know" heading invites invention.
     */
    suspend fun promptBlock(childId: String? = null, isThai: Boolean = true): String {
        val facts = recall(childId).getOrNull().orEmpty()
        if (facts.isEmpty()) return ""
        val header = if (isThai) "สิ่งที่จำได้เกี่ยวกับครอบครัวนี้:"
                     else "What you remember about this family:"
        val hedge = if (isThai) " (เป็นการคาดเดา ยังไม่ได้ยืนยัน)" else " (your own guess, unconfirmed)"
        return buildString {
            appendLine(header)
            facts.take(40).forEach { f ->
                append("- ").append(f.value)
                if (f.source == MemorySource.INFERRED) append(hedge)
                appendLine()
            }
        }
    }

    /**
     * Writes facts, updating rather than duplicating.
     *
     * The unique index is (family_id, child_id, key), so re-learning something overwrites it. That
     * is deliberate: a family whose routine has changed should not end up holding two contradictory
     * rows with no way to tell which one is current.
     */
    suspend fun remember(facts: List<MemoryFact>): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val familyId = me() ?: error("memory_requires_sign_in")
            val valid = facts.filter { it.key.matches(KEY_SHAPE) && it.value.isNotBlank() }
            if (valid.isEmpty()) return@runCatching 0

            // Never overwrite a muted row. She turned that topic off; learning it again is not
            // consent to start mentioning it again.
            val mutedKeys = recall(includeMuted = true).getOrNull()
                .orEmpty().filter { it.muted }.map { it.childId to it.key }.toSet()

            val rows = valid
                .filterNot { (it.childId to it.key) in mutedKeys }
                .map { fact ->
                    buildJsonObject {
                        put("family_id", JsonPrimitive(familyId))
                        fact.childId?.let { put("child_id", JsonPrimitive(it)) }
                        put("key", JsonPrimitive(fact.key))
                        put("value", JsonPrimitive(fact.value.take(500)))
                        put("source", JsonPrimitive(fact.source.wire))
                        put("confidence", JsonPrimitive(fact.confidence.coerceIn(0f, 1f)))
                    }
                }
            if (rows.isEmpty()) return@runCatching 0
            supabase.from("assistant_memory").upsert(rows) { onConflict = "family_id,child_id,key" }
            rows.size
        }
    }

    /** Forget one fact. A real delete: the memory screen promises that and has to mean it. */
    suspend fun forget(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            supabase.from("assistant_memory").delete { filter { eq("id", id) } }
            Unit
        }
    }

    /** Stop using a fact without losing it, so it is never silently learned again. */
    suspend fun setMuted(id: String, muted: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            supabase.from("assistant_memory").update(
                buildJsonObject { put("muted", JsonPrimitive(muted)) }
            ) { filter { eq("id", id) } }
            Unit
        }
    }

    /**
     * Reads a conversation and writes back what is worth keeping.
     *
     * Deliberately conservative. The prompt forbids three things outright, and each is a real
     * failure this app must avoid rather than a style preference:
     *
     *  * **No medical conclusions.** "ลูกเป็นหวัด" is a diagnosis, and remembering it would let the
     *    assistant repeat a diagnosis that nobody qualified ever made.
     *  * **No passing events.** Today's temperature belongs in the health record. Memory is for what
     *    is still true next month.
     *  * **No invention.** A fact absent from the conversation must not appear, however likely.
     *
     * Runs on the normal chain, so Gemma answers first and the fallbacks cover an outage. Failure is
     * harmless - the conversation already happened and only a little memory is lost - so this never
     * surfaces an error to the parent.
     */
    suspend fun extractFrom(
        conversation: String,
        childId: String?,
        isThai: Boolean = true
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            if (conversation.length < MIN_CONVERSATION_CHARS) return@runCatching 0

            val prompt = buildString {
                appendLine(
                    if (isThai)
                        "อ่านบทสนทนาระหว่างผู้ปกครองกับผู้ช่วย แล้วดึงเฉพาะข้อเท็จจริงที่ยังจริงในเดือนหน้า"
                    else
                        "Read this conversation between a parent and an assistant. Extract only facts that will still be true next month."
                )
                appendLine()
                appendLine(
                    if (isThai)
                        "ห้ามเด็ดขาด: ห้ามบันทึกการวินิจฉัยหรือข้อสรุปทางการแพทย์ " +
                            "ห้ามบันทึกเหตุการณ์ชั่วคราว เช่น อุณหภูมิวันนี้หรืออาการวันนี้ " +
                            "ห้ามแต่งข้อมูลที่ไม่ได้อยู่ในบทสนทนา ถ้าไม่มีอะไรน่าจำ ให้ตอบ []"
                    else
                        "Never: no diagnoses or medical conclusions; no passing events such as today's " +
                            "temperature or symptoms; nothing not actually present in the conversation. " +
                            "If there is nothing worth remembering, answer []."
                )
                appendLine()
                appendLine(
                    "Answer with a JSON array only. Each item: " +
                        "{\"key\": short_snake_case_slug, \"value\": one short sentence, " +
                        "\"source\": \"said\" if the parent stated it or \"inferred\" if you worked it out, " +
                        "\"confidence\": 0.0-1.0}. At most 5 items."
                )
                appendLine()
                appendLine(conversation.take(6000))
            }

            val raw = ClaudeClient.generateContent(
                prompt = prompt,
                useSearch = false,
                maxTokens = 600,
                system = null,
                jsonMode = true
            )

            val parsed = runCatching {
                Json { ignoreUnknownKeys = true }
                    .decodeFromString<List<ExtractedFact>>(extractArray(raw))
            }.getOrNull().orEmpty()

            if (parsed.isEmpty()) return@runCatching 0

            remember(
                parsed.take(5).map { f ->
                    MemoryFact(
                        id = "",
                        childId = childId,
                        key = f.key.trim().lowercase().replace(NON_KEY_CHARS, "_").take(48),
                        value = f.value.trim(),
                        // The model proposes a source, and anything it did not clearly attribute to
                        // the parent reads as a guess. Erring toward INFERRED is the safe direction:
                        // a hedged true fact costs nothing, a guess stated as fact costs trust.
                        source = MemorySource.fromWire(f.source),
                        confidence = f.confidence ?: 0.5f,
                        muted = false
                    )
                }
            ).getOrDefault(0)
        }
    }

    @Serializable
    private data class ExtractedFact(
        val key: String,
        val value: String,
        val source: String? = null,
        val confidence: Float? = null
    )

    private fun AssistantMemoryDto.toFact() = MemoryFact(
        id = id,
        childId = childId,
        key = key,
        value = value,
        source = MemorySource.fromWire(source),
        confidence = confidence,
        muted = muted
    )

    companion object {
        private val KEY_SHAPE = Regex("^[a-z0-9_]{2,48}$")
        private val NON_KEY_CHARS = Regex("[^a-z0-9_]")

        /** Below this, a conversation is a greeting and holds nothing worth keeping. */
        private const val MIN_CONVERSATION_CHARS = 120

        /**
         * Pulls the JSON array out of a model reply.
         *
         * Models wrap arrays in prose or fences even when told not to, and a failed parse silently
         * costs the family a memory, so this is worth the few lines.
         */
        fun extractArray(raw: String): String {
            val cleaned = raw.trim()
                .removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val start = cleaned.indexOf('[')
            val end = cleaned.lastIndexOf(']')
            return if (start >= 0 && end > start) cleaned.substring(start, end + 1) else "[]"
        }
    }
}
