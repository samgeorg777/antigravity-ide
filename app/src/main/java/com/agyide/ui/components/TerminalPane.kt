package com.agyide.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.util.TypedValue
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Terminal
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
import com.agyide.ui.IdeViewModel
import com.agyide.ui.theme.CosmicColors
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.terminal.TextStyle
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient

@Composable
fun TerminalPane(
    viewModel: IdeViewModel,
    onOpenUrlInPreview: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val tabVersion by viewModel.terminalTabVersion.collectAsState()
    val activeIdx by viewModel.activeTerminalIndex.collectAsState()

    var ctrlActive by remember { mutableStateOf(false) }
    var altActive by remember { mutableStateOf(false) }
    var terminalFontSp by remember { mutableFloatStateOf(13f) }
    var terminalViewRef by remember { mutableStateOf<TerminalView?>(null) }

    val sessionClient = remember(context) {
        object : TerminalSessionClient {
            override fun onTextChanged(changedSession: TerminalSession) {
                terminalViewRef?.onScreenUpdated()
                viewModel.notifyTerminalUpdated()
            }

            override fun onTitleChanged(changedSession: TerminalSession) {}

            override fun onSessionFinished(finishedSession: TerminalSession) {
                viewModel.refreshAuthStatus()
            }

            override fun onCopyTextToClipboard(session: TerminalSession, text: String) {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                cm?.setPrimaryClip(ClipData.newPlainText("Antigravity Terminal", text))
            }

            override fun onPasteTextFromClipboard(session: TerminalSession?) {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                val clip = cm?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
                if (!clip.isNullOrEmpty()) {
                    terminalViewRef?.mEmulator?.paste(clip)
                }
            }

            override fun onBell(session: TerminalSession) {}

            override fun onColorsChanged(session: TerminalSession) {
                applyCosmicTerminalColors(terminalViewRef?.mEmulator)
                terminalViewRef?.onScreenUpdated()
            }

            override fun onTerminalCursorStateChange(state: Boolean) {
                terminalViewRef?.onScreenUpdated()
            }

            override fun getTerminalCursorStyle(): Int = 0
            override fun logError(tag: String?, message: String?) {}
            override fun logWarn(tag: String?, message: String?) {}
            override fun logInfo(tag: String?, message: String?) {}
            override fun logDebug(tag: String?, message: String?) {}
            override fun logVerbose(tag: String?, message: String?) {}
            override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {}
            override fun logStackTrace(tag: String?, e: Exception?) {}
        }
    }

    val viewClient = remember(context) {
        object : TerminalViewClient {
            override fun onScale(scale: Float): Float {
                val next = (terminalFontSp * scale).coerceIn(9f, 24f)
                terminalFontSp = next
                return 1.0f
            }

            override fun onSingleTapUp(e: MotionEvent) {
                val tv = terminalViewRef ?: return
                val emulator = tv.mEmulator
                if (emulator != null) {
                    val colRow = tv.getColumnAndRow(e, true)
                    val word = emulator.screen.getWordAtLocation(colRow[0], colRow[1])
                    val urlMatch = Regex("https?://[^\\s\"'<>()\\]]+").find(word ?: "")
                    if (urlMatch != null) {
                        var url = urlMatch.value.trimEnd('.', ',', ';', ':')
                        if (url.contains("127.0.0.1") || url.contains("localhost")) {
                            onOpenUrlInPreview(url)
                        } else {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                            }
                        }
                        return
                    }
                }
                tv.requestFocus()
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.showSoftInput(tv, InputMethodManager.SHOW_IMPLICIT)
            }

            override fun shouldBackButtonBeMappedToEscape(): Boolean = false
            override fun shouldEnforceCharBasedInput(): Boolean = false
            override fun shouldUseCtrlSpaceWorkaround(): Boolean = false
            override fun isTerminalViewSelected(): Boolean = true
            override fun copyModeChanged(copyMode: Boolean) {}

            override fun onKeyDown(keyCode: Int, e: KeyEvent, session: TerminalSession): Boolean {
                if (keyCode == KeyEvent.KEYCODE_ENTER && !e.isShiftPressed) return false
                return false
            }

            override fun onKeyUp(keyCode: Int, e: KeyEvent): Boolean = false

            override fun onLongPress(event: MotionEvent): Boolean {
                sessionClient.onPasteTextFromClipboard(terminalViewRef?.mTermSession)
                return true
            }

            override fun readControlKey(): Boolean {
                val v = ctrlActive
                if (v) ctrlActive = false
                return v
            }

            override fun readAltKey(): Boolean {
                val v = altActive
                if (v) altActive = false
                return v
            }

            override fun readShiftKey(): Boolean = false
            override fun readFnKey(): Boolean = false
            override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession): Boolean = false

            override fun onEmulatorSet() {
                applyCosmicTerminalColors(terminalViewRef?.mEmulator)
                terminalViewRef?.onScreenUpdated()
            }

            override fun logError(tag: String?, message: String?) {}
            override fun logWarn(tag: String?, message: String?) {}
            override fun logInfo(tag: String?, message: String?) {}
            override fun logDebug(tag: String?, message: String?) {}
            override fun logVerbose(tag: String?, message: String?) {}
            override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {}
            override fun logStackTrace(tag: String?, e: Exception?) {}
        }
    }

    LaunchedEffect(Unit) {
        viewModel.ensureInitialTerminalSession(sessionClient) {
            terminalViewRef?.onScreenUpdated()
        }
    }

    val tabs = remember(tabVersion) { viewModel.terminalTabs.toList() }
    val activeSession = tabs.getOrNull(activeIdx)?.session

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CosmicColors.Background)
    ) {
        // Terminal Tabs & Quick Actions Bar
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
                tabs.forEachIndexed { idx, tab ->
                    val selected = idx == activeIdx
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) CosmicColors.SurfaceElevated else Color.Transparent)
                            .border(
                                1.dp,
                                if (selected) CosmicColors.Cyan.copy(alpha = 0.5f) else CosmicColors.SurfaceBorder,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { viewModel.selectTerminalTab(idx) }
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Terminal, contentDescription = null, tint = if (selected) CosmicColors.Cyan else CosmicColors.TextMuted, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(tab.title, color = if (selected) CosmicColors.Cyan else CosmicColors.TextSecondary, fontSize = 11.sp)
                        if (tabs.size > 1) {
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close",
                                tint = CosmicColors.TextMuted,
                                modifier = Modifier
                                    .size(13.dp)
                                    .clickable { viewModel.closeTerminalTab(idx) }
                            )
                        }
                    }
                }
            }

            TextButton(
                onClick = { viewModel.createTerminalTab("AI", "agy") },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text("+ agy", color = CosmicColors.Violet, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            IconButton(onClick = { viewModel.createTerminalTab("Bash") }, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Default.Add, contentDescription = "New Shell", tint = CosmicColors.Cyan, modifier = Modifier.size(18.dp))
            }
            IconButton(
                onClick = { sessionClient.onPasteTextFromClipboard(activeSession) },
                modifier = Modifier.size(30.dp)
            ) {
                Icon(Icons.Default.ContentPaste, contentDescription = "Paste", tint = CosmicColors.TextSecondary, modifier = Modifier.size(16.dp))
            }
        }

        // Terminal Emulator View
        val monoFont = remember(context) {
            runCatching { Typeface.createFromAsset(context.assets, "JetBrainsMono-Regular.ttf") }
                .getOrDefault(Typeface.MONOSPACE)
        }
        val textSizePx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            terminalFontSp,
            context.resources.displayMetrics
        ).toInt()

        var boundSessionHandle by remember { mutableStateOf<String?>(null) }

        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(CosmicColors.Background),
            factory = { ctx ->
                TerminalView(ctx, null).apply {
                    setTypeface(monoFont)
                    setTextSize(textSizePx)
                    setBackgroundColor(0xFF0B0F19.toInt())
                    setTerminalViewClient(viewClient)
                    isFocusable = true
                    isFocusableInTouchMode = true
                    terminalViewRef = this
                }
            },
            update = { tv ->
                tv.setTextSize(textSizePx)
                if (activeSession != null && boundSessionHandle != activeSession.mHandle) {
                    boundSessionHandle = activeSession.mHandle
                    tv.attachSession(activeSession)
                    applyCosmicTerminalColors(tv.mEmulator)
                    tv.onScreenUpdated()
                }
            }
        )

        // Extra Keys Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CosmicColors.Surface)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            ExtraKeyChip("ESC", false) { activeSession?.write("\u001b") }
            ExtraKeyChip("TAB", false) { activeSession?.write("\t") }
            ExtraKeyChip("CTRL", ctrlActive) { ctrlActive = !ctrlActive }
            ExtraKeyChip("ALT", altActive) { altActive = !altActive }
            ExtraKeyChip("C-c", false) { activeSession?.write("\u0003") }
            ExtraKeyChip("↑", false) { activeSession?.write("\u001b[A") }
            ExtraKeyChip("↓", false) { activeSession?.write("\u001b[B") }
            ExtraKeyChip("←", false) { activeSession?.write("\u001b[D") }
            ExtraKeyChip("→", false) { activeSession?.write("\u001b[C") }
            ExtraKeyChip("/", false) { activeSession?.write("/") }
            ExtraKeyChip("-", false) { activeSession?.write("-") }
            ExtraKeyChip("|", false) { activeSession?.write("|") }
            ExtraKeyChip("agy", false) { activeSession?.write("agy\n") }
            ExtraKeyChip("git", false) { activeSession?.write("git status\n") }
        }
    }
}

@Composable
private fun ExtraKeyChip(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (active) CosmicColors.Cyan.copy(alpha = 0.25f) else CosmicColors.SurfaceElevated)
            .border(
                1.dp,
                if (active) CosmicColors.Cyan else CosmicColors.SurfaceBorder,
                RoundedCornerShape(6.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            color = if (active) CosmicColors.Cyan else CosmicColors.TextPrimary,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun applyCosmicTerminalColors(emulator: TerminalEmulator?) {
    val colors = emulator?.mColors?.mCurrentColors ?: return
    colors[TextStyle.COLOR_INDEX_BACKGROUND] = 0xFF0B0F19.toInt()
    colors[TextStyle.COLOR_INDEX_FOREGROUND] = 0xFFE2E8F0.toInt()
    colors[TextStyle.COLOR_INDEX_CURSOR] = 0xFF00F0FF.toInt()
}
