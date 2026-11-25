package com.agyide.engine

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicReference

data class AgyModelOption(
    val id: String,
    val label: String
)

data class SavedConversation(
    val id: String,
    val title: String,
    val preview: String,
    val lastModified: String,
    val workspacePath: String?
)

sealed interface AgyStreamEvent {
    data class Init(
        val conversationId: String,
        val cwd: String,
        val tools: List<String>,
        val permissionMode: String
    ) : AgyStreamEvent

    data class StepUpdate(
        val conversationId: String,
        val stepIndex: Int,
        val state: String,
        val stepType: String,
        val textDelta: String?,
        val toolName: String?,
        val toolParamsPretty: String?,
        val toolOutput: String?,
        val durationSeconds: Double?,
        val totalTokens: Long?
    ) : AgyStreamEvent

    data class Result(
        val conversationId: String,
        val status: String,
        val response: String,
        val error: String?,
        val durationSeconds: Double?,
        val totalTokens: Long?
    ) : AgyStreamEvent

    data class LogLine(val text: String, val isError: Boolean = false) : AgyStreamEvent
}

object AgyAgentBridge {

    private val gson = Gson()
    private val activeProcess = AtomicReference<Process?>(null)

    val defaultModels = listOf(
        AgyModelOption("gemini-4-hydrogen-high", "Gemini 4 Hydrogen (High)"),
        AgyModelOption("gemini-4-hydrogen-xhigh", "Gemini 4 Hydrogen (XHigh)"),
        AgyModelOption("gemini-4-hydrogen-medium", "Gemini 4 Hydrogen (Medium)"),
        AgyModelOption("gemini-3.8-flash-high", "Gemini 3.8 Flash (High)"),
        AgyModelOption("gemini-3.8-flash-medium", "Gemini 3.8 Flash (Medium)"),
        AgyModelOption("claude-sonnet-4-6", "Claude Sonnet 4.6"),
        AgyModelOption("claude-opus-4-6-thinking", "Claude Opus 4.6 (Thinking)"),
        AgyModelOption("gpt-oss-120b-medium", "GPT-OSS 120B (Medium)")
    )

    fun cancelActiveRun() {
        activeProcess.getAndSet(null)?.let { proc ->
            runCatching { proc.destroyForcibly() }
        }
    }

