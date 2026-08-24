package com.klarl.accessibility.actions

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Performs the actual UI actions (click, navigate-to/focus, back) that a resolved voice command
 * maps to. Deliberately re-queries the live window via [AccessibilityService.getRootInActiveWindow]
 * rather than reusing [com.klarl.accessibility.model.ScreenNode] data, because
 * [AccessibilityNodeInfo] instances go stale within moments of being obtained - by the time a
 * command has round-tripped through STT (and possibly Claude), the tree we extracted earlier may
 * no longer be valid.
 *
 * Talks to the framework [AccessibilityNodeInfo] type directly rather than the
 * [com.klarl.accessibility.extraction.AccessibilityNode] read-only abstraction used elsewhere,
 * since performing actions isn't meaningfully unit-testable without a real window anyway.
 */
class ScreenActionExecutor(private val service: AccessibilityService) {

    private companion object {
        const val TAG = "KlarlActionExecutor"
    }

    fun goBack(): Boolean = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)

    /** Clicks the element whose visible text/label best matches [targetDescription]. */
    fun activateElement(targetDescription: String): Boolean =
        withMatchingClickableNode(targetDescription) { node ->
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }

    /** Moves accessibility focus to the element matching [targetDescription] (no activation). */
    fun navigateTo(targetDescription: String): Boolean =
        withMatchingNode(targetDescription) { node ->
            node.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
        }

    private fun withMatchingClickableNode(label: String, action: (AccessibilityNodeInfo) -> Boolean): Boolean {
        val root = service.rootInActiveWindow ?: return false
        try {
            val candidate = root.findAccessibilityNodeInfosByText(label)?.firstOrNull() ?: return false
            val clickable = nearestClickableSelfOrAncestor(candidate) ?: return false
            return action(clickable)
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Nod blev ogiltig innan åtgärden kunde utföras", e)
            return false
        } finally {
            root.recycle()
        }
    }

    private fun withMatchingNode(label: String, action: (AccessibilityNodeInfo) -> Boolean): Boolean {
        val root = service.rootInActiveWindow ?: return false
        try {
            val candidate = root.findAccessibilityNodeInfosByText(label)?.firstOrNull() ?: return false
            return action(candidate)
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Nod blev ogiltig innan åtgärden kunde utföras", e)
            return false
        } finally {
            root.recycle()
        }
    }

    private fun nearestClickableSelfOrAncestor(start: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = start
        var depth = 0
        while (current != null && depth < 20) {
            if (current.isClickable) return current
            current = current.parent
            depth++
        }
        return null
    }
}
