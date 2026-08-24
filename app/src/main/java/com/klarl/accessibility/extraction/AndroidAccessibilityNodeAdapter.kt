package com.klarl.accessibility.extraction

import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo

/** Wraps a real [AccessibilityNodeInfo] behind the platform-independent [AccessibilityNode] interface. */
class AndroidAccessibilityNodeAdapter(
    private val node: AccessibilityNodeInfo
) : AccessibilityNode {

    override val className: String? get() = node.className?.toString()
    override val text: String? get() = node.text?.toString()
    override val contentDescription: String? get() = node.contentDescription?.toString()
    override val viewIdResourceName: String? get() = node.viewIdResourceName
    override val hintText: String?
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) node.hintText?.toString() else null
    override val isPassword: Boolean get() = node.isPassword
    override val isVisibleToUser: Boolean get() = node.isVisibleToUser
    override val isClickable: Boolean get() = node.isClickable
    override val isCheckable: Boolean get() = node.isCheckable
    override val isChecked: Boolean get() = node.isChecked
    override val isEditable: Boolean get() = node.isEditable
    override val isHeading: Boolean
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) node.isHeading else false
    override val childCount: Int get() = node.childCount

    override fun getChild(index: Int): AccessibilityNode? {
        val child = node.getChild(index) ?: return null
        return AndroidAccessibilityNodeAdapter(child)
    }

    override fun recycle() {
        @Suppress("DEPRECATION")
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                node.recycle()
            }
        } catch (_: IllegalStateException) {
            // Already recycled elsewhere - safe to ignore.
        }
    }
}
