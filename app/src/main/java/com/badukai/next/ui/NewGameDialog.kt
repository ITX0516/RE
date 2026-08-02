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
    val colors = BadukNextColors
    var selectedColor by remember { mutableStateOf(StoneColor.BLACK) }
    var selectedSize by remember { mutableIntStateOf(19) }
    var selectedHandicap by remember { mutableIntStateOf(0) }
    var komiText by remember { mutableStateOf("${GameConstants.DEFAULT_KOMI}") }
    var aiTime by remember { mutableIntStateOf(initialAiTime) }
    var aiCanResign by remember { mutableStateOf(initialAiCanResign) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp), color = colors.Surface, shadowElevation = 6.dp) {
            Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("New Game", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.TextPrimary)
                Spacer(Modifier.height(14.dp))

                Text("AI plays", color = colors.TextSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StoneColor.entries.forEach { c ->
                        ColorOption(c, c.displayName, selected = selectedColor != c) { selectedColor = c.opposite() }
                    }
                }

                Spacer(Modifier.height(14.dp))
                Text("Board: ${selectedSize}\u00D7${selectedSize}", color = colors.TextSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(colors.SurfaceVariant).clickable { if (selectedSize > 7) selectedSize-- }.padding(horizontal = 10.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
                        Text("\u2212", color = colors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                    Text("$selectedSize", Modifier.width(40.dp), textAlign = TextAlign.Center, color = colors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(colors.SurfaceVariant).clickable { if (selectedSize < 19) selectedSize++ }.padding(horizontal = 10.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
                        Text("+", color = colors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(Modifier.height(14.dp))
                Text("Handicap: $selectedHandicap", color = colors.TextSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(colors.SurfaceVariant).clickable { if (selectedHandicap > 0) selectedHandicap-- }.padding(horizontal = 10.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
                        Text("\u2212", color = colors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                    Text("$selectedHandicap", Modifier.width(40.dp), textAlign = TextAlign.Center, color = colors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(colors.SurfaceVariant).clickable { if (selectedHandicap < 9) selectedHandicap++ }.padding(horizontal = 10.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
                        Text("+", color = colors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(Modifier.height(14.dp))
                Text("Komi", color = colors.TextSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                Box(Modifier.width(80.dp).clip(RoundedCornerShape(6.dp)).background(colors.SurfaceVariant).border(1.dp, colors.Divider, RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
                    Text(komiText, color = colors.TextPrimary, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("0.5", "6.5", "7.5").forEach { preset ->
                        Box(Modifier.clip(RoundedCornerShape(4.dp)).background(if (komiText == preset) colors.AccentLight else colors.SurfaceVariant).clickable { komiText = preset }.padding(horizontal = 10.dp, vertical = 4.dp), contentAlignment = Alignment.Center) {
                            Text(preset, color = colors.TextPrimary, fontSize = 11.sp)
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                Text("AI move time: ${aiTime}s", color = colors.TextSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(colors.SurfaceVariant).clickable { aiTime = (aiTime - 5).coerceAtLeast(1) }.padding(horizontal = 10.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
                        Text("\u2212", color = colors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                    Text("$aiTime", Modifier.width(40.dp), textAlign = TextAlign.Center, color = colors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(colors.SurfaceVariant).clickable { aiTime = (aiTime + 5).coerceAtMost(120) }.padding(horizontal = 10.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
                        Text("+", color = colors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { aiCanResign = !aiCanResign }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("AI can resign", color = colors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text("Allow AI to resign when losing badly", color = colors.TextSecondary, fontSize = 11.sp)
                    }
                    Switch(
                        checked = aiCanResign, onCheckedChange = { aiCanResign = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = colors.TextOnAccent, checkedTrackColor = colors.Accent, uncheckedThumbColor = colors.Surface, uncheckedTrackColor = colors.Divider)
                    )
                }

                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(colors.Surface).border(1.dp, colors.Divider, RoundedCornerShape(8.dp)).clickable(onClick = onDismiss).padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                        Text("Cancel", color = colors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Box(Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(colors.Accent).clickable {
                        onStartGame(selectedColor, selectedSize, selectedHandicap, komiText.toFloatOrNull() ?: GameConstants.DEFAULT_KOMI, aiTime, aiCanResign)
                    }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                        Text("Start", color = colors.TextOnAccent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorOption(color: StoneColor, label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = BadukNextColors
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) colors.AccentLight else colors.SurfaceVariant)
            .border(1.dp, if (selected) colors.Accent else colors.Divider, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Box(
            modifier = Modifier.size(28.dp).shadow(1.dp, CircleShape).clip(CircleShape)
                .background(if (color == StoneColor.BLACK) colors.BlackStone else colors.WhiteStone)
                .then(if (color == StoneColor.WHITE) Modifier.border(0.5.dp, colors.WhiteStoneBorder, CircleShape) else Modifier)
        )
        Spacer(Modifier.height(4.dp))
        Text(label, color = colors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}