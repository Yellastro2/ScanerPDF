package com.nla.AIscanerPDF.data.db

import kotlinx.serialization.json.Json
import com.nla.AIscanerPDF.domain.model.CropCorners
import com.nla.AIscanerPDF.domain.model.Document
import com.nla.AIscanerPDF.domain.model.DocumentFilter
import com.nla.AIscanerPDF.domain.model.DocumentPage

private val json = Json { ignoreUnknownKeys = true }

fun PageEntity.toDomain(): DocumentPage = DocumentPage(
    id = id,
    documentId = documentId,
    position = position,
    originalPath = originalPath,
    processedPath = processedPath,
    previewPath = previewPath,
    crop = cropJson?.let { runCatching { json.decodeFromString<CropCorners>(it) }.getOrNull() },
    rotationDegrees = rotationDegrees,
    filter = runCatching { DocumentFilter.valueOf(filter) }.getOrDefault(DocumentFilter.ORIGINAL),
    brightness = brightness,
    contrast = contrast,
    recognizedText = recognizedText,
)

fun DocumentPage.toEntity(): PageEntity = PageEntity(
    id = id,
    documentId = documentId,
    position = position,
    originalPath = originalPath,
    processedPath = processedPath,
    previewPath = previewPath,
    cropJson = crop?.let { json.encodeToString(CropCorners.serializer(), it) },
    rotationDegrees = rotationDegrees,
    filter = filter.name,
    brightness = brightness,
    contrast = contrast,
    recognizedText = recognizedText,
)

fun DocumentEntity.toDomain(pages: List<PageEntity>): Document = Document(
    id = id,
    name = name,
    createdAt = createdAt,
    updatedAt = updatedAt,
    pageCount = pages.size,
    previewPath = pages.minByOrNull { it.position }?.let { it.previewPath ?: it.processedPath ?: it.originalPath },
    hasRecognizedText = pages.any { !it.recognizedText.isNullOrBlank() },
)
