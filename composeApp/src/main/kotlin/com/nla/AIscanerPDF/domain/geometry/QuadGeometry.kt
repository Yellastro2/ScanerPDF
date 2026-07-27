package com.nla.AIscanerPDF.domain.geometry

import com.nla.AIscanerPDF.domain.model.CropCorners
import com.nla.AIscanerPDF.domain.model.CropPoint
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Чистая геометрия для оценки кандидатов-четырёхугольников от детектора.
 * Вынесена из OpenCV-кода, чтобы тестироваться на JVM без нативных библиотек.
 */
object QuadGeometry {

    /** Упорядочивает 4 точки как TL, TR, BR, BL. */
    fun orderCorners(points: List<CropPoint>): CropCorners {
        require(points.size == 4) { "Ожидаются 4 точки" }
        val topLeft = points.minBy { it.x + it.y }
        val bottomRight = points.maxBy { it.x + it.y }
        val topRight = points.maxBy { it.x - it.y }
        val bottomLeft = points.minBy { it.x - it.y }
        return CropCorners(topLeft, topRight, bottomRight, bottomLeft)
    }

    /**
     * Качество углов: 1.0 — все углы прямые, 0.0 — вырожденные.
     * Считается по модулю косинуса угла в каждой вершине.
     */
    fun angleQuality(corners: CropCorners): Float {
        val p = corners.asList()
        var worst = 0f
        for (i in p.indices) {
            val prev = p[(i + 3) % 4]
            val cur = p[i]
            val next = p[(i + 1) % 4]
            val c = abs(cosineAt(cur, prev, next))
            if (c > worst) worst = c
        }
        return (1f - worst).coerceIn(0f, 1f)
    }

    /**
     * Итоговая оценка кандидата: доля площади кадра * качество углов.
     * Кандидаты во весь кадр слегка штрафуются — это чаще рамка кадра,
     * а не документ.
     */
    fun scoreCandidate(corners: CropCorners, imageAreaFraction: Float): Float {
        val quality = angleQuality(corners)
        val fullFramePenalty = if (imageAreaFraction > 0.98f) 0.5f else 1f
        return imageAreaFraction.coerceIn(0f, 1f) * quality * fullFramePenalty
    }

    /** Кандидат достоверен: достаточно большой и с почти прямыми углами. */
    fun isPlausibleDocument(corners: CropCorners, imageAreaFraction: Float): Boolean =
        imageAreaFraction >= MIN_AREA_FRACTION &&
            angleQuality(corners) >= MIN_ANGLE_QUALITY &&
            QuadValidator.isValid(corners)

    const val MIN_AREA_FRACTION = 0.08f
    const val MIN_ANGLE_QUALITY = 0.55f

    private fun cosineAt(vertex: CropPoint, a: CropPoint, b: CropPoint): Float {
        val v1x = a.x - vertex.x
        val v1y = a.y - vertex.y
        val v2x = b.x - vertex.x
        val v2y = b.y - vertex.y
        val dot = v1x * v2x + v1y * v2y
        val len = sqrt((v1x * v1x + v1y * v1y).toDouble()).toFloat() *
            sqrt((v2x * v2x + v2y * v2y).toDouble()).toFloat()
        if (len < 1e-9f) return 1f
        return dot / len
    }
}
