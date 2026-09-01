package com.example.data.remote

import com.example.data.model.RagSettings
import com.example.data.model.RetrievedChunk
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Locale
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class GeminiPart(
    @Json(name = "text") val text: String? = null
)

@JsonClass(generateAdapter = true)
data class GeminiContent(
    @Json(name = "role") val role: String? = null,
    @Json(name = "parts") val parts: List<GeminiPart>
)

@JsonClass(generateAdapter = true)
data class GeminiGenerationConfig(
    @Json(name = "temperature") val temperature: Float? = null,
    @Json(name = "topP") val topP: Float? = null,
    @Json(name = "topK") val topK: Int? = null
)

@JsonClass(generateAdapter = true)
data class GeminiGenerateContentRequest(
    @Json(name = "contents") val contents: List<GeminiContent>,
    @Json(name = "systemInstruction") val systemInstruction: GeminiContent? = null,
    @Json(name = "generationConfig") val generationConfig: GeminiGenerationConfig? = null
)

@JsonClass(generateAdapter = true)
data class GeminiCandidate(
    @Json(name = "content") val content: GeminiContent? = null,
    @Json(name = "finishReason") val finishReason: String? = null
)

@JsonClass(generateAdapter = true)
data class GeminiGenerateContentResponse(
    @Json(name = "candidates") val candidates: List<GeminiCandidate>? = null,
    @Json(name = "error") val error: GeminiError? = null
)

@JsonClass(generateAdapter = true)
data class GeminiError(
    @Json(name = "code") val code: Int? = null,
    @Json(name = "message") val message: String? = null,
    @Json(name = "status") val status: String? = null
)

class GeminiRagService {
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val requestAdapter = moshi.adapter(GeminiGenerateContentRequest::class.java)
    private val responseAdapter = moshi.adapter(GeminiGenerateContentResponse::class.java)

