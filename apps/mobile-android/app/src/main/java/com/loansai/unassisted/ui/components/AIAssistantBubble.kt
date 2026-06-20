package com.loansai.unassisted.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.loansai.unassisted.domain.model.AIAssistantMessage
import com.loansai.unassisted.domain.model.MessageType
import com.loansai.unassisted.viewmodel.AIAssistantViewModel
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import androidx.compose.foundation.border
import androidx.compose.ui.zIndex

/**
 * Enhanced AI Assistant bubble that persists across screens
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIAssistantBubble(
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    viewModel: AIAssistantViewModel = hiltViewModel()
) {
    // Use collectAsState() to convert StateFlow to State for Compose
    val unreadSuggestions by viewModel.unreadSuggestions.collectAsState()
    val isDialogVisible by viewModel.isDialogVisible.collectAsState()
    val pulseAnimation by animateFloatAsState(
        targetValue = if (unreadSuggestions > 0) 1.1f else 1f,
        animationSpec = tween(500),
        label = "pulseAnimation"
    )
    
    // AI Assistant floating button with pulsing animation when there are suggestions
    Box(modifier = modifier) {
        BadgedBox(
            badge = {
                if (unreadSuggestions > 0) {
                    Badge {
                        Text(text = unreadSuggestions.toString())
                    }
                }
            }
        ) {
            FloatingActionButton(
                onClick = { 
                    viewModel.showDialog()
                    onClick()
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = CircleShape,
                modifier = Modifier
                    .shadow(
                        elevation = 6.dp,
                        shape = CircleShape
                    )
                    .scale(pulseAnimation) // Apply pulsing animation
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Chat,
                    contentDescription = "AI Assistant"
                )
            }
        }
    }
    
    // AI Assistant dialog
    if (isDialogVisible) {
        AIAssistantDialog(
            onDismiss = { viewModel.hideDialog() },
            viewModel = viewModel
        )
    }
}

/**
 * Enhanced AI Assistant dialog with smoother animations and improved design
 */
@Composable
fun AIAssistantDialog(
    onDismiss: () -> Unit,
    viewModel: AIAssistantViewModel
) {
    val messages by viewModel.messages.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    var userInput by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    
    // Scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }
    
    // Mark all messages as read when dialog opens
    LaunchedEffect(Unit) {
        viewModel.markAllMessagesAsRead()
    }
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .padding(16.dp)
                // Use fillMaxWidth and fixed heightIn with a safe bottom margin
                .fillMaxWidth()
                .heightIn(max = (configuration.screenHeightDp * 0.75f).dp) // Reduced from 80% to 75%
                .padding(bottom = 48.dp) // Add extra padding at bottom to clear navigation bar
                .shadow(8.dp, RoundedCornerShape(20.dp)),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                // Dialog header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Chat,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        Text(
                            text = "AI Assistant",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                HorizontalDivider(
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                )
                
                // Message list
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 12.dp),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (messages.isEmpty() && !isLoading) {
                        item {
                            WelcomeMessage()
                        }
                    }
                    
                    items(messages) { message ->
                        MessageItem(message = message)
                    }
                    
                    if (isLoading) {
                        item {
                            TypingIndicator()
                        }
                    }
                }
                
                // Input field
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = userInput,
                        onValueChange = { userInput = it },
                        placeholder = { Text("Ask me anything...") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(24.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                            unfocusedIndicatorColor = MaterialTheme.colorScheme.outline
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                if (userInput.isNotBlank() && !isLoading) {
                                    viewModel.sendMessage(userInput)
                                    userInput = ""
                                }
                            }
                        ),
                        maxLines = 3
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    IconButton(
                        onClick = {
                            if (userInput.isNotBlank() && !isLoading) {
                                viewModel.sendMessage(userInput)
                                userInput = ""
                                
                                // Scroll to bottom
                                coroutineScope.launch {
                                    if (messages.isNotEmpty()) {
                                        listState.animateScrollToItem(messages.size)
                                    }
                                }
                            }
                        },
                        enabled = userInput.isNotBlank() && !isLoading,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(
                                if (userInput.isNotBlank() && !isLoading)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.surfaceVariant
                            )
                            .size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Send,
                            contentDescription = "Send",
                            tint = if (userInput.isNotBlank() && !isLoading)
                                MaterialTheme.colorScheme.onPrimary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}


/**
 * Message item in the chat
 */
