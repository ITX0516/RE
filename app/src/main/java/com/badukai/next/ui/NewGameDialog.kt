package com.badukai.next.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.badukai.next.game.GameConstants
import com.badukai.next.game.StoneColor

@Composable
fun NewGameDialog(
    onDismiss: () -> Unit,
    onStartGame: (StoneColor, Int, Int, Float, Int, Boolean) -> Unit,
    initialAiTime: Int = 20,
    initialAiCanResign: Boolean = true
) {
    val colors = LocalThemeColors.current
    var selectedColor by remember { mutableStateOf(StoneColor.BLACK) }
    var selectedSize by remember { mutableIntStateOf(19) }
    var selectedHandicap by remember { mutableIntStateOf(0) }
    var komiText by remember { mutableStateOf("${GameConstants.DEFAULT_KOMI}") }
    var aiTime by remember { mutableIntStateOf(initialAiTime) }
    var aiCanResign by remember { mutableStateOf(initialAiCanResign) }

    Dialog(onDismissRequest = onDismiss) {
        // Liquid-glass dialog container
        Box(
            Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .glassSurface(
                    shape = RoundedCornerShape(28.dp),
                    intensity = GlassIntensity.STRONG,
                    accentRim = false,
                    addShadow = true
                )
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Title pill
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassSurface(
                            shape = RoundedCornerShape(14.dp),
                            intensity = GlassIntensity.THIN,
                            addShadow = false
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("New Game", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.TextPrimary)
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .glassSurface(
                                shape = CircleShape,
                                intensity = GlassIntensity.CARD,
                                accentRim = true,
                                addShadow = false
                            )
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("\u2715", color = colors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(14.dp))

                // AI plays (player color) selector
                Text("AI plays", color = colors.TextSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .glassSurface(
                            shape = RoundedCornerShape(18.dp),
                            intensity = GlassIntensity.THIN,
                            addShadow = false
                        )
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    StoneColor.entries.forEach { c ->
                        // The selector shows "what the AI plays" — so if
                        // AI plays BLACK then the user has chosen WHITE,
                        // hence `selected` compares against the OPPOSITE.
                        ColorOption(c, c.displayName, selected = selectedColor != c) {
                            selectedColor = c.opposite()
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                Text("Board: ${selectedSize}\u00D7${selectedSize}", color = colors.TextSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                StepperGlassRow(
                    value = selectedSize,
                    onDec = { if (selectedSize > 7) selectedSize-- },
                    onInc = { if (selectedSize < 19) selectedSize++ }
                )

                Spacer(Modifier.height(14.dp))
                Text("Handicap: $selectedHandicap", color = colors.TextSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                StepperGlassRow(
                    value = selectedHandicap,
                    onDec = { if (selectedHandicap > 0) selectedHandicap-- },
                    onInc = { if (selectedHandicap < 9) selectedHandicap++ }
                )

                Spacer(Modifier.height(14.dp))
                Text("Komi", color = colors.TextSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier
                        .width(100.dp)
                        .glassSurface(
                            shape = RoundedCornerShape(14.dp),
                            intensity = GlassIntensity.CARD,
                            accentRim = false,
                            addShadow = false
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        komiText, color = colors.TextPrimary, fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
                Row(
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .glassSurface(
                            shape = RoundedCornerShape(14.dp),
                            intensity = GlassIntensity.THIN,
                            addShadow = false
                        )
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("0.5", "6.5", "7.5").forEach { preset ->
                        val sel = komiText == preset
                        Box(
                            Modifier
                                .glassSurface(
                                    shape = RoundedCornerShape(10.dp),
                                    intensity = if (sel) GlassIntensity.STRONG else GlassIntensity.THIN,
                                    accentRim = sel,
                                    addShadow = false
                                )
                                .background(if (sel) colors.Accent.copy(alpha = 0.9f) else Color.Transparent)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { komiText = preset }
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                preset,
                                color = if (sel) colors.TextOnAccent else colors.TextPrimary,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                Text("AI move time: ${aiTime}s", color = colors.TextSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                StepperGlassRow(
                    value = aiTime,
                    onDec = { aiTime = (aiTime - 5).coerceAtLeast(1) },
                    onInc = { aiTime = (aiTime + 5).coerceAtMost(120) }
                )

                Spacer(Modifier.height(10.dp))
                // Toggle AI can resign (glass row)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassSurface(
                            shape = RoundedCornerShape(14.dp),
                            intensity = GlassIntensity.CARD,
                            addShadow = false
                        )
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { aiCanResign = !aiCanResign }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("AI can resign", color = colors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text("Allow AI to resign when losing badly", color = colors.TextSecondary, fontSize = 11.sp)
                    }
                    Switch(
                        checked = aiCanResign, onCheckedChange = { aiCanResign = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = colors.TextOnAccent,
                            checkedTrackColor = colors.Accent,
                            uncheckedThumbColor = colors.Surface,
                            uncheckedTrackColor = colors.Divider
                        )
                    )
                }

                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Cancel: outlined glass
                    Box(
                        Modifier
                            .weight(1f)
                            .glassSurface(
                                shape = RoundedCornerShape(14.dp),
                                intensity = GlassIntensity.CARD,
                                accentRim = false,
                                addShadow = false
                            )
                            .clip(RoundedCornerShape(14.dp))
                            .clickable(onClick = onDismiss)
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Cancel", color = colors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                    // Start: primary glass
                    Box(
                        Modifier
                            .weight(1f)
                            .glassButton(shape = RoundedCornerShape(14.dp), primary = true)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                onStartGame(
                                    selectedColor, selectedSize, selectedHandicap,
                                    komiText.toFloatOrNull() ?: GameConstants.DEFAULT_KOMI,
                                    aiTime, aiCanResign
                                )
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Start", color = colors.TextOnAccent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

/** +/- stepper styled in glass */
@Composable
private fun StepperGlassRow(value: Int, onDec: () -> Unit, onInc: () -> Unit) {
    val colors = LocalThemeColors.current
    Row(
        modifier = Modifier
            .glassSurface(
                shape = RoundedCornerShape(16.dp),
                intensity = GlassIntensity.CARD,
                addShadow = false
            )
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier
                .glassSurface(
                    shape = RoundedCornerShape(12.dp),
                    intensity = GlassIntensity.CARD,
                    addShadow = false
                )
                .clip(RoundedCornerShape(12.dp))
                .clickable { onDec() }
                .padding(horizontal = 14.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("\u2212", color = colors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(10.dp))
        Text(
            "$value",
            Modifier.width(48.dp),
            textAlign = TextAlign.Center,
            color = colors.TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier
                .glassSurface(
                    shape = RoundedCornerShape(12.dp),
                    intensity = GlassIntensity.CARD,
                    addShadow = false
                )
                .clip(RoundedCornerShape(12.dp))
                .clickable { onInc() }
                .padding(horizontal = 14.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("+", color = colors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ColorOption(color: StoneColor, label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalThemeColors.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .glassSurface(
                shape = RoundedCornerShape(14.dp),
                intensity = if (selected) GlassIntensity.STRONG else GlassIntensity.CARD,
                accentRim = selected,
                addShadow = false
            )
            .background(if (selected) colors.Accent.copy(alpha = 0.12f) else Color.Transparent)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .shadow(1.dp, CircleShape)
                .clip(CircleShape)
                .background(if (color == StoneColor.BLACK) colors.BlackStone else colors.WhiteStone)
                .then(
                    if (color == StoneColor.WHITE)
                        Modifier.border(0.5.dp, colors.WhiteStoneBorder, CircleShape)
                    else Modifier
                )
        )
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            color = if (selected) colors.Accent else colors.TextPrimary,
            fontSize = 12.sp, fontWeight = FontWeight.Medium
        )
    }
}
