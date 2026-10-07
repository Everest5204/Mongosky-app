package com.mongosky.app.profile

import com.mongosky.app.mediapost.MediaPost
import com.mongosky.app.mediapost.MediaPostType
import com.mongosky.app.post.FeedAuthor
import com.mongosky.app.post.FeedMedia
import com.mongosky.app.post.FeedMediaType
import com.mongosky.app.post.FeedPost
import com.mongosky.app.post.FeedPostType
import com.mongosky.app.post.FeedSource
import com.mongosky.app.textpost.TextPost
import java.net.URI

/** Converts only successful, owned creation results, never an upload draft. */
internal fun MediaPost.toOwnProfilePost(): FeedPost? {
    if (!isSelf || media.isEmpty()) return null
    val postType = when (type) {
        MediaPostType.IMAGE -> FeedPostType.IMAGE
        MediaPostType.VIDEO -> FeedPostType.VIDEO
        else -> return null
    }
    val mediaType = if (type == MediaPostType.IMAGE) FeedMediaType.IMAGE else FeedMediaType.VIDEO
    if (media.any { it.type != mediaType }) return null
    return FeedPost(id = id, userId = userId, author = author, source = FeedSource.MEDIA, type = postType,
        createdAt = createdAt, updatedAt = updatedAt, caption = caption, media = media,
        likesCount = likesCount, commentsCount = commentsCount, sharesCount = sharesCount,
        isSelf = true, isFollowing = false)
}

internal fun TextPost.toOwnProfilePost(): FeedPost? {
    if (!isSelf || displayText.isBlank()) return null
    return FeedPost(id = id, userId = userId, author = author, source = FeedSource.TEXT, type = FeedPostType.TEXT,
        createdAt = createdAt, updatedAt = updatedAt, text = text, caption = caption,
        textBackground = textBackground, textColor = textColor, textStyle = textStyle,
        likesCount = likesCount, commentsCount = commentsCount, sharesCount = sharesCount,
        isSelf = true, isFollowing = false)
}

data class OwnProfile(
    val id: String,
    val firstName: String,
    val lastName: String,
    val bio: String = "",
    val profileImageUrl: String? = null,
    val coverImageUrl: String? = null,
    val followersCount: Long = 0,
    val followingCount: Long = 0,
    val postsCount: Long = 0
) {
    val displayName: String get() = "$firstName $lastName".trim()
    val author: FeedAuthor get() = FeedAuthor(id, firstName, lastName, profileImageUrl)
}

enum class OwnProfileTab(val label: String) { ALL("All"), PHOTOS("Photos"), VIDEOS("Videos") }
enum class ProfileImageKind(val endpoint: String, val field: String, val maxEdge: Int) {
    AVATAR("profile-image", "profile_image", 1600),
    COVER("cover-image", "cover_image", 2400)
}

/** Media responses are partial; an empty unrelated image field must not erase it. */
data class ProfileMediaChange(val ownerId: String, val kind: ProfileImageKind, val imageUrl: String)
data class ProfileChanged(val ownerId: String, val version: Long, val mediaChanged: Boolean)

object ProfileBio {
    const val MAX_LENGTH = 100
    fun normalize(text: String): String = text.replace("\r\n", "\n").replace('\r', '\n').trim()
    fun count(text: String): Int = text.codePointCount(0, text.length)
    fun limit(text: String): String = if (count(text) <= MAX_LENGTH) text else
        text.substring(0, text.offsetByCodePoints(0, MAX_LENGTH))
}

enum class ProfileConnectionKind(val path: String, val label: String) {
    FOLLOWERS("followers", "Followers"), FOLLOWING("following", "Following")
}
data class ProfileConnection(
    val id: String,
    val firstName: String,
    val lastName: String,
    val imageUrl: String?,
    val isFollowing: Boolean,
    val followsYou: Boolean,
    val isSelf: Boolean
) {
    val displayName: String get() = "$firstName $lastName".trim()
}
data class ProfileConnectionPage(
    val items: List<ProfileConnection>, val nextCursor: String?, val hasMore: Boolean
)

data class ProfileGalleryAsset(val key: String, val postId: String, val media: FeedMedia, val caption: String) {
    val thumbnail: String? get() = profileThumbnail(media.url, media.type)
}

fun profileGallery(posts: List<FeedPost>, type: FeedMediaType): List<ProfileGalleryAsset> = posts.flatMap { post ->
    post.media.filter { it.type == type }.sortedBy { it.order }.mapIndexed { index, media ->
        ProfileGalleryAsset("${post.source}:${post.id}:${media.id ?: index}:$type", post.id, media, post.caption)
    }
}

/** Small Cloudinary derivatives; other providers and signed links stay usable. */
fun profileThumbnail(url: String, type: FeedMediaType): String? {
    val uri = runCatching { URI(url) }.getOrNull() ?: return if (type == FeedMediaType.IMAGE) url else null
    if (uri.host != "res.cloudinary.com" || uri.scheme != "https") return if (type == FeedMediaType.IMAGE) url else null
    val segment = if (type == FeedMediaType.VIDEO) "/video/upload/" else "/image/upload/"
    val at = url.indexOf(segment)
    if (at < 0) return if (type == FeedMediaType.IMAGE) url else null
    val prefix = url.substring(0, at + segment.length)
    val asset = url.substring(at + segment.length)
    if (asset.startsWith("s--")) return if (type == FeedMediaType.IMAGE) url else null
    if (type == FeedMediaType.IMAGE) return prefix + "f_auto,q_auto:eco,c_limit,w_600/" + asset
    val path = asset.substringBefore('?')
    val dot = path.lastIndexOf('.')
    if (dot <= path.lastIndexOf('/')) return null
    return prefix + "so_auto,c_fill,g_auto,w_480,h_854,f_auto,q_auto/" + path.substring(0, dot) + ".jpg"
}
