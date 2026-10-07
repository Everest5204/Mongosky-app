package com.mongosky.app.post

import com.mongosky.app.mediapost.MediaPost
import com.mongosky.app.post.FeedAuthor
import com.mongosky.app.post.FeedSource
import com.mongosky.app.textpost.TextPost
import java.time.Instant

sealed class HomePost {
    abstract val id: String
    abstract val author: FeedAuthor
    abstract val createdAt: Instant
    abstract val likesCount: Long
    abstract val commentsCount: Long
    abstract val source: FeedSource

    val key: String
        get() = "${source.name}:$id"

    data class Text(val post: TextPost) : HomePost() {
        override val id get() = post.id
        override val author get() = post.author
        override val createdAt get() = post.createdAt
        override val likesCount get() = post.likesCount
        override val commentsCount get() = post.commentsCount
        override val source get() = FeedSource.TEXT
    }

    data class Media(val post: MediaPost) : HomePost() {
        override val id get() = post.id
        override val author get() = post.author
        override val createdAt get() = post.createdAt
        override val likesCount get() = post.likesCount
        override val commentsCount get() = post.commentsCount
        override val source get() = FeedSource.MEDIA
    }
}
