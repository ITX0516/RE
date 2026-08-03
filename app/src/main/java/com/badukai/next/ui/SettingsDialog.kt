package com.badukai.next.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.badukai.next.engine.ModelSource
import com.badukai.next.game.PlacementMode
import com.badukai.next.game.StoneAnimation

/**
 * iOS-style full-screen Settings page. NOT a dialog — this is a full-route
 * composable with:
 *   - Large inline "Settings" title (iOS largeTitle)
 *   - Top-left chevron-back navigation pill
 *   - Grouped inset list (iOS UITableView.Grouped) where each section is a
 *     rounded card with thin separators, section footers, and navigation rows
 *   - Liquid-glass background gradient inherited from root; each group
 *     card uses GlassIntensity.CARD so the background subtly shows through
 *   - Consistent 14pt body / 12pt footnote typography matching iOS HIG
 *
 * Callers route to this page by toggling state.showSettings, which switches
 * GameScreen from BoardView composition to SettingsScreen composition via
 * a simple AnimatedContent. See GameScreen.kt for the routing switch.
 */
@Composable
fun SettingsScreen(
    showCoordinates: Boolean,
    soundEnabled: Boolean,
    currentTheme: GameTheme,
    currentPlacementMode: PlacementMode,
    currentAnimation: StoneAnimation,
    placeSoundIndex: Int,
    aiMoveTimeSeconds: Int,
    aiCanResign: Boolean,
    aiModelSource: ModelSource,
    customModelDisplayName: String,
    onBack: () -> Unit,
    onToggleCoordinates: () -> Unit,
    onToggleSound: () -> Unit,
    onSetTheme: (GameTheme) -> Unit,
    onSetPlacementMode: (PlacementMode) -> Unit,
    onSetAnimation: (StoneAnimation) -> Unit,
    onSetPlaceSound: (Int) -> Unit,
    onSetAiMoveTime: (Int) -> Unit,
    onSetAiCanResign: (Boolean) -> Unit,
    onSetAiModelSource: (ModelSource) -> Unit,
    onPickCustomModel: () -> Unit,
    onResetAiModelToBundled: () -> Unit
) {
    val colors = LocalThemeColors.current
    Box(
        Modifier
            .fillMaxSize()
            .glassBackgroundGradient()
            .statusBarsPadding()
    ) {
        Column(Modifier.fillMaxSize()) {
            // ═══ Navigation bar + Large title (iOS largeTitle style) ═══
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back pill: chevron.left + "Back"
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .glassSurface(
                            shape = RoundedCornerShape(12.dp),
                            intensity = GlassIntensity.THIN,
                            accentRim = false,
                            addShadow = false
                        )
                        .clickable(onClick = onBack)
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "\u2039",
                            color = colors.Accent,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.offset(y = (-1.5).dp)
                        )
                        Spacer(Modifier.width(2.dp))
                        Text(
                            "Back",
                            color = colors.Accent,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Large title (pure text, not in a glass box — matches iOS)
            Text(
                "Settings",
                color = colors.TextPrimary,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 2.dp)
            )

            // ═══ Scrollable grouped list ═══
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(28.dp)
            ) {
                // ── Section: Appearance ──
                SettingsSectionHeader("APPEARANCE")
                SettingsGroupCard {
                    ThemeGroupedRow(selectedTheme = currentTheme, onSelect = onSetTheme)
                }
                SettingsSectionFooter("Choose a visual theme. All themes ship with a matching liquid-glass gradient, glass edge tint, and stone finish.")

                // ── Section: Board ──
                SettingsSectionHeader("BOARD & INTERACTION")
                SettingsGroupCard {
                    ToggleGroupedRow(
                        title = "Coordinates",
                        subtitle = null,
                        checked = showCoordinates,
                        onToggle = onToggleCoordinates
                    )
                    IosThinDivider()
                    PlacementModeGrouped(
                        current = currentPlacementMode,
                        onSelect = onSetPlacementMode
                    )
                    IosThinDivider()
                    StoneAnimationGrouped(
                        current = currentAnimation,
                        onSelect = onSetAnimation
                    )
                }

                // ── Section: Sound ──
                SettingsSectionHeader("SOUND")
                SettingsGroupCard {
                    ToggleGroupedRow(
                        title = "Sound Effects",
                        subtitle = null,
                        checked = soundEnabled,
                        onToggle = onToggleSound
                    )
                }
                SettingsGroupCard {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                        Text(
                            "Stone Place Sound",
                            color = colors.TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .glassSurface(
                                    shape = RoundedCornerShape(12.dp),
                                    intensity = GlassIntensity.THIN,
                                    addShadow = false
                                )
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            for (i in 0 until 5) {
                                val label = "S${i + 1}"
                                val selected = i == placeSoundIndex
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(9.dp))
                                        .then(
                                            if (selected)
                                                Modifier.background(
                                                    brush = Brush.horizontalGradient(
                                                        listOf(colors.Accent, colors.AccentLight)
                                                    )
                                                )
                                            else Modifier
                                        )
                                        .clickable { onSetPlaceSound(i) }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        label,
                                        color = if (selected) colors.TextOnAccent else colors.TextPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }

                // ── Section: AI ──
                SettingsSectionHeader("AI")
                SettingsGroupCard {
                    AiMoveTimeStepper(value = aiMoveTimeSeconds, onChange = onSetAiMoveTime)
                    IosThinDivider()
                    ToggleGroupedRow(
                        title = "AI Can Resign",
                        subtitle = "Allows AI to resign when losing badly",
                        checked = aiCanResign,
                        onToggle = { onSetAiCanResign(!aiCanResign) }
                    )
                }
                SettingsSectionFooter("Longer move times give stronger play but make the app feel slower. 30–60s is a good balance on a 6b model.")

                SettingsSectionHeader("AI WEIGHTS")
                SettingsGroupCard {
                    ModelSource.entries.forEachIndexed { idx, src ->
                        val selected = aiModelSource == src
                        val enabled = src != ModelSource.CUSTOM || customModelDisplayName.isNotBlank()
                        val subtitle = when (src) {
                            ModelSource.BUNDLED_ASSET -> "Built-in 6b, works offline"
                            ModelSource.DOWNLOADED -> "Downloaded from katagotraining.org"
                            ModelSource.CUSTOM -> if (customModelDisplayName.isNotBlank()) customModelDisplayName else "Not imported"
                            else -> ""
                        }
                        RadioGroupedRow(
                            title = src.displayName,
                            subtitle = subtitle,
                            selected = selected && enabled,
                            enabled = enabled,
                            onClick = { if (enabled) onSetAiModelSource(src) }
                        )
                        if (idx < ModelSource.entries.size - 1) IosThinDivider()
                    }
                }
                SettingsGroupCard {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .glassButton(shape = RoundedCornerShape(14.dp), primary = true)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onPickCustomModel() }
                                .padding(vertical = 11.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Import Custom…",
                                color = colors.TextOnAccent,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .glassSurface(
                                    shape = RoundedCornerShape(14.dp),
                                    intensity = GlassIntensity.CARD,
                                    accentRim = true,
                                    addShadow = false
                                )
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onResetAiModelToBundled() }
                                .padding(vertical = 11.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Reset to Built-in",
                                color = colors.TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
                SettingsSectionFooter("Supported formats: .bin.gz / .txt.gz (raw KataGo weights). File must be ≥ 1MB and valid gzip. Custom imports live in app-private storage.")

                // Footer: version
                Spacer(Modifier.height(4.dp))
                Text(
                    "BadukNext v1.0",
                    color = colors.TextSecondary.copy(alpha = 0.65f),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Private building blocks: iOS grouped-list look
// ─────────────────────────────────────────────────────────────

@Composable
private fun SettingsSectionHeader(label: String) {
    val colors = LocalThemeColors.current
    Text(
        label,
        color = colors.TextSecondary.copy(alpha = 0.85f),
        fontSize = 12.5.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.4.sp,
        modifier = Modifier.padding(start = 6.dp, end = 6.dp)
    )
}

@Composable
private fun SettingsSectionFooter(text: String) {
    val colors = LocalThemeColors.current
    Text(
        text,
        color = colors.TextSecondary.copy(alpha = 0.75f),
        fontSize = 12.sp,
        lineHeight = 16.sp,
        modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 6.dp)
    )
}

@Composable
private fun SettingsGroupCard(
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = LocalThemeColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassSurface(
                shape = RoundedCornerShape(16.dp),
                intensity = GlassIntensity.CARD,
                accentRim = false,
                addShadow = false
            )
    ) {
        content()
    }
}

/** Thin 0.5dp inset divider used inside grouped cards between rows. */
@Composable
private fun IosThinDivider() {
    val colors = LocalThemeColors.current
    Divider(
        color = colors.TextSecondary.copy(alpha = 0.10f),
        thickness = 0.5.dp,
        modifier = Modifier.padding(start = 44.dp)
    )
}

/** Standard iOS-style toggle row — icon space + title/subtitle + Switch trailing. */
@Composable
private fun ToggleGroupedRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onToggle: () -> Unit
) {
    val colors = LocalThemeColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onToggle)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                color = colors.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    color = colors.TextSecondary,
                    fontSize = 12.sp
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.TextOnAccent,
                checkedTrackColor = colors.Accent,
                uncheckedThumbColor = colors.Surface,
                uncheckedTrackColor = colors.Divider
            )
        )
    }
}

