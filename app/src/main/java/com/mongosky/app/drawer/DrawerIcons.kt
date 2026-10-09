package com.mongosky.app.drawer

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** Cached local Lucide-style vectors, matching the web menu without a network/icon dependency. */
internal object DrawerIcons {
    val profile by lazy { outline("Profile", "M16 7A4 4 0 1 1 8 7A4 4 0 1 1 16 7M4 21V19A6 6 0 0 1 10 13H14A6 6 0 0 1 20 19V21") }
    val activity by lazy { outline("Activity", "M3 11A9 9 0 1 1 5.5 18.5M3 4V11H10M12 7V12L16 14") }
    val saved by lazy { outline("Saved", "M5 3H19V21L12 17L5 21Z") }
    val signup by lazy { outline("Signup", "M16 21V19A4 4 0 0 0 12 15H6A4 4 0 0 0 2 19V21M13 7A4 4 0 1 1 5 7A4 4 0 1 1 13 7M20 8V14M17 11H23") }
    val settings by lazy { outline("Settings", "M10 3H14L15 6L18 5L21 9L19 12L21 15L18 19L15 18L14 21H10L9 18L6 19L3 15L5 12L3 9L6 5L9 6ZM15 12A3 3 0 1 1 9 12A3 3 0 1 1 15 12") }
    val guidelines by lazy { outline("Guidelines", "M12 3L20 6V12C20 17 16 20 12 22C8 20 4 17 4 12V6ZM8 12L11 15L16 9") }
    val name by lazy { outline("ChangeName", "M15 5L19 9M4 20L5 15L17 3A2.8 2.8 0 0 1 21 7L9 19Z") }
    val mobile by lazy { outline("MobileApp", "M7 2H17A2 2 0 0 1 19 4V20A2 2 0 0 1 17 22H7A2 2 0 0 1 5 20V4A2 2 0 0 1 7 2ZM12 18H12.01") }
    val signOut by lazy { outline("SignOut", "M10 3H4A1 1 0 0 0 3 4V20A1 1 0 0 0 4 21H10M8 12H21M17 8L21 12L17 16") }
    val close by lazy { outline("Close", "M6 6L18 18M18 6L6 18") }
    val verified by lazy {
        builder("Verified").apply {
            addPath(PathParser().parsePathString("M23 12L20.56 9.21L20.9 5.52L17.29 4.7L15.4 1.5L12 2.96L8.6 1.5L6.71 4.69L3.1 5.51L3.44 9.2L1 12L3.44 14.79L3.1 18.48L6.71 19.3L8.6 22.5L12 21.04L15.4 22.5L17.29 19.31L20.9 18.49L20.56 14.8Z").toNodes(), fill = SolidColor(Color(0xFFFF3040)))
            addPath(PathParser().parsePathString("M10.09 16.17L6.5 12.58L7.91 11.17L10.09 13.34L16.09 7.34L17.5 8.76Z").toNodes(), fill = SolidColor(Color.White))
        }.build()
    }
    fun forDestination(item: DrawerDestination): ImageVector = when (item) {
        DrawerDestination.PROFILE -> profile; DrawerDestination.MY_ACTIVITY -> activity
        DrawerDestination.SAVED_ITEMS -> saved; DrawerDestination.SIGNUP -> signup
        DrawerDestination.SETTINGS -> settings; DrawerDestination.GUIDELINES -> guidelines
        DrawerDestination.CHANGE_NAME -> name; DrawerDestination.MOBILE_APP -> mobile
        DrawerDestination.SIGN_OUT -> signOut
    }
    private fun builder(name: String) = ImageVector.Builder("MongoskyDrawer$name", 24.dp, 24.dp, 24f, 24f)
    private fun outline(name: String, data: String) = builder(name).apply {
        addPath(PathParser().parsePathString(data).toNodes(), stroke = SolidColor(Color.Black),
            strokeLineWidth = 2.2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
    }.build()
}
