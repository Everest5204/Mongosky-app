package com.mongosky.app.settingsinformation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalDensity
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

private val documentInk = Color(0xFF111827)
private val documentBody = Color(0xFF344054)
private val documentBorder = Color(0xFFE5E7EB)
private val documentBack by lazy {
    ImageVector.Builder("DocumentBack", 22.dp, 22.dp, 24f, 24f).apply {
        addPath(PathParser().parsePathString("M15 18L9 12L15 6").toNodes(),
            stroke = SolidColor(documentInk), strokeLineWidth = 2.2f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
    }.build()
}

/** The host supplies safe drawing insets and a SaveableStateProvider for scroll restoration. */
@Composable
internal fun SettingsDocumentScreen(
    document: SettingsDocument,
    art: (String) -> SettingsArt,
    onBack: () -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.White)) {
        val wide = maxWidth >= 768.dp
        val horizontal = if (wide) 28.dp else if (maxWidth <= 420.dp) 17.dp else 20.dp
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.widthIn(max = 980.dp).fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center) {
                Text(document.title, Modifier.fillMaxWidth().padding(horizontal = 48.dp, vertical = 14.dp).semantics { heading() },
                    color = documentInk, fontFamily = FontFamily.SansSerif,
                    fontSize = if (wide) 20.sp else 18.sp, fontWeight = FontWeight.ExtraBold,
                    lineHeight = 25.sp, textAlign = TextAlign.Center)
                IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart).size(48.dp)) {
                    Icon(documentBack, contentDescription = "Back", tint = documentInk, modifier = Modifier.size(22.dp))
                }
            }
            LazyColumn(Modifier.widthIn(max = 880.dp).fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(start = horizontal, end = horizontal,
                    top = if (wide) 38.dp else 28.dp, bottom = if (wide) 64.dp else 48.dp)) {
                item(key = "introduction", contentType = "introduction") {
                    Column {
                        document.lastUpdated?.let {
                            Text(it, color = Color(0xFF667085), fontSize = 14.sp, lineHeight = 21.sp, fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.SansSerif)
                            Spacer(Modifier.height(18.dp))
                        }
                        DocumentParagraphs(document.paragraphs, wide)
                        Spacer(Modifier.height(if (wide) 34.dp else 28.dp))
                        HorizontalDivider(color = documentBorder)
                    }
                }
                items(document.blocks, key = { it.id }, contentType = {
                    if (it is SettingsDocumentBlock.Illustration) "illustration" else "section"
                }) { block ->
                    when (block) {
                        is SettingsDocumentBlock.Section -> DocumentSection(block, wide)
                        is SettingsDocumentBlock.Illustration -> DocumentArtCard(block, art(block.illustration), wide)
                    }
                }
            }
        }
    }
}

@Composable
private fun DocumentParagraphs(paragraphs: List<String>, wide: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        paragraphs.forEach {
            Text(it, color = documentBody, fontFamily = FontFamily.SansSerif,
                fontSize = if (wide) 17.sp else 16.sp, lineHeight = if (wide) 30.6.sp else 28.sp)
        }
    }
}

@Composable
private fun DocumentSection(section: SettingsDocumentBlock.Section, wide: Boolean) {
    Column {
        Column(Modifier.padding(vertical = if (wide) 34.dp else 29.dp)) {
            Text(section.title, Modifier.semantics { heading() }, color = documentInk,
                fontFamily = FontFamily.SansSerif, fontSize = if (wide) 25.sp else 21.sp,
                fontWeight = FontWeight.ExtraBold, lineHeight = if (wide) 32.5.sp else 27.3.sp)
            Spacer(Modifier.height(17.dp))
            DocumentParagraphs(section.paragraphs, wide)
            if (section.points.isNotEmpty()) {
                if (section.paragraphs.isNotEmpty()) Spacer(Modifier.height(20.dp))
                Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
                    section.points.forEach { point ->
                        Row {
                            Text("•", Modifier.width(25.dp), color = Color(0xFF00866B),
                                fontSize = if (wide) 18.sp else 17.sp, lineHeight = if (wide) 29.75.sp else 28.sp)
                            Text(buildAnnotatedString {
                                withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold, color = Color(0xFF17202D))) {
                                    append(point.label); append(": ")
                                }
                                append(point.text)
                            }, color = documentBody, fontFamily = FontFamily.SansSerif,
                                fontSize = if (wide) 17.sp else 16.sp, lineHeight = if (wide) 29.75.sp else 28.sp)
                        }
                    }
                }
            }
        }
        HorizontalDivider(color = documentBorder)
    }
}

@Composable
private fun DocumentArtCard(block: SettingsDocumentBlock.Illustration, art: SettingsArt, wide: Boolean) {
    val future = block.illustration == "future-growth"
    val reversed = block.illustration in setOf("safe-community", "privacy-control")
    val gradient = if (future) listOf(Color(0xFFF1F8FB), Color.White)
        else if (reversed) listOf(Color.White, Color(0xFFF1FAF8))
        else listOf(Color(0xFFEFFBF7), Color.White)
    BoxWithConstraints(Modifier.padding(vertical = if (wide) 38.dp else 30.dp).fillMaxWidth()
        .border(1.dp, if (future) Color(0xFFCDDFE8) else Color(0xFFCCE9E2), RoundedCornerShape(if (wide) 24.dp else 20.dp))
        .background(Brush.linearGradient(gradient), RoundedCornerShape(if (wide) 24.dp else 20.dp))
        .padding(horizontal = if (wide) 32.dp else 17.dp, vertical = if (wide) 30.dp else 24.dp)) {
        val sideBySide = maxWidth >= 650.dp && LocalDensity.current.fontScale <= 1.2f
        if (sideBySide) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                if (reversed) SettingsVectorIllustration(art, Modifier.weight(1.1f))
                DocumentArtCopy(block, Modifier.weight(.9f), TextAlign.Start, true)
                if (!reversed) SettingsVectorIllustration(art, Modifier.weight(1.1f))
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
                DocumentArtCopy(block, Modifier.fillMaxWidth(), TextAlign.Center, wide)
                SettingsVectorIllustration(art, Modifier.widthIn(max = 440.dp))
            }
        }
    }
}

@Composable
private fun DocumentArtCopy(block: SettingsDocumentBlock.Illustration, modifier: Modifier, align: TextAlign, wide: Boolean) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(block.title, Modifier.fillMaxWidth().semantics { heading() }, color = documentInk,
            fontFamily = FontFamily.SansSerif, fontSize = if (wide) 25.sp else 21.sp,
            fontWeight = FontWeight.ExtraBold, lineHeight = if (wide) 32.5.sp else 27.3.sp, textAlign = align)
        Text(block.description, Modifier.fillMaxWidth(), color = Color(0xFF475467),
            fontFamily = FontFamily.SansSerif, fontSize = if (wide) 16.sp else 15.sp,
            fontWeight = FontWeight.Medium, lineHeight = if (wide) 27.2.sp else 25.5.sp, textAlign = align)
    }
}
