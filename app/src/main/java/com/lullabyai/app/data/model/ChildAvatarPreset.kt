package com.lullabyai.app.data.model

import com.lullabyai.app.BuildConfig
import com.lullabyai.app.R

/**
 * The four ready-made child photos a parent can pick instead of uploading one.
 *
 * Stored in children.avatar_url as "preset:set1" rather than as a file. Four fixed images do not
 * need object storage, and a sentinel is the only form that survives every place the photo shows
 * up: the app, the LIFF pages in LINE, and the public emergency card, which has no session to
 * sign a private URL with. Each client keeps its own copy of the four files and maps the
 * sentinel to it — nongrak-web does the same in src/lib/childAvatar.ts, and the ids must match.
 */
object ChildAvatarPreset {

    const val PREFIX = "preset:"

    /** Ordered as the picker shows them. */
    val ids: List<String> = listOf("set1", "set2", "set3", "set4")

    private val drawables: Map<String, Int> = mapOf(
        "set1" to R.drawable.child_avatar_preset1,
        "set2" to R.drawable.child_avatar_preset2,
        "set3" to R.drawable.child_avatar_preset3,
        "set4" to R.drawable.child_avatar_preset4
    )

    /** What goes into children.avatar_url for a picked preset. */
    fun storedValue(id: String): String = PREFIX + id

    fun isPreset(stored: String?): Boolean = stored?.startsWith(PREFIX) == true

    /** The picked id, or null when this is an uploaded photo. */
    fun idOf(stored: String?): String? =
        if (isPreset(stored)) stored!!.removePrefix(PREFIX).takeIf { it in drawables } else null

    fun drawableRes(id: String): Int? = drawables[id]

    /**
     * A URI Coil loads exactly like a remote one, so every existing AsyncImage that shows a child
     * photo keeps working without knowing presets exist. Uses the applicationId, not the
     * namespace — android.resource:// resolves by package, and those two differ in this app.
     */
    fun resourceUri(id: String): String? =
        drawables[id]?.let { "android.resource://${BuildConfig.APPLICATION_ID}/$it" }

    /** Null when [stored] is not a preset, so callers can fall through to signing a real file. */
    fun resourceUriFor(stored: String?): String? = idOf(stored)?.let { resourceUri(it) }
}
