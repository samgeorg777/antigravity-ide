package com.agyide.engine

import android.content.Context
import android.os.Environment
import android.system.Os
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

data class BootstrapProgress(
    val stepTitle: String,
    val detail: String,
    val fraction: Float,
    val done: Boolean = false,
    val error: String? = null
)

object BootstrapManager {

    private const val AGY_TARBALL_URL =
        "https://github.com/wallentx/antigravity-cli-termux/releases/latest/download/antigravity-termux-standalone.tar.gz"

    fun ensureReady(context: Context): Flow<BootstrapProgress> = channelFlow {
        try {
            RuntimeEnv.homeDir(context).apply { mkdirs() }
            val projects = RuntimeEnv.projectsDir(context).apply { mkdirs() }
            RuntimeEnv.uploadsDir(context).apply { mkdirs() }

            if (!RuntimeEnv.isBootstrapInstalled(context)) {
                send(BootstrapProgress("Extracting Linux Rootfs", "Unpacking ARM64 toolchain & glibc...", 0.06f))
                extractBootstrapFromAssets(context) { frac, msg ->
                    trySend(BootstrapProgress("Extracting Linux Rootfs", msg, frac))
                }
                send(BootstrapProgress("Configuring Environment", "Linking storage, DNS & permissions...", 0.56f))
                configureRuntimeFiles(context)
            } else {
                send(BootstrapProgress("Runtime Ready", "Linux & glibc environment verified", 0.56f))
                configureRuntimeFiles(context)
            }

            ensureStarterProject(projects)

            if (!RuntimeEnv.isAgyInstalled(context)) {
                send(BootstrapProgress("Downloading Antigravity Engine", "Connecting to GitHub release...", 0.60f))
                downloadAndInstallAgy(context) { frac, title, detail ->
                    trySend(BootstrapProgress(title, detail, frac))
                }
            }

            send(BootstrapProgress("Ready", "Antigravity IDE environment initialized", 1.0f, done = true))
        } catch (e: Exception) {
            send(
                BootstrapProgress(
                    stepTitle = "Setup Error",
                    detail = e.message ?: "Unknown bootstrap failure",
                    fraction = 0f,
                    done = false,
                    error = e.stackTraceToString()
                )
            )
        }
    }.flowOn(Dispatchers.IO)

