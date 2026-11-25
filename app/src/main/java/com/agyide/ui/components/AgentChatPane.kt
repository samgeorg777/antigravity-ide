package com.agyide.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agyide.engine.AgyModelOption
import com.agyide.engine.SavedConversation
import com.agyide.ui.ChatMessage
import com.agyide.ui.TurnBlock
import com.agyide.ui.theme.CosmicColors
import java.io.File

private data class SlashCommandTemplate(
    val command: String,
    val description: String,
    val expansion: String
)

private val slashTemplates = listOf(
    SlashCommandTemplate("/plan", "Create a step-by-step plan before editing", "First analyze the codebase and propose a concise implementation plan before making changes: "),
    SlashCommandTemplate("/review", "Review recent changes or current file for bugs", "Review the active workspace changes and open files for bugs, edge cases, and security issues: "),
    SlashCommandTemplate("/fix", "Diagnose and fix an error or failing command", "Investigate and fix the following issue in this workspace: "),
    SlashCommandTemplate("/test", "Write and run tests for this project", "Run existing tests or add unit tests for the current workspace and verify they pass: "),
    SlashCommandTemplate("/explain", "Explain how the project or file works", "Explain the architecture and key code paths for: "),
    SlashCommandTemplate("/commit", "Summarize changes and draft a git commit", "Inspect git diff and summarize the changes with a clear commit message.")
)

