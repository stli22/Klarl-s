package com.klarl.accessibility.model

/**
 * A full extraction of one foreground window at a point in time. This is the unit that gets
 * serialized and sent to Claude, and the unit we keep around as "the last known screen" so
 * follow-up voice commands can be resolved against it without re-reading the UI tree.
 */
data class ScreenSnapshot(
    val packageName: String,
    val windowTitle: String?,
    val timestampMillis: Long,
    val rootNodes: List<ScreenNode>
) {
    val isEmpty: Boolean get() = rootNodes.isEmpty()
}
