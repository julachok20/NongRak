package com.lullabyai.app.util

/**
 * The badge on a community profile.
 *
 * The mockups show "สมาชิกผู้ทรงคุณค่าระดับ 5", "Top Contributor" and "คุณแม่สมาชิกเหรียญทอง". A badge
 * on a parenting profile is a claim other parents read as authority - it changes whose BLW advice
 * they follow at 2am - so each one here is earned from things that actually happened in the app:
 * posts written, likes those posts received, and answers left under other people's questions.
 *
 * It measures PARTICIPATION, not correctness. A parent with a gold badge is one who shows up, not
 * one whose advice has been checked by anybody, and [caveat] says exactly that on the profile.
 * Nothing here is medical standing - only the doctor role means that.
 */
object ContributorLevel {

    /** Level 0 means "no badge at all", which is the right answer for a new account. */
    const val MAX_LEVEL = 5

    /**
     * Points behind the badge, from real activity.
     *
     * An answer under someone else's question counts double a post of your own: helping is the
     * thing worth encouraging, and it is harder to farm than posting.
     */
    fun points(posts: Int, likesReceived: Int, answersGiven: Int): Int =
        posts.coerceAtLeast(0) + likesReceived.coerceAtLeast(0) + answersGiven.coerceAtLeast(0) * 2

    /** Thresholds are deliberately round, so they can be explained to a parent who asks. */
    fun level(points: Int): Int = when {
        points >= 500 -> 5
        points >= 200 -> 4
        points >= 75 -> 3
        points >= 25 -> 2
        points >= 5 -> 1
        else -> 0
    }

    fun level(posts: Int, likesReceived: Int, answersGiven: Int): Int =
        level(points(posts, likesReceived, answersGiven))

    /** The badge text, or null at level 0 - a new parent gets no badge rather than a sad one. */
    fun label(level: Int, isThai: Boolean): String? = when (level) {
        5 -> if (isThai) "สมาชิกผู้ทรงคุณค่าระดับ 5" else "Level 5 contributor"
        4 -> if (isThai) "คุณแม่สมาชิกเหรียญทอง" else "Gold contributor"
        3 -> if (isThai) "คุณแม่สมาชิกเหรียญเงิน" else "Silver contributor"
        2 -> if (isThai) "สมาชิกที่แบ่งปันสม่ำเสมอ" else "Regular contributor"
        1 -> if (isThai) "สมาชิกใหม่ที่เริ่มแบ่งปัน" else "New contributor"
        else -> null
    }

    /** Top Contributor, as drawn in mockup 58. Level 4 and up, so it stays uncommon. */
    fun isTopContributor(level: Int): Boolean = level >= 4

    /**
     * The line under the badge. Shown wherever a badge is, because a badge without it reads as an
     * endorsement of the advice rather than a count of the posts.
     */
    fun caveat(isThai: Boolean): String = if (isThai) {
        "ระดับนี้นับจากการแบ่งปันในชุมชน ไม่ใช่การรับรองทางการแพทย์"
    } else {
        "This level counts community participation. It is not a medical qualification."
    }

    /** How many more points to the next badge, for the progress line. Null at the top. */
    fun pointsToNextLevel(points: Int): Int? = when (level(points)) {
        0 -> 5 - points
        1 -> 25 - points
        2 -> 75 - points
        3 -> 200 - points
        4 -> 500 - points
        else -> null
    }
}
