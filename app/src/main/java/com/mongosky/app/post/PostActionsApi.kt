package com.mongosky.app.post

import com.mongosky.app.comments.CommentActions
import com.mongosky.app.comments.CommentApi
import com.mongosky.app.network.PostHttp
import com.mongosky.app.network.PostTransport
import com.mongosky.app.reactions.ReactionActions
import com.mongosky.app.reactions.ReactionApi

/** Compatibility facade; each feature owns its endpoints and shares one transport. */
interface PostActions : CommentActions, ReactionActions

class PostActionsApi internal constructor(transport: PostTransport) : PostActions,
    CommentActions by CommentApi(transport), ReactionActions by ReactionApi(transport) {
    constructor() : this(PostHttp())
}
