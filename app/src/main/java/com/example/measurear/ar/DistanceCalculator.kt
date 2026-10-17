package com.example.measurear.ar

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class Point3D(
    val x: Float,
    val y: Float,
    val z: Float
) {
    operator fun minus(other: Point3D) = Point3D(x - other.x, y - other.y, z - other.z)
    operator fun plus(other: Point3D) = Point3D(x + other.x, y + other.y, z + other.z)
    operator fun times(scalar: Float) = Point3D(x * scalar, y * scalar, z * scalar)
    operator fun div(scalar: Float) = Point3D(x / scalar, y / scalar, z / scalar)

    fun dot(other: Point3D): Float = x * other.x + y * other.y + z * other.z

    fun cross(other: Point3D): Point3D = Point3D(
        y * other.z - z * other.y,
        z * other.x - x * other.z,
        x * other.y - y * other.x
    )

    fun length(): Float = sqrt(x * x + y * y + z * z)

    fun normalized(): Point3D {
        val len = length()
        return if (len > 0.0001f) this / len else Point3D(0f, 1f, 0f)
    }
}

enum class MeasureMode(val label: String) {
    STANDARD("Distance"),
    BOX_3D("3D Box / Volume"),
    RECTANGLE("2D Surface"),
    HEIGHT("Height"),
    MULTI_POINT("Chain / Area")
}

data class BoxDimensions(
    val length: Float,
    val breadth: Float,
    val height: Float,
    val volume: Float,
    val surfaceArea: Float
)

data class RectangleDimensions(
    val length: Float,
    val breadth: Float,
    val area: Float,
    val perimeter: Float
)

enum class TrackingQualityHint(val message: String?) {
    NONE(null),
    INITIALIZING("Move phone slowly to scan environment"),
    TOO_DARK("Too dark — turn on flash with top button"),
    TOO_FAST("Move phone more slowly"),
    INSUFFICIENT_FEATURES("Find a textured surface or turn on flash"),
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
     * Newell's method / cross-product fan triangulation.
     */
    fun calculatePolygonArea(points: List<Point3D>): Float {
        if (points.size < 3) return 0f
        val p0 = points[0]
        var totalArea = 0f

        for (i in 1 until points.size - 1) {
            val u = points[i] - p0
            val v = points[i + 1] - p0
            val cross = u.cross(v)
            totalArea += 0.5f * cross.length()
        }
        return totalArea
    }

    /**
     * Calculates simultaneous 3D Box dimensions: Length, Breadth, Height, and Volume.
     */
    fun calculateBoxDimensions(
        cornerA: Point3D,
        cornerB: Point3D,
        cornerC: Point3D?,
        cornerTop: Point3D?
    ): BoxDimensions {
        val length = calculateEuclideanDistance(cornerA, cornerB)

        val breadth = if (cornerC != null) {
            val ab = (cornerB - cornerA).normalized()
            val ac = cornerC - cornerA
            val proj = ab * ac.dot(ab)
            val perp = ac - proj
            perp.length().coerceAtLeast(0.01f)
        } else {
            0f
        }

        val height = if (cornerTop != null) {
            abs(cornerTop.y - cornerA.y).coerceAtLeast(0.01f)
        } else {
            0f
        }

        val volume = length * breadth * height
        val surfaceArea = 2f * (length * breadth + length * height + breadth * height)

        return BoxDimensions(
            length = length,
            breadth = breadth,
            height = height,
            volume = volume,
            surfaceArea = surfaceArea
        )
    }

    /**
     * Calculates 3D bounding box dimensions directly from two diagonal corner points.
     */
    fun calculateBoundingBox(cornerA: Point3D, cornerB: Point3D): BoxDimensions {
        val dx = abs(cornerB.x - cornerA.x).coerceAtLeast(0.01f)
        val dy = abs(cornerB.y - cornerA.y).coerceAtLeast(0.01f)
        val dz = abs(cornerB.z - cornerA.z).coerceAtLeast(0.01f)

        val dims = listOf(dx, dy, dz).sortedDescending()
        val length = dims[0]
        val breadth = dims[1]
        val height = dims[2]

        val volume = length * breadth * height
        val surfaceArea = 2f * (length * breadth + length * height + breadth * height)

        return BoxDimensions(
            length = length,
            breadth = breadth,
            height = height,
            volume = volume,
            surfaceArea = surfaceArea
        )
    }

