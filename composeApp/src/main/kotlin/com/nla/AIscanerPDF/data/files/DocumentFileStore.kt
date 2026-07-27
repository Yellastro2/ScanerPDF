package com.nla.AIscanerPDF.data.files

import android.content.Context
import java.io.File
import java.util.UUID

/**
 * Файловая структура документов во внутреннем хранилище (п. 7 ТЗ):
 * documents/{documentId}/{original|processed|previews|export}/...
 * Запись через временный файл + rename защищает от мусора при ошибках.
 */
class DocumentFileStore(private val context: Context) {

    val root: File get() = File(context.filesDir, "documents")

    fun documentDir(documentId: String): File = File(root, documentId)
    fun originalDir(documentId: String): File = ensure(File(documentDir(documentId), "original"))
    fun processedDir(documentId: String): File = ensure(File(documentDir(documentId), "processed"))
    fun previewsDir(documentId: String): File = ensure(File(documentDir(documentId), "previews"))
    fun exportDir(documentId: String): File = ensure(File(documentDir(documentId), "export"))

    fun newOriginalFile(documentId: String): File =
        File(originalDir(documentId), "page_${UUID.randomUUID()}.jpg")

    fun processedFileFor(documentId: String, pageId: String): File =
        File(processedDir(documentId), "$pageId.jpg")

    fun previewFileFor(documentId: String, pageId: String): File =
        File(previewsDir(documentId), "$pageId.webp")

    fun deleteDocumentFiles(documentId: String) {
        documentDir(documentId).deleteRecursively()
    }

    fun deleteAll() {
        root.deleteRecursively()
    }

    fun deletePageFiles(page: com.nla.AIscanerPDF.domain.model.DocumentPage) {
        listOfNotNull(page.originalPath, page.processedPath, page.previewPath)
            .forEach { runCatching { File(it).delete() } }
    }

    /** Атомарная запись: сначала во временный файл, затем rename. */
    inline fun writeAtomically(target: File, write: (File) -> Unit): File {
        val tmp = File(target.parentFile, target.name + ".tmp")
        try {
            write(tmp)
            if (target.exists()) target.delete()
            check(tmp.renameTo(target)) { "Не удалось переместить временный файл" }
            return target
        } finally {
            if (tmp.exists()) tmp.delete()
        }
    }

    private fun ensure(dir: File): File {
        if (!dir.exists()) dir.mkdirs()
        return dir
    }
}
