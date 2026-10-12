package com.mongosky.app.about

import androidx.compose.runtime.Composable
import com.mongosky.app.settingsinformation.SettingsDocumentScreen

@Composable
internal fun AboutScreen(onBack: () -> Unit) {
    SettingsDocumentScreen(AboutContent.document, art = { type ->
        when (type) {
            "connect-and-share" -> AboutArt.connect_and_share
            "safe-community" -> AboutArt.safe_community
            "future-growth" -> AboutArt.future_growth
            else -> error("Unknown About illustration: $type")
        }
    }, onBack = onBack)
}
