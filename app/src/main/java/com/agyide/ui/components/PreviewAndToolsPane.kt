package com.agyide.ui.components

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.agyide.ui.theme.CosmicColors

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PreviewAndToolsPane(
    previewUrl: String,
    reloadNonce: Int,
    deviceLog: String,
    deviceBusy: Boolean,
    hasAuth: Boolean,
    onChangeUrl: (String) -> Unit,
    onReload: () -> Unit,
    onRunDeviceCmd: (Array<String>) -> Unit,
    onOpenAuthSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var subTab by remember { mutableIntStateOf(0) } // 0 = Web Preview, 1 = Device & Tools
    var urlInput by remember(previewUrl) { mutableStateOf(previewUrl) }
    var desktopMode by remember { mutableStateOf(false) }
    var showConsole by remember { mutableStateOf(false) }
    val consoleLogs = remember { mutableStateListOf<String>() }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    var pairPort by remember { mutableStateOf("") }
    var pairCode by remember { mutableStateOf("") }
    var connectPort by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CosmicColors.Background)
    ) {
        TabRow(
            selectedTabIndex = subTab,
            containerColor = CosmicColors.Surface,
            contentColor = CosmicColors.Cyan
        ) {
            Tab(selected = subTab == 0, onClick = { subTab = 0 }, text = { Text("Web Preview", fontSize = 12.sp) })
            Tab(selected = subTab == 1, onClick = { subTab = 1 }, text = { Text("Device & IDE Tools", fontSize = 12.sp) })
        }

        if (subTab == 0) {
            // Address Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CosmicColors.Surface)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { webViewRef?.goBack() }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = CosmicColors.TextSecondary, modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = { webViewRef?.goForward() }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Forward", tint = CosmicColors.TextSecondary, modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = { onReload() }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Refresh, contentDescription = "Reload", tint = CosmicColors.Cyan, modifier = Modifier.size(16.dp))
                }

                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )

                IconButton(onClick = { onChangeUrl(urlInput) }, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Go", tint = CosmicColors.Green)
                }
                IconButton(onClick = { desktopMode = !desktopMode }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = if (desktopMode) Icons.Default.Computer else Icons.Default.PhoneAndroid,
                        contentDescription = "Viewport",
                        tint = if (desktopMode) CosmicColors.Cyan else CosmicColors.TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
                IconButton(onClick = { showConsole = !showConsole }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Terminal,
                        contentDescription = "Console",
                        tint = if (showConsole) CosmicColors.Violet else CosmicColors.TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Quick localhost presets
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CosmicColors.SurfaceElevated)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("http://127.0.0.1:8080", "http://127.0.0.1:3000", "http://127.0.0.1:5173").forEach { preset ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(CosmicColors.Surface)
                            .border(1.dp, CosmicColors.SurfaceBorder, RoundedCornerShape(6.dp))
                            .clickable {
                                urlInput = preset
                                onChangeUrl(preset)
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(preset.removePrefix("http://"), color = CosmicColors.CyanMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }

            var loadedUrl by remember { mutableStateOf("") }
            var lastNonce by remember { mutableIntStateOf(-1) }

            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = true
                        settings.cacheMode = WebSettings.LOAD_NO_CACHE
                        webViewClient = object : WebViewClient() {}
                        webChromeClient = object : WebChromeClient() {
                            override fun onConsoleMessage(msg: ConsoleMessage): Boolean {
                                consoleLogs.add("[${msg.messageLevel()}] ${msg.message()} (${msg.lineNumber()})")
                                if (consoleLogs.size > 150) consoleLogs.removeAt(0)
                                return true
                            }
                        }
                        webViewRef = this
                    }
                },
                update = { wv ->
                    wv.settings.useWideViewPort = desktopMode
                    wv.settings.loadWithOverviewMode = desktopMode
                    wv.settings.userAgentString = if (desktopMode) {
                        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36"
                    } else null

                    if (loadedUrl != previewUrl || lastNonce != reloadNonce) {
                        loadedUrl = previewUrl
                        lastNonce = reloadNonce
                        wv.loadUrl(previewUrl)
                    }
                }
            )

            if (showConsole) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .background(CosmicColors.Surface)
                        .border(1.dp, CosmicColors.SurfaceBorder)
                        .padding(6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("JS Console (${consoleLogs.size})", color = CosmicColors.Violet, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { consoleLogs.clear() }, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)) {
                            Text("Clear", fontSize = 11.sp)
                        }
                    }
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(consoleLogs) { log ->
                            Text(log, color = CosmicColors.TextSecondary, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                        }
                    }
                }
            }
        } else {
            // Device & IDE Tools
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CosmicColors.Surface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CosmicColors.SurfaceBorder)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Account & Permissions", color = CosmicColors.Cyan, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(
                                text = if (hasAuth) "Antigravity AI credentials configured." else "Sign in with Google or set a Gemini API key to activate the AI agent.",
                                color = CosmicColors.TextSecondary,
                                fontSize = 12.sp
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = onOpenAuthSettings,
                                    colors = ButtonDefaults.buttonColors(containerColor = CosmicColors.Cyan)
                                ) {
                                    Text("Auth / API Key", color = CosmicColors.Background, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                                OutlinedButton(onClick = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                            data = Uri.parse("package:${context.packageName}")
                                        }
                                        runCatching { context.startActivity(intent) }
                                    }
                                }) {
                                    Text("All Files & USB", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CosmicColors.Surface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CosmicColors.SurfaceBorder)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Autonomous Device Control (agy-device)", color = CosmicColors.Violet, fontWeight = FontWeight.Bold, fontSize = 14.sp)

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { onRunDeviceCmd(arrayOf("status")) }, enabled = !deviceBusy) {
                                    Text("Status", fontSize = 12.sp)
                                }
                                OutlinedButton(onClick = { onRunDeviceCmd(arrayOf("discover")) }, enabled = !deviceBusy) {
                                    Text("Discover mDNS", fontSize = 12.sp)
                                }
                                OutlinedButton(onClick = { onRunDeviceCmd(arrayOf("screenshot")) }, enabled = !deviceBusy) {
                                    Text("Screenshot", fontSize = 12.sp)
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = pairPort,
                                    onValueChange = { pairPort = it },
                                    label = { Text("Pair Port", fontSize = 11.sp) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = pairCode,
                                    onValueChange = { pairCode = it },
                                    label = { Text("6-Digit Code", fontSize = 11.sp) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                Button(
                                    onClick = { onRunDeviceCmd(arrayOf("pair", pairPort.trim(), pairCode.trim())) },
                                    enabled = !deviceBusy && pairPort.isNotBlank() && pairCode.isNotBlank()
                                ) { Text("Pair", fontSize = 12.sp) }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = connectPort,
                                    onValueChange = { connectPort = it },
                                    label = { Text("ADB Port", fontSize = 11.sp) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                Button(
                                    onClick = { onRunDeviceCmd(arrayOf("connect", connectPort.trim())) },
                                    enabled = !deviceBusy && connectPort.isNotBlank()
                                ) { Text("Connect", fontSize = 12.sp) }
                                OutlinedButton(onClick = {
                                    runCatching {
                                        context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
                                    }
                                }) { Text("Dev Options", fontSize = 12.sp) }
                            }

                            Text(
                                text = deviceLog,
                                color = CosmicColors.TextPrimary,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(CosmicColors.Background)
                                    .padding(10.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
