package com.agyide.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class WorkspaceNode(
    val file: File,
    val name: String,
    val isDirectory: Boolean,
    val depth: Int,
    val sizeBytes: Long
)

data class DiffLine(
    val type: DiffType,
    val text: String
)

enum class DiffType { CONTEXT, ADDED, REMOVED, HEADER }

object WorkspaceManager {

    private val ignoredDirs = setOf(".git", ".gradle", "build", "node_modules", "__pycache__", ".idea")

    suspend fun listProjects(projectsRoot: File): List<File> = withContext(Dispatchers.IO) {
        if (!projectsRoot.exists()) projectsRoot.mkdirs()
        projectsRoot.listFiles()
            ?.filter { it.isDirectory && !it.name.startsWith(".") }
            ?.sortedBy { it.name.lowercase() }
            ?: emptyList()
    }

    suspend fun listChildren(dir: File, showHidden: Boolean = false): List<File> = withContext(Dispatchers.IO) {
        if (!dir.exists() || !dir.isDirectory) return@withContext emptyList()
        dir.listFiles()
            ?.filter { showHidden || !it.name.startsWith(".") }
            ?.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })
            ?: emptyList()
    }

    suspend fun flattenTree(
        root: File,
        expandedPaths: Set<String>,
        showHidden: Boolean = false
    ): List<WorkspaceNode> = withContext(Dispatchers.IO) {
        val result = mutableListOf<WorkspaceNode>()
        fun walk(dir: File, depth: Int) {
            val children = dir.listFiles()
                ?.filter { f ->
                    (showHidden || !f.name.startsWith(".")) &&
                        !(f.isDirectory && f.name in ignoredDirs && depth > 0)
                }
                ?.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })
                ?: return
            for (child in children) {
                result += WorkspaceNode(
                    file = child,
                    name = child.name,
                    isDirectory = child.isDirectory,
                    depth = depth,
                    sizeBytes = if (child.isFile) child.length() else 0L
                )
                if (child.isDirectory && expandedPaths.contains(child.absolutePath)) {
                    walk(child, depth + 1)
                }
            }
        }
        if (root.exists()) walk(root, 0)
        result
    }

    suspend fun collectAllFilePaths(root: File, maxFiles: Int = 400): List<String> = withContext(Dispatchers.IO) {
        val out = mutableListOf<String>()
        fun walk(dir: File) {
            if (out.size >= maxFiles) return
            val children = dir.listFiles() ?: return
            for (child in children) {
                if (out.size >= maxFiles) return
                if (child.isDirectory) {
                    if (child.name !in ignoredDirs && !child.name.startsWith(".")) {
                        walk(child)
                    }
                } else {
                    val rel = child.relativeTo(root).path
                    out += rel
                }
            }
        }
        if (root.exists()) walk(root)
        out.sorted()
    }

    suspend fun readTextFile(file: File, maxBytes: Long = 2 * 1024 * 1024): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (!file.exists()) error("File does not exist: ${file.name}")
                if (file.length() > maxBytes) {
                    error("File is too large (${file.length() / 1024} KB) for the code editor")
                }
                file.readText(Charsets.UTF_8)
            }
        }

    suspend fun writeTextFile(file: File, content: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                file.parentFile?.mkdirs()
                file.writeText(content, Charsets.UTF_8)
            }
        }

    fun languageScopeForFile(fileName: String): String? {
        return when (fileName.substringAfterLast('.', "").lowercase()) {
            "kt", "kts" -> "source.kotlin"
            "java" -> "source.java"
            "py" -> "source.python"
            "js", "jsx", "mjs", "cjs", "ts", "tsx" -> "source.js"
            "html", "htm" -> "text.html.basic"
            "xml", "svg", "gradle" -> "text.xml"
            "md", "markdown" -> "text.html.markdown"
            "lua" -> "source.lua"
            else -> null
        }
    }

    fun computeSimpleDiff(original: String, updated: String): List<DiffLine> {
        val a = original.lines()
        val b = updated.lines()
        val n = a.size
        val m = b.size
        if (n * m > 250_000) {
            return listOf(DiffLine(DiffType.HEADER, "Diff too large to compute inline (${n} -> ${m} lines)"))
        }
        val dp = Array(n + 1) { IntArray(m + 1) }
        for (i in n - 1 downTo 0) {
            for (j in m - 1 downTo 0) {
                dp[i][j] = if (a[i] == b[j]) dp[i + 1][j + 1] + 1
                else maxOf(dp[i + 1][j], dp[i][j + 1])
            }
        }
        val out = mutableListOf<DiffLine>()
        var i = 0
        var j = 0
        while (i < n && j < m) {
            when {
                a[i] == b[j] -> {
                    out += DiffLine(DiffType.CONTEXT, "  ${a[i]}")
                    i++; j++
                }
                dp[i + 1][j] >= dp[i][j + 1] -> {
                    out += DiffLine(DiffType.REMOVED, "- ${a[i]}")
                    i++
                }
                else -> {
                    out += DiffLine(DiffType.ADDED, "+ ${b[j]}")
                    j++
                }
            }
        }
        while (i < n) out += DiffLine(DiffType.REMOVED, "- ${a[i++]}")
        while (j < m) out += DiffLine(DiffType.ADDED, "+ ${b[j++]}")
        return out
    }
}
