package com.nla.AIscanerPDF.data.repository

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import kotlinx.coroutines.withContext
import com.nla.AIscanerPDF.core.AppError
import com.nla.AIscanerPDF.core.AppResult
import com.nla.AIscanerPDF.core.DispatchersProvider
import com.nla.AIscanerPDF.data.export.PdfExporter
import com.nla.AIscanerPDF.data.files.DocumentFileStore
import com.nla.AIscanerPDF.data.imageprocessing.BitmapLoader
import com.nla.AIscanerPDF.domain.logic.PdfFileNameGenerator
import com.nla.AIscanerPDF.domain.model.ExportedFile
import com.nla.AIscanerPDF.domain.model.PdfExportOptions
import com.nla.AIscanerPDF.domain.repository.DocumentRepository
import com.nla.AIscanerPDF.domain.repository.ExportRepository
import java.io.File
import java.io.FileOutputStream

class ExportRepositoryImpl(
    private val context: Context,
    private val documents: DocumentRepository,
    private val files: DocumentFileStore,
    private val pdfExporter: PdfExporter,
    private val dispatchers: DispatchersProvider,
) : ExportRepository {

    override suspend fun exportToPdf(
        documentId: String,
        options: PdfExportOptions,
    ): AppResult<ExportedFile> = withContext(dispatchers.io) {
        val doc = documents.getDocument(documentId)
            ?: return@withContext AppResult.Failure(AppError.DocumentNotFound)
        try {
            val fileName = options.fileName
                ?: PdfFileNameGenerator.generate(doc.document.name, System.currentTimeMillis())
            val outFile = File(files.exportDir(documentId), fileName)
            files.writeAtomically(outFile) { tmp -> pdfExporter.export(doc.pages, options, tmp) }
            AppResult.Success(ExportedFile(outFile.absolutePath, "application/pdf"))
        } catch (e: OutOfMemoryError) {
            AppResult.Failure(AppError.OutOfMemory)
        } catch (e: Exception) {
            AppResult.Failure(AppError.PdfExportFailed)
        }
    }

    override suspend fun exportPagesToJpg(documentId: String): AppResult<List<ExportedFile>> =
        withContext(dispatchers.io) {
            val doc = documents.getDocument(documentId)
                ?: return@withContext AppResult.Failure(AppError.DocumentNotFound)
            try {
                val exported = doc.pages.sortedBy { it.position }.mapIndexed { index, page ->
                    val source = page.processedPath ?: page.originalPath
                    val outFile = File(files.exportDir(documentId), "page_${index + 1}.jpg")
                    if (page.processedPath != null) {
                        File(source).copyTo(outFile, overwrite = true)
                    } else {
                        val bitmap = BitmapLoader.decodeSampled(source, 3200)
                        try {
                            files.writeAtomically(outFile) { tmp ->
                                FileOutputStream(tmp).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
                            }
                        } finally {
                            bitmap.recycle()
                        }
                    }
                    ExportedFile(outFile.absolutePath, "image/jpeg")
                }
                AppResult.Success(exported)
            } catch (e: OutOfMemoryError) {
                AppResult.Failure(AppError.OutOfMemory)
            } catch (e: Exception) {
                AppResult.Failure(AppError.Unknown)
            }
        }

    override suspend fun exportTextToTxt(documentId: String, text: String): AppResult<ExportedFile> =
        withContext(dispatchers.io) {
            try {
                val outFile = File(files.exportDir(documentId), "text.txt")
                files.writeAtomically(outFile) { tmp -> tmp.writeText(text) }
                AppResult.Success(ExportedFile(outFile.absolutePath, "text/plain"))
            } catch (e: Exception) {
                AppResult.Failure(AppError.Unknown)
            }
        }

    override suspend fun shareFile(file: ExportedFile): AppResult<Unit> = try {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            File(file.path),
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = file.mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, null).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
        AppResult.Success(Unit)
    } catch (e: Exception) {
        AppResult.Failure(AppError.Unknown)
    }
}
