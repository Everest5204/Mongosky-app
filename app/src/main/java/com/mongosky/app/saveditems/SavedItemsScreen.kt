package com.mongosky.app.saveditems

import androidx.compose.runtime.Composable
import com.mongosky.app.shared.InformationComingSoon

/** Matches SavedItemsPage.tsx: saved-post storage is not yet present in the launched web app. */
@Composable
internal fun SavedItemsScreen(onBack: () -> Unit) {
    InformationComingSoon("Saved Items", "This feature will be available within a few days.", onBack)
}
