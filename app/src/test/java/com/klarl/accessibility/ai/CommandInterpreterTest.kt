package com.klarl.accessibility.ai

import com.klarl.accessibility.model.LocalCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandInterpreterTest {

    @Test
    fun `go back phrase is classified locally`() {
        assertEquals(LocalCommand.GoBack, CommandInterpreter.classify("gå tillbaka"))
        assertEquals(LocalCommand.GoBack, CommandInterpreter.classify("Backa"))
    }

    @Test
    fun `read more phrase is classified locally`() {
        assertEquals(LocalCommand.ReadMore, CommandInterpreter.classify("läs mer"))
    }

    @Test
    fun `repeat phrase is classified locally`() {
        assertEquals(LocalCommand.RepeatSummary, CommandInterpreter.classify("säg igen"))
    }

    @Test
    fun `headings phrase is classified locally`() {
        assertEquals(LocalCommand.ReadHeadings, CommandInterpreter.classify("visa alla rubriker"))
    }

    @Test
    fun `numeric option selection is classified locally`() {
        assertEquals(LocalCommand.SelectOption(2), CommandInterpreter.classify("alternativ 2"))
    }

    @Test
    fun `ordinal word option selection is classified locally`() {
        assertEquals(LocalCommand.SelectOption(1), CommandInterpreter.classify("välj första"))
    }

    @Test
    fun `unrecognized free-form command falls through to AI interpretation`() {
        val result = CommandInterpreter.classify("öppna inställningarna för notiser")
        assertTrue(result is LocalCommand.NeedsAiInterpretation)
        assertEquals("öppna inställningarna för notiser", (result as LocalCommand.NeedsAiInterpretation).rawText)
    }

    @Test
    fun `blank input falls through to AI interpretation rather than crashing`() {
        assertTrue(CommandInterpreter.classify("") is LocalCommand.NeedsAiInterpretation)
    }
}
