package com.example.measurear

import com.example.measurear.ar.DistanceCalculator
import com.example.measurear.ar.Point3D
import org.junit.Assert.assertEquals
import org.junit.Test

class DistanceCalculatorTest {

    @Test
    fun `calculateEuclideanDistance computes accurate 3D distance`() {
        val a = Point3D(1f, 2f, 3f)
        val b = Point3D(4f, 6f, 15f)
        // dx = 3, dy = 4, dz = 12 -> sqrt(9 + 16 + 144) = sqrt(169) = 13
        val distance = DistanceCalculator.calculateEuclideanDistance(a, b)
        assertEquals(13.0f, distance, 0.0001f)
    }

    @Test
    fun `calculateVerticalHeight returns absolute Y difference`() {
        val floor = Point3D(0.4f, -1.25f, -2.0f)
        val top = Point3D(0.4f, 0.55f, -2.0f)
        val height = DistanceCalculator.calculateVerticalHeight(floor, top)
        assertEquals(1.80f, height, 0.0001f)
    }

    @Test
    fun `calculatePolylineLength computes open chain and closed perimeter`() {
        val square = listOf(
            Point3D(0f, 0f, 0f),
            Point3D(2f, 0f, 0f),
            Point3D(2f, 0f, 2f),
            Point3D(0f, 0f, 2f)
        )
        val openLength = DistanceCalculator.calculatePolylineLength(square, closeLoop = false)
        val closedPerimeter = DistanceCalculator.calculatePolylineLength(square, closeLoop = true)

        assertEquals(6.0f, openLength, 0.0001f)
        assertEquals(8.0f, closedPerimeter, 0.0001f)
    }

    @Test
    fun `calculatePolygonArea computes area of 4-point rectangle in 3D`() {
        val rect = listOf(
            Point3D(0f, 0f, 0f),
            Point3D(3f, 0f, 0f),
            Point3D(3f, 0f, 4f),
            Point3D(0f, 0f, 4f)
        )
        val area = DistanceCalculator.calculatePolygonArea(rect)
        assertEquals(12.0f, area, 0.0001f)
    }

    @Test
    fun `calculateBoundingBox computes dimensions volume and surface area`() {
        val a = Point3D(0f, 0f, 0f)
        val b = Point3D(2f, 3f, 4f)
        val box = DistanceCalculator.calculateBoundingBox(a, b)
        // dims: 4, 3, 2
        assertEquals(4.0f, box.length, 0.0001f)
        assertEquals(3.0f, box.breadth, 0.0001f)
        assertEquals(2.0f, box.height, 0.0001f)
        assertEquals(24.0f, box.volume, 0.0001f)
        // 2 * (12 + 8 + 6) = 2 * 26 = 52
        assertEquals(52.0f, box.surfaceArea, 0.0001f)
    }

    @Test
    fun `calculateRectangleDimensions computes planar dimensions and area`() {
        val a = Point3D(0f, 0f, 0f)
        val b = Point3D(3f, 0f, 4f)
        val rect = DistanceCalculator.calculateRectangleDimensions(a, b)
        assertEquals(4.0f, rect.length, 0.0001f)
        assertEquals(3.0f, rect.breadth, 0.0001f)
        assertEquals(12.0f, rect.area, 0.0001f)
        assertEquals(14.0f, rect.perimeter, 0.0001f)
    }

    @Test
    fun `snapToNearestEdge snaps point within threshold`() {
        val segment = Point3D(0f, 0f, 0f) to Point3D(1f, 0f, 0f)
        val pointNear = Point3D(0.5f, 0.02f, 0f) // 2cm away
        val (snapped, didSnap) = DistanceCalculator.snapToNearestEdge(pointNear, listOf(segment), thresholdMeters = 0.035f)
        assertEquals(true, didSnap)
        assertEquals(0.5f, snapped.x, 0.001f)
        assertEquals(0.0f, snapped.y, 0.001f)
    }

    @Test
    fun `snapToLevelAxis snaps near-vertical ray to vertical axis`() {
        val start = Point3D(0f, 0f, 0f)
        val current = Point3D(0.02f, 1.5f, 0.01f) // slight tilt
        val (snapped, didSnap) = DistanceCalculator.snapToLevelAxis(start, current, thresholdDegrees = 3.5f)
        assertEquals(true, didSnap)
        assertEquals(0f, snapped.x, 0.0001f)
        assertEquals(1.5f, snapped.y, 0.0001f)
        assertEquals(0f, snapped.z, 0.0001f)
    }
}
