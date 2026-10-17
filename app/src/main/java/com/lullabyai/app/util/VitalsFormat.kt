package com.lullabyai.app.util

/** Height / weight on Hub, Summary, Growth History — empty after a delete is "none", not a dash. */
object VitalsFormat {
    fun missing(isThai: Boolean) = if (isThai) "ไม่มี" else "None"

    fun amount(value: Double?, isThai: Boolean): String =
        value?.let { "%.1f".format(it) } ?: missing(isThai)

    fun amountWithUnit(value: Double?, isThai: Boolean, unitTh: String, unitEn: String): String =
        value?.let { "${"%.1f".format(it)} ${if (isThai) unitTh else unitEn}" } ?: missing(isThai)
}
