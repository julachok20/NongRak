package com.lullabyai.app.data.model

import com.lullabyai.app.BuildConfig
import com.lullabyai.app.R

/**
 * รูปสำเร็จรูป — the ready-made faces a parent can pick instead of uploading a photo.
 *
 * The artwork arrives as sheets of many faces; each is cut to its circle and shipped as one file. Stored in children.avatar_url as "preset:a03".
 *
 * Bundled rather than uploaded: a few dozen small fixed images do not need object storage, and a
 * sentinel is the only form that survives every place the photo shows up — the app, the LIFF
 * pages in LINE, and the public emergency card, which has no session to sign a private URL with.
 * nongrak-web keeps the same ids in src/lib/childAvatar.ts; **the ids are the contract.**
 */
object ChildAvatarPreset {

    const val PREFIX = "preset:"

    /**
     * The picker order: everyday faces, accessories, costumes, then themes and seasons.
     *
     * Cut from the two expression sheets strictly to each circle — the ring is the border, and a
     * crown or an ear reaching past it is cut off. 47 of the 85 are kept: faces nobody picks as a
     * child's photo (crying, angry, dizzy) are out, and where both sheets draw the same thing the
     * sharper Sheet1 crop is kept.
     */
    val ids: List<String> = listOf(
        "a00", "a01", "a02", "a03", "a04", "a05", "a12", "a13",
        "a14", "b02", "b13", "b24", "a20", "a21", "a22", "a23",
        "a24", "a25", "b45", "a50", "a52", "a53", "a30", "a31",
        "a32", "a33", "a34", "a35", "b35", "b36", "a54", "a51",
        "a40", "a41", "a42", "a43", "a44", "a45", "a55", "b46",
        "b51", "b53", "b55", "b56", "b62", "b65", "b66"
    )

    private val drawables: Map<String, Int> = mapOf(
        "a00" to R.drawable.child_avatar_a00, "a01" to R.drawable.child_avatar_a01, "a02" to R.drawable.child_avatar_a02,
        "a03" to R.drawable.child_avatar_a03, "a04" to R.drawable.child_avatar_a04, "a05" to R.drawable.child_avatar_a05,
        "a12" to R.drawable.child_avatar_a12, "a13" to R.drawable.child_avatar_a13, "a14" to R.drawable.child_avatar_a14,
        "b02" to R.drawable.child_avatar_b02, "b13" to R.drawable.child_avatar_b13, "b24" to R.drawable.child_avatar_b24,
        "a20" to R.drawable.child_avatar_a20, "a21" to R.drawable.child_avatar_a21, "a22" to R.drawable.child_avatar_a22,
        "a23" to R.drawable.child_avatar_a23, "a24" to R.drawable.child_avatar_a24, "a25" to R.drawable.child_avatar_a25,
        "b45" to R.drawable.child_avatar_b45, "a50" to R.drawable.child_avatar_a50, "a52" to R.drawable.child_avatar_a52,
        "a53" to R.drawable.child_avatar_a53, "a30" to R.drawable.child_avatar_a30, "a31" to R.drawable.child_avatar_a31,
        "a32" to R.drawable.child_avatar_a32, "a33" to R.drawable.child_avatar_a33, "a34" to R.drawable.child_avatar_a34,
        "a35" to R.drawable.child_avatar_a35, "b35" to R.drawable.child_avatar_b35, "b36" to R.drawable.child_avatar_b36,
        "a54" to R.drawable.child_avatar_a54, "a51" to R.drawable.child_avatar_a51, "a40" to R.drawable.child_avatar_a40,
        "a41" to R.drawable.child_avatar_a41, "a42" to R.drawable.child_avatar_a42, "a43" to R.drawable.child_avatar_a43,
        "a44" to R.drawable.child_avatar_a44, "a45" to R.drawable.child_avatar_a45, "a55" to R.drawable.child_avatar_a55,
        "b46" to R.drawable.child_avatar_b46, "b51" to R.drawable.child_avatar_b51, "b53" to R.drawable.child_avatar_b53,
        "b55" to R.drawable.child_avatar_b55, "b56" to R.drawable.child_avatar_b56, "b62" to R.drawable.child_avatar_b62,
        "b65" to R.drawable.child_avatar_b65, "b66" to R.drawable.child_avatar_b66
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
