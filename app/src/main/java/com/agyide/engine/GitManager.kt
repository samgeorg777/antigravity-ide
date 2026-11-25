package com.agyide.engine

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class GitFileChange(
    val statusCode: String,
    val path: String,
    val staged: Boolean
)

data class GitCommitInfo(
    val hash: String,
    val message: String
)

data class GitRepoState(
    val isRepo: Boolean = false,
    val branch: String = "",
    val aheadBehind: String = "",
    val changes: List<GitFileChange> = emptyList(),
    val recentCommits: List<GitCommitInfo> = emptyList(),
    val branches: List<String> = emptyList(),
    val error: String? = null
)

object GitManager {

    private fun gitCmd(context: Context, repoDir: File, vararg args: String): CommandResult {
        val git = RuntimeEnv.gitFile(context)
        if (!git.canExecute()) {
            return CommandResult(-1, "", "git binary not found")
        }
        return RuntimeEnv.runBinary(
            context = context,
            command = listOf(git.absolutePath, *args),
            cwd = repoDir,
            timeoutSeconds = 30
        )
    }

    suspend fun inspectRepo(context: Context, repoDir: File): GitRepoState = withContext(Dispatchers.IO) {
        val check = gitCmd(context, repoDir, "rev-parse", "--is-inside-work-tree")
        if (check.exitCode != 0 || !check.stdout.trim().equals("true", ignoreCase = true)) {
            return@withContext GitRepoState(isRepo = false)
        }

        val statusRes = gitCmd(context, repoDir, "status", "--porcelain=v1", "-b")
        var branch = "HEAD"
        var aheadBehind = ""
        val changes = mutableListOf<GitFileChange>()

        statusRes.stdout.lines().forEach { line ->
            if (line.startsWith("## ")) {
                val info = line.removePrefix("## ").trim()
                val branchPart = info.substringBefore("...")
                branch = branchPart.substringBefore(" [").trim()
                val bracket = Regex("\\[(.+?)]").find(info)?.groupValues?.getOrNull(1)
                if (bracket != null) aheadBehind = bracket
            } else if (line.length >= 4) {
                val x = line[0]
                val y = line[1]
                val path = line.substring(3).trim()
                if (x != ' ' && x != '?') {
                    changes += GitFileChange("$x", path, staged = true)
                }
                if (y != ' ') {
                    changes += GitFileChange(if (x == '?' && y == '?') "??" else "$y", path, staged = false)
                }
            }
        }

        val logRes = gitCmd(context, repoDir, "log", "-n", "15", "--oneline")
        val commits = logRes.stdout.lines().mapNotNull { l ->
            val t = l.trim()
            if (t.isEmpty()) null
            else GitCommitInfo(t.substringBefore(' '), t.substringAfter(' ', ""))
        }

        val branchRes = gitCmd(context, repoDir, "branch", "--format=%(refname:short)")
        val branches = branchRes.stdout.lines().map { it.trim() }.filter { it.isNotEmpty() }

        GitRepoState(
            isRepo = true,
            branch = branch,
            aheadBehind = aheadBehind,
            changes = changes,
            recentCommits = commits,
            branches = branches
        )
    }

    suspend fun diffFile(context: Context, repoDir: File, path: String, staged: Boolean): String =
        withContext(Dispatchers.IO) {
            val res = if (staged) {
                gitCmd(context, repoDir, "diff", "--cached", "--", path)
            } else {
                gitCmd(context, repoDir, "diff", "--", path)
            }
            if (res.stdout.isBlank() && !staged) {
                val file = File(repoDir, path)
                if (file.exists() && file.isFile && file.length() < 200_000) {
                    return@withContext file.readLines().joinToString("\n") { "+ $it" }
                }
            }
            res.stdout.ifBlank { "(No diff available)" }
        }

    suspend fun initRepo(context: Context, repoDir: File): CommandResult =
        withContext(Dispatchers.IO) { gitCmd(context, repoDir, "init") }

    suspend fun stageFile(context: Context, repoDir: File, path: String): CommandResult =
        withContext(Dispatchers.IO) { gitCmd(context, repoDir, "add", "--", path) }

    suspend fun stageAll(context: Context, repoDir: File): CommandResult =
        withContext(Dispatchers.IO) { gitCmd(context, repoDir, "add", "-A") }

    suspend fun unstageFile(context: Context, repoDir: File, path: String): CommandResult =
        withContext(Dispatchers.IO) { gitCmd(context, repoDir, "reset", "HEAD", "--", path) }

    suspend fun commit(context: Context, repoDir: File, message: String): CommandResult =
        withContext(Dispatchers.IO) {
            gitCmd(context, repoDir, "commit", "-m", message)
        }

    suspend fun pull(context: Context, repoDir: File): CommandResult =
        withContext(Dispatchers.IO) { gitCmd(context, repoDir, "pull") }

    suspend fun push(context: Context, repoDir: File): CommandResult =
        withContext(Dispatchers.IO) { gitCmd(context, repoDir, "push") }

    suspend fun clone(context: Context, url: String, targetDir: File): CommandResult =
        withContext(Dispatchers.IO) {
            val parent = targetDir.parentFile ?: RuntimeEnv.projectsDir(context)
            parent.mkdirs()
            gitCmd(context, parent, "clone", url, targetDir.name)
        }
}
