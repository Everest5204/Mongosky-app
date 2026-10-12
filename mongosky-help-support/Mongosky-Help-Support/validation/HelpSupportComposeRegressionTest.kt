package com.mongosky.app.helpsupport

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.Composition
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Exercises the real Compose saveable-state guard used by lazy item reuse. */
class HelpSupportComposeRegressionTest {
    private class EmptyApplier : AbstractApplier<Unit>(Unit) {
        override fun insertTopDown(index: Int, instance: Unit) = Unit
        override fun insertBottomUp(index: Int, instance: Unit) = Unit
        override fun remove(index: Int, count: Int) = Unit
        override fun move(from: Int, to: Int, count: Int) = Unit
        override fun onClear() = Unit
    }

    private fun composeKeys(keys: List<String>) = runBlocking {
        val recomposer = Recomposer(coroutineContext)
        val composition = Composition(EmptyApplier(), recomposer)
        try {
            composition.setContent {
                val holder = rememberSaveableStateHolder()
                keys.forEach { key -> holder.SaveableStateProvider(key) { } }
            }
        } finally { composition.dispose(); recomposer.close() }
    }

    @Test fun previousFeedbackKeysReproduceTheCrash() {
        val oldKeys = listOf("intro", "search", "topics", "faq-heading") +
            HelpSupportContent.articles.map { it.id } +
            listOf("actions-heading", "report", "feedback", "contact", "handoff")
        val error = assertThrows(IllegalArgumentException::class.java) { composeKeys(oldKeys) }
        assertTrue(error.message.orEmpty().contains("feedback"))
        println("REPRODUCED: ${error.message}")
    }
    @Test fun fixedFeedbackAndAllRowsComposeWithoutCollision() {
        val keys = HelpSupportListKeys.fixedRows + HelpSupportContent.articles.map { HelpSupportListKeys.article(it.id) }
        assertEquals(keys.size, keys.toSet().size)
        assertNotEquals(HelpSupportListKeys.FEEDBACK, HelpSupportListKeys.article("feedback"))
        composeKeys(keys)
    }
    @Test fun filteringReorderingAndClearingKeepStableUniqueKeys() {
        val scenarios = listOf(HelpSupportContent.articles, HelpSupportContent.articles.reversed(), emptyList()) +
            HelpTopic.entries.map { topic -> HelpSupportContent.articles.filter { it.topic == topic } }
        scenarios.forEach { articles ->
            val keys = HelpSupportListKeys.fixedRows + articles.map { HelpSupportListKeys.article(it.id) }
            assertEquals(keys.size, keys.toSet().size)
            composeKeys(keys)
        }
        val original = HelpSupportContent.articles.associate { it.id to HelpSupportListKeys.article(it.id) }
        HelpSupportContent.articles.reversed().forEach { assertEquals(original[it.id], HelpSupportListKeys.article(it.id)) }
    }
}
