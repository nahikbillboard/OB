package com.example.data.vector

import com.example.data.model.RetrievedChunk
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sqrt

class VectorEngine(
    private val vectorDimension: Int = 256
) {
    private val moshi = Moshi.Builder().build()
    private val floatListType = Types.newParameterizedType(List::class.java, java.lang.Float::class.javaObjectType)
    private val jsonAdapter = moshi.adapter<List<Float>>(floatListType)

    // Stop words to downweight non-semantic tokens
    private val stopWords = setOf(
        "the", "is", "at", "which", "on", "a", "an", "and", "or", "in", "for", "with",
        "to", "of", "it", "this", "that", "by", "from", "as", "be", "was", "are", "were",
        "will", "can", "has", "have", "had", "do", "does", "did", "but", "not", "so",
        "if", "they", "we", "he", "she", "you", "i", "me", "my", "our", "your", "their"
    )

    /**
     * Generates a 256-dimensional normalized vector embedding for text using
     * subword tokenization, character n-grams (3-grams and 4-grams), and TF-IDF style term weights.
     */
    fun computeEmbedding(text: String): FloatArray {
        val vector = FloatArray(vectorDimension) { 0f }
        val tokens = tokenize(text)
        if (tokens.isEmpty()) return vector

        // 1. Unigram feature hashing
        for (token in tokens) {
            val weight = if (stopWords.contains(token)) 0.2f else 1.0f
            val h1 = abs(token.hashCode()) % vectorDimension
            val h2 = abs(token.reversed().hashCode()) % vectorDimension
            val sign = if ((token.hashCode() and 1) == 0) 1f else -1f

            vector[h1] += weight * sign
            vector[h2] += weight * 0.5f
        }

        // 2. Character n-gram hashing for subword morphological similarity
        val cleanText = text.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9 ]"), " ")
        val char3Grams = generateNGrams(cleanText, 3)
        for (gram in char3Grams) {
            val h = abs(gram.hashCode()) % vectorDimension
            vector[h] += 0.35f
        }

        val char4Grams = generateNGrams(cleanText, 4)
        for (gram in char4Grams) {
            val h = abs(gram.hashCode()) % vectorDimension
            vector[h] += 0.25f
        }

        // 3. L2 Normalize vector
        return l2Normalize(vector)
    }

    private fun tokenize(text: String): List<String> {
        return text.lowercase(Locale.ROOT)
            .split(Regex("[^a-zA-Z0-9_]+"))
            .filter { it.length > 1 }
    }

    private fun generateNGrams(text: String, n: Int): List<String> {
        if (text.length < n) return emptyList()
        val ngrams = mutableListOf<String>()
        for (i in 0..(text.length - n)) {
            val sub = text.substring(i, i + n)
            if (sub.isNotBlank()) {
                ngrams.add(sub)
            }
        }
        return ngrams
    }

    private fun l2Normalize(vector: FloatArray): FloatArray {
        var sumSquares = 0.0
        for (v in vector) {
            sumSquares += v * v
        }
        val norm = sqrt(sumSquares).toFloat()
        if (norm == 0f) return vector
        return FloatArray(vector.size) { i -> vector[i] / norm }
    }

    /**
     * Computes the Cosine Similarity between two L2-normalized vectors.
     */
    fun cosineSimilarity(v1: FloatArray, v2: FloatArray): Float {
        if (v1.isEmpty() || v2.isEmpty()) return 0f
        val len = minOf(v1.size, v2.size)
        var dot = 0f
        for (i in 0 until len) {
            dot += v1[i] * v2[i]
        }
        return dot.coerceIn(-1f, 1f)
    }

    /**
     * Serialize FloatArray to JSON string for database storage
     */
    fun vectorToJson(vector: FloatArray): String {
        val list = vector.map { it }
        return jsonAdapter.toJson(list)
    }

    /**
     * Deserialize JSON string to FloatArray
     */
    fun jsonToVector(json: String): FloatArray {
        return try {
            val list = jsonAdapter.fromJson(json) ?: emptyList()
            FloatArray(list.size) { i -> list[i] }
        } catch (e: Exception) {
            FloatArray(vectorDimension) { 0f }
        }
    }

    /**
     * Performs BM25 / Keyword overlap score for hybrid retrieval
     */
    fun keywordScore(queryTokens: Set<String>, text: String): Float {
        val textTokens = tokenize(text).toSet()
        if (textTokens.isEmpty() || queryTokens.isEmpty()) return 0f
        val intersection = queryTokens.intersect(textTokens)
        return (intersection.size.toFloat() / queryTokens.size.toFloat()).coerceIn(0f, 1f)
    }

    /**
     * Rank chunks using hybrid vector search (Cosine similarity + Keyword boosting)
     */
    fun rankChunks(
        query: String,
        chunks: List<Triple<Long, Long, Pair<String, String>>>, // chunkId, docId, Pair(text, vectorJson)
        chunkMeta: Map<Long, Triple<String, String, Pair<Int, Int>>>, // chunkId -> (docTitle, fileType, Pair(chunkIdx, wordCount))
        topK: Int = 4,
        similarityThreshold: Float = 0.20f
    ): List<RetrievedChunk> {
        val queryVector = computeEmbedding(query)
        val queryTokens = tokenize(query).filter { !stopWords.contains(it) }.toSet()

        val scoredList = mutableListOf<RetrievedChunk>()

        for (item in chunks) {
            val chunkId = item.first
            val docId = item.second
            val chunkText = item.third.first
            val chunkVectorJson = item.third.second

            val chunkVector = jsonToVector(chunkVectorJson)
            val vectorSim = cosineSimilarity(queryVector, chunkVector)
            val keyScore = keywordScore(queryTokens, chunkText)

            // Hybrid score: 75% vector cosine similarity + 25% exact keyword match
            val hybridScore = (vectorSim * 0.75f) + (keyScore * 0.25f)

            if (hybridScore >= similarityThreshold) {
                val meta = chunkMeta[chunkId]
                val docTitle = meta?.first ?: "Document"
                val fileType = meta?.second ?: "TXT"
                val chunkIdx = meta?.third?.first ?: 0
                val wordCount = meta?.third?.second ?: 0

                scoredList.add(
                    RetrievedChunk(
                        chunkId = chunkId,
                        documentId = docId,
                        documentTitle = docTitle,
                        fileType = fileType,
                        chunkIndex = chunkIdx,
                        text = chunkText,
                        similarityScore = hybridScore,
                        wordCount = wordCount
                    )
                )
            }
        }

        return scoredList.sortedByDescending { it.similarityScore }.take(topK)
    }
}
