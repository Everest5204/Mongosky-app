package com.mongosky.app.settings

/** App-level Back belongs to the host; these policies never finish an Activity. */
internal object SettingsNavigation {
    const val ROUTE = "SETTINGS"
    fun push(stack: List<String>): List<String> =
        if (stack.lastOrNull() == ROUTE) stack else stack.ifEmpty { listOf("HOME") } + ROUTE
    fun back(stack: List<String>, expected: String): List<String> =
        if (expected != ROUTE || stack.lastOrNull() != expected) stack
        else stack.dropLast(1).ifEmpty { listOf("HOME") }
}
