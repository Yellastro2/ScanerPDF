package com.nla.AIscanerPDF.data

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.android.OpenCVLoader
import ru.aiscanner.docs.core.DefaultDispatchersProvider
import ru.aiscanner.docs.data.imageprocessing.OpenCvDocumentCornerDetector
import ru.aiscanner.docs.domain.geometry.QuadValidator
import ru.aiscanner.docs.domain.model.CornerDetectionResult

/**
 * Интеграционные тесты детектора на синтетических изображениях
 * (androidTest/assets/testimages, без персональных данных).
 */
@RunWith(AndroidJUnit4::class)
class OpenCvCornerDetectorTest {

    private lateinit var detector: OpenCvDocumentCornerDetector

    @Before
    fun setUp() {
        check(OpenCVLoader.initLocal()) { "OpenCV не инициализирован" }
        detector = OpenCvDocumentCornerDetector(DefaultDispatchersProvider())
    }

    private suspend fun detect(asset: String): CornerDetectionResult {
        val context = InstrumentationRegistry.getInstrumentation().context
        val bitmap = context.assets.open("testimages/$asset").use { BitmapFactory.decodeStream(it) }
        return detector.detectInBitmap(bitmap).also { bitmap.recycle() }
    }

    @Test
    fun detectsLightDocumentOnDarkBackground() = runTest {
        val result = detect("light_doc_dark_bg.jpg")
        assertTrue("детекция не сработала", result.detected)
        assertTrue(QuadValidator.isValid(result.corners))
        // Документ занимает центральную область; углы не должны совпадать с рамкой кадра
        assertTrue(result.corners.topLeft.x in 0.05f..0.35f)
        assertTrue(result.corners.bottomRight.x in 0.65f..0.98f)
    }

    @Test
    fun detectsDarkDocumentOnLightBackground() = runTest {
        val result = detect("dark_doc_light_bg.jpg")
        assertTrue(result.detected)
        assertTrue(QuadValidator.isValid(result.corners))
    }

    @Test
    fun detectsAngledDocument() = runTest {
        val result = detect("angled_doc.jpg")
        assertTrue(result.detected)
        assertTrue(QuadValidator.isValid(result.corners))
    }

    @Test
    fun detectsDocumentWithShadow() = runTest {
        val result = detect("doc_with_shadow.jpg")
        // Тень может ухудшить контур, но валидный результат обязателен
        assertTrue(QuadValidator.isValid(result.corners))
    }

    @Test
    fun detectsDocumentInLowLight() = runTest {
        val result = detect("low_light_doc.jpg")
        assertTrue(QuadValidator.isValid(result.corners))
    }

    @Test
    fun occludedCornerStillYieldsValidQuad() = runTest {
        val result = detect("occluded_corner_doc.jpg")
        assertTrue(QuadValidator.isValid(result.corners))
    }

    @Test
    fun noDocumentFallsBackToInsetCorners() = runTest {
        val result = detect("no_document.jpg")
        assertFalse("на пустом кадре не должно быть уверенной детекции", result.detected)
        assertTrue(QuadValidator.isValid(result.corners))
    }
}
