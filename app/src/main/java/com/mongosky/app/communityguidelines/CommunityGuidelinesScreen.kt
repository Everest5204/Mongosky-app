package com.mongosky.app.communityguidelines

import com.mongosky.app.darkmode.AppColors

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mongosky.app.shared.*
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
internal fun CommunityGuidelinesScreen(onBack: () -> Unit) {
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var jump by remember { mutableStateOf<Job?>(null) }
    fun goTo(index: Int) { jump?.cancel(); jump = scope.launch { list.animateScrollToItem(index) } }
    val fontScale = LocalDensity.current.fontScale
    InformationPageFrame("Community Guidelines", onBack, Color(0xFFF4F7F6), onTitleClick = { goTo(0) }) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val small = maxWidth <= 480.dp
            val wide = maxWidth > 900.dp && fontScale <= 1.3f
            LazyColumn(state = list, modifier = Modifier.widthIn(max = 1200.dp).fillMaxSize().align(Alignment.TopCenter),
                contentPadding = PaddingValues(horizontal = if (small) 12.dp else 16.dp, vertical = if (small) 14.dp else 20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)) {
                item(key = "guidelines-hero") { GuidelineHero(small) }
                item(key = "guidelines-navigation") {
                    InformationCard {
                        Column(Modifier.padding(14.dp)) {
                            Text("On this page", color = AppColors.content(InformationColors.muted), fontSize = 11.sp, letterSpacing = .9.sp, fontWeight = FontWeight.ExtraBold)
                            Spacer(Modifier.height(10.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(CommunityGuidelinesContent.navigation, key = { it.id }) { section ->
                                    OutlinedButton(onClick = { goTo(CommunityGuidelinesContent.blocks.indexOf(section) + 2) },
                                        shape = RoundedCornerShape(100.dp), border = BorderStroke(1.dp, AppColors.outline(InformationColors.border)),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.content(InformationColors.text)),
                                        contentPadding = PaddingValues(horizontal = 13.dp, vertical = 10.dp), modifier = Modifier.heightIn(min = 48.dp)) {
                                        Text(section.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                }
                items(CommunityGuidelinesContent.blocks, key = { it.id }, contentType = { if (it.illustration == null) "rules" else "illustration" }) { block ->
                    if (block.illustration == null) GuidelineSection(block, wide, small)
                    else GuidelineIllustrationCard(block, wide, small)
                }
                item(key = "guidelines-closing") { GuidelineClosing(onBack, wide) }
                item(key = "guidelines-bottom-space") { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}
@Composable
private fun GuidelineHero(small: Boolean) {
    InformationCard(colors = listOf(Color.White, Color(0xFFF0FAF7), Color(0xFFEFF3FF))) {
        Column(Modifier.fillMaxWidth().padding(horizontal = if (small) 18.dp else 24.dp, vertical = if (small) 32.dp else 40.dp)) {
            InformationBadge(CommunityGuidelinesContent.heroBadge, Color.White.copy(alpha = .8f), icon = true)
            Spacer(Modifier.height(18.dp))
            InformationHeading(CommunityGuidelinesContent.heroTitle, if (small) 34f else 44f)
            Spacer(Modifier.height(19.dp))
            Text(CommunityGuidelinesContent.heroDescription, color = AppColors.content(Color(0xFF526172)), fontSize = 15.sp, lineHeight = 26.sp)
            Spacer(Modifier.height(24.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CommunityGuidelinesContent.heroHighlights.forEach { highlight ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(28.dp).background(AppColors.surface(InformationColors.primary.copy(alpha = .08f)), CircleShape), contentAlignment = Alignment.Center) {
                            Icon(InformationIcons.check, null, tint = AppColors.content(InformationColors.primary), modifier = Modifier.size(17.dp))
                        }
                        Text(highlight, color = AppColors.content(Color(0xFF344054)), fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
@Composable
private fun GuidelineSection(block: GuidelineBlock, wide: Boolean, small: Boolean) {
    InformationCard(radius = if (small) 19.dp else 22.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = if (small) 18.dp else 21.dp, vertical = if (small) 23.dp else 26.dp)) {
            InformationBadge(block.eyebrow.uppercase(Locale.ROOT))
            Spacer(Modifier.height(14.dp))
            InformationHeading(block.title, if (wide) 34f else 26f)
            Spacer(Modifier.height(15.dp))
            Text(block.description, color = AppColors.content(Color(0xFF526172)), fontSize = 15.sp, lineHeight = 26.sp)
            if (block.paragraphs.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    block.paragraphs.forEach { Text(it, color = AppColors.content(InformationColors.text), fontSize = 14.sp, lineHeight = 24.sp) }
                }
            }
            if (block.rules.isNotEmpty()) {
                Spacer(Modifier.height(23.dp))
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    block.rules.chunked(if (wide) 2 else 1).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            row.forEach { rule -> GuidelineRuleCard(rule, Modifier.weight(1f)) }
                            if (wide && row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
            block.note?.let {
                Spacer(Modifier.height(25.dp))
                Surface(color = AppColors.surface(Color(0xFFFFF9ED)), border = BorderStroke(1.dp, AppColors.outline(Color(0xFFF3D8A8))), shape = RoundedCornerShape(16.dp)) {
                    Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(11.dp), verticalAlignment = Alignment.Top) {
                        Icon(InformationIcons.info, "Important safety information", tint = AppColors.content(Color(0xFFA66A08)), modifier = Modifier.size(30.dp))
                        Text(it, color = AppColors.content(Color(0xFF805716)), fontSize = 13.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
@Composable
private fun GuidelineRuleCard(rule: GuidelineRule, modifier: Modifier) {
    Surface(modifier, color = AppColors.surface(Color(0xFFF9FBFA)), border = BorderStroke(1.dp, AppColors.outline(Color(0xFFE8ECEB))), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(11.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.size(34.dp).background(AppColors.surface(Color(0xFFE9F7F3)), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                Icon(InformationIcons.shield, null, tint = AppColors.content(InformationColors.primary), modifier = Modifier.size(18.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(rule.title, color = AppColors.content(Color(0xFF1D2939)), fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(7.dp))
                Text(rule.description, color = AppColors.content(InformationColors.muted), fontSize = 13.sp, lineHeight = 22.sp)
            }
        }
    }
}
@Composable
private fun GuidelineIllustrationCard(block: GuidelineBlock, wide: Boolean, small: Boolean) {
    val type = block.illustration
    val colors = when (type) {
        "respect-and-safety" -> listOf(Color(0xFFF5F5FF), Color(0xFFF4FBFF), Color(0xFFECFAF7))
        "authenticity-and-trust" -> listOf(Color(0xFFFFF8EF), Color(0xFFF8F3FF), Color(0xFFEFF8FF))
        else -> listOf(Color(0xFFEFFBF7), Color(0xFFF1F8FF), Color(0xFFF7F3FF))
    }
    val accent = when (type) { "respect-and-safety" -> Color(0xFF594FC1); "authenticity-and-trust" -> Color(0xFFA35A16); else -> Color(0xFF08745F) }
    val badge = when (type) { "respect-and-safety" -> Color(0xFFECEAFF); "authenticity-and-trust" -> Color(0xFFFFF0DF); else -> Color(0xFFE2F7F1) }
    val art = when (type) { "respect-and-safety" -> CommunityGuidelinesArt.respect; "authenticity-and-trust" -> CommunityGuidelinesArt.trust; else -> CommunityGuidelinesArt.privacy }
    InformationCard(colors = colors, radius = if (small) 19.dp else 22.dp) {
        Box(Modifier.padding(horizontal = if (small) 18.dp else 21.dp, vertical = if (small) 23.dp else 26.dp)) {
            InformationPair(wide, gap = 24.dp, left = {
                Column {
                    InformationBadge(block.eyebrow.uppercase(Locale.ROOT), badge, accent)
                    Spacer(Modifier.height(14.dp))
                    InformationHeading(block.title, if (wide) 34f else 26f)
                    Spacer(Modifier.height(17.dp))
                    Text(block.description, color = AppColors.content(Color(0xFF586678)), fontSize = 14.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium)
                }
            }, right = { InformationIllustration(art) })
        }
    }
}
@Composable
private fun GuidelineClosing(onBack: () -> Unit, wide: Boolean) {
    InformationCard(colors = listOf(Color(0xFF005F4D), Color(0xFF00866B), Color(0xFF167F89)), border = null) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 19.dp, vertical = 36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(55.dp).background(AppColors.surface(Color.White.copy(alpha = .13f)), RoundedCornerShape(19.dp)), contentAlignment = Alignment.Center) {
                Icon(InformationIcons.shield, null, tint = AppColors.content(Color.White), modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.height(18.dp))
            InformationHeading(CommunityGuidelinesContent.closingTitle, if (wide) 34f else 26f, Color.White, centered = true)
            Spacer(Modifier.height(17.dp))
            Text(CommunityGuidelinesContent.closingDescription, color = AppColors.content(Color.White.copy(alpha = .82f)), fontSize = 15.sp, lineHeight = 26.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(28.dp))
            InformationBackButton(CommunityGuidelinesContent.closingPrimaryActionLabel, onBack, Modifier.widthIn(max = 440.dp).fillMaxWidth())
        }
    }
}
