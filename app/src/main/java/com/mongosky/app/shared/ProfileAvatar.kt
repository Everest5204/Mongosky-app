package com.mongosky.app.shared

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.Disposable
import coil.request.ImageRequest
import coil.size.Scale

/** Uses Coil's shared memory/disk caches and decodes to the measured avatar size. */
@Composable
fun ProfileAvatar(
    imageUrl: String?,
    modifier: Modifier = Modifier.size(44.dp),
    fallbackText: String? = null
) {
    val context = LocalContext.current
    val request = remember(context, imageUrl) {
        imageUrl?.takeIf { it.isNotBlank() }?.let {
            avatarRequest(context.applicationContext, it).build()
        }
    }
    Box(
        modifier = modifier.clip(CircleShape).background(Color(0xFF07927C)),
        contentAlignment = Alignment.Center
    ) {
        if (fallbackText.isNullOrBlank()) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                tint = Color(0xFFEAFFF7),
                modifier = Modifier.size(24.dp)
            )
        } else {
            Text(
                text = fallbackText,
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
        }
        if (request != null) {
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/** Preloads while Home/gallery is visible; it never waits on the main thread. */
fun preloadProfileAvatar(context: Context, imageUrl: String?, pixels: Int): Disposable? {
    val url = imageUrl?.takeIf { it.isNotBlank() } ?: return null
    val applicationContext = context.applicationContext
    val request = avatarRequest(applicationContext, url)
        .size(pixels.coerceAtLeast(1))
        .build()
    return applicationContext.imageLoader.enqueue(request)
}

private fun avatarRequest(context: Context, url: String): ImageRequest.Builder =
    ImageRequest.Builder(context)
        .data(url)
        .scale(Scale.FILL)
        .crossfade(false)