    /**
     * Calculates 2D rectangle dimensions (Length, Breadth, Area) from two diagonal corner points.
     */
    fun calculateRectangleDimensions(
        cornerA: Point3D,
        cornerB: Point3D
    ): RectangleDimensions {
        val dx = abs(cornerB.x - cornerA.x)
        val dy = abs(cornerB.y - cornerA.y)
        val dz = abs(cornerB.z - cornerA.z)

        // For dominant horizontal plane (XZ), take dx and dz
        // For dominant vertical plane, take max of horizontal and dy
        val dim1 = sqrt(dx * dx + dz * dz)
        val dim2 = dy

        val length: Float
        val breadth: Float
        if (dim2 > 0.05f && dim1 > 0.05f) {
            length = max(dim1, dim2)
            breadth = min(dim1, dim2)
        } else {
            length = max(dx, dz).coerceAtLeast(0.01f)
            breadth = min(dx, dz).coerceAtLeast(0.01f)
        }

        val area = length * breadth
        val perimeter = 2f * (length + breadth)

        return RectangleDimensions(
            length = length,
            breadth = breadth,
            area = area,
            perimeter = perimeter
        )
    }

    /**
     * Snaps a 3D point to the nearest plane edge if within [thresholdMeters].
     * Returns a pair of (SnappedPoint, isSnapped).
     */
    fun snapToNearestEdge(
        point: Point3D,
        edgeSegments: List<Pair<Point3D, Point3D>>,
        thresholdMeters: Float = 0.035f
    ): Pair<Point3D, Boolean> {
        var closestPoint = point
        var minDistance = thresholdMeters
        var didSnap = false

        for (segment in edgeSegments) {
            val a = segment.first
            val b = segment.second
            val ab = b - a
            val abLenSq = ab.x * ab.x + ab.y * ab.y + ab.z * ab.z
            if (abLenSq < 0.0001f) continue

            val ap = point - a
            val t = (ap.dot(ab) / abLenSq).coerceIn(0f, 1f)
            val projected = a + ab * t
            val dist = calculateEuclideanDistance(point, projected)

            if (dist < minDistance) {
                minDistance = dist
                closestPoint = projected
                didSnap = true
            }
        }

        return closestPoint to didSnap
    }

    /**
     * Snaps a line to horizontal or vertical axes when within [thresholdDegrees],
     * matching the iOS Measure gravity/level alignment snapping.
     */
    fun snapToLevelAxis(
        start: Point3D,
        current: Point3D,
        thresholdDegrees: Float = 3.5f
    ): Pair<Point3D, Boolean> {
        val delta = current - start
        val horizDist = sqrt(delta.x * delta.x + delta.z * delta.z)
        val verticalDist = abs(delta.y)

        // Check if nearly vertical (angle with Y axis < threshold)
        val totalDist = sqrt(horizDist * horizDist + verticalDist * verticalDist)
        if (totalDist < 0.05f) return current to false

        val angleFromVerticalDeg = Math.toDegrees(atan2(horizDist.toDouble(), verticalDist.toDouble())).toFloat()
        if (angleFromVerticalDeg < thresholdDegrees) {
            // Snap to purely vertical line
            return Point3D(start.x, current.y, start.z) to true
        }

        val angleFromHorizontalDeg = Math.toDegrees(atan2(verticalDist.toDouble(), horizDist.toDouble())).toFloat()
        if (angleFromHorizontalDeg < thresholdDegrees) {
            // Snap to purely horizontal plane
            return Point3D(current.x, start.y, current.z) to true
        }

        return current to false
    }
}
