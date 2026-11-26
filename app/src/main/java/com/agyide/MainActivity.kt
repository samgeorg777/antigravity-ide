package com.agyide

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agyide.engine.RuntimeEnv
import com.agyide.ui.IdePane
import com.agyide.ui.IdeUiState
import com.agyide.ui.IdeViewModel
import com.agyide.ui.components.*
import com.agyide.ui.theme.AntigravityIdeTheme
import com.agyide.ui.theme.CosmicColors
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: IdeViewModel by viewModels()

    override fun onResume() {
        super.onResume()
        if (viewModel.uiState.value.isReady) {
            viewModel.refreshAuthStatus()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AntigravityIdeTheme {
                val uiState by viewModel.uiState.collectAsState()
                val snackbarHostState = remember { SnackbarHostState() }

                val filePickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenMultipleDocuments()
                ) { uris ->
                    uris.forEach { viewModel.attachFileFromUri(it) }
                }

                val voiceLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
                ) { result ->
                    if (result.resultCode == Activity.RESULT_OK) {
                        val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                        val text = matches?.firstOrNull()
                        if (!text.isNullOrBlank()) {
                            viewModel.appendVoiceTranscript(text)
                        }
                    }
                }

                LaunchedEffect(uiState.snackbarMessage) {
                    val msg = uiState.snackbarMessage ?: return@LaunchedEffect
                    snackbarHostState.showSnackbar(msg)
                    viewModel.clearSnackbar()
                }

                if (!uiState.isReady) {
                    SetupProgressScreen(
                        progress = uiState.bootstrapProgress,
                        onRetry = { viewModel.startBootstrap() }
                    )
                } else {
                    IdeWorkspaceRoot(
                        uiState = uiState,
                        viewModel = viewModel,
                        snackbarHostState = snackbarHostState,
                        onPickAttachment = {
                            filePickerLauncher.launch(arrayOf("*/*"))
                        },
                        onVoiceInput = {
                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your prompt to Antigravity")
                            }
                            runCatching { voiceLauncher.launch(intent) }
                                .onFailure { viewModel.showMessage("Voice input unavailable on this device") }
                        }
                    )
                }

                if (uiState.showAuthDialog) {
                    AuthSetupDialog(
                        currentApiKey = uiState.apiKey,
                        hasOAuthToken = RuntimeEnv.hasAuthCredentials(this, null),
                        onSaveApiKey = { viewModel.saveApiKey(it) },
                        onLaunchGoogleLogin = { viewModel.launchInteractiveLoginInTerminal() },
                        onDismiss = { viewModel.setShowAuthDialog(false) }
                    )
                }

                uiState.diffModal?.let { modal ->
                    DiffViewerModal(modal = modal, onDismiss = { viewModel.dismissDiffModal() })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IdeWorkspaceRoot(
    uiState: IdeUiState,
    viewModel: IdeViewModel,
    snackbarHostState: SnackbarHostState,
    onPickAttachment: () -> Unit,
    onVoiceInput: () -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(CosmicColors.Background)
    ) {
        val isWideTablet = maxWidth >= 840.dp

        if (isWideTablet) {
            TabletDesktopIdeLayout(
                uiState = uiState,
                viewModel = viewModel,
                snackbarHostState = snackbarHostState,
                onPickAttachment = onPickAttachment,
                onVoiceInput = onVoiceInput
            )
        } else {
            PhoneCompactIdeLayout(
                uiState = uiState,
                viewModel = viewModel,
                snackbarHostState = snackbarHostState,
                onPickAttachment = onPickAttachment,
                onVoiceInput = onVoiceInput
            )
        }
    }
}

