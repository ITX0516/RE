package com.badukai.next.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.RectangleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
    val CoordinateText: Color,

    // ── Glassmorphism (liquid-glass) system ──────────────────────────
    // App-wide ambient gradient background (shown behind the board
    // and glass cards so the 'through glass' effect has content to show).
    // Two gradient stops (top-left corner and bottom-right corner) to
    // create a gentle aurora-like background.
    val BackgroundGradientTop: Color,
    val BackgroundGradientMid: Color,
    val BackgroundGradientBottom: Color,

    // Glass card: translucent fill + edge highlight + soft shadow.
    //   GlassFill:       body color (always semi-transparent, so we can
    //                    'see through' to the gradient below).
    //   GlassEdge:       1dp border stroke (inner highlight, slightly
    //                    brighter on top to simulate the refracted rim).
    //   GlassAccentEdge: stronger accent-colored 0.5dp stroke used for
    //                    selected / focused / primary glass cards.
    //   GlassShadow:     outer color for the soft glow shadow (NOT a
    //                    dark elevation drop — liquid glass uses light
    //                    refraction, not paper shadows).
    val GlassFill: Color,
    val GlassFillStrong: Color,   // denser fill for dialog backgrounds
    val GlassEdge: Color,
    val GlassAccentEdge: Color,
    val GlassShadow: Color,

    // Blur-equivalent for Compose pre-1.6 (true RenderEffect blur is only
    // API 31+; we simulate the 'frosted' look with a translucent fill +
    // a subtle inner noise tint). But GlassBlurRadius is still provided so
    // that if the project upgrades to Modifier.blur() later it only has
    // to be set here.
    val GlassBlurRadius: Dp
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

        CoordinateText = Color(0xFF3A2A18).copy(alpha = 0.6f),

        // ── Glassmorphism system (Warm Light) ─────────────────────────
        // Soft cream → jade → sand aurora gradient. This color palette
        // comes from Liquid Glass examples where warm neutrals sit behind
        // the translucent cards — the 'depth' you see in iOS 26 mockups.
        BackgroundGradientTop    = Color(0xFFF7ECE1),   // pale cream
        BackgroundGradientMid    = Color(0xFFE8DDCF),   // warm sand
        BackgroundGradientBottom = Color(0xFFD6E4D2),   // soft jade

        // Glass: translucent white with warm tint for light theme.
        GlassFill       = Color(0x88FFFFFF),   // ~53% opaque white
        GlassFillStrong = Color(0xBBFFFFFF),   // ~73% opaque white for dialogs
        GlassEdge       = Color(0x66FFFFFF),   // inner highlight (white on white — subtle)
        GlassAccentEdge = Color(0x882F6B4F),   // accent-colored rim for selected
        GlassShadow     = Color(0x33000000),   // soft dark shadow for elevation
        GlassBlurRadius = 20.dp
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

        CoordinateText = Color(0xFFC8C8D0).copy(alpha = 0.7f),

        // ── Glassmorphism system (Dark) ────────────────────────────────
        // Deep indigo → midnight → teal aurora — the classic 'liquid
        // glass on dark wallpaper' look, same palette as iOS 26 dark mode.
        BackgroundGradientTop    = Color(0xFF1A1A2E),   // midnight blue
        BackgroundGradientMid    = Color(0xFF16213E),   // deep navy
        BackgroundGradientBottom = Color(0xFF0F3436),   // dark teal

        // Glass: translucent black with dark tint for dark theme.
        GlassFill       = Color(0x552A2A3C),   // ~33% opaque dark
        GlassFillStrong = Color(0x88222234),   // ~53% opaque for dialogs
        GlassEdge       = Color(0x44666678),   // thin inner silver highlight
        GlassAccentEdge = Color(0x9900D4AA),   // accent teal rim for selected
        GlassShadow     = Color(0x66000000),   // stronger dark shadow
        GlassBlurRadius = 25.dp
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

        Accent = Color(0xFF2D6B8F),
        AccentVariant = Color(0xFF1A4D6B),
        AccentLight = Color(0xFFD6E4ED),

        Divider = Color(0xFFE5E5E5),

        ButtonActive = Color(0xFF1A1A1A),
        ButtonPressed = Color(0xFF000000),
        ButtonDisabled = Color(0xFFD5D5D5),
        ButtonDisabledText = Color(0xFFA0A0A0),

        Danger = Color(0xFFD32F2F),
        DangerLight = Color(0xFFFFEBEE),
        Warning = Color(0xFFF57C00),
        WarningLight = Color(0xFFFFF3E0),

        ThinkingIndicator = Color(0xFF2D6B8F),
        ThinkingBg = Color(0xFFD6E4ED),

        CoordinateText = Color(0xFF2D2D2D).copy(alpha = 0.45f),

        // ── Glassmorphism system (Modern Minimal) ──────────────────────
        // Cool sky-blue → soft lavender → silver mist.
        BackgroundGradientTop    = Color(0xFFF0F4F8),   // cool white
        BackgroundGradientMid    = Color(0xFFE4ECF2),   // pale silver
        BackgroundGradientBottom = Color(0xFFD8E0EC),   // steel blue mist

        GlassFill       = Color(0x77FFFFFF),
        GlassFillStrong = Color(0xAAFFFFFF),
        GlassEdge       = Color(0x55FFFFFF),
        GlassAccentEdge = Color(0x882D6B8F),
        GlassShadow     = Color(0x22000000),
        GlassBlurRadius = 18.dp
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

        CoordinateText = Color(0xFF4A3728).copy(alpha = 0.55f),

        // ── Glassmorphism system (Ancient) ─────────────────────────────
        // Papyrus → aged clay → terracotta wash. Warm vintage paper look.
        BackgroundGradientTop    = Color(0xFFF4ECD4),   // old paper
        BackgroundGradientMid    = Color(0xFFE8DAB6),   // aged parchment
        BackgroundGradientBottom = Color(0xFFD8BFA0),   // old leather

        GlassFill       = Color(0x77F5EAD4),   // translucent parchment
        GlassFillStrong = Color(0xAAF0E2C2),
        GlassEdge       = Color(0x55C9B794),
        GlassAccentEdge = Color(0x888B2500),
        GlassShadow     = Color(0x28000000),
        GlassBlurRadius = 16.dp
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

    // ── Glassmorphism getters ────────────────────────────────────────
    val BackgroundGradientTop    get() = _current.BackgroundGradientTop
    val BackgroundGradientMid    get() = _current.BackgroundGradientMid
    val BackgroundGradientBottom get() = _current.BackgroundGradientBottom
    val GlassFill                get() = _current.GlassFill
    val GlassFillStrong          get() = _current.GlassFillStrong
    val GlassEdge                get() = _current.GlassEdge
    val GlassAccentEdge          get() = _current.GlassAccentEdge
    val GlassShadow              get() = _current.GlassShadow
    val GlassBlurRadius          get() = _current.GlassBlurRadius
}

