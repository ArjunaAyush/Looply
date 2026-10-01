package com.arjunaayush.looply.navigation

sealed class Screen {
    object Home : Screen()
    data class Reels(val initialIndex: Int = 0) : Screen()
    object Saved : Screen()
    object Settings : Screen()
}

class AppNavigation(private val onScreenChanged: (Screen) -> Unit) {

    var currentScreen: Screen? = null
        private set

    /**
     * Navigates to a specific destination screen.
     */
    fun navigateTo(screen: Screen) {
        if (currentScreen == screen) return
        currentScreen = screen
        onScreenChanged(screen)
    }

    /**
     * Handles Android device back press navigation.
     * Returns true if it navigated back to Home, or false if already at Home.
     */
    fun handleBackPressed(): Boolean {
        val current = currentScreen
        return if (current != null && current !is Screen.Home) {
            navigateTo(Screen.Home)
            true
        } else {
            false
        }
    }
}
