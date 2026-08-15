package com.lullabyai.app.viewmodel

/**
 * Turns what the database refused into a sentence a parent can act on.
 *
 * The RPCs raise machine-readable codes (`CHILD_OVER_TOTAL|<combined>|<goal>|<room>`) carrying
 * their amounts in satang. Everything readable is built here instead: the reader's language, baht
 * rather than satang, and — where there is one — the number that *would* fit. Postgres has no idea
 * who is reading, and a driver message wrapped in JSON is not an error message.
 */
object ExpenseErrors {

    /** 125050 satang -> "1,250". Whole baht: goals are set in round numbers. */
    private fun baht(satang: Long): String = "%,d".format(satang / 100)

    /**
     * Reads one field of the code. The driver wraps the message in JSON, so the final field
     * arrives with the rest of the body still attached (`800000","details":null…`) — taking only
     * the leading digits is what keeps the last number from being lost.
     */
    private fun longAt(parts: List<String>, index: Int): Long? =
        parts.getOrNull(index)?.trim()?.takeWhile { it.isDigit() }
            ?.takeIf { it.isNotEmpty() }?.toLongOrNull()

    /**
     * [raw] is whatever the driver surfaced — the code may sit inside a JSON body or behind a
     * Postgres prefix, so this searches the text rather than comparing it. Null means no error.
     */
    /**
     * Pulls (attempted total, already allocated) out of a TOTAL_BELOW_CHILDREN refusal, in satang.
     * Null for any other failure — this one is answerable with a prompt rather than a message.
     */
    fun parseOverTotal(raw: String?): Pair<Long, Long>? {
        if (raw == null || !raw.contains("TOTAL_BELOW_CHILDREN")) return null
        val parts = raw.substringAfter("TOTAL_BELOW_CHILDREN").split("|")
        val attempted = longAt(parts, 1) ?: return null
        val allocated = longAt(parts, 2) ?: return null
        return attempted to allocated
    }

    /** Whole baht with separators, for dialogs that build their own sentence. */
    fun bahtText(satang: Long): String = baht(satang)

    fun message(raw: String?, isThai: Boolean): String? {
        if (raw.isNullOrBlank()) return null

        if (raw.contains("CHILD_OVER_TOTAL")) {
            val parts = raw.substringAfter("CHILD_OVER_TOTAL").split("|")
            val combined = longAt(parts, 1)
            val goal = longAt(parts, 2)
            val room = longAt(parts, 3)
            return if (combined != null && goal != null && room != null) {
                if (isThai)
                    "งบของลูกทุกคนรวมกันจะเป็น ${baht(combined)} ฿ ซึ่งเกินงบรวม ${baht(goal)} ฿ " +
                        "— ลูกคนนี้ตั้งได้สูงสุด ${baht(room)} ฿"
                else
                    "All children's goals would total ${baht(combined)} ฿, above the " +
                        "${baht(goal)} ฿ overall goal — this child can have at most ${baht(room)} ฿."
            } else {
                if (isThai) "งบของลูกทุกคนรวมกันเกินงบรวม"
                else "The children's goals exceed the overall goal."
            }
        }

        if (raw.contains("TOTAL_BELOW_CHILDREN")) {
            val parts = raw.substringAfter("TOTAL_BELOW_CHILDREN").split("|")
            val attempted = longAt(parts, 1)
            val allocated = longAt(parts, 2)
            return if (attempted != null && allocated != null) {
                if (isThai)
                    "งบรวม ${baht(attempted)} ฿ ต่ำกว่างบที่ตั้งให้ลูกไว้แล้ว ${baht(allocated)} ฿ " +
                        "— ลดงบของลูกก่อน หรือตั้งงบรวมอย่างน้อย ${baht(allocated)} ฿"
                else
                    "An overall goal of ${baht(attempted)} ฿ is below the ${baht(allocated)} ฿ " +
                        "already given to the children — lower a child's goal first, or set at " +
                        "least ${baht(allocated)} ฿."
            } else {
                if (isThai) "งบรวมต่ำกว่างบที่ตั้งให้ลูกไว้แล้ว"
                else "The overall goal is below what the children already hold."
            }
        }

        if (raw.contains("EXPENSE_LOCKED")) {
            return if (isThai)
                "รายการนี้ถูกล็อกแล้ว เพราะทำเครื่องหมายว่าใช้แล้วเกิน 30 นาที จึงแก้ไขไม่ได้"
            else "This expense is locked — it was marked used over 30 minutes ago and can no " +
                "longer be changed."
        }

        if (raw.contains("invalid_amount")) {
            return if (isThai) "กรุณาระบุจำนวนเงินที่มากกว่า 0" else "Enter an amount above 0."
        }

        if (raw.contains("not authorized")) {
            return if (isThai) "คุณไม่มีสิทธิ์แก้ไขงบของเด็กคนนี้"
            else "You do not have permission to change this child's budget."
        }

        if (raw.contains("no_child")) {
            return if (isThai) "ยังไม่ได้เลือกเด็ก" else "No child selected yet."
        }

        // Anything unrecognised: say plainly that it failed rather than showing a driver dump,
        // which tells the reader nothing and reads like a crash.
        return if (isThai) "บันทึกไม่สำเร็จ กรุณาลองอีกครั้ง" else "Could not save. Please try again."
    }
}
