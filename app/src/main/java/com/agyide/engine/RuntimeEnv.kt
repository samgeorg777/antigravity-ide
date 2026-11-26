package com.agyide.engine

import android.content.Context
import java.io.File
import java.util.concurrent.TimeUnit

data class CommandResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String
)

object RuntimeEnv {
    const val PACKAGE_ID = "com.agyide"

    fun filesDir(context: Context): File = context.filesDir
    fun usrDir(context: Context): File = File(context.filesDir, "usr")
    fun stagingDir(context: Context): File = File(context.filesDir, "usr-staging")
    fun homeDir(context: Context): File = File(context.filesDir, "home")
    fun projectsDir(context: Context): File = File(homeDir(context), "projects")
    fun uploadsDir(context: Context): File = File(homeDir(context), "uploads")
    fun tmpDir(context: Context): File = File(usrDir(context), "tmp")

    fun bashFile(context: Context): File = File(usrDir(context), "bin/bash")
    fun agyFile(context: Context): File = File(usrDir(context), "bin/agy")
    fun agyPayloadFile(context: Context): File = File(usrDir(context), "bin/agy.va39")
    fun gitFile(context: Context): File = File(usrDir(context), "bin/git")

    fun isBootstrapInstalled(context: Context): Boolean {
        val usr = usrDir(context)
        val loader = File(usr, "glibc/lib/ld-linux-aarch64.so.1")
        return bashFile(context).exists() && (loader.exists() || File(usr, "glibc").exists())
    }

    fun isAgyInstalled(context: Context): Boolean {
        return agyFile(context).canExecute() && agyPayloadFile(context).exists()
    }

    fun hasAuthCredentials(context: Context, apiKey: String? = null): Boolean {
        if (!apiKey.isNullOrBlank()) return true
        val oauth = File(homeDir(context), ".gemini/antigravity-cli/antigravity-oauth-token")
        return oauth.exists() && oauth.length() > 10
    }

    fun buildEnvironment(context: Context, apiKey: String? = null): Map<String, String> {
        val usr = usrDir(context).absolutePath
        val home = homeDir(context).absolutePath
        val tmp = tmpDir(context).apply { mkdirs() }.absolutePath
        val env = linkedMapOf(
            "HOME" to home,
            "PREFIX" to usr,
            "TMPDIR" to tmp,
            "PATH" to "$usr/bin:$usr/bin/applets:/system/bin:/system/xbin",
            "TERM" to "xterm-256color",
            "COLORTERM" to "truecolor",
            "LANG" to "en_US.UTF-8",
            "GODEBUG" to "netdns=cgo",
            "GIT_TERMINAL_PROMPT" to "0",
            "SSL_CERT_FILE" to "$usr/etc/tls/cert.pem",
            "CURL_CA_BUNDLE" to "$usr/etc/tls/cert.pem",
            "TERMUX_VERSION" to "0.118.0",
            "TERMUX_APP__PACKAGE_NAME" to PACKAGE_ID,
            "TERMUX__USER_ID" to "0",
            "BROWSER" to "$usr/bin/xdg-open",
            "AGY_AUTO_UPDATE" to "0"
        )
        if (!apiKey.isNullOrBlank()) {
            env["GEMINI_API_KEY"] = apiKey.trim()
        }
        return env
    }

    fun envArray(context: Context, apiKey: String? = null): Array<String> =
        buildEnvironment(context, apiKey).map { (k, v) -> "$k=$v" }.toTypedArray()

    fun runBinary(
        context: Context,
        command: List<String>,
        cwd: File = projectsDir(context),
        apiKey: String? = null,
        timeoutSeconds: Long = 30
    ): CommandResult {
        return try {
            if (!cwd.exists()) cwd.mkdirs()
            val pb = ProcessBuilder(command)
            pb.directory(cwd)
            val pbEnv = pb.environment()
            pbEnv.remove("LD_PRELOAD")
            pbEnv.remove("LD_LIBRARY_PATH")
            pbEnv.putAll(buildEnvironment(context, apiKey))

            val proc = pb.start()
            val outBuf = StringBuilder()
            val errBuf = StringBuilder()

            val tOut = Thread {
                proc.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { outBuf.appendLine(it) }
                }
            }
            val tErr = Thread {
                proc.errorStream.bufferedReader().useLines { lines ->
                    lines.forEach { errBuf.appendLine(it) }
                }
            }
            tOut.start()
            tErr.start()

            val finished = proc.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            if (!finished) {
                proc.destroyForcibly()
                tOut.join(500)
                tErr.join(500)
                return CommandResult(-1, outBuf.toString(), "Timed out after ${timeoutSeconds}s")
            }
            tOut.join(1000)
            tErr.join(1000)
            CommandResult(proc.exitValue(), outBuf.toString(), errBuf.toString())
        } catch (e: Exception) {
            CommandResult(-1, "", e.message ?: e.javaClass.simpleName)
        }
    }
}
