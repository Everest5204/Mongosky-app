package com.mongosky.app.deleteaccount

internal object DeleteAccountNavigation {
    const val ROUTE = "DELETE_ACCOUNT"
    fun open(stack: List<String>): List<String> = if (stack.lastOrNull() == ROUTE) stack
        else stack.ifEmpty { listOf("HOME", "SETTINGS") } + ROUTE
    fun back(stack: List<String>, expected: String): List<String> =
        if (expected != ROUTE || stack.lastOrNull() != expected) stack
        else stack.dropLast(1).ifEmpty { listOf("SETTINGS") }
}
