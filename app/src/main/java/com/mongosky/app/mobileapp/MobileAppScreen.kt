package com.mongosky.app.mobileapp

import com.mongosky.app.darkmode.AppColors

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch

@Composable
internal fun MobileAppScreen(onBack: () -> Unit) {
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val fontScale = LocalDensity.current.fontScale
    InformationPageFrame("Mobile App", onBack, Color(0xFFF3F7F5), onTitleClick = { scope.launch { list.animateScrollToItem(0) } }) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val larger = maxWidth >= 560.dp
            val wide = maxWidth >= 820.dp && fontScale <= 1.3f
            val side = if (larger) 20.dp else 12.dp
            val titleSize = if (wide) 52f else (maxWidth.value * .11f).coerceIn(36f, 52f)
            LazyColumn(state = list, modifier = Modifier.widthIn(max = 1240.dp).fillMaxSize().align(Alignment.TopCenter),
                contentPadding = PaddingValues(start = side, end = side, top = if (larger) 24.dp else 16.dp, bottom = 48.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)) {
                item(key = "hero") {
                    InformationCard(colors = listOf(Color.White, Color(0xFFEFFBF7), Color(0xFFEEF4FF))) {
                        Box(Modifier.padding(horizontal = if (larger) 32.dp else 18.dp, vertical = if (larger) 42.dp else 30.dp)) {
                            InformationPair(wide, left = {
                                Column {
                                    InformationBadge(MobileAppContent.heroEyebrow.uppercase(Locale.ROOT))
                                    Spacer(Modifier.height(14.dp))
                                    InformationHeading(MobileAppContent.heroTitle, titleSize)
                                    Spacer(Modifier.height(20.dp))
                                    Text(MobileAppContent.heroDescription, color = AppColors.content(Color(0xFF435366)), fontSize = 16.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium)
                                    Spacer(Modifier.height(15.dp))
                                    Text(MobileAppContent.heroSupportingText, color = AppColors.content(InformationColors.muted), fontSize = 14.sp, lineHeight = 24.sp)
                                    Spacer(Modifier.height(24.dp))
                                    MobileAppStatus(MobileAppContent.heroStatusLabel)
                                }
                            }, right = { InformationIllustration(MobileAppArt.development) })
                        }
                    }
                }
                item(key = "introduction") {
                    InformationCard {
                        Box(Modifier.padding(horizontal = if (larger) 30.dp else 20.dp, vertical = 30.dp)) {
                            InformationPair(wide, left = {
                                Column {
                                    InformationBadge(MobileAppContent.introductionEyebrow.uppercase(Locale.ROOT))
                                    Spacer(Modifier.height(14.dp))
                                    InformationHeading(MobileAppContent.introductionTitle, if (wide) 36f else 28f)
                                    Spacer(Modifier.height(16.dp))
                                    Text(MobileAppContent.introductionDescription, color = AppColors.content(Color(0xFF526172)), fontSize = 15.sp, lineHeight = 26.sp)
                                }
                            }, right = {
                                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    MobileAppContent.introductionParagraphs.forEachIndexed { index, text ->
                                        if (index > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(AppColors.outline(InformationColors.border)))
                                        Text(text, color = AppColors.content(InformationColors.text), fontSize = 14.sp, lineHeight = 24.sp)
                                    }
                                }
                            })
                        }
                    }
                }
                MobileAppContent.platforms.forEach { section ->
                    item(key = section.id) { MobileAppPlatformCard(section, wide, larger) }
                }
                item(key = "closing") {
                    InformationCard(colors = listOf(Color(0xFF005F4D), Color(0xFF00866B), Color(0xFF147D86)), border = null) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 38.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            InformationBadge(MobileAppContent.closingEyebrow.uppercase(Locale.ROOT), Color.White.copy(alpha = .12f), Color.White)
                            Spacer(Modifier.height(14.dp))
                            InformationHeading(MobileAppContent.closingTitle, if (wide) 36f else 28f, Color.White, centered = true)
                            Spacer(Modifier.height(18.dp))
                            Text(MobileAppContent.closingDescription, color = AppColors.content(Color.White.copy(alpha = .88f)), fontSize = 15.sp, lineHeight = 26.sp, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(13.dp))
                            Text(MobileAppContent.closingSupportingText, color = AppColors.content(Color.White.copy(alpha = .7f)), fontSize = 13.sp, lineHeight = 22.sp, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(26.dp))
                            InformationBackButton(MobileAppContent.closingBackButtonLabel, onBack, Modifier.widthIn(max = 440.dp).fillMaxWidth())
                        }
                    }
                }
            }
        }
    }
}
@Composable
private fun MobileAppPlatformCard(section: MobileAppPlatform, wide: Boolean, larger: Boolean) {
    val ios = section.id == "ios-app"
    val colors = if (ios) listOf(Color(0xFFF8F9FF), Color(0xFFEFEDFF), Color(0xFFEAF7FF))
        else listOf(Color(0xFFF5FFFB), Color(0xFFEAF9F3), Color(0xFFE5F5ED))
    val accent = if (ios) Color(0xFF514BB9) else InformationColors.primaryDark
    InformationCard(colors = colors, radius = 26.dp) {
        Box(Modifier.padding(horizontal = if (larger) 30.dp else 18.dp, vertical = 30.dp)) {
            val copy: @Composable () -> Unit = {
                Column {
                    InformationBadge(section.eyebrow.uppercase(Locale.ROOT), if (ios) Color(0xFFECEBFF) else Color(0xFFE9F7F3), accent)
                    Spacer(Modifier.height(14.dp))
                    InformationHeading(section.title, if (wide) 36f else 28f)
                    Spacer(Modifier.height(17.dp))
                    Text(section.description, color = AppColors.content(Color(0xFF48586B)), fontSize = 16.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(22.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        section.paragraphs.forEach { paragraph ->
                            Row(Modifier.height(IntrinsicSize.Min)) {
                                Box(Modifier.width(3.dp).fillMaxHeight().background(AppColors.surface(accent.copy(alpha = .2f))))
                                Text(paragraph, color = AppColors.content(Color(0xFF5C6878)), fontSize = 14.sp, lineHeight = 24.sp, modifier = Modifier.padding(start = 16.dp).weight(1f))
                            }
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                    MobileAppStatus(section.status, accent)
                }
            }
            val illustration: @Composable () -> Unit = { InformationIllustration(if (ios) MobileAppArt.ios else MobileAppArt.android) }
            InformationPair(wide, left = if (ios && wide) illustration else copy, right = if (ios && wide) copy else illustration)
        }
    }
}
@Composable
private fun MobileAppStatus(label: String, accent: Color = InformationColors.primaryDark) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(22.dp).background(AppColors.surface(accent.copy(alpha = .1f)), CircleShape), contentAlignment = Alignment.Center) {
            Box(Modifier.size(10.dp).background(AppColors.surface(accent), CircleShape))
        }
        Text(label, color = AppColors.content(accent), fontSize = 13.sp, lineHeight = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f, fill = false))
    }
}
