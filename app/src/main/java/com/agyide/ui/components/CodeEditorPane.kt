package com.agyide.ui.components

import android.graphics.Typeface
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.WrapText
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.agyide.engine.DiffLine
import com.agyide.engine.DiffType
import com.agyide.engine.WorkspaceManager
import com.agyide.ui.DiffModalState
import com.agyide.ui.EditorTab
import com.agyide.ui.theme.CosmicColors
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.event.SelectionChangeEvent
import io.github.rosemoe.sora.lang.EmptyLanguage
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.EditorSearcher
import io.github.rosemoe.sora.widget.schemes.SchemeDarcula

@Composable
fun CodeEditorPane(
    openTabs: List<EditorTab>,
    activeTabPath: String?,
    editorTheme: String,
    fontSize: Float,
    wordWrap: Boolean,
    showFindBar: Boolean,
    onSelectTab: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    onContentChanged: (String, String, Int, Int) -> Unit,
    onCursorChanged: (String, Int, Int) -> Unit,
    onSave: () -> Unit,
    onShowDiff: () -> Unit,
    onAskAgent: () -> Unit,
    onPreviewHtml: () -> Unit,
    onToggleFind: () -> Unit,
    onToggleWrap: () -> Unit,
    onChangeFontSize: (Float) -> Unit,
    onSelectTheme: (String) -> Unit,
    onOpenFilesPane: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeTab = openTabs.find { it.path == activeTabPath }
    var editorRef by remember { mutableStateOf<CodeEditor?>(null) }
    var showThemeMenu by remember { mutableStateOf(false) }

    var searchQuery by remember { mutableStateOf("") }
    var replaceQuery by remember { mutableStateOf("") }
    var matchCase by remember { mutableStateOf(false) }
    var useRegex by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CosmicColors.Background)
    ) {
        // Top Editor Tab Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CosmicColors.Surface)
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                openTabs.forEach { tab ->
                    val selected = tab.path == activeTabPath
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) CosmicColors.SurfaceElevated else Color.Transparent)
                            .border(
                                width = 1.dp,
                                color = if (selected) CosmicColors.Cyan.copy(alpha = 0.5f) else CosmicColors.SurfaceBorder,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { onSelectTab(tab.path) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (tab.isDirty) "● ${tab.title}" else tab.title,
                            color = if (selected) CosmicColors.Cyan else CosmicColors.TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                        )
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close tab",
                            tint = CosmicColors.TextMuted,
                            modifier = Modifier
                                .size(14.dp)
                                .clickable { onCloseTab(tab.path) }
                        )
                    }
                }
            }

            if (activeTab != null) {
                IconButton(onClick = onSave, modifier = Modifier.size(34.dp)) {
                    Icon(
                        Icons.Default.Save,
                        contentDescription = "Save",
                        tint = if (activeTab.isDirty) CosmicColors.Amber else CosmicColors.TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(onClick = onAskAgent, modifier = Modifier.size(34.dp)) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = "Ask Agent",
                        tint = CosmicColors.Cyan,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        if (activeTab == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Code,
                        contentDescription = null,
                        tint = CosmicColors.TextMuted,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                    Text("No file open in editor", color = CosmicColors.TextSecondary)
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = onOpenFilesPane) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Open File from Explorer")
                    }
                }
            }
            return
        }

        // Secondary Action Strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CosmicColors.SurfaceElevated)
                .padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Ln ${activeTab.cursorLine + 1}, Col ${activeTab.cursorColumn + 1}",
                color = CosmicColors.TextSecondary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
            Spacer(Modifier.weight(1f))

            IconButton(onClick = { editorRef?.undo() }, modifier = Modifier.size(30.dp)) {
                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo", tint = CosmicColors.TextSecondary, modifier = Modifier.size(16.dp))
            }
            IconButton(onClick = { editorRef?.redo() }, modifier = Modifier.size(30.dp)) {
                Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo", tint = CosmicColors.TextSecondary, modifier = Modifier.size(16.dp))
            }
            IconButton(onClick = onToggleFind, modifier = Modifier.size(30.dp)) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = "Find & Replace",
                    tint = if (showFindBar) CosmicColors.Cyan else CosmicColors.TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
            if (activeTab.isDirty) {
                IconButton(onClick = onShowDiff, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Default.Compare, contentDescription = "View Unsaved Diff", tint = CosmicColors.Amber, modifier = Modifier.size(16.dp))
                }
            }
            if (activeTab.title.endsWith(".html", true) || activeTab.title.endsWith(".htm", true)) {
                IconButton(onClick = onPreviewHtml, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Preview HTML", tint = CosmicColors.Green, modifier = Modifier.size(18.dp))
                }
            }
            IconButton(onClick = onToggleWrap, modifier = Modifier.size(30.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.WrapText,
                    contentDescription = "Word Wrap",
                    tint = if (wordWrap) CosmicColors.Cyan else CosmicColors.TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
            IconButton(onClick = { onChangeFontSize(-1f) }, modifier = Modifier.size(28.dp)) {
                Text("A-", color = CosmicColors.TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            IconButton(onClick = { onChangeFontSize(1f) }, modifier = Modifier.size(28.dp)) {
                Text("A+", color = CosmicColors.TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Box {
                IconButton(onClick = { showThemeMenu = true }, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Default.Palette, contentDescription = "Editor Theme", tint = CosmicColors.Violet, modifier = Modifier.size(16.dp))
                }
                DropdownMenu(expanded = showThemeMenu, onDismissRequest = { showThemeMenu = false }) {
                    listOf(
                        "antigravity_cosmic" to "Antigravity Cosmic",
                        "darcula" to "Darcula",
                        "abyss" to "Abyss",
                        "solarized_dark" to "Solarized Dark",
                        "quietlight" to "Quiet Light"
                    ).forEach { (key, label) ->
                        DropdownMenuItem(
                            text = { Text(if (key == editorTheme) "✓ $label" else label) },
                            onClick = {
                                showThemeMenu = false
                                onSelectTheme(key)
                            }
                        )
                    }
                }
            }
        }

        // Optional Find & Replace Bar
        if (showFindBar) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CosmicColors.Surface)
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { q ->
                            searchQuery = q
                            applyEditorSearch(editorRef, q, matchCase, useRegex)
                        },
                        placeholder = { Text("Find...", fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = matchCase,
                        onClick = {
                            matchCase = !matchCase
                            applyEditorSearch(editorRef, searchQuery, matchCase, useRegex)
                        },
                        label = { Text("Aa", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = useRegex,
                        onClick = {
                            useRegex = !useRegex
                            applyEditorSearch(editorRef, searchQuery, matchCase, useRegex)
                        },
                        label = { Text(".*", fontSize = 11.sp) }
                    )
                    IconButton(onClick = { editorRef?.searcher?.gotoPrevious() }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Prev")
                    }
                    IconButton(onClick = { editorRef?.searcher?.gotoNext() }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Next")
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = replaceQuery,
                        onValueChange = { replaceQuery = it },
                        placeholder = { Text("Replace with...", fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { editorRef?.searcher?.replaceThis(replaceQuery) }) {
                        Text("Replace", fontSize = 12.sp)
                    }
                    TextButton(onClick = { editorRef?.searcher?.replaceAll(replaceQuery) }) {
                        Text("All", fontSize = 12.sp)
                    }
                }
            }
        }

        // Sora CodeEditor Canvas
        SoraEditorSurface(
            tab = activeTab,
            themeName = editorTheme,
            fontSize = fontSize,
            wordWrap = wordWrap,
            onEditorReady = { editorRef = it },
            onContentChanged = onContentChanged,
            onCursorChanged = onCursorChanged,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )

        // Code Symbol Quick-Insert Row
        val symbols = remember {
            listOf("TAB" to "    ", "{" to "{}", "}" to "}", "(" to "()", ")" to ")", "[" to "[]", "]" to "]", "=" to "=", ";" to ";", ":" to ":", "\"" to "\"\"", "'" to "''", "<" to "<", ">" to ">", "/" to "/", "_" to "_")
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CosmicColors.Surface)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            symbols.forEach { (label, insertText) ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(CosmicColors.SurfaceElevated)
                        .border(1.dp, CosmicColors.SurfaceBorder, RoundedCornerShape(6.dp))
                        .clickable {
                        val cursorPos = if (insertText in listOf("{}", "()", "[]", "\"\"", "''")) 1 else insertText.length
                        editorRef?.insertText(insertText, cursorPos)
                    }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = label,
                        color = CosmicColors.TextPrimary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

private fun applyEditorSearch(editor: CodeEditor?, query: String, matchCase: Boolean, regex: Boolean) {
    val searcher = editor?.searcher ?: return
    if (query.isEmpty()) {
        searcher.stopSearch()
        return
    }
    runCatching {
        val type = if (regex) EditorSearcher.SearchOptions.TYPE_REGULAR_EXPRESSION else EditorSearcher.SearchOptions.TYPE_NORMAL
        searcher.search(query, EditorSearcher.SearchOptions(type, !matchCase))
    }
}

@Composable
private fun SoraEditorSurface(
    tab: EditorTab,
    themeName: String,
    fontSize: Float,
    wordWrap: Boolean,
    onEditorReady: (CodeEditor) -> Unit,
    onContentChanged: (String, String, Int, Int) -> Unit,
    onCursorChanged: (String, Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val latestTabPath by rememberUpdatedState(tab.path)
    val latestOnContentChanged by rememberUpdatedState(onContentChanged)
    val latestOnCursorChanged by rememberUpdatedState(onCursorChanged)

    var boundPath by remember { mutableStateOf<String?>(null) }
    var boundJumpId by remember { mutableStateOf(-1) }
    var boundTheme by remember { mutableStateOf<String?>(null) }
    var suppressChangeCallback by remember { mutableStateOf(false) }

    val monoTypeface = remember(context) {
        runCatching { Typeface.createFromAsset(context.assets, "JetBrainsMono-Regular.ttf") }
            .getOrDefault(Typeface.MONOSPACE)
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            CodeEditor(ctx).apply {
                typefaceText = monoTypeface
                typefaceLineNumber = monoTypeface
                setLineSpacing(2f, 1.15f)
                props.stickyScroll = true
                setTextSize(fontSize)
                isWordwrap = wordWrap

                subscribeAlways(ContentChangeEvent::class.java) { _ ->
                    if (!suppressChangeCallback) {
                        val c = cursor
                        latestOnContentChanged(latestTabPath, text.toString(), c.leftLine, c.leftColumn)
                    }
                }
                subscribeAlways(SelectionChangeEvent::class.java) { _ ->
                    if (!suppressChangeCallback) {
                        val c = cursor
                        latestOnCursorChanged(latestTabPath, c.leftLine, c.leftColumn)
                    }
                }
                onEditorReady(this)
            }
        },
        update = { editor ->
            editor.setTextSize(fontSize)
            editor.isWordwrap = wordWrap

            if (boundTheme != themeName) {
                boundTheme = themeName
                runCatching {
                    ThemeRegistry.getInstance().setTheme(themeName)
                    editor.colorScheme = TextMateColorScheme.create(ThemeRegistry.getInstance())
                }.onFailure {
                    editor.colorScheme = SchemeDarcula()
                }
            }

            val switchedFile = boundPath != tab.path
            val externalContentRefresh = !switchedFile && !tab.isDirty && editor.text.toString() != tab.content
            if (switchedFile || externalContentRefresh) {
                suppressChangeCallback = true
                boundPath = tab.path
                val scope = WorkspaceManager.languageScopeForFile(tab.title)
                val lang = if (scope != null) {
                    runCatching { TextMateLanguage.create(scope, true) }.getOrElse { EmptyLanguage() }
                } else {
                    EmptyLanguage()
                }
                editor.setEditorLanguage(lang)
                editor.setText(tab.content)
                val safeLine = tab.cursorLine.coerceIn(0, (editor.text.lineCount - 1).coerceAtLeast(0))
                val safeCol = tab.cursorColumn.coerceIn(0, editor.text.getColumnCount(safeLine))
                editor.setSelection(safeLine, safeCol, true)
                suppressChangeCallback = false
            }

            if (boundJumpId != tab.jumpRequestId && tab.jumpRequestId > 0) {
                boundJumpId = tab.jumpRequestId
                val safeLine = tab.cursorLine.coerceIn(0, (editor.text.lineCount - 1).coerceAtLeast(0))
                editor.jumpToLine(safeLine)
            }
        }
    )
}

@Composable
fun DiffViewerModal(
    modal: DiffModalState,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CosmicColors.Surface,
        title = {
            Text(modal.title, color = CosmicColors.Cyan, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 180.dp, max = 440.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(CosmicColors.Background)
                    .padding(8.dp)
            ) {
                items(modal.lines) { line: DiffLine ->
                    val bg = when (line.type) {
                        DiffType.ADDED -> Color(0x2822C55E)
                        DiffType.REMOVED -> Color(0x28FF5370)
                        DiffType.HEADER -> Color(0x2200F0FF)
                        DiffType.CONTEXT -> Color.Transparent
                    }
                    val fg = when (line.type) {
                        DiffType.ADDED -> Color(0xFF4ADE80)
                        DiffType.REMOVED -> Color(0xFFFF6E8A)
                        DiffType.HEADER -> CosmicColors.Cyan
                        DiffType.CONTEXT -> CosmicColors.TextSecondary
                    }
                    Text(
                        text = line.text,
                        color = fg,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(bg)
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = CosmicColors.Cyan)
            }
        }
    )
}
