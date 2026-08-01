package com.badukai.next.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.badukai.next.game.GameResult
import kotlin.math.sin
import kotlin.random.Random

/**
 * End-of-game celebration overlay with falling particles.
 * WIN  → confetti (colorful)
 * LOSE → autumn leaves (orange/brown)
 * DRAW → handshake emoji falling
 */
@Composable
fun CelebrationOverlay(result: GameResult, onDismiss: () -> Unit) {
    val colors = BadukNextColors
    val titleColor = when (result) {
        GameResult.WIN -> Color(0xFF4CAF50)
        GameResult.LOSE -> Color(0xFFE53935)
        GameResult.DRAW -> Color(0xFF607D8B)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .zIndex(10f),
        contentAlignment = Alignment.Center
    ) {
        FallingParticles(result)

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                result.label,
                color = titleColor,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(24.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.Accent)
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = 40.dp, vertical = 12.dp)
            ) {
                Text("Continue", color = colors.TextOnAccent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

private data class Particle(
    var x: Float,
    var y: Float,
    var speed: Float,
    var size: Float,
    var rotation: Float,
    var rotSpeed: Float,
    var sway: Float,
    var color: Color
)

@Composable
private fun FallingParticles(result: GameResult) {
    val count = 40
    val particles = remember(result) { mutableListOf<Particle>() }
    val progress = remember { Animatable(0f) }

    LaunchedEffect(result) {
        val rng = Random(System.currentTimeMillis())
        particles.clear()
        val colorPool = when (result) {
            GameResult.WIN -> listOf(
                Color(0xFFFF5252), Color(0xFFFFEB3B), Color(0xFF4CAF50),
                Color(0xFF2196F3), Color(0xFF9C27B0), Color(0xFFFF9800)
            )
            GameResult.LOSE -> listOf(
                Color(0xFFD84315), Color(0xFFEF6C00), Color(0xFFFFB300),
                Color(0xFF8D6E63), Color(0xFFBF360C)
            )
            GameResult.DRAW -> listOf(
                Color(0xFF607D8B), Color(0xFF90A4AE), Color(0xFFB0BEC5)
            )
        }
        for (i in 0 until count) {
            particles.add(
                Particle(
                    x = rng.nextFloat() * 1000f,
                    y = rng.nextFloat() * -800f,
                    speed = 0.8f + rng.nextFloat() * 1.4f,
                    size = 4f + rng.nextFloat() * 8f,
                    rotation = rng.nextFloat() * 360f,
                    rotSpeed = (rng.nextFloat() - 0.5f) * 6f,
                    sway = rng.nextFloat() * 2f - 1f,
                    color = colorPool[rng.nextInt(colorPool.size)]
                )
            )
        }
        progress.snapTo(0f)
        progress.animateTo(1f, animationSpec = tween(6000, easing = LinearEasing))
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val t = progress.value
        val dt = 0.016f * t * 100f

        particles.forEach { p ->
            p.y += p.speed * (h / 1000f) * dt
            p.x += p.sway * sin(t * 3f + p.rotation) * 1.5f
            p.rotation += p.rotSpeed

            if (p.y < h + 40f) {
                rotate(p.rotation, pivot = Offset(p.x * w / 1000f, p.y)) {
                    drawRect(
                        color = p.color.copy(alpha = 0.85f),
                        topLeft = Offset(p.x * w / 1000f - p.size / 2, p.y - p.size / 2),
                        size = Size(p.size, p.size * 0.6f)
                    )
                }
            }
        }
    }
}
