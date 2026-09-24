package com.lullabyai.app.util

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Daily targets from ปริมาณสารอาหารอ้างอิงที่ควรได้รับประจำวันสำหรับคนไทย พ.ศ. 2563 (Thai DRI 2020),
 * bundled as assets/nutrition/dri_th_2563.json — the same way [GrowthReference] bundles WHO.
 *
 * Carbohydrate and fat have no gram figure in the DRI; the book gives them as a share of energy
 * (carbohydrate 45-65 %, fat 35-40 % under two years and 25-35 % after), so their gram ranges are
 * derived from the child's energy target here, not invented.
 */
object DriReference {

    data class Targets(
        val groupId: String,
        val labelTh: String,
        val labelEn: String,
        /** 0-5 months: the book lists breast milk instead of figures. */
        val breastMilkCovers: Boolean,
        val energyKcal: Int?,
        val proteinG: Double?,
        val carbG: ClosedFloatingPointRange<Double>?,
        val fatG: ClosedFloatingPointRange<Double>?,
        /** Single-value targets keyed like the food table (calcium, iron, zinc, vitaminA, ...). */
        val values: Map<String, Double>,
        /** Range targets (sodium, potassium, chloride): below min is low, above max is too much. */
        val ranges: Map<String, ClosedFloatingPointRange<Double>>,
        /** Keys whose figure is an Adequate Intake (the book's *), not an RDA. */
        val adequateIntake: Set<String>
    )

    private class Group(
        val id: String, val labelTh: String, val labelEn: String,
        val ages: IntRange, val sex: Int?, val breastMilkCovers: Boolean,
        val energy: Map<Int, Double>, val protein: Map<Int, Double>,
        val values: Map<String, Double>, val ranges: Map<String, ClosedFloatingPointRange<Double>>,
        val adequate: Set<String>
    )

    private class Amdr(val carbPct: ClosedFloatingPointRange<Double>, val fatPct: List<Pair<IntRange, ClosedFloatingPointRange<Double>>>)

    @Volatile private var groups: List<Group> = emptyList()
    @Volatile private var amdr: Amdr? = null

    private val META = setOf(
        "id", "labelTh", "labelEn", "ageMonths", "sex", "breastMilkCovers", "energyKcal", "proteinG",
        "referenceWeightKg", "referenceHeightCm", "adequateIntake"
    )

    fun load(context: android.content.Context) {
        if (groups.isNotEmpty()) return
        synchronized(this) {
            if (groups.isNotEmpty()) return
            install(context.assets.open("nutrition/dri_th_2563.json").bufferedReader().use { it.readText() })
        }
    }

    /** Tests (plain JVM, no assets): install the same file read from disk. */
    fun install(json: String) {
        val root = Json.parseToJsonElement(json).jsonObject
        val a = root.getValue("amdr").jsonObject
        amdr = Amdr(
            carbPct = a.getValue("carbohydratePctOfEnergy").range(),
            fatPct = a.getValue("fatPctOfEnergyByAgeMonths").jsonArray.map {
                val o = it.jsonObject
                o.getValue("ageMonths").intRange() to o.getValue("pct").range()
            }
        )
        groups = root.getValue("groups").jsonArray.map { g ->
            val o = g.jsonObject
            val values = mutableMapOf<String, Double>()
            val ranges = mutableMapOf<String, ClosedFloatingPointRange<Double>>()
            o.filterKeys { it !in META && !it.endsWith("Note") }.forEach { (k, v) ->
                when (v) {
                    is JsonArray -> ranges[k] = v.range()
                    is JsonPrimitive -> v.doubleOrNull?.let { values[k] = it }
                    else -> Unit
                }
            }
            Group(
                id = o.str("id"), labelTh = o.str("labelTh"), labelEn = o.str("labelEn"),
                ages = o.getValue("ageMonths").intRange(),
                sex = when (o["sex"]?.jsonPrimitive?.content) { "male" -> 1; "female" -> 2; else -> null },
                breastMilkCovers = o["breastMilkCovers"]?.jsonPrimitive?.booleanOrNull ?: false,
                energy = o.getValue("energyKcal").bySex(), protein = o.getValue("proteinG").bySex(),
                values = values, ranges = ranges,
                adequate = o["adequateIntake"]?.jsonArray?.map { it.jsonPrimitive.content }?.toSet().orEmpty()
            )
        }
    }

