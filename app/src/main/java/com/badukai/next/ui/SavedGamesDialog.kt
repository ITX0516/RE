package com.badukai.next.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

@Composable
fun SavedGamesDialog(
    games: List<Pair<String, String>>,
    onDismiss: () -> Unit,
    onLoad: (String) -> Unit
) {
    val colors = LocalThemeColors.current
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
            Column(modifier = Modifier.padding(20.dp)) {
                // Title + close
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
                    Text(
                        "Saved Games",
                        fontSize = 18.sp, fontWeight = FontWeight.Bold,
                        color = colors.TextPrimary
                    )
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
                Spacer(Modifier.height(12.dp))
                if (games.isEmpty()) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .glassSurface(
                                shape = RoundedCornerShape(16.dp),
                                intensity = GlassIntensity.CARD,
                                addShadow = false
                            )
                            .padding(horizontal = 12.dp, vertical = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No saved games yet", color = colors.TextSecondary, fontSize = 13.sp)
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        games.forEach { (name, path) ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .glassSurface(
                                        shape = RoundedCornerShape(14.dp),
                                        intensity = GlassIntensity.CARD,
                                        accentRim = false,
                                        addShadow = false
                                    )
                                    .background(colors.Accent.copy(alpha = 0.08f))
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { onLoad(path) }
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    name,
                                    color = colors.TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                // Close button: primary glass
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassButton(shape = RoundedCornerShape(14.dp), primary = true)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(onClick = onDismiss)
                        .padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Close", color = colors.TextOnAccent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
