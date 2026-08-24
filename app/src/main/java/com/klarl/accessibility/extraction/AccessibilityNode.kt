package com.klarl.accessibility.extraction

/**
 * Minimal, platform-independent view of an [android.view.accessibility.AccessibilityNodeInfo].
 *
 * [NodeExtractor] operates entirely against this interface rather than the Android framework
 * class directly, so extraction/masking logic can be unit tested with plain JUnit (using
 * [FakeAccessibilityNode]) instead of needing Robolectric to mock a final Android class.
 */
interface AccessibilityNode {
    val className: String?
    val text: String?
    val contentDescription: String?
    val viewIdResourceName: String?
    val hintText: String?
    val isPassword: Boolean
    val isVisibleToUser: Boolean
    val isClickable: Boolean
    val isCheckable: Boolean
    val isChecked: Boolean
    val isEditable: Boolean
    val isHeading: Boolean
    val childCount: Int
    fun getChild(index: Int): AccessibilityNode?

    /** No-op for anything that isn't backed by a real, poolable framework object. */
    fun recycle() {}
}

/** Simple in-memory implementation used by tests and any future non-Android callers. */
data class FakeAccessibilityNode(
    override val className: String? = null,
    override val text: String? = null,
    override val contentDescription: String? = null,
    override val viewIdResourceName: String? = null,
    override val hintText: String? = null,
    override val isPassword: Boolean = false,
    override val isVisibleToUser: Boolean = true,
    override val isClickable: Boolean = false,
    override val isCheckable: Boolean = false,
    override val isChecked: Boolean = false,
    override val isEditable: Boolean = false,
    override val isHeading: Boolean = false,
    val children: List<AccessibilityNode> = emptyList()
) : AccessibilityNode {
    override val childCount: Int get() = children.size
    override fun getChild(index: Int): AccessibilityNode? = children.getOrNull(index)
}
