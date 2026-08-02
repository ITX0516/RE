package com.badukai.next.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    val colors = BadukNextColors
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp), color = colors.Surface, shadowElevation = 6.dp) {
            Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("AI Strength", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.TextPrimary)
                Spacer(Modifier.height(4.dp))
                Text("Choose your opponent", color = colors.TextSecondary, fontSize = 12.sp)
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
    val colors = BadukNextColors
    Box(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) colors.AccentLight else colors.SurfaceVariant)
            .border(1.dp, if (selected) colors.Accent else colors.Divider, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(18.dp).clip(CircleShape).border(if (selected) 5.dp else 1.5.dp, if (selected) colors.Accent else colors.TextSecondary, CircleShape))
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(model.displayName, color = colors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(model.description, color = colors.TextSecondary, fontSize = 11.sp)
            }
        }
    }
}