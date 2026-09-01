package com.example

import com.example.data.vector.TextChunker
import com.example.data.vector.VectorEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VectorEngineUnitTest {

    private val vectorEngine = VectorEngine()
    private val chunker = TextChunker()

    @Test
    fun testTextChunking_splitsCorrectly() {
        val longText = (1..100).joinToString(" ") { "word$it" }
        val chunks = chunker.chunkText(longText, chunkSizeWords = 30, chunkOverlapWords = 10)

        assertTrue(chunks.isNotEmpty())
        assertTrue(chunks.size > 1)
        assertEquals(0, chunks[0].index)
    }

    @Test
    fun testVectorEmbedding_similarTextsHaveHighCosineSimilarity() {
        val text1 = "Retrieval augmented generation with vector database search on local device"
        val text2 = "Vector database search and retrieval augmented generation on device"
        val text3 = "Baking chocolate chip cookies with butter and sugar"

        val vec1 = vectorEngine.computeEmbedding(text1)
        val vec2 = vectorEngine.computeEmbedding(text2)
        val vec3 = vectorEngine.computeEmbedding(text3)

        val sim12 = vectorEngine.cosineSimilarity(vec1, vec2)
        val sim13 = vectorEngine.cosineSimilarity(vec1, vec3)

        assertTrue("Semantically similar texts should score higher ($sim12 vs $sim13)", sim12 > sim13)
        assertTrue("Cosine similarity should be positive for related text", sim12 > 0.4f)
    }

    @Test
    fun testVectorSerialization_roundTrip() {
        val text = "DocuMind privacy focused local vector engine"
        val vec = vectorEngine.computeEmbedding(text)
        val json = vectorEngine.vectorToJson(vec)
        val deserialized = vectorEngine.jsonToVector(json)

        assertEquals(vec.size, deserialized.size)
        for (i in vec.indices) {
            assertEquals(vec[i], deserialized[i], 0.0001f)
        }
    }
}
