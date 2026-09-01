package com.example.data.parser

import android.content.Context
import android.net.Uri
import com.example.data.model.DocumentType
import com.example.data.model.ParsedDocument
import java.io.BufferedReader
import java.io.InputStreamReader

class TxtParser {
    fun parse(context: Context, uri: Uri, fileName: String): ParsedDocument {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Cannot open stream for URI: $uri")
        
        val text = inputStream.use { stream ->
            BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).readText()
        }

        val fileDescriptor = context.contentResolver.openAssetFileDescriptor(uri, "r")
        val size = fileDescriptor?.use { it.length } ?: text.toByteArray().size.toLong()

        return ParsedDocument(
            title = fileName,
            text = text.trim(),
            type = DocumentType.TXT,
            fileSizeBytes = if (size > 0) size else text.toByteArray().size.toLong()
        )
    }

    fun parseRawText(title: String, text: String, type: DocumentType = DocumentType.MANUAL): ParsedDocument {
        val bytes = text.toByteArray(Charsets.UTF_8)
        return ParsedDocument(
            title = title,
            text = text.trim(),
            type = type,
            fileSizeBytes = bytes.size.toLong()
        )
    }
}
