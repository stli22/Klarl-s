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

    /**
     * Screen summaries and command interpretation are both short *answers*, but Claude Opus 5
     * runs adaptive thinking by default, which spends output tokens on reasoning before it
     * writes the actual text - a low max_tokens can be entirely consumed by thinking, leaving
     * stop_reason=max_tokens and no text block at all (see ClaudeApiClient's handling of that).
     * 2048 leaves headroom for thinking + a still-short structured JSON answer.
     */
    const val MAX_TOKENS = 2048L

    /**
     * These are simple, schema-constrained tasks (a short summary, or picking one of a handful
     * of enum actions) - "low" effort keeps Claude's adaptive thinking brief, which both avoids
     * the max_tokens problem above and keeps latency down for the ~3s narration target.
     */
    const val EFFORT = "low"

    const val CONNECT_TIMEOUT_SECONDS = 5L
    const val READ_TIMEOUT_SECONDS = 15L
}
