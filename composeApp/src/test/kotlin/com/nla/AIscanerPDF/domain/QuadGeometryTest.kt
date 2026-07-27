package com.nla.AIscanerPDF.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.aiscanner.docs.domain.geometry.QuadGeometry
import ru.aiscanner.docs.domain.model.CropPoint

class QuadGeometryTest {

    @Test
    fun `orderCorners arranges points clockwise from top-left`() {
        val shuffled = listOf(
            CropPoint(0.9f, 0.8f), // BR
            CropPoint(0.1f, 0.1f), // TL
            CropPoint(0.1f, 0.8f), // BL
            CropPoint(0.9f, 0.1f), // TR
        )
        val ordered = QuadGeometry.orderCorners(shuffled)
        assertEquals(CropPoint(0.1f, 0.1f), ordered.topLeft)
        assertEquals(CropPoint(0.9f, 0.1f), ordered.topRight)
        assertEquals(CropPoint(0.9f, 0.8f), ordered.bottomRight)
        assertEquals(CropPoint(0.1f, 0.8f), ordered.bottomLeft)
    }

    @Test
    fun `rectangle has high angle quality`() {
        val rect = QuadGeometry.orderCorners(
            listOf(CropPoint(0.2f, 0.2f), CropPoint(0.8f, 0.2f), CropPoint(0.8f, 0.7f), CropPoint(0.2f, 0.7f)),
        )
        assertTrue(QuadGeometry.angleQuality(rect) > 0.95f)
    }

    @Test
    fun `sheared quad has lower angle quality than rectangle`() {
        val sheared = QuadGeometry.orderCorners(
            listOf(CropPoint(0.3f, 0.2f), CropPoint(0.9f, 0.35f), CropPoint(0.75f, 0.8f), CropPoint(0.1f, 0.6f)),
        )
        val rect = QuadGeometry.orderCorners(
            listOf(CropPoint(0.2f, 0.2f), CropPoint(0.8f, 0.2f), CropPoint(0.8f, 0.7f), CropPoint(0.2f, 0.7f)),
        )
        assertTrue(QuadGeometry.angleQuality(sheared) < QuadGeometry.angleQuality(rect))
    }

    @Test
    fun `tiny candidate is not a plausible document`() {
        val rect = QuadGeometry.orderCorners(
            listOf(CropPoint(0.45f, 0.45f), CropPoint(0.55f, 0.45f), CropPoint(0.55f, 0.55f), CropPoint(0.45f, 0.55f)),
        )
        assertFalse(QuadGeometry.isPlausibleDocument(rect, imageAreaFraction = 0.01f))
        assertTrue(QuadGeometry.isPlausibleDocument(rect, imageAreaFraction = 0.3f))
    }

    @Test
    fun `full-frame candidate is penalized against partial-frame candidate`() {
        val full = QuadGeometry.orderCorners(
            listOf(CropPoint(0f, 0f), CropPoint(1f, 0f), CropPoint(1f, 1f), CropPoint(0f, 1f)),
        )
        val partial = QuadGeometry.orderCorners(
            listOf(CropPoint(0.1f, 0.1f), CropPoint(0.9f, 0.1f), CropPoint(0.9f, 0.9f), CropPoint(0.1f, 0.9f)),
        )
        assertTrue(
            QuadGeometry.scoreCandidate(partial, 0.64f) > QuadGeometry.scoreCandidate(full, 1.0f),
        )
    }
}
