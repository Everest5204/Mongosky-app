package com.mongosky.app.settingsinformation

internal data class SettingsDocumentPoint(val label: String, val text: String)

internal sealed interface SettingsDocumentBlock {
    val id: String
    data class Section(
        override val id: String,
        val title: String,
        val paragraphs: List<String> = emptyList(),
        val points: List<SettingsDocumentPoint> = emptyList()
    ) : SettingsDocumentBlock
    data class Illustration(
        override val id: String,
        val illustration: String,
        val title: String,
        val description: String
    ) : SettingsDocumentBlock
}

internal data class SettingsDocument(
    val title: String,
    val paragraphs: List<String>,
    val blocks: List<SettingsDocumentBlock>,
    val lastUpdated: String? = null
)
