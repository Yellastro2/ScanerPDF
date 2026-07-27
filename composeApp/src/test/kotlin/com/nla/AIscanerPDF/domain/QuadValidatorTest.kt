package com.nla.AIscanerPDF.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.aiscanner.docs.domain.geometry.QuadValidator
import ru.aiscanner.docs.domain.model.CropCorners
import ru.aiscanner.docs.domain.model.CropPoint

class QuadValidatorTest {

    @Test
    fun `valid rectangle passes validation`() {
        val corners = CropCorners(
            CropPoint(0.1f, 0.1f),
            CropPoint(0.9f, 0.1f),
            CropPoint(0.9f, 0.9f),
            CropPoint(0.1f, 0.9f),
        )
        assertTrue(QuadValidator.isValid(corners))
    }

    @Test
    fun `self-intersecting hourglass is invalid`() {
        // Верхние точки перепутаны местами с нижними по диагонали
        val corners = CropCorners(
            CropPoint(0.1f, 0.1f),
            CropPoint(0.9f, 0.9f),
            CropPoint(0.9f, 0.1f),
            CropPoint(0.1f, 0.9f),
        )
        assertFalse(QuadValidator.isValid(corners))
    }

    @Test
    fun `degenerate quad with tiny area is invalid`() {
        val corners = CropCorners(
            CropPoint(0.5f, 0.5f),
            CropPoint(0.501f, 0.5f),
            CropPoint(0.501f, 0.501f),
            CropPoint(0.5f, 0.501f),
        )
        assertFalse(QuadValidator.isValid(corners))
    }

    @Test
    fun `clamp keeps points inside unit square`() {
        val corners = CropCorners(
            CropPoint(-0.2f, 0.1f),
            CropPoint(1.4f, -3f),
            CropPoint(0.9f, 1.9f),
            CropPoint(0.1f, 0.9f),
        )
        val clamped = QuadValidator.clamp(corners)
        clamped.asList().forEach {
            assertTrue(it.x in 0f..1f)
            assertTrue(it.y in 0f..1f)
        }
        assertEquals(0f, clamped.topLeft.x, 1e-6f)
        assertEquals(1f, clamped.topRight.x, 1e-6f)
    }
}
