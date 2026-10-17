package com.mongosky.app.caption

import org.junit.Assert.*
import org.junit.Test

class HashtagIndexTest {
    @Test fun countsDistinctPeopleAndPostsNotTagOccurrences() {
        val index = HashtagIndex.build(listOf(
            HashtagDocument("text:1", "a", "#Travel #travel #TRAVEL"),
            HashtagDocument("media:2", "a", "#travel"),
            HashtagDocument("text:3", "b", "#travel")
        ))
        val usage = index.usage("travel")!!
        assertEquals(2, usage.people)
        assertEquals(3, usage.posts)
        assertEquals(setOf("text:1", "media:2", "text:3"), index.matchingKeys("TRAVEL"))
    }
    @Test fun lastPostVersionWinsAndFullRebuildRemovesDeletedUsage() {
        val index = HashtagIndex.build(listOf(
            HashtagDocument("1", "a", "#old"), HashtagDocument("1", "a", "#new")
        ))
        assertNull(index.usage("old"))
        assertEquals(1, index.usage("new")?.posts)
        assertNull(HashtagIndex.build(emptyList()).usage("new"))
    }
    @Test fun matchingRequiresCompleteTagAndIgnoresUrlFragments() {
        val index = HashtagIndex.build(listOf(HashtagDocument("1", "a", "#travelogue https://example.com/#travel")))
        assertTrue(index.matchingKeys("travel").isEmpty())
        assertEquals(1, index.suggestions("trav").size)
    }
    @Test fun unknownTagHasNoInventedZeroTotal() {
        assertNull(HashtagIndex.Empty.usage("new"))
        assertTrue(HashtagIndex.Empty.suggestions("new").isEmpty())
    }
    @Test fun prefixSuggestionsAreBoundedAndSortedByPeople() {
        val docs = (1..10).map { HashtagDocument("$it", "$it", "#tag$it #popular") }
        val index = HashtagIndex.build(docs)
        assertEquals(6, index.suggestions("").size)
        assertEquals("popular", index.suggestions("").first().tag)
        assertEquals(10, index.usage("popular")?.people)
        assertEquals(2, index.suggestions("tag1").size)
    }
}
