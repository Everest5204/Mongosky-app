package com.mongosky.app.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mongosky.app.profile.OwnProfileTab

@Composable
internal fun OwnProfileTabs(selected: OwnProfileTab, onSelect: (OwnProfileTab) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth().selectableGroup()) {
            OwnProfileTab.entries.forEach { tab ->
                val active = tab == selected
                Column(Modifier.weight(1f).selectable(active, role = Role.Tab) { onSelect(tab) }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.fillMaxWidth().heightIn(min = 58.dp).padding(vertical = 18.dp), contentAlignment = Alignment.Center) {
                        Text(tab.label, fontSize = 17.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                            color = if (active) OwnProfileColors.brand else OwnProfileColors.text)
                    }
                    Box(Modifier.fillMaxWidth().height(3.dp).background(if (active) OwnProfileColors.brand else androidx.compose.ui.graphics.Color.Transparent))
                }
            }
        }
        OwnProfileDivider()
    }
}
