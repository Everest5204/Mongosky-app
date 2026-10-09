package com.mongosky.app.search

import java.io.IOException

data class SearchUser(
    val id: String,
    val firstName: String,
    val lastName: String,
    val imageUrl: String? = null,
    val title: String = "",
    val verified: Boolean = false
) {
    val fullName: String get() = listOf(firstName, lastName).filter { it.isNotBlank() }
        .joinToString(" ").ifBlank { "Mongosky user" }
}

data class SearchResult(val users: List<SearchUser>, val query: String)

enum class SearchStatus { IDLE, LOADING, RESULTS, ERROR }
enum class SearchFailure { SESSION_EXPIRED, ACCESS_DENIED, RATE_LIMITED, NETWORK, TIMEOUT, SERVER, INVALID_RESPONSE, REJECTED }

class SearchException(
    message: String,
    val failure: SearchFailure,
    val retryAfterSeconds: Int = 0,
    cause: Throwable? = null
) : IOException(message, cause)

data class SearchUiState(
    val query: String = "",
    val users: List<SearchUser> = emptyList(),
    val status: SearchStatus = SearchStatus.IDLE,
    val refreshing: Boolean = false,
    val error: String? = null,
    val sessionExpired: Boolean = false,
    val lastUpdatedAt: Long? = null
) {
    val normalizedQuery: String get() = SearchPolicy.normalize(query)
}

