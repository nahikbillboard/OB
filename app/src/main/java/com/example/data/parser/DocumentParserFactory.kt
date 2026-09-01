package com.example.data.parser

import android.content.Context
import android.net.Uri
import com.example.data.model.DocumentType
import com.example.data.model.ParsedDocument
import java.util.Locale

class DocumentParserFactory(
    private val txtParser: TxtParser = TxtParser(),
    private val docxParser: DocxParser = DocxParser(),
    private val pdfParser: PdfParser = PdfParser()
) {

    fun parseUri(context: Context, uri: Uri, fileName: String, mimeType: String?): ParsedDocument {
        val lowerName = fileName.lowercase(Locale.ROOT)
        val lowerMime = mimeType?.lowercase(Locale.ROOT) ?: ""

        return when {
            lowerName.endsWith(".pdf") || lowerMime.contains("pdf") -> {
                pdfParser.parse(context, uri, fileName)
            }
            lowerName.endsWith(".docx") || lowerMime.contains("wordprocessingml") || lowerMime.contains("docx") -> {
                docxParser.parse(context, uri, fileName)
            }
            else -> {
                txtParser.parse(context, uri, fileName)
            }
        }
    }

    fun parseRawText(title: String, text: String, type: DocumentType = DocumentType.MANUAL): ParsedDocument {
        return txtParser.parseRawText(title, text, type)
    }
}
