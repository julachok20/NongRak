package com.lullabyai.app.viewmodel

import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The point of these messages is that a parent can act on them, so what is asserted is that the
 * useful number survives the trip from Postgres — in baht, not satang — and that nothing raw ever
 * reaches the screen.
 */
class ExpenseErrorsTest {

    /** What the driver actually hands back: the code buried in a JSON body. */
    private fun wrapped(code: String) =
        """{"code":"23514","details":null,"hint":null,"message":"$code"}"""

    /**
     * The shape supabase-kt really throws. `RestException` appends code, hint, URL, headers and
     * method to the Postgres message, so the last field of the code arrives with a newline and a
     * page of transport detail stuck to it — and that whole dump is what used to reach the screen.
     */
    private fun restException(code: String) = buildString {
        append(code)
        append("\nCode: 23514\nHint: null\nDetails: null")
        append("\nURL: https://rdylsezwjdujvllyjhqb.supabase.co/rest/v1/rpc/set_child_budget")
        append("\nHeaders: [apikey=****, Authorization=****]")
        append("\nHttp Method: POST")
    }

    @Test
    fun `the real driver message still yields every number`() {
        val msg = ExpenseErrors.message(
            restException("CHILD_OVER_TOTAL|2100000|2000000|800000"), isThai = false
        )!!
        assertTrue(msg, msg.contains("21,000"))
        assertTrue(msg, msg.contains("20,000"))
        assertTrue(msg, msg.contains("8,000"))
        // None of the transport noise may survive into what the parent reads.
        assertTrue(msg, !msg.contains("Headers"))
        assertTrue(msg, !msg.contains("supabase.co"))
        assertTrue(msg, !msg.contains("23514"))
    }

    @Test
    fun `no error is no message`() {
        assertNull(ExpenseErrors.message(null, true))
        assertNull(ExpenseErrors.message("", true))
    }

    @Test
    fun `child over total names the room left, in baht`() {
        val msg = ExpenseErrors.message(
            wrapped("CHILD_OVER_TOTAL|2100000|2000000|800000"), isThai = false
        )!!
        assertTrue(msg, msg.contains("21,000"))   // combined
        assertTrue(msg, msg.contains("20,000"))   // overall goal
        assertTrue(msg, msg.contains("8,000"))    // what this child can still have
        assertTrue(msg, !msg.contains("2100000")) // never raw satang
    }

    @Test
    fun `child over total is answerable in Thai too`() {
        val msg = ExpenseErrors.message(
            wrapped("CHILD_OVER_TOTAL|2100000|2000000|800000"), isThai = true
        )!!
        assertTrue(msg, msg.contains("8,000"))
        assertTrue(msg, msg.contains("฿"))
    }

    @Test
    fun `total below children says the minimum that would work`() {
        val msg = ExpenseErrors.message(
            wrapped("TOTAL_BELOW_CHILDREN|1000000|1200000"), isThai = false
        )!!
        assertTrue(msg, msg.contains("10,000"))
        assertTrue(msg, msg.contains("12,000"))
    }

    @Test
    fun `a malformed code still produces a sentence rather than a crash`() {
        val msg = ExpenseErrors.message(wrapped("CHILD_OVER_TOTAL|nonsense"), isThai = false)!!
        assertTrue(msg, msg.isNotBlank())
        assertTrue(msg, !msg.contains("|"))
    }

    @Test
    fun `the lock explains why, not just that it failed`() {
        val msg = ExpenseErrors.message(wrapped("EXPENSE_LOCKED"), isThai = false)!!
        assertTrue(msg, msg.contains("30 minutes"))
    }

    @Test
    fun `an unrecognised failure never shows the driver dump`() {
        val raw = """{"code":"PGRST301","message":"JWSError JWSInvalidSignature"}"""
        val msg = ExpenseErrors.message(raw, isThai = false)!!
        assertTrue(msg, !msg.contains("JWSError"))
        assertTrue(msg, !msg.contains("PGRST"))
        assertTrue(msg, msg.contains("Could not save"))
    }
}
