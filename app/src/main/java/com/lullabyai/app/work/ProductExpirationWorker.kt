package com.lullabyai.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lullabyai.app.data.remote.supabase
import com.lullabyai.app.data.repository.ChildRepository
import com.lullabyai.app.data.repository.NotifCategory
import com.lullabyai.app.data.repository.NotificationRepository
import com.lullabyai.app.data.settings.SettingsManager
import io.github.jan.supabase.auth.auth
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Background worker (WorkManager) that checks recorded items in cost records for product expiration.
 * Reminds parents about products expiring soon (e.g. within 7, 3, 1 days, today, or already expired).
 * Dedupe markers ("${expense.id}:$daysLeft") ensure alerts fire without repetitive spam.
 */
class ProductExpirationWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    // Thresholds: alert when 7, 3, 1 days left, day of expiration (0), or expired (< 0)
    private val alertOffsets = listOf(7, 3, 1, 0)

    override suspend fun doWork(): Result {
        runCatching { supabase.auth.awaitInitialization() }
        supabase.auth.currentUserOrNull() ?: return Result.success()

        val childRepo = ChildRepository()
        val children = childRepo.getChildren().getOrDefault(emptyList())
        val childNamesById = children.associate { it.id to it.name }

        val expiringExpenses = childRepo.listExpiringProducts(null, withinDays = 7)
            .getOrElse { return Result.retry() }

        if (expiringExpenses.isEmpty()) return Result.success()

        val today = LocalDate.now()
        val settings = SettingsManager(applicationContext)
        val alreadyReminded = settings.getProductExpirationRemindedKeys()
        val notifRepo = NotificationRepository()
        val newlyReminded = mutableSetOf<String>()

        for (e in expiringExpenses) {
            val expires = e.expiresOn
            if (!e.hasExpiration || e.alreadyUsed || expires.isNullOrBlank()) continue

            val expDate = runCatching { LocalDate.parse(expires.take(10)) }.getOrNull() ?: continue
            val daysLeft = ChronoUnit.DAYS.between(today, expDate).toInt()

            // Decide whether this daysLeft warrants an alert
            val dedupeTag = when {
                daysLeft < 0 -> "expired"
                daysLeft in alertOffsets -> "$daysLeft"
                // If it's inside 7 days but not an exact offset, also alert once for the window if not already alerted
                daysLeft in 2..6 && "${e.id}:window" !in alreadyReminded -> "window"
                else -> null
            } ?: continue

            val dedupeKey = "${e.id}:$dedupeTag"
            if (dedupeKey in alreadyReminded || dedupeKey in newlyReminded) continue

            val productName = e.name?.takeIf { it.isNotBlank() }
                ?: e.note?.takeIf { it.isNotBlank() }
                ?: "สินค้า"
            val childName = e.childId?.let { childNamesById[it] }
            val childSuffix = if (!childName.isNullOrBlank()) " ของน้อง$childName" else ""
            val formattedDate = formatThaiDate(expDate)

            val title: String
            val body: String

            when {
                daysLeft < 0 -> {
                    title = "สินค้าหมดอายุแล้ว!"
                    body = "$productName$childSuffix หมดอายุแล้วเมื่อ $formattedDate กรุณาตรวจสอบและหลีกเลี่ยงการใช้งาน"
                }
                daysLeft == 0 -> {
                    title = "สินค้าหมดอายุวันนี้!"
                    body = "$productName$childSuffix จะหมดอายุในวันนี้ ($formattedDate) กรุณาตรวจสอบก่อนนำไปใช้งาน"
                }
                daysLeft == 1 -> {
                    title = "สินค้าจะหมดอายุพรุ่งนี้!"
                    body = "$productName$childSuffix จะหมดอายุในวันพรุ่งนี้ ($formattedDate) รีบนำมาใช้งานก่อนหมดอายุ"
                }
                else -> {
                    title = "สินค้าใกล้หมดอายุ (อีก $daysLeft วัน)"
                    body = "$productName$childSuffix จะหมดอายุในอีก $daysLeft วัน ($formattedDate)"
                }
            }

            notifRepo.notify(
                category = NotifCategory.WARNING,
                title = title,
                body = body
            )
            newlyReminded += dedupeKey
        }

        if (newlyReminded.isNotEmpty()) {
            settings.setProductExpirationRemindedKeys(alreadyReminded + newlyReminded)
        }

        return Result.success()
    }

    companion object {
        fun formatThaiDate(date: LocalDate): String {
            val months = listOf(
                "ม.ค.", "ก.พ.", "มี.ค.", "เม.ย.", "พ.ค.", "มิ.ย.",
                "ก.ค.", "ส.ค.", "ก.ย.", "ต.ค.", "พ.ย.", "ธ.ค."
            )
            val monthStr = months.getOrElse(date.monthValue - 1) { "" }
            val buddhistYear = (date.year + 543) % 100
            return "${date.dayOfMonth} $monthStr $buddhistYear"
        }
    }
}
