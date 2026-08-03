package com.lullabyai.app.data

/**
 * The seven age buckets the Vaccine Book dropdown offers, exactly as the user specified (#7):
 * แรกเกิด / 6 เดือนแรก / 1 ปี / 1 ปี 6 เดือน / 2 ปี / 2 ปี 6 เดือน / 3 ปี
 *
 * ⚠️ NEEDS MEDICAL REVIEW — these buckets are COARSER than the Thai NIP schedule already encoded in
 * [VaccineSetupCatalog], which has doses at 2, 4, 6 and **9** months. The user's list has no 9-month
 * bucket, so buckets are treated as CUMULATIVE WINDOWS: a bucket holds every dose due after the
 * previous bucket's ceiling, up to and including its own. Consequences worth checking:
 *   - the 2 / 4 / 6-month doses all land in "6 เดือนแรก"  (intended — the label means "first 6 months")
 *   - **MMR เข็มที่ 1 (9 เดือน) lands in "1 ปี"** because there is no 9-month bucket
 * Every row still renders its true recommended age, so nothing is hidden. If MMR-1 belongs elsewhere,
 * change [BUCKETS] ceilings only — nothing else depends on the placement.
 *
 * ponytail: derives everything from VaccineSetupCatalog rather than restating the schedule. A second
 * hand-written copy of a medical schedule is a correctness trap the moment one of them is edited.
 */
object VaccineAgeBuckets {

    /**
     * @param label     the exact Thai label the user asked for
     * @param maxMonths inclusive ceiling; a dose belongs to the first bucket whose ceiling it fits
     */
    data class Bucket(val label: String, val labelEn: String, val maxMonths: Int)

    /** Order matters — this is the dropdown order the user listed. */
    val BUCKETS: List<Bucket> = listOf(
        Bucket("แรกเกิด", "Newborn", 0),
        Bucket("6 เดือนแรก", "First 6 months", 6),
        Bucket("1 ปี", "1 year", 12),
        Bucket("1 ปี 6 เดือน", "1 year 6 months", 18),
        Bucket("2 ปี", "2 years", 24),
        Bucket("2 ปี 6 เดือน", "2 years 6 months", 30),
        Bucket("3 ปี", "3 years", 36)
    )

    /** The bucket a dose belongs to, by its recommended age in months. */
    fun bucketForMonths(ageMonths: Int): Bucket =
        BUCKETS.firstOrNull { ageMonths <= it.maxMonths } ?: BUCKETS.last()

    /**
     * Multi-dose series, declared EXPLICITLY rather than parsed out of dose names.
     *
     * ⚠️ NEEDS MEDICAL REVIEW — two judgement calls are baked in here:
     *   1. "DTP กระตุ้น" (18m booster) is treated as continuing the DTP-HB-Hib primary series even
     *      though the product name differs. Same for "OPV กระตุ้น".
     *   2. Rota is listed as a 3-dose series; some Rota products are 2-dose. VaccineSetupCatalog's
     *      own row says "ถ้าใช้ชนิด 3 ครั้ง" (if using the 3-dose type), so a 2-dose product would
     *      show one dose too many here.
     * String-parsing the dose numbers would have been shorter but silently wrong on both counts.
     */
    private val SERIES: Map<String, List<String>> = mapOf(
        "DTP-HB-Hib" to listOf(
            "DTP-HB-Hib เข็มที่ 1", "DTP-HB-Hib เข็มที่ 2", "DTP-HB-Hib เข็มที่ 3", "DTP กระตุ้น"
        ),
        "OPV" to listOf("OPV เข็มที่ 1", "OPV เข็มที่ 2", "OPV เข็มที่ 3", "OPV กระตุ้น"),
        "Rota" to listOf("Rota เข็มที่ 1", "Rota เข็มที่ 2", "Rota เข็มที่ 3"),
        "MMR" to listOf("MMR เข็มที่ 1", "MMR เข็มที่ 2"),
        "JE" to listOf("JE เข็มที่ 1", "JE เข็มที่ 2")
    )

    /** Recommended age in months for a dose, from the shared NIP catalogue. */
    fun ageMonthsOf(dbName: String): Int? =
        VaccineSetupCatalog.SCHEDULE.firstOrNull { it.dbName == dbName }?.ageMonths

    /** Where a dose sits in its multi-dose course, for THIS child's actual record. */
    sealed interface CourseStatus {
        /** Single-dose vaccine — there is no course to finish. */
        data object NotASeries : CourseStatus
        /** Every dose of the course is already completed — say so, don't ask for more (#9). */
        data object Complete : CourseStatus
        /**
         * [dose] is the earliest dose of the course still outstanding.
         * [monthsAway] is 0 when the outstanding dose is not later than the one being shown
         * (out-of-order records, or a dose missing from the catalogue), so callers must not
         * print "in 0 months" — omit the figure instead.
         */
        data class Outstanding(val dose: String, val monthsAway: Int) : CourseStatus
    }

