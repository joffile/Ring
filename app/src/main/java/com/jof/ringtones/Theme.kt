package com.jof.ringtones

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

data class Pal(
    val dark: Boolean,
    val skyTop: Color, val skyMid: Color, val skyLow: Color,
    val ridgeFar: Color, val ridgeNear: Color,
    val orb: Color, val orbGlow: Color,
    val primary: Color, val secondary: Color, val tertiary: Color,
    val success: Color, val warning: Color, val danger: Color, val info: Color,
    val surface: Color, val surfaceHigh: Color, val border: Color,
    val text: Color, val textDim: Color, val onPrimary: Color, val scrim: Color
)

val Night = Pal(
    dark = true,
    skyTop = Color(0xFF07061A), skyMid = Color(0xFF1E1B4B), skyLow = Color(0xFF4C3F91),
    ridgeFar = Color(0xFF3B3480), ridgeNear = Color(0xFF171335),
    orb = Color(0xFFF5F1E3), orbGlow = Color(0xFFBFA8FF),
    primary = Color(0xFFA78BFA), secondary = Color(0xFF22D3EE), tertiary = Color(0xFFF472B6),
    success = Color(0xFF34D399), warning = Color(0xFFFBBF24), danger = Color(0xFFFB7185), info = Color(0xFF60A5FA),
    surface = Color(0xFF14122E), surfaceHigh = Color(0xFF231F4A), border = Color(0xFF413A80),
    text = Color(0xFFECEAFF), textDim = Color(0xFFA9A3D9), onPrimary = Color(0xFF120F28), scrim = Color(0xFF05040F)
)

val Day = Pal(
    dark = false,
    skyTop = Color(0xFFFFEBD6), skyMid = Color(0xFFFFC3A8), skyLow = Color(0xFFF2777A),
    ridgeFar = Color(0xFFE86A8A), ridgeNear = Color(0xFF9C1458),
    orb = Color(0xFFFFF6D0), orbGlow = Color(0xFFFFC94D),
    primary = Color(0xFFE11D48), secondary = Color(0xFF0284C7), tertiary = Color(0xFF7C3AED),
    success = Color(0xFF047857), warning = Color(0xFFB45309), danger = Color(0xFFB91C1C), info = Color(0xFF1D4ED8),
    surface = Color(0xFFFFF4EA), surfaceHigh = Color(0xFFFFFFFF), border = Color(0xFFEFC0AC),
    text = Color(0xFF3B1024), textDim = Color(0xFF8A5A6B), onPrimary = Color(0xFFFFF7F0), scrim = Color(0xFF3A1220)
)

fun palAt(p: Float): Pal = Pal(
    dark = p < 0.5f,
    skyTop = lerp(Night.skyTop, Day.skyTop, p),
    skyMid = lerp(Night.skyMid, Day.skyMid, p),
    skyLow = lerp(Night.skyLow, Day.skyLow, p),
    ridgeFar = lerp(Night.ridgeFar, Day.ridgeFar, p),
    ridgeNear = lerp(Night.ridgeNear, Day.ridgeNear, p),
    orb = lerp(Night.orb, Day.orb, p),
    orbGlow = lerp(Night.orbGlow, Day.orbGlow, p),
    primary = lerp(Night.primary, Day.primary, p),
    secondary = lerp(Night.secondary, Day.secondary, p),
    tertiary = lerp(Night.tertiary, Day.tertiary, p),
    success = lerp(Night.success, Day.success, p),
    warning = lerp(Night.warning, Day.warning, p),
    danger = lerp(Night.danger, Day.danger, p),
    info = lerp(Night.info, Day.info, p),
    surface = lerp(Night.surface, Day.surface, p),
    surfaceHigh = lerp(Night.surfaceHigh, Day.surfaceHigh, p),
    border = lerp(Night.border, Day.border, p),
    text = lerp(Night.text, Day.text, p),
    textDim = lerp(Night.textDim, Day.textDim, p),
    onPrimary = lerp(Night.onPrimary, Day.onPrimary, p),
    scrim = lerp(Night.scrim, Day.scrim, p)
)

fun schemeFor(pal: Pal): ColorScheme = if (pal.dark) {
    darkColorScheme(
        primary = pal.primary, onPrimary = pal.onPrimary,
        primaryContainer = pal.surfaceHigh, onPrimaryContainer = pal.text,
        secondary = pal.secondary, tertiary = pal.tertiary,
        background = pal.skyTop, onBackground = pal.text,
        surface = pal.surface, onSurface = pal.text,
        surfaceVariant = pal.surfaceHigh, onSurfaceVariant = pal.textDim,
        outline = pal.border, error = pal.danger
    )
} else {
    lightColorScheme(
        primary = pal.primary, onPrimary = pal.onPrimary,
        primaryContainer = pal.surfaceHigh, onPrimaryContainer = pal.text,
        secondary = pal.secondary, tertiary = pal.tertiary,
        background = pal.skyTop, onBackground = pal.text,
        surface = pal.surface, onSurface = pal.text,
        surfaceVariant = pal.surfaceHigh, onSurfaceVariant = pal.textDim,
        outline = pal.border, error = pal.danger
    )
}

val LocalPal = compositionLocalOf { Night }
