package com.nla.AIscanerPDF.data.ocr

import android.content.Context
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import com.nla.AIscanerPDF.core.DispatchersProvider
import com.nla.AIscanerPDF.data.imageprocessing.BitmapLoader
import java.io.File

/**
 * Офлайн OCR на Tesseract (tesseract4android, без GMS).
 * Модели rus/eng (tessdata_fast) поставляются в assets и при первом
 * запуске копируются во внутреннее хранилище. Обоснование выбора
 * Tesseract вместо PaddleOCR — в README.
 *
 * Распознанный текст не логируется (п. 10 ТЗ).
 */
class TesseractOcrEngine(
    private val context: Context,
    private val dispatchers: DispatchersProvider,
) : OcrEngine {

    override val isAvailable: Boolean = true
    override val supportedLanguages: Set<String> = setOf("rus", "eng", "rus+eng")

    private val initMutex = Mutex()

    override suspend fun recognize(imagePath: String, language: String): String =
        withContext(dispatchers.default) {
            val dataDir = ensureTessData()
            val bitmap = BitmapLoader.decodeSampled(imagePath, OCR_MAX_DIMENSION)
            val tess = TessBaseAPI()
            try {
                coroutineContext.ensureActive()
                val initialized = tess.init(dataDir.absolutePath, mapLanguage(language))
                check(initialized) { "Не удалось инициализировать OCR-движок" }
                tess.setImage(bitmap)
                coroutineContext.ensureActive()
                val text = tess.utF8Text.orEmpty()
                coroutineContext.ensureActive()
                text.trim()
            } catch (e: CancellationException) {
                tess.stop()
                throw e
            } finally {
                tess.recycle()
                bitmap.recycle()
            }
        }

    /** "ru+en"/"ru"/"en" из настроек → коды tessdata. */
    private fun mapLanguage(language: String): String = when (language.lowercase()) {
        "ru", "rus" -> "rus"
        "en", "eng" -> "eng"
        else -> "rus+eng"
    }

    /**
     * Каталог с tessdata/: копирование из assets атомарно через .tmp,
     * повторные запуски используют уже скопированные модели.
     */
    private suspend fun ensureTessData(): File = initMutex.withLock {
        val root = File(context.filesDir, "tesseract")
        val tessdata = File(root, "tessdata")
        if (!tessdata.exists()) tessdata.mkdirs()
        MODEL_FILES.forEach { name ->
            val target = File(tessdata, name)
            if (!target.exists() || target.length() == 0L) {
                val tmp = File(tessdata, "$name.tmp")
                context.assets.open("tessdata/$name").use { input ->
                    tmp.outputStream().use { output -> input.copyTo(output) }
                }
                if (target.exists()) target.delete()
                check(tmp.renameTo(target)) { "Не удалось подготовить модель OCR" }
            }
        }
        root
    }

    private companion object {
        const val OCR_MAX_DIMENSION = 2200
        val MODEL_FILES = listOf("rus.traineddata", "eng.traineddata")
    }
}
