package com.mongosky.app.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

@Composable
fun MongoskyTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        content = content
    )
}
