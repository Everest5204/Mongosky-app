package com.mongosky.app.shared

import org.junit.Assert.*
import org.junit.Test

class PublicLinksTest {
    @Test fun webRoutesMatchExistingWebsite() {
        assertEquals("https://mongosky.com/post/abc-123", PublicLinks.post("abc-123"))
        assertEquals("https://mongosky.com/profile/123", PublicLinks.profile(" 123 "))
        assertEquals("https://mongosky.com/hashtag/hello_world", PublicLinks.hashtag("#hello_world"))
    }
    @Test fun identifierCannotChangeRouteQueryOrFragment() {
        assertEquals("https://mongosky.com/post/a%2Fb%20%3Fc%23d", PublicLinks.post("a/b ?c#d"))
    }
    @Test(expected = IllegalArgumentException::class) fun emptyIdentifiersAreRejected() {
        PublicLinks.profile(" ")
    }
}
