package com.lullabyai.app.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.lullabyai.app.data.remote.supabase
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID

@Serializable
data class FeedbackSubmission(
    val id: String = UUID.randomUUID().toString(),
    val rating: Int,
    val category: String,
    val message: String,
    val userEmail: String? = null,
    val userName: String? = null,
    val deviceModel: String = "${Build.MANUFACTURER} ${Build.MODEL}",
    val osVersion: String = "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})",
    val appVersion: String = "1.0.0",
    val createdAt: String = Instant.now().toString()
)

class FeedbackRepository {

    companion object {
        const val DEVELOPER_EMAIL = "julachok2@gmail.com"
    }

    /**
     * Submits feedback to Supabase backend (best-effort) and prepares email dispatch.
     */
    suspend fun submitFeedback(submission: FeedbackSubmission): Result<Unit> = runCatching {
        runCatching {
            supabase.from("feedbacks").insert(submission)
        }
        Unit
    }

    /**
     * Creates an Intent to send the feedback details directly to developer email (julachok2@gmail.com).
     */
    fun createEmailIntent(submission: FeedbackSubmission, isThai: Boolean): Intent {
        val stars = "★".repeat(submission.rating) + "☆".repeat((5 - submission.rating).coerceAtLeast(0))
        val subject = "[NongRak AI Feedback] $stars (${submission.rating}/5) - ${submission.category}"
        val body = buildString {
            appendLine("=== NongRak AI User Feedback & Review ===")
            appendLine("คะแนนความพึงพอใจ / Rating: $stars (${submission.rating} จาก 5 ดาว)")
            appendLine("ประเภทข้อเสนอแนะ / Category: ${submission.category}")
            if (!submission.userName.isNullOrBlank()) {
                appendLine("ชื่อผู้ใช้งาน / User: ${submission.userName}")
            }
            appendLine("อีเมลติดต่อ / Contact Email: ${submission.userEmail?.ifBlank { null } ?: "ไม่ได้ระบุ (Not specified)"}")
            appendLine("วันที่ส่ง / Timestamp: ${submission.createdAt}")
            appendLine("อุปกรณ์ / Device: ${submission.deviceModel}")
            appendLine("ระบบปฏิบัติการ / OS: ${submission.osVersion}")
            appendLine("เวอร์ชันแอป / App Version: ${submission.appVersion}")
            appendLine()
            appendLine("--- ข้อเสนอแนะและความคิดเห็น / Feedback Message ---")
            appendLine(submission.message)
            appendLine("=========================================")
        }

        val mailtoUri = Uri.parse(
            "mailto:$DEVELOPER_EMAIL?subject=${Uri.encode(subject)}&body=${Uri.encode(body)}"
        )
        return Intent(Intent.ACTION_SENDTO, mailtoUri)
    }
}
