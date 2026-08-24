package com.klarl.accessibility.ai

import com.klarl.accessibility.BuildConfig

/**
 * Central place for Claude API settings. The API key and model come from `local.properties`
 * (see app/build.gradle.kts) via [BuildConfig] - never hardcode a key here.
 *
 * Model defaults to claude-opus-5. If real-device measurements show the ~3s narration budget
 * (see spec acceptance criteria) can't be hit with Opus on a given connection, this is the one
 * place to swap in a faster model (e.g. claude-sonnet-5) - override CLAUDE_MODEL in
 * local.properties rather than editing this file.
 */
object ClaudeConfig {
    const val API_URL = "https://api.anthropic.com/v1/messages"
    const val ANTHROPIC_VERSION = "2023-06-01"

    val apiKey: String get() = BuildConfig.CLAUDE_API_KEY
    val model: String get() = BuildConfig.CLAUDE_MODEL
    val isConfigured: Boolean get() = apiKey.isNotBlank()

    /** Screen summaries and command interpretation are both short, so this is generous headroom. */
    const val MAX_TOKENS = 1024L

    const val CONNECT_TIMEOUT_SECONDS = 5L
    const val READ_TIMEOUT_SECONDS = 8L
}