@Composable
fun MessageItem(message: AIAssistantMessage) {
    val isUserMessage = message.messageType == MessageType.USER_MESSAGE
    
    val backgroundColor = when (message.messageType) {
        MessageType.USER_MESSAGE -> MaterialTheme.colorScheme.primary
        MessageType.AI_RESPONSE -> MaterialTheme.colorScheme.surfaceVariant
        MessageType.AI_SUGGESTION -> MaterialTheme.colorScheme.tertiaryContainer
        MessageType.SYSTEM_MESSAGE -> MaterialTheme.colorScheme.secondaryContainer
        MessageType.ERROR_MESSAGE -> MaterialTheme.colorScheme.errorContainer
    }
    
    val textColor = when (message.messageType) {
        MessageType.USER_MESSAGE -> MaterialTheme.colorScheme.onPrimary
        MessageType.AI_RESPONSE -> MaterialTheme.colorScheme.onSurfaceVariant
        MessageType.AI_SUGGESTION -> MaterialTheme.colorScheme.onTertiaryContainer
        MessageType.SYSTEM_MESSAGE -> MaterialTheme.colorScheme.onSecondaryContainer
        MessageType.ERROR_MESSAGE -> MaterialTheme.colorScheme.onErrorContainer
    }
    
    val formatter = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val timeText = remember(message) {
        formatter.format(message.timestamp)
    }
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentAlignment = if (isUserMessage) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .align(if (isUserMessage) Alignment.CenterEnd else Alignment.CenterStart),
            horizontalAlignment = if (isUserMessage) Alignment.End else Alignment.Start
        ) {
            // Avatar or icon for AI messages
            if (!isUserMessage && message.messageType == MessageType.AI_RESPONSE) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Chat,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Text(
                        text = "LoansAI",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
            
            // Message bubble
            Surface(
                shape = RoundedCornerShape(
                    topStart = if (isUserMessage) 16.dp else 4.dp,
                    topEnd = if (isUserMessage) 4.dp else 16.dp,
                    bottomStart = 16.dp,
                    bottomEnd = 16.dp
                ),
                color = backgroundColor,
                shadowElevation = 0.5.dp,
                modifier = Modifier
                    .align(if (isUserMessage) Alignment.End else Alignment.Start)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    Text(
                        text = message.message,
                        color = textColor,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(
                        text = timeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor.copy(alpha = 0.7f),
                        textAlign = if (isUserMessage) TextAlign.End else TextAlign.Start,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

/**
 * Typing indicator when AI is responding
 */
@Composable
fun TypingIndicator() {
    Row(
        modifier = Modifier
            .padding(vertical = 4.dp)
            .clip(
                RoundedCornerShape(
                    topStart = 0.dp,
                    topEnd = 16.dp,
                    bottomStart = 16.dp,
                    bottomEnd = 16.dp
                )
            )
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "AI is thinking...",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
        
        Spacer(modifier = Modifier.width(8.dp))
        
        TypingDots()
    }
}

/**
 * Animated typing dots
 */
@Composable
fun TypingDots() {
    val dotSize = 6.dp
    val dotSpacing = 3.dp
    
    Row(
        horizontalArrangement = Arrangement.spacedBy(dotSpacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0..2) {
            val delay = i * 150
            var visible by remember { mutableStateOf(true) }
            val alpha by animateFloatAsState(
                targetValue = if (visible) 1f else 0.3f,
                animationSpec = tween(300),
                label = "dot-alpha-$i"
            )
            
            LaunchedEffect(Unit) {
                while (true) {
                    kotlinx.coroutines.delay(300L + delay)
                    visible = !visible
                }
            }
            
            Box(
                modifier = Modifier
                    .size(dotSize)
                    .clip(CircleShape)
                    .background(
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
                    )
            )
        }
    }
}

/**
 * Welcome message when no conversation exists
 */
@Composable
fun WelcomeMessage() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Logo/icon
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.Chat,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Welcome to LoansAI Assistant",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "I'm here to guide you through your loan application journey. Ask me anything about the process, required documents, or how to enhance your loan eligibility.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "Try asking:",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            SuggestedQuestion("What documents do I need for the loan?")
            SuggestedQuestion("How much loan can I get with my salary?")
            SuggestedQuestion("What happens after I submit my application?")
            SuggestedQuestion("How do I improve my eligibility?")
        }
    }
}

/**
 * Clickable suggested question for the welcome message
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuggestedQuestion(
    question: String,
    viewModel: AIAssistantViewModel = hiltViewModel()
) {
    Card(
        onClick = {
            viewModel.sendMessage(question)
        },
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp)
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.Chat,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Text(
                text = question,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(modifier = Modifier.weight(1f))
            
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.Send,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}