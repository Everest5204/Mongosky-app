package com.mongosky.app.helpsupport

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** Local UI state only. No support endpoint, polling, fake ticket or send confirmation. */
@Composable
internal fun HelpSupportRoute(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val pages = rememberSaveableStateHolder()
    var query by rememberSaveable { mutableStateOf("") }
    var topicName by rememberSaveable { mutableStateOf<String?>(null) }
    var expandedId by rememberSaveable { mutableStateOf<String?>(null) }
    var contactOpen by rememberSaveable { mutableStateOf(false) }
    var reasonName by rememberSaveable { mutableStateOf(SupportReason.GENERAL.name) }
    var summary by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var includeAppInfo by rememberSaveable { mutableStateOf(false) }
    var attempted by rememberSaveable { mutableStateOf(false) }
    var clearConfirm by rememberSaveable { mutableStateOf(false) }
    var emailUnavailable by rememberSaveable { mutableStateOf(false) }
    var openingEmail by remember { mutableStateOf(false) }
    var focusErrorRequest by remember { mutableStateOf(0) }
    val summaryFocus = remember { FocusRequester() }
    val descriptionFocus = remember { FocusRequester() }
    val reason = SupportReason.entries.firstOrNull { it.name == reasonName } ?: SupportReason.GENERAL
    val topic = HelpTopic.entries.firstOrNull { it.name == topicName }
    val draft = SupportDraft(reason, summary, description, includeAppInfo)
    val errors = if (attempted) HelpSupportPolicy.validate(draft) else SupportDraftErrors()

    fun back() { if (contactOpen) contactOpen = false else onBack() }
    fun copyEmail() {
        val copied = HelpSupportActions.copyEmail(context)
        scope.launch { snackbar.showSnackbar(if (copied) "Support email copied." else "Couldn't copy the email. ${HelpSupportContent.CONTACT_EMAIL}") }
    }
    fun openEmail() {
        if (openingEmail) return
        attempted = true
        val validation = HelpSupportPolicy.validate(draft)
        if (!validation.isValid) {
            focusErrorRequest++
            return
        }
        openingEmail = true
        scope.launch {
            try {
                val info = if (draft.includeAppInfo) HelpSupportActions.appInfo(context) else null
                val email = HelpSupportPolicy.prepareEmail(draft, info)
                emailUnavailable = !HelpSupportActions.openEmail(context, email)
            } finally { openingEmail = false }
        }
    }

    BackHandler(enabled = !clearConfirm && !emailUnavailable, onBack = ::back)
    Box(Modifier.fillMaxSize()) {
        if (contactOpen) {
            pages.SaveableStateProvider("support-contact") {
            HelpSupportContactScreen(draft, errors, openingEmail, summaryFocus, descriptionFocus, focusErrorRequest,
                onDraft = { updated ->
                    reasonName = updated.reason.name; summary = updated.summary
                    description = updated.description; includeAppInfo = updated.includeAppInfo
                }, onOpenEmail = ::openEmail, onClearDraft = { clearConfirm = true },
                onCopyEmail = ::copyEmail, onBack = ::back)
            }
        } else {
            pages.SaveableStateProvider("support-faq") {
            HelpSupportScreen(query, topic, expandedId,
                onQuery = { query = it }, onTopic = { topicName = it?.name },
                onToggleArticle = { expandedId = if (expandedId == it) null else it },
                onContact = { selected ->
                    if (summary.isBlank() && description.isBlank()) reasonName = selected.name
                    contactOpen = true
                }, onCopyEmail = ::copyEmail, onBack = ::back)
            }
        }
        SnackbarHost(snackbar, Modifier.align(androidx.compose.ui.Alignment.BottomCenter).imePadding().padding(12.dp))
    }
    if (clearConfirm) AlertDialog(onDismissRequest = { clearConfirm = false },
        containerColor = HelpSupportColors.background, title = { Text("Clear this draft?") },
        text = { Text("The summary and details you wrote will be removed.") },
        confirmButton = { TextButton(onClick = {
            summary = ""; description = ""; includeAppInfo = false; attempted = false; clearConfirm = false
        }) { Text("Clear draft", color = HelpSupportColors.brand) } },
        dismissButton = { TextButton(onClick = { clearConfirm = false }) { Text("Keep draft") } })
    if (emailUnavailable) AlertDialog(onDismissRequest = { emailUnavailable = false },
        containerColor = HelpSupportColors.background, title = { Text("Email app unavailable") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Open or set up an email app, or copy our address and contact us from another device. Your draft is saved here.")
                SelectionContainer { Text(HelpSupportContent.CONTACT_EMAIL) }
            }
        },
        confirmButton = { TextButton(onClick = { copyEmail(); emailUnavailable = false }) { Text("Copy email", color = HelpSupportColors.brand) } },
        dismissButton = { TextButton(onClick = { emailUnavailable = false }) { Text("Close") } })
}
