package com.mongosky.app.search

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlin.math.roundToInt

internal object SearchColors {
    val text = Color(0xFF101828)
    val muted = Color(0xFF7C8491)
    val secondary = Color(0xFF98A2B3)
    val soft = Color(0xFFF4F4F6)
    val border = Color(0xFFE7E9ED)
    val brand = Color(0xFFE60023)
    val avatar = Color(0xFF07927C)
}

@Composable
internal fun SearchInput(
    query: String, busy: Boolean, focusRequester: FocusRequester,
    onChange: (String) -> Unit, onClear: () -> Unit, onSubmit: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query, onValueChange = onChange,
        modifier = modifier.fillMaxWidth().heightIn(min = 58.dp).focusRequester(focusRequester)
            .semantics { contentDescription = "Search people by name" },
        placeholder = { Text("Search people...", color = SearchColors.muted, fontSize = 16.sp) },
        textStyle = TextStyle(color = SearchColors.text, fontSize = 16.sp, fontWeight = FontWeight.Medium),
        leadingIcon = { Icon(SearchIcons.search, contentDescription = null, tint = SearchColors.muted) },
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (busy) CircularProgressIndicator(
                    modifier = Modifier.size(18.dp).semantics { contentDescription = "Searching" },
                    color = SearchColors.muted, strokeWidth = 2.dp
                )
                if (query.isNotEmpty()) IconButton(onClick = onClear, modifier = Modifier.size(48.dp)) {
                    Icon(SearchIcons.close, contentDescription = "Clear search", tint = SearchColors.muted)
                }
                else Spacer(Modifier.width(14.dp))
            }
        },
        singleLine = true, shape = RoundedCornerShape(16.dp),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Words, autoCorrectEnabled = false, imeAction = ImeAction.Search
        ),
        keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color.White, unfocusedContainerColor = SearchColors.soft,
            focusedBorderColor = Color(0xFFCFD4DC), unfocusedBorderColor = Color.Transparent,
            cursorColor = SearchColors.text
        )
    )
}

@Composable
internal fun SearchAvatar(user: SearchUser, size: Dp = 52.dp) {
    val context = LocalContext.current
    val pixels = with(LocalDensity.current) { size.toPx().roundToInt().coerceAtLeast(1) }
    val request = remember(context, user.imageUrl, pixels) {
        ImageRequest.Builder(context).data(user.imageUrl).size(pixels).crossfade(false).build()
    }
    Box(Modifier.size(size).clip(CircleShape).background(SearchColors.avatar), contentAlignment = Alignment.Center) {
        Icon(SearchIcons.person, contentDescription = null,
            tint = Color(0xFFEAFFF7), modifier = Modifier.size(size * 0.52f))
        if (user.imageUrl != null) AsyncImage(
            model = request, contentDescription = null,
            modifier = Modifier.size(size), contentScale = ContentScale.Crop
        )
    }
}

@Composable
internal fun SearchPersonRow(user: SearchUser, horizontalPadding: Dp, onClick: () -> Unit) {
    Surface(
        onClick = onClick, color = Color.White,
        modifier = Modifier.fillMaxWidth().semantics {
            role = Role.Button
            contentDescription = "Open ${user.fullName}'s profile"
        }
    ) {
        Row(Modifier.padding(horizontal = horizontalPadding, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            SearchAvatar(user)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(user.fullName, color = SearchColors.text, fontSize = 15.sp,
                        fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false))
                    if (user.verified) Icon(SearchIcons.verified, contentDescription = "Verified account",
                        tint = Color.Unspecified, modifier = Modifier.size(18.dp))
                }
                if (user.title.isNotBlank()) Text(user.title, color = SearchColors.secondary, fontSize = 13.sp,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
internal fun SearchStatePanel(
    title: String, message: String, icon: ImageVector,
    modifier: Modifier = Modifier, error: Boolean = false,
    actionLabel: String? = null, onAction: (() -> Unit)? = null
) {
    Column(
        modifier.padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        Box(Modifier.size(112.dp).clip(CircleShape)
            .background(if (error) Color(0xFFFFF1F2) else Color(0xFFF3F5F8)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(60.dp),
                tint = if (error) Color(0xFFD92D20) else Color(0xFF59616E))
        }
        Spacer(Modifier.height(20.dp))
        Text(title, color = SearchColors.text, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        Spacer(Modifier.height(8.dp))
        Text(message, modifier = Modifier.widthIn(max = 430.dp), color = SearchColors.muted,
            fontSize = 14.sp, lineHeight = 21.sp, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(20.dp))
            Button(onClick = onAction, colors = ButtonDefaults.buttonColors(containerColor = SearchColors.brand),
                shape = RoundedCornerShape(12.dp), modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(SearchIcons.refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(actionLabel, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
internal fun SearchSkeletonList(horizontalPadding: Dp) {
    val transition = rememberInfiniteTransition(label = "search-loading")
    val pulse = transition.animateFloat(0.45f, 1f,
        animationSpec = infiniteRepeatable(tween(750), RepeatMode.Reverse), label = "search-loading-opacity")
    Column(Modifier.padding(horizontal = horizontalPadding, vertical = 18.dp)
        .semantics { contentDescription = "Searching people" }) {
        repeat(5) {
            Row(Modifier.fillMaxWidth().padding(vertical = 14.dp).graphicsLayer { alpha = pulse.value },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(52.dp).clip(CircleShape).background(SearchColors.border))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Box(Modifier.fillMaxWidth(0.72f).height(11.dp).clip(RoundedCornerShape(6.dp)).background(SearchColors.border))
                    Box(Modifier.fillMaxWidth(0.43f).height(9.dp).clip(RoundedCornerShape(6.dp)).background(SearchColors.border))
                }
            }
        }
    }
}

@Composable
internal fun SearchRefreshError(message: String, horizontalPadding: Dp, onRetry: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(Color(0xFFFFF8F8)).padding(start = horizontalPadding, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(message, color = SearchColors.muted, fontSize = 12.sp, modifier = Modifier.weight(1f)
            .padding(vertical = 10.dp).semantics { liveRegion = LiveRegionMode.Polite })
        TextButton(onClick = onRetry) { Text("Retry", color = SearchColors.brand, fontWeight = FontWeight.Bold) }
    }
}
