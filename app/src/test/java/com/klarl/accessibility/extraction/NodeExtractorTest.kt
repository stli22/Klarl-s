package com.klarl.accessibility.extraction

import com.klarl.accessibility.model.ScreenRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NodeExtractorTest {

    private val extractor = NodeExtractor()

    @Test
    fun `password field text never appears in extracted tree`() {
        val root = FakeAccessibilityNode(
            children = listOf(
                FakeAccessibilityNode(
                    className = "android.widget.EditText",
                    text = "hunter2",
                    isPassword = true,
                    isEditable = true
                )
            )
        )

        val result = extractor.extract(root)

        assertEquals(1, result.size)
        val passwordNode = result.first()
        assertEquals(ScreenRole.PASSWORD_FIELD, passwordNode.role)
        assertTrue(passwordNode.isSensitive)
        assertNull(passwordNode.text)
        assertNull(passwordNode.contentDescription)
    }

    @Test
    fun `invisible nodes are dropped`() {
        val root = FakeAccessibilityNode(
            children = listOf(
                FakeAccessibilityNode(text = "Hidden", isVisibleToUser = false),
                FakeAccessibilityNode(text = "Visible", className = "android.widget.TextView")
            )
        )

        val result = extractor.extract(root)

        assertEquals(1, result.size)
        assertEquals("Visible", result.first().text)
    }

    @Test
    fun `empty layout wrappers are flattened away`() {
        val root = FakeAccessibilityNode(
            children = listOf(
                FakeAccessibilityNode(
                    className = "android.widget.LinearLayout",
                    children = listOf(
                        FakeAccessibilityNode(
                            className = "android.widget.LinearLayout",
                            children = listOf(
                                FakeAccessibilityNode(
                                    className = "android.widget.TextView",
                                    text = "Rubrik",
                                    isHeading = true
                                )
                            )
                        )
                    )
                )
            )
        )

        val result = extractor.extract(root)

        assertEquals(1, result.size)
        assertEquals(ScreenRole.HEADING, result.first().role)
        assertEquals("Rubrik", result.first().text)
    }

    @Test
    fun `ad container subtree is dropped entirely`() {
        val root = FakeAccessibilityNode(
            children = listOf(
                FakeAccessibilityNode(
                    viewIdResourceName = "com.example.app:id/ad_container",
                    children = listOf(FakeAccessibilityNode(text = "Köp nu!"))
                ),
                FakeAccessibilityNode(text = "Riktigt innehåll", className = "android.widget.TextView")
            )
        )

        val result = extractor.extract(root)

        assertEquals(1, result.size)
        assertEquals("Riktigt innehåll", result.first().text)
    }

    @Test
    fun `node count is capped by config`() {
        val manyChildren = (1..50).map {
            FakeAccessibilityNode(text = "Item $it", className = "android.widget.TextView")
        }
        val root = FakeAccessibilityNode(children = manyChildren)
        val cappedExtractor = NodeExtractor(NodeExtractionConfig(maxNodes = 10))

        val result = cappedExtractor.extract(root)

        assertTrue(result.size <= 10)
    }

    @Test
    fun `long run of identical siblings is collapsed`() {
        val repeatedRows = (1..6).map {
            FakeAccessibilityNode(text = "Annons", className = "android.widget.TextView")
        }
        val root = FakeAccessibilityNode(children = repeatedRows)

        val result = extractor.extract(root)

        assertEquals(1, result.size)
        assertTrue(result.first().text!!.contains("×6"))
    }

    @Test
    fun `clickable element with no text is not silently dropped`() {
        val root = FakeAccessibilityNode(
            children = listOf(
                FakeAccessibilityNode(
                    className = "android.widget.ImageButton",
                    contentDescription = "Stäng",
                    isClickable = true
                )
            )
        )

        val result = extractor.extract(root)

        assertEquals(1, result.size)
        assertFalse(result.first().contentDescription.isNullOrBlank())
    }
}
