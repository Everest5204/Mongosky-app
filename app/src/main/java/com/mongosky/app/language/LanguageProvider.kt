package com.mongosky.app.language

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.view.LayoutInflater
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext

/** Updates native/Compose resources in place while retaining navigation, drafts, and ViewModels. */
@Composable
internal fun LanguageProvider(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val locale = AppLanguage.current
    val localizedConfiguration = remember(configuration, locale) {
        AppLanguage.configuration(configuration, locale)
    }
    val localizedContext = remember(context, localizedConfiguration) {
        LanguageContext(context, localizedConfiguration)
    }
    CompositionLocalProvider(
        LocalContext provides localizedContext,
        LocalConfiguration provides localizedConfiguration,
        content = content
    )
}

/** Keep the Activity in the ContextWrapper chain for existing navigation and native dialogs. */
private class LanguageContext(base: Context, configuration: Configuration) : ContextWrapper(base) {
    private val localized = base.createConfigurationContext(configuration)
    private val localizedTheme by lazy {
        localized.resources.newTheme().apply { setTo(base.theme) }
    }
    override fun getResources(): Resources = localized.resources
    override fun getAssets() = localized.assets
    override fun getTheme(): Resources.Theme = localizedTheme
    override fun getSystemService(name: String): Any? =
        if (name == Context.LAYOUT_INFLATER_SERVICE) LayoutInflater.from(baseContext).cloneInContext(this)
        else super.getSystemService(name)
}
