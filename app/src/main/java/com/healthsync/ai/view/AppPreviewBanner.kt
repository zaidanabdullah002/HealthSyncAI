package com.healthsync.ai.view

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AppPreviewBanner() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF0F0C29), Color(0xFF302B63), Color(0xFF24243E))
                )
            )
            .padding(20.dp)
    ) {
        // Background circles for depth
        Box(
            modifier = Modifier
                .size(120.dp)
                .offset(x = 220.dp, y = (-20).dp)
                .background(Color(0xFF4F46E5).copy(alpha = 0.3f), CircleShape)
        )
        Box(
            modifier = Modifier
                .size(80.dp)
                .offset(x = 260.dp, y = 60.dp)
                .background(Color(0xFF7C3AED).copy(alpha = 0.2f), CircleShape)
        )

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Heartbeat icon using Canvas
                Canvas(modifier = Modifier.size(20.dp)) {
                    val path = Path().apply {
                        moveTo(0f, size.height * 0.5f)
                        lineTo(size.width * 0.2f, size.height * 0.5f)
                        lineTo(size.width * 0.35f, size.height * 0.1f)
                        lineTo(size.width * 0.5f, size.height * 0.9f)
                        lineTo(size.width * 0.65f, size.height * 0.3f)
                        lineTo(size.width * 0.8f, size.height * 0.5f)
                        lineTo(size.width, size.height * 0.5f)
                    }
                    drawPath(path, color = Color(0xFFA78BFA),
                        style = Stroke(width = 2.dp.toPx(),
                            cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
                Text(
                    "HealthSync AI",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                )
            }

            Text(
                "Offline-first · Multi-device · CRDT sync",
                color = Color(0xFF818CF8),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )

            Spacer(Modifier.height(8.dp))

            // Three stat pills
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatPill(label = "1B+", sublabel = "scale")
                StatPill(label = "CRDT", sublabel = "sync")
                StatPill(label = "Hilt", sublabel = "DI")
                StatPill(label = "Flow", sublabel = "reactive")
            }
        }
    }
}

@Composable
fun StatPill(label: String, sublabel: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, color = Color.White,
                fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(sublabel, color = Color(0xFF6B7280),
                fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        }
    }
}
