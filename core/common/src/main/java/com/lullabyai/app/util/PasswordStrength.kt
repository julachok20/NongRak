package com.lullabyai.app.util

/**
 * Client-side password strength check — purely advisory feedback shown while typing a NEW
 * password (signup, reset, change). Never applied retroactively to existing accounts: it only
 * ever runs where the user is actively typing a password into a field, so old accounts are
 * never re-checked or blocked by this.
 */
object PasswordStrength {

    enum class Level { WEAK, FAIR, GOOD, STRONG }

    data class Result(val level: Level, val score: Int, val totalCriteria: Int)

    /** Score 0-6 from length + character variety. Not a "real" entropy calculation — just enough
     *  signal to nudge users away from the weakest passwords without being annoying. */
    fun check(password: String): Result {
        var score = 0
        if (password.length >= 8) score++
        if (password.length >= 12) score++
        if (password.any { it.isUpperCase() }) score++
        if (password.any { it.isLowerCase() }) score++
        if (password.any { it.isDigit() }) score++
        if (password.any { !it.isLetterOrDigit() }) score++

        val level = when {
            password.length < 8 -> Level.WEAK
            score <= 2 -> Level.WEAK
            score <= 3 -> Level.FAIR
            score <= 4 -> Level.GOOD
            else -> Level.STRONG
        }
        return Result(level, score, 6)
    }

    fun label(level: Level, isThai: Boolean): String = when (level) {
        Level.WEAK -> if (isThai) "รหัสผ่านอ่อนแอ" else "Weak"
        Level.FAIR -> if (isThai) "พอใช้" else "Fair"
        Level.GOOD -> if (isThai) "ดี" else "Good"
        Level.STRONG -> if (isThai) "แข็งแรงมาก" else "Strong"
    }
}
