package com.lullabyai.app.util

import android.os.Build
import com.lullabyai.app.BuildConfig

/**
 * Names this phone in the User-Agent every Supabase request carries, so Active Sessions can say
 * "samsung SM-A546E • Android 16" instead of the Ktor default "ktor-client" (which is all
 * auth.sessions recorded before this existed). Supabase stores it once, when a session signs in.
 */
object DeviceLabel {
    const val LEGACY_AGENT = "ktor-client"

    fun userAgent(
        version: String = BuildConfig.VERSION_NAME,
        manufacturer: String = Build.MANUFACTURER,
        model: String = Build.MODEL,
        release: String = Build.VERSION.RELEASE
    ): String = "NongRak/$version ($manufacturer $model; Android $release)"

    private val OURS = Regex("""^NongRak/\S+ \((.+); Android ([^)]+)\)$""")

    /** "samsung SM-A546E • Android 16" for our own agent; null for anything else (legacy, web, blank). */
    fun parse(userAgent: String?): String? {
        val match = OURS.find(userAgent?.trim() ?: return null) ?: return null
        return "${match.groupValues[1]} • Android ${match.groupValues[2]}"
    }
}
