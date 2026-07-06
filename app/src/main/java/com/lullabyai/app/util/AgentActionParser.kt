package com.lullabyai.app.util

import com.lullabyai.app.data.model.AgentAction
import com.lullabyai.app.data.model.NeedInfoRequest
import kotlinx.serialization.json.Json

private val ACTION_BLOCK_REGEX = Regex("<AGENT_ACTION>(.*?)</AGENT_ACTION>", RegexOption.DOT_MATCHES_ALL)
private val NEED_INFO_BLOCK_REGEX = Regex("<NEED_INFO>(.*?)</NEED_INFO>", RegexOption.DOT_MATCHES_ALL)
private val json = Json { ignoreUnknownKeys = true }

/**
 * Splits the AI's raw chat reply into (displayText, action, needInfo). The model is instructed
 * (RelationshipLabels.chatSystemPrompt) to append at most one of these blocks:
 * - <AGENT_ACTION>{...}</AGENT_ACTION> when the user asked to log/edit/delete data
 * - <NEED_INFO>{...}</NEED_INFO> when the AI needs one piece of missing info to analyze further
 * Either way the chat bubble shows clean text; the block becomes a confirmation card or a popup.
 */
fun parseAgentAction(raw: String): Triple<String, AgentAction?, NeedInfoRequest?> {
    val actionMatch = ACTION_BLOCK_REGEX.find(raw)
    val needInfoMatch = NEED_INFO_BLOCK_REGEX.find(raw)
    val action = actionMatch?.let { runCatching { json.decodeFromString<AgentAction>(it.groupValues[1].trim()) }.getOrNull() }
    val needInfo = needInfoMatch?.let { runCatching { json.decodeFromString<NeedInfoRequest>(it.groupValues[1].trim()) }.getOrNull() }
    var displayText = raw
    actionMatch?.let { displayText = displayText.replace(it.value, "") }
    needInfoMatch?.let { displayText = displayText.replace(it.value, "") }
    return Triple(displayText.trim(), action, needInfo)
}
