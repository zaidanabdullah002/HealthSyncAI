package com.healthsync.ai.view

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.healthsync.ai.model.HealthSummary
import com.healthsync.ai.viewmodel.DashBoardViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayActivityCount(
    context: Context,
    viewModel: DashBoardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val deviceMetrics by viewModel.deviceMetrics.collectAsStateWithLifecycle()
    val todayData by viewModel.todayData.collectAsStateWithLifecycle()
    val lastSynced by viewModel.lastSynced.collectAsStateWithLifecycle()

    var isRefreshing by remember { mutableStateOf(false) }
    var selectedDevice by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { viewModel.getTodayData() }

    val displaySteps = if (todayData.steps > 0) todayData.steps else uiState.steps
    val displayActiveTime = if (todayData.activeTime > 0) todayData.activeTime else uiState.activeTime
    val displayCalories = if (todayData.calories > 0) todayData.calories else uiState.calories

    val deviceColors = listOf(
        Color(0xFF60A5FA), Color(0xFFA78BFA),
        Color(0xFF34D399), Color(0xFFFBBF24),
        Color(0xFFF472B6), Color(0xFF38BDF8)
    )

    selectedDevice?.let { deviceId ->
        val currentMetrics = deviceMetrics[deviceId] ?: HealthSummary(0.0, 0.0, 0.0)
        DeviceDialog(
            deviceId = deviceId,
            onDismiss = { selectedDevice = null },
            onSave = { steps, activeTime, calories ->
                if (steps > 0) viewModel.saveEvent(
                    deviceId, "STEPS", currentMetrics.steps + steps
                )
                if (activeTime > 0) viewModel.saveEvent(
                    deviceId, "ACTIVE_TIME", currentMetrics.activeTime + activeTime
                )
                if (calories > 0) viewModel.saveEvent(
                    deviceId, "CALORIES", currentMetrics.calories + calories
                )
                selectedDevice = null
            }
        )
    }

    if (uiState.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color(0xFF818CF8))
        }
        return
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            scope.launch {
                viewModel.triggerSync()
                viewModel.getTodayData()
                Toast.makeText(context, "Synced ✓", Toast.LENGTH_SHORT).show()
            }
        }
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF080810)),
            contentPadding = PaddingValues(
                start = 20.dp, end = 20.dp,
                top = 48.dp, bottom = 40.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // ── Header ──────────────────────────────────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column {
                        Text(
                            "HealthSync",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFFE0E0FF)
                        )
                        Text(
                            "MAX-REGISTER CRDT · OFFLINE-FIRST",
                            fontSize = 9.sp,
                            color = Color(0xFF374151),
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.5.sp
                        )
                        lastSynced?.let {
                            val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                                .format(Date(it))
                            Text(
                                "last synced $time",
                                fontSize = 9.sp,
                                color = Color(0xFF065F46),
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                    TextButton(
                        onClick = { viewModel.clearData() },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = Color(0xFF4C1D1D)
                        )
                    ) {
                        Text(
                            "clear",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // ── Steps hero ──────────────────────────────────
            item {
                StepsHeroCard(steps = displaySteps.toInt())
            }

            // ── Active Time + Calories ───────────────────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricCard(
                        modifier = Modifier.weight(1f),
                        label = "Active",
                        value = "${displayActiveTime.toInt()}",
                        unit = "minutes",
                        color = Color(0xFF34D399),
                        bgColor = Color(0xFF022C22)
                    )
                    MetricCard(
                        modifier = Modifier.weight(1f),
                        label = "Calories",
                        value = "${displayCalories.toInt()}",
                        unit = "kcal",
                        color = Color(0xFFFB923C),
                        bgColor = Color(0xFF1C0A00)
                    )
                }
            }

            // ── Devices header ───────────────────────────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "DEVICES",
                        fontSize = 10.sp,
                        letterSpacing = 2.sp,
                        color = Color(0xFF374151),
                        fontFamily = FontFamily.Monospace
                    )
                    OutlinedButton(
                        onClick = {
                            val newId = "device-${(deviceMetrics.size + 1)
                                .toString().padStart(3, '0')}"
                            viewModel.saveEvent(newId, "STEPS", 0.0)
                        },
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFF1F2937)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFF4B5563)
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(
                            "+ add device",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // ── Device cards ─────────────────────────────────
            itemsIndexed(deviceMetrics.entries.toList()) { index, (deviceId, metrics) ->
                val color = deviceColors[index % deviceColors.size]
                DeviceCard(
                    deviceName = deviceId,
                    metrics = metrics,
                    color = color,
                    onTap = { selectedDevice = deviceId },
                    onRemove = { viewModel.removeDevice(deviceId) }
                )
            }

            // ── CRDT badge ───────────────────────────────────
            item {
                CRDTBadge(
                    deviceMetrics = deviceMetrics,
                    result = displaySteps.toInt()
                )
            }
        }
    }
}

