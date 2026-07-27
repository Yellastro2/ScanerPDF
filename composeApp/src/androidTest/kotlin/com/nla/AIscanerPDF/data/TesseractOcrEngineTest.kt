package com.nla.AIscanerPDF.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import ru.aiscanner.docs.core.DefaultDispatchersProvider
import ru.aiscanner.docs.data.ocr.TesseractOcrEngine
import java.io.File

/** OCR-тесты на трёх синтетических документах (без персональных данных). */
@RunWith(AndroidJUnit4::class)
class TesseractOcrEngineTest {

    private lateinit var engine: TesseractOcrEngine

    @Before
    fun setUp() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        engine = TesseractOcrEngine(appContext, DefaultDispatchersProvider())
    }

    private fun copyAssetToFile(asset: String): String {
        val testContext = InstrumentationRegistry.getInstrumentation().context
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val out = File(appContext.cacheDir, asset)
        testContext.assets.open("testimages/$asset").use { input ->
            out.outputStream().use { input.copyTo(it) }
        }
        return out.absolutePath
    }

    @Test
    fun recognizesRussianText() = runTest {
        val text = engine.recognize(copyAssetToFile("ocr_russian.png"), "ru+en")
        assertTrue("не найдено слово ДОКУМЕНТ: $text", text.contains("ДОКУМЕНТ", ignoreCase = true))
        assertTrue(text.contains("Договор", ignoreCase = true))
        assertTrue(text.contains("125"))
        assertTrue(text.contains("15.07.2026"))
    }

    @Test
    fun recognizesEnglishText() = runTest {
        val text = engine.recognize(copyAssetToFile("ocr_english.png"), "ru+en")
        assertTrue(text.contains("DOCUMENT", ignoreCase = true))
        assertTrue(text.contains("agreement", ignoreCase = true))
    }

    @Test
    fun recognizesMixedTextWithNumbersAndPhone() = runTest {
        val text = engine.recognize(copyAssetToFile("ocr_mixed.png"), "ru+en")
        assertTrue(text.contains("INVOICE", ignoreCase = true))
        assertTrue(text.contains("2026"))
        assertTrue(text.contains("48"))
    }
}