/** Inline radio row with leading check-circle; used for single-choice rows. */
@Composable
private fun RadioGroupedRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val colors = LocalThemeColors.current
    val alpha = if (enabled) 1f else 0.45f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .border(
                    if (selected) 6.dp else 1.5.dp,
                    if (selected) colors.Accent else colors.TextSecondary.copy(alpha = alpha),
                    CircleShape
                )
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = colors.TextPrimary.copy(alpha = alpha),
                fontSize = 15.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
            )
            Text(
                subtitle,
                color = colors.TextSecondary.copy(alpha = alpha),
                fontSize = 12.sp
            )
        }
    }
}

/** Inline segmented theme picker — horizontal chip row inside a grouped row. */
@Composable
private fun ThemeGroupedRow(
    selectedTheme: GameTheme,
    onSelect: (GameTheme) -> Unit
) {
    val colors = LocalThemeColors.current
    Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
        Text(
            "Theme",
            color = colors.TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .glassSurface(
                    shape = RoundedCornerShape(12.dp),
                    intensity = GlassIntensity.THIN,
                    addShadow = false
                )
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            GameTheme.entries.forEach { theme ->
                val selected = theme == selectedTheme
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .then(
                            if (selected)
                                Modifier.background(
                                    brush = Brush.horizontalGradient(
                                        listOf(colors.Accent, colors.AccentLight)
                                    )
                                )
                            else Modifier
                        )
                        .clickable { onSelect(theme) }
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        theme.displayName,
                        color = if (selected) colors.TextOnAccent else colors.TextPrimary,
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

/** Inline segmented placement mode picker. */
@Composable
private fun PlacementModeGrouped(
    current: PlacementMode,
    onSelect: (PlacementMode) -> Unit
) {
    val colors = LocalThemeColors.current
    Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
        Text(
            "Placement Mode",
            color = colors.TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(8.dp))
        PlacementMode.entries.forEach { mode ->
            val selected = mode == current
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onSelect(mode) }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .border(
                            if (selected) 5.dp else 1.5.dp,
                            if (selected) colors.Accent else colors.TextSecondary,
                            CircleShape
                        )
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    mode.displayName,
                    color = colors.TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                )
            }
        }
    }
}

