package com.nla.AIscanerPDF.data.files

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.withContext
import com.nla.AIscanerPDF.core.DispatchersProvider
import com.nla.AIscanerPDF.domain.repository.ImageImporter
import java.io.FileOutputStream

/**
 * Импорт из галереи через Photo Picker/SAF (п. 7 ТЗ): полный доступ
 * к файлам устройства не запрашивается, изображение копируется во
 * внутреннее хранилище документа.
 */
class AndroidImageImporter(
    private val context: Context,
    private val files: DocumentFileStore,
    private val dispatchers: DispatchersProvider,
) : ImageImporter {

    override suspend fun importImage(documentId: String, uriString: String): String =
        withContext(dispatchers.io) {
            val target = files.newOriginalFile(documentId)
            val uri = Uri.parse(uriString)
            context.contentResolver.openInputStream(uri)?.use { input ->
                files.writeAtomically(target) { tmp ->
                    FileOutputStream(tmp).use { output -> input.copyTo(output) }
                }
            } ?: error("Не удалось открыть изображение")
            target.absolutePath
        }
}
