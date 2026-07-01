package com.lullabyai.app.data.remote

import io.github.jan.supabase.storage.storage
import kotlin.time.Duration.Companion.seconds

/**
 * Central place for avatar/photo storage security.
 *
 * Photos (parent + child) live in a PRIVATE Supabase bucket, locked per-user folder via RLS,
 * and are only ever exposed through short-lived signed URLs. To change behaviour, edit the
 * two constants below — nothing else needs to change.
 */
object StorageHelper {
    /** The private bucket that holds all profile/child photos. */
    const val AVATAR_BUCKET = "avatars"

    /** How long an image link stays valid before it must be re-signed. Lower = more secure. */
    const val SIGNED_URL_TTL_SECONDS = 3600 // 1 hour

    /**
     * Normalise a stored value to a bucket path.
     * Accepts a raw path ("uid/avatar.jpg") OR a legacy public/signed URL (back-compat),
     * so old rows that stored a full public URL still resolve correctly.
     */
    fun toPath(stored: String?): String? {
        if (stored.isNullOrBlank()) return null
        val publicMarker = "/object/public/$AVATAR_BUCKET/"
        val signMarker = "/object/sign/$AVATAR_BUCKET/"
        return when {
            stored.contains(publicMarker) -> stored.substringAfter(publicMarker).substringBefore("?")
            stored.contains(signMarker) -> stored.substringAfter(signMarker).substringBefore("?")
            stored.startsWith("http") -> null            // unknown external URL — cannot sign
            else -> stored.substringBefore("?")           // already a bucket path
        }
    }

    /**
     * Create a short-lived signed URL for a stored avatar path/URL.
     * Returns null on failure (caller falls back to a placeholder).
     */
    suspend fun signedAvatarUrl(stored: String?): String? {
        val path = toPath(stored) ?: return null
        return runCatching {
            supabase.storage.from(AVATAR_BUCKET).createSignedUrl(path, SIGNED_URL_TTL_SECONDS.seconds)
        }.getOrNull()
    }
}
