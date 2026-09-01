package com.example.data.parser

import android.content.Context
import android.net.Uri
import com.example.data.model.DocumentType
import com.example.data.model.ParsedDocument
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.io.StringReader
import java.util.zip.ZipInputStream

class DocxParser {
    fun parse(context: Context, uri: Uri, fileName: String): ParsedDocument {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Cannot open stream for URI: $uri")

        val fileDescriptor = context.contentResolver.openAssetFileDescriptor(uri, "r")
        val size = fileDescriptor?.use { it.length } ?: 0L

        val text = parseDocxStream(inputStream)

        return ParsedDocument(
            title = fileName,
            text = text.trim(),
            type = DocumentType.DOCX,
            fileSizeBytes = if (size > 0) size else text.toByteArray().size.toLong()
        )
    }

    private fun parseDocxStream(inputStream: InputStream): String {
        val sb = StringBuilder()
        ZipInputStream(inputStream).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == "word/document.xml") {
                    val xmlContent = zip.bufferedReader(Charsets.UTF_8).readText()
                    sb.append(extractTextFromWordXml(xmlContent))
                    break
                }
                entry = zip.nextEntry
            }
        }
        return sb.toString()
    }

    private fun extractTextFromWordXml(xml: String): String {
        val result = StringBuilder()
        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xml))

            var eventType = parser.eventType
            var inParagraph = false

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        val name = parser.name
                        if (name.equals("p", ignoreCase = true) || name.endsWith(":p")) {
                            inParagraph = true
                        } else if (name.equals("t", ignoreCase = true) || name.endsWith(":t")) {
                            val text = parser.nextText()
                            result.append(text)
                        } else if (name.equals("br", ignoreCase = true) || name.endsWith(":br")) {
                            result.append("\n")
                        } else if (name.equals("cr", ignoreCase = true) || name.endsWith(":cr")) {
                            result.append("\n")
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        val name = parser.name
                        if (name.equals("p", ignoreCase = true) || name.endsWith(":p")) {
                            result.append("\n\n")
                            inParagraph = false
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            // Fallback: Regex extraction for <w:t> tags
            val regex = Regex("<(?:[a-zA-Z0-9]+:)?t(?:\\s[^>]*)?>(.*?)</(?:[a-zA-Z0-9]+:)?t>", RegexOption.DOT_MATCHES_ALL)
            val matches = regex.findAll(xml)
            for (match in matches) {
                result.append(match.groupValues[1]).append(" ")
            }
        }
        return result.toString()
    }
}
