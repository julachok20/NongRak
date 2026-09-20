package com.lullabyai.app.data.model

import com.lullabyai.app.BuildConfig
import com.lullabyai.app.R

/**
 * รูปสำเร็จรูป — the ready-made faces a parent can pick instead of uploading a photo.
 *
 * The artwork arrives as four contact sheets of many faces each, not as four pictures; they are
 * cut into 142 individual circular WebPs at build-prep time (see the slicer kept with the
 * source sheets) and shipped one file per face. Stored in children.avatar_url as "preset:s1r0c2".
 *
 * Bundled rather than uploaded: 142 small fixed images do not need object storage, and a
 * sentinel is the only form that survives every place the photo shows up — the app, the LIFF
 * pages in LINE, and the public emergency card, which has no session to sign a private URL with.
 * nongrak-web keeps the same ids in src/lib/childAvatar.ts; **the ids are the contract.**
 */
object ChildAvatarPreset {

    const val PREFIX = "preset:"

    /** Grouped as the picker shows them — one row of the sheet per source sheet. */
    val groups: Map<String, List<String>> = mapOf(
        "s1" to listOf(
        "s1r0c0", "s1r0c1", "s1r0c2", "s1r0c3", "s1r0c4",
        "s1r1c0", "s1r1c1", "s1r1c2", "s1r1c3", "s1r1c4",
        "s1r2c0", "s1r2c1", "s1r2c2", "s1r2c3", "s1r2c4",
        "s1r3c0", "s1r3c1", "s1r3c2", "s1r3c3", "s1r3c4",
        "s1r4c0", "s1r4c1", "s1r4c2", "s1r4c3", "s1r4c4"
        ),
        "s2" to listOf(
        "s2r0c0", "s2r0c1", "s2r0c2", "s2r0c3", "s2r0c4",
        "s2r1c0", "s2r1c1", "s2r1c2", "s2r1c3", "s2r1c4",
        "s2r2c0", "s2r2c1", "s2r2c2", "s2r2c3", "s2r2c4",
        "s2r3c0", "s2r3c1", "s2r3c2", "s2r3c3", "s2r3c4",
        "s2r4c0", "s2r4c1", "s2r4c2", "s2r4c3", "s2r4c4"
        ),
        "s3" to listOf(
        "s3r0c0", "s3r0c1", "s3r0c2", "s3r0c3", "s3r0c4",
        "s3r0c5", "s3r0c6", "s3r0c7", "s3r1c0", "s3r1c1",
        "s3r1c2", "s3r1c3", "s3r1c4", "s3r1c5", "s3r1c6",
        "s3r1c7", "s3r2c0", "s3r2c1", "s3r2c2", "s3r2c3",
        "s3r2c4", "s3r2c5", "s3r2c6", "s3r2c7", "s3r3c0",
        "s3r3c1", "s3r3c2", "s3r3c3", "s3r3c4", "s3r3c5",
        "s3r3c6", "s3r3c7", "s3r4c0", "s3r4c1", "s3r4c2",
        "s3r4c3", "s3r4c4", "s3r4c5", "s3r4c6", "s3r4c7",
        "s3r5c0", "s3r5c1", "s3r5c2", "s3r5c3", "s3r5c4",
        "s3r5c5", "s3r5c6", "s3r5c7", "s3r6c0", "s3r6c1",
        "s3r6c2", "s3r6c3", "s3r6c4", "s3r6c5", "s3r6c6",
        "s3r6c7"
        ),
        "s4" to listOf(
        "s4r0c0", "s4r0c1", "s4r0c2", "s4r0c3", "s4r0c4",
        "s4r0c5", "s4r1c0", "s4r1c1", "s4r1c2", "s4r1c3",
        "s4r1c4", "s4r1c5", "s4r2c0", "s4r2c1", "s4r2c2",
        "s4r2c3", "s4r2c4", "s4r2c5", "s4r3c0", "s4r3c1",
        "s4r3c2", "s4r3c3", "s4r3c4", "s4r3c5", "s4r4c0",
        "s4r4c1", "s4r4c2", "s4r4c3", "s4r4c4", "s4r4c5",
        "s4r5c0", "s4r5c1", "s4r5c2", "s4r5c3", "s4r5c4",
        "s4r5c5"
        )
    )

