package com.mongosky.app.deleteaccount

internal data class DeletedDataItem(val icon: String, val description: String)
internal object DeleteAccountContent {
    val dataItems = listOf(
        DeletedDataItem("profile", "Your profile information, profile photo and cover photo"),
        DeletedDataItem("media", "Your posts, uploaded photos and videos"),
        DeletedDataItem("users", "Your followers and following list"),
        DeletedDataItem("message", "Your comments, replies and AI chat history"),
        DeletedDataItem("badge", "Your verification badge and account details"),
        DeletedDataItem("heart", "Your reactions and account-related notifications")
    )
}

