package com.mongosky.app.darkmode

/** A standalone settings destination with guarded taps and Back callbacks. */
internal object DarkModeNavigation {
    const val ROUTE = "DARK_MODE"

    fun owns(route: String): Boolean = route == ROUTE

    fun open(stack: List<String>): List<String> =
        if (stack.lastOrNull() == "SETTINGS") stack + ROUTE else stack

    fun back(stack: List<String>, expected: String): List<String> =
        if (expected != ROUTE || stack.lastOrNull() != expected) stack
        else stack.dropLast(1).ifEmpty { listOf("HOME", "SETTINGS") }
}