    val ids: List<String> = groups.values.flatten()

    private val drawables: Map<String, Int> = mapOf(
        "s1r0c0" to R.drawable.child_avatar_s1r0c0, "s1r0c1" to R.drawable.child_avatar_s1r0c1, "s1r0c2" to R.drawable.child_avatar_s1r0c2,
        "s1r0c3" to R.drawable.child_avatar_s1r0c3, "s1r0c4" to R.drawable.child_avatar_s1r0c4, "s1r1c0" to R.drawable.child_avatar_s1r1c0,
        "s1r1c1" to R.drawable.child_avatar_s1r1c1, "s1r1c2" to R.drawable.child_avatar_s1r1c2, "s1r1c3" to R.drawable.child_avatar_s1r1c3,
        "s1r1c4" to R.drawable.child_avatar_s1r1c4, "s1r2c0" to R.drawable.child_avatar_s1r2c0, "s1r2c1" to R.drawable.child_avatar_s1r2c1,
        "s1r2c2" to R.drawable.child_avatar_s1r2c2, "s1r2c3" to R.drawable.child_avatar_s1r2c3, "s1r2c4" to R.drawable.child_avatar_s1r2c4,
        "s1r3c0" to R.drawable.child_avatar_s1r3c0, "s1r3c1" to R.drawable.child_avatar_s1r3c1, "s1r3c2" to R.drawable.child_avatar_s1r3c2,
        "s1r3c3" to R.drawable.child_avatar_s1r3c3, "s1r3c4" to R.drawable.child_avatar_s1r3c4, "s1r4c0" to R.drawable.child_avatar_s1r4c0,
        "s1r4c1" to R.drawable.child_avatar_s1r4c1, "s1r4c2" to R.drawable.child_avatar_s1r4c2, "s1r4c3" to R.drawable.child_avatar_s1r4c3,
        "s1r4c4" to R.drawable.child_avatar_s1r4c4, "s2r0c0" to R.drawable.child_avatar_s2r0c0, "s2r0c1" to R.drawable.child_avatar_s2r0c1,
        "s2r0c2" to R.drawable.child_avatar_s2r0c2, "s2r0c3" to R.drawable.child_avatar_s2r0c3, "s2r0c4" to R.drawable.child_avatar_s2r0c4,
        "s2r1c0" to R.drawable.child_avatar_s2r1c0, "s2r1c1" to R.drawable.child_avatar_s2r1c1, "s2r1c2" to R.drawable.child_avatar_s2r1c2,
        "s2r1c3" to R.drawable.child_avatar_s2r1c3, "s2r1c4" to R.drawable.child_avatar_s2r1c4, "s2r2c0" to R.drawable.child_avatar_s2r2c0,
        "s2r2c1" to R.drawable.child_avatar_s2r2c1, "s2r2c2" to R.drawable.child_avatar_s2r2c2, "s2r2c3" to R.drawable.child_avatar_s2r2c3,
        "s2r2c4" to R.drawable.child_avatar_s2r2c4, "s2r3c0" to R.drawable.child_avatar_s2r3c0, "s2r3c1" to R.drawable.child_avatar_s2r3c1,
        "s2r3c2" to R.drawable.child_avatar_s2r3c2, "s2r3c3" to R.drawable.child_avatar_s2r3c3, "s2r3c4" to R.drawable.child_avatar_s2r3c4,
        "s2r4c0" to R.drawable.child_avatar_s2r4c0, "s2r4c1" to R.drawable.child_avatar_s2r4c1, "s2r4c2" to R.drawable.child_avatar_s2r4c2,
        "s2r4c3" to R.drawable.child_avatar_s2r4c3, "s2r4c4" to R.drawable.child_avatar_s2r4c4, "s3r0c0" to R.drawable.child_avatar_s3r0c0,
        "s3r0c1" to R.drawable.child_avatar_s3r0c1, "s3r0c2" to R.drawable.child_avatar_s3r0c2, "s3r0c3" to R.drawable.child_avatar_s3r0c3,
        "s3r0c4" to R.drawable.child_avatar_s3r0c4, "s3r0c5" to R.drawable.child_avatar_s3r0c5, "s3r0c6" to R.drawable.child_avatar_s3r0c6,
        "s3r0c7" to R.drawable.child_avatar_s3r0c7, "s3r1c0" to R.drawable.child_avatar_s3r1c0, "s3r1c1" to R.drawable.child_avatar_s3r1c1,
        "s3r1c2" to R.drawable.child_avatar_s3r1c2, "s3r1c3" to R.drawable.child_avatar_s3r1c3, "s3r1c4" to R.drawable.child_avatar_s3r1c4,
        "s3r1c5" to R.drawable.child_avatar_s3r1c5, "s3r1c6" to R.drawable.child_avatar_s3r1c6, "s3r1c7" to R.drawable.child_avatar_s3r1c7,
        "s3r2c0" to R.drawable.child_avatar_s3r2c0, "s3r2c1" to R.drawable.child_avatar_s3r2c1, "s3r2c2" to R.drawable.child_avatar_s3r2c2,
        "s3r2c3" to R.drawable.child_avatar_s3r2c3, "s3r2c4" to R.drawable.child_avatar_s3r2c4, "s3r2c5" to R.drawable.child_avatar_s3r2c5,
        "s3r2c6" to R.drawable.child_avatar_s3r2c6, "s3r2c7" to R.drawable.child_avatar_s3r2c7, "s3r3c0" to R.drawable.child_avatar_s3r3c0,
        "s3r3c1" to R.drawable.child_avatar_s3r3c1, "s3r3c2" to R.drawable.child_avatar_s3r3c2, "s3r3c3" to R.drawable.child_avatar_s3r3c3,
        "s3r3c4" to R.drawable.child_avatar_s3r3c4, "s3r3c5" to R.drawable.child_avatar_s3r3c5, "s3r3c6" to R.drawable.child_avatar_s3r3c6,
        "s3r3c7" to R.drawable.child_avatar_s3r3c7, "s3r4c0" to R.drawable.child_avatar_s3r4c0, "s3r4c1" to R.drawable.child_avatar_s3r4c1,
        "s3r4c2" to R.drawable.child_avatar_s3r4c2, "s3r4c3" to R.drawable.child_avatar_s3r4c3, "s3r4c4" to R.drawable.child_avatar_s3r4c4,
        "s3r4c5" to R.drawable.child_avatar_s3r4c5, "s3r4c6" to R.drawable.child_avatar_s3r4c6, "s3r4c7" to R.drawable.child_avatar_s3r4c7,
        "s3r5c0" to R.drawable.child_avatar_s3r5c0, "s3r5c1" to R.drawable.child_avatar_s3r5c1, "s3r5c2" to R.drawable.child_avatar_s3r5c2,
        "s3r5c3" to R.drawable.child_avatar_s3r5c3, "s3r5c4" to R.drawable.child_avatar_s3r5c4, "s3r5c5" to R.drawable.child_avatar_s3r5c5,
        "s3r5c6" to R.drawable.child_avatar_s3r5c6, "s3r5c7" to R.drawable.child_avatar_s3r5c7, "s3r6c0" to R.drawable.child_avatar_s3r6c0,
        "s3r6c1" to R.drawable.child_avatar_s3r6c1, "s3r6c2" to R.drawable.child_avatar_s3r6c2, "s3r6c3" to R.drawable.child_avatar_s3r6c3,
        "s3r6c4" to R.drawable.child_avatar_s3r6c4, "s3r6c5" to R.drawable.child_avatar_s3r6c5, "s3r6c6" to R.drawable.child_avatar_s3r6c6,
        "s3r6c7" to R.drawable.child_avatar_s3r6c7, "s4r0c0" to R.drawable.child_avatar_s4r0c0, "s4r0c1" to R.drawable.child_avatar_s4r0c1,
        "s4r0c2" to R.drawable.child_avatar_s4r0c2, "s4r0c3" to R.drawable.child_avatar_s4r0c3, "s4r0c4" to R.drawable.child_avatar_s4r0c4,
        "s4r0c5" to R.drawable.child_avatar_s4r0c5, "s4r1c0" to R.drawable.child_avatar_s4r1c0, "s4r1c1" to R.drawable.child_avatar_s4r1c1,
        "s4r1c2" to R.drawable.child_avatar_s4r1c2, "s4r1c3" to R.drawable.child_avatar_s4r1c3, "s4r1c4" to R.drawable.child_avatar_s4r1c4,
        "s4r1c5" to R.drawable.child_avatar_s4r1c5, "s4r2c0" to R.drawable.child_avatar_s4r2c0, "s4r2c1" to R.drawable.child_avatar_s4r2c1,
        "s4r2c2" to R.drawable.child_avatar_s4r2c2, "s4r2c3" to R.drawable.child_avatar_s4r2c3, "s4r2c4" to R.drawable.child_avatar_s4r2c4,
        "s4r2c5" to R.drawable.child_avatar_s4r2c5, "s4r3c0" to R.drawable.child_avatar_s4r3c0, "s4r3c1" to R.drawable.child_avatar_s4r3c1,
        "s4r3c2" to R.drawable.child_avatar_s4r3c2, "s4r3c3" to R.drawable.child_avatar_s4r3c3, "s4r3c4" to R.drawable.child_avatar_s4r3c4,
        "s4r3c5" to R.drawable.child_avatar_s4r3c5, "s4r4c0" to R.drawable.child_avatar_s4r4c0, "s4r4c1" to R.drawable.child_avatar_s4r4c1,
        "s4r4c2" to R.drawable.child_avatar_s4r4c2, "s4r4c3" to R.drawable.child_avatar_s4r4c3, "s4r4c4" to R.drawable.child_avatar_s4r4c4,
        "s4r4c5" to R.drawable.child_avatar_s4r4c5, "s4r5c0" to R.drawable.child_avatar_s4r5c0, "s4r5c1" to R.drawable.child_avatar_s4r5c1,
        "s4r5c2" to R.drawable.child_avatar_s4r5c2, "s4r5c3" to R.drawable.child_avatar_s4r5c3, "s4r5c4" to R.drawable.child_avatar_s4r5c4,
        "s4r5c5" to R.drawable.child_avatar_s4r5c5
    )

    /** What goes into children.avatar_url for a picked face. */
    fun storedValue(id: String): String = PREFIX + id

    fun isPreset(stored: String?): Boolean = stored?.startsWith(PREFIX) == true

    /** The picked id, or null for an uploaded photo or an id this build does not know. */
    fun idOf(stored: String?): String? =
        if (isPreset(stored)) stored!!.removePrefix(PREFIX).takeIf { it in drawables } else null

    fun drawableRes(id: String): Int? = drawables[id]

    /**
     * A URI Coil loads exactly like a remote one, so every existing AsyncImage showing a child
     * photo keeps working without knowing presets exist. Uses the applicationId, not the
     * namespace — android.resource:// resolves by package, and those two differ in this app.
     */
    fun resourceUri(id: String): String? =
        drawables[id]?.let { "android.resource://${BuildConfig.APPLICATION_ID}/$it" }

    /** Null when [stored] is not a preset, so callers fall through to signing a real file. */
    fun resourceUriFor(stored: String?): String? = idOf(stored)?.let { resourceUri(it) }
}