    private fun ensure() {
        if (groups.isEmpty()) load(com.lullabyai.app.NongRakApp.instance)
    }

    /** Targets for a child of [ageMonths] and [gender], or null outside 0-12 years. Sex not set
     *  averages the boys' and girls' figures (same convention as [GrowthReference]). */
    fun forChild(ageMonths: Int, gender: String?): Targets? {
        ensure()
        val sex = sexOf(gender)
        val atAge = groups.filter { ageMonths in it.ages }
        if (atAge.isEmpty()) return null
        // 9-12 y is split into a boys' and a girls' group; below that one group carries both sexes.
        val pick = atAge.filter { it.sex == null || sex == null || it.sex == sex }
        fun avg(f: (Group) -> Double?): Double? = pick.mapNotNull(f).takeIf { it.isNotEmpty() }?.average()
        fun bySex(m: (Group) -> Map<Int, Double>): Double? =
            avg { g -> if (sex != null) m(g)[sex] else m(g).values.takeIf { it.isNotEmpty() }?.average() }

        val first = pick.first()
        val energy = bySex { it.energy }
        val keys = pick.flatMap { it.values.keys }.toSet()
        val rangeKeys = pick.flatMap { it.ranges.keys }.toSet()
        val a = amdr ?: return null
        val fatPct = a.fatPct.firstOrNull { ageMonths in it.first }?.second
        return Targets(
            groupId = if (pick.size == 1) first.id else first.id.substringBeforeLast('-'),
            labelTh = if (pick.size == 1) first.labelTh else "เด็ก 9-12 ปี",
            labelEn = if (pick.size == 1) first.labelEn else "Child 9-12 years",
            breastMilkCovers = first.breastMilkCovers,
            energyKcal = energy?.let { kotlin.math.round(it).toInt() },
            proteinG = bySex { it.protein },
            carbG = energy?.let { e -> (e * a.carbPct.start / 100 / 4)..(e * a.carbPct.endInclusive / 100 / 4) },
            fatG = if (energy != null && fatPct != null) (energy * fatPct.start / 100 / 9)..(energy * fatPct.endInclusive / 100 / 9) else null,
            values = keys.associateWith { k -> avg { it.values[k] } ?: 0.0 },
            ranges = rangeKeys.associateWith { k ->
                val rs = pick.mapNotNull { it.ranges[k] }
                rs.map { it.start }.average()..rs.map { it.endInclusive }.average()
            },
            adequateIntake = pick.flatMap { it.adequate }.toSet()
        )
    }

    private fun sexOf(gender: String?): Int? = when (gender?.trim()?.lowercase()) {
        "male", "boy", "m", "ชาย" -> 1
        "female", "girl", "f", "หญิง" -> 2
        else -> null
    }

    private fun JsonObject.str(k: String) = getValue(k).jsonPrimitive.content

    private fun JsonElement.range(): ClosedFloatingPointRange<Double> {
        val a = jsonArray.map { it.jsonPrimitive.doubleOrNull ?: 0.0 }
        return a[0]..a[1]
    }

    private fun JsonElement.intRange(): IntRange {
        val a = jsonArray.map { it.jsonPrimitive.content.toDouble().toInt() }
        return a[0]..a[1]
    }

    private fun JsonElement.bySex(): Map<Int, Double> {
        val o = jsonObject
        return buildMap {
            o["male"]?.jsonPrimitive?.doubleOrNull?.let { put(1, it) }
            o["female"]?.jsonPrimitive?.doubleOrNull?.let { put(2, it) }
        }
    }
}
