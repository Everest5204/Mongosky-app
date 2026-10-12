package com.mongosky.app.privacypolicy

import androidx.compose.runtime.Composable
import com.mongosky.app.settingsinformation.SettingsDocumentScreen

@Composable
internal fun PrivacyPolicyScreen(onBack: () -> Unit) {
    SettingsDocumentScreen(PrivacyPolicyContent.document, art = { type ->
        when (type) {
            "data-protection" -> PrivacyPolicyArt.data_protection
            "privacy-control" -> PrivacyPolicyArt.privacy_control
            else -> error("Unknown PrivacyPolicy illustration: $type")
        }
    }, onBack = onBack)
}
