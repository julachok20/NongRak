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

    /**
     * True when the account has several children and this message names none of them.
     *
     * The selected child is still used, because answering with no child context at all is worse.
     * But the AI must not present a guess as fact: with three children, "วัคซีนถัดไปคือ..." about
     * whichever happened to be selected is confidently wrong two times in three. The caller passes
     * this on so the reply asks which child instead.
     */
    fun isAmbiguous(text: String, children: List<Child>, current: Child?): Boolean {
        if (children.size < 2 || text.isBlank()) return false
        return resolve(text, children, null) == null && current != null
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

    /**
     * Instruction for the model when the account has more than one child.
     *
     * The Child Info tab's selection is a screen default for records, not the subject of the
     * conversation. Without this, the prompt only contains that one child and every answer
     * sounds as if the others do not exist.
     */
    fun familyPreamble(children: List<Child>, selected: Child?, isThai: Boolean): String {
        if (children.size < 2) return ""
        val names = children.joinToString(if (isThai) ", " else ", ") { it.name }
        val tab = selected?.name?.takeIf { it.isNotBlank() }
        return if (isThai) {
            "ครอบครัวนี้มีลูก ${children.size} คน: $names\n" +
                (if (tab != null) "แท็บข้อมูลลูกที่เปิดอยู่คือ $tab — นั่นเป็นแค่หน้าจอแอป ไม่ได้หมายความว่าคำถามเกี่ยวกับคนนั้น\n" else "") +
                "พิจารณาลูกทุกคนเสมอ ถ้าผู้ใช้เอ่ยชื่อหรือพูดถึงคนใดในเสียง ให้ใช้เด็กคนนั้น " +
                "ถ้าไม่ได้ระบุชื่อ ให้ตอบโดยคำนึงถึงทุกคน ห้ามสมมติว่าหมายถึงเด็กในแท็บข้อมูลลูก\n"
        } else {
            "This family has ${children.size} children: $names\n" +
                (if (tab != null) "The Child Info tab is currently on $tab — that is only the app screen, not the subject of the question.\n" else "") +
                "Always consider every child. If the parent names one, use that child. " +
                "If they do not, answer with all of them in mind. Do not assume they mean the Child Info tab selection.\n"
        }
    }
}
