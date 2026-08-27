package com.klarl.accessibility.ai

import com.klarl.accessibility.model.CommandActionType
import com.klarl.accessibility.model.InterpretedCommand
import com.klarl.accessibility.model.NavigationOption
import com.klarl.accessibility.model.ScreenSummary
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Thin, direct client for the Claude Messages API (`POST /v1/messages`) using OkHttp + org.json.
 *
 * Deliberately raw HTTP instead of the official `anthropic-java` SDK: that SDK is built for JVM
 * server processes (Jackson-based reflection, not tuned for Android/R8/APK size) and this app
 * calls Claude directly from an Android client. That direct-from-client call pattern is itself
 * only meant for this prototype phase - see README.md "Säkerhet och integritet": production
 * should proxy through a backend that never ships the API key to the device, at which point that
 * backend is a natural place to use the official SDK instead of this class.
 */
class ClaudeApiClient(
    private val apiKey: String = ClaudeConfig.apiKey,
    private val model: String = ClaudeConfig.model,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(ClaudeConfig.CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(ClaudeConfig.READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()
) {
    private val jsonMediaType = "application/json".toMediaType()

    suspend fun summarizeScreen(screenDescription: String): Result<ScreenSummary> {
        val body = requestBody(
            system = ClaudePromptBuilder.screenSummarySystemPrompt(),
            userContent = screenDescription,
            schema = ClaudePromptBuilder.screenSummarySchema()
        )
        return execute(body).mapCatching { json ->
            ScreenSummary(
                summaryText = json.getString("summary"),
                options = json.getJSONArray("options").let { arr ->
                    (0 until arr.length()).map { NavigationOption(arr.getString(it)) }
                }
            )
        }
    }

    suspend fun interpretCommand(
        spokenCommand: String,
        lastScreenDescription: String
    ): Result<InterpretedCommand> {
        val userContent = buildString {
            append("Senast kända skärm:\n")
            append(lastScreenDescription)
            append("\n\nAnvändarens röstkommando: \"")
            append(spokenCommand)
            append("\"")
        }
        val body = requestBody(
            system = ClaudePromptBuilder.commandInterpretationSystemPrompt(),
            userContent = userContent,
            schema = ClaudePromptBuilder.commandInterpretationSchema()
        )
        return execute(body).mapCatching { json ->
            InterpretedCommand(
                action = runCatching { CommandActionType.valueOf(json.getString("action")) }
                    .getOrDefault(CommandActionType.UNKNOWN),
                targetDescription = if (json.isNull("targetDescription")) null else json.optString("targetDescription", null),
                spokenResponse = json.getString("spokenResponse"),
                requiresConfirmation = json.getBoolean("requiresConfirmation")
            )
        }
    }

    private fun requestBody(system: String, userContent: String, schema: JSONObject): JSONObject =
        JSONObject().apply {
            put("model", model)
            put("max_tokens", ClaudeConfig.MAX_TOKENS)
            put("system", system)
            put(
                "messages",
                JSONArray().put(
                    JSONObject().apply {
                        put("role", "user")
                        put("content", userContent)
                    }
                )
            )
            put(
                "output_config",
                JSONObject().apply {
                    put("effort", ClaudeConfig.EFFORT)
                    put(
                        "format",
                        JSONObject().apply {
                            put("type", "json_schema")
                            put("schema", schema)
                        }
                    )
                }
            )
        }

    private suspend fun execute(body: JSONObject): Result<JSONObject> {
        if (apiKey.isBlank()) {
            return Result.failure(IllegalStateException("Ingen Claude API-nyckel konfigurerad"))
        }
        return runCatching {
            val request = Request.Builder()
                .url(ClaudeConfig.API_URL)
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", ClaudeConfig.ANTHROPIC_VERSION)
                .addHeader("content-type", "application/json")
                .post(body.toString().toRequestBody(jsonMediaType))
                .build()

            val responseBody = awaitCall(request)
            val responseJson = JSONObject(responseBody)

            if (responseJson.has("error")) {
                val message = responseJson.optJSONObject("error")?.optString("message") ?: "Okänt API-fel"
                throw IOException("Claude API-fel: $message")
            }

            // output_config.format guarantees a text block *if Claude gets to write one* - but
            // adaptive thinking spends output tokens before the text block, so a too-tight
            // max_tokens can hit stop_reason=max_tokens with only a thinking block and no text
            // at all. Surface that case with a clear message instead of a bare "no such element".
            val contentBlocks = responseJson.getJSONArray("content")
                .let { arr -> (0 until arr.length()).map { arr.getJSONObject(it) } }
            val textBlock = contentBlocks.firstOrNull { it.optString("type") == "text" }
            if (textBlock == null) {
                val stopReason = responseJson.optString("stop_reason", "okänd")
                throw IOException(
                    "Inget textsvar från Claude (stop_reason=$stopReason) - troligen tog " +
                        "'thinking' hela max_tokens-budgeten. Höj ClaudeConfig.MAX_TOKENS " +
                        "eller sänk ClaudeConfig.EFFORT."
                )
            }

            JSONObject(textBlock.getString("text"))
        }
    }

    private suspend fun awaitCall(request: Request): String = suspendCancellableCoroutine { continuation ->
        val call = httpClient.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val bodyString = it.body?.string().orEmpty()
                    if (!it.isSuccessful && bodyString.isBlank()) {
                        continuation.resumeWithException(IOException("HTTP ${it.code} från Claude API"))
                        return
                    }
                    continuation.resume(bodyString)
                }
            }
        })
    }
}
