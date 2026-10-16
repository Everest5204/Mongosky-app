package com.mongosky.app.helpsupport

import com.mongosky.app.language.appText
import com.mongosky.app.language.appDescription

import com.mongosky.app.darkmode.AppColors

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun HelpSupportHeader(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
            Icon(HelpSupportIcons.back, appDescription("Go back"), tint = AppColors.content(HelpSupportColors.text), modifier = Modifier.size(24.dp))
        }
        Text(appText(title), Modifier.weight(1f).semantics { heading() }, color = AppColors.content(HelpSupportColors.text),
            textAlign = TextAlign.Center, fontSize = 18.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(48.dp))
    }
}

@Composable
internal fun HelpSectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(appText(text), modifier.semantics { heading() }, color = AppColors.content(HelpSupportColors.text),
        fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
internal fun HelpArticleCard(article: HelpArticle, expanded: Boolean, onToggle: () -> Unit) {
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, tween(140), label = "FAQ arrow")
    Surface(shape = RoundedCornerShape(14.dp), color = AppColors.surface(HelpSupportColors.background),
        border = BorderStroke(1.dp, AppColors.outline(HelpSupportColors.border)), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.animateContentSize(tween(160))) {
            Row(Modifier.fillMaxWidth().heightIn(min = 60.dp)
                .clickable(role = Role.Button, onClickLabel = appText(if (expanded) "Collapse answer" else "Read answer"), onClick = onToggle)
                .semantics(mergeDescendants = true) { stateDescription = appText(if (expanded) "Expanded" else "Collapsed") }
                .padding(horizontal = 16.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(appText(article.question), Modifier.weight(1f), color = AppColors.content(HelpSupportColors.text),
                    fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.width(12.dp))
                Icon(HelpSupportIcons.down, null, tint = AppColors.content(HelpSupportColors.muted),
                    modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = rotation })
            }
            AnimatedVisibility(visible = expanded) {
                Text(appText(article.answer), Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    color = AppColors.content(HelpSupportColors.muted), fontSize = 14.sp, lineHeight = 22.sp)
            }
        }
    }
}

@Composable
internal fun HelpActionRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(14.dp), color = AppColors.surface(HelpSupportColors.surface),
        modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 76.dp).clickable(role = Role.Button, onClick = onClick)
            .padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(icon, null, tint = AppColors.content(HelpSupportColors.text), modifier = Modifier.size(24.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(appText(title), color = AppColors.content(HelpSupportColors.text), fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold)
                Text(appText(subtitle), color = AppColors.content(HelpSupportColors.muted), fontSize = 13.sp, lineHeight = 19.sp)
            }
            Icon(HelpSupportIcons.chevron, null, tint = AppColors.content(HelpSupportColors.muted), modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
internal fun HelpContactCard(onContact: () -> Unit, onCopyEmail: () -> Unit) {
    Surface(shape = RoundedCornerShape(18.dp), color = AppColors.surface(HelpSupportColors.softBrand),
        modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            HelpSectionTitle("Still need help?")
            Text(appText("Tell us what happened. We're here to help."), color = AppColors.content(HelpSupportColors.muted),
                fontSize = 14.sp, lineHeight = 21.sp)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(HelpSupportIcons.mail, null, tint = AppColors.content(HelpSupportColors.brand), modifier = Modifier.size(22.dp))
                Text(HelpSupportContent.CONTACT_EMAIL, Modifier.weight(1f), color = AppColors.content(HelpSupportColors.text),
                    fontSize = 14.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium)
                IconButton(onClick = onCopyEmail, modifier = Modifier.size(48.dp)) {
                    Icon(HelpSupportIcons.copy, appDescription("Copy support email"), tint = AppColors.content(HelpSupportColors.brand),
                        modifier = Modifier.size(20.dp))
                }
            }
            Button(onClick = onContact, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.fill(HelpSupportColors.brand))) {
                Text(appText("Contact support"), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
