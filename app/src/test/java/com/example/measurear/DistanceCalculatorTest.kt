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
}