    /**
     * Course status of [dbName] given every vaccine name this child has already completed.
     *
     * This is what lets the book say "this one isn't finished — come back in about N months"
     * (the user's "some vaccine that when process you need to come back to have more like in
     * 4 months") WITHOUT nagging for a dose the child has already had: it looks at the whole
     * series against the child's real record, not just the one dose next in line (#9 —
     * "if vaccine already have a full set ... just show you have a full set").
     *
     * @param completed the `vaccine_name` of every record whose status is "completed".
     */
    fun courseStatus(dbName: String, completed: Set<String>): CourseStatus {
        val series = SERIES.values.firstOrNull { dbName in it } ?: return CourseStatus.NotASeries
        val next = series.firstOrNull { it !in completed } ?: return CourseStatus.Complete
        val gap = ageMonthsOf(next)?.let { n -> ageMonthsOf(dbName)?.let { (n - it).coerceAtLeast(0) } } ?: 0
        return CourseStatus.Outstanding(next, gap)
    }

    /** True when the vaccine is one dose of a multi-dose course (used to show the series badge). */
    fun isPartOfSeries(dbName: String): Boolean = SERIES.values.any { dbName in it }

    /** Human label for a dose's recommended age, shown on every row so the bucket never hides it. */
    fun ageLabel(ageMonths: Int, isThai: Boolean): String = when {
        ageMonths == 0 -> if (isThai) "แรกเกิด" else "at birth"
        ageMonths < 12 -> if (isThai) "$ageMonths เดือน" else "$ageMonths months"
        ageMonths % 12 == 0 -> if (isThai) "${ageMonths / 12} ปี" else "${ageMonths / 12} years"
        else -> {
            val y = ageMonths / 12
            val m = ageMonths % 12
            if (isThai) "$y ปี $m เดือน" else "${y}y ${m}m"
        }
    }
}

/**
 * Self-check for the bucket/series wiring — the one runnable thing that fails if the medical
 * mapping above is edited carelessly. Call [run] from a scratch main or a future unit test.
 */
internal object VaccineAgeBucketsCheck {
    fun run() {
        // Every catalogued dose must land in a bucket that can actually hold it.
        VaccineSetupCatalog.SCHEDULE.forEach { dose ->
            val b = VaccineAgeBuckets.bucketForMonths(dose.ageMonths)
            check(dose.ageMonths <= b.maxMonths) { "${dose.dbName} (${dose.ageMonths}m) -> ${b.label}" }
        }
        // The documented 2/4/6-month grouping.
        check(VaccineAgeBuckets.bucketForMonths(2).label == "6 เดือนแรก")
        check(VaccineAgeBuckets.bucketForMonths(6).label == "6 เดือนแรก")
        // The documented MMR-1 edge case: 9 months has no bucket of its own, so it lands in "1 ปี".
        check(VaccineAgeBuckets.bucketForMonths(9).label == "1 ปี")
        // DTP primary series spacing: dose 1 (2m) -> dose 2 (4m) is a 2-month return.
        val d = VaccineAgeBuckets.courseStatus("DTP-HB-Hib เข็มที่ 1", setOf("DTP-HB-Hib เข็มที่ 1"))
        check(d == VaccineAgeBuckets.CourseStatus.Outstanding("DTP-HB-Hib เข็มที่ 2", 2)) { "DTP 1->2 wrong: $d" }
        // MMR-1 (9m) -> MMR-2 (18m) is a 9-month return gap.
        val m = VaccineAgeBuckets.courseStatus("MMR เข็มที่ 1", setOf("MMR เข็มที่ 1"))
        check(m == VaccineAgeBuckets.CourseStatus.Outstanding("MMR เข็มที่ 2", 9)) { "MMR 1->2 wrong: $m" }
        // Single-dose vaccine is not a course.
        check(VaccineAgeBuckets.courseStatus("BCG", emptySet()) == VaccineAgeBuckets.CourseStatus.NotASeries)
        check(!VaccineAgeBuckets.isPartOfSeries("BCG"))

        // --- #9: a finished course must report Complete, never "you need more". ---
        val jeAll = setOf("JE เข็มที่ 1", "JE เข็มที่ 2")
        check(VaccineAgeBuckets.courseStatus("JE เข็มที่ 1", jeAll) == VaccineAgeBuckets.CourseStatus.Complete)
        check(VaccineAgeBuckets.courseStatus("JE เข็มที่ 2", jeAll) == VaccineAgeBuckets.CourseStatus.Complete)
        // The bug this fixes: the LAST dose of a half-done course must still point at the gap,
        // not go quiet just because nothing follows it in the list.
        check(
            VaccineAgeBuckets.courseStatus("JE เข็มที่ 2", setOf("JE เข็มที่ 2")) ==
                VaccineAgeBuckets.CourseStatus.Outstanding("JE เข็มที่ 1", 0)
        )
        // And the other half: an already-given dose 2 must be skipped — the row for dose 1 points
        // at dose 3 (6m, so 4 months on from dose 1), not at the dose the child has already had.
        val dtp12 = setOf("DTP-HB-Hib เข็มที่ 1", "DTP-HB-Hib เข็มที่ 2")
        check(
            VaccineAgeBuckets.courseStatus("DTP-HB-Hib เข็มที่ 1", dtp12) ==
                VaccineAgeBuckets.CourseStatus.Outstanding("DTP-HB-Hib เข็มที่ 3", 4)
        )
        // Full DTP course incl. the 18m booster -> Complete.
        check(
            VaccineAgeBuckets.courseStatus(
                "DTP กระตุ้น",
                dtp12 + setOf("DTP-HB-Hib เข็มที่ 3", "DTP กระตุ้น")
            ) == VaccineAgeBuckets.CourseStatus.Complete
        )
    }
}
