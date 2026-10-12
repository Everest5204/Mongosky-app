package com.mongosky.app.privacypolicy

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import com.mongosky.app.settingsinformation.SettingsArt
import com.mongosky.app.settingsinformation.SettingsArtNode

// Native paths from the web SVGs, including gradients, strokes and dashed connectors.
internal object PrivacyPolicyArt {
    val data_protection by lazy {
        SettingsArt(520f, 280f, listOf(
            SettingsArtNode(data = "M82 63C126 16 196 28 240 49C296 76 336 37 391 54C450 72 472 132 442 185C411 240 351 257 292 240C234 223 205 256 144 236C80 214 38 110 82 63Z", fill = Brush.linearGradient(0f to Color(0xFFDDF7EF), 1f to Color(0xFFF2FBF8), start = Offset(78f, 34f), end = Offset(438f, 246f)), alpha = 1f),
            SettingsArtNode(data = "M96.0 73.0A8.0 8.0 0 1 0 112.0 73.0A8.0 8.0 0 1 0 96.0 73.0Z", fill = SolidColor(Color(0xFF10A987)), alpha = 0.25f),
            SettingsArtNode(data = "M408.0 197.0A11.0 11.0 0 1 0 430.0 197.0A11.0 11.0 0 1 0 408.0 197.0Z", fill = SolidColor(Color(0xFF007A62)), alpha = 0.16f),
            SettingsArtNode(data = "M157 143H202", fill = null, alpha = 1f, stroke = SolidColor(Color(0xFF9CCFC3)), strokeWidth = 3f, roundCap = true, dash = listOf(7f, 8f)),
            SettingsArtNode(data = "M319 142H367", fill = null, alpha = 1f, stroke = SolidColor(Color(0xFF9CCFC3)), strokeWidth = 3f, roundCap = true, dash = listOf(7f, 8f)),
            SettingsArtNode(data = "M77.0 112.0A49.0 18.0 0 1 0 175.0 112.0A49.0 18.0 0 1 0 77.0 112.0Z", fill = SolidColor(Color(0xFFFFFFFF)), alpha = 1f, stroke = SolidColor(Color(0xFF111827)), strokeWidth = 3f),
            SettingsArtNode(data = "M77 112V171C77 181 99 189 126 189C153 189 175 181 175 171V112", fill = SolidColor(Color(0xFFFFFFFF)), alpha = 1f, stroke = SolidColor(Color(0xFF111827)), strokeWidth = 3f),
            SettingsArtNode(data = "M77 141C77 151 99 159 126 159C153 159 175 151 175 141", fill = null, alpha = 1f, stroke = SolidColor(Color(0xFF111827)), strokeWidth = 3f),
            SettingsArtNode(data = "M77 170C77 180 99 188 126 188C153 188 175 180 175 170", fill = null, alpha = 1f, stroke = SolidColor(Color(0xFF111827)), strokeWidth = 3f),
            SettingsArtNode(data = "M90.0 112.0A36.0 10.0 0 1 0 162.0 112.0A36.0 10.0 0 1 0 90.0 112.0Z", fill = SolidColor(Color(0xFFDDF7EF)), alpha = 1f),
            SettingsArtNode(data = "M260 48L321 72V124C321 166 296 203 260 218C224 203 199 166 199 124V72L260 48Z", fill = Brush.linearGradient(0f to Color(0xFF10A987), 1f to Color(0xFF007A62), start = Offset(205f, 52f), end = Offset(316f, 214f)), alpha = 1f, stroke = SolidColor(Color(0xFF006A55)), strokeWidth = 4f, roundJoin = true),
            SettingsArtNode(data = "M237 126V111C237 98 247 88 260 88C273 88 283 98 283 111V126", fill = null, alpha = 1f, stroke = SolidColor(Color(0xFFFFFFFF)), strokeWidth = 7f, roundCap = true),
            SettingsArtNode(data = "M240.0 121.0H280.0A13.0 13.0 0 0 1 293.0 134.0V162.0A13.0 13.0 0 0 1 280.0 175.0H240.0A13.0 13.0 0 0 1 227.0 162.0V134.0A13.0 13.0 0 0 1 240.0 121.0Z", fill = SolidColor(Color(0xFFFFFFFF)), alpha = 1f),
            SettingsArtNode(data = "M253.0 146.0A7.0 7.0 0 1 0 267.0 146.0A7.0 7.0 0 1 0 253.0 146.0Z", fill = SolidColor(Color(0xFF00866B)), alpha = 1f),
            SettingsArtNode(data = "M260 152V160", fill = null, alpha = 1f, stroke = SolidColor(Color(0xFF00866B)), strokeWidth = 5f, roundCap = true),
            SettingsArtNode(data = "M380.0 85.0H435.0A14.0 14.0 0 0 1 449.0 99.0V179.0A14.0 14.0 0 0 1 435.0 193.0H380.0A14.0 14.0 0 0 1 366.0 179.0V99.0A14.0 14.0 0 0 1 380.0 85.0Z", fill = SolidColor(Color(0xFFFFFFFF)), alpha = 1f, stroke = SolidColor(Color(0xFF111827)), strokeWidth = 3f),
            SettingsArtNode(data = "M388 111H428", fill = null, alpha = 1f, stroke = SolidColor(Color(0xFF00866B)), strokeWidth = 5f, roundCap = true),
            SettingsArtNode(data = "M388 135H431", fill = null, alpha = 1f, stroke = SolidColor(Color(0xFFCBD5E1)), strokeWidth = 4f, roundCap = true),
            SettingsArtNode(data = "M388 154H422", fill = null, alpha = 1f, stroke = SolidColor(Color(0xFFCBD5E1)), strokeWidth = 4f, roundCap = true),
            SettingsArtNode(data = "M394.0 178.0A13.0 13.0 0 1 0 420.0 178.0A13.0 13.0 0 1 0 394.0 178.0Z", fill = SolidColor(Color(0xFFE1F7F0)), alpha = 1f),
            SettingsArtNode(data = "M401 178L405 182L414 173", fill = null, alpha = 1f, stroke = SolidColor(Color(0xFF00866B)), strokeWidth = 3f, roundCap = true, roundJoin = true),
        ))
    }
    val privacy_control by lazy {
        SettingsArt(520f, 280f, listOf(
            SettingsArtNode(data = "M67 97C93 35 170 25 224 53C275 79 315 43 376 52C438 62 474 113 458 171C442 230 382 252 323 237C264 222 223 253 160 237C91 219 40 162 67 97Z", fill = Brush.linearGradient(0f to Color(0xFFE4F7F2), 1f to Color(0xFFF7FBFA), start = Offset(76f, 42f), end = Offset(450f, 242f)), alpha = 1f),
            SettingsArtNode(data = "M416.0 81.0A9.0 9.0 0 1 0 434.0 81.0A9.0 9.0 0 1 0 416.0 81.0Z", fill = SolidColor(Color(0xFF10A987)), alpha = 0.22f),
            SettingsArtNode(data = "M64.0 203.0A12.0 12.0 0 1 0 88.0 203.0A12.0 12.0 0 1 0 64.0 203.0Z", fill = SolidColor(Color(0xFF007A62)), alpha = 0.14f),
            SettingsArtNode(data = "M84.0 135.0A67.0 67.0 0 1 0 218.0 135.0A67.0 67.0 0 1 0 84.0 135.0Z", fill = SolidColor(Color(0xFFFFFFFF)), alpha = 1f, stroke = SolidColor(Color(0xFF111827)), strokeWidth = 3f),
            SettingsArtNode(data = "M126.0 118.0A25.0 25.0 0 1 0 176.0 118.0A25.0 25.0 0 1 0 126.0 118.0Z", fill = Brush.linearGradient(0f to Color(0xFF10A987), 1f to Color(0xFF007A62), start = Offset(92f, 91f), end = Offset(202f, 207f)), alpha = 1f),
            SettingsArtNode(data = "M105 187C112 159 129 147 151 147C173 147 190 159 197 187", fill = SolidColor(Color(0xFFDDF7EF)), alpha = 1f, stroke = SolidColor(Color(0xFF00866B)), strokeWidth = 4f, roundCap = true),
            SettingsArtNode(data = "M170.0 84.0A20.0 20.0 0 1 0 210.0 84.0A20.0 20.0 0 1 0 170.0 84.0Z", fill = SolidColor(Color(0xFFFFFFFF)), alpha = 1f, stroke = SolidColor(Color(0xFF111827)), strokeWidth = 3f),
            SettingsArtNode(data = "M181 84L187 90L199 77", fill = null, alpha = 1f, stroke = SolidColor(Color(0xFF00866B)), strokeWidth = 4f, roundCap = true, roundJoin = true),
            SettingsArtNode(data = "M220 139H264", fill = null, alpha = 1f, stroke = SolidColor(Color(0xFF9CCFC3)), strokeWidth = 3f, roundCap = true, dash = listOf(7f, 8f)),
            SettingsArtNode(data = "M287.0 68.0H428.0A22.0 22.0 0 0 1 450.0 90.0V191.0A22.0 22.0 0 0 1 428.0 213.0H287.0A22.0 22.0 0 0 1 265.0 191.0V90.0A22.0 22.0 0 0 1 287.0 68.0Z", fill = SolidColor(Color(0xFFFFFFFF)), alpha = 1f, stroke = SolidColor(Color(0xFF111827)), strokeWidth = 3f),
            SettingsArtNode(data = "M292 105H420", fill = null, alpha = 1f, stroke = SolidColor(Color(0xFFD1D9DE)), strokeWidth = 6f, roundCap = true),
            SettingsArtNode(data = "M320.0 105.0A13.0 13.0 0 1 0 346.0 105.0A13.0 13.0 0 1 0 320.0 105.0Z", fill = SolidColor(Color(0xFF00866B)), alpha = 1f, stroke = SolidColor(Color(0xFFFFFFFF)), strokeWidth = 4f),
            SettingsArtNode(data = "M292 141H420", fill = null, alpha = 1f, stroke = SolidColor(Color(0xFFD1D9DE)), strokeWidth = 6f, roundCap = true),
            SettingsArtNode(data = "M373.0 141.0A13.0 13.0 0 1 0 399.0 141.0A13.0 13.0 0 1 0 373.0 141.0Z", fill = SolidColor(Color(0xFF00866B)), alpha = 1f, stroke = SolidColor(Color(0xFFFFFFFF)), strokeWidth = 4f),
            SettingsArtNode(data = "M292 177H420", fill = null, alpha = 1f, stroke = SolidColor(Color(0xFFD1D9DE)), strokeWidth = 6f, roundCap = true),
            SettingsArtNode(data = "M303.0 177.0A13.0 13.0 0 1 0 329.0 177.0A13.0 13.0 0 1 0 303.0 177.0Z", fill = SolidColor(Color(0xFF00866B)), alpha = 1f, stroke = SolidColor(Color(0xFFFFFFFF)), strokeWidth = 4f),
            SettingsArtNode(data = "M393.0 207.0A32.0 32.0 0 1 0 457.0 207.0A32.0 32.0 0 1 0 393.0 207.0Z", fill = SolidColor(Color(0xFF00866B)), alpha = 1f, stroke = SolidColor(Color(0xFFFFFFFF)), strokeWidth = 5f),
            SettingsArtNode(data = "M414 207L422 215L438 198", fill = null, alpha = 1f, stroke = SolidColor(Color(0xFFFFFFFF)), strokeWidth = 5f, roundCap = true, roundJoin = true),
        ))
    }
}
