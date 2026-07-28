package com.badukai.next.ui

import androidx.compose.ui.graphics.Color

/**
 * Color scheme for BadukNext
 */
object BadukNextColors {
    // Board colors - kaya wood style
    val BoardBackground = Color(0xFFE4B877) // Kaya wood tone
    val BoardLine = Color(0xFF3A2A18)       // Warm dark brown lines
    val StarPoint = Color(0xFF3A2A18)       // Same as lines

    // Stone colors
    val BlackStone = Color(0xFF111111)      // Deep black
    val BlackStoneHighlight = Color(0xFF555555)
    val WhiteStone = Color(0xFFFAFAF5)      // Warm off-white (clam shell tone)
    val WhiteStoneHighlight = Color(0xFFFFFFFF)
    val WhiteStoneBorder = Color(0xFF9A9A9A)

    // Last move markers
    val LastMoveMarkerOnBlack = Color(0xFFFF5252) // Red ring on black
    val LastMoveMarkerOnWhite = Color(0xFFE53935) // Dark red ring on white
    val LastMoveMarker = Color(0xFFE53935)

    // UI colors
    val Background = Color(0xFFF5F1EA)      // Warm parchment background
    val Surface = Color(0xFFFFFFFF)
    val SurfaceVariant = Color(0xFFFDF8F1)  // Warm card surface

    val TextPrimary = Color(0xFF201A12)
    val TextSecondary = Color(0xFF706250)
    val TextOnAccent = Color(0xFFFFFFFF)

    val Accent = Color(0xFF2F6B4F)          // Deep green accent
    val AccentVariant = Color(0xFF4A8C6B)
    val AccentLight = Color(0xFFE3EDE6)     // Light green tint

    val Divider = Color(0xFFE8DED0)

    // Button states
    val ButtonActive = Color(0xFF2F6B4F)
    val ButtonPressed = Color(0xFF24563E)
    val ButtonDisabled = Color(0xFFC9C0B2)
    val ButtonDisabledText = Color(0xFF8A7F6F)

    val Danger = Color(0xFFB53A2E)          // Resign red
    val DangerLight = Color(0xFFFAE5E2)
    val Warning = Color(0xFFBF7A1A)
    val WarningLight = Color(0xFFFBEFD9)

    // Status colors
    val ThinkingIndicator = Color(0xFF2F6B4F)
    val ThinkingBg = Color(0xFFE3EDE6)
}
