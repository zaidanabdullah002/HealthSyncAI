// TodayActivityCount.kt
package com.healthsync.ai.view

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.healthsync.ai.viewmodel.DashBoardViewModel

@Composable
fun TodayActivityCount(viewModel: DashBoardViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val deviceSteps by viewModel.deviceSteps.collectAsStateWithLifecycle()

    if (uiState.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val deviceColors = listOf(
        Color(0xFF60A5FA),
        Color(0xFFA78BFA),
        Color(0xFF34D399),
        Color(0xFFFBBF24)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0F))
            .padding(horizontal = 24.dp)
            .padding(top = 24.dp)
            .padding(bottom = 16.dp) // padding for home button/nav bar
            .systemBarsPadding() // ensuring it doesn't go below system buttons
    ) {
        // STATIC HEADER SEGMENT
        AppPreviewBanner()
        
        Spacer(Modifier.height(16.dp))

        Column {
            Text(
                "HealthSync AI",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFE0E0FF)
            )
            Text(
                "Today · Max-Register CRDT",
                fontSize = 12.sp,
                color = Color(0xFF4B5563),
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(Modifier.height(16.dp))

        StepsCard(steps = uiState.steps.toInt())

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricCard(
                modifier = Modifier.weight(1f),
                label = "Active",
                value = "${uiState.activeTime.toInt()}",
                unit = "minutes",
                color = Color(0xFF0D9488)
            )
            MetricCard(
                modifier = Modifier.weight(1f),
                label = "Calories",
                value = "${uiState.calories.toInt()}",
                unit = "kcal",
                color = Color(0xFFEA580C)
            )
        }

        Spacer(Modifier.height(20.dp))

        Text(
            "DEVICES",
            fontSize = 11.sp,
            letterSpacing = 2.sp,
            color = Color(0xFF4B5563),
            fontFamily = FontFamily.Monospace
        )

        Spacer(Modifier.height(12.dp))

        // SCROLLABLE DEVICE LIST SEGMENT
        // We use weight(1f) to make the list take up the remaining space and be scrollable
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            itemsIndexed(deviceSteps.toList()) { index, (deviceId, steps) ->
                val color = deviceColors[index % deviceColors.size]
                DeviceCard(
                    deviceName = deviceId,
                    steps = steps.toInt(),
                    color = color,
                    onWalk = {
                        viewModel.saveEvent(deviceId, "STEPS", steps + 1000.0)
                    }
                )
            }

            item {
                OutlinedButton(
                    onClick = {
                        val newDeviceId = "device-${(deviceSteps.size + 1).toString().padStart(3, '0')}"
                        viewModel.saveEvent(newDeviceId, "STEPS", 1000.0)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFF1F2937)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF4B5563))
                ) {
                    Text("+ Connect New Device", fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                }
            }
        }

        // STATIC FOOTER SEGMENT
        Spacer(Modifier.height(12.dp))
        CRDTBadge(
            deviceSteps = deviceSteps,
            result = uiState.steps.toInt()
        )
    }
}

@Composable
fun StepsCard(steps: Int) {
    val progress = (steps / 10000f).coerceIn(0f, 1f)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF4F46E5)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                "TOTAL STEPS",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 11.sp,
                letterSpacing = 1.5.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                steps.toLocaleString(),
                color = Color.White,
                fontSize = 52.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-2).sp
            )
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(99.dp)),
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.2f)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "${steps.toLocaleString()} / 10,000 goal",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
fun MetricCard(
    modifier: Modifier,
    label: String,
    value: String,
    unit: String,
    color: Color
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = color),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(label, color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp,
                letterSpacing = 1.5.sp, fontFamily = FontFamily.Monospace)
            Text(value, color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Text(unit, color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp,
                fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
fun DeviceCard(
    deviceName: String,
    steps: Int,
    color: Color,
    onWalk: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF111118)),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0xFF1F2937))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(color, CircleShape)
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        deviceName,
                        color = Color(0xFF9CA3AF),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        "${steps.toLocaleString()} steps",
                        color = color,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Button(
                onClick = onWalk,
                colors = ButtonDefaults.buttonColors(
                    containerColor = color.copy(alpha = 0.15f),
                    contentColor = color
                ),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text("+ 1000", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun CRDTBadge(deviceSteps: Map<String, Double>, result: Int) {
    val expression = if (deviceSteps.isEmpty()) "0" else deviceSteps.entries.joinToString(", ") { (device, steps) ->
        "${device.substringBefore("-")}: ${steps.toInt()}"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F0F1A)),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, Color(0xFF312E81))
    ) {
        Text(
            "max($expression) = $result",
            modifier = Modifier.padding(12.dp),
            color = Color(0xFF818CF8),
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp
        )
    }
}

// extension to format numbers with commas
fun Int.toLocaleString(): String {
    return String.format("%,d", this)
}
