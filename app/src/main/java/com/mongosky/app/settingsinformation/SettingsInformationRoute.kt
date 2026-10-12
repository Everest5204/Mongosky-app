package com.mongosky.app.settingsinformation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import com.mongosky.app.about.AboutScreen
import com.mongosky.app.getverified.GetVerifiedScreen
import com.mongosky.app.privacypolicy.PrivacyPolicyScreen

@Composable
internal fun SettingsInformationRoute(route: String, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    when (route) {
        "ABOUT" -> AboutScreen(onBack)
        "PRIVACY_POLICY" -> PrivacyPolicyScreen(onBack)
        "GET_VERIFIED" -> GetVerifiedScreen(onBack)
    }
}
