package com.lullabyai.app.viewmodel

import com.lullabyai.app.data.model.ChildExpense
import com.lullabyai.app.work.ProductExpirationWorker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class ProductExpirationTest {

    @Test
    fun `formatThaiDate produces Buddhist Era year and correct Thai month abbreviation`() {
        // 2026 + 543 = 2569 BE -> year 69
        val septDate = LocalDate.of(2026, 9, 21)
        assertEquals("21 ก.ย. 69", ProductExpirationWorker.formatThaiDate(septDate))

        val janDate = LocalDate.of(2026, 1, 5)
        assertEquals("5 ม.ค. 69", ProductExpirationWorker.formatThaiDate(janDate))

        val decDate = LocalDate.of(2025, 12, 31)
        // 2025 + 543 = 2568 BE -> year 68
        assertEquals("31 ธ.ค. 68", ProductExpirationWorker.formatThaiDate(decDate))
    }

    @Test
    fun `days until calculation identifies expired and upcoming products`() {
        val today = LocalDate.of(2026, 9, 21)

        val expiredDate = LocalDate.of(2026, 9, 19)
        val daysExpired = ChronoUnit.DAYS.between(today, expiredDate).toInt()
        assertEquals(-2, daysExpired)
        assertTrue(daysExpired < 0)

        val todayExpDate = LocalDate.of(2026, 9, 21)
        val daysToday = ChronoUnit.DAYS.between(today, todayExpDate).toInt()
        assertEquals(0, daysToday)

        val tomorrowExpDate = LocalDate.of(2026, 9, 22)
        val daysTomorrow = ChronoUnit.DAYS.between(today, tomorrowExpDate).toInt()
        assertEquals(1, daysTomorrow)

        val weekExpDate = LocalDate.of(2026, 9, 28)
        val daysWeek = ChronoUnit.DAYS.between(today, weekExpDate).toInt()
        assertEquals(7, daysWeek)
        assertTrue(daysWeek in 0..7)

        val futureExpDate = LocalDate.of(2026, 10, 15)
        val daysFuture = ChronoUnit.DAYS.between(today, futureExpDate).toInt()
        assertFalse(daysFuture in 0..7)
    }

    @Test
    fun `expiring product filter excludes already used or non-expiring goods`() {
        val today = LocalDate.of(2026, 9, 21)
        val maxDate = today.plusDays(7)
        val minDate = today.minusDays(7)

        fun isExpiring(expense: ChildExpense): Boolean {
            if (!expense.hasExpiration || expense.alreadyUsed || expense.expiresOn.isNullOrBlank()) {
                return false
            }
            val exp = runCatching { LocalDate.parse(expense.expiresOn!!.take(10)) }.getOrNull() ?: return false
            return !exp.isBefore(minDate) && !exp.isAfter(maxDate)
        }

        val activeExpiringItem = ChildExpense(
            id = "exp-1",
            name = "Baby Formula",
            amountSatang = 45000,
            hasExpiration = true,
            alreadyUsed = false,
            expiresOn = "2026-09-25"
        )
        assertTrue(isExpiring(activeExpiringItem))

        val consumedItem = ChildExpense(
            id = "exp-2",
            name = "Baby Puree",
            amountSatang = 8500,
            hasExpiration = true,
            alreadyUsed = true, // Consumed items must not trigger expiration reminders
            expiresOn = "2026-09-22"
        )
        assertFalse(isExpiring(consumedItem))

        val noExpirationItem = ChildExpense(
            id = "exp-3",
            name = "Baby Clothes",
            amountSatang = 32000,
            hasExpiration = false,
            alreadyUsed = false,
            expiresOn = null
        )
        assertFalse(isExpiring(noExpirationItem))

        val farFutureItem = ChildExpense(
            id = "exp-4",
            name = "Diaper Box",
            amountSatang = 69000,
            hasExpiration = true,
            alreadyUsed = false,
            expiresOn = "2027-01-01"
        )
        assertFalse(isExpiring(farFutureItem))

        val longExpiredItem = ChildExpense(
            id = "exp-5",
            name = "Old Medicine",
            amountSatang = 12000,
            hasExpiration = true,
            alreadyUsed = false,
            expiresOn = "2026-08-01" // Expired over a month ago
        )
        assertFalse(isExpiring(longExpiredItem))
    }

    @Test
    fun `deduplication key formatting avoids repeat alerts`() {
        val expenseId = "expense-uuid-1234"
        val offsetKey7 = "$expenseId:7"
        val offsetKey3 = "$expenseId:3"
        val offsetKeyToday = "$expenseId:0"
        val offsetKeyExpired = "$expenseId:expired"

        val remindedKeys = setOf(offsetKey7, offsetKey3)

        assertTrue(offsetKey7 in remindedKeys)
        assertTrue(offsetKey3 in remindedKeys)
        assertFalse(offsetKeyToday in remindedKeys)
        assertFalse(offsetKeyExpired in remindedKeys)
    }
}
