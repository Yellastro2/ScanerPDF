package com.nla.AIscanerPDF.domain.model

data class Document(
    val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val pageCount: Int,
    val previewPath: String?,
    val hasRecognizedText: Boolean,
)

data class DocumentWithPages(
    val document: Document,
    val pages: List<DocumentPage>,
)
