package com.lullabyai.app.data.repository

import android.content.Intent
import android.net.Uri
import android.os.Build
import com.lullabyai.app.data.remote.supabase
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.storage.storage
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

/** One app review, shaped like a `public.feedbacks` row (user_id and created_at are filled by the database). */
@Serializable
data class FeedbackSubmission(
    val rating: Int,
    /** Chip keys the parent picked, e.g. "ai_accurate" or "bug". */
    val highlights: List<String> = emptyList(),
    val message: String = "",
    @SerialName("contact_email") val contactEmail: String? = null,
    @SerialName("image_path") val imagePath: String? = null,
    @SerialName("device_model") val deviceModel: String = "${Build.MANUFACTURER} ${Build.MODEL}",
    @SerialName("os_version") val osVersion: String = "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})",
    @SerialName("app_version") val appVersion: String = "unknown"
)

class FeedbackRepository {

    companion object {
        const val DEVELOPER_EMAIL = "julachok2@gmail.com"
        private const val BUCKET = "feedback-media"
    }

    /**
     * Saves a review, with an optional screenshot uploaded first to the private feedback bucket.
     *
     * Failures are returned, not swallowed. The old version wrapped the insert in a runCatching and
     * dropped the result, into a table that did not exist — every review was lost while the screen
     * said thank you.
     */
    suspend fun submit(submission: FeedbackSubmission, image: ByteArray?): Result<Unit> = runCatching {
        val userId = supabase.auth.currentUserOrNull()?.id ?: error("feedback_signed_out")
        val imagePath = image?.let { bytes ->
            val path = "$userId/${UUID.randomUUID()}.jpg"
            supabase.storage.from(BUCKET).upload(path, bytes) { upsert = false }
            path
        }
        supabase.from("feedbacks").insert(submission.copy(imagePath = imagePath))
        Unit
    }

    /**
     * The "contact the team" fallback when saving fails: a pre-filled email to the developer that
     * carries the review AND the error the app received, so the report is not lost either way.
     */
    fun createEmailIntent(submission: FeedbackSubmission, isThai: Boolean, error: String?): Intent {
        val stars = "★".repeat(submission.rating) + "☆".repeat((5 - submission.rating).coerceAtLeast(0))
        val subject = "[NongRak AI Review] $stars (${submission.rating}/5)"
        val body = buildString {
            appendLine("=== NongRak AI Review ===")
            appendLine("คะแนน / Rating: $stars (${submission.rating}/5)")
            if (submission.highlights.isNotEmpty()) appendLine("หัวข้อ / Highlights: ${submission.highlights.joinToString(", ")}")
            appendLine("อีเมลติดต่อ / Contact: ${submission.contactEmail ?: "-"}")
            appendLine("อุปกรณ์ / Device: ${submission.deviceModel}")
            appendLine("ระบบ / OS: ${submission.osVersion}")
            appendLine("เวอร์ชันแอป / App: ${submission.appVersion}")
            if (!error.isNullOrBlank()) {
                appendLine()
                appendLine(if (isThai) "--- ข้อผิดพลาดที่แอปได้รับตอนส่ง ---" else "--- Error the app received ---")
                appendLine(error)
            }
            appendLine()
            appendLine(if (isThai) "--- ความคิดเห็น ---" else "--- Message ---")
            appendLine(submission.message.ifBlank { "-" })
        }
        val mailtoUri = Uri.parse("mailto:$DEVELOPER_EMAIL?subject=${Uri.encode(subject)}&body=${Uri.encode(body)}")
        return Intent(Intent.ACTION_SENDTO, mailtoUri)
    }
}
