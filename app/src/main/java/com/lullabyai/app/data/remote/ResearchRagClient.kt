package com.lullabyai.app.data.remote

import com.lullabyai.app.BuildConfig
import io.github.jan.supabase.auth.auth
import io.ktor.client.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*

/**
 * Retrieves relevant chunks from the curated child-development knowledge base (the "research-rag"
 * Edge Function, backed by pgvector). Used to ground chatbot answers and cite sources.
 */
object ResearchRagClient {

    data class Chunk(val title: String, val source: String, val content: String, val similarity: Double)

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val client = HttpClient(Android) {
        install(HttpTimeout) { requestTimeoutMillis = 20_000; socketTimeoutMillis = 20_000 }
    }
    private val functionUrl = "${BuildConfig.SUPABASE_URL.trimEnd('/')}/functions/v1/research-rag"

    /** Top-[k] knowledge chunks for [query], most-similar first. Empty on any failure (RAG is optional). */
    suspend fun search(query: String, k: Int = 4): List<Chunk> = runCatching {
        val token = runCatching { supabase.auth.currentAccessTokenOrNull() }.getOrNull()
        val raw = client.post(functionUrl) {
            header("apikey", BuildConfig.SUPABASE_ANON_KEY)
            if (!token.isNullOrBlank()) header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject {
                put("action", "search")
                put("query", query)
                put("k", k)
            }.toString())
        }.bodyAsText()

        val root = json.parseToJsonElement(raw).jsonObject
        val chunks = root["chunks"]?.jsonArray ?: return@runCatching emptyList()
        chunks.mapNotNull { el ->
            val o = el.jsonObject
            val content = o["content"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            Chunk(
                title = o["title"]?.jsonPrimitive?.contentOrNull ?: "",
                source = o["source"]?.jsonPrimitive?.contentOrNull ?: "",
                content = content,
                similarity = o["similarity"]?.jsonPrimitive?.doubleOrNull ?: 0.0
            )
        }
    }.getOrDefault(emptyList())
}