@Composable
private fun TabletDesktopIdeLayout(
    uiState: IdeUiState,
    viewModel: IdeViewModel,
    snackbarHostState: SnackbarHostState,
    onPickAttachment: () -> Unit,
    onVoiceInput: () -> Unit
) {
    var showLeftSidebar by remember { mutableStateOf(true) }
    var showBottomDrawer by remember { mutableStateOf(true) }
    var bottomTab by remember { mutableIntStateOf(0) } // 0 = Terminal, 1 = Preview

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = CosmicColors.Background,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CosmicColors.Surface)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { showLeftSidebar = !showLeftSidebar }, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.Menu, contentDescription = "Toggle Explorer", tint = if (showLeftSidebar) CosmicColors.Cyan else CosmicColors.TextSecondary)
                }
                Spacer(Modifier.width(6.dp))
                Text("▲ ANTIGRAVITY IDE", color = CosmicColors.Cyan, fontWeight = FontWeight.Bold, fontSize = 14.sp, letterSpacing = 1.sp)
                Spacer(Modifier.width(12.dp))
                Text(
                    text = uiState.activeProject?.name ?: "Workspace",
                    color = CosmicColors.TextPrimary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (uiState.gitState.isRepo) {
                    Spacer(Modifier.width(8.dp))
                    Text("(${uiState.gitState.branch})", color = CosmicColors.Violet, fontSize = 12.sp)
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { viewModel.saveAllDirtyTabs() }) {
                    Text("Save All", color = CosmicColors.Amber, fontSize = 12.sp)
                }
                IconButton(onClick = { showBottomDrawer = !showBottomDrawer }, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.Terminal, contentDescription = "Toggle Terminal", tint = if (showBottomDrawer) CosmicColors.Cyan else CosmicColors.TextSecondary)
                }
                IconButton(onClick = { viewModel.setShowAuthDialog(true) }, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.Key, contentDescription = "Auth Settings", tint = if (uiState.hasAuth) CosmicColors.Green else CosmicColors.Amber)
                }
            }
        }
    ) { padding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (showLeftSidebar) {
                Box(
                    modifier = Modifier
                        .width(270.dp)
                        .fillMaxHeight()
                        .border(1.dp, CosmicColors.SurfaceBorder)
                ) {
                    RenderExplorerPane(uiState, viewModel)
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    RenderEditorPane(uiState, viewModel)
                }

                if (showBottomDrawer) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(250.dp)
                            .border(1.dp, CosmicColors.SurfaceBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(CosmicColors.SurfaceElevated)
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { bottomTab = 0 }) {
                                Text("Terminal", color = if (bottomTab == 0) CosmicColors.Cyan else CosmicColors.TextSecondary, fontSize = 12.sp)
                            }
                            TextButton(onClick = { bottomTab = 1 }) {
                                Text("Preview & Device", color = if (bottomTab == 1) CosmicColors.Cyan else CosmicColors.TextSecondary, fontSize = 12.sp)
                            }
                        }
                        if (bottomTab == 0) {
                            TerminalPane(
                                viewModel = viewModel,
                                onOpenUrlInPreview = { url ->
                                    viewModel.setPreviewUrl(url)
                                    bottomTab = 1
                                }
                            )
                        } else {
                            RenderPreviewPane(uiState, viewModel)
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .width(360.dp)
                    .fillMaxHeight()
                    .border(1.dp, CosmicColors.SurfaceBorder)
            ) {
                RenderChatPane(uiState, viewModel, onPickAttachment, onVoiceInput)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhoneCompactIdeLayout(
    uiState: IdeUiState,
    viewModel: IdeViewModel,
    snackbarHostState: SnackbarHostState,
    onPickAttachment: () -> Unit,
    onVoiceInput: () -> Unit
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = uiState.activePane != IdePane.EDITOR,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = CosmicColors.Surface) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("▲ ANTIGRAVITY IDE", color = CosmicColors.Cyan, fontWeight = FontWeight.Bold, fontSize = 17.sp, letterSpacing = 1.sp)
                    Text(
                        text = "Workspace: ${uiState.activeProject?.name ?: "-"}",
                        color = CosmicColors.TextSecondary,
                        fontSize = 12.sp
                    )
                    HorizontalDivider(color = CosmicColors.SurfaceBorder)

                    uiState.projects.forEach { proj ->
                        val active = proj.absolutePath == uiState.activeProject?.absolutePath
                        NavigationDrawerItem(
                            label = { Text(proj.name) },
                            selected = active,
                            onClick = {
                                viewModel.selectProject(proj)
                                scope.launch { drawerState.close() }
                            },
                            icon = { Icon(Icons.Default.Folder, contentDescription = null) }
                        )
                    }

                    HorizontalDivider(color = CosmicColors.SurfaceBorder)

                    NavigationDrawerItem(
                        label = { Text("Auth & API Key") },
                        selected = false,
                        onClick = {
                            scope.launch { drawerState.close() }
                            viewModel.setShowAuthDialog(true)
                        },
                        icon = { Icon(Icons.Default.Key, contentDescription = null, tint = CosmicColors.Cyan) }
                    )
                    NavigationDrawerItem(
                        label = { Text("Save All Dirty Files") },
                        selected = false,
                        onClick = {
                            viewModel.saveAllDirtyTabs()
                            scope.launch { drawerState.close() }
                        },
                        icon = { Icon(Icons.Default.Save, contentDescription = null, tint = CosmicColors.Amber) }
                    )
                }
            }
        }
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = CosmicColors.Background,
            topBar = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CosmicColors.Surface)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { scope.launch { drawerState.open() } }, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu", tint = CosmicColors.Cyan)
                    }
                    Spacer(Modifier.width(6.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = uiState.activeProject?.name ?: "Antigravity IDE",
                            color = CosmicColors.TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val sub = buildList {
                            if (uiState.gitState.isRepo) add("git:${uiState.gitState.branch}")
                            uiState.openTabs.find { it.path == uiState.activeTabPath }?.let { add(it.title) }
                        }.joinToString(" · ")
                        if (sub.isNotEmpty()) {
                            Text(sub, color = CosmicColors.CyanMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    IconButton(onClick = { viewModel.setShowAuthDialog(true) }, modifier = Modifier.size(34.dp)) {
                        Icon(
                            Icons.Default.Key,
                            contentDescription = "Auth",
                            tint = if (uiState.hasAuth) CosmicColors.Green else CosmicColors.Amber,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            },
            bottomBar = {
                NavigationBar(
                    containerColor = CosmicColors.Surface,
                    tonalElevation = 0.dp
                ) {
                    val items = listOf(
                        Triple(IdePane.CHAT, "Agent", Icons.Default.AutoAwesome),
                        Triple(IdePane.EDITOR, "Code", Icons.Default.Code),
                        Triple(IdePane.EXPLORER, "Files", Icons.Default.Folder),
                        Triple(IdePane.TERMINAL, "Term", Icons.Default.Terminal),
                        Triple(IdePane.PREVIEW, "Preview", Icons.Default.Web)
                    )
                    items.forEach { (pane, label, icon) ->
                        NavigationBarItem(
                            selected = uiState.activePane == pane,
                            onClick = { viewModel.selectPane(pane) },
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label, fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = CosmicColors.Cyan,
                                selectedTextColor = CosmicColors.Cyan,
                                indicatorColor = CosmicColors.SurfaceElevated,
                                unselectedIconColor = CosmicColors.TextMuted,
                                unselectedTextColor = CosmicColors.TextMuted
                            )
                        )
                    }
                }
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                when (uiState.activePane) {
                    IdePane.CHAT -> RenderChatPane(uiState, viewModel, onPickAttachment, onVoiceInput)
                    IdePane.EDITOR -> RenderEditorPane(uiState, viewModel)
                    IdePane.EXPLORER -> RenderExplorerPane(uiState, viewModel)
                    IdePane.TERMINAL -> TerminalPane(
                        viewModel = viewModel,
                        onOpenUrlInPreview = { url ->
                            viewModel.setPreviewUrl(url)
                            viewModel.selectPane(IdePane.PREVIEW)
                        }
                    )
                    IdePane.PREVIEW -> RenderPreviewPane(uiState, viewModel)
                }
            }
        }
    }
}

@Composable
private fun RenderChatPane(
    uiState: IdeUiState,
    viewModel: IdeViewModel,
    onPickAttachment: () -> Unit,
    onVoiceInput: () -> Unit
) {
    AgentChatPane(
        messages = uiState.chatMessages,
        activeConversationId = uiState.activeConversationId,
        savedConversations = uiState.savedConversations,
        models = uiState.models,
        selectedModelId = uiState.selectedModelId,
        selectedEffort = uiState.selectedEffort,
        selectedMode = uiState.selectedMode,
        isRunning = uiState.isAgentRunning,
        composerText = uiState.composerText,
        attachedPaths = uiState.attachedPaths,
        workspaceFiles = uiState.workspaceFiles,
        onComposerChange = { viewModel.updateComposerText(it) },
        onSend = { viewModel.sendPrompt() },
        onCancel = { viewModel.cancelAgentRun() },
        onNewChat = { viewModel.startNewConversation() },
        onResumeConversation = { viewModel.resumeSavedConversation(it) },
        onRefreshConversations = { viewModel.refreshSavedConversations() },
        onSelectModel = { viewModel.selectModel(it) },
        onSelectEffort = { viewModel.selectEffort(it) },
        onSelectMode = { viewModel.selectMode(it) },
        onPickAttachment = onPickAttachment,
        onRemoveAttachment = { viewModel.removeAttachment(it) },
        onVoiceInput = onVoiceInput,
        onOpenPath = { path, line -> viewModel.openWorkspaceRelativePath(path, line) }
    )
}

@Composable
private fun RenderEditorPane(uiState: IdeUiState, viewModel: IdeViewModel) {
    CodeEditorPane(
        openTabs = uiState.openTabs,
        activeTabPath = uiState.activeTabPath,
        editorTheme = uiState.editorTheme,
        fontSize = uiState.editorFontSize,
        wordWrap = uiState.editorWordWrap,
        showFindBar = uiState.showFindBar,
        onSelectTab = { viewModel.selectTab(it) },
        onCloseTab = { viewModel.closeTab(it) },
        onContentChanged = { path, content, line, col -> viewModel.updateActiveTabContent(path, content, line, col) },
        onCursorChanged = { path, line, col -> viewModel.updateCursorPosition(path, line, col) },
        onSave = { viewModel.saveActiveTab() },
        onShowDiff = { viewModel.showUnsavedDiffForActiveTab() },
        onAskAgent = { viewModel.askAgentAboutActiveFile() },
        onPreviewHtml = { viewModel.previewActiveHtmlFile() },
        onToggleFind = { viewModel.toggleFindBar() },
        onToggleWrap = { viewModel.toggleWordWrap() },
        onChangeFontSize = { viewModel.changeEditorFontSize(it) },
        onSelectTheme = { viewModel.setEditorTheme(it) },
        onOpenFilesPane = { viewModel.selectPane(IdePane.EXPLORER) }
    )
}

@Composable
private fun RenderExplorerPane(uiState: IdeUiState, viewModel: IdeViewModel) {
    ExplorerAndGitPane(
        projects = uiState.projects,
        activeProject = uiState.activeProject,
        treeNodes = uiState.treeNodes,
        expandedPaths = uiState.expandedPaths,
        fileFilter = uiState.fileFilter,
        gitState = uiState.gitState,
        gitCommitMsg = uiState.gitCommitMsg,
        gitBusy = uiState.gitBusy,
        gitMessage = uiState.gitMessage,
        onSelectProject = { viewModel.selectProject(it) },
        onCreateProject = { viewModel.createNewProject(it) },
        onCloneRepo = { url, name -> viewModel.cloneGitProject(url, name) },
        onFilterChanged = { viewModel.setFileFilter(it) },
        onToggleDir = { viewModel.toggleDirectory(it) },
        onOpenFile = { viewModel.openFile(it) },
        onCreateItem = { dir, name, isDir -> viewModel.createFileOrFolder(dir, name, isDir) },
        onRenameItem = { target, name -> viewModel.renameFileOrFolder(target, name) },
        onDeleteItem = { viewModel.deleteFileOrFolder(it) },
        onRefreshTree = { viewModel.refreshFileTree() },
        onGitInit = { viewModel.gitInit() },
        onGitStage = { viewModel.gitStage(it) },
        onGitStageAll = { viewModel.gitStageAll() },
        onGitUnstage = { viewModel.gitUnstage(it) },
        onGitCommitMsgChange = { viewModel.updateGitCommitMsg(it) },
        onGitCommit = { viewModel.gitCommit() },
        onGitPull = { viewModel.gitPull() },
        onGitPush = { viewModel.gitPush() },
        onOpenGitDiff = { path, staged -> viewModel.openGitDiff(path, staged) },
        onRefreshGit = { viewModel.refreshGit() }
    )
}

@Composable
private fun RenderPreviewPane(uiState: IdeUiState, viewModel: IdeViewModel) {
    PreviewAndToolsPane(
        previewUrl = uiState.previewUrl,
        reloadNonce = uiState.previewReloadNonce,
        deviceLog = uiState.deviceLog,
        deviceBusy = uiState.deviceBusy,
        hasAuth = uiState.hasAuth,
        onChangeUrl = { viewModel.setPreviewUrl(it) },
        onReload = { viewModel.reloadPreview() },
        onRunDeviceCmd = { args -> viewModel.runDeviceCommand(*args) },
        onOpenAuthSettings = { viewModel.setShowAuthDialog(true) }
    )
}
