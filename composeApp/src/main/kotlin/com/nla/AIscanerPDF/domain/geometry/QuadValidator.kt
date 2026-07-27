package com.nla.AIscanerPDF.domain.geometry

import com.nla.AIscanerPDF.domain.model.CropCorners
import com.nla.AIscanerPDF.domain.model.CropPoint
import kotlin.math.abs

/**
 * Валидация и нормализация четырёхугольника обрезки (п. 5.3 ТЗ):
 * точки не выходят за пределы изображения, фигура не самопересекается,
 * площадь не вырождена.
 */
object QuadValidator {

    private const val MIN_AREA = 0.005f // 0.5% площади изображения

    /** Ограничивает точки диапазоном [0..1]. */
    fun clamp(corners: CropCorners): CropCorners =
        CropCorners.fromList(
            corners.asList().map { CropPoint(it.x.coerceIn(0f, 1f), it.y.coerceIn(0f, 1f)) },
        )

    /** true, если четырёхугольник простой (без самопересечений) и не вырожден. */
    fun isValid(corners: CropCorners): Boolean {
        val p = corners.asList()
        // Противоположные рёбра не должны пересекаться:
        // рёбра: (0-1),(1-2),(2-3),(3-0); несмежные пары: (0-1 vs 2-3) и (1-2 vs 3-0)
        if (segmentsIntersect(p[0], p[1], p[2], p[3])) return false
        if (segmentsIntersect(p[1], p[2], p[3], p[0])) return false
        return area(p) >= MIN_AREA
    }

    /** Площадь по формуле шнурования (в нормализованных координатах). */
    fun area(points: List<CropPoint>): Float {
        var sum = 0f
        for (i in points.indices) {
            val a = points[i]
            val b = points[(i + 1) % points.size]
            sum += a.x * b.y - b.x * a.y
        }
        return abs(sum) / 2f
    }

    private fun orientation(a: CropPoint, b: CropPoint, c: CropPoint): Int {
        val v = (b.y - a.y) * (c.x - b.x) - (b.x - a.x) * (c.y - b.y)
        return when {
            v > 1e-9f -> 1
            v < -1e-9f -> -1
            else -> 0
        }
    }

    private fun onSegment(a: CropPoint, b: CropPoint, c: CropPoint): Boolean =
        b.x in minOf(a.x, c.x)..maxOf(a.x, c.x) && b.y in minOf(a.y, c.y)..maxOf(a.y, c.y)

    /** Классическая проверка пересечения отрезков (p1,p2) и (p3,p4). */
    fun segmentsIntersect(p1: CropPoint, p2: CropPoint, p3: CropPoint, p4: CropPoint): Boolean {
        val o1 = orientation(p1, p2, p3)
        val o2 = orientation(p1, p2, p4)
        val o3 = orientation(p3, p4, p1)
        val o4 = orientation(p3, p4, p2)
        if (o1 != o2 && o3 != o4) return true
        if (o1 == 0 && onSegment(p1, p3, p2)) return true
        if (o2 == 0 && onSegment(p1, p4, p2)) return true
        if (o3 == 0 && onSegment(p3, p1, p4)) return true
        if (o4 == 0 && onSegment(p3, p2, p4)) return true
        return false
    }
}
