package com.healthsync.ai.view

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.healthsync.ai.viewmodel.DashBoardViewModel
import kotlinx.coroutines.delay

private data class ChatBubble(
    val id: String,
    val role: String,
    val text: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onBack: () -> Unit,
    viewModel: DashBoardViewModel = hiltViewModel()
) {
    val chatResponse by viewModel.chatResponse.collectAsStateWithLifecycle()
    val isChatLoading by viewModel.isChatLoading.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val messages = remember { mutableStateListOf<ChatBubble>() }
    val visibleTexts = remember { mutableStateMapOf<String, String>() }
    var input by remember { mutableStateOf("Am I on track today?") }
    var messageCounter by remember { mutableStateOf(0) }
    var thinkingDotIndex by remember { mutableStateOf(0) }
    val quickPrompts = listOf(
        "Set a 12k goal by Friday",
        "Am I on track this week?",
        "Any anomalies today?",
        "Make me a daily plan"
    )

    LaunchedEffect(Unit) {
        if (messages.isEmpty()) {
            messageCounter += 1
            messages.add(ChatBubble("msg-$messageCounter", "assistant", "Ask me a health question."))
            visibleTexts["msg-$messageCounter"] = "Ask me a health question."
        }
    }

    LaunchedEffect(chatResponse) {
        chatResponse?.let { response ->
            if (messages.lastOrNull()?.role != "assistant" || messages.lastOrNull()?.text != response.assistantResponse) {
                messageCounter += 1
                val id = "msg-$messageCounter"
                messages.add(ChatBubble(id, "assistant", response.assistantResponse))
                visibleTexts[id] = ""
            }
        }
    }

    LaunchedEffect(messages.size) {
        val latest = messages.lastOrNull()
        if (latest?.role == "assistant") {
            val fullText = latest.text
            val builder = StringBuilder()
            fullText.forEachIndexed { index, ch ->
                builder.append(ch)
                visibleTexts[latest.id] = builder.toString()
                delay(if (index < 6) 12 else 8)
            }
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    LaunchedEffect(isChatLoading) {
        if (isChatLoading) {
            while (true) {
                thinkingDotIndex = (thinkingDotIndex + 1) % 3
                delay(300)
            }
        } else {
            thinkingDotIndex = 0
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Text(
                            "Ask AI",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 22.sp,
                            letterSpacing = (-0.5).sp
                        )
                        Text(
                            "Health questions, backed by your data",
                            fontSize = 11.sp,
                            color = Color(0xFF6B7280),
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            quickPrompts.take(2).forEach { prompt ->
                                QuickPromptChip(
                                    text = prompt,
                                    onClick = { input = prompt }
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF080810),
                    titleContentColor = Color(0xFFE2E8F0),
                    navigationIconContentColor = Color(0xFFE2E8F0)
                )
            )
        },
        bottomBar = {
            Surface(
                color = Color(0xFF080810),
                tonalElevation = 0.dp
            ) {
                Column(
                    modifier = Modifier
                        .imePadding()
                        .navigationBarsPadding()
                        .padding(12.dp)
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        label = { Text("Message") },
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    val trimmed = input.trim()
                                    if (trimmed.isNotEmpty()) {
                                        messageCounter += 1
                                        messages.add(ChatBubble("msg-$messageCounter", "user", trimmed))
                                        viewModel.askAgent(trimmed)
                                        input = ""
                                    }
                                },
                                enabled = !isChatLoading
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                            }
                        }
                    )
                }
            }
        },
        containerColor = Color(0xFF080810)
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .padding(bottom = 96.dp),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                ChatHero()
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    quickPrompts.drop(2).forEach { prompt ->
                        QuickPromptChip(
                            text = prompt,
                            onClick = { input = prompt }
                        )
                    }
                }
            }

            items(messages) { bubble ->
                ChatBubbleRow(
                    bubble = bubble,
                    visibleText = if (bubble.role == "assistant") visibleTexts[bubble.id] ?: "" else bubble.text
                )
            }

            if (isChatLoading) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(18.dp))
                                .background(Color(0xFF111120))
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            AvatarDot(
                                label = "AI",
                                bgColor = Color(0xFF1E1B4B),
                                fgColor = Color(0xFF818CF8)
                            )
                            TypingDotsRow(
                                activeIndex = thinkingDotIndex
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatBubbleRow(bubble: ChatBubble, visibleText: String) {
    val isUser = bubble.role == "user"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!isUser) {
            AvatarDot(
                label = "AI",
                bgColor = Color(0xFF1E1B4B),
                fgColor = Color(0xFF818CF8)
            )
            Spacer(modifier = Modifier.size(8.dp))
        }
        Column(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 18.dp,
                        topEnd = 18.dp,
                        bottomStart = if (isUser) 18.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 18.dp
                    )
                )
                .background(if (isUser) Color(0xFF4F46E5) else Color(0xFF111120))
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                text = if (isUser) "You" else "Health AI",
                fontSize = 10.sp,
                color = if (isUser) Color.White.copy(alpha = 0.7f) else Color(0xFF818CF8),
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = visibleText,
                color = Color(0xFFE2E8F0),
                fontSize = 14.sp
            )
        }
        if (isUser) {
            Spacer(modifier = Modifier.size(8.dp))
            AvatarDot(
                label = "You",
                bgColor = Color(0xFF4F46E5),
                fgColor = Color.White
            )
        }
    }
}

@Composable
private fun TypingDotsRow(activeIndex: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(3) { index ->
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF818CF8).copy(alpha = if (index == activeIndex) 1f else 0.25f))
                    .alpha(if (index == activeIndex) 1f else 0.5f)
            )
        }
    }
}

@Composable
private fun ChatHero() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF141427), Color(0xFF0F1020))))
            .padding(16.dp)
    ) {
        Text(
            "Goal Coach",
            color = Color(0xFF818CF8),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.2.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "Ask about goals, weekly progress, daily plans, or anomalies.",
            color = Color(0xFFE2E8F0),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun QuickPromptChip(
    text: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        color = Color(0xFF111120),
        tonalElevation = 0.dp,
        border = BorderStroke(1.dp, Color(0xFF1F2937))
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            color = Color(0xFFCBD5E1),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun AvatarDot(
    label: String,
    bgColor: Color,
    fgColor: Color
) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label.first().uppercase(),
            color = fgColor,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp
        )
    }
}
