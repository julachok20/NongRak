package com.lullabyai.app.data.remote

import com.lullabyai.app.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** A place the parent can pick, from Google Places. */
data class PlaceSuggestion(
    val id: String,
    val name: String,
    val address: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    /** Google's own bracket, e.g. PRICE_LEVEL_MODERATE. Null when Google has no view. */
    val priceLevel: String? = null,
    val rating: Double? = null,
    /**
     * Today's opening hours as Google already phrased them, e.g. "จันทร์: 10:00 – 22:00".
     *
     * Google localises this string for the requested language, so it is shown as-is rather than
     * rebuilt from the raw periods — reformatting it here would only reintroduce the day-name and
     * "open 24 hours" cases Google has already handled.
     */
    val hoursToday: String? = null,
    val openNow: Boolean? = null
) {
    /** A rough per-person cost in baht for the estimate field. Null when Google says nothing. */
    val roughCostBaht: Int?
        get() = when (priceLevel) {
            "PRICE_LEVEL_FREE" -> 0
            "PRICE_LEVEL_INEXPENSIVE" -> 150
            "PRICE_LEVEL_MODERATE" -> 400
            "PRICE_LEVEL_EXPENSIVE" -> 900
            "PRICE_LEVEL_VERY_EXPENSIVE" -> 2000
            else -> null
        }
}

/**
 * Google Places (New). Two jobs: letting a parent pick a real สถานที่ when adding an activity, and
 * finding actual nearby venues for the AI to recommend.
 *
 * The AI is never asked to invent a place name. It chooses among and explains places that came from
 * here — a fabricated address sends a family somewhere that does not exist, which is a different
 * class of failure from a clumsy suggestion.
 */
object PlacesClient {

    /** False when no key is configured, so callers can say so rather than show an empty list. */
    val isConfigured: Boolean get() = BuildConfig.MAPS_API_KEY.isNotBlank()

    private const val BASE = "https://places.googleapis.com/v1"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val http by lazy {
        HttpClient(OkHttp) {
            install(ContentNegotiation) { json(json) }
            install(HttpTimeout) {
                requestTimeoutMillis = 15_000
                connectTimeoutMillis = 10_000
            }
        }
    }

    /**
     * Only the fields actually rendered. Places bills by field mask, so asking for everything costs
     * more per call for data nothing displays.
     */
    private const val FIELDS =
        "places.id,places.displayName,places.formattedAddress,places.location," +
            "places.priceLevel,places.rating,places.regularOpeningHours"

    /** Free-text lookup for the place field — "สวนน้ำ", "พิพิธภัณฑ์เด็ก". */
    suspend fun searchText(
        query: String,
        lat: Double? = null,
        lng: Double? = null,
        languageCode: String = "th"
    ): Result<List<PlaceSuggestion>> {
        if (!isConfigured) return Result.failure(IllegalStateException("places_not_configured"))
        if (query.isBlank()) return Result.success(emptyList())
        val body = buildJsonObject {
            put("textQuery", query)
            put("languageCode", languageCode)
            put("maxResultCount", 10)
            // Bias, not restrict: someone searching a named venue should still find it even when
            // looking from another province.
            if (lat != null && lng != null) {
                put("locationBias", buildJsonObject {
                    put("circle", buildJsonObject {
                        put("center", buildJsonObject {
                            put("latitude", lat); put("longitude", lng)
                        })
                        put("radius", 30_000.0)
                    })
                })
            }
        }
        return call("$BASE/places:searchText", body)
    }

    /**
     * Real venues around the family, for the recommendation rail. [includedTypes] are Places type
     * names such as `amusement_park`, `zoo`, `museum`, `park`.
     */
    suspend fun searchNearby(
        lat: Double,
        lng: Double,
        includedTypes: List<String>,
        radiusMetres: Double = 15_000.0,
        // A wider shortlist so the model has enough variety to pick several different kinds of
        // outing rather than whichever few came back first.
        maxResults: Int = 20,
        languageCode: String = "th"
    ): Result<List<PlaceSuggestion>> {
        if (!isConfigured) return Result.failure(IllegalStateException("places_not_configured"))
        val body = buildJsonObject {
            put("languageCode", languageCode)
            put("maxResultCount", maxResults)
            put("includedTypes", buildJsonArray { includedTypes.forEach { add(JsonPrimitive(it)) } })
            put("locationRestriction", buildJsonObject {
                put("circle", buildJsonObject {
                    put("center", buildJsonObject { put("latitude", lat); put("longitude", lng) })
                    put("radius", radiusMetres)
                })
            })
        }
        return call("$BASE/places:searchNearby", body)
    }

    private suspend fun call(url: String, body: JsonObject): Result<List<PlaceSuggestion>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val text = http.post(url) {
                    contentType(ContentType.Application.Json)
                    header("X-Goog-Api-Key", BuildConfig.MAPS_API_KEY)
                    header("X-Goog-FieldMask", FIELDS)
                    setBody(body)
                }.bodyAsText()
                parse(text)
            }
        }

    /**
     * Hand-parsed rather than mapped to data classes: Places nests display names and coordinates a
     * couple of levels down, and a missing branch should drop one field, not fail the whole list.
     */
    private fun parse(raw: String): List<PlaceSuggestion> {
        val root = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull()
            ?: return emptyList()
        // A rejected request (an unknown type name, a key without Places enabled) answers 200-shaped
        // JSON carrying an `error` object. Surfaced as a failure so the caller can fall back instead
        // of reading it as "there is nothing near this family".
        root["error"]?.jsonObject?.let { e ->
            val message = e["message"]?.jsonPrimitive?.contentOrNull ?: "places_request_rejected"
            throw IllegalStateException(message)
        }
        val places = root["places"]?.jsonArray ?: return emptyList()
        // Places numbers weekdayDescriptions from Monday; DayOfWeek does too, from 1.
        val todayIndex = java.time.LocalDate.now().dayOfWeek.value - 1
        return places.mapNotNull { el ->
            val o = runCatching { el.jsonObject }.getOrNull() ?: return@mapNotNull null
            val id = o["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val name = o["displayName"]?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull
                ?: return@mapNotNull null
            val loc = o["location"]?.jsonObject
            val hours = o["regularOpeningHours"]?.jsonObject
            PlaceSuggestion(
                id = id,
                name = name,
                address = o["formattedAddress"]?.jsonPrimitive?.contentOrNull,
                lat = loc?.get("latitude")?.jsonPrimitive?.doubleOrNull,
                lng = loc?.get("longitude")?.jsonPrimitive?.doubleOrNull,
                priceLevel = o["priceLevel"]?.jsonPrimitive?.contentOrNull,
                rating = o["rating"]?.jsonPrimitive?.doubleOrNull,
                hoursToday = hours?.get("weekdayDescriptions")?.jsonArray
                    ?.getOrNull(todayIndex)?.jsonPrimitive?.contentOrNull,
                openNow = hours?.get("openNow")?.jsonPrimitive?.booleanOrNull
            )
        }
    }
}