@Composable
fun AgentChatPane(
    messages: List<ChatMessage>,
    activeConversationId: String?,
    savedConversations: List<SavedConversation>,
    models: List<AgyModelOption>,
    selectedModelId: String,
    selectedEffort: String,
    selectedMode: String,
    isRunning: Boolean,
    composerText: String,
    attachedPaths: List<String>,
    workspaceFiles: List<String>,
    onComposerChange: (String) -> Unit,
    onSend: () -> Unit,
    onCancel: () -> Unit,
    onNewChat: () -> Unit,
    onResumeConversation: (SavedConversation) -> Unit,
    onRefreshConversations: () -> Unit,
    onSelectModel: (String) -> Unit,
    onSelectEffort: (String) -> Unit,
    onSelectMode: (String) -> Unit,
    onPickAttachment: () -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onVoiceInput: () -> Unit,
    onOpenPath: (String, Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    var showModelMenu by remember { mutableStateOf(false) }
    var showModeMenu by remember { mutableStateOf(false) }
    var showHistorySheet by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, messages.lastOrNull()?.blocks?.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CosmicColors.Background)
    ) {
        // Top Agent Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CosmicColors.Surface)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNewChat, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Default.AddComment, contentDescription = "New Chat", tint = CosmicColors.Cyan, modifier = Modifier.size(19.dp))
            }
            IconButton(
                onClick = {
                    onRefreshConversations()
                    showHistorySheet = true
                },
                modifier = Modifier.size(34.dp)
            ) {
                Icon(Icons.Default.History, contentDescription = "Conversation History", tint = CosmicColors.Violet, modifier = Modifier.size(19.dp))
            }

            // Model Selector
            Box(modifier = Modifier.weight(1f)) {
                val activeModelLabel = models.find { it.id == selectedModelId }?.label ?: selectedModelId
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(CosmicColors.SurfaceElevated)
                        .border(1.dp, CosmicColors.SurfaceBorder, RoundedCornerShape(8.dp))
                        .clickable { showModelMenu = true }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = CosmicColors.Cyan, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = activeModelLabel,
                        color = CosmicColors.TextPrimary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = CosmicColors.TextSecondary, modifier = Modifier.size(16.dp))
                }
                DropdownMenu(expanded = showModelMenu, onDismissRequest = { showModelMenu = false }) {
                    models.forEach { m ->
                        DropdownMenuItem(
                            text = { Text(if (m.id == selectedModelId) "✓ ${m.label}" else m.label, fontSize = 13.sp) },
                            onClick = {
                                showModelMenu = false
                                onSelectModel(m.id)
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.width(6.dp))

            // Mode & Effort Menu
            Box {
                AssistChip(
                    onClick = { showModeMenu = true },
                    label = {
                        val modeLabel = if (selectedMode == "default") "Default" else selectedMode
                        val effLabel = if (selectedEffort == "default") "" else " · $selectedEffort"
                        Text("$modeLabel$effLabel", fontSize = 11.sp)
                    }
                )
                DropdownMenu(expanded = showModeMenu, onDismissRequest = { showModeMenu = false }) {
                    Text("Mode", modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), color = CosmicColors.Cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    listOf("default" to "Default", "plan" to "Plan", "accept-edits" to "Accept Edits", "auto" to "Auto").forEach { (id, label) ->
                        DropdownMenuItem(
                            text = { Text(if (selectedMode == id) "✓ $label" else label) },
                            onClick = { onSelectMode(id); showModeMenu = false }
                        )
                    }
                    HorizontalDivider()
                    Text("Reasoning Effort", modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), color = CosmicColors.Violet, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    listOf("default" to "Model Default", "low" to "Low", "medium" to "Medium", "high" to "High", "xhigh" to "XHigh").forEach { (id, label) ->
                        DropdownMenuItem(
                            text = { Text(if (selectedEffort == id) "✓ $label" else label) },
                            onClick = { onSelectEffort(id); showModeMenu = false }
                        )
                    }
                }
            }
        }

        // Chat Transcript or Empty Welcome State
        if (messages.isEmpty()) {
            EmptyAgentWelcome(
                activeConversationId = activeConversationId,
                onQuickPrompt = { prompt ->
                    onComposerChange(prompt)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 10.dp),
                contentPadding = PaddingValues(vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    ChatMessageCard(msg = msg, onOpenPath = onOpenPath)
                }
            }
        }

        // Slash Command Autocomplete Popup
        val slashMatches = remember(composerText) {
            if (composerText.startsWith("/") && !composerText.contains(' ')) {
                slashTemplates.filter { it.command.startsWith(composerText, ignoreCase = true) }
            } else emptyList()
        }
        if (slashMatches.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CosmicColors.SurfaceElevated)
                    .border(1.dp, CosmicColors.SurfaceBorder)
                    .padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                slashMatches.forEach { tpl ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onComposerChange(tpl.expansion) }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(tpl.command, color = CosmicColors.Cyan, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                        Spacer(Modifier.width(10.dp))
                        Text(tpl.description, color = CosmicColors.TextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }

        // @ Mention File Autocomplete Popup
        val atToken = remember(composerText) {
            val match = Regex("(?:^|\\s)@([^\\s]*)$").find(composerText)
            match?.groupValues?.getOrNull(1)
        }
        val fileMatches = remember(atToken, workspaceFiles) {
            if (atToken != null) {
                workspaceFiles.filter { it.contains(atToken, ignoreCase = true) }.take(6)
            } else emptyList()
        }
        if (fileMatches.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CosmicColors.SurfaceElevated)
                    .border(1.dp, CosmicColors.SurfaceBorder)
                    .padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text("Attach workspace file reference:", color = CosmicColors.Violet, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 6.dp))
                fileMatches.forEach { rel ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .clickable {
                                val updated = composerText.replace(Regex("@([^\\s]*)$"), "@$rel ")
                                onComposerChange(updated)
                            }
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Description, contentDescription = null, tint = CosmicColors.Cyan, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(rel, color = CosmicColors.TextPrimary, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }

        // Attached Files Strip
        if (attachedPaths.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CosmicColors.Surface)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                attachedPaths.forEach { path ->
                    InputChip(
                        selected = true,
                        onClick = { onRemoveAttachment(path) },
                        label = { Text(File(path).name, fontSize = 11.sp) },
                        trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(14.dp)) }
                    )
                }
            }
        }

        // Composer Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CosmicColors.Surface)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            IconButton(onClick = onPickAttachment, modifier = Modifier.size(38.dp)) {
                Icon(Icons.Default.AttachFile, contentDescription = "Attach Image or File", tint = CosmicColors.TextSecondary)
            }
            IconButton(onClick = onVoiceInput, modifier = Modifier.size(38.dp)) {
                Icon(Icons.Default.Mic, contentDescription = "Voice Prompt", tint = CosmicColors.Violet)
            }

            OutlinedTextField(
                value = composerText,
                onValueChange = onComposerChange,
                placeholder = { Text("Ask Antigravity... (/ commands, @ files)", fontSize = 13.sp, color = CosmicColors.TextMuted) },
                maxLines = 5,
                modifier = Modifier.weight(1f)
            )

            Spacer(Modifier.width(6.dp))

            if (isRunning) {
                FilledIconButton(
                    onClick = onCancel,
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = CosmicColors.Red)
                ) {
                    Icon(Icons.Default.Stop, contentDescription = "Stop", tint = Color.White)
                }
            } else {
                FilledIconButton(
                    onClick = onSend,
                    enabled = composerText.isNotBlank() || attachedPaths.isNotEmpty(),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = CosmicColors.Cyan)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = CosmicColors.Background)
                }
            }
        }
    }

    if (showHistorySheet) {
        AlertDialog(
            onDismissRequest = { showHistorySheet = false },
            containerColor = CosmicColors.Surface,
            title = { Text("Saved Sessions", color = CosmicColors.Cyan, fontWeight = FontWeight.Bold) },
            text = {
                if (savedConversations.isEmpty()) {
                    Text("No saved Antigravity sessions found yet.", color = CosmicColors.TextSecondary)
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(savedConversations, key = { it.id }) { conv ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(CosmicColors.SurfaceElevated)
                                    .border(1.dp, CosmicColors.SurfaceBorder, RoundedCornerShape(10.dp))
                                    .clickable {
                                        showHistorySheet = false
                                        onResumeConversation(conv)
                                    }
                                    .padding(10.dp)
                            ) {
                                Text(conv.title, color = CosmicColors.TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (conv.workspacePath != null) {
                                    Text(conv.workspacePath, color = CosmicColors.CyanMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Text(conv.id.take(12), color = CosmicColors.TextMuted, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showHistorySheet = false }) { Text("Close", color = CosmicColors.Cyan) }
            }
        )
    }
}

@Composable
private fun EmptyAgentWelcome(
    activeConversationId: String?,
    onQuickPrompt: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = CosmicColors.Cyan, modifier = Modifier.size(44.dp))
        Spacer(Modifier.height(10.dp))
        Text("Antigravity Agent", color = CosmicColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (activeConversationId != null) "Continuing session ${activeConversationId.take(8)}..." else "Autonomous coding, file edits, terminal execution & planning directly inside your workspace.",
            color = CosmicColors.TextSecondary,
            fontSize = 13.sp
        )
        Spacer(Modifier.height(18.dp))
        listOf(
            "Summarize this workspace and key files",
            "Create a modern responsive web app in index.html",
            "Check git status and review recent changes"
        ).forEach { prompt ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(CosmicColors.Surface)
                    .border(1.dp, CosmicColors.SurfaceBorder, RoundedCornerShape(10.dp))
                    .clickable { onQuickPrompt(prompt) }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Bolt, contentDescription = null, tint = CosmicColors.Violet, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(prompt, color = CosmicColors.TextPrimary, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun ChatMessageCard(
    msg: ChatMessage,
    onOpenPath: (String, Int?) -> Unit
) {
    if (msg.isUser) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.End
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .clip(RoundedCornerShape(14.dp, 14.dp, 4.dp, 14.dp))
                    .background(Color(0xFF162544))
                    .border(1.dp, CosmicColors.Cyan.copy(alpha = 0.3f), RoundedCornerShape(14.dp, 14.dp, 4.dp, 14.dp))
                    .padding(12.dp)
            ) {
                if (msg.attachments.isNotEmpty()) {
                    Text(
                        text = msg.attachments.joinToString(", ") { File(it).name },
                        color = CosmicColors.Cyan,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(Modifier.height(4.dp))
                }
                Text(msg.userText, color = CosmicColors.TextPrimary, fontSize = 14.sp)
            }
        }
        return
    }

    // Assistant Card
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CosmicColors.Surface)
            .border(1.dp, CosmicColors.SurfaceBorder, RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = CosmicColors.Cyan, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
            Text("Antigravity", color = CosmicColors.Cyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            if (msg.isStreaming) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = CosmicColors.Cyan)
            } else if (msg.durationSeconds != null || msg.totalTokens != null) {
                val meta = buildList {
                    msg.durationSeconds?.let { add(String.format("%.1fs", it)) }
                    msg.totalTokens?.let { add("${it} tok") }
                }.joinToString(" · ")
                Text(meta, color = CosmicColors.TextMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            }
        }

        msg.blocks.forEach { block ->
            when (block) {
                is TurnBlock.ToolCallBlock -> ToolCallCard(block = block, onOpenPath = onOpenPath)
                is TurnBlock.TextBlock -> MarkdownContent(text = block.text, onOpenPath = onOpenPath)
            }
        }

        if (!msg.errorText.isNullOrBlank()) {
            Text(
                text = msg.errorText,
                color = CosmicColors.Red,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x22FF5370))
                    .padding(8.dp)
            )
        }
    }
}

