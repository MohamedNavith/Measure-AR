package com.example.measurear.ar

import kotlin.math.abs
import kotlin.math.sqrt

data class Point3D(
    val x: Float,
    val y: Float,
    val z: Float
) {
    operator fun minus(other: Point3D) = Point3D(x - other.x, y - other.y, z - other.z)
    operator fun plus(other: Point3D) = Point3D(x + other.x, y + other.y, z + other.z)
    operator fun div(scalar: Float) = Point3D(x / scalar, y / scalar, z / scalar)
}

enum class MeasureMode(val label: String) {
    STANDARD("Distance"),
    MULTI_POINT("Chain / Area"),
    HEIGHT("Height")
}

enum class TrackingQualityHint(val message: String?) {
    NONE(null),
    INITIALIZING("Move phone slowly to scan environment"),
    TOO_DARK("Too dark — turn on more lights"),
    TOO_FAST("Move phone more slowly"),
    INSUFFICIENT_FEATURES("Find a textured, non-reflective surface"),
    CAMERA_UNAVAILABLE("Camera unavailable")
}

object DistanceCalculator {

    /**
     * Calculates 3D Euclidean distance in meters between two 3D coordinates:
     * $$d = \sqrt{(x_2 - x_1)^2 + (y_2 - y_1)^2 + (z_2 - z_1)^2}$$
     */
    fun calculateEuclideanDistance(a: Point3D, b: Point3D): Float {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val dz = b.z - a.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    /**
     * Calculates pure vertical displacement in meters for Height Mode.
     */
    fun calculateVerticalHeight(floorPoint: Point3D, topPoint: Point3D): Float {
        return abs(topPoint.y - floorPoint.y)
    }

    /**
     * Calculates the midpoint between two 3D points for placing floating badges.
     */
    fun midpoint(a: Point3D, b: Point3D): Point3D {
        return Point3D(
            x = (a.x + b.x) * 0.5f,
            y = (a.y + b.y) * 0.5f,
            z = (a.z + b.z) * 0.5f
        )
    }

    /**
     * Calculates total polyline length (or closed perimeter) across a chain of 3D points.
     */
    fun calculatePolylineLength(points: List<Point3D>, closeLoop: Boolean = false): Float {
        if (points.size < 2) return 0f
        var sum = 0f
        for (i in 0 until points.size - 1) {
            sum += calculateEuclideanDistance(points[i], points[i + 1])
        }
        if (closeLoop && points.size >= 3) {
            sum += calculateEuclideanDistance(points.last(), points.first())
        }
        return sum
    }

    /**
     * Calculates the 3D polygon area (in square meters) for 3+ points using
     * Newell's method / cross-product fan triangulation projected on the dominant plane,
     * or direct 3D triangle fan area around the first vertex:
     * $$\text{Area} = \frac{1}{2} \sum_{i=1}^{n-2} \| (\vec{P}_i - \vec{P}_0) \times (\vec{P}_{i+1} - \vec{P}_0) \|$$
     */
    fun calculatePolygonArea(points: List<Point3D>): Float {
        if (points.size < 3) return 0f
        val p0 = points[0]
        var totalArea = 0f

        for (i in 1 until points.size - 1) {
            val u = points[i] - p0
            val v = points[i + 1] - p0
            // Cross product u x v
            val cx = u.y * v.z - u.z * v.y
            val cy = u.z * v.x - u.x * v.z
            val cz = u.x * v.y - u.y * v.x
            val crossMagnitude = sqrt(cx * cx + cy * cy + cz * cz)
            totalArea += 0.5f * crossMagnitude
        }
        return totalArea
    }
}
