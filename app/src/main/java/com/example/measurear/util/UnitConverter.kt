package com.example.measurear.util

import java.util.Locale
import kotlin.math.floor
import kotlin.math.roundToInt

enum class MeasureUnit(val symbol: String, val label: String) {
    CM("cm", "Centimeters"),
    M("m", "Meters"),
    IN("in", "Inches"),
    FT("ft", "Feet & Inches")
}

object UnitConverter {
    private const val METERS_TO_CM = 100f
    private const val METERS_TO_INCHES = 39.3700787f
    private const val METERS_TO_FEET = 3.2808399f

    fun convertDistance(meters: Float, unit: MeasureUnit): Float {
        return when (unit) {
            MeasureUnit.CM -> meters * METERS_TO_CM
            MeasureUnit.M -> meters
            MeasureUnit.IN -> meters * METERS_TO_INCHES
            MeasureUnit.FT -> meters * METERS_TO_FEET
        }
    }

    fun formatDistance(meters: Float, unit: MeasureUnit): String {
        val safeMeters = meters.coerceAtLeast(0f)
        return when (unit) {
            MeasureUnit.CM -> String.format(Locale.US, "%.1f cm", safeMeters * METERS_TO_CM)
            MeasureUnit.M -> String.format(Locale.US, "%.2f m", safeMeters)
            MeasureUnit.IN -> String.format(Locale.US, "%.1f in", safeMeters * METERS_TO_INCHES)
            MeasureUnit.FT -> {
                val totalInches = safeMeters * METERS_TO_INCHES
                var feet = floor(totalInches / 12f).toInt()
                var inches = (totalInches - feet * 12f).roundToInt()
                if (inches == 12) {
                    feet += 1
                    inches = 0
                }
                if (feet > 0) {
                    "$feet' $inches\""
                } else {
                    "$inches\""
                }
            }
        }
    }

    fun formatArea(squareMeters: Float, unit: MeasureUnit): String {
        val safeSqMeters = squareMeters.coerceAtLeast(0f)
        return when (unit) {
            MeasureUnit.CM -> String.format(Locale.US, "%.0f cm²", safeSqMeters * 10_000f)
            MeasureUnit.M -> String.format(Locale.US, "%.2f m²", safeSqMeters)
            MeasureUnit.IN -> String.format(Locale.US, "%.1f in²", safeSqMeters * 1550.0031f)
            MeasureUnit.FT -> String.format(Locale.US, "%.2f ft²", safeSqMeters * 10.76391f)
        }
    }

    fun formatVolume(cubicMeters: Float, unit: MeasureUnit): String {
        val safeCubicMeters = cubicMeters.coerceAtLeast(0f)
        return when (unit) {
            MeasureUnit.CM -> String.format(Locale.US, "%.0f cm³", safeCubicMeters * 1_000_000f)
            MeasureUnit.M -> String.format(Locale.US, "%.3f m³", safeCubicMeters)
            MeasureUnit.IN -> String.format(Locale.US, "%.0f in³", safeCubicMeters * 61023.74f)
            MeasureUnit.FT -> String.format(Locale.US, "%.2f ft³", safeCubicMeters * 35.31467f)
        }
    }
}
