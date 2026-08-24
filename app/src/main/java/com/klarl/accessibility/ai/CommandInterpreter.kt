package com.klarl.accessibility.ai

import com.klarl.accessibility.model.LocalCommand

/**
 * First stage of voice-command handling: fast, offline, keyword-based matching for the small
 * set of commands the spec calls out explicitly ("hoppa, läs mer, gå tillbaka"). Anything that
 * doesn't match falls through to [LocalCommand.NeedsAiInterpretation], which the caller resolves
 * via [ClaudeApiClient.interpretCommand] against the last known screen.
 *
 * Pure function of the input text - no I/O, no Android dependency - so it's trivially unit testable.
 */
object CommandInterpreter {

    private val goBackPhrases = listOf("gå tillbaka", "tillbaka", "backa", "avbryt")
    private val readMorePhrases = listOf("läs mer", "mer information", "fortsätt läsa", "läs vidare")
    private val repeatPhrases = listOf("upprepa", "säg igen", "vad sa du", "läs igen")
    private val headingsPhrases = listOf("visa alla rubriker", "läs alla rubriker", "rubriker", "visa rubriker")

    fun classify(rawText: String): LocalCommand {
        val text = normalize(rawText)
        if (text.isBlank()) return LocalCommand.NeedsAiInterpretation(rawText)

        selectOptionIndex(text)?.let { return LocalCommand.SelectOption(it) }

        return when {
            goBackPhrases.any { text.contains(it) } -> LocalCommand.GoBack
            readMorePhrases.any { text.contains(it) } -> LocalCommand.ReadMore
            repeatPhrases.any { text.contains(it) } -> LocalCommand.RepeatSummary
            headingsPhrases.any { text.contains(it) } -> LocalCommand.ReadHeadings
            else -> LocalCommand.NeedsAiInterpretation(rawText)
        }
    }

    private val ordinalWords = mapOf(
        "första" to 1, "ett" to 1, "en" to 1,
        "andra" to 2, "två" to 2,
        "tredje" to 3, "tre" to 3,
        "fjärde" to 4, "fyra" to 4
    )

    /** Matches phrases like "alternativ två" / "välj första" / "nummer 2". */
    private fun selectOptionIndex(text: String): Int? {
        if (!(text.contains("alternativ") || text.contains("välj") || text.contains("nummer"))) return null
        Regex("""\d+""").find(text)?.let { return it.value.toIntOrNull() }
        ordinalWords.entries.firstOrNull { (word, _) -> text.contains(word) }?.let { return it.value }
        return null
    }

    private fun normalize(text: String): String = text.trim().lowercase()
}
