package com.example.saqr.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

object SaqrColors {
    // Canvas & Void Backgrounds
    val VoidBlack = Color(0xFF06090F)
    val DarkCosmic = Color(0xFF0B101D)
    val SurfaceDark = Color(0xFF111827)
    val SurfaceGlass = Color(0x1AFFFFFF)
    val SurfaceGlassElevated = Color(0x28FFFFFF)
    val SurfaceGlassNavy = Color(0x33101C38)

    // Neon & Radiant Accents
    val ElectricCyan = Color(0xFF00F0FF)
    val DeepCyan = Color(0xFF00A3FF)
    val PlasmaViolet = Color(0xFF9D4EDD)
    val NeonPurple = Color(0xFF7B2CBF)
    val EmeraldPulse = Color(0xFF00E676)
    val CyberAmber = Color(0xFFFFB703)
    val CrimsonFlare = Color(0xFFFF0055)

    // Glass Borders
    val GlassBorderLight = Color(0x26FFFFFF)
    val GlassBorderCyan = Color(0x4D00F0FF)
    val GlassBorderViolet = Color(0x4D9D4EDD)

    // Text & Content
    val TextPrimary = Color(0xFFF1F5F9)
    val TextSecondary = Color(0xFF94A3B8)
    val TextTertiary = Color(0xFF64748B)

    // Reactive Gradients
    val CyanVioletGradient = Brush.linearGradient(
        listOf(ElectricCyan, PlasmaViolet)
    )
    val CyanEmeraldGradient = Brush.linearGradient(
        listOf(ElectricCyan, EmeraldPulse)
    )
    val GlassCardBorderGradient = Brush.linearGradient(
        listOf(Color(0x6000F0FF), Color(0x20FFFFFF), Color(0x609D4EDD))
    )
    val ThermalWarningGradient = Brush.linearGradient(
        listOf(CyberAmber, CrimsonFlare)
    )
}