// ── Steps Hero Card ──────────────────────────────────────────

@Composable
fun StepsHeroCard(steps: Int) {
    val progress = (steps / 10000f).coerceIn(0f, 1f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF1E1B4B),
                        Color(0xFF312E81),
                        Color(0xFF4C1D95)
                    )
                )
            )
            .padding(20.dp)
    ) {
        Column {
            Text(
                "TOTAL STEPS TODAY",
                fontSize = 9.sp,
                color = Color.White.copy(alpha = 0.4f),
                letterSpacing = 2.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                steps.toLocaleString(),
                fontSize = 52.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                letterSpacing = (-2).sp,
                lineHeight = 56.sp
            )
            Text(
                "● backend verified",
                fontSize = 10.sp,
                color = Color.White.copy(alpha = 0.25f),
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(99.dp)),
                color = Color.White.copy(alpha = 0.8f),
                trackColor = Color.White.copy(alpha = 0.1f)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "${steps.toLocaleString()} / 10,000 goal",
                fontSize = 10.sp,
                color = Color.White.copy(alpha = 0.3f),
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

// ── Metric Card ──────────────────────────────────────────────

@Composable
fun MetricCard(
    modifier: Modifier,
    label: String,
    value: String,
    unit: String,
    color: Color,
    bgColor: Color
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .padding(14.dp)
    ) {
        Column {
            Text(
                label.uppercase(),
                fontSize = 9.sp,
                color = color.copy(alpha = 0.6f),
                letterSpacing = 1.5.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                value,
                fontSize = 30.sp,
                fontWeight = FontWeight.ExtraBold,
                color = color
            )
            Text(
                unit,
                fontSize = 10.sp,
                color = color.copy(alpha = 0.4f),
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

// ── Device Card ──────────────────────────────────────────────

@Composable
fun DeviceCard(
    deviceName: String,
    metrics: HealthSummary,
    color: Color,
    onTap: () -> Unit,
    onRemove: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF0D0D1A))
            .clickable { onTap() }
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {

            // Device name + remove button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(color, CircleShape)
                    )
                    Text(
                        deviceName,
                        fontSize = 12.sp,
                        color = Color(0xFF6B7280),
                        fontFamily = FontFamily.Monospace
                    )
                }
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1F0F0F))
                        .clickable { onRemove() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("×", fontSize = 14.sp, color = Color(0xFFF87171))
                }
            }

            // Three metric pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                DeviceMetricPill(
                    modifier = Modifier.weight(1f),
                    label = "STEPS",
                    value = metrics.steps.toInt().toLocaleString(),
                    color = color
                )
                DeviceMetricPill(
                    modifier = Modifier.weight(1f),
                    label = "ACTIVE",
                    value = "${metrics.activeTime.toInt()}m",
                    color = Color(0xFF34D399)
                )
                DeviceMetricPill(
                    modifier = Modifier.weight(1f),
                    label = "KCAL",
                    value = "${metrics.calories.toInt()}",
                    color = Color(0xFFFB923C)
                )
            }
        }
    }
}

// ── Device Metric Pill ───────────────────────────────────────

