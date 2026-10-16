package com.mongosky.app.signup

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.mongosky.app.about.AboutScreen
import com.mongosky.app.privacypolicy.PrivacyPolicyScreen

internal enum class SignupDocument(val key: String) {
    ABOUT("about"), PRIVACY("privacy");
    companion object { fun fromKey(key: String?) = entries.firstOrNull { it.key == key } }
}

/** Reuse existing native translated documents without a browser or network request. */
@Composable
internal fun SignupDocumentScreen(document: SignupDocument, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Box(Modifier.fillMaxSize().safeDrawingPadding().testTag("signup-document-${document.key}")) {
        when (document) {
            SignupDocument.ABOUT -> AboutScreen(onBack)
            SignupDocument.PRIVACY -> PrivacyPolicyScreen(onBack)
        }
    }
}
