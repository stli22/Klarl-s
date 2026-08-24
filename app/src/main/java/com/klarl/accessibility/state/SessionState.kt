package com.klarl.accessibility.state

import com.klarl.accessibility.model.ScreenSummary
import com.klarl.accessibility.model.ScreenSnapshot

/**
 * The "last known screen" the app keeps around so a follow-up voice command can be resolved
 * without re-reading the UI tree. Deliberately in-memory only and cleared whenever the
 * foreground app changes - nothing here is persisted to disk.
 */
class SessionState {
    @Volatile var lastSnapshot: ScreenSnapshot? = null
        private set

    @Volatile var lastSnapshotText: String? = null
        private set

    @Volatile var lastSummary: ScreenSummary? = null

    fun update(snapshot: ScreenSnapshot, snapshotText: String) {
        lastSnapshot = snapshot
        lastSnapshotText = snapshotText
        lastSummary = null
    }

    fun clear() {
        lastSnapshot = null
        lastSnapshotText = null
        lastSummary = null
    }
}
