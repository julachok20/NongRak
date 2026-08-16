package com.lullabyai.app.data.repository

import com.lullabyai.app.data.remote.supabase
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * What feeding the companion did.
 *
 * @param fed false when the feed landed inside the cooldown — the meal was still recorded for the
 *   child, the mascot simply was not paid twice for it. The banner stays quiet in that case.
 */
data class BondFeed(
    val xp: Int,
    val level: Int,
    val leveledUp: Boolean,
    val fed: Boolean
)

/**
 * ความผูกพัน — the bond between a carer and their companion.
 *
 * Held per user AND per mascot, so switching companion switches to a separate relationship rather
 * than carrying one level across all four. Levels are computed by Postgres from xp and never
 * stored, so there is only ever one answer to what level a companion is.
 */
class MascotBondRepository {

    private fun parse(raw: String, leveledUp: Boolean? = null): BondFeed? {
        // feed_mascot returns a table, so PostgREST answers with a one-row array.
        val row: JsonObject = runCatching {
            Json.parseToJsonElement(raw).jsonArray.firstOrNull()?.jsonObject
        }.getOrNull() ?: return null
        fun int(key: String, fallback: Int) =
            row[key]?.jsonPrimitive?.content?.toIntOrNull() ?: fallback
        fun bool(key: String) = row[key]?.jsonPrimitive?.content?.toBoolean() ?: false
        return BondFeed(
            xp = int("xp", 0),
            level = int("level", 1),
            leveledUp = leveledUp ?: bool("leveled_up"),
            fed = bool("fed")
        )
    }

    /** Feeds the companion. Safe to call after every meal — the cooldown lives in the RPC. */
    suspend fun feed(mascot: String, xp: Int = 10): Result<BondFeed> = runCatching {
        val raw = supabase.postgrest.rpc(
            "feed_mascot",
            buildJsonObject {
                put("p_mascot", JsonPrimitive(mascot))
                put("p_xp", JsonPrimitive(xp))
            }
        ).data
        parse(raw) ?: error("feed_mascot returned no row")
    }

    /**
     * The current bond, for showing a level without feeding.
     *
     * Zero xp runs the same upsert paying nothing, so reading and creating the row are one
     * operation — a companion never has to exist before it can be looked at.
     */
    suspend fun get(mascot: String): Result<BondFeed?> = runCatching {
        val raw = supabase.postgrest.rpc(
            "feed_mascot",
            buildJsonObject {
                put("p_mascot", JsonPrimitive(mascot))
                put("p_xp", JsonPrimitive(0))
            }
        ).data
        parse(raw, leveledUp = false)
    }
}
