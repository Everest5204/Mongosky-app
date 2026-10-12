package com.mongosky.app.myactivity

import androidx.compose.runtime.Composable
import com.mongosky.app.shared.InformationComingSoon

/** Matches MyActivityPage.tsx; this informational screen needs no session or database reads. */
@Composable
internal fun MyActivityScreen(onBack: () -> Unit) {
    InformationComingSoon("My Activity", "This feature will be available within a few days.", onBack)
}
