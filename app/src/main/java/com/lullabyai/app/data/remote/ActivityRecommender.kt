package com.lullabyai.app.data.remote

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * One card on the AI แนะนำกิจกรรมและที่เที่ยว rail.
 *
 * Serializable because the batch is cached in Postgres for three days — the same shape goes out to
 * the database and comes back, so there is no second representation to keep in step.
 */
@kotlinx.serialization.Serializable
data class ActivityRecommendation(
    val title: String,
    val reason: String,
    /**
     * `home` or `place`.
     *
     * Going out always means going out *somewhere* — a park, a mall, a meal — so `place` always
     * carries a real venue from Google Places in [placeId]. Nothing is suggested outside the home
     * that cannot be pointed at on a map.
     */
    val kind: String = KIND_HOME,
    @kotlinx.serialization.SerialName("place_id") val placeId: String? = null,
    val address: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    @kotlinx.serialization.SerialName("cost_baht") val costBaht: Int? = null,
    /**
     * The hour the model thinks this suits, chosen inside the venue's opening hours — a meal out
     * lands in the evening and a park in the morning. Only a default for the picker: the parent
     * still sets both ends before anything is saved.
     */
    @kotlinx.serialization.SerialName("start_hour") val startHour: Int? = null,
    @kotlinx.serialization.SerialName("duration_hours") val durationHours: Int? = null,
    /** Today's opening hours, verbatim from Google, so the suggested time can be judged. */
    @kotlinx.serialization.SerialName("hours_today") val hoursToday: String? = null
) {
    val isPlace: Boolean get() = kind == KIND_PLACE

    companion object {
        const val KIND_HOME = "home"
        const val KIND_PLACE = "place"
    }
}

/**
 * Coarse location only.
 *
 * Finding a playground a few kilometres away does not need a street-level fix, and coarse is a far
 * smaller thing to ask of a parent — and to declare on Play — than precise location.
 */
object CoarseLocation {

    fun granted(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * The last position the system already knows, never a fresh fix: this runs while a screen is
     * being drawn, and waiting on GPS would either block it or drain the battery for a suggestion
     * rail. Null when permission is absent or nothing has been recorded yet.
     */
    fun lastKnown(context: Context): Pair<Double, Double>? {
        if (!granted(context)) return null
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return null
        return runCatching {
            listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
                .asSequence()
                .mapNotNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }
                .maxByOrNull { it.time }
                ?.let { it.latitude to it.longitude }
        }.getOrNull()
    }
}

/**
 * Builds the recommendation rail.
 *
 * The AI is a chooser and an explainer, never a source of facts about the world. Venues come from
 * Google Places and the model may only pick from that list by id — a hallucinated address sends a
 * family somewhere that does not exist. Home activities it may invent freely, because the worst
 * case there is a dull afternoon.
 *
 * What shapes the choice: the kinds of thing this child is actually logged doing, and what is left
 * of the month's budget.
 */
object ActivityRecommender {

    /**
     * One kind of outing, searched on its own.
     *
     * Asking for every type in a single request returns whatever Places ranks highest overall, and
     * around a Thai home that is cafés, restaurants and malls — twenty of them, with the beach an
     * hour up the coast never reaching the list. The model can only choose from what it is given, so
     * telling it to "vary them" while handing it twenty restaurants changes nothing. Each group gets
     * its own request and therefore its own slots.
     */
    private data class PlaceGroup(
        /** Shown to the model as a heading, so the spread is visible in the list itself. */
        val label: String,
        val types: List<String>,
        /**
         * Retried with these when Places rejects the request. One type name it does not know fails
         * the whole call, and losing a group is better than losing every outing.
         */
        val safeTypes: List<String>,
        val radiusMetres: Double,
        val maxResults: Int = 8
    )

