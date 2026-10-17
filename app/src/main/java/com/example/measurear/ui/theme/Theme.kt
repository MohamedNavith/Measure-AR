package com.example.measurear.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val ArYellow = Color(0xFFFFCC00)
val ArGreen = Color(0xFF34C759)
val GlassSurfaceDark = Color(0xAA16161A)
val GlassBorderLight = Color(0x33FFFFFF)

private val DarkColorScheme = darkColorScheme(
    primary = ArYellow,
    onPrimary = Color.Black,
    secondary = ArGreen,
    onSecondary = Color.Black,
    surface = Color(0xFF141417),
    background = Color(0xFF0B0B0E),
    onSurface = Color.White,
    onBackground = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFFD9A700),
    onPrimary = Color.Black,
    secondary = ArGreen,
    surface = Color(0xFFF6F6F9),
    background = Color.White
)

val TabularMeasureTextStyle = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Bold,
    fontFeatureSettings = "tnum",
    letterSpacing = (-0.3).sp
)

private val AppTypography = Typography(
    headlineMedium = TabularMeasureTextStyle.copy(fontSize = 28.sp),
    titleLarge = TabularMeasureTextStyle.copy(fontSize = 20.sp),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp
    )
)

@Composable
fun MeasureArTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}
