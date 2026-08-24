package com.klarl.accessibility.serialization

import com.klarl.accessibility.model.ScreenNode
import com.klarl.accessibility.model.ScreenRole
import com.klarl.accessibility.model.ScreenSnapshot

/**
 * Turns a [ScreenSnapshot] into the compact, indented-text representation sent to Claude.
 *
 * Indented text rather than JSON: it carries the same structural information (nesting = tree
 * shape) in noticeably fewer tokens than an equivalent JSON tree (no repeated key names or
 * brace/quote overhead), which matters for the ~3s round-trip acceptance target.
 */
object ScreenSnapshotSerializer {

    fun toCompactText(snapshot: ScreenSnapshot): String = buildString {
        append("App: ").append(snapshot.packageName).append('\n')
        if (!snapshot.windowTitle.isNullOrBlank()) {
            append("Skärm: ").append(snapshot.windowTitle).append('\n')
        }
        if (snapshot.isEmpty) {
            append("(inget läsbart innehåll hittades)")
            return@buildString
        }
        snapshot.rootNodes.forEach { appendNode(it, depth = 0) }
    }

    private fun StringBuilder.appendNode(node: ScreenNode, depth: Int) {
        append("  ".repeat(depth))
        append("- ").append(roleLabel(node.role))
        val label = describeLabel(node)
        if (label != null) append(": ").append(label)
        if (node.isChecked != null) append(if (node.isChecked) " [ikryssad]" else " [ej ikryssad]")
        append('\n')
        node.children.forEach { appendNode(it, depth + 1) }
    }

    private fun describeLabel(node: ScreenNode): String? = when {
        node.isSensitive -> maskedLabel(node)
        !node.text.isNullOrBlank() -> "\"${node.text}\""
        !node.contentDescription.isNullOrBlank() -> "\"${node.contentDescription}\" (alt-text)"
        else -> null
    }

    // Fixed protocol markers sent to Claude (see ClaudePromptBuilder's system prompt, which
    // references this exact text) - not user-facing UI copy, so intentionally not a string
    // resource.
    private fun maskedLabel(node: ScreenNode): String =
        if (node.role == ScreenRole.PASSWORD_FIELD) "[maskerat fält]" else "[maskerad uppgift]"

    private fun roleLabel(role: ScreenRole): String = when (role) {
        ScreenRole.HEADING -> "rubrik"
        ScreenRole.BUTTON -> "knapp"
        ScreenRole.LINK -> "länk"
        ScreenRole.TEXT_FIELD -> "textfält"
        ScreenRole.PASSWORD_FIELD -> "lösenordsfält"
        ScreenRole.IMAGE -> "bild"
        ScreenRole.CHECKBOX -> "kryssruta"
        ScreenRole.SWITCH -> "växlingsknapp"
        ScreenRole.TEXT -> "text"
        ScreenRole.CONTAINER -> "grupp"
        ScreenRole.UNKNOWN -> "element"
    }
}
