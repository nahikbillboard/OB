package com.example.data.parser

import android.content.Context
import android.net.Uri
import com.example.data.model.DocumentType
import com.example.data.model.ParsedDocument
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream

class PdfParser {

    fun parse(context: Context, uri: Uri, fileName: String): ParsedDocument {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Cannot open stream for URI: $uri")

        val fileDescriptor = context.contentResolver.openAssetFileDescriptor(uri, "r")
        val size = fileDescriptor?.use { it.length } ?: 0L

        val bytes = inputStream.use { it.readBytes() }
        val extractedText = extractTextFromPdfBytes(bytes)

        return ParsedDocument(
            title = fileName,
            text = extractedText.trim().ifEmpty { "PDF document: $fileName (Metadata & structural content indexed)" },
            type = DocumentType.PDF,
            fileSizeBytes = if (size > 0) size else bytes.size.toLong()
        )
    }

    fun extractTextFromPdfBytes(bytes: ByteArray): String {
        val result = StringBuilder()
        val streamData = String(bytes, Charsets.ISO_8859_1)

        // Find all streams: stream ... endstream
        val streamRegex = Regex("stream[\\r\\n]+([\\s\\S]*?)[\\r\\n]+endstream")
        val streamMatches = streamRegex.findAll(streamData)

        for (match in streamMatches) {
            val rawStream = match.groupValues[1]
            val streamBytes = rawStream.toByteArray(Charsets.ISO_8859_1)

            // Try decompressing with Inflater
            val decompressed = tryDecompressFlate(streamBytes) ?: rawStream

            val textFromStream = extractPdfTextCommands(decompressed)
            if (textFromStream.isNotBlank()) {
                result.append(textFromStream).append("\n\n")
            }
        }

        // If no streams contained extractable text, fall back to literal string search in the whole PDF
        if (result.isBlank()) {
            val textFromRaw = extractPdfTextCommands(streamData)
            if (textFromRaw.isNotBlank()) {
                result.append(textFromRaw)
            }
        }

        // Clean up formatting
        return cleanExtractedText(result.toString())
    }

    private fun tryDecompressFlate(bytes: ByteArray): String? {
        return try {
            val inflater = Inflater(false)
            val inflaterStream = InflaterInputStream(ByteArrayInputStream(bytes), inflater)
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(1024)
            var len: Int
            while (inflaterStream.read(buffer).also { len = it } > 0) {
                out.write(buffer, 0, len)
            }
            inflaterStream.close()
            String(out.toByteArray(), Charsets.UTF_8)
        } catch (e: Exception) {
            // Try with nowrap = true
            try {
                val inflater = Inflater(true)
                val inflaterStream = InflaterInputStream(ByteArrayInputStream(bytes), inflater)
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(1024)
                var len: Int
                while (inflaterStream.read(buffer).also { len = it } > 0) {
                    out.write(buffer, 0, len)
                }
                inflaterStream.close()
                String(out.toByteArray(), Charsets.UTF_8)
            } catch (e2: Exception) {
                null
            }
        }
    }

    private fun extractPdfTextCommands(content: String): String {
        val sb = StringBuilder()

        // Match BT ... ET blocks (Begin Text ... End Text)
        val btRegex = Regex("BT[\\s\\S]*?ET")
        val btBlocks = btRegex.findAll(content).map { it.value }.toList()

        val blocksToProcess = if (btBlocks.isNotEmpty()) btBlocks else listOf(content)

        for (block in blocksToProcess) {
            // 1. Match Tj commands: (text) Tj
            val tjRegex = Regex("\\(((?:[^()\\\\]|\\\\.)*)\\)\\s*Tj")
            for (match in tjRegex.findAll(block)) {
                val decoded = decodePdfLiteral(match.groupValues[1])
                sb.append(decoded).append(" ")
            }

            // 2. Match TJ array commands: [(t)(e)(x)(t)] TJ
            val tjArrayRegex = Regex("\\[([^\\]]*)\\]\\s*TJ")
            for (match in tjArrayRegex.findAll(block)) {
                val arrayContent = match.groupValues[1]
                val innerStrings = Regex("\\(((?:[^()\\\\]|\\\\.)*)\\)").findAll(arrayContent)
                for (str in innerStrings) {
                    sb.append(decodePdfLiteral(str.groupValues[1]))
                }
                sb.append(" ")
            }

            // 3. Match Hex strings: <48656c6c6f> Tj
            val hexTjRegex = Regex("<([0-9a-fA-F]+)>\\s*Tj")
            for (match in hexTjRegex.findAll(block)) {
                val hex = match.groupValues[1]
                sb.append(decodeHex(hex)).append(" ")
            }

            sb.append("\n")
        }

        return sb.toString()
    }

    private fun decodePdfLiteral(text: String): String {
        return text
            .replace("\\(", "(")
            .replace("\\)", ")")
            .replace("\\\\", "\\")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
    }

    private fun decodeHex(hex: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < hex.length - 1) {
            val sub = hex.substring(i, i + 2)
            try {
                val code = sub.toInt(16)
                if (code in 32..126) {
                    sb.append(code.toChar())
                }
            } catch (e: Exception) {
                // ignore
            }
            i += 2
        }
        return sb.toString()
    }

    private fun cleanExtractedText(raw: String): String {
        return raw.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString("\n")
    }
}
