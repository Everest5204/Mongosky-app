package com.mongosky.app.language

internal enum class AppLocale(val tag: String) {
    ENGLISH("en"), BANGLA("bn");

    fun other() = if (this == BANGLA) ENGLISH else BANGLA

    companion object {
        fun parse(tag: String?) = entries.firstOrNull { it.tag == tag } ?: ENGLISH
    }
}