    fun runPromptStream(
        context: Context,
        prompt: String,
        workspaceDir: File,
        conversationId: String? = null,
        modelId: String? = null,
        effort: String? = null,
        mode: String? = null,
        apiKey: String? = null
    ): Flow<AgyStreamEvent> = callbackFlow {
        val agyBin = RuntimeEnv.agyFile(context)
        if (!agyBin.canExecute()) {
            trySend(AgyStreamEvent.LogLine("Antigravity CLI binary is not installed or executable.", isError = true))
            close()
            return@callbackFlow
        }

        val cmd = mutableListOf(
            agyBin.absolutePath,
            "--output-format", "stream-json"
        )
        if (!conversationId.isNullOrBlank()) {
            cmd += listOf("--resume", conversationId)
        }
        if (!modelId.isNullOrBlank()) {
            cmd += listOf("--model", modelId)
        }
        if (!effort.isNullOrBlank() && effort != "default") {
            cmd += listOf("--effort", effort)
        }
        if (!mode.isNullOrBlank() && mode != "default") {
            cmd += listOf("--mode", mode)
        }
        cmd += "-p=$prompt"

        val pb = ProcessBuilder(cmd).apply {
            directory(if (workspaceDir.exists()) workspaceDir else RuntimeEnv.projectsDir(context))
            environment().remove("LD_PRELOAD")
            environment().remove("LD_LIBRARY_PATH")
            environment().putAll(RuntimeEnv.buildEnvironment(context, apiKey))
        }

        val proc = try {
            pb.start()
        } catch (e: Exception) {
            trySend(AgyStreamEvent.LogLine("Failed to launch agy: ${e.message}", isError = true))
            close()
            return@callbackFlow
        }

        activeProcess.getAndSet(proc)?.let { old -> runCatching { old.destroyForcibly() } }
        runCatching { proc.outputStream.close() }

        val stderrThread = Thread {
            runCatching {
                proc.errorStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        val trimmed = line.trim()
                        if (trimmed.isNotEmpty()) {
                            trySend(AgyStreamEvent.LogLine(trimmed, isError = !trimmed.startsWith("warning:")))
                        }
                    }
                }
            }
        }
        stderrThread.start()

        try {
            proc.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    val trimmed = line.trim()
                    if (trimmed.isNotEmpty()) {
                        parseEventLine(trimmed)?.let { evt ->
                            trySend(evt)
                        }
                    }
                }
            }
            proc.waitFor()
        } catch (e: Exception) {
            trySend(AgyStreamEvent.LogLine("Stream error: ${e.message}", isError = true))
        } finally {
            stderrThread.join(500)
            activeProcess.compareAndSet(proc, null)
            close()
        }

        awaitClose {
            activeProcess.compareAndSet(proc, null)
            runCatching { proc.destroyForcibly() }
        }
    }.flowOn(Dispatchers.IO)

    private fun parseEventLine(line: String): AgyStreamEvent? {
        if (!line.startsWith("{")) {
            return AgyStreamEvent.LogLine(line, isError = false)
        }
        return try {
            val root = gson.fromJson(line, JsonObject::class.java) ?: return null
            val eventType = root.optString("event") ?: return null
            when (eventType) {
                "init" -> {
                    val convId = root.optString("conversation_id") ?: ""
                    val initObj = root.optObject("init")
                    val cwd = initObj?.optString("cwd") ?: ""
                    val perm = initObj?.optString("permission_mode") ?: ""
                    val tools = mutableListOf<String>()
                    initObj?.getAsJsonArray("tools")?.forEach { el ->
                        if (el.isJsonPrimitive) tools += el.asString
                    }
                    AgyStreamEvent.Init(convId, cwd, tools, perm)
                }

                "step_update" -> {
                    val su = root.optObject("step_update") ?: return null
                    val convId = su.optString("conversation_id") ?: ""
                    val stepIdx = su.optInt("step_index") ?: 0
                    val state = su.optString("state") ?: ""
                    val stepType = su.optString("step_type") ?: ""
                    val textDelta = su.optString("text_delta")
                    val duration = su.optDouble("duration_seconds")
                    val usage = su.optObject("usage")
                    val tokens = usage?.optLong("total_tokens")
                    val toolInfo = su.optObject("tool_info")
                    val toolName = su.optString("tool_name") ?: toolInfo?.optString("name")
                    val params = toolInfo?.get("parameters")?.let { formatJsonCompact(it) }
                    val output = toolInfo?.optString("output")
                    AgyStreamEvent.StepUpdate(
                        conversationId = convId,
                        stepIndex = stepIdx,
                        state = state,
                        stepType = stepType,
                        textDelta = textDelta,
                        toolName = toolName,
                        toolParamsPretty = params,
                        toolOutput = output,
                        durationSeconds = duration,
                        totalTokens = tokens
                    )
                }

                "result" -> {
                    val res = root.optObject("result") ?: return null
                    val convId = res.optString("conversation_id") ?: ""
                    val status = res.optString("status") ?: ""
                    val response = res.optString("response") ?: ""
                    val error = res.optString("error")
                    val duration = res.optDouble("duration_seconds")
                    val tokens = res.optObject("usage")?.optLong("total_tokens")
                    AgyStreamEvent.Result(convId, status, response, error, duration, tokens)
                }

                else -> AgyStreamEvent.LogLine(line, isError = false)
            }
        } catch (_: Exception) {
            AgyStreamEvent.LogLine(line, isError = false)
        }
    }

    suspend fun fetchModels(context: Context, apiKey: String? = null): List<AgyModelOption> =
        withContext(Dispatchers.IO) {
            val agy = RuntimeEnv.agyFile(context)
            if (!agy.canExecute()) return@withContext defaultModels
            val res = RuntimeEnv.runBinary(
                context = context,
                command = listOf(agy.absolutePath, "models"),
                apiKey = apiKey,
                timeoutSeconds = 15
            )
            if (res.exitCode != 0) return@withContext defaultModels
            val parsed = res.stdout.lines().mapNotNull { line ->
                val parts = line.split('\t')
                if (parts.size >= 2 && parts[0].isNotBlank()) {
                    AgyModelOption(parts[0].trim(), parts[1].trim())
                } else null
            }
            parsed.ifEmpty { defaultModels }
        }

    suspend fun loadSavedConversations(context: Context): List<SavedConversation> =
        withContext(Dispatchers.IO) {
            val home = RuntimeEnv.homeDir(context)
            val dbFile = File(home, ".gemini/antigravity-cli/conversation_summaries.db")
            if (dbFile.exists()) {
                val list = mutableListOf<SavedConversation>()
                runCatching {
                    SQLiteDatabase.openDatabase(
                        dbFile.absolutePath,
                        null,
                        SQLiteDatabase.OPEN_READONLY
                    ).use { db ->
                        val cursor = db.rawQuery(
                            """
                            SELECT conversation_id, title, preview, last_modified_time, workspace_uris
                            FROM conversation_summaries
                            WHERE COALESCE(parent_conversation_id, '') = ''
                            ORDER BY last_modified_time DESC
                            LIMIT 40
                            """.trimIndent(),
                            null
                        )
                        cursor.use { c ->
                            while (c.moveToNext()) {
                                val id = c.getString(0) ?: continue
                                val title = c.getString(1)?.takeIf { it.isNotBlank() } ?: "Untitled Session"
                                val preview = c.getString(2) ?: ""
                                val modified = c.getString(3) ?: ""
                                val uris = c.getString(4) ?: ""
                                val wsPath = extractWorkspacePath(uris)
                                list += SavedConversation(id, title, preview, modified, wsPath)
                            }
                        }
                    }
                }
                if (list.isNotEmpty()) return@withContext list
            }

            // Fallback to history.jsonl
            val histFile = File(home, ".gemini/antigravity-cli/history.jsonl")
            if (!histFile.exists()) return@withContext emptyList()
            val byId = linkedMapOf<String, SavedConversation>()
            runCatching {
                histFile.readLines().asReversed().forEach { raw ->
                    if (raw.isBlank()) return@forEach
                    val obj = gson.fromJson(raw, JsonObject::class.java) ?: return@forEach
                    val id = obj.optString("conversationId") ?: return@forEach
                    if (!byId.containsKey(id)) {
                        val display = obj.optString("display") ?: "Conversation"
                        val ws = obj.optString("workspace")
                        val ts = obj.optLong("timestamp")?.toString() ?: ""
                        byId[id] = SavedConversation(id, display.take(64), display, ts, ws)
                    }
                }
            }
            byId.values.take(40)
        }

    private fun extractWorkspacePath(workspaceUrisJson: String): String? {
        val match = Regex("file://([^\"\\]]+)").find(workspaceUrisJson)
        return match?.groupValues?.getOrNull(1)
    }

    private fun formatJsonCompact(el: JsonElement): String {
        return try {
            if (el.isJsonObject) {
                el.asJsonObject.entrySet().joinToString(", ") { (k, v) ->
                    val vs = if (v.isJsonPrimitive) v.asString else v.toString()
                    "$k=${vs.take(120)}"
                }
            } else el.toString()
        } catch (_: Exception) {
            el.toString()
        }
    }

    private fun JsonObject.optObject(key: String): JsonObject? =
        get(key)?.takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonObject.optString(key: String): String? =
        get(key)?.takeIf { it.isJsonPrimitive }?.asString

    private fun JsonObject.optInt(key: String): Int? =
        get(key)?.takeIf { it.isJsonPrimitive }?.asInt

    private fun JsonObject.optLong(key: String): Long? =
        get(key)?.takeIf { it.isJsonPrimitive }?.asLong

    private fun JsonObject.optDouble(key: String): Double? =
        get(key)?.takeIf { it.isJsonPrimitive }?.asDouble
}