    private fun extractBootstrapFromAssets(
        context: Context,
        onStep: (Float, String) -> Unit
    ) {
        val usrDir = RuntimeEnv.usrDir(context)
        val stagingDir = RuntimeEnv.stagingDir(context)

        if (stagingDir.exists()) stagingDir.deleteRecursively()
        if (usrDir.exists()) usrDir.deleteRecursively()
        stagingDir.mkdirs()

        val symlinks = mutableListOf<Pair<String, String>>()
        val buffer = ByteArray(32 * 1024)
        var entryCount = 0

        context.assets.open("bootstrap-aarch64.zip").use { raw ->
            ZipInputStream(BufferedInputStream(raw, 64 * 1024)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val name = entry.name
                    entryCount++
                    if (entryCount % 150 == 0) {
                        val frac = (0.06f + (entryCount.coerceAtMost(2500) / 2500f) * 0.42f)
                        onStep(frac, "Extracting file $entryCount ($name)...")
                    }

                    if (name == "SYMLINKS.txt") {
                        val baos = ByteArrayOutputStream()
                        while (true) {
                            val count = zis.read(buffer)
                            if (count <= 0) break
                            baos.write(buffer, 0, count)
                        }
                        baos.toString(Charsets.UTF_8.name()).lineSequence().forEach { line ->
                            val idx = line.indexOf('←')
                            if (idx > 0 && idx < line.length - 1) {
                                val target = line.substring(0, idx)
                                val linkPath = File(stagingDir, line.substring(idx + 1)).absolutePath
                                symlinks += target to linkPath
                            }
                        }
                    } else {
                        val outFile = File(stagingDir, name)
                        if (entry.isDirectory) {
                            outFile.mkdirs()
                        } else {
                            outFile.parentFile?.mkdirs()
                            FileOutputStream(outFile).use { fos ->
                                while (true) {
                                    val count = zis.read(buffer)
                                    if (count <= 0) break
                                    fos.write(buffer, 0, count)
                                }
                            }
                            if (name.startsWith("bin/") ||
                                name.startsWith("libexec/") ||
                                name.startsWith("glibc/") ||
                                name.startsWith("lib/apt/")
                            ) {
                                runCatching { Os.chmod(outFile.absolutePath, 493) } // 0755
                            }
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        }

        onStep(0.50f, "Creating ${symlinks.size} POSIX symlinks...")
        symlinks.forEach { (target, linkPath) ->
            val linkFile = File(linkPath)
            linkFile.parentFile?.mkdirs()
            runCatching { Os.remove(linkPath) }
            runCatching { Os.symlink(target, linkPath) }
        }

        if (!stagingDir.renameTo(usrDir)) {
            throw IllegalStateException("Failed to move bootstrap staging directory to ${usrDir.absolutePath}")
        }
        onStep(0.55f, "Rootfs extracted ($entryCount entries)")
    }

    private fun configureRuntimeFiles(context: Context) {
        val usr = RuntimeEnv.usrDir(context)
        val home = RuntimeEnv.homeDir(context)
        val etcDirs = listOf(File(usr, "etc"), File(usr, "glibc/etc"))
        etcDirs.forEach { dir ->
            dir.mkdirs()
            File(dir, "hosts").writeText("127.0.0.1 localhost\n::1 localhost ip6-localhost ip6-loopback\n")
            File(dir, "resolv.conf").writeText("nameserver 8.8.8.8\nnameserver 1.1.1.1\n")
            File(dir, "nsswitch.conf").writeText("hosts: files dns\nnetworks: files\n")
        }

        val loader = File(usr, "glibc/lib/ld-linux-aarch64.so.1")
        if (loader.exists()) {
            runCatching { Os.chmod(loader.absolutePath, 493) }
        }

        File(home, ".termux").apply { mkdirs() }.also { termuxDir ->
            val colors = File(termuxDir, "colors.properties")
            if (!colors.exists()) {
                colors.writeText(
                    """
                    background=#0B0F19
                    foreground=#E2E8F0
                    cursor=#00F0FF
                    color0=#151B28
                    color1=#FF5370
                    color2=#22C55E
                    color3=#FFD166
                    color4=#38BDF8
                    color5=#C084FC
                    color6=#00F0FF
                    color7=#E2E8F0
                    color8=#475569
                    color9=#FF6E8A
                    color10=#4ADE80
                    color11=#FDE047
                    color12=#60A5FA
                    color13=#D8B4FE
                    color14=#67E8F9
                    color15=#FFFFFF
                    """.trimIndent()
                )
            }
        }

        val storageDir = File(home, "storage").apply { mkdirs() }
        val extRoot = Environment.getExternalStorageDirectory()
        if (extRoot.exists()) {
            safeSymlink(extRoot.absolutePath, File(storageDir, "shared"))
            safeSymlink(File(extRoot, "Download").absolutePath, File(storageDir, "downloads"))
            safeSymlink(File(extRoot, "Documents").absolutePath, File(storageDir, "documents"))
        }
    }

    private fun safeSymlink(target: String, linkFile: File) {
        if (!linkFile.exists() && File(target).exists()) {
            runCatching { Os.symlink(target, linkFile.absolutePath) }
        }
    }

    private fun downloadAndInstallAgy(
        context: Context,
        onStep: (Float, String, String) -> Unit
    ) {
        val usr = RuntimeEnv.usrDir(context)
        val tmp = RuntimeEnv.tmpDir(context).apply { mkdirs() }
        val tarFile = File(tmp, "antigravity.tar.gz")
        if (tarFile.exists()) tarFile.delete()

        var currentUrl = AGY_TARBALL_URL
        var conn: HttpURLConnection
        var redirects = 0
        while (true) {
            conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 20_000
                readTimeout = 180_000
                setRequestProperty("User-Agent", "AntigravityIDE-Android/1.0")
            }
            val code = conn.responseCode
            if (code in 300..399 && redirects < 6) {
                currentUrl = conn.getHeaderField("Location") ?: break
                conn.disconnect()
                redirects++
            } else {
                break
            }
        }

        if (conn.responseCode !in 200..299) {
            throw IllegalStateException("HTTP ${conn.responseCode} downloading Antigravity engine")
        }

        val totalBytes = conn.contentLengthLong
        var downloaded = 0L
        val buf = ByteArray(32 * 1024)
        var lastEmitMs = 0L

        conn.inputStream.use { input ->
            FileOutputStream(tarFile).use { output ->
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    output.write(buf, 0, n)
                    downloaded += n
                    val now = System.currentTimeMillis()
                    if (now - lastEmitMs > 250) {
                        lastEmitMs = now
                        val mbDone = downloaded / (1024f * 1024f)
                        val detail = if (totalBytes > 0) {
                            val mbTotal = totalBytes / (1024f * 1024f)
                            String.format("Downloaded %.1f / %.1f MB", mbDone, mbTotal)
                        } else {
                            String.format("Downloaded %.1f MB", mbDone)
                        }
                        val frac = if (totalBytes > 0) {
                            0.60f + (downloaded.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) * 0.28f
                        } else 0.72f
                        onStep(frac, "Downloading Antigravity Engine", detail)
                    }
                }
            }
        }
        conn.disconnect()

        onStep(0.90f, "Installing Antigravity Engine", "Extracting agy & agy.va39 (~250 MB)...")
        val tarBin = File(usr, "bin/tar").absolutePath
        val binDir = File(usr, "bin").absolutePath
        val res = RuntimeEnv.runBinary(
            context = context,
            command = listOf(tarBin, "-xzf", tarFile.absolutePath, "-C", binDir, "agy", "agy.va39"),
            timeoutSeconds = 180
        )
        tarFile.delete()

        if (res.exitCode != 0) {
            throw IllegalStateException("Failed to extract agy engine: ${res.stderr}")
        }

        listOf(RuntimeEnv.agyFile(context), RuntimeEnv.agyPayloadFile(context)).forEach { f ->
            if (f.exists()) runCatching { Os.chmod(f.absolutePath, 493) }
        }
    }

    private fun ensureStarterProject(projectsDir: File) {
        val existing = projectsDir.listFiles()?.filter { !it.name.startsWith(".") }.orEmpty()
        if (existing.isNotEmpty()) return

        val sample = File(projectsDir, "welcome-antigravity").apply { mkdirs() }
        File(sample, "README.md").writeText(
            """
            # Welcome to Antigravity IDE for Android

            You are running a standalone Linux + glibc + Antigravity AI workspace natively on Android.

            ## Quick Tips
            - **Code Editor**: Open any file from the Explorer, edit with TextMate syntax highlighting, and ask the Agent about your file.
            - **Agent Chat**: Switch models, use `/` slash commands or `@file` mentions, and inspect live tool calls.
            - **Terminal & Preview**: Run dev servers in the embedded terminal and preview `localhost` directly inside the IDE.
            """.trimIndent()
        )
        File(sample, "app.py").writeText(
            """
            from http.server import HTTPServer, SimpleHTTPRequestHandler


            def main():
                port = 8080
                print(f"Serving Antigravity preview on http://127.0.0.1:{port}")
                HTTPServer(("127.0.0.1", port), SimpleHTTPRequestHandler).serve_forever()


            if __name__ == "__main__":
                main()
            """.trimIndent()
        )
        File(sample, "index.html").writeText(
            """
            <!DOCTYPE html>
            <html lang="en">
            <head>
              <meta charset="UTF-8" />
              <meta name="viewport" content="width=device-width, initial-scale=1.0" />
              <title>Antigravity IDE Preview</title>
              <style>
                body {
                  margin: 0;
                  min-height: 100vh;
                  display: grid;
                  place-items: center;
                  background: radial-gradient(circle at top, #151b28, #0b0f19);
                  color: #e2e8f0;
                  font-family: system-ui, sans-serif;
                }
                .card {
                  padding: 28px;
                  border-radius: 16px;
                  border: 1px solid rgba(0, 240, 255, 0.4);
                  background: rgba(17, 22, 37, 0.9);
                  text-align: center;
                }
                h1 { color: #00f0ff; margin: 0 0 8px; }
                p { color: #94a3b8; margin: 0; }
              </style>
            </head>
            <body>
              <div class="card">
                <h1>▲ Antigravity IDE</h1>
                <p>Edit this HTML or ask the Agent to build an app and preview it live.</p>
              </div>
            </body>
            </html>
            """.trimIndent()
        )
    }
}
