package com.example.data.vector

class TextChunker {

    data class Chunk(
        val index: Int,
        val text: String,
        val wordCount: Int
    )

    fun chunkText(
        text: String,
        chunkSizeWords: Int = 250,
        chunkOverlapWords: Int = 40
    ): List<Chunk> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return emptyList()

        // Split text by paragraphs first
        val paragraphs = trimmed.split(Regex("\n{2,}")).map { it.trim() }.filter { it.isNotEmpty() }
        val wordsList = mutableListOf<String>()

        for (para in paragraphs) {
            val wordsInPara = para.split(Regex("\\s+")).filter { it.isNotBlank() }
            wordsList.addAll(wordsInPara)
            // Add paragraph boundary indicator
            wordsList.add("\n\n")
        }

        val step = (chunkSizeWords - chunkOverlapWords).coerceAtLeast(1)
        val chunks = mutableListOf<Chunk>()
        var chunkIndex = 0
        var start = 0

        val actualWords = wordsList.filter { it != "\n\n" }
        if (actualWords.isEmpty()) return emptyList()

        if (actualWords.size <= chunkSizeWords) {
            return listOf(
                Chunk(
                    index = 0,
                    text = trimmed,
                    wordCount = actualWords.size
                )
            )
        }

        while (start < actualWords.size) {
            val end = (start + chunkSizeWords).coerceAtMost(actualWords.size)
            val chunkWords = actualWords.subList(start, end)
            val chunkString = chunkWords.joinToString(" ")

            chunks.add(
                Chunk(
                    index = chunkIndex++,
                    text = chunkString,
                    wordCount = chunkWords.size
                )
            )

            if (end >= actualWords.size) break
            start += step
        }

        return chunks
    }
}
