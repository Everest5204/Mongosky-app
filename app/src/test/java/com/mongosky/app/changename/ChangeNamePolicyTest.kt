package com.mongosky.app.changename

import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class ChangeNamePolicyTest {
    @Test fun unicodeAndWhitespace() {
        assertEquals("Nurtaj Jahan", normalizeName("  Nurtaj\u00a0  Jahan  "))
        assertNull(validateName("😀".repeat(15), "😀".repeat(15)))
        assertNotNull(validateName("😀".repeat(16), "😀".repeat(15)))
        assertNotNull(validateName(" ", "Name"))
    }
    @Test fun cooldownAndUnchangedNames() {
        val now = Instant.parse("2026-10-09T00:00:00Z")
        val status = NameStatus("First", "Last", false, now.plusSeconds(1))
        val state = ChangeNameState(status, "New", "Last", loading = false, now = now)
        assertFalse(state.canSave)
        assertTrue(state.copy(now = now.plusSeconds(1)).canSave)
        assertFalse(state.copy(first = "First", now = now.plusSeconds(1)).canSave)
        assertEquals("1 second", remainingTime(status.next!!, now))
        assertEquals("0 seconds", remainingTime(now, now.plusSeconds(2)))
        assertFalse(state.copy(status = null).canSave)
    }
}
