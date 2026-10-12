package com.mongosky.app.ai

internal object AiPolicy {
    const val MAX_DRAFT_POINTS = 4000
    const val MAX_RESPONSE_CHARS = 64 * 1024
    const val MAX_HISTORY = 250
    const val MAX_CHATS = 50
    const val MAX_CACHE_CHARS = 512 * 1024
    private val objectId = Regex("[a-f0-9]{24}")
    fun validId(value: String) = objectId.matches(value) && value != "0".repeat(24)
    fun validToken(value: String) = value.isNotBlank() && value.length <= 16384 && value.all { it > ' ' && it < '\u007f' }
    fun validUnicode(value: String): Boolean {
        var i = 0
        while (i < value.length) {
            val c = value[i++]
            if (Character.isHighSurrogate(c)) {
                if (i == value.length || !Character.isLowSurrogate(value[i++])) return false
            } else if (Character.isLowSurrogate(c)) return false
        }
        return true
    }
    fun cleanDraft(value: String): String? {
        val clean = value.trim()
        return clean.takeIf { it.isNotEmpty() && validUnicode(it) && it.codePointCount(0,it.length) <= MAX_DRAFT_POINTS }
    }
    fun limitDraft(value: String): String {
        if (!validUnicode(value)) return value.filterNot { Character.isSurrogate(it) }.take(MAX_DRAFT_POINTS)
        return if (value.codePointCount(0,value.length) <= MAX_DRAFT_POINTS) value
        else value.substring(0,value.offsetByCodePoints(0,MAX_DRAFT_POINTS))
    }
    fun sortedChats(chats: List<AiChat>) = chats.distinctBy { it.id }
        .sortedWith(compareByDescending<AiChat> { it.updatedAt }.thenByDescending { it.id }).take(MAX_CHATS)
    fun upsert(chats: List<AiChat>, incoming: AiChat) = sortedChats(listOf(incoming) + chats.filter { it.id != incoming.id })
    fun saved(messages: List<AiMessage>) = messages.filter { it.delivery == AiDelivery.SAVED }.takeLast(MAX_HISTORY)
}
