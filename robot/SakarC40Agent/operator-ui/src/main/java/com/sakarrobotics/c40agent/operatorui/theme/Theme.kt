package com.sakarrobotics.c40agent.operatorui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Sakar Robotics brand tokens, transcribed 1:1 from
 * docs/architecture/SAKAR_WEB_UI_DESIGN_SYSTEM.md (the same tokens the
 * Sakar Web fleet-management console uses) - this on-robot app is meant
 * to look like the same product family, not a separately-invented look.
 * No Keenon colors/branding anywhere in this file or anything built on it.
 */
object SakarColors {
    val Background = Color(0xFFF8FAFC)
    val Surface = Color(0xFFFFFFFF)
    val Border = Color(0xFFE2E8F0)
    val BorderStrong = Color(0xFFCBD5E1)
    val TextPrimary = Color(0xFF0F172A)
    val TextMuted = Color(0xFF475569)
    val TextFaint = Color(0xFF64748B)
    val TextSubtle = Color(0xFF94A3B8)
    val Primary = Color(0xFFFF914D)
    val PrimaryStrong = Color(0xFFFF6100)
    val PrimarySoft = Color(0xFFFFF6F0)
    val PrimarySoftStrong = Color(0xFFFFE9DB)
    val Success = Color(0xFF059669)
    val Warning = Color(0xFFD97706)
    val WarningText = Color(0xFF92400E)
    val Danger = Color(0xFFDC2626)
    val Info = Color(0xFF2563EB)

    // Not in the web design system (this app runs on a robot-mounted screen, not an office
    // monitor) - a dedicated dark surface for the always-on home dashboard, kept deliberately
    // separate from "dark mode" (there is none, matching the web app) and used only where a robot
    // console legitimately benefits from it (status band, raw diagnostic log).
    val ConsoleBackground = Color(0xFF0F172A)
    val ConsoleText = Color(0xFF34D399)
}

private val SakarLightColorScheme = lightColorScheme(
    primary = SakarColors.Primary,
    onPrimary = Color.White,
    primaryContainer = SakarColors.PrimarySoftStrong,
    onPrimaryContainer = SakarColors.PrimaryStrong,
    secondary = SakarColors.Info,
    background = SakarColors.Background,
    onBackground = SakarColors.TextPrimary,
    surface = SakarColors.Surface,
    onSurface = SakarColors.TextPrimary,
    surfaceVariant = SakarColors.PrimarySoft,
    onSurfaceVariant = SakarColors.TextMuted,
    outline = SakarColors.Border,
    error = SakarColors.Danger,
    onError = Color.White
)

private val SakarTypography = Typography(
    headlineLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 30.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 24.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp)
)

@Composable
fun SakarTheme(content: @Composable () -> Unit) {
    // The reference design system explicitly has no dark mode (see SAKAR_WEB_UI_DESIGN_SYSTEM.md
    // section 1) - this on-robot app follows the same rule (always the light Sakar scheme) rather
    // than inventing one.
    MaterialTheme(
        colorScheme = SakarLightColorScheme,
        typography = SakarTypography,
        content = content
    )
}
