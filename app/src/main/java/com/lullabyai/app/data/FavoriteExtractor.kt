package com.lullabyai.app.data

import com.lullabyai.app.data.model.ChildActivity
import com.lullabyai.app.data.model.ChildActivityEntry
import com.lullabyai.app.data.model.ChildFavorite
import com.lullabyai.app.data.model.FavoriteCategory
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Finds สิ่งที่น้องชอบ in what the family has already written down.
 *
 * The AI only proposes. Everything it returns is checked here against the lines it was actually
 * sent, and a label that does not appear word for word in the line it cites is dropped — so the
 * worst a bad answer can do is add nothing. That check is the whole safeguard, which is why it
 * lives in pure code with a test, and not in a prompt that asks nicely.
 *
 * Medical rows never reach the model. A dose of syrup the child took without fuss is not a
 * favourite food, and nothing about a symptom should be turned into something to offer a child.
 */
object FavoriteExtractor {

    /** A proposal that survived validation. */
    data class Candidate(val category: String, val label: String)

    const val MAX_LINES = 60
    const val MAX_LINE_CHARS = 200
    const val MAX_PER_RUN = 5
    private const val MAX_LABEL_CHARS = 60

    /** Feed rows worth reading: what a child eats, how they slept, what a caregiver noticed. */
    private val READABLE_KINDS = setOf("food", "milk", "sleep")

    /**
     * The numbered lines sent to the model. Only rows that carry words a person chose — a milk
     * volume alone says nothing about what a child likes — and never a medical row.
     */
    fun sourceLines(feed: List<ChildActivityEntry>, activities: List<ChildActivity>): List<String> {
        val fromFeed = feed.asSequence()
            .filter { it.category != "medical" }
            .filter { it.kind in READABLE_KINDS || it.kind.startsWith("care_") }
            .mapNotNull { e ->
                // A food row's item name is the favourite itself; elsewhere only the note is prose.
                val parts = if (e.kind == "food") listOfNotNull(e.detail, e.note) else listOfNotNull(e.note)
                parts.map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" — ").takeIf { it.isNotEmpty() }
            }
        val fromActivities = activities.asSequence().mapNotNull { a ->
            listOfNotNull(a.title, a.locationName, a.notes)
                .map { it.trim() }.filter { it.isNotEmpty() }
                .joinToString(" — ").takeIf { it.isNotEmpty() }
        }
        return (fromFeed + fromActivities)
            .map { it.replace(Regex("\\s+"), " ").take(MAX_LINE_CHARS) }
            .distinct()
            .take(MAX_LINES)
            .toList()
    }

    val SYSTEM = """
You read short notes a family wrote about their young child and pick out things the child LIKES
or that CALM the child. You never guess, and you never infer anything from illness or medicine.

Categories (use exactly these ids):
toy = a toy or comfort object; food = a food or flavour; sound = a song or calming sound;
activity = a favourite activity or place; soothing = something that settles the child when upset;
interaction = a phrase, game or way of being talked to that the child responds to.

Rules:
- Only include something the note says the child enjoys, loves, wants, calms down with, or asks for.
- "label" must be copied WORD FOR WORD from the line, as short as possible (2-6 words).
- "line" is the number of the line you copied it from.
- Skip anything already in the "already recorded" list.
- If nothing qualifies, return {"items": []}.

Reply with JSON only: {"items":[{"category":"food","label":"...","line":3}]}
""".trimIndent()

    fun prompt(childName: String, lines: List<String>, existing: List<ChildFavorite>): String = buildString {
        appendLine("Child: ${childName.ifBlank { "the child" }}")
        if (existing.isNotEmpty()) {
            appendLine("Already recorded: " + existing.joinToString("; ") { "${it.category}: ${it.label}" })
        }
        appendLine()
        appendLine("Notes:")
        lines.forEachIndexed { i, l -> appendLine("${i + 1}. $l") }
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun norm(s: String) = s.lowercase().replace(Regex("\\s+"), " ").trim()

    /** The items array, whether or not the model wrapped its JSON in a code fence. */
    private fun itemsOf(raw: String): JsonArray? {
        val candidates = listOf(
            raw.trim(),
            raw.substringAfter("```json", "").substringBefore("```").trim(),
            raw.substringAfter("```", "").substringBefore("```").trim(),
            raw.substring(raw.indexOf('{').coerceAtLeast(0), (raw.lastIndexOf('}') + 1).coerceAtLeast(0))
        )
        for (c in candidates) {
            if (c.isEmpty()) continue
            val arr = runCatching { json.parseToJsonElement(c).jsonObject["items"]?.jsonArray }.getOrNull()
            if (arr != null) return arr
        }
        return null
    }

    /**
     * Keeps only proposals that are well formed, grounded in the cited line, and new.
     *
     * Returns an empty list — never throws — for anything unparseable: a failed scan is simply a
     * day with nothing to add, and it must not interrupt a parent who did not ask for it.
     */
    fun parse(raw: String?, lines: List<String>, existing: List<ChildFavorite>): List<Candidate> {
        if (raw.isNullOrBlank()) return emptyList()
        val items = itemsOf(raw) ?: return emptyList()

        val seen = existing.map { it.category to norm(it.label) }.toMutableSet()
        val out = mutableListOf<Candidate>()
        for (el in items) {
            val o = runCatching { el.jsonObject }.getOrNull() ?: continue
            val category = o["category"]?.jsonPrimitive?.contentOrNull?.trim() ?: continue
            val label = o["label"]?.jsonPrimitive?.contentOrNull?.trim()?.replace(Regex("\\s+"), " ") ?: continue
            val lineNo = o["line"]?.jsonPrimitive?.intOrNull ?: continue

            if (category !in FavoriteCategory.ordered) continue
            if (label.length !in 2..MAX_LABEL_CHARS) continue
            val cited = lines.getOrNull(lineNo - 1) ?: continue
            // The one check that matters: the words must be in the note the model says they came from.
            if (norm(label) !in norm(cited)) continue
            val key = category to norm(label)
            if (key in seen) continue
            seen += key
            out += Candidate(category, label)
            if (out.size == MAX_PER_RUN) break
        }
        return out
    }
}