    private val PLACE_GROUPS = listOf(
        PlaceGroup(
            label = "ธรรมชาติและกลางแจ้ง (ทะเล สวน สนามเด็กเล่น)",
            types = listOf(
                "beach", "park", "national_park", "hiking_area",
                "botanical_garden", "playground", "water_park", "wildlife_park"
            ),
            safeTypes = listOf("park"),
            // A beach or a national park is worth a longer drive than lunch, so this reaches out
            // far enough to actually find one.
            radiusMetres = 40_000.0
        ),
        PlaceGroup(
            label = "ห้างสรรพสินค้าและร้านอาหาร",
            types = listOf("shopping_mall", "restaurant", "cafe", "market", "movie_theater"),
            safeTypes = listOf("shopping_mall", "restaurant", "cafe"),
            radiusMetres = 15_000.0
        ),
        PlaceGroup(
            label = "เรียนรู้และเที่ยวชม",
            types = listOf(
                "zoo", "aquarium", "museum", "amusement_park", "tourist_attraction", "library"
            ),
            safeTypes = listOf("zoo", "aquarium", "museum", "amusement_park", "tourist_attraction"),
            radiusMetres = 30_000.0
        )
    )

    /**
     * Real venues near the family, kept in their groups.
     *
     * The three requests run together — one after another would be three round trips a parent waits
     * through. A venue that answers two groups is listed once, under the first.
     */
    private suspend fun nearbyByGroup(
        lat: Double,
        lng: Double
    ): Map<String, List<PlaceSuggestion>> = coroutineScope {
        val found = PLACE_GROUPS.map { g ->
            async {
                // Null only on a rejected request; an area with no beach answers success-and-empty
                // and costs no retry.
                val places = PlacesClient
                    .searchNearby(lat, lng, g.types, g.radiusMetres, g.maxResults).getOrNull()
                    ?: PlacesClient
                        .searchNearby(lat, lng, g.safeTypes, g.radiusMetres, g.maxResults)
                        .getOrDefault(emptyList())
                g.label to places
            }
        }.awaitAll()

        val seen = mutableSetOf<String>()
        found.associate { (label, places) -> label to places.filter { seen.add(it.id) } }
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private const val SYSTEM = """
You suggest activities for a parent and their young child, in Thai.

Two kinds of item:
- "home"  — something to do at home. Invent freely. No place_id.
- "place" — going out somewhere together: a park, a mall, a meal out, a museum. You MUST choose one
            of the NEARBY PLACES given to you and copy its id into place_id exactly. Never name a
            venue that is not in that list. Never invent an address. If no NEARBY PLACES are given,
            produce no "place" items at all.

Rules:
- Respect the budget. If little is left, prefer free or cheap options and say so in the reason.
- Lean toward what the child already enjoys, given as activity counts.
- reason: one short Thai sentence, concrete, about why it suits THIS child. No greetings, no fluff.
- cost_baht: rough per-visit cost for the family, 0 for free. Omit if genuinely unknown.
- start_hour: the hour (0-23) this outing suits best, and it MUST fall inside the opening hours
  shown for that place. A meal out belongs in the evening, a park or a beach in the morning.
- duration_hours: how long the family would realistically stay, 1-8.

Return ONLY JSON: {"items":[{"kind":"home"|"place","title":"...","reason":"...","place_id":"...",
"cost_baht":0,"start_hour":10,"duration_hours":2}]}

Between 5 and 7 items. Favour going out: when NEARBY PLACES are given, at least four items must be
"place".

NEARBY PLACES arrive under headings. Take at most two picks from any one heading, so that every
heading with venues under it is represented — a day at the beach or a park, a named mall or a meal
out, and somewhere to learn. Four picks from one heading is wrong even if those four are the
highest-rated venues in the list.

Keep one or two "home" items for a day nobody wants to leave the house.
"""

    /**
     * @param likedKinds care-log kinds to counts — what this child is actually logged doing.
     * @param remainingBudgetBaht null when no budget is set, which the model is told plainly rather
     *   than being handed a zero it would read as "no money left".
     */
    suspend fun recommend(
        childName: String,
        likedKinds: Map<String, Int>,
        remainingBudgetBaht: Long?,
        lat: Double?,
        lng: Double?
    ): Result<List<ActivityRecommendation>> {
        // Real venues first, so the model has something true to choose from.
        val byGroup: Map<String, List<PlaceSuggestion>> =
            if (lat != null && lng != null && PlacesClient.isConfigured) nearbyByGroup(lat, lng)
            else emptyMap()
        val nearby: List<PlaceSuggestion> = byGroup.values.flatten()

        val likedText = likedKinds.entries
            .sortedByDescending { it.value }
            .take(5)
            .joinToString(", ") { "${it.key} (${it.value} ครั้ง)" }
            .ifBlank { "ยังไม่มีข้อมูล" }

        val budgetText = remainingBudgetBaht
            ?.let { "เหลืองบเดือนนี้ประมาณ $it บาท" }
            ?: "ยังไม่ได้ตั้งงบประมาณ"

        // Kept under headings: the model is asked to spread its picks across them, which it can only
        // do if it can see which venue belongs where.
        val placesText = byGroup.entries
            .filter { it.value.isNotEmpty() }
            .joinToString("\n\n") { (label, places) ->
                val rows = places.joinToString("\n") { p ->
                    val cost = p.roughCostBaht?.let { " ~$it บาท" } ?: ""
                    // The hours are given so start_hour lands while the place is actually open.
                    val hours = p.hoursToday?.let { " | เวลาเปิดวันนี้: $it" } ?: ""
                    "- id=${p.id} | ${p.name}$cost$hours"
                }
                "[$label]\n$rows"
            }
            .ifBlank { "(ไม่มีข้อมูลสถานที่ใกล้เคียง — แนะนำเฉพาะกิจกรรมที่บ้าน)" }

        val prompt = buildString {
            appendLine("ชื่อลูก: $childName")
            appendLine("กิจกรรมที่ลูกทำบ่อย: $likedText")
            appendLine(budgetText)
            appendLine()
            appendLine("NEARBY PLACES:")
            append(placesText)
        }

        val result = AiProxyClient.generate(
            prompt = prompt,
            useSearch = false,
            maxTokens = 900,
            system = SYSTEM.trim(),
            jsonMode = true
        )
        val text = result.text ?: return Result.failure(IllegalStateException("ai_no_response"))
        return Result.success(parse(text, nearby))
    }

    /**
     * Any item claiming to be a place is matched back against the list that was sent. An id the
     * model invented is dropped rather than shown — that is the whole safeguard, so it happens here
     * and not in the UI.
     */
    private fun parse(raw: String, nearby: List<PlaceSuggestion>): List<ActivityRecommendation> {
        val obj = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull()
            ?: return emptyList()
        val items = obj["items"]?.jsonArray ?: return emptyList()
        val byId = nearby.associateBy { it.id }

        return items.mapNotNull { el ->
            val o = runCatching { el.jsonObject }.getOrNull() ?: return@mapNotNull null
            val title = o["title"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            val reason = o["reason"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val kind = o["kind"]?.jsonPrimitive?.contentOrNull
            val cost = o["cost_baht"]?.jsonPrimitive?.intOrNull
            // Kept in range here rather than trusted: a 25 or a -3 would prefill a picker with a
            // time that cannot exist.
            val startHour = o["start_hour"]?.jsonPrimitive?.intOrNull?.takeIf { it in 0..23 }
            val durationHours = o["duration_hours"]?.jsonPrimitive?.intOrNull?.takeIf { it in 1..8 }

            when (kind) {
                ActivityRecommendation.KIND_PLACE -> {
                    val placeId = o["place_id"]?.jsonPrimitive?.contentOrNull
                    // An invented venue is dropped: only ids that came from Places survive.
                    val place = byId[placeId] ?: return@mapNotNull null
                    ActivityRecommendation(
                        // The venue's real name wins over whatever the model typed.
                        title = place.name,
                        reason = reason,
                        kind = ActivityRecommendation.KIND_PLACE,
                        placeId = place.id,
                        address = place.address,
                        lat = place.lat,
                        lng = place.lng,
                        costBaht = cost ?: place.roughCostBaht,
                        startHour = startHour,
                        durationHours = durationHours,
                        // Google's own wording wins here too — the model is not asked to retype it.
                        hoursToday = place.hoursToday
                    )
                }
                else -> ActivityRecommendation(
                    title = title,
                    reason = reason,
                    kind = ActivityRecommendation.KIND_HOME,
                    costBaht = cost,
                    startHour = startHour,
                    durationHours = durationHours
                )
            }
        }
    }
}
