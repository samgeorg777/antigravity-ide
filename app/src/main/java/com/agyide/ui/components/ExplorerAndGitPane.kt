package com.agyide.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agyide.engine.GitRepoState
import com.agyide.engine.WorkspaceNode
import com.agyide.ui.theme.CosmicColors
import java.io.File

@Composable
fun ExplorerAndGitPane(
    projects: List<File>,
    activeProject: File?,
    treeNodes: List<WorkspaceNode>,
    expandedPaths: Set<String>,
    fileFilter: String,
    gitState: GitRepoState,
    gitCommitMsg: String,
    gitBusy: Boolean,
    gitMessage: String?,
    onSelectProject: (File) -> Unit,
    onCreateProject: (String) -> Unit,
    onCloneRepo: (String, String) -> Unit,
    onFilterChanged: (String) -> Unit,
    onToggleDir: (File) -> Unit,
    onOpenFile: (File) -> Unit,
    onCreateItem: (File, String, Boolean) -> Unit,
    onRenameItem: (File, String) -> Unit,
    onDeleteItem: (File) -> Unit,
    onRefreshTree: () -> Unit,
    onGitInit: () -> Unit,
    onGitStage: (String) -> Unit,
    onGitStageAll: () -> Unit,
    onGitUnstage: (String) -> Unit,
    onGitCommitMsgChange: (String) -> Unit,
    onGitCommit: () -> Unit,
    onGitPull: () -> Unit,
    onGitPush: () -> Unit,
    onOpenGitDiff: (String, Boolean) -> Unit,
    onRefreshGit: () -> Unit,
    modifier: Modifier = Modifier
) {
    var subTab by remember { mutableIntStateOf(0) } // 0 = Files, 1 = Git
    var showProjectMenu by remember { mutableStateOf(false) }
    var showNewProjectDialog by remember { mutableStateOf(false) }
    var showCloneDialog by remember { mutableStateOf(false) }
    var createTargetDir by remember { mutableStateOf<File?>(null) }
    var createIsDir by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<File?>(null) }
    var deleteTarget by remember { mutableStateOf<File?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CosmicColors.Background)
    ) {
        // Workspace Header & Switcher
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CosmicColors.Surface)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(CosmicColors.SurfaceElevated)
                        .border(1.dp, CosmicColors.SurfaceBorder, RoundedCornerShape(8.dp))
                        .clickable { showProjectMenu = true }
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.FolderSpecial, contentDescription = null, tint = CosmicColors.Cyan, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = activeProject?.name ?: "Select Workspace",
                        color = CosmicColors.TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = CosmicColors.TextSecondary)
                }

                DropdownMenu(expanded = showProjectMenu, onDismissRequest = { showProjectMenu = false }) {
                    projects.forEach { proj ->
                        DropdownMenuItem(
                            text = { Text(if (proj.absolutePath == activeProject?.absolutePath) "✓ ${proj.name}" else proj.name) },
                            onClick = {
                                showProjectMenu = false
                                onSelectProject(proj)
                            }
                        )
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Default.CreateNewFolder, contentDescription = null) },
                        text = { Text("New Workspace") },
                        onClick = {
                            showProjectMenu = false
                            showNewProjectDialog = true
                        }
                    )
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Default.CloudDownload, contentDescription = null) },
                        text = { Text("Clone Git Repo") },
                        onClick = {
                            showProjectMenu = false
                            showCloneDialog = true
                        }
                    )
                }
            }

            IconButton(
                onClick = { activeProject?.let { createTargetDir = it; createIsDir = false } },
                modifier = Modifier.size(34.dp)
            ) {
                Icon(Icons.Default.NoteAdd, contentDescription = "New File", tint = CosmicColors.Cyan, modifier = Modifier.size(18.dp))
            }
            IconButton(
                onClick = { activeProject?.let { createTargetDir = it; createIsDir = true } },
                modifier = Modifier.size(34.dp)
            ) {
                Icon(Icons.Default.CreateNewFolder, contentDescription = "New Folder", tint = CosmicColors.Violet, modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = { onRefreshTree(); onRefreshGit() }, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = CosmicColors.TextSecondary, modifier = Modifier.size(18.dp))
            }
        }

        // Files / Git Sub-tabs
        TabRow(
            selectedTabIndex = subTab,
            containerColor = CosmicColors.Surface,
            contentColor = CosmicColors.Cyan
        ) {
            Tab(selected = subTab == 0, onClick = { subTab = 0 }, text = { Text("Files", fontSize = 12.sp) })
            Tab(
                selected = subTab == 1,
                onClick = { subTab = 1; onRefreshGit() },
                text = {
                    val count = gitState.changes.size
                    Text(if (count > 0) "Git ($count)" else "Git", fontSize = 12.sp)
                }
            )
        }

        if (subTab == 0) {
            // File Search Filter
            OutlinedTextField(
                value = fileFilter,
                onValueChange = onFilterChanged,
                placeholder = { Text("Filter files...", fontSize = 12.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )

            val filtered = remember(treeNodes, fileFilter) {
                if (fileFilter.isBlank()) treeNodes
                else treeNodes.filter { it.name.contains(fileFilter, ignoreCase = true) }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 6.dp)
            ) {
                items(filtered, key = { it.file.absolutePath }) { node ->
                    var showItemMenu by remember { mutableStateOf(false) }
                    val isExpanded = expandedPaths.contains(node.file.absolutePath)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .clickable {
                                if (node.isDirectory) onToggleDir(node.file) else onOpenFile(node.file)
                            }
                            .padding(
                                start = (8 + node.depth * 14).dp,
                                end = 4.dp,
                                top = 6.dp,
                                bottom = 6.dp
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = when {
                                node.isDirectory && isExpanded -> Icons.Default.KeyboardArrowDown
                                node.isDirectory -> Icons.Default.KeyboardArrowRight
                                else -> Icons.Default.Description
                            },
                            contentDescription = null,
                            tint = if (node.isDirectory) CosmicColors.Cyan else fileColor(node.name),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        if (node.isDirectory) {
                            Icon(
                                Icons.Default.Folder,
                                contentDescription = null,
                                tint = CosmicColors.Amber,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(
                            text = node.name,
                            color = CosmicColors.TextPrimary,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        Box {
                            IconButton(onClick = { showItemMenu = true }, modifier = Modifier.size(26.dp)) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Actions", tint = CosmicColors.TextMuted, modifier = Modifier.size(15.dp))
                            }
                            DropdownMenu(expanded = showItemMenu, onDismissRequest = { showItemMenu = false }) {
                                if (node.isDirectory) {
                                    DropdownMenuItem(
                                        text = { Text("New File Here") },
                                        onClick = { showItemMenu = false; createTargetDir = node.file; createIsDir = false }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("New Folder Here") },
                                        onClick = { showItemMenu = false; createTargetDir = node.file; createIsDir = true }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("Rename") },
                                    onClick = { showItemMenu = false; renameTarget = node.file }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete", color = CosmicColors.Red) },
                                    onClick = { showItemMenu = false; deleteTarget = node.file }
                                )
                            }
                        }
                    }
                }
            }
        } else {
            GitPanel(
                gitState = gitState,
                commitMsg = gitCommitMsg,
                busy = gitBusy,
                statusMsg = gitMessage,
                onInit = onGitInit,
                onStage = onGitStage,
                onStageAll = onGitStageAll,
                onUnstage = onGitUnstage,
                onCommitMsgChange = onGitCommitMsgChange,
                onCommit = onGitCommit,
                onPull = onGitPull,
                onPush = onGitPush,
                onOpenDiff = onOpenGitDiff,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        }
    }

    // Dialogs
    if (showNewProjectDialog) {
        PromptInputDialog(
            title = "New Workspace",
            label = "Project folder name",
            initialValue = "",
            onConfirm = { name -> showNewProjectDialog = false; onCreateProject(name) },
            onDismiss = { showNewProjectDialog = false }
        )
    }

    if (showCloneDialog) {
        CloneRepoDialog(
            onConfirm = { url, name -> showCloneDialog = false; onCloneRepo(url, name) },
            onDismiss = { showCloneDialog = false }
        )
    }

    createTargetDir?.let { dir ->
        PromptInputDialog(
            title = if (createIsDir) "New Folder" else "New File",
            label = if (createIsDir) "Folder name" else "File name (e.g. Main.kt)",
            initialValue = "",
            onConfirm = { name ->
                createTargetDir = null
                onCreateItem(dir, name, createIsDir)
            },
            onDismiss = { createTargetDir = null }
        )
    }

    renameTarget?.let { target ->
        PromptInputDialog(
            title = "Rename ${target.name}",
            label = "New name",
            initialValue = target.name,
            onConfirm = { name ->
                renameTarget = null
                onRenameItem(target, name)
            },
            onDismiss = { renameTarget = null }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor = CosmicColors.Surface,
            title = { Text("Delete ${target.name}?", color = CosmicColors.Red) },
            text = { Text("This permanently deletes the item from disk.", color = CosmicColors.TextSecondary) },
            confirmButton = {
                Button(
                    onClick = { deleteTarget = null; onDeleteItem(target) },
                    colors = ButtonDefaults.buttonColors(containerColor = CosmicColors.Red)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun GitPanel(
    gitState: GitRepoState,
    commitMsg: String,
    busy: Boolean,
    statusMsg: String?,
    onInit: () -> Unit,
    onStage: (String) -> Unit,
    onStageAll: () -> Unit,
    onUnstage: (String) -> Unit,
    onCommitMsgChange: (String) -> Unit,
    onCommit: () -> Unit,
    onPull: () -> Unit,
    onPush: () -> Unit,
    onOpenDiff: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!gitState.isRepo) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Not a Git repository", color = CosmicColors.TextSecondary)
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = onInit,
                    enabled = !busy,
                    colors = ButtonDefaults.buttonColors(containerColor = CosmicColors.Cyan)
                ) {
                    Text("Initialize Git Repo", color = CosmicColors.Background, fontWeight = FontWeight.Bold)
                }
            }
        }
        return
    }

    LazyColumn(
        modifier = modifier.padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(CosmicColors.SurfaceElevated)
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.AccountTree, contentDescription = null, tint = CosmicColors.Violet, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(gitState.branch, color = CosmicColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    if (gitState.aheadBehind.isNotBlank()) {
                        Text(gitState.aheadBehind, color = CosmicColors.Cyan, fontSize = 11.sp)
                    }
                }
                OutlinedButton(onClick = onPull, enabled = !busy, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
                    Text("Pull", fontSize = 12.sp)
                }
                Spacer(Modifier.width(6.dp))
                Button(
                    onClick = onPush,
                    enabled = !busy,
                    colors = ButtonDefaults.buttonColors(containerColor = CosmicColors.Cyan),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text("Push", color = CosmicColors.Background, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (!statusMsg.isNullOrBlank()) {
            item {
                Text(
                    text = statusMsg,
                    color = CosmicColors.CyanMuted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(CosmicColors.Surface)
                        .padding(8.dp)
                )
            }
        }

        item {
            OutlinedTextField(
                value = commitMsg,
                onValueChange = onCommitMsgChange,
                label = { Text("Commit message", fontSize = 12.sp) },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onStageAll, enabled = !busy, modifier = Modifier.weight(1f)) {
                    Text("Stage All", fontSize = 12.sp)
                }
                Button(
                    onClick = onCommit,
                    enabled = !busy && commitMsg.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = CosmicColors.Purple),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Commit", fontSize = 12.sp)
                }
            }
        }

        item {
            Text(
                text = "CHANGES (${gitState.changes.size})",
                color = CosmicColors.TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }

        if (gitState.changes.isEmpty()) {
            item {
                Text("Working tree clean", color = CosmicColors.TextMuted, fontSize = 12.sp)
            }
        } else {
            items(gitState.changes) { ch ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(CosmicColors.Surface)
                        .clickable { onOpenDiff(ch.path, ch.staged) }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = ch.statusCode,
                        color = if (ch.staged) CosmicColors.Green else CosmicColors.Amber,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.width(26.dp)
                    )
                    Text(
                        text = ch.path,
                        color = CosmicColors.TextPrimary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = { if (ch.staged) onUnstage(ch.path) else onStage(ch.path) },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(if (ch.staged) "Unstage" else "Stage", fontSize = 11.sp, color = CosmicColors.Cyan)
                    }
                }
            }
        }

        if (gitState.recentCommits.isNotEmpty()) {
            item {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "RECENT COMMITS",
                    color = CosmicColors.TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            items(gitState.recentCommits) { c ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = c.hash,
                        color = CosmicColors.Cyan,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = c.message,
                        color = CosmicColors.TextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

private fun fileColor(name: String): Color {
    return when (name.substringAfterLast('.', "").lowercase()) {
        "kt", "kts" -> CosmicColors.Violet
        "java" -> CosmicColors.Amber
        "py" -> CosmicColors.CyanMuted
        "js", "ts", "jsx", "tsx" -> Color(0xFFFDE047)
        "html", "xml" -> Color(0xFFFF6E8A)
        "md", "json" -> CosmicColors.Green
        else -> CosmicColors.TextSecondary
    }
}

@Composable
private fun PromptInputDialog(
    title: String,
    label: String,
    initialValue: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var value by remember { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CosmicColors.Surface,
        title = { Text(title, color = CosmicColors.Cyan) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(label) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(value) },
                colors = ButtonDefaults.buttonColors(containerColor = CosmicColors.Cyan)
            ) {
                Text("OK", color = CosmicColors.Background, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun CloneRepoDialog(
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var url by remember { mutableStateOf("") }
    var folder by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CosmicColors.Surface,
        title = { Text("Clone Git Repository", color = CosmicColors.Cyan) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("HTTPS Git URL") },
                    placeholder = { Text("https://github.com/user/repo.git") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = folder,
                    onValueChange = { folder = it },
                    label = { Text("Folder name (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(url, folder) },
                enabled = url.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = CosmicColors.Cyan)
            ) {
                Text("Clone", color = CosmicColors.Background, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
