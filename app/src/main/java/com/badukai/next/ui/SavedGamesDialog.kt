package com.badukai.next.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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

@Composable
fun SavedGamesDialog(
    games: List<Pair<String, String>>,
    onDismiss: () -> Unit,
    onLoad: (String) -> Unit
) {
    val colors = BadukNextColors
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp), color = colors.Surface, shadowElevation = 6.dp) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Saved Games", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.TextPrimary)
                Spacer(Modifier.height(12.dp))
                if (games.isEmpty()) {
                    Text("No saved games yet", color = colors.TextSecondary, fontSize = 13.sp)
                } else {
                    Column(modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
                        games.forEach { (name, path) ->
                            Box(
                                modifier = Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(colors.SurfaceVariant)
                                    .clickable { onLoad(path) }
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            ) { Text(name, color = colors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium) }
                            Spacer(Modifier.height(6.dp))
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Box(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(colors.Accent)
                        .clickable(onClick = onDismiss).padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center
                ) { Text("Close", color = colors.TextOnAccent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}