package com.lullabyai.app.util

import com.lullabyai.app.data.model.AgentAction
import com.lullabyai.app.data.model.NeedInfoRequest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private val ACTION_BLOCK_REGEX = Regex("<AGENT_ACTION>(.*?)</AGENT_ACTION>", RegexOption.DOT_MATCHES_ALL)
private val NEED_INFO_BLOCK_REGEX = Regex("<NEED_INFO>(.*?)</NEED_INFO>", RegexOption.DOT_MATCHES_ALL)
private val QUERY_BLOCK_REGEX = Regex("<AI_QUERY>(.*?)</AI_QUERY>", RegexOption.DOT_MATCHES_ALL)
private val json = Json { ignoreUnknownKeys = true }

/**
 * A request to READ something before answering.
 *
 * Reads are kept separate from [AgentAction] because the two need opposite handling: a read runs
 * immediately and unattended, and its result goes back to the model so it can carry on reasoning,
 * while a write waits for the parent to tap confirm. Row-level security is what makes running
 * reads unattended safe — a query can only return what this user could have opened by hand.
 */
data class AgentQuery(
    val tool: String,
    val params: Map<String, String> = emptyMap()
)

/**
 * Everything one AI reply asked for.
 *
 * A turn may carry several actions — "log the feed and remind me at six" is two — and several
 * queries, since answering "how was this week?" may need sleep and feeds together. The previous
 * single-block parser silently dropped everything after the first, so a two-part instruction half
 * happened with nothing to show the rest had been lost.
 */
data class AgentTurn(
    val displayText: String,
    val actions: List<AgentAction> = emptyList(),
    val queries: List<AgentQuery> = emptyList(),
    val needInfo: NeedInfoRequest? = null
) {
    /** True when the model is mid-plan: it asked for data and has not answered yet. */
    val wantsMoreWork: Boolean get() = queries.isNotEmpty()
}

/**
 * Splits the AI's raw reply into visible text plus every block it appended.
 *
 * The chat bubble always shows clean prose; the blocks become confirmation cards, a choice popup,
 * or data fetches the parent never sees.
 */
fun parseAgentTurn(raw: String): AgentTurn {
    var displayText = raw

    val actions = ACTION_BLOCK_REGEX.findAll(raw).mapNotNull { m ->
        displayText = displayText.replace(m.value, "")
        runCatching { json.decodeFromString<AgentAction>(m.groupValues[1].trim()) }.getOrNull()
    }.toList()

    val queries = QUERY_BLOCK_REGEX.findAll(raw).mapNotNull { m ->
        displayText = displayText.replace(m.value, "")
        runCatching {
            val obj = json.parseToJsonElement(m.groupValues[1].trim()) as? JsonObject
                ?: return@runCatching null
            val tool = (obj["tool"] as? JsonPrimitive)?.content ?: return@runCatching null
            val params = (obj["params"] as? JsonObject)
                ?.mapValues { (_, v) -> (v as? JsonPrimitive)?.content.orEmpty() }
                .orEmpty()
            AgentQuery(tool, params)
        }.getOrNull()
    }.toList()

    val needInfo = NEED_INFO_BLOCK_REGEX.find(raw)?.let { m ->
        displayText = displayText.replace(m.value, "")
        runCatching { json.decodeFromString<NeedInfoRequest>(m.groupValues[1].trim()) }.getOrNull()
    }

    return AgentTurn(displayText.trim(), actions, queries, needInfo)
}

/**
 * The original single-block form, kept so existing callers are unaffected.
 *
 * Returns the FIRST action only — use [parseAgentTurn] anywhere that should handle a reply asking
 * for two things at once.
 */
fun parseAgentAction(raw: String): Triple<String, AgentAction?, NeedInfoRequest?> {
    val turn = parseAgentTurn(raw)
    return Triple(turn.displayText, turn.actions.firstOrNull(), turn.needInfo)
}

/**
 * What the chat bubble shows for a finished reply.
 *
 * Blocks are stripped as in [parseAgentTurn]. Missing info is asked in plain chat now; a model that
 * still answers with the old <NEED_INFO> block gets its question shown as text, rather than lost
 * along with the popup that used to display it.
 */
fun chatVisibleText(raw: String): String {
    val turn = parseAgentTurn(raw)
    val question = turn.needInfo?.question?.trim().orEmpty()
    return listOf(turn.displayText, question).filter { it.isNotBlank() }.joinToString("\n\n")
}

private val BLOCK_OPENERS = listOf("<AGENT_ACTION>", "<NEED_INFO>", "<AI_QUERY>")

/**
 * The part of a reply that can be shown while it is still arriving.
 *
 * A block streams in over several pieces, so the bubble would flash `<AGENT_` and half-written
 * JSON. Everything from the first block opener — or from a trailing "<" that could still become
 * one — is held back; [parseAgentTurn] handles the finished reply.
 */
fun visibleWhileStreaming(partial: String): String {
    var i = partial.indexOf('<')
    while (i >= 0) {
        val rest = partial.substring(i)
        if (BLOCK_OPENERS.any { rest.startsWith(it) || it.startsWith(rest) }) return partial.substring(0, i).trimEnd()
        i = partial.indexOf('<', i + 1)
    }
    return partial
}
