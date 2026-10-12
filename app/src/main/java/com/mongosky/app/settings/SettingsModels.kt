package com.mongosky.app.settings

/** Stable menu IDs match the web. The submenu screens will be connected in later sections. */
internal enum class SettingsMenuId(val webId: String) {
    LANGUAGE("language"), DARK_MODE("dark-mode"), PERSONAL_DETAILS("personal-details"),
    CHANGE_PASSWORD("change-password"), GET_VERIFIED("get-verified"), PRIVACY_POLICY("privacy-policy"),
    HELP_AND_SUPPORT("help-and-support"), DEACTIVATE_ACCOUNT("deactivate-account"),
    DELETE_ACCOUNT("delete-account"), ABOUT("about")
}
internal data class SettingsMenuItem(
    val id: SettingsMenuId, val label: String, val showChevron: Boolean = false, val danger: Boolean = false
)
internal object SettingsContent {
    val items = listOf(
        SettingsMenuItem(SettingsMenuId.LANGUAGE, "Language"),
        SettingsMenuItem(SettingsMenuId.DARK_MODE, "Dark Mode"),
        SettingsMenuItem(SettingsMenuId.PERSONAL_DETAILS, "Personal Details"),
        SettingsMenuItem(SettingsMenuId.CHANGE_PASSWORD, "Change Password", showChevron = true),
        SettingsMenuItem(SettingsMenuId.GET_VERIFIED, "Get Verified", showChevron = true),
        SettingsMenuItem(SettingsMenuId.PRIVACY_POLICY, "Privacy Policy", showChevron = true),
        SettingsMenuItem(SettingsMenuId.HELP_AND_SUPPORT, "Help and Support"),
        SettingsMenuItem(SettingsMenuId.DEACTIVATE_ACCOUNT, "Deactivate Account", danger = true),
        SettingsMenuItem(SettingsMenuId.DELETE_ACCOUNT, "Delete Account", showChevron = true, danger = true),
        SettingsMenuItem(SettingsMenuId.ABOUT, "About", showChevron = true)
    )
}