    /**
     * Test if a Gemini API key is active and valid
     */
    suspend fun testApiKey(apiKey: String): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("API Key cannot be empty."))
        }
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"
        val requestObj = GeminiGenerateContentRequest(
            contents = listOf(
                GeminiContent(
                    role = "user",
                    parts = listOf(GeminiPart(text = "Respond with 'API Key Active and Valid' only."))
                )
            ),
            generationConfig = GeminiGenerationConfig(temperature = 0.1f)
        )
        val jsonPayload = requestAdapter.toJson(requestObj)
        val body = jsonPayload.toRequestBody("application/json; charset=utf-8".toMediaType())
        val httpRequest = Request.Builder().url(url).post(body).build()

        try {
            val response = okHttpClient.newCall(httpRequest).execute()
            val rawResponse = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                val errorObj = try { responseAdapter.fromJson(rawResponse)?.error } catch (e: Exception) { null }
                val errorMsg = errorObj?.message ?: "HTTP ${response.code}: ${response.message}"
                return@withContext Result.failure(Exception(errorMsg))
            }
            val parsed = responseAdapter.fromJson(rawResponse)
            val reply = parsed?.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: "Key validated successfully."
            Result.success(reply.trim())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Generates a grounded RAG response using Gemini API with retrieved document context
     */
    suspend fun generateRagResponse(
        query: String,
        retrievedChunks: List<RetrievedChunk>,
        apiKey: String,
        settings: RagSettings
    ): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("No API Key provided."))
        }

        val model = if (settings.geminiModel.isNotBlank()) settings.geminiModel else "gemini-3.5-flash"
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"

        val contextBuilder = StringBuilder()
        if (retrievedChunks.isNotEmpty()) {
            contextBuilder.append("=== RETRIEVED USER DOCUMENT CONTEXT ===\n\n")
            retrievedChunks.forEachIndexed { idx, chunk ->
                contextBuilder.append("[Source ${idx + 1} | Document: \"${chunk.documentTitle}\" (${chunk.fileType}) | Match Score: ${(chunk.similarityScore * 100).toInt()}%]\n")
                contextBuilder.append("${chunk.text}\n\n")
            }
        } else {
            contextBuilder.append("(No matching document chunks found in local vector database above threshold)\n")
        }

        val systemPrompt = """
            You are DocuMind AI, an intelligent, privacy-first knowledge assistant.
            Your role is to accurately answer the user's questions based on the retrieved context from their uploaded personal files (PDF, DOCX, TXT, and Notes).
            
            Guidelines:
            1. Ground your answers strictly in the provided retrieved document sources whenever relevant.
            2. Explicitly cite your sources using inline citations like [Source 1], [Source 2] or document names.
            3. If the context does not contain enough information to fully answer the query, clearly state what information is present in their database and what is missing.
            4. Keep answers structured, polite, easy to scan with bullet points or bold headers.
        """.trimIndent()

        val userPrompt = """
            $contextBuilder
            
            === USER QUESTION ===
            $query
        """.trimIndent()

        val requestObj = GeminiGenerateContentRequest(
            contents = listOf(
                GeminiContent(
                    role = "user",
                    parts = listOf(GeminiPart(text = userPrompt))
                )
            ),
            systemInstruction = GeminiContent(
                parts = listOf(GeminiPart(text = systemPrompt))
            ),
            generationConfig = GeminiGenerationConfig(
                temperature = 0.3f,
                topP = 0.95f
            )
        )

        val jsonPayload = requestAdapter.toJson(requestObj)
        val body = jsonPayload.toRequestBody("application/json; charset=utf-8".toMediaType())
        val httpRequest = Request.Builder().url(url).post(body).build()

        try {
            val response = okHttpClient.newCall(httpRequest).execute()
            val rawResponse = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                val errorObj = try { responseAdapter.fromJson(rawResponse)?.error } catch (e: Exception) { null }
                val errorMsg = errorObj?.message ?: "API Error ${response.code}"
                return@withContext Result.failure(Exception(errorMsg))
            }
            val parsed = responseAdapter.fromJson(rawResponse)
            val answer = parsed?.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (answer != null) {
                Result.success(answer.trim())
            } else {
                Result.failure(Exception("Empty candidate response from Gemini API."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Offline local extractive RAG synthesizer when user is offline or has no API key
     */
    fun generateOfflineExtractiveResponse(
        query: String,
        retrievedChunks: List<RetrievedChunk>
    ): String {
        if (retrievedChunks.isEmpty()) {
            return "No matching information was found in your local database for \"$query\".\n\n" +
                    "• Try adding documents (.pdf, .docx, .txt) or entering notes in the Knowledge Base tab.\n" +
                    "• You can also lower the similarity threshold in Settings to broaden search results."
        }

        val sb = StringBuilder()
        sb.append("### Local Offline Vector Search Results\n\n")
        sb.append("Synthesized from **${retrievedChunks.size} relevant document excerpt(s)** stored on your device:\n\n")

        val queryTerms = query.lowercase(Locale.ROOT).split(Regex("[^a-zA-Z0-9]+")).filter { it.length > 2 }

        retrievedChunks.forEachIndexed { index, chunk ->
            val matchPercent = (chunk.similarityScore * 100).toInt()
            sb.append("**Source [${index + 1}]: ${chunk.documentTitle}** (${chunk.fileType} • ${matchPercent}% match)\n")

            // Extract best matching sentences
            val sentences = chunk.text.split(Regex("(?<=[.!?])\\s+")).filter { it.isNotBlank() }
            val topSentences = sentences.sortedByDescending { s ->
                val lowerS = s.lowercase(Locale.ROOT)
                queryTerms.count { lowerS.contains(it) }
            }.take(3)

            if (topSentences.isNotEmpty()) {
                topSentences.forEach { sentence ->
                    sb.append("> \"${sentence.trim()}\"\n")
                }
            } else {
                val preview = if (chunk.text.length > 200) chunk.text.take(200) + "..." else chunk.text
                sb.append("> \"${preview.trim()}\"\n")
            }
            sb.append("\n")
        }

        sb.append("---\n")
        sb.append("💡 *Tip: Add a Gemini API key in Settings to enable natural language conversational answers grounded with these sources.*")

        return sb.toString()
    }
}
