package com.mongosky.app.search

/** The feature boundary also lets tests control latency and out-of-order responses. */
interface SearchRepository {
    suspend fun search(token: String, query: String): SearchResult
    suspend fun verifiedIds(token: String, ids: List<String>): Set<String>
}

class LiveSearchRepository(private val api: SearchApi = SearchApi()) : SearchRepository {
    override suspend fun search(token: String, query: String) = api.search(token, query)
    override suspend fun verifiedIds(token: String, ids: List<String>) = api.verifiedIds(token, ids)
}