@Composable
fun DeviceMetricPill(
    modifier: Modifier,
    label: String,
    value: String,
    color: Color
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF111120))
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            label,
            fontSize = 8.sp,
            color = color.copy(alpha = 0.5f),
            letterSpacing = 1.sp,
            fontFamily = FontFamily.Monospace
        )
        Text(
            value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

// ── Device Dialog ────────────────────────────────────────────

@Composable
fun DeviceDialog(
    deviceId: String,
    onDismiss: () -> Unit,
    onSave: (steps: Double, activeTime: Double, calories: Double) -> Unit
) {
    var selectedSteps by remember { mutableStateOf(0.0) }
    var selectedTime by remember { mutableStateOf(0.0) }
    var selectedCalories by remember { mutableStateOf(0.0) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                    .background(Color(0xFF0D0D1A))
                    .padding(24.dp)
            ) {
                // Handle bar
                Box(
                    modifier = Modifier
                        .width(32.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(Color(0xFF1F2937))
                        .align(Alignment.CenterHorizontally)
                )

                Spacer(Modifier.height(20.dp))

                Text(
                    deviceId,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFFE2E8F0)
                )
                Text(
                    "Log activity for this device",
                    fontSize = 11.sp,
                    color = Color(0xFF374151),
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(bottom = 20.dp)
                )

                QuickSelectSection(
                    label = "STEPS",
                    options = listOf(
                        "+1k" to 1000.0,
                        "+3k" to 3000.0,
                        "+5k" to 5000.0,
                        "+10k" to 10000.0
                    ),
                    selected = selectedSteps,
                    onSelect = { selectedSteps = it }
                )

                Spacer(Modifier.height(14.dp))

                QuickSelectSection(
                    label = "ACTIVE TIME",
                    options = listOf(
                        "15m" to 15.0,
                        "30m" to 30.0,
                        "1hr" to 60.0,
                        "90m" to 90.0
                    ),
                    selected = selectedTime,
                    onSelect = { selectedTime = it }
                )

                Spacer(Modifier.height(14.dp))

                QuickSelectSection(
                    label = "CALORIES",
                    options = listOf(
                        "100" to 100.0,
                        "250" to 250.0,
                        "500" to 500.0,
                        "800" to 800.0
                    ),
                    selected = selectedCalories,
                    onSelect = { selectedCalories = it }
                )

                Spacer(Modifier.height(20.dp))

                Button(
                    onClick = {
                        onSave(selectedSteps, selectedTime, selectedCalories)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4F46E5)
                    )
                ) {
                    Text(
                        "Save to Device",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        }
    }
}

// ── Quick Select Section ─────────────────────────────────────

@Composable
fun QuickSelectSection(
    label: String,
    options: List<Pair<String, Double>>,
    selected: Double,
    onSelect: (Double) -> Unit
) {
    Column {
        Text(
            label,
            fontSize = 9.sp,
            color = Color(0xFF374151),
            letterSpacing = 1.5.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { (optionLabel, value) ->
                val isSelected = selected == value
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isSelected) Color(0xFF1E1B4B)
                            else Color(0xFF111120)
                        )
                        .clickable { onSelect(value) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        optionLabel,
                        fontSize = 12.sp,
                        color = if (isSelected) Color(0xFFA78BFA)
                        else Color(0xFF4B5563),
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

// ── CRDT Badge ───────────────────────────────────────────────

@Composable
fun CRDTBadge(deviceMetrics: Map<String, HealthSummary>, result: Int) {
    val expression = if (deviceMetrics.isEmpty()) "0"
    else deviceMetrics.entries.joinToString(", ") { (device, metrics) ->
        "$device: ${metrics.steps.toInt()}"  // ← show full name
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF0A0A14))
            .padding(12.dp)
    ) {
        Text(
            "max($expression) = $result",
            color = Color(0xFF818CF8),
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp
        )
    }
}

// ── Extension ────────────────────────────────────────────────

fun Int.toLocaleString(): String = String.format("%,d", this)