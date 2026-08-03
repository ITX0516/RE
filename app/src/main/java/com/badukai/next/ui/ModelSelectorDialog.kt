package com.badukai.next.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.badukai.next.engine.KataGoEngine

@Composable
fun ModelSelectorDialog(
    currentModel: KataGoEngine.Model,
    onDismiss: () -> Unit,
    onSelectModel: (KataGoEngine.Model) -> Unit
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
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
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
                    Column {
                        Text("AI Strength", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.TextPrimary)
                        Text("Choose your opponent", color = colors.TextSecondary, fontSize = 12.sp)
                    }
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
                KataGoEngine.Model.entries.forEach { model ->
                    ModelOption(model = model, selected = model == currentModel, onClick = { onSelectModel(model) })
                    if (model != KataGoEngine.Model.entries.last()) Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun ModelOption(model: KataGoEngine.Model, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalThemeColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glassSurface(
                shape = RoundedCornerShape(16.dp),
                intensity = if (selected) GlassIntensity.STRONG else GlassIntensity.CARD,
                accentRim = selected,
                addShadow = false
            )
            .background(if (selected) colors.Accent.copy(alpha = 0.12f) else Color.Transparent)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .border(
                        if (selected) 5.dp else 1.5.dp,
                        if (selected) colors.Accent else colors.TextSecondary,
                        CircleShape
                    )
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(model.displayName, color = colors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(model.description, color = colors.TextSecondary, fontSize = 11.sp)
            }
        }
    }
}
