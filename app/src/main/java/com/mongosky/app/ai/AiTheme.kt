package com.mongosky.app.ai

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme

@Immutable
internal data class AiColors(
    val background:Color,val surface:Color,val soft:Color,val text:Color,val muted:Color,
    val border:Color,val user:Color,val errorBackground:Color,val errorText:Color,val errorBorder:Color
) {
    val red=Color(0xFFFF0000)
    fun scheme(dark:Boolean) = if(dark) darkColorScheme(primary=red,onPrimary=Color.White,
        background=background,onBackground=text,surface=surface,onSurface=text,
        surfaceVariant=soft,onSurfaceVariant=muted,outline=border,error=errorText)
    else lightColorScheme(primary=red,onPrimary=Color.White,background=background,onBackground=text,
        surface=surface,onSurface=text,surfaceVariant=soft,onSurfaceVariant=muted,outline=border,error=errorText)
    companion object {
        val Light=AiColors(Color.White,Color.White,Color(0xFFF6F7F9),Color(0xFF17191C),Color(0xFF7A7F87),
            Color(0xFFE6E8EB),Color(0xFFF0F2F4),Color(0xFFFFF1F2),Color(0xFFB91C31),Color(0xFFFFD5DC))
        val Dark=AiColors(Color(0xFF101113),Color(0xFF17191C),Color(0xFF202226),Color(0xFFF5F6F7),Color(0xFFA6ABB2),
            Color(0xFF2B2E33),Color(0xFF25282D),Color(0xFF361A1F),Color(0xFFFFB6BF),Color(0xFF643039))
    }
}
