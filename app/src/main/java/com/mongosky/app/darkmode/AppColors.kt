package com.mongosky.app.darkmode

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** Semantic UI colors. Authored media/art and scrims are never passed through this adapter. */
internal object AppColors {
    val darkBackground = Color(0xFF101319)
    val darkSurface = Color(0xFF191E26)
    val darkSoft = Color(0xFF222A35)
    val darkText = Color(0xFFE8EDF4)
    val darkMuted = Color(0xFFADB7C5)
    val darkOutline = Color(0xFF465263)
    val brand = Color(0xFFE60023)
    val LightScheme = lightColorScheme(primary = brand,onPrimary = Color.White,
        primaryContainer = Color(0xFFFFE7EB),onPrimaryContainer = Color(0xFF8B102A),
        secondary = Color(0xFF00866B),onSecondary = Color.White,
        background = Color.White,onBackground = Color(0xFF111827),surface = Color.White,onSurface = Color(0xFF111827),
        surfaceVariant = Color(0xFFF3F4F6),onSurfaceVariant = Color(0xFF667085),outline = Color(0xFF667085),outlineVariant = Color(0xFFE5E7EB))
    val DarkScheme = darkColorScheme(primary = Color(0xFFFF8294),onPrimary = Color(0xFF380914),
        primaryContainer = Color(0xFF55202C),onPrimaryContainer = Color(0xFFFFD9DF),
        secondary = Color(0xFF75DABC),onSecondary = Color(0xFF06392C),secondaryContainer = Color(0xFF174C3F),onSecondaryContainer = Color(0xFFA1F1D5),
        background = darkBackground,onBackground = darkText,surface = darkSurface,onSurface = darkText,
        surfaceVariant = darkSoft,onSurfaceVariant = darkMuted,outline = darkOutline,outlineVariant = Color(0xFF343E4C),
        error = Color(0xFFFFB4BB),onError = Color(0xFF5B0F1C),errorContainer = Color(0xFF4D2029),onErrorContainer = Color(0xFFFFDADF),
        surfaceDim = darkBackground,surfaceBright = Color(0xFF303948),surfaceContainerLowest = Color(0xFF0C0F14),
        surfaceContainerLow = Color(0xFF151A22),surfaceContainer = darkSurface,surfaceContainerHigh = darkSoft,
        surfaceContainerHighest = Color(0xFF2A3441),inverseSurface = Color(0xFFE8EDF4),inverseOnSurface = Color(0xFF172033))
    val scheme get() = if (AppAppearance.isDark) DarkScheme else LightScheme
    fun scheme(light: ColorScheme) = if (AppAppearance.isDark) DarkScheme else light
    private enum class Role { SURFACE, CONTENT, OUTLINE, FILL }
    private val darkCache = ConcurrentHashMap<Long,Color>()
    fun surface(light: Color): Color = choose(light,Role.SURFACE)
    fun content(light: Color): Color = choose(light,Role.CONTENT)
    fun outline(light: Color): Color = choose(light,Role.OUTLINE)
    fun fill(light: Color): Color = choose(light,Role.FILL)
    fun surfaces(light: List<Color>) = if (AppAppearance.isDark) light.map(::surface) else light
    fun plainPostText(light: Color,plain: Boolean): Color = if (plain) content(light) else light
    private fun choose(light: Color,role: Role): Color {
        if (!AppAppearance.isDark || light == Color.Unspecified || light.alpha == 0f) return light
        val argb = light.toArgb();val key = (role.ordinal.toLong() shl 32) or (argb.toLong() and 0xFFFFFFFFL)
        val cached = darkCache[key];if (cached != null) return cached
        val result = adaptDark(light,role)
        return if (darkCache.size < 1024) darkCache.putIfAbsent(key,result) ?: result else result
    }
    private fun adaptDark(light: Color,role: Role): Color {
        val alpha = light.alpha;val rgb = light.copy(alpha = 1f)
        val l = luminance(rgb);val hi = max(rgb.red,max(rgb.green,rgb.blue));val lo = min(rgb.red,min(rgb.green,rgb.blue));val saturation = hi-lo
        val result = when (role) {
            Role.CONTENT -> if (l >= .72) rgb else if (saturation < .16f) {
                if (l < .07) darkText else darkMuted
            } else ensureContrast(rgb,darkSurface,4.5,true)
            Role.OUTLINE -> if (saturation > .30f && l < .55) ensureContrast(rgb,darkSurface,3.0,true) else darkOutline
            Role.SURFACE -> when {
                l < .12 -> rgb // Intentionally dark media surfaces and the existing AI dark palette.
                saturation < .09f && l > .85 -> darkSurface
                saturation < .16f && l > .50 -> darkSoft
                l > .45 -> Color(.07f+rgb.red*.055f,.075f+rgb.green*.055f,.09f+rgb.blue*.055f)
                else -> ensureContrast(rgb,Color.White,4.5,false)
            }
            Role.FILL -> when {
                l > .72 && saturation < .18f -> darkSoft
                l > .5 -> Color(.08f+rgb.red*.07f,.085f+rgb.green*.07f,.10f+rgb.blue*.07f)
                else -> ensureContrast(rgb,Color.White,4.5,false)
            }
        }
        return result.copy(alpha = alpha)
    }
    internal fun contrast(a: Color,b: Color): Double { val x = luminance(a);val y = luminance(b);return (max(x,y)+.05)/(min(x,y)+.05) }
    private fun ensureContrast(original: Color,background: Color,minimum: Double,lighter: Boolean): Color {
        if (contrast(original,background) >= minimum) return original
        var lo = 0f;var hi = 1f;val target = if (lighter) Color.White else Color.Black
        repeat(12) { val mid = (lo+hi)/2;val c = blend(original,target,mid);if (contrast(c,background) >= minimum) hi = mid else lo = mid }
        return blend(original,target,hi)
    }
    private fun blend(a: Color,b: Color,t: Float) = Color(a.red+(b.red-a.red)*t,a.green+(b.green-a.green)*t,a.blue+(b.blue-a.blue)*t)
    private fun luminance(c: Color): Double {
        fun channel(v: Float): Double { val s=v.toDouble();return if(s<=.04045) s/12.92 else ((s+.055)/1.055).pow(2.4) }
        return .2126*channel(c.red)+.7152*channel(c.green)+.0722*channel(c.blue)
    }
}