/** Inline segmented stone-animation picker. */
@Composable
private fun StoneAnimationGrouped(
    current: StoneAnimation,
    onSelect: (StoneAnimation) -> Unit
) {
    val colors = LocalThemeColors.current
    Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
        Text(
            "Stone Animation",
            color = colors.TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(8.dp))
        StoneAnimation.entries.forEach { anim ->
            val selected = anim == current
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onSelect(anim) }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .border(
                            if (selected) 5.dp else 1.5.dp,
                            if (selected) colors.Accent else colors.TextSecondary,
                            CircleShape
                        )
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    anim.displayName,
                    color = colors.TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                )
            }
        }
    }
}

/** Stepper for AI move time (iOS-style stepper inside a grouped card cell). */
@Composable
private fun AiMoveTimeStepper(
    value: Int,
    onChange: (Int) -> Unit
) {
    val colors = LocalThemeColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "AI Move Time",
                color = colors.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                "Seconds per move",
                color = colors.TextSecondary,
                fontSize = 12.sp
            )
        }
        Row(
            modifier = Modifier
                .glassSurface(
                    shape = RoundedCornerShape(12.dp),
                    intensity = GlassIntensity.THIN,
                    addShadow = false
                )
                .padding(3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .glassSurface(
                        shape = RoundedCornerShape(10.dp),
                        intensity = GlassIntensity.CARD,
                        addShadow = false
                    )
                    .clickable { onChange((value - 5).coerceAtLeast(5)) }
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("\u2212", color = colors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(8.dp))
            Text(
                "$value",
                color = colors.TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(54.dp)
            )
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .glassSurface(
                        shape = RoundedCornerShape(10.dp),
                        intensity = GlassIntensity.CARD,
                        addShadow = false
                    )
                    .clickable { onChange((value + 5).coerceAtMost(300)) }
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("+", color = colors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
