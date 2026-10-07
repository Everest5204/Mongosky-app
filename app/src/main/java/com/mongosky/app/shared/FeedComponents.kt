package com.mongosky.app.shared

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mongosky.app.post.FeedColors

@Composable
internal fun FeedSpinner() { CircularProgressIndicator(modifier = Modifier.size(24.dp), color = FeedColors.brand, strokeWidth = 2.dp) }

@Composable
internal fun FeedNotice(message: String, action: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(message, color = FeedColors.muted, fontSize = 14.sp, lineHeight = 21.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Button(onClick = onClick, colors = ButtonDefaults.buttonColors(containerColor = FeedColors.brand, contentColor = Color.White)) {
            Text(action, fontSize = 14.sp)
        }
    }
}
