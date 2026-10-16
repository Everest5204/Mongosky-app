@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.mongosky.app.helpsupport

import com.mongosky.app.language.appText
import com.mongosky.app.language.appDescription

import com.mongosky.app.darkmode.AppColors

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun HelpSupportScreen(
    query: String, topic: HelpTopic?, expandedId: String?, onQuery: (String) -> Unit,
    onTopic: (HelpTopic?) -> Unit, onToggleArticle: (String) -> Unit,
    onContact: (SupportReason) -> Unit, onCopyEmail: () -> Unit, onBack: () -> Unit
) {
    val focus = LocalFocusManager.current
    val results = remember(query, topic, com.mongosky.app.language.AppLanguage.current) { HelpSupportPolicy.search(query, topic) }
    val list = rememberLazyListState()
    BoxWithConstraints(Modifier.fillMaxSize().background(AppColors.surface(HelpSupportColors.background)).imePadding()) {
        val side = if (maxWidth < 360.dp) 16.dp else 20.dp
        Column(Modifier.widthIn(max = 680.dp).fillMaxWidth().align(Alignment.TopCenter)) {
            HelpSupportHeader("Help & Support", onBack)
            LazyColumn(state = list, modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(start = side, end = side, top = 16.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item(key = HelpSupportListKeys.INTRO, contentType = "intro") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Surface(color = AppColors.surface(HelpSupportColors.softBrand), shape = RoundedCornerShape(18.dp), modifier = Modifier.size(56.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(HelpSupportIcons.help, null, tint = AppColors.content(HelpSupportColors.brand), modifier = Modifier.size(30.dp))
                            }
                        }
                        Text(appText("How can we help?"), Modifier.semantics { heading() }, color = AppColors.content(HelpSupportColors.text),
                            fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold)
                        Text(appText("Find an answer or get in touch with Mongosky."), color = AppColors.content(HelpSupportColors.muted),
                            fontSize = 14.sp, lineHeight = 22.sp)
                        Spacer(Modifier.height(2.dp))
                    }
                }
                item(key = HelpSupportListKeys.SEARCH, contentType = "search") {
                    OutlinedTextField(value = query, onValueChange = { onQuery(it.take(HelpSupportPolicy.MAX_QUERY)) },
                        modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(14.dp),
                        label = { Text(appText("Search help")) },
                        leadingIcon = { Icon(HelpSupportIcons.search, null, modifier = Modifier.size(21.dp)) },
                        trailingIcon = if (query.isEmpty()) null else ({
                            IconButton(onClick = { onQuery("") }) { Icon(HelpSupportIcons.close, appDescription("Clear search"), modifier = Modifier.size(20.dp)) }
                        }),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AppColors.outline(HelpSupportColors.brand),
                            focusedLabelColor = HelpSupportColors.brand, cursorColor = AppColors.content(HelpSupportColors.brand)))
                }
                item(key = HelpSupportListKeys.TOPICS, contentType = "topics") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item(key = "all") { HelpTopicChip("All topics", topic == null) { focus.clearFocus(); onTopic(null) } }
                        items(HelpTopic.entries, key = { it.name }) { choice ->
                            HelpTopicChip(choice.label, topic == choice) { focus.clearFocus(); onTopic(choice) }
                        }
                    }
                }
                item(key = HelpSupportListKeys.FAQ_HEADING, contentType = "heading") {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        HelpSectionTitle(if (query.isBlank() && topic == null) "Frequently asked questions" else "Help articles")
                        if (query.isNotBlank() || topic != null) Text(appText("${results.size} ${if (results.size == 1) "answer" else "answers"} found"),
                            color = AppColors.content(HelpSupportColors.muted), fontSize = 13.sp)
                    }
                }
                if (results.isEmpty()) {
                    item(key = HelpSupportListKeys.EMPTY, contentType = "empty") {
                        Column(Modifier.fillMaxWidth().padding(vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(appText("No matching answers"), color = AppColors.content(HelpSupportColors.text), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Text(appText("Try another word or browse all topics. You can also contact support below."),
                                color = AppColors.content(HelpSupportColors.muted), fontSize = 14.sp, lineHeight = 22.sp)
                            TextButton(onClick = { focus.clearFocus(); onQuery(""); onTopic(null) }) { Text(appText("Show all answers"), color = AppColors.content(HelpSupportColors.brand)) }
                        }
                    }
                }
                items(results, key = { HelpSupportListKeys.article(it.id) }, contentType = { "faq" }) { article ->
                    HelpArticleCard(article, expandedId == article.id) { focus.clearFocus(); onToggleArticle(article.id) }
                }
                item(key = HelpSupportListKeys.ACTIONS_HEADING, contentType = "heading") { HelpSectionTitle("Talk to us", Modifier.padding(top = 12.dp)) }
                item(key = HelpSupportListKeys.REPORT, contentType = "action") {
                    HelpActionRow(HelpSupportIcons.report, "Report a problem", "Something isn't working as expected.") {
                        focus.clearFocus(); onContact(SupportReason.TECHNICAL)
                    }
                }
                item(key = HelpSupportListKeys.FEEDBACK, contentType = "action") {
                    HelpActionRow(HelpSupportIcons.feedback, "Send feedback", "Share an idea or suggest an improvement.") {
                        focus.clearFocus(); onContact(SupportReason.FEEDBACK)
                    }
                }
                item(key = HelpSupportListKeys.CONTACT, contentType = "contact") {
                    HelpContactCard(onContact = { focus.clearFocus(); onContact(SupportReason.GENERAL) }, onCopyEmail = onCopyEmail)
                }
                item(key = HelpSupportListKeys.HANDOFF, contentType = "note") {
                    Text(appText("Support messages open in your email app. Review your message there before sending."),
                        color = AppColors.content(HelpSupportColors.muted), fontSize = 12.sp, lineHeight = 19.sp)
                }
            }
        }
    }
}

@Composable
private fun HelpTopicChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(appText(label)) },
        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AppColors.surface(HelpSupportColors.softBrand),
            selectedLabelColor = AppColors.content(HelpSupportColors.brand)), modifier = Modifier.heightIn(min = 48.dp))
}
