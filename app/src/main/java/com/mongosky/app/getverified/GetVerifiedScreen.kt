package com.mongosky.app.getverified

import com.mongosky.app.darkmode.AppColors

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun GetVerifiedScreen(onBack: () -> Unit) {
    val ink = Color(0xFF18181B)
    BoxWithConstraints(Modifier.fillMaxSize().background(AppColors.surface(Color.White))) {
        val wide = maxWidth >= 640.dp
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = 576.dp).fillMaxWidth()
                .padding(horizontal = if (wide) 32.dp else 20.dp)
                .padding(top = if (wide) 28.dp else 20.dp, bottom = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                        Icon(GetVerifiedIcons.back, contentDescription = "Back", tint = AppColors.content(ink), modifier = Modifier.size(23.dp))
                    }
                    Text(GetVerifiedContent.title, Modifier.weight(1f).semantics { heading() }, color = AppColors.content(ink),
                        fontFamily = FontFamily.SansSerif, fontSize = 24.sp, lineHeight = 32.sp,
                        fontWeight = FontWeight.SemiBold, letterSpacing = (-.6).sp)
                }
                Spacer(Modifier.height(if (wide) 40.dp else 32.dp))
                Column(Modifier.widthIn(max = 384.dp).fillMaxWidth()
                    .shadow(1.dp, RoundedCornerShape(24.dp))
                    .background(AppColors.surface(Color.White), RoundedCornerShape(24.dp))
                    .border(1.dp, AppColors.outline(Color(0xFFFFE4E6)), RoundedCornerShape(24.dp))
                    .padding(horizontal = if (wide) 32.dp else 24.dp, vertical = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    GetVerifiedIllustration()
                    Spacer(Modifier.height(28.dp))
                    Text(GetVerifiedContent.cardTitle, Modifier.fillMaxWidth().semantics { heading() }, color = AppColors.content(ink),
                        fontFamily = FontFamily.SansSerif, fontSize = 24.sp, fontWeight = FontWeight.Bold,
                        lineHeight = 32.sp, letterSpacing = (-.6).sp, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    Text(buildAnnotatedString {
                        append(GetVerifiedContent.prefix)
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = AppColors.content(ink))) {
                            append(GetVerifiedContent.followers)
                        }
                        append(GetVerifiedContent.suffix)
                    }, Modifier.widthIn(max = 320.dp).fillMaxWidth(), color = AppColors.content(Color(0xFF52525C)),
                        fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 28.sp,
                        textAlign = TextAlign.Center)
                }
            }
        }
    }
}
