package com.sakarrobotics.installationassistant.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Sakar Robotics brand tokens (orange primary). */
object SakarColors {
    val Orange = Color(0xFFFF6100)
    val OrangeLight = Color(0xFFFF914D)
    val OrangeSoft = Color(0xFFFFF1E6)
    val OrangeSoftStrong = Color(0xFFFFE0CC)
    val Ink = Color(0xFF0F172A)
    val Muted = Color(0xFF475569)
    val Faint = Color(0xFF64748B)
    val Border = Color(0xFFE2E8F0)
    val Background = Color(0xFFF8FAFC)
    val Surface = Color(0xFFFFFFFF)
    val Success = Color(0xFF047857)
    val SuccessSoft = Color(0xFFD1FAE5)
    val Warning = Color(0xFF92400E)
    val WarningSoft = Color(0xFFFEF3C7)
    val Danger = Color(0xFFB91C1C)
    val DangerSoft = Color(0xFFFEE2E2)
    val Info = Color(0xFF1D4ED8)
    val InfoSoft = Color(0xFFDBEAFE)

    // Map canvas
    val CellUnknown = Color(0xFF334155)
    val CellFree = Color(0xFFF8FAFC)
    val CellWall = Color(0xFF0F172A)
    val CellRobot = Orange
}

private val SakarScheme = lightColorScheme(
    primary = SakarColors.Orange,
    onPrimary = Color.White,
    primaryContainer = SakarColors.OrangeSoftStrong,
    onPrimaryContainer = SakarColors.Ink,
    secondary = SakarColors.Ink,
    onSecondary = Color.White,
    secondaryContainer = SakarColors.OrangeSoft,
    onSecondaryContainer = SakarColors.Ink,
    background = SakarColors.Background,
    onBackground = SakarColors.Ink,
    surface = SakarColors.Surface,
    onSurface = SakarColors.Ink,
    surfaceVariant = SakarColors.OrangeSoft,
    onSurfaceVariant = SakarColors.Muted,
    outline = SakarColors.Border,
    error = SakarColors.Danger,
    onError = Color.White
)

private val SakarShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp)
)

@Composable
fun SakarTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = SakarScheme, shapes = SakarShapes, content = content)
}