@Composable
private fun ToolCallCard(
    block: TurnBlock.ToolCallBlock,
    onOpenPath: (String, Int?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val extractedPath = remember(block.paramsSummary) {
        Regex("(?:AbsolutePath|TargetFile|DirectoryPath|SearchPath)=([^,\\s]+)").find(block.paramsSummary)?.groupValues?.getOrNull(1)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(CosmicColors.SurfaceElevated)
            .border(1.dp, CosmicColors.SurfaceBorder, RoundedCornerShape(10.dp))
            .clickable { expanded = !expanded }
            .padding(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (block.isRunning) {
                CircularProgressIndicator(modifier = Modifier.size(13.dp), strokeWidth = 2.dp, color = CosmicColors.Violet)
            } else {
                Icon(Icons.Default.Build, contentDescription = null, tint = CosmicColors.Green, modifier = Modifier.size(14.dp))
            }
            Spacer(Modifier.width(6.dp))
            Text(
                text = block.toolName,
                color = CosmicColors.Violet,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
            if (block.paramsSummary.isNotBlank()) {
                Spacer(Modifier.width(6.dp))
                Text(
                    text = block.paramsSummary,
                    color = CosmicColors.TextSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            if (extractedPath != null) {
                TextButton(
                    onClick = { onOpenPath(extractedPath, null) },
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                ) {
                    Text("Open", fontSize = 11.sp, color = CosmicColors.Cyan)
                }
            }
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = CosmicColors.TextMuted,
                modifier = Modifier.size(16.dp)
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (block.paramsSummary.isNotBlank()) {
                    Text(block.paramsSummary, color = CosmicColors.TextSecondary, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                }
                if (!block.output.isNullOrBlank()) {
                    Text(
                        text = block.output.take(1500),
                        color = CosmicColors.TextPrimary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(CosmicColors.Background)
                            .padding(8.dp)
                    )
                }
            }
        }
    }
}

private sealed interface MdSegment {
    data class Paragraph(val text: String) : MdSegment
    data class CodeFence(val lang: String, val code: String) : MdSegment
}

@Composable
private fun MarkdownContent(
    text: String,
    onOpenPath: (String, Int?) -> Unit
) {
    val segments = remember(text) { parseMarkdownSegments(text) }
    val clipboard = LocalClipboardManager.current

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        segments.forEach { seg ->
            when (seg) {
                is MdSegment.CodeFence -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(CosmicColors.Background)
                            .border(1.dp, CosmicColors.SurfaceBorder, RoundedCornerShape(8.dp))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(CosmicColors.SurfaceElevated)
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(seg.lang.ifBlank { "code" }, color = CosmicColors.CyanMuted, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                            Spacer(Modifier.weight(1f))
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy Code",
                                tint = CosmicColors.TextSecondary,
                                modifier = Modifier
                                    .size(15.dp)
                                    .clickable { clipboard.setText(AnnotatedString(seg.code)) }
                            )
                        }
                        Text(
                            text = seg.code,
                            color = CosmicColors.TextPrimary,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(10.dp)
                        )
                    }
                }

                is MdSegment.Paragraph -> {
                    val annotated = remember(seg.text) { renderInlineMarkdown(seg.text) }
                    Text(text = annotated, fontSize = 14.sp, lineHeight = 20.sp)

                    // Quick open pills for any file paths mentioned in text
                    val fileRefs = remember(seg.text) {
                        Regex("(?:[a-zA-Z0-9_.-]+/)*[a-zA-Z0-9_.-]+\\.(?:kt|kts|java|py|js|ts|jsx|tsx|html|css|xml|json|md|sh|yml|yaml)(?::(\\d+))?")
                            .findAll(seg.text)
                            .map { it.value }
                            .distinct()
                            .take(4)
                            .toList()
                    }
                    if (fileRefs.isNotEmpty()) {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            fileRefs.forEach { ref ->
                                val parts = ref.split(':')
                                val path = parts[0]
                                val line = parts.getOrNull(1)?.toIntOrNull()
                                AssistChip(
                                    onClick = { onOpenPath(path, line) },
                                    leadingIcon = { Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(14.dp), tint = CosmicColors.Cyan) },
                                    label = { Text(ref, fontSize = 11.sp, fontFamily = FontFamily.Monospace) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun parseMarkdownSegments(input: String): List<MdSegment> {
    val lines = input.lines()
    val out = mutableListOf<MdSegment>()
    val textBuf = StringBuilder()
    val codeBuf = StringBuilder()
    var inCode = false
    var codeLang = ""

    fun flushText() {
        val t = textBuf.toString().trim()
        if (t.isNotEmpty()) out += MdSegment.Paragraph(t)
        textBuf.clear()
    }

    for (line in lines) {
        val trimmed = line.trimStart()
        if (trimmed.startsWith("```")) {
            if (!inCode) {
                flushText()
                inCode = true
                codeLang = trimmed.removePrefix("```").trim()
            } else {
                out += MdSegment.CodeFence(codeLang, codeBuf.toString().trimEnd())
                codeBuf.clear()
                inCode = false
                codeLang = ""
            }
        } else if (inCode) {
            codeBuf.appendLine(line)
        } else {
            textBuf.appendLine(line)
        }
    }
    if (inCode && codeBuf.isNotEmpty()) {
        out += MdSegment.CodeFence(codeLang, codeBuf.toString().trimEnd())
    }
    flushText()
    return out
}

private fun renderInlineMarkdown(raw: String): AnnotatedString = buildAnnotatedString {
    val lines = raw.lines()
    lines.forEachIndexed { idx, line ->
        val trimmed = line.trimStart()
        when {
            trimmed.startsWith("### ") -> {
                withStyle(SpanStyle(color = CosmicColors.Cyan, fontWeight = FontWeight.Bold, fontSize = 15.sp)) {
                    append(trimmed.removePrefix("### "))
                }
            }
            trimmed.startsWith("## ") -> {
                withStyle(SpanStyle(color = CosmicColors.Cyan, fontWeight = FontWeight.Bold, fontSize = 16.sp)) {
                    append(trimmed.removePrefix("## "))
                }
            }
            trimmed.startsWith("# ") -> {
                withStyle(SpanStyle(color = CosmicColors.Cyan, fontWeight = FontWeight.Bold, fontSize = 17.sp)) {
                    append(trimmed.removePrefix("# "))
                }
            }
            else -> appendFormattedLine(line)
        }
        if (idx < lines.lastIndex) append('\n')
    }
}

private fun AnnotatedString.Builder.appendFormattedLine(line: String) {
    var i = 0
    while (i < line.length) {
        if (line.startsWith("**", i)) {
            val end = line.indexOf("**", i + 2)
            if (end > i + 2) {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = CosmicColors.TextPrimary)) {
                    append(line.substring(i + 2, end))
                }
                i = end + 2
                continue
            }
        }
        if (line[i] == '`') {
            val end = line.indexOf('`', i + 1)
            if (end > i + 1) {
                withStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        color = CosmicColors.Violet,
                        background = Color(0xFF151B28)
                    )
                ) {
                    append(" ${line.substring(i + 1, end)} ")
                }
                i = end + 1
                continue
            }
        }
        if (line[i] == '*' && (i + 1 < line.length && line[i + 1] != ' ')) {
            val end = line.indexOf('*', i + 1)
            if (end > i + 1) {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    append(line.substring(i + 1, end))
                }
                i = end + 1
                continue
            }
        }
        withStyle(SpanStyle(color = CosmicColors.TextPrimary)) {
            append(line[i])
        }
        i++
    }
}
