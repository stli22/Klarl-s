package com.klarl.accessibility.model

/**
 * Compact, platform-independent representation of one node in a screen's UI tree.
 *
 * This is deliberately *not* a wrapper around [android.view.accessibility.AccessibilityNodeInfo]
 * itself: node info instances are short-lived, expensive, and impossible to unit test without
 * Robolectric. [ScreenNode] is what survives extraction - the [role]/[text]/[contentDescription]
 * fields are exactly what gets sent to the AI backend (after masking), so nothing sensitive
 * should ever be added here without going through [isSensitive] handling first.
 */
data class ScreenNode(
    val role: ScreenRole,
    val text: String?,
    val contentDescription: String?,
    val viewIdResourceName: String? = null,
    val isSensitive: Boolean = false,
    val isClickable: Boolean = false,
    val isEditable: Boolean = false,
    val isChecked: Boolean? = null,
    val children: List<ScreenNode> = emptyList()
) {
    /** True if this node (ignoring children) carries no information worth sending to the AI. */
    fun isEmptyLeaf(): Boolean =
        children.isEmpty() &&
            text.isNullOrBlank() &&
            contentDescription.isNullOrBlank() &&
            !isClickable &&
            !isEditable &&
            isChecked == null
}

enum class ScreenRole {
    HEADING,
    BUTTON,
    LINK,
    TEXT_FIELD,
    PASSWORD_FIELD,
    IMAGE,
    CHECKBOX,
    SWITCH,
    TEXT,
    CONTAINER,
    UNKNOWN
}
