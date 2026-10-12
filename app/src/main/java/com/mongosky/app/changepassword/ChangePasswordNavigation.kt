package com.mongosky.app.changepassword

internal object ChangePasswordNavigation {
    const val CHANGE = "CHANGE_PASSWORD"
    const val RECOVERY = "CHANGE_PASSWORD_RESET"
    fun owns(route: String) = route == CHANGE || route == RECOVERY
    fun open(stack: List<String>): List<String> =
        if (stack.lastOrNull()?.let(::owns) == true) stack
        else stack.ifEmpty { listOf("HOME", "SETTINGS") } + CHANGE
    fun recover(stack: List<String>): List<String> =
        if (stack.lastOrNull() == CHANGE) stack + RECOVERY else stack
    fun back(stack: List<String>, expected: String): List<String> =
        if (!owns(expected) || stack.lastOrNull() != expected) stack
        else stack.dropLast(1).ifEmpty { listOf("SETTINGS") }
}
