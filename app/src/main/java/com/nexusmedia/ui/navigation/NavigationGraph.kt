package com.nexusmedia.ui.navigation

/**
 * Type-safe navigation reference for NexusMedia.
 * Replace simple string-based navigation (currentTab) with this NavHost graph.
 */
object NavigationGraph {
    const val ROUTE_HOME = "home"
    const val ROUTE_SEARCH = "search"
    const val ROUTE_LIBRARY = "library"
    const val ROUTE_SETTINGS = "settings"
    const val ROUTE_PROFILE = "profile"
    const val ROUTE_PLAYER = "player/{mediaId}"
}
