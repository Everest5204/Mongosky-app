package com.mongosky.app.helpsupport

/** FAQ IDs and fixed rows occupy separate key namespaces in the same LazyColumn. */
internal object HelpSupportListKeys {
    const val INTRO = "help:section:intro"
    const val SEARCH = "help:section:search"
    const val TOPICS = "help:section:topics"
    const val FAQ_HEADING = "help:section:faq-heading"
    const val EMPTY = "help:section:empty"
    const val ACTIONS_HEADING = "help:section:actions-heading"
    const val REPORT = "help:action:report"
    const val FEEDBACK = "help:action:feedback"
    const val CONTACT = "help:action:contact"
    const val HANDOFF = "help:section:handoff"

    fun article(id: String): String = "help:faq:$id"

    val fixedRows = listOf(INTRO, SEARCH, TOPICS, FAQ_HEADING, EMPTY,
        ACTIONS_HEADING, REPORT, FEEDBACK, CONTACT, HANDOFF)
}