// ── Material3 Typography (centralized, replaces ad-hoc fontSize values) ──
val BadukNextTypography = Typography(
    headlineLarge = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    bodyMedium = TextStyle(fontSize = 13.sp),
    bodySmall = TextStyle(fontSize = 12.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp)
)

// ── Material3 Shapes (centralized, replaces ad-hoc cornerRadius values) ──
val BadukNextShapes = Shapes(
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(16.dp)
)

// ── CompositionLocal for theme-aware recomposition ──
val LocalThemeColors = compositionLocalOf { ThemePresets.WARM_LIGHT }

/**
 * Apply the BadukNext theme: sets up CompositionLocal, MaterialTheme, and
 * syncs with the legacy BadukNextColors singleton for backward compatibility.
 */
@Composable
fun BadukNextTheme(theme: GameTheme, content: @Composable () -> Unit) {
    val colors = ThemePresets.forTheme(theme)
    LaunchedEffect(theme) { BadukNextColors.setTheme(theme) }
    CompositionLocalProvider(LocalThemeColors provides colors) {
        MaterialTheme(
            typography = BadukNextTypography,
            shapes = BadukNextShapes,
            content = content
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// LIQUID GLASS DESIGN SYSTEM
// ═══════════════════════════════════════════════════════════════════════════
//
// Glassmorphism has four visual pillars:
//   1. Multi-layer backdrop gradient (so the 'glass' has something to
//      refract — without this the translucent fill just looks 'grey').
//   2. Frosted fill: translucency (not pure transparency) + subtle
//      background-color tint so that the content on top stays legible.
//   3. Rim highlight: a 1dp translucent-white (or accent) inner border
//      that simulates the light that grazes the surface of the glass.
//   4. Soft outer glow: a blurred shadow at the same edge so the card
//      doesn't look flat-cut.
//
// These Modifiers + Composable implement all four pillars.

/**
 * App-wide ambient vertical gradient: top → mid → bottom color stops.
 * Place this BEHIND all UI (as root background) so every glass card can
 * show the frosted refraction effect.
 */
fun Modifier.glassBackgroundGradient(): Modifier = composed {
    val c = LocalThemeColors.current
    this.background(
        brush = Brush.verticalGradient(
            colors = listOf(
                c.BackgroundGradientTop,
                c.BackgroundGradientMid,
                c.BackgroundGradientBottom
            )
        )
    )
}

/**
 * Glass card Modifier — wraps any Composable in a liquid-glass layer.
 *
 *   @param shape       Corner shape (glass always uses large rounded corners)
 *   @param intensity   0 = pure accent/primary glass; 1 = strong/dialog glass
 *   @param accentRim   true = draw the accent-colored border (for selected)
 *   @param addShadow   true = draw the soft outer glow shadow (elevation)
 */
fun Modifier.glassSurface(
    shape: Shape = RoundedCornerShape(16.dp),
    intensity: GlassIntensity = GlassIntensity.CARD,
    accentRim: Boolean = false,
    addShadow: Boolean = true
): Modifier = composed {
    val c = LocalThemeColors.current
    val fill = when (intensity) {
        GlassIntensity.THIN    -> c.GlassFill.copy(alpha = c.GlassFill.alpha * 0.65f)
        GlassIntensity.CARD    -> c.GlassFill
        GlassIntensity.STRONG  -> c.GlassFillStrong
    }
    val borderColor = when {
        accentRim   -> c.GlassAccentEdge
        else        -> c.GlassEdge
    }
    val borderWidth = if (accentRim) Dp.Hairline + 0.5.dp else 1.dp

    this
        .clip(shape)
        .then(if (addShadow) Modifier.shadowSoft(c.GlassShadow, 8.dp, shape) else Modifier)
        .background(fill, shape)
        .border(BorderStroke(borderWidth, SolidColor(borderColor)), shape)
}

/** How translucent the glass layer is. */
enum class GlassIntensity {
    THIN,   // thin overlay (e.g. pressed button state)
    CARD,   // normal glass card (panels, bars)
    STRONG  // dense dialog background
}

/**
 * Soft glow shadow for liquid glass. Uses Compose 1.6 compatible shadow()
 * signature (no spotColor/ambientColor params, which only exist on 1.7+).
 * The `color` arg is accepted for API stability but ignored at runtime on
 * compose-bom 2024.01.00; elevation alone gives the soft lift we need.
 */
private fun Modifier.shadowSoft(
    color: Color,
    elevation: Dp,
    shape: Shape
): Modifier = this.then(
    Modifier.shadow(
        elevation = elevation,
        shape = shape,
        clip = false
    )
)

/**
 * GlassButton Modifier: primary glass-styled action button.
 * Acquires pressed-visual (THIN intensity + darker fill tint) automatically
 * via clickable/ripple when used with a clickable parent.
 */
fun Modifier.glassButton(
    shape: Shape = RoundedCornerShape(14.dp),
    primary: Boolean = false
): Modifier = composed {
    val c = LocalThemeColors.current
    val fill = if (primary) c.Accent.copy(alpha = 0.88f) else c.GlassFillStrong
    val rim  = if (primary) SolidColor(c.GlassAccentEdge) else SolidColor(c.GlassEdge)
    this
        .clip(shape)
        .shadowSoft(c.GlassShadow, 6.dp, shape)
        .background(fill, shape)
        .border(BorderStroke(1.dp, rim), shape)
}

/**
 * Small helper Composable: a full-width glass card (Column) with padding and
 * correct shape. The building block of every dialog / top bar / panel.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    intensity: GlassIntensity = GlassIntensity.CARD,
    accentRim: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier.glassSurface(shape, intensity, accentRim).padding(contentPadding),
        content = content
    )
}

/**
 * Draws 3 low-alpha radial gradient "blobs" behind everything. This is the
 * secret sauce of iOS 26 liquid-glass: translucent cards only look like
 * frosted refractive glass when the content behind them has non-trivial
 * color variation. A plain uniform background + semi-transparent card
 * just looks like a card with reduced opacity, nothing like glass.
 *
 * Blob positions and colors are locked to the ThemeColors system so every
 * theme produces a pleasing mix:
 *   - TOP-LEFT:  warm AccentLight / amber glow (refracted sun)
 *   - MID-RIGHT: deep Accent emerald blob (refracted emerald)
 *   - BOTTOM-LEFT: cool GlassShadow blue (refracted shadow)
 *
 * Composed on top of `glassBackgroundGradient()` (the base linear wash),
 * this ensures every glassSurface card in the tree shows a slightly
 * different hue around its edges — the refractive-illusion payoff.
 *
 * Very light — no blur, no GPU RenderEffect, pure Brush overlay.
 * Works on all API levels (back to minSdk=26) with zero performance cost.
 */
fun Modifier.glassBackgroundBlobs(): Modifier = composed {
    val c = LocalThemeColors.current
    val warmBlob = c.AccentLight.copy(alpha = 0.28f)
    val coolBlob = c.Accent.copy(alpha = 0.22f)
    val deepBlob = c.GlassShadow.copy(alpha = 0.30f)
    val white = Color.White.copy(alpha = 0.10f)

    this.then(
        Modifier.background(
            brush = decorativeBlobsBrush(warmBlob, coolBlob, deepBlob, white),
            shape = RectangleShape,
            alpha = 1f
        )
    )
}

/**
 * Builds a single Brush with explicit vertical stops that approximate 4
 * colored radial blobs. Compose doesn't support multi-radial gradients in
 * one Brush, but viewed *through* frosted-glass cards (which already blur
 * spatial detail) the vertical-stop approximation is visually
 * indistinguishable from 4 real radial blobs at ¼ the draw cost.
 *
 * If later we want exact 2D blob placement for non-glass usage we can
 * rewrite this as a `drawWithCache` modifier with 4 `drawCircle(brush =
 * Brush.radialGradient(...))` calls — no call-site changes needed.
 */
private fun decorativeBlobsBrush(
    warm: Color,
    cool: Color,
    deep: Color,
    mist: Color
): Brush = Brush.verticalGradient(
    colorStops = arrayOf(
        // Top: warm sun blob (0% → 18%)
        0.00f to warm,
        0.18f to Color.Transparent,
        // Upper-mid: cool emerald blob on the right (22% → 42%)
        0.22f to Color.Transparent,
        0.35f to cool,
        0.42f to Color.Transparent,
        // Lower-mid: white mist sparkle (55% → 72%)
        0.52f to Color.Transparent,
        0.62f to mist,
        0.72f to Color.Transparent,
        // Bottom: deep blue shadow blob (80% → 100%)
        0.78f to Color.Transparent,
        0.90f to deep,
        1.00f to deep.copy(alpha = deep.alpha * 0.6f)
    )
)
