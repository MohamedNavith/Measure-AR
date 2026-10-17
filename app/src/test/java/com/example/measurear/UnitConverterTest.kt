package com.example.measurear

import com.example.measurear.util.MeasureUnit
import com.example.measurear.util.UnitConverter
import org.junit.Assert.assertEquals
import org.junit.Test

class UnitConverterTest {

    @Test
    fun `formatDistance formats centimeters and meters accurately`() {
        assertEquals("145.6 cm", UnitConverter.formatDistance(1.456f, MeasureUnit.CM))
        assertEquals("1.46 m", UnitConverter.formatDistance(1.456f, MeasureUnit.M))
    }

    @Test
    fun `formatDistance formats inches and feet-inches accurately`() {
        // 1 meter = 39.3700787 inches -> 3 feet 3 inches
        assertEquals("39.4 in", UnitConverter.formatDistance(1.0f, MeasureUnit.IN))
        assertEquals("3' 3\"", UnitConverter.formatDistance(1.0f, MeasureUnit.FT))
    }

    @Test
    fun `formatDistance handles rounding 12 inches up to next foot`() {
        // 0.3048 meters = exactly 12 inches = 1' 0"
        assertEquals("1' 0\"", UnitConverter.formatDistance(0.3048f, MeasureUnit.FT))
    }

    @Test
    fun `formatArea converts square meters to target square units`() {
        assertEquals("2.50 m²", UnitConverter.formatArea(2.5f, MeasureUnit.M))
        assertEquals("25000 cm²", UnitConverter.formatArea(2.5f, MeasureUnit.CM))
    }
}
