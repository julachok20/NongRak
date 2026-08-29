package com.lullabyai.app.util

import com.lullabyai.app.data.model.Child

/**
 * Works out which child a message is about.
 *
 * A carer with more than one child says "กีก้ากินยังไง" without first switching the app to Giga,
 * and answering about the selected child instead is worse than useless — it is confidently wrong
 * about the wrong baby. Naming a child in the sentence picks that child for this message only; the
 * app's own selection is untouched.
 */
object ChildMention {

    /** Shorter than this matches by accident — a two-letter fragment appears inside other words. */
    private const val MIN_NAME_LENGTH = 2

    /**
     * The child named in [text], or [fallback] when nobody is named.
     *
     * Deliberately conservative. With one child there is nothing to disambiguate, and a sentence
     * naming two children keeps the current selection rather than guessing — being asked which one
     * beats silently answering about the other.
     */
    fun resolve(text: String, children: List<Child>, fallback: Child?): Child? {
        if (children.size < 2 || text.isBlank()) return fallback

        val mentioned = children.filter { child ->
            namesFor(child).any { text.contains(it, ignoreCase = true) }
        }
        return if (mentioned.size == 1) mentioned.first() else fallback
    }

    /** The child named in [text] when it is NOT [current] — so the caller can say it switched. */
    fun switchedAwayFrom(text: String, children: List<Child>, current: Child?): Child? {
        val resolved = resolve(text, children, current)
        return if (resolved != null && resolved.id != current?.id) resolved else null
    }

    /**
     * Every way a carer might refer to this child.
     *
     * The full name plus each word of it, because "น้องกีก้า ใจดี" gets said as "กีก้า", plus the
     * nickname the profile stores in notes as "ชื่อเล่น:".
     */
    private fun namesFor(child: Child): List<String> {
        val out = mutableListOf<String>()
        val full = child.name.trim()
        if (full.length >= MIN_NAME_LENGTH) out += full
        full.split(' ', ' ').forEach { part ->
            val p = part.trim()
            if (p.length >= MIN_NAME_LENGTH) out += p
        }
        val notes = child.notes.orEmpty()
        if (notes.startsWith("ชื่อเล่น:")) {
            val nick = notes.substringAfter("ชื่อเล่น:").trim().lineSequence().firstOrNull()?.trim()
            if (nick != null && nick.length >= MIN_NAME_LENGTH) out += nick
        }
        return out.distinct()
    }
}
