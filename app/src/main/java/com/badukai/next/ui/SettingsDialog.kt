package com.badukai.next.ui

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.badukai.next.engine.KataGoEngine
import com.badukai.next.engine.ModelSource
import com.badukai.next.game.PlacementMode
import com.badukai.next.game.StoneAnimation

@Composable
fun SettingsDialog(
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
    onDismiss: () -> Unit,
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
    val colors = BadukNextColors
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp), color = colors.Surface, shadowElevation = 6.dp) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Settings", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.TextPrimary)
                Spacer(Modifier.height(14.dp))

                CollapsibleSection(title = "Sound", defaultExpanded = false) {
                    ToggleRow("Sound On", "Stone placement and capture sounds", soundEnabled, onToggleSound)
                    Spacer(Modifier.height(8.dp))
                    Text("Place sound", fontSize = 12.sp, color = colors.TextSecondary)
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        for (i in 0 until 5) {
                            val label = "S${i + 1}"
                            Box(
                                modifier = Modifier.clip(RoundedCornerShape(8.dp))
                                    .background(if (i == placeSoundIndex) colors.AccentLight else colors.SurfaceVariant)
                                    .border(1.dp, if (i == placeSoundIndex) colors.Accent else colors.Divider, RoundedCornerShape(8.dp))
                                    .clickable { onSetPlaceSound(i) }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) { Text(label, color = if (i == placeSoundIndex) colors.Accent else colors.TextPrimary, fontSize = 12.sp, fontWeight = if (i == placeSoundIndex) FontWeight.SemiBold else FontWeight.Normal) }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                CollapsibleSection(title = "Game", defaultExpanded = false) {
                    ToggleRow("Coordinates", "Show board letters and numbers", showCoordinates, onToggleCoordinates)
                    Spacer(Modifier.height(10.dp))
                    Text("Placement mode", fontSize = 12.sp, color = colors.TextSecondary)
                    Spacer(Modifier.height(4.dp))
                    PlacementMode.entries.forEach { mode ->
                        SettingsRadioOption(label = mode.displayName, selected = mode == currentPlacementMode, onClick = { onSetPlacementMode(mode) })
                        Spacer(Modifier.height(4.dp))
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("Stone animation", fontSize = 12.sp, color = colors.TextSecondary)
                    Spacer(Modifier.height(4.dp))
                    StoneAnimation.entries.forEach { anim ->
                        SettingsRadioOption(label = anim.displayName, selected = anim == currentAnimation, onClick = { onSetAnimation(anim) })
                        Spacer(Modifier.height(4.dp))
                    }
                }

                Spacer(Modifier.height(10.dp))
                CollapsibleSection(title = "AI", defaultExpanded = true) {
                    Text("Move time (seconds)", fontSize = 12.sp, color = colors.TextSecondary)
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        Box(Modifier.clip(RoundedCornerShape(6.dp)).background(colors.SurfaceVariant).clickable { onSetAiMoveTime(aiMoveTimeSeconds - 5) }.padding(horizontal = 12.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
                            Text("\u2212", color = colors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                        Text("$aiMoveTimeSeconds", Modifier.width(50.dp), textAlign = TextAlign.Center, color = colors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Box(Modifier.clip(RoundedCornerShape(6.dp)).background(colors.SurfaceVariant).clickable { onSetAiMoveTime(aiMoveTimeSeconds + 5) }.padding(horizontal = 12.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
                            Text("+", color = colors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    ToggleRow("AI can resign", "Allow AI to resign when losing badly", aiCanResign, { onSetAiCanResign(!aiCanResign) })
                    Spacer(Modifier.height(14.dp))
                    Divider(color = colors.Divider)
                    Spacer(Modifier.height(10.dp))
                    Text("AI weights source", fontSize = 12.sp, color = colors.TextSecondary, fontWeight = FontWeight.SemiBold)
                    Text("离线内置 6b 无需首次下载；可切换到在线下载或导入自定义 .txt.gz/.bin.gz", fontSize = 11.sp, color = colors.TextSecondary)
                    Spacer(Modifier.height(6.dp))
                    ModelSource.entries.forEach { src ->
                        val selected = aiModelSource == src
                        val enabled = src != ModelSource.CUSTOM || customModelDisplayName.isNotBlank()
                        val subtitle = when (src) {
                            ModelSource.BUNDLED_ASSET -> "APK 内置 6b，离线首启可用"
                            ModelSource.DOWNLOADED -> "从 katagotraining.org 下载 6b（需要联网）"
                            ModelSource.CUSTOM -> if (customModelDisplayName.isNotBlank()) "当前：$customModelDisplayName" else "未导入（请点击下方按钮选择文件）"
                            // else — exhaustive fallback; normally unreachable.
                            else -> ""
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (selected) colors.AccentLight else colors.SurfaceVariant)
                                    .border(1.dp, if (selected) colors.Accent else colors.Divider, RoundedCornerShape(8.dp))
                                    .clickable(enabled = enabled) { onSetAiModelSource(src) }
                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clip(CircleShape)
                                            .border(
                                                if (selected) 4.5.dp else 1.3.dp,
                                                if (selected) colors.Accent else colors.TextSecondary,
                                                CircleShape
                                            )
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column {
                                        Text(src.displayName, color = if (enabled) colors.TextPrimary else colors.TextSecondary, fontSize = 13.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                                        Text(subtitle, color = colors.TextSecondary, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(5.dp))
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onPickCustomModel,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.Accent, contentColor = colors.TextOnAccent)
                        ) {
                            Text("选择自定义文件…", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                        OutlinedButton(
                            onClick = onResetAiModelToBundled,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, colors.Accent),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.Accent)
                        ) {
                            Text("恢复默认内置", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Text("支持 .bin.gz / .txt.gz (KataGo 原生)，必须 ≥ 1MB 且为 gzip 压缩（默认 KataGo 模型下载页直接可用）", fontSize = 10.5.sp, color = colors.TextSecondary)
                }

                Spacer(Modifier.height(10.dp))
                CollapsibleSection(title = "Theme", defaultExpanded = false) {
                    GameTheme.entries.forEach { theme ->
                        SettingsRadioOption(label = theme.displayName, selected = theme == currentTheme, onClick = { onSetTheme(theme) })
                        Spacer(Modifier.height(4.dp))
                    }
                }

                Spacer(Modifier.height(14.dp))
                Divider(color = colors.Divider)
                Spacer(Modifier.height(10.dp))
                Text("BadukNext v1.0", fontSize = 11.sp, color = colors.TextSecondary)
            }
        }
    }
}

@Composable
private fun CollapsibleSection(title: String, defaultExpanded: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val colors = BadukNextColors
    var expanded by remember { mutableStateOf(defaultExpanded) }
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { expanded = !expanded }.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.TextPrimary)
            Text(if (expanded) "\u25B2" else "\u25BC", fontSize = 12.sp, color = colors.TextSecondary)
        }
        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(start = 4.dp), content = content)
        }
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onToggle: () -> Unit) {
    val colors = BadukNextColors
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = onToggle).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = colors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = colors.TextSecondary, fontSize = 11.sp)
        }
        Switch(
            checked = checked, onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(checkedThumbColor = colors.TextOnAccent, checkedTrackColor = colors.Accent, uncheckedThumbColor = colors.Surface, uncheckedTrackColor = colors.Divider)
        )
    }
}

@Composable
private fun SettingsRadioOption(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = BadukNextColors
    Box(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) colors.AccentLight else colors.SurfaceVariant)
            .border(1.dp, if (selected) colors.Accent else colors.Divider, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(16.dp).clip(CircleShape).border(if (selected) 5.dp else 1.5.dp, if (selected) colors.Accent else colors.TextSecondary, CircleShape))
            Spacer(Modifier.width(10.dp))
            Text(label, color = colors.TextPrimary, fontSize = 13.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
        }
    }
}
