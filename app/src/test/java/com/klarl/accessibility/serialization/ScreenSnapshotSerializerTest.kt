package com.klarl.accessibility.serialization

import com.klarl.accessibility.model.ScreenNode
import com.klarl.accessibility.model.ScreenRole
import com.klarl.accessibility.model.ScreenSnapshot
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenSnapshotSerializerTest {

    @Test
    fun `masked password node never leaks its value in serialized output`() {
        val snapshot = ScreenSnapshot(
            packageName = "com.example.bank",
            windowTitle = "Logga in",
            timestampMillis = 0L,
            rootNodes = listOf(
                ScreenNode(
                    role = ScreenRole.PASSWORD_FIELD,
                    text = null,
                    contentDescription = null,
                    isSensitive = true,
                    isEditable = true
                )
            )
        )

        val output = ScreenSnapshotSerializer.toCompactText(snapshot)

        assertTrue(output.contains("[maskerat fält]"))
        assertFalse(output.contains("hunter2"))
    }

    @Test
    fun `serialized output includes package name and node text`() {
        val snapshot = ScreenSnapshot(
            packageName = "com.example.news",
            windowTitle = null,
            timestampMillis = 0L,
            rootNodes = listOf(
                ScreenNode(role = ScreenRole.HEADING, text = "Dagens nyheter", contentDescription = null)
            )
        )

        val output = ScreenSnapshotSerializer.toCompactText(snapshot)

        assertTrue(output.contains("com.example.news"))
        assertTrue(output.contains("Dagens nyheter"))
    }

    @Test
    fun `empty snapshot produces a readable placeholder instead of an empty string`() {
        val snapshot = ScreenSnapshot("com.example.empty", null, 0L, emptyList())

        val output = ScreenSnapshotSerializer.toCompactText(snapshot)

        assertTrue(output.contains("inget läsbart innehåll"))
    }
}
