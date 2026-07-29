package com.badukai.next.ui

import androidx.compose.ui.graphics.Color

enum class GameTheme(val displayName: String) {
    WARM_LIGHT("Warm Light"),
    DARK("Dark"),
    MODERN_MINIMAL("Modern Minimal"),
    ANCIENT("Ancient")
}

data class ThemeColors(
    // Board
    val BoardBackground: Color,
    val BoardLine: Color,
    val StarPoint: Color,

    // Stones
    val BlackStone: Color,
    val BlackStoneHighlight: Color,
    val WhiteStone: Color,
    val WhiteStoneHighlight: Color,
    val WhiteStoneBorder: Color,

    // Last move
    val LastMoveMarkerOnBlack: Color,
    val LastMoveMarkerOnWhite: Color,
    val LastMoveMarker: Color,

    // UI
    val Background: Color,
    val Surface: Color,
    val SurfaceVariant: Color,

    val TextPrimary: Color,
    val TextSecondary: Color,
    val TextOnAccent: Color,

    val Accent: Color,
    val AccentVariant: Color,
    val AccentLight: Color,

    val Divider: Color,

    // Buttons
    val ButtonActive: Color,
    val ButtonPressed: Color,
    val ButtonDisabled: Color,
    val ButtonDisabledText: Color,

    val Danger: Color,
    val DangerLight: Color,
    val Warning: Color,
    val WarningLight: Color,

    // Status
    val ThinkingIndicator: Color,
    val ThinkingBg: Color,

    // Coordinate text color
    val CoordinateText: Color
)

object ThemePresets {
    val WARM_LIGHT = ThemeColors(
        BoardBackground = Color(0xFFE4B877),
        BoardLine = Color(0xFF3A2A18),
        StarPoint = Color(0xFF3A2A18),

        BlackStone = Color(0xFF111111),
        BlackStoneHighlight = Color(0xFF555555),
        WhiteStone = Color(0xFFFAFAF5),
        WhiteStoneHighlight = Color(0xFFFFFFFF),
        WhiteStoneBorder = Color(0xFF9A9A9A),

        LastMoveMarkerOnBlack = Color(0xFFFF5252),
        LastMoveMarkerOnWhite = Color(0xFFE53935),
        LastMoveMarker = Color(0xFFE53935),

        Background = Color(0xFFF5F1EA),
        Surface = Color(0xFFFFFFFF),
        SurfaceVariant = Color(0xFFFDF8F1),

        TextPrimary = Color(0xFF201A12),
        TextSecondary = Color(0xFF706250),
        TextOnAccent = Color(0xFFFFFFFF),

        Accent = Color(0xFF2F6B4F),
        AccentVariant = Color(0xFF4A8C6B),
        AccentLight = Color(0xFFE3EDE6),

        Divider = Color(0xFFE8DED0),

        ButtonActive = Color(0xFF2F6B4F),
        ButtonPressed = Color(0xFF24563E),
        ButtonDisabled = Color(0xFFC9C0B2),
        ButtonDisabledText = Color(0xFF8A7F6F),

        Danger = Color(0xFFB53A2E),
        DangerLight = Color(0xFFFAE5E2),
        Warning = Color(0xFFBF7A1A),
        WarningLight = Color(0xFFFBEFD9),

        ThinkingIndicator = Color(0xFF2F6B4F),
        ThinkingBg = Color(0xFFE3EDE6),

        CoordinateText = Color(0xFF3A2A18).copy(alpha = 0.6f)
    )

    val DARK = ThemeColors(
        BoardBackground = Color(0xFFC4944A),
        BoardLine = Color(0xFF2A2010),
        StarPoint = Color(0xFF2A2010),

        BlackStone = Color(0xFF0D0D0D),
        BlackStoneHighlight = Color(0xFF3A3A3A),
        WhiteStone = Color(0xFFE8E8E0),
        WhiteStoneHighlight = Color(0xFFFFFFFF),
        WhiteStoneBorder = Color(0xFF888888),

        LastMoveMarkerOnBlack = Color(0xFF00D4AA),
        LastMoveMarkerOnWhite = Color(0xFF00B894),
        LastMoveMarker = Color(0xFF00D4AA),

        Background = Color(0xFF121218),
        Surface = Color(0xFF1E1E2A),
        SurfaceVariant = Color(0xFF282836),

        TextPrimary = Color(0xFFE8E8EC),
        TextSecondary = Color(0xFF9999A8),
        TextOnAccent = Color(0xFF121218),

        Accent = Color(0xFF00D4AA),
        AccentVariant = Color(0xFF00B894),
        AccentLight = Color(0xFF1A3A34),

        Divider = Color(0xFF333344),

        ButtonActive = Color(0xFF00D4AA),
        ButtonPressed = Color(0xFF00A882),
        ButtonDisabled = Color(0xFF3A3A48),
        ButtonDisabledText = Color(0xFF666676),

        Danger = Color(0xFFE05555),
        DangerLight = Color(0xFF3A2020),
        Warning = Color(0xFFD4A844),
        WarningLight = Color(0xFF3A3020),

        ThinkingIndicator = Color(0xFF00D4AA),
        ThinkingBg = Color(0xFF1A3A34),

        CoordinateText = Color(0xFF2A2010).copy(alpha = 0.5f)
    )

