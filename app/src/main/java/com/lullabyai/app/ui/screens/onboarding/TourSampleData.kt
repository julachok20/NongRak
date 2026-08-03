package com.lullabyai.app.ui.screens.onboarding

import com.lullabyai.app.data.model.Child
import com.lullabyai.app.data.model.DevelopmentMilestone

/** Placeholder child + data shown during the first-run tour when the account has no real child yet. */
object TourSampleData {
    const val SAMPLE_CHILD_ID = "tour-sample-child"

    val child = Child(
        id = SAMPLE_CHILD_ID,
        name = "น้องมิน",
        birthdate = "2023-06-15",
        gender = "female",
        weightKg = 12.5,
        heightCm = 85.0
    )

    fun displayName(isThai: Boolean): String = if (isThai) "น้องมิน" else "Sample Min"

    /** ~24 months for plausible growth stats in the preview. */
    fun ageMonths(): Int = 24

    fun ageLabel(isThai: Boolean): String =
        if (isThai) "2 ปี" else "2 years"

    val sampleMilestones: List<DevelopmentMilestone> = listOf(
        DevelopmentMilestone(id = "t1", childId = SAMPLE_CHILD_ID, category = "motor", milestoneName = "Sample", isAchieved = true, ageMonthsExpected = 18),
        DevelopmentMilestone(id = "t2", childId = SAMPLE_CHILD_ID, category = "language", milestoneName = "Sample", isAchieved = true, ageMonthsExpected = 24),
        DevelopmentMilestone(id = "t3", childId = SAMPLE_CHILD_ID, category = "social", milestoneName = "Sample", isAchieved = false, ageMonthsExpected = 24)
    )
}
