package com.klarl.accessibility.extraction

import com.klarl.accessibility.model.ScreenNode
import com.klarl.accessibility.model.ScreenRole

/** Tuning knobs kept separate from the algorithm so tests can shrink limits deterministically. */
data class NodeExtractionConfig(
    /** Hard cap on how many nodes end up in the tree we send to Claude - keeps prompts small
     *  and the round trip within the ~3s acceptance target even on deeply nested screens. */
    val maxNodes: Int = 80,
    val maxDepth: Int = 40,
    /** Consecutive sibling nodes with identical (role, text) beyond this count are collapsed
     *  into one "×N upprepade element" entry - handles repeated list/feed rows. */
    val repeatedSiblingCollapseThreshold: Int = 3
)

/**
 * Walks an [AccessibilityNode] tree and turns it into the compact [ScreenNode] tree that gets
 * sent to Claude, per the spec:
 *  - drop invisible nodes
 *  - drop nodes/subtrees that are pure noise (ad containers, empty layout wrappers)
 *  - flatten uninformative container nodes so the AI sees content, not layout
 *  - mask sensitive fields via [SensitiveFieldMasker] *before* text ever reaches a [ScreenNode]
 *  - collapse obviously-repeated rows and cap total node count
 */
class NodeExtractor(private val config: NodeExtractionConfig = NodeExtractionConfig()) {

    private val adNoiseKeywords = listOf(
        "admob", "ad_container", "adcontainer", "banner_ad", "ad_banner",
        "ad_frame", "adview", "sponsored", "annons"
    )

    fun extract(root: AccessibilityNode): List<ScreenNode> {
        val budget = NodeBudget(config.maxNodes)
        val nodes = extractChildren(root, depth = 0, budget = budget)
        return collapseRepeatedSiblings(nodes)
    }

    private class NodeBudget(var remaining: Int) {
        fun tryConsume(): Boolean {
            if (remaining <= 0) return false
            remaining--
            return true
        }
    }

    private fun extractChildren(
        parent: AccessibilityNode,
        depth: Int,
        budget: NodeBudget
    ): List<ScreenNode> {
        if (depth >= config.maxDepth) return emptyList()
        val result = mutableListOf<ScreenNode>()
        for (i in 0 until parent.childCount) {
            if (budget.remaining <= 0) break
            val child = parent.getChild(i) ?: continue
            try {
                result += extractNode(child, depth + 1, budget)
            } finally {
                child.recycle()
            }
        }
        return result
    }

    /** Returns 0 or 1 ScreenNode for [node] - 0 if it (and its subtree) is pure noise/empty. */
    private fun extractNode(node: AccessibilityNode, depth: Int, budget: NodeBudget): List<ScreenNode> {
        if (!node.isVisibleToUser) return emptyList()
        if (isAdNoise(node)) return emptyList()

        val role = classifyRole(node)
        val sensitive = SensitiveFieldMasker.isSensitive(node)
        val (text, contentDescription) = if (sensitive) {
            null to null
        } else {
            node.text?.takeIf { it.isNotBlank() } to node.contentDescription?.takeIf { it.isNotBlank() }
        }

        val ownInfoPresent = sensitive || role == ScreenRole.IMAGE ||
            !text.isNullOrBlank() || !contentDescription.isNullOrBlank() ||
            node.isClickable || node.isEditable || node.isCheckable

        val children = extractChildren(node, depth, budget)

        if (!ownInfoPresent) {
            // Pure layout wrapper: flatten it away and surface its children directly, so the
            // AI sees "button, heading, text" rather than ten nested LinearLayouts.
            return children
        }

        if (!budget.tryConsume()) return emptyList()

        val screenNode = ScreenNode(
            role = role,
            text = text,
            contentDescription = contentDescription,
            viewIdResourceName = node.viewIdResourceName,
            isSensitive = sensitive,
            isClickable = node.isClickable,
            isEditable = node.isEditable,
            isChecked = if (node.isCheckable) node.isChecked else null,
            children = children
        )
        if (screenNode.isEmptyLeaf()) return emptyList()
        return listOf(screenNode)
    }

    private fun classifyRole(node: AccessibilityNode): ScreenRole {
        val cls = node.className.orEmpty()
        return when {
            SensitiveFieldMasker.isSensitive(node) -> ScreenRole.PASSWORD_FIELD
            node.isHeading -> ScreenRole.HEADING
            node.isEditable -> ScreenRole.TEXT_FIELD
            node.isCheckable && cls.contains("Switch", ignoreCase = true) -> ScreenRole.SWITCH
            node.isCheckable -> ScreenRole.CHECKBOX
            cls.contains("ImageView", ignoreCase = true) ||
                cls.contains("ImageButton", ignoreCase = true) -> ScreenRole.IMAGE
            node.isClickable && cls.contains("Button", ignoreCase = true) -> ScreenRole.BUTTON
            node.isClickable && looksLikeLink(node) -> ScreenRole.LINK
            node.isClickable -> ScreenRole.BUTTON
            cls.contains("TextView", ignoreCase = true) -> ScreenRole.TEXT
            cls.contains("Layout", ignoreCase = true) || cls.contains("ViewGroup", ignoreCase = true) ->
                ScreenRole.CONTAINER
            else -> ScreenRole.UNKNOWN
        }
    }

    private fun looksLikeLink(node: AccessibilityNode): Boolean {
        val cls = node.className.orEmpty()
        return cls.contains("TextView", ignoreCase = true) &&
            (node.viewIdResourceName?.contains("link", ignoreCase = true) == true)
    }

    private fun isAdNoise(node: AccessibilityNode): Boolean {
        val haystack = listOfNotNull(node.viewIdResourceName, node.className)
            .joinToString(" ") { it.lowercase() }
        return adNoiseKeywords.any { haystack.contains(it) }
    }

    /** Collapses runs of >= threshold consecutive siblings with identical (role, text, contentDescription). */
    private fun collapseRepeatedSiblings(nodes: List<ScreenNode>): List<ScreenNode> {
        if (nodes.isEmpty()) return nodes
        val result = mutableListOf<ScreenNode>()
        var i = 0
        while (i < nodes.size) {
            var runEnd = i + 1
            while (runEnd < nodes.size && isSameShape(nodes[i], nodes[runEnd])) runEnd++
            val runLength = runEnd - i
            if (runLength >= config.repeatedSiblingCollapseThreshold) {
                val representative = nodes[i]
                result += representative.copy(
                    text = buildString {
                        if (!representative.text.isNullOrBlank()) append(representative.text)
                        if (isNotEmpty()) append(" ")
                        append("(×$runLength upprepade)")
                    }
                )
            } else {
                for (j in i until runEnd) {
                    result += nodes[j].copy(children = collapseRepeatedSiblings(nodes[j].children))
                }
            }
            i = runEnd
        }
        return result
    }

    private fun isSameShape(a: ScreenNode, b: ScreenNode): Boolean =
        a.role == b.role && a.text == b.text && a.contentDescription == b.contentDescription &&
            a.children.size == b.children.size
}
