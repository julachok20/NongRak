package com.lullabyai.app.data.repository

import com.lullabyai.app.data.remote.supabase
import com.lullabyai.app.ui.mascot.MascotWardrobe
import com.lullabyai.app.ui.mascot.WardrobeSlot
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.contentOrNull
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
 * A companion's bond, read without touching it.
 *
 * Defaults describe a companion that has never been fed or dressed — a real state, since the row
 * is only created on the first feed or equip.
 */
data class MascotBond(
    val mascot: String,
    val xp: Int = 0,
    val level: Int = 1,
    val fedCount: Int = 0,
    val equipped: Map<String, String> = emptyMap()
)

/** The care milestones the server prices, in mascot_xp_kinds. */
enum class XpKind(val key: String) {
    CHILD_ADDED("child_added"),
    VACCINE_COMPLETED("vaccine_completed"),
    DOCTOR_VISIT("doctor_visit"),
    ILLNESS_RECOVERED("illness_recovered"),
    MILESTONE_ACHIEVED("milestone_achieved"),
    GROWTH_RECORDED("growth_recorded"),
    DAILY_CARE("daily_care")
}

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
     * Uses get_mascot_bond, NOT feed_mascot(p_xp = 0). feed_mascot only skips its write while
     * inside the 20-minute cooldown; outside it a zero-xp call still stamped last_fed_at and
     * bumped fed_count, so merely opening the companion screen ate the next real feed.
     *
     * A companion with no row yet is a real state, not an error: it reads as a fresh level 1
     * wearing nothing, and the row appears the first time it is fed or dressed.
     */
    suspend fun get(mascot: String): Result<MascotBond> = runCatching {
        val raw = supabase.postgrest.rpc(
            "get_mascot_bond",
            buildJsonObject { put("p_mascot", JsonPrimitive(mascot)) }
        ).data
        val row = runCatching {
            Json.parseToJsonElement(raw).jsonArray.firstOrNull()?.jsonObject
        }.getOrNull() ?: return@runCatching MascotBond(mascot)

        fun int(key: String, fallback: Int) =
            row[key]?.jsonPrimitive?.content?.toIntOrNull() ?: fallback

        MascotBond(
            mascot = mascot,
            xp = int("xp", 0),
            level = int("level", 1),
            fedCount = int("fed_count", 0),
            equipped = (row["equipped"] as? JsonObject).orEmpty()
        )
    }

    /**
     * Dresses the companion.
     *
     * The level check is here as well as in the closet UI because equipping is also reachable
     * from a stale screen: a companion switched underneath, or an item that was unlocked when
     * the grid was drawn and is not for THIS companion. Passing an item the bond has not earned
     * is a caller bug, and naming it beats silently dressing the mascot in nothing.
     */
    suspend fun equip(mascot: String, slot: WardrobeSlot, itemId: String?): Result<Map<String, String>> =
        runCatching {
            val bond = get(mascot).getOrThrow()
            val next = bond.equipped.toMutableMap()
            if (itemId == null) {
                next.remove(slot.name.lowercase())
            } else {
                val item = MascotWardrobe.byId(itemId)
                    ?: error("equip: unknown item '$itemId'")
                require(item.slot == slot) {
                    "equip: '$itemId' is a ${item.slot}, not a $slot"
                }
                require(item.unlockedAt(bond.level)) {
                    "equip: '$itemId' needs level ${item.requiredLevel}, $mascot is level ${bond.level}"
                }
                next[slot.name.lowercase()] = itemId
            }

            supabase.postgrest.rpc(
                "set_mascot_equipped",
                buildJsonObject {
                    put("p_mascot", JsonPrimitive(mascot))
                    put("p_equipped", buildJsonObject { next.forEach { (k, v) -> put(k, JsonPrimitive(v)) } })
                }
            )
            next.toMap()
        }

    /**
     * Records a care milestone against the companion.
     *
     * Safe to call every time the thing is observed rather than only when it first happens: the
     * RPC is idempotent per (user, mascot, kind, refId), so a retry, a re-sync or a second tap
     * counts once. [BondFeed.fed] is false when it had already been counted.
     *
     * @param refId the row the milestone is about — a vaccine id, a growth id, or a date for
     *   daily care. It is the idempotency key, so it must identify the event, not the moment.
     */
    suspend fun award(mascot: String, kind: XpKind, refId: String): Result<BondFeed> = runCatching {
        val raw = supabase.postgrest.rpc(
            "award_mascot_xp",
            buildJsonObject {
                put("p_mascot", JsonPrimitive(mascot))
                put("p_kind", JsonPrimitive(kind.key))
                put("p_ref_id", JsonPrimitive(refId))
            }
        ).data
        // award_mascot_xp names the field "awarded"; feed_mascot names it "fed". Same meaning:
        // did this call actually add anything.
        val row = Json.parseToJsonElement(raw).jsonArray.firstOrNull()?.jsonObject
            ?: error("award_mascot_xp returned no row")
        fun int(key: String, fallback: Int) =
            row[key]?.jsonPrimitive?.content?.toIntOrNull() ?: fallback
        BondFeed(
            xp = int("xp", 0),
            level = int("level", 1),
            leveledUp = row["leveled_up"]?.jsonPrimitive?.content?.toBoolean() ?: false,
            fed = row["awarded"]?.jsonPrimitive?.content?.toBoolean() ?: false
        )
    }
}

private fun JsonObject?.orEmpty(): Map<String, String> =
    this?.mapNotNull { (k, v) -> (v as? JsonPrimitive)?.contentOrNull?.let { k to it } }?.toMap()
        ?: emptyMap()
