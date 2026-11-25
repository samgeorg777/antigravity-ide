package com.agyide.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.agyide.engine.AgyAgentBridge
import com.agyide.engine.AgyModelOption
import com.agyide.engine.AgyStreamEvent
import com.agyide.engine.BootstrapManager
import com.agyide.engine.BootstrapProgress
import com.agyide.engine.DiffLine
import com.agyide.engine.GitManager
import com.agyide.engine.GitRepoState
import com.agyide.engine.RuntimeEnv
import com.agyide.engine.SavedConversation
import com.agyide.engine.WorkspaceManager
import com.agyide.engine.WorkspaceNode
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

enum class IdePane(val label: String) {
    CHAT("Agent"),
    EDITOR("Editor"),
    EXPLORER("Files & Git"),
    TERMINAL("Terminal"),
    PREVIEW("Preview")
}

data class EditorTab(
    val file: File,
    val content: String,
    val savedContent: String,
    val cursorLine: Int = 0,
    val cursorColumn: Int = 0,
    val jumpRequestId: Int = 0
) {
    val path: String get() = file.absolutePath
    val title: String get() = file.name
    val isDirty: Boolean get() = content != savedContent
}

data class DiffModalState(
    val title: String,
    val lines: List<DiffLine>,
    val rawPatch: String? = null
)

sealed interface TurnBlock {
    val id: String

    data class TextBlock(
        override val id: String,
        val text: String
    ) : TurnBlock

    data class ToolCallBlock(
        override val id: String,
        val stepIndex: Int,
        val toolName: String,
        val paramsSummary: String,
        val output: String?,
        val isRunning: Boolean,
        val durationSeconds: Double?
    ) : TurnBlock
}

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val isUser: Boolean,
    val userText: String = "",
    val attachments: List<String> = emptyList(),
    val blocks: List<TurnBlock> = emptyList(),
    val isStreaming: Boolean = false,
    val errorText: String? = null,
    val durationSeconds: Double? = null,
    val totalTokens: Long? = null
)

data class TerminalTabState(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val session: TerminalSession
)

data class IdeUiState(
    val bootstrapProgress: BootstrapProgress = BootstrapProgress("Starting", "Checking Antigravity runtime...", 0f),
    val isReady: Boolean = false,
    val hasAuth: Boolean = false,
    val apiKey: String = "",
    val showAuthDialog: Boolean = false,
    val activePane: IdePane = IdePane.CHAT,
    val projects: List<File> = emptyList(),
    val activeProject: File? = null,
    val expandedPaths: Set<String> = emptySet(),
    val treeNodes: List<WorkspaceNode> = emptyList(),
    val workspaceFiles: List<String> = emptyList(),
    val fileFilter: String = "",
    val openTabs: List<EditorTab> = emptyList(),
    val activeTabPath: String? = null,
    val editorTheme: String = "antigravity_cosmic",
    val editorFontSize: Float = 13f,
    val editorWordWrap: Boolean = false,
    val showFindBar: Boolean = false,
    val diffModal: DiffModalState? = null,
    val gitState: GitRepoState = GitRepoState(),
    val gitCommitMsg: String = "",
    val gitBusy: Boolean = false,
    val gitMessage: String? = null,
    val chatMessages: List<ChatMessage> = emptyList(),
    val activeConversationId: String? = null,
    val savedConversations: List<SavedConversation> = emptyList(),
    val models: List<AgyModelOption> = AgyAgentBridge.defaultModels,
    val selectedModelId: String = "gemini-4-hydrogen-high",
    val selectedEffort: String = "default",
    val selectedMode: String = "default",
    val isAgentRunning: Boolean = false,
    val composerText: String = "",
    val attachedPaths: List<String> = emptyList(),
    val previewUrl: String = "http://127.0.0.1:8080",
    val previewReloadNonce: Int = 0,
    val deviceLog: String = "ADB status not checked yet.",
    val deviceBusy: Boolean = false,
    val snackbarMessage: String? = null
)

class IdeViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("antigravity_ide_prefs", Context.MODE_PRIVATE)
    private val _uiState = MutableStateFlow(
        IdeUiState(
            apiKey = prefs.getString("gemini_api_key", "") ?: "",
            selectedModelId = prefs.getString("selected_model", "gemini-4-hydrogen-high") ?: "gemini-4-hydrogen-high",
            selectedEffort = prefs.getString("selected_effort", "default") ?: "default",
            selectedMode = prefs.getString("selected_mode", "default") ?: "default",
            editorTheme = prefs.getString("editor_theme", "antigravity_cosmic") ?: "antigravity_cosmic",
            editorFontSize = prefs.getFloat("editor_font_size", 13f),
            editorWordWrap = prefs.getBoolean("editor_word_wrap", false)
        )
    )
    val uiState: StateFlow<IdeUiState> = _uiState.asStateFlow()

    val terminalTabs = mutableListOf<TerminalTabState>()
    private val _terminalTabVersion = MutableStateFlow(0)
    val terminalTabVersion: StateFlow<Int> = _terminalTabVersion.asStateFlow()

    private val _activeTerminalIndex = MutableStateFlow(0)
    val activeTerminalIndex: StateFlow<Int> = _activeTerminalIndex.asStateFlow()

    private var activeTerminalClient: TerminalSessionClient? = null
    private var terminalScreenCallback: (() -> Unit)? = null

    init {
        startBootstrap()
    }

    fun startBootstrap() {
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            BootstrapManager.ensureReady(ctx).collect { progress ->
                _uiState.update { it.copy(bootstrapProgress = progress) }
                if (progress.done) {
                    onRuntimeReady()
                }
            }
        }
    }

    private suspend fun onRuntimeReady() {
        val ctx = getApplication<Application>()
        val projectsRoot = RuntimeEnv.projectsDir(ctx)
        val projects = WorkspaceManager.listProjects(projectsRoot)
        val savedProjectPath = prefs.getString("active_project_path", null)
        val initialProject = savedProjectPath?.let { File(it) }?.takeIf { it.exists() && it.isDirectory }
            ?: projects.firstOrNull()
            ?: projectsRoot

        val hasAuth = RuntimeEnv.hasAuthCredentials(ctx, _uiState.value.apiKey)
        _uiState.update {
            it.copy(
                isReady = true,
                hasAuth = hasAuth,
                showAuthDialog = !hasAuth,
                projects = projects,
                activeProject = initialProject
            )
        }

        selectProject(initialProject)
        refreshModels()
        refreshSavedConversations()

        // Auto-open README.md or main file in starter project if nothing is open yet
        if (_uiState.value.openTabs.isEmpty()) {
            val readme = File(initialProject, "README.md")
            if (readme.exists()) openFile(readme, switchToEditor = false)
        }
    }

    fun selectPane(pane: IdePane) {
        _uiState.update { it.copy(activePane = pane) }
        if (pane == IdePane.EXPLORER) {
            refreshGit()
        }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    fun showMessage(msg: String) {
        _uiState.update { it.copy(snackbarMessage = msg) }
    }

    fun setShowAuthDialog(show: Boolean) {
        _uiState.update { it.copy(showAuthDialog = show) }
    }

    fun saveApiKey(key: String) {
        val trimmed = key.trim()
        prefs.edit().putString("gemini_api_key", trimmed).apply()
        val hasAuth = RuntimeEnv.hasAuthCredentials(getApplication(), trimmed)
        _uiState.update {
            it.copy(
                apiKey = trimmed,
                hasAuth = hasAuth,
                showAuthDialog = false,
                snackbarMessage = if (trimmed.isNotEmpty()) "Gemini API key saved" else "Cleared API key"
            )
        }
        refreshModels()
    }

    fun refreshAuthStatus() {
        val hasAuth = RuntimeEnv.hasAuthCredentials(getApplication(), _uiState.value.apiKey)
        _uiState.update { it.copy(hasAuth = hasAuth) }
    }

    fun launchInteractiveLoginInTerminal() {
        _uiState.update { it.copy(showAuthDialog = false, activePane = IdePane.TERMINAL) }
        sendCommandToTerminal("agy")
    }

    // =========================================================================
    // Projects & File Explorer
    // =========================================================================

    fun refreshProjects() {
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            val list = WorkspaceManager.listProjects(RuntimeEnv.projectsDir(ctx))
            _uiState.update { it.copy(projects = list) }
        }
    }

    fun selectProject(projectDir: File) {
        if (!projectDir.exists()) return
        prefs.edit().putString("active_project_path", projectDir.absolutePath).apply()
        _uiState.update {
            it.copy(
                activeProject = projectDir,
                expandedPaths = setOf(projectDir.absolutePath)
            )
        }
        refreshFileTree()
        refreshGit()
    }

    fun createNewProject(name: String) {
        val clean = name.trim().replace(Regex("[^a-zA-Z0-9._-]"), "-")
        if (clean.isEmpty()) return
        val ctx = getApplication<Application>()
        val dir = File(RuntimeEnv.projectsDir(ctx), clean).apply { mkdirs() }
        File(dir, "README.md").writeText("# $clean\n")
        refreshProjects()
        selectProject(dir)
        showMessage("Created workspace $clean")
    }

    fun cloneGitProject(url: String, folderName: String) {
        val ctx = getApplication<Application>()
        val name = folderName.trim().ifBlank {
            url.trim().substringAfterLast('/').removeSuffix(".git").ifBlank { "cloned-repo" }
        }
        val target = File(RuntimeEnv.projectsDir(ctx), name)
        viewModelScope.launch {
            _uiState.update { it.copy(gitBusy = true) }
            val res = GitManager.clone(ctx, url.trim(), target)
            _uiState.update { it.copy(gitBusy = false) }
            if (res.exitCode == 0 && target.exists()) {
                refreshProjects()
                selectProject(target)
                showMessage("Cloned $name")
            } else {
                showMessage("Clone failed: ${res.stderr.ifBlank { res.stdout }}")
            }
        }
    }

    fun setFileFilter(filter: String) {
        _uiState.update { it.copy(fileFilter = filter) }
    }

    fun toggleDirectory(dir: File) {
        val path = dir.absolutePath
        _uiState.update { state ->
            val updated = if (path in state.expandedPaths) {
                state.expandedPaths - path
            } else {
                state.expandedPaths + path
            }
            state.copy(expandedPaths = updated)
        }
        refreshFileTree()
    }

    fun refreshFileTree() {
        val project = _uiState.value.activeProject ?: return
        val expanded = _uiState.value.expandedPaths
        viewModelScope.launch {
            val nodes = WorkspaceManager.flattenTree(project, expanded)
            val allFiles = WorkspaceManager.collectAllFilePaths(project)
            _uiState.update { it.copy(treeNodes = nodes, workspaceFiles = allFiles) }
        }
    }

    fun createFileOrFolder(parentDir: File, name: String, isDirectory: Boolean) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val target = File(parentDir, clean)
            if (isDirectory) {
                target.mkdirs()
            } else {
                target.parentFile?.mkdirs()
                if (!target.exists()) target.writeText("")
            }
            refreshFileTree()
            if (!isDirectory && target.exists()) {
                openFile(target, switchToEditor = true)
            }
        }
    }

    fun renameFileOrFolder(target: File, newName: String) {
        val clean = newName.trim()
        if (clean.isEmpty() || clean == target.name) return
        viewModelScope.launch(Dispatchers.IO) {
            val dest = File(target.parentFile, clean)
            if (target.renameTo(dest)) {
                _uiState.update { state ->
                    val updatedTabs = state.openTabs.map { tab ->
                        if (tab.path == target.absolutePath) tab.copy(file = dest) else tab
                    }
                    val newActive = if (state.activeTabPath == target.absolutePath) dest.absolutePath else state.activeTabPath
                    state.copy(openTabs = updatedTabs, activeTabPath = newActive)
                }
                refreshFileTree()
                refreshGit()
            }
        }
    }

    fun deleteFileOrFolder(target: File) {
        viewModelScope.launch(Dispatchers.IO) {
            val deleted = if (target.isDirectory) target.deleteRecursively() else target.delete()
            if (deleted) {
                _uiState.update { state ->
                    val remaining = state.openTabs.filterNot { it.path == target.absolutePath || it.path.startsWith(target.absolutePath + "/") }
                    val newActive = if (state.activeTabPath in remaining.map { it.path }) state.activeTabPath else remaining.lastOrNull()?.path
                    state.copy(openTabs = remaining, activeTabPath = newActive)
                }
                refreshFileTree()
                refreshGit()
            }
        }
    }

    // =========================================================================
    // Code Editor Tabs & Actions
    // =========================================================================

    fun openFile(file: File, line: Int? = null, switchToEditor: Boolean = true) {
        if (!file.exists() || file.isDirectory) return
        val existing = _uiState.value.openTabs.find { it.path == file.absolutePath }
        if (existing != null) {
            _uiState.update { state ->
                val tabs = if (line != null) {
                    state.openTabs.map {
                        if (it.path == existing.path) {
                            it.copy(cursorLine = (line - 1).coerceAtLeast(0), cursorColumn = 0, jumpRequestId = it.jumpRequestId + 1)
                        } else it
                    }
                } else state.openTabs
                state.copy(
                    openTabs = tabs,
                    activeTabPath = existing.path,
                    activePane = if (switchToEditor) IdePane.EDITOR else state.activePane
                )
            }
            return
        }

        viewModelScope.launch {
            val result = WorkspaceManager.readTextFile(file)
            result.onSuccess { text ->
                val tab = EditorTab(
                    file = file,
                    content = text,
                    savedContent = text,
                    cursorLine = ((line ?: 1) - 1).coerceAtLeast(0),
                    jumpRequestId = if (line != null) 1 else 0
                )
                _uiState.update { state ->
                    state.copy(
                        openTabs = state.openTabs + tab,
                        activeTabPath = tab.path,
                        activePane = if (switchToEditor) IdePane.EDITOR else state.activePane
                    )
                }
            }.onFailure { err ->
                showMessage(err.message ?: "Cannot open file")
            }
        }
    }

    fun openWorkspaceRelativePath(relOrAbs: String, line: Int? = null) {
        val project = _uiState.value.activeProject
        val candidate = if (relOrAbs.startsWith("/")) File(relOrAbs) else File(project, relOrAbs)
        if (candidate.exists() && candidate.isFile) {
            openFile(candidate, line = line, switchToEditor = true)
        } else {
            showMessage("File not found: $relOrAbs")
        }
    }

    fun selectTab(path: String) {
        _uiState.update { it.copy(activeTabPath = path) }
    }

    fun closeTab(path: String) {
        _uiState.update { state ->
            val remaining = state.openTabs.filterNot { it.path == path }
            val nextActive = if (state.activeTabPath == path) remaining.lastOrNull()?.path else state.activeTabPath
            state.copy(openTabs = remaining, activeTabPath = nextActive)
        }
    }

    fun updateActiveTabContent(path: String, newContent: String, line: Int, col: Int) {
        _uiState.update { state ->
            val updated = state.openTabs.map { tab ->
                if (tab.path == path) {
                    tab.copy(content = newContent, cursorLine = line, cursorColumn = col)
                } else tab
            }
            state.copy(openTabs = updated)
        }
    }

    fun updateCursorPosition(path: String, line: Int, col: Int) {
        _uiState.update { state ->
            val updated = state.openTabs.map { tab ->
                if (tab.path == path) tab.copy(cursorLine = line, cursorColumn = col) else tab
            }
            state.copy(openTabs = updated)
        }
    }

    fun saveActiveTab() {
        val tab = _uiState.value.openTabs.find { it.path == _uiState.value.activeTabPath } ?: return
        viewModelScope.launch {
            WorkspaceManager.writeTextFile(tab.file, tab.content).onSuccess {
                _uiState.update { state ->
                    state.copy(
                        openTabs = state.openTabs.map {
                            if (it.path == tab.path) it.copy(savedContent = it.content) else it
                        },
                        snackbarMessage = "Saved ${tab.title}"
                    )
                }
                refreshGit()
            }.onFailure { err ->
                showMessage("Save failed: ${err.message}")
            }
        }
    }

    fun saveAllDirtyTabs() {
        val dirty = _uiState.value.openTabs.filter { it.isDirty }
        if (dirty.isEmpty()) return
        viewModelScope.launch {
            dirty.forEach { tab -> WorkspaceManager.writeTextFile(tab.file, tab.content) }
            _uiState.update { state ->
                state.copy(
                    openTabs = state.openTabs.map { it.copy(savedContent = it.content) },
                    snackbarMessage = "Saved ${dirty.size} file(s)"
                )
            }
            refreshGit()
        }
    }

    fun showUnsavedDiffForActiveTab() {
        val tab = _uiState.value.openTabs.find { it.path == _uiState.value.activeTabPath } ?: return
        viewModelScope.launch(Dispatchers.Default) {
            val diff = WorkspaceManager.computeSimpleDiff(tab.savedContent, tab.content)
            _uiState.update { it.copy(diffModal = DiffModalState("Unsaved changes: ${tab.title}", diff)) }
        }
    }

    fun dismissDiffModal() {
        _uiState.update { it.copy(diffModal = null) }
    }

    fun toggleFindBar() {
        _uiState.update { it.copy(showFindBar = !it.showFindBar) }
    }

    fun setEditorTheme(themeName: String) {
        prefs.edit().putString("editor_theme", themeName).apply()
        _uiState.update { it.copy(editorTheme = themeName) }
    }

    fun changeEditorFontSize(delta: Float) {
        val next = (_uiState.value.editorFontSize + delta).coerceIn(10f, 24f)
        prefs.edit().putFloat("editor_font_size", next).apply()
        _uiState.update { it.copy(editorFontSize = next) }
    }

    fun toggleWordWrap() {
        val next = !_uiState.value.editorWordWrap
        prefs.edit().putBoolean("editor_word_wrap", next).apply()
        _uiState.update { it.copy(editorWordWrap = next) }
    }

    fun askAgentAboutActiveFile() {
        val tab = _uiState.value.openTabs.find { it.path == _uiState.value.activeTabPath } ?: return
        val project = _uiState.value.activeProject
        val relPath = if (project != null && tab.file.absolutePath.startsWith(project.absolutePath)) {
            tab.file.relativeTo(project).path
        } else tab.file.absolutePath
        val lineNum = tab.cursorLine + 1
        val snippet = "@$relPath (line $lineNum) "
        _uiState.update { state ->
            val current = state.composerText
            val updated = if (current.isBlank()) snippet else "$current\n$snippet"
            state.copy(composerText = updated, activePane = IdePane.CHAT)
        }
    }

    fun previewActiveHtmlFile() {
        val tab = _uiState.value.openTabs.find { it.path == _uiState.value.activeTabPath } ?: return
        if (tab.isDirty) saveActiveTab()
        _uiState.update {
            it.copy(
                previewUrl = "file://${tab.file.absolutePath}",
                previewReloadNonce = it.previewReloadNonce + 1,
                activePane = IdePane.PREVIEW
            )
        }
    }

    private fun syncOpenTabsFromDisk() {
        val tabs = _uiState.value.openTabs
        if (tabs.isEmpty()) return
        viewModelScope.launch {
            val refreshed = tabs.map { tab ->
                if (!tab.isDirty && tab.file.exists()) {
                    val fresh = WorkspaceManager.readTextFile(tab.file).getOrNull()
                    if (fresh != null && fresh != tab.savedContent) {
                        tab.copy(content = fresh, savedContent = fresh)
                    } else tab
                } else tab
            }
            _uiState.update { it.copy(openTabs = refreshed) }
        }
    }

    // =========================================================================
    // Git Operations
    // =========================================================================

    fun refreshGit() {
        val ctx = getApplication<Application>()
        val project = _uiState.value.activeProject ?: return
        viewModelScope.launch {
            val state = GitManager.inspectRepo(ctx, project)
            _uiState.update { it.copy(gitState = state) }
        }
    }

    fun updateGitCommitMsg(msg: String) {
        _uiState.update { it.copy(gitCommitMsg = msg) }
    }

    fun gitInit() {
        val ctx = getApplication<Application>()
        val project = _uiState.value.activeProject ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(gitBusy = true) }
            val res = GitManager.initRepo(ctx, project)
            _uiState.update { it.copy(gitBusy = false, gitMessage = res.stdout.ifBlank { res.stderr }) }
            refreshGit()
        }
    }

    fun gitStage(path: String) {
        val ctx = getApplication<Application>()
        val project = _uiState.value.activeProject ?: return
        viewModelScope.launch {
            GitManager.stageFile(ctx, project, path)
            refreshGit()
        }
    }

    fun gitStageAll() {
        val ctx = getApplication<Application>()
        val project = _uiState.value.activeProject ?: return
        viewModelScope.launch {
            GitManager.stageAll(ctx, project)
            refreshGit()
        }
    }

    fun gitUnstage(path: String) {
        val ctx = getApplication<Application>()
        val project = _uiState.value.activeProject ?: return
        viewModelScope.launch {
            GitManager.unstageFile(ctx, project, path)
            refreshGit()
        }
    }

    fun gitCommit() {
        val ctx = getApplication<Application>()
        val project = _uiState.value.activeProject ?: return
        val msg = _uiState.value.gitCommitMsg.trim()
        if (msg.isEmpty()) {
            showMessage("Enter a commit message first")
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(gitBusy = true) }
            val res = GitManager.commit(ctx, project, msg)
            _uiState.update {
                it.copy(
                    gitBusy = false,
                    gitCommitMsg = if (res.exitCode == 0) "" else it.gitCommitMsg,
                    gitMessage = if (res.exitCode == 0) "Committed: $msg" else res.stderr.ifBlank { res.stdout }
                )
            }
            refreshGit()
        }
    }

    fun gitPull() {
        val ctx = getApplication<Application>()
        val project = _uiState.value.activeProject ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(gitBusy = true) }
            val res = GitManager.pull(ctx, project)
            _uiState.update { it.copy(gitBusy = false, gitMessage = res.stdout.ifBlank { res.stderr }) }
            refreshFileTree()
            syncOpenTabsFromDisk()
            refreshGit()
        }
    }

    fun gitPush() {
        val ctx = getApplication<Application>()
        val project = _uiState.value.activeProject ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(gitBusy = true) }
            val res = GitManager.push(ctx, project)
            _uiState.update {
                it.copy(
                    gitBusy = false,
                    gitMessage = if (res.exitCode == 0) "Pushed to remote" else res.stderr.ifBlank { res.stdout }
                )
            }
            refreshGit()
        }
    }

    fun openGitDiff(path: String, staged: Boolean) {
        val ctx = getApplication<Application>()
        val project = _uiState.value.activeProject ?: return
        viewModelScope.launch {
            val raw = GitManager.diffFile(ctx, project, path, staged)
            val lines = raw.lines().map { line ->
                when {
                    line.startsWith("+++") || line.startsWith("---") || line.startsWith("@@") || line.startsWith("diff ") ->
                        DiffLine(com.agyide.engine.DiffType.HEADER, line)
                    line.startsWith("+") -> DiffLine(com.agyide.engine.DiffType.ADDED, line)
                    line.startsWith("-") -> DiffLine(com.agyide.engine.DiffType.REMOVED, line)
                    else -> DiffLine(com.agyide.engine.DiffType.CONTEXT, line)
                }
            }
            _uiState.update { it.copy(diffModal = DiffModalState("Git Diff: $path", lines, raw)) }
        }
    }

    // =========================================================================
    // Antigravity Agent Chat
    // =========================================================================

    fun refreshModels() {
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            val list = AgyAgentBridge.fetchModels(ctx, _uiState.value.apiKey)
            _uiState.update { it.copy(models = list) }
        }
    }

    fun refreshSavedConversations() {
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            val list = AgyAgentBridge.loadSavedConversations(ctx)
            _uiState.update { it.copy(savedConversations = list) }
        }
    }

    fun selectModel(modelId: String) {
        prefs.edit().putString("selected_model", modelId).apply()
        _uiState.update { it.copy(selectedModelId = modelId) }
    }

    fun selectEffort(effort: String) {
        prefs.edit().putString("selected_effort", effort).apply()
        _uiState.update { it.copy(selectedEffort = effort) }
    }

    fun selectMode(mode: String) {
        prefs.edit().putString("selected_mode", mode).apply()
        _uiState.update { it.copy(selectedMode = mode) }
    }

    fun updateComposerText(text: String) {
        _uiState.update { it.copy(composerText = text) }
    }

    fun appendVoiceTranscript(transcript: String) {
        if (transcript.isBlank()) return
        _uiState.update { state ->
            val current = state.composerText.trimEnd()
            val next = if (current.isEmpty()) transcript else "$current $transcript"
            state.copy(composerText = next)
        }
    }

    fun attachFileFromUri(uri: Uri) {
        val ctx = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val uploadDir = RuntimeEnv.uploadsDir(ctx).apply { mkdirs() }
                var name = "attachment_${System.currentTimeMillis()}"
                ctx.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) name = cursor.getString(idx) ?: name
                    }
                }
                val sanitized = name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                var dest = File(uploadDir, sanitized)
                if (dest.exists()) {
                    dest = File(uploadDir, "${System.currentTimeMillis()}_$sanitized")
                }
                ctx.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(dest).use { output -> input.copyTo(output) }
                }
                _uiState.update { state ->
                    state.copy(attachedPaths = state.attachedPaths + dest.absolutePath)
                }
            }.onFailure { err ->
                showMessage("Failed to attach file: ${err.message}")
            }
        }
    }

    fun removeAttachment(path: String) {
        _uiState.update { it.copy(attachedPaths = it.attachedPaths - path) }
    }

    fun startNewConversation() {
        if (_uiState.value.isAgentRunning) AgyAgentBridge.cancelActiveRun()
        _uiState.update {
            it.copy(
                activeConversationId = null,
                chatMessages = emptyList(),
                isAgentRunning = false
            )
        }
    }

    fun resumeSavedConversation(conv: SavedConversation) {
        if (_uiState.value.isAgentRunning) AgyAgentBridge.cancelActiveRun()
        val wsFile = conv.workspacePath?.let { File(it) }?.takeIf { it.exists() && it.isDirectory }
        if (wsFile != null) {
            selectProject(wsFile)
        }
        _uiState.update {
            it.copy(
                activeConversationId = conv.id,
                chatMessages = listOf(
                    ChatMessage(
                        isUser = false,
                        blocks = listOf(
                            TurnBlock.TextBlock(
                                id = UUID.randomUUID().toString(),
                                text = "Resumed session **${conv.title}** (`${conv.id.take(8)}...`). Send a prompt to continue this thread."
                            )
                        )
                    )
                ),
                activePane = IdePane.CHAT
            )
        }
    }

    fun cancelAgentRun() {
        AgyAgentBridge.cancelActiveRun()
        _uiState.update { state ->
            val updated = state.chatMessages.map { msg ->
                if (msg.isStreaming) {
                    msg.copy(
                        isStreaming = false,
                        errorText = "Cancelled by user",
                        blocks = msg.blocks.map { blk ->
                            if (blk is TurnBlock.ToolCallBlock && blk.isRunning) blk.copy(isRunning = false) else blk
                        }
                    )
                } else msg
            }
            state.copy(isAgentRunning = false, chatMessages = updated)
        }
    }

    fun sendPrompt() {
        val state = _uiState.value
        val rawText = state.composerText.trim()
        if ((rawText.isEmpty() && state.attachedPaths.isEmpty()) || state.isAgentRunning) return

        val ctx = getApplication<Application>()
        if (!RuntimeEnv.hasAuthCredentials(ctx, state.apiKey)) {
            _uiState.update { it.copy(showAuthDialog = true) }
            return
        }

        val workspace = state.activeProject ?: RuntimeEnv.projectsDir(ctx)
        val promptToSend = buildString {
            if (state.attachedPaths.isNotEmpty()) {
                append("Attached files:\n")
                state.attachedPaths.forEach { append("- ").append(it).append('\n') }
                append('\n')
            }
            append(rawText)
        }.trim()

        val userMsg = ChatMessage(
            isUser = true,
            userText = rawText,
            attachments = state.attachedPaths
        )
        val assistantMsgId = UUID.randomUUID().toString()
        val assistantPlaceholder = ChatMessage(
            id = assistantMsgId,
            isUser = false,
            isStreaming = true
        )

        _uiState.update {
            it.copy(
                composerText = "",
                attachedPaths = emptyList(),
                isAgentRunning = true,
                chatMessages = it.chatMessages + userMsg + assistantPlaceholder
            )
        }

        viewModelScope.launch {
            AgyAgentBridge.runPromptStream(
                context = ctx,
                prompt = promptToSend,
                workspaceDir = workspace,
                conversationId = state.activeConversationId,
                modelId = state.selectedModelId,
                effort = state.selectedEffort,
                mode = state.selectedMode,
                apiKey = state.apiKey
            ).collect { event ->
                handleStreamEvent(assistantMsgId, event)
            }

            // Ensure streaming flag is cleared on stream end
            _uiState.update { s ->
                s.copy(
                    isAgentRunning = false,
                    chatMessages = s.chatMessages.map { msg ->
                        if (msg.id == assistantMsgId && msg.isStreaming) {
                            msg.copy(
                                isStreaming = false,
                                blocks = msg.blocks.map { b ->
                                    if (b is TurnBlock.ToolCallBlock && b.isRunning) b.copy(isRunning = false) else b
                                }
                            )
                        } else msg
                    }
                )
            }
            refreshFileTree()
            syncOpenTabsFromDisk()
            refreshGit()
            refreshSavedConversations()
            refreshAuthStatus()
        }
    }

    private fun handleStreamEvent(assistantMsgId: String, event: AgyStreamEvent) {
        _uiState.update { state ->
            var nextConvId = state.activeConversationId
            val updatedMessages = state.chatMessages.map { msg ->
                if (msg.id != assistantMsgId) return@map msg
                when (event) {
                    is AgyStreamEvent.Init -> {
                        if (event.conversationId.isNotBlank()) nextConvId = event.conversationId
                        msg
                    }

                    is AgyStreamEvent.StepUpdate -> {
                        if (event.conversationId.isNotBlank()) nextConvId = event.conversationId
                        val mutableBlocks = msg.blocks.toMutableList()
                        when (event.stepType) {
                            "agent_response" -> {
                                val delta = event.textDelta ?: ""
                                if (delta.isNotEmpty()) {
                                    val blockId = "text_${event.stepIndex}"
                                    val existingIdx = mutableBlocks.indexOfFirst { it.id == blockId }
                                    if (existingIdx >= 0) {
                                        val prev = mutableBlocks[existingIdx] as TurnBlock.TextBlock
                                        mutableBlocks[existingIdx] = prev.copy(text = prev.text + delta)
                                    } else {
                                        mutableBlocks += TurnBlock.TextBlock(blockId, delta)
                                    }
                                }
                            }

                            "tool" -> {
                                val blockId = "tool_${event.stepIndex}"
                                val existingIdx = mutableBlocks.indexOfFirst { it.id == blockId }
                                val toolBlock = TurnBlock.ToolCallBlock(
                                    id = blockId,
                                    stepIndex = event.stepIndex,
                                    toolName = event.toolName ?: "tool",
                                    paramsSummary = event.toolParamsPretty ?: "",
                                    output = event.toolOutput,
                                    isRunning = event.state == "ACTIVE",
                                    durationSeconds = event.durationSeconds
                                )
                                if (existingIdx >= 0) {
                                    mutableBlocks[existingIdx] = toolBlock
                                } else {
                                    mutableBlocks += toolBlock
                                }
                            }
                        }
                        msg.copy(blocks = mutableBlocks)
                    }

                    is AgyStreamEvent.Result -> {
                        if (event.conversationId.isNotBlank()) nextConvId = event.conversationId
                        val hasText = msg.blocks.any { it is TurnBlock.TextBlock && it.text.isNotBlank() }
                        val finalBlocks = if (!hasText && event.response.isNotBlank()) {
                            msg.blocks + TurnBlock.TextBlock("result_text", event.response)
                        } else msg.blocks
                        msg.copy(
                            blocks = finalBlocks,
                            isStreaming = false,
                            errorText = event.error?.takeIf { it.isNotBlank() },
                            durationSeconds = event.durationSeconds,
                            totalTokens = event.totalTokens
                        )
                    }

                    is AgyStreamEvent.LogLine -> {
                        if (event.isError) {
                            val combined = listOfNotNull(msg.errorText, event.text).joinToString("\n")
                            msg.copy(errorText = combined)
                        } else msg
                    }
                }
            }
            state.copy(
                activeConversationId = nextConvId,
                chatMessages = updatedMessages
            )
        }
    }

    // =========================================================================
    // Embedded Terminal Sessions
    // =========================================================================

    fun ensureInitialTerminalSession(client: TerminalSessionClient, onUpdate: () -> Unit) {
        activeTerminalClient = client
        terminalScreenCallback = onUpdate
        if (terminalTabs.isEmpty() && _uiState.value.isReady) {
            createTerminalTab("Bash")
        } else {
            terminalTabs.forEach { it.session.updateTerminalSessionClient(client) }
        }
    }

    fun createTerminalTab(titlePrefix: String = "Shell", initialCommand: String? = null) {
        val ctx = getApplication<Application>()
        val client = activeTerminalClient ?: return
        val bash = RuntimeEnv.bashFile(ctx)
        if (!bash.exists()) return
        val cwd = _uiState.value.activeProject ?: RuntimeEnv.projectsDir(ctx)
        if (!cwd.exists()) cwd.mkdirs()

        val session = TerminalSession(
            bash.absolutePath,
            cwd.absolutePath,
            arrayOf("-l"),
            RuntimeEnv.envArray(ctx, _uiState.value.apiKey),
            2000,
            client
        )
        val label = "$titlePrefix ${terminalTabs.size + 1}"
        terminalTabs += TerminalTabState(title = label, session = session)
        _activeTerminalIndex.value = terminalTabs.lastIndex
        _terminalTabVersion.update { it + 1 }

        if (!initialCommand.isNullOrBlank()) {
            viewModelScope.launch {
                kotlinx.coroutines.delay(450)
                session.write("$initialCommand\n")
            }
        }
    }

    fun selectTerminalTab(index: Int) {
        if (index in terminalTabs.indices) {
            _activeTerminalIndex.value = index
        }
    }

    fun closeTerminalTab(index: Int) {
        if (index !in terminalTabs.indices) return
        val removed = terminalTabs.removeAt(index)
        runCatching { removed.session.finishIfRunning() }
        if (terminalTabs.isEmpty()) {
            createTerminalTab("Shell")
        } else {
            _activeTerminalIndex.value = index.coerceAtMost(terminalTabs.lastIndex)
            _terminalTabVersion.update { it + 1 }
        }
    }

    fun sendCommandToTerminal(command: String) {
        val current = terminalTabs.getOrNull(_activeTerminalIndex.value)
        if (current != null) {
            current.session.write("$command\n")
            terminalScreenCallback?.invoke()
        } else {
            createTerminalTab("CLI", initialCommand = command)
        }
    }

    fun notifyTerminalUpdated() {
        terminalScreenCallback?.invoke()
    }

    // =========================================================================
    // Preview & Device Control
    // =========================================================================

    fun setPreviewUrl(url: String) {
        val normalized = if (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("file://")) {
            url
        } else "http://$url"
        _uiState.update {
            it.copy(
                previewUrl = normalized,
                previewReloadNonce = it.previewReloadNonce + 1
            )
        }
    }

    fun reloadPreview() {
        _uiState.update { it.copy(previewReloadNonce = it.previewReloadNonce + 1) }
    }

    fun runDeviceCommand(vararg args: String) {
        val ctx = getApplication<Application>()
        val script = File(RuntimeEnv.usrDir(ctx), "bin/agy-device")
        viewModelScope.launch {
            _uiState.update { it.copy(deviceBusy = true) }
            val res = withContext(Dispatchers.IO) {
                RuntimeEnv.runBinary(
                    context = ctx,
                    command = listOf(RuntimeEnv.bashFile(ctx).absolutePath, script.absolutePath, *args),
                    timeoutSeconds = 30
                )
            }
            val combined = buildString {
                if (res.stdout.isNotBlank()) append(res.stdout.trim())
                if (res.stderr.isNotBlank()) {
                    if (isNotEmpty()) append('\n')
                    append(res.stderr.trim())
                }
            }.ifBlank { "Exit code: ${res.exitCode}" }
            _uiState.update { it.copy(deviceBusy = false, deviceLog = combined) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        AgyAgentBridge.cancelActiveRun()
        terminalTabs.forEach { runCatching { it.session.finishIfRunning() } }
    }
}