    val MODERN_MINIMAL = ThemeColors(
        BoardBackground = Color(0xFFE8D5B7),
        BoardLine = Color(0xFF2D2D2D),
        StarPoint = Color(0xFF2D2D2D),

        BlackStone = Color(0xFF1A1A1A),
        BlackStoneHighlight = Color(0xFF4A4A4A),
        WhiteStone = Color(0xFFFCFCFA),
        WhiteStoneHighlight = Color(0xFFFFFFFF),
        WhiteStoneBorder = Color(0xFFB0B0B0),

        LastMoveMarkerOnBlack = Color(0xFFFF6B6B),
        LastMoveMarkerOnWhite = Color(0xFFE53935),
        LastMoveMarker = Color(0xFFFF6B6B),

        Background = Color(0xFFFAFAFA),
        Surface = Color(0xFFFFFFFF),
        SurfaceVariant = Color(0xFFF5F5F5),

        TextPrimary = Color(0xFF1A1A1A),
        TextSecondary = Color(0xFF8A8A8A),
        TextOnAccent = Color(0xFFFFFFFF),

        Accent = Color(0xFF1A1A1A),
        AccentVariant = Color(0xFF3A3A3A),
        AccentLight = Color(0xFFEEEEEE),

        Divider = Color(0xFFE5E5E5),

        ButtonActive = Color(0xFF1A1A1A),
        ButtonPressed = Color(0xFF000000),
        ButtonDisabled = Color(0xFFD5D5D5),
        ButtonDisabledText = Color(0xFFA0A0A0),

        Danger = Color(0xFFD32F2F),
        DangerLight = Color(0xFFFFEBEE),
        Warning = Color(0xFFF57C00),
        WarningLight = Color(0xFFFFF3E0),

        ThinkingIndicator = Color(0xFF1A1A1A),
        ThinkingBg = Color(0xFFEEEEEE),

        CoordinateText = Color(0xFF2D2D2D).copy(alpha = 0.45f)
    )

    val ANCIENT = ThemeColors(
        BoardBackground = Color(0xFFDCB468),
        BoardLine = Color(0xFF4A3728),
        StarPoint = Color(0xFF4A3728),

        BlackStone = Color(0xFF1A1410),
        BlackStoneHighlight = Color(0xFF4A3A30),
        WhiteStone = Color(0xFFF5F0E8),
        WhiteStoneHighlight = Color(0xFFFFFFFF),
        WhiteStoneBorder = Color(0xFFA09080),

        LastMoveMarkerOnBlack = Color(0xFFC41E3A),
        LastMoveMarkerOnWhite = Color(0xFFA01830),
        LastMoveMarker = Color(0xFFC41E3A),

        Background = Color(0xFFF0E6D3),
        Surface = Color(0xFFF8F2E8),
        SurfaceVariant = Color(0xFFEDE0CC),

        TextPrimary = Color(0xFF3C2A18),
        TextSecondary = Color(0xFF8B7355),
        TextOnAccent = Color(0xFFFFF8F0),

        Accent = Color(0xFF8B2500),
        AccentVariant = Color(0xFF6B1C00),
        AccentLight = Color(0xFFF0E0D8),

        Divider = Color(0xFFD4C4A8),

        ButtonActive = Color(0xFF8B2500),
        ButtonPressed = Color(0xFF6B1C00),
        ButtonDisabled = Color(0xFFD8CCB8),
        ButtonDisabledText = Color(0xFFB0A090),

        Danger = Color(0xFFA03030),
        DangerLight = Color(0xFFFAE8E0),
        Warning = Color(0xFFB8860B),
        WarningLight = Color(0xFFFBF0D8),

        ThinkingIndicator = Color(0xFF8B2500),
        ThinkingBg = Color(0xFFF0E0D8),

        CoordinateText = Color(0xFF4A3728).copy(alpha = 0.55f)
    )

    fun forTheme(theme: GameTheme): ThemeColors = when (theme) {
        GameTheme.WARM_LIGHT -> WARM_LIGHT
        GameTheme.DARK -> DARK
        GameTheme.MODERN_MINIMAL -> MODERN_MINIMAL
        GameTheme.ANCIENT -> ANCIENT
    }
}

object BadukNextColors {
    private var _current: ThemeColors = ThemePresets.WARM_LIGHT

    fun setTheme(theme: GameTheme) {
        _current = ThemePresets.forTheme(theme)
    }

    fun current(): ThemeColors = _current

    val BoardBackground get() = _current.BoardBackground
    val BoardLine get() = _current.BoardLine
    val StarPoint get() = _current.StarPoint

    val BlackStone get() = _current.BlackStone
    val BlackStoneHighlight get() = _current.BlackStoneHighlight
    val WhiteStone get() = _current.WhiteStone
    val WhiteStoneHighlight get() = _current.WhiteStoneHighlight
    val WhiteStoneBorder get() = _current.WhiteStoneBorder

    val LastMoveMarkerOnBlack get() = _current.LastMoveMarkerOnBlack
    val LastMoveMarkerOnWhite get() = _current.LastMoveMarkerOnWhite
    val LastMoveMarker get() = _current.LastMoveMarker

    val Background get() = _current.Background
    val Surface get() = _current.Surface
    val SurfaceVariant get() = _current.SurfaceVariant

    val TextPrimary get() = _current.TextPrimary
    val TextSecondary get() = _current.TextSecondary
    val TextOnAccent get() = _current.TextOnAccent

    val Accent get() = _current.Accent
    val AccentVariant get() = _current.AccentVariant
    val AccentLight get() = _current.AccentLight

    val Divider get() = _current.Divider

    val ButtonActive get() = _current.ButtonActive
    val ButtonPressed get() = _current.ButtonPressed
    val ButtonDisabled get() = _current.ButtonDisabled
    val ButtonDisabledText get() = _current.ButtonDisabledText

    val Danger get() = _current.Danger
    val DangerLight get() = _current.DangerLight
    val Warning get() = _current.Warning
    val WarningLight get() = _current.WarningLight

    val ThinkingIndicator get() = _current.ThinkingIndicator
    val ThinkingBg get() = _current.ThinkingBg

    val CoordinateText get() = _current.CoordinateText
}
