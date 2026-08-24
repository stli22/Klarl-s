package com.klarl.accessibility.ai

import org.json.JSONArray
import org.json.JSONObject

/**
 * Builds the system prompts and JSON-schema output constraints ([output_config.format]) for the
 * two requests this app makes to Claude. Structured outputs (rather than free text + best-effort
 * parsing) are used so the on-device parsing code can trust the response shape.
 */
object ClaudePromptBuilder {

    fun screenSummarySystemPrompt(): String = """
        Du är en tillgänglighetsassistent som hjälper en synskadad användare förstå vad som
        visas på skärmen i en Android-app, utifrån en textbeskrivning av skärmens UI-träd.
        Beskrivningen kan innehålla fält märkta [maskerat fält] eller [maskerad uppgift] -
        dessa är lösenord/betalningsuppgifter som är dolda för dig av säkerhetsskäl; nämn att
        fältet finns men gissa aldrig dess innehåll.

        Svara ALLTID på svenska, kort och konkret - användaren lyssnar på svaret, inte läser det.
        Ge:
        - en kort sammanfattning (1-3 meningar) av vad skärmen innehåller och vad man kan göra där
        - 2 till 4 förslag på relevanta nästa steg, formulerade som korta talade instruktioner
          användaren skulle kunna säga (t.ex. "läs artikeln", "hoppa till formuläret",
          "visa alla rubriker")
    """.trimIndent()

    fun screenSummarySchema(): JSONObject = JSONObject().apply {
        put("type", "object")
        put(
            "properties",
            JSONObject().apply {
                put("summary", JSONObject().apply { put("type", "string") })
                put(
                    "options",
                    JSONObject().apply {
                        put("type", "array")
                        put("items", JSONObject().apply { put("type", "string") })
                        put("minItems", 2)
                        put("maxItems", 4)
                    }
                )
            }
        )
        put("required", JSONArray(listOf("summary", "options")))
        put("additionalProperties", false)
    }

    fun commandInterpretationSystemPrompt(): String = """
        Du är en tillgänglighetsassistent som tolkar ett otydligt eller komplext röstkommando
        från en synskadad användare, utifrån den senast kända skärmbeskrivningen (samma format
        som skärmsammanfattningar: indenterad text med rubrik/knapp/länk/textfält/etc).

        Välj EN av dessa handlingar (fältet "action"):
        - READ_MORE: användaren vill höra mer detaljer om nuvarande skärm
        - REPEAT_SUMMARY: användaren vill höra sammanfattningen igen
        - GO_BACK: användaren vill navigera bakåt
        - READ_HEADINGS: användaren vill höra alla rubriker på skärmen
        - NAVIGATE_TO: användaren vill hoppa till ett specifikt element (ange det i targetDescription,
          med exakt text/etikett från skärmbeskrivningen)
        - ACTIVATE_ELEMENT: användaren vill trycka på/aktivera ett specifikt element (ange det i
          targetDescription, med exakt text/etikett från skärmbeskrivningen)
        - UNKNOWN: kommandot går inte att tolka mot den kända skärmen

        Sätt requiresConfirmation till true om handlingen är potentiellt känslig eller svår att
        ångra (t.ex. skicka, radera, köpa, betala, skriva i ett formulär, eller om targetDescription
        pekar på en knapp med sådan innebörd). Skriv ett kort, talat svar på svenska i
        spokenResponse som antingen bekräftar vad du ska göra eller förklarar varför du inte förstod.
    """.trimIndent()

    fun commandInterpretationSchema(): JSONObject = JSONObject().apply {
        put("type", "object")
        put(
            "properties",
            JSONObject().apply {
                put(
                    "action",
                    JSONObject().apply {
                        put("type", "string")
                        put(
                            "enum",
                            JSONArray(
                                listOf(
                                    "READ_MORE", "REPEAT_SUMMARY", "GO_BACK", "READ_HEADINGS",
                                    "NAVIGATE_TO", "ACTIVATE_ELEMENT", "UNKNOWN"
                                )
                            )
                        )
                    }
                )
                put(
                    "targetDescription",
                    JSONObject().apply { put("type", JSONArray(listOf("string", "null"))) }
                )
                put("spokenResponse", JSONObject().apply { put("type", "string") })
                put("requiresConfirmation", JSONObject().apply { put("type", "boolean") })
            }
        )
        put(
            "required",
            JSONArray(listOf("action", "targetDescription", "spokenResponse", "requiresConfirmation"))
        )
        put("additionalProperties", false)
    }
}
