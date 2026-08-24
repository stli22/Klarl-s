package com.klarl.accessibility.model

/** One of the 2-4 suggested next steps Claude returns alongside a screen summary. */
data class NavigationOption(val label: String)

/** Result of asking Claude to summarize a [ScreenSnapshot]. */
data class ScreenSummary(
    val summaryText: String,
    val options: List<NavigationOption>
)
