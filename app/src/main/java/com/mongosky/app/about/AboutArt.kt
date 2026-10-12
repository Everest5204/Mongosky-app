package com.mongosky.app.about

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import com.mongosky.app.settingsinformation.SettingsArt
import com.mongosky.app.settingsinformation.SettingsArtNode

// Native paths from the web SVGs, including gradients, strokes and dashed connectors.
internal object AboutArt {
    val connect_and_share by lazy {
        SettingsArt(520f, 280f, listOf(
            SettingsArtNode(data = "M73 88C99 25 178 25 231 51C283 77 326 40 387 52C449 64 476 124 452 180C426 240 361 251 303 235C248 220 208 252 145 234C80 215 46 151 73 88Z", fill = Brush.linearGradient(0f to Color(0xFFDDF7EF), 1f to Color(0xFFF5FCFA), start = Offset(67f, 38f), end = Offset(457f, 244f)), alpha = 1f),
            SettingsArtNode(data = "M151 112L218 126", fill = null, alpha = 1f, stroke = SolidColor(Color(0xFF8ECBBC)), strokeWidth = 4f, roundCap = true, dash = listOf(7f, 8f)),
            SettingsArtNode(data = "M301 122L375 99", fill = null, alpha = 1f, stroke = SolidColor(Color(0xFF8ECBBC)), strokeWidth = 4f, roundCap = true, dash = listOf(7f, 8f)),
            SettingsArtNode(data = "M300 165L379 191", fill = null, alpha = 1f, stroke = SolidColor(Color(0xFF8ECBBC)), strokeWidth = 4f, roundCap = true, dash = listOf(7f, 8f)),
            SettingsArtNode(data = "M84.0 75.0H148.0A16.0 16.0 0 0 1 164.0 91.0V137.0A16.0 16.0 0 0 1 148.0 153.0H84.0A16.0 16.0 0 0 1 68.0 137.0V91.0A16.0 16.0 0 0 1 84.0 75.0Z", fill = SolidColor(Color(0xFFFFFFFF)), alpha = 1f, stroke = SolidColor(Color(0xFF111827)), strokeWidth = 3f),
            SettingsArtNode(data = "M82.0 99.0A10.0 10.0 0 1 0 102.0 99.0A10.0 10.0 0 1 0 82.0 99.0Z", fill = SolidColor(Color(0xFF00866B)), alpha = 1f),
            SettingsArtNode(data = "M112 95H141", fill = null, alpha = 1f, stroke = SolidColor(Color(0xFF111827)), strokeWidth = 5f, roundCap = true),
            SettingsArtNode(data = "M88 124H143", fill = null, alpha = 1f, stroke = SolidColor(Color(0xFFCAD4DA)), strokeWidth = 4f, roundCap = true),
            SettingsArtNode(data = "M88 139H128", fill = null, alpha = 1f, stroke = SolidColor(Color(0xFFCAD4DA)), strokeWidth = 4f, roundCap = true),
            SettingsArtNode(data = "M190.0 142.0A70.0 70.0 0 1 0 330.0 142.0A70.0 70.0 0 1 0 190.0 142.0Z", fill = SolidColor(Color(0xFFFFFFFF)), alpha = 1f, stroke = SolidColor(Color(0xFF111827)), strokeWidth = 4f),
            SettingsArtNode(data = "M232.0 122.0A28.0 28.0 0 1 0 288.0 122.0A28.0 28.0 0 1 0 232.0 122.0Z", fill = Brush.linearGradient(0f to Color(0xFF10A987), 1f to Color(0xFF007A62), start = Offset(210f, 75f), end = Offset(310f, 207f)), alpha = 1f),
            SettingsArtNode(data = "M213 193C220 160 238 148 260 148C282 148 300 160 307 193", fill = SolidColor(Color(0xFFDDF7EF)), alpha = 1f, stroke = SolidColor(Color(0xFF00866B)), strokeWidth = 4f, roundCap = true),
            SettingsArtNode(data = "M287.0 92.0A20.0 20.0 0 1 0 327.0 92.0A20.0 20.0 0 1 0 287.0 92.0Z", fill = SolidColor(Color(0xFF00866B)), alpha = 1f, stroke = SolidColor(Color(0xFFFFFFFF)), strokeWidth = 5f),
            SettingsArtNode(data = "M298 92H316", fill = null, alpha = 1f, stroke = SolidColor(Color(0xFFFFFFFF)), strokeWidth = 4f, roundCap = true),
            SettingsArtNode(data = "M307 83V101", fill = null, alpha = 1f, stroke = SolidColor(Color(0xFFFFFFFF)), strokeWidth = 4f, roundCap = true),
            SettingsArtNode(data = "M365 71H439C449 71 457 79 457 89V128C457 138 449 146 439 146H408L389 163V146H365C355 146 347 138 347 128V89C347 79 355 71 365 71Z", fill = SolidColor(Color(0xFFFFFFFF)), alpha = 1f, stroke = SolidColor(Color(0xFF111827)), strokeWidth = 3f, roundJoin = true),
            SettingsArtNode(data = "M375.0 109.0A5.0 5.0 0 1 0 385.0 109.0A5.0 5.0 0 1 0 375.0 109.0Z", fill = SolidColor(Color(0xFF00866B)), alpha = 1f),
            SettingsArtNode(data = "M397.0 109.0A5.0 5.0 0 1 0 407.0 109.0A5.0 5.0 0 1 0 397.0 109.0Z", fill = SolidColor(Color(0xFF00866B)), alpha = 1f),
            SettingsArtNode(data = "M419.0 109.0A5.0 5.0 0 1 0 429.0 109.0A5.0 5.0 0 1 0 419.0 109.0Z", fill = SolidColor(Color(0xFF00866B)), alpha = 1f),
            SettingsArtNode(data = "M370.0 203.0A39.0 39.0 0 1 0 448.0 203.0A39.0 39.0 0 1 0 370.0 203.0Z", fill = SolidColor(Color(0xFFFFFFFF)), alpha = 1f, stroke = SolidColor(Color(0xFF111827)), strokeWidth = 3f),
            SettingsArtNode(data = "M409 221C390 210 381 200 381 188C381 177 390 170 400 170C405 170 409 173 409 177C413 173 417 170 423 170C433 170 441 177 441 188C441 200 431 210 409 221Z", fill = SolidColor(Color(0xFFEF4444)), alpha = 1f),
        ))
    }
    val safe_community by lazy {
        SettingsArt(520f, 280f, listOf(
            SettingsArtNode(data = "M72 89C103 29 176 24 230 49C287 75 329 38 391 54C451 70 476 127 449 184C421 241 357 251 298 235C242 220 201 250 143 233C79 214 41 149 72 89Z", fill = Brush.linearGradient(0f to Color(0xFFF0F9F7), 1f to Color(0xFFDDF7EF), start = Offset(61f, 39f), end = Offset(457f, 241f)), alpha = 1f),
            SettingsArtNode(data = "M82.0 76.0A9.0 9.0 0 1 0 100.0 76.0A9.0 9.0 0 1 0 82.0 76.0Z", fill = SolidColor(Color(0xFF00866B)), alpha = 0.2f),
            SettingsArtNode(data = "M421.0 207.0A12.0 12.0 0 1 0 445.0 207.0A12.0 12.0 0 1 0 421.0 207.0Z", fill = SolidColor(Color(0xFF00866B)), alpha = 0.14f),
            SettingsArtNode(data = "M90.0 125.0A30.0 30.0 0 1 0 150.0 125.0A30.0 30.0 0 1 0 90.0 125.0Z", fill = SolidColor(Color(0xFFFFFFFF)), alpha = 1f, stroke = SolidColor(Color(0xFF111827)), strokeWidth = 3f),
            SettingsArtNode(data = "M110.0 116.0A10.0 10.0 0 1 0 130.0 116.0A10.0 10.0 0 1 0 110.0 116.0Z", fill = SolidColor(Color(0xFF00866B)), alpha = 1f),
            SettingsArtNode(data = "M101 144C105 130 112 126 120 126C128 126 135 130 139 144", fill = SolidColor(Color(0xFFDDF7EF)), alpha = 1f, stroke = SolidColor(Color(0xFF00866B)), strokeWidth = 3f, roundCap = true),
            SettingsArtNode(data = "M370.0 125.0A30.0 30.0 0 1 0 430.0 125.0A30.0 30.0 0 1 0 370.0 125.0Z", fill = SolidColor(Color(0xFFFFFFFF)), alpha = 1f, stroke = SolidColor(Color(0xFF111827)), strokeWidth = 3f),
            SettingsArtNode(data = "M390.0 116.0A10.0 10.0 0 1 0 410.0 116.0A10.0 10.0 0 1 0 390.0 116.0Z", fill = SolidColor(Color(0xFF00866B)), alpha = 1f),
            SettingsArtNode(data = "M381 144C385 130 392 126 400 126C408 126 415 130 419 144", fill = SolidColor(Color(0xFFDDF7EF)), alpha = 1f, stroke = SolidColor(Color(0xFF00866B)), strokeWidth = 3f, roundCap = true),
            SettingsArtNode(data = "M151 125H196", fill = null, alpha = 1f, stroke = SolidColor(Color(0xFF8ECBBC)), strokeWidth = 4f, roundCap = true, dash = listOf(7f, 8f)),
            SettingsArtNode(data = "M324 125H369", fill = null, alpha = 1f, stroke = SolidColor(Color(0xFF8ECBBC)), strokeWidth = 4f, roundCap = true, dash = listOf(7f, 8f)),
            SettingsArtNode(data = "M260 45L326 70V123C326 168 299 207 260 224C221 207 194 168 194 123V70L260 45Z", fill = Brush.linearGradient(0f to Color(0xFF12AE8B), 1f to Color(0xFF00755E), start = Offset(211f, 44f), end = Offset(310f, 220f)), alpha = 1f, stroke = SolidColor(Color(0xFF006A55)), strokeWidth = 4f, roundJoin = true),
            SettingsArtNode(data = "M215.0 126.0A45.0 45.0 0 1 0 305.0 126.0A45.0 45.0 0 1 0 215.0 126.0Z", fill = SolidColor(Color(0xFFFFFFFF)), alpha = 0.16f),
            SettingsArtNode(data = "M233 126L251 144L289 105", fill = null, alpha = 1f, stroke = SolidColor(Color(0xFFFFFFFF)), strokeWidth = 9f, roundCap = true, roundJoin = true),
            SettingsArtNode(data = "M102 192C132 221 173 230 209 215", fill = null, alpha = 1f, stroke = SolidColor(Color(0xFF111827)), strokeWidth = 4f, roundCap = true),
            SettingsArtNode(data = "M311 215C347 230 388 221 418 192", fill = null, alpha = 1f, stroke = SolidColor(Color(0xFF111827)), strokeWidth = 4f, roundCap = true),
            SettingsArtNode(data = "M102 192L104 207", fill = null, alpha = 1f, stroke = SolidColor(Color(0xFF111827)), strokeWidth = 4f, roundCap = true),
            SettingsArtNode(data = "M418 192L416 207", fill = null, alpha = 1f, stroke = SolidColor(Color(0xFF111827)), strokeWidth = 4f, roundCap = true),
        ))
    }
    val future_growth by lazy {
        SettingsArt(520f, 280f, listOf(
            SettingsArtNode(data = "M69 93C94 34 165 20 222 47C278 73 322 41 382 51C445 62 478 119 456 177C434 237 371 256 310 237C253 219 209 252 147 235C82 217 43 154 69 93Z", fill = Brush.linearGradient(0f to Color(0xFFDDF7EF), 1f to Color(0xFFF7FCFB), start = Offset(64f, 38f), end = Offset(456f, 246f)), alpha = 1f),
            SettingsArtNode(data = "M145.0 51.0H245.0A27.0 27.0 0 0 1 272.0 78.0V214.0A27.0 27.0 0 0 1 245.0 241.0H145.0A27.0 27.0 0 0 1 118.0 214.0V78.0A27.0 27.0 0 0 1 145.0 51.0Z", fill = SolidColor(Color(0xFFFFFFFF)), alpha = 1f, stroke = SolidColor(Color(0xFF111827)), strokeWidth = 4f),
            SettingsArtNode(data = "M149.0 73.0H241.0A15.0 15.0 0 0 1 256.0 88.0V201.0A15.0 15.0 0 0 1 241.0 216.0H149.0A15.0 15.0 0 0 1 134.0 201.0V88.0A15.0 15.0 0 0 1 149.0 73.0Z", fill = Brush.linearGradient(0f to Color(0xFF12AE8B), 1f to Color(0xFF00755E), start = Offset(148f, 68f), end = Offset(270f, 224f)), alpha = 1f),
            SettingsArtNode(data = "M173 62H217", fill = null, alpha = 1f, stroke = SolidColor(Color(0xFF111827)), strokeWidth = 5f, roundCap = true),
            SettingsArtNode(data = "M189.0 228.0A6.0 6.0 0 1 0 201.0 228.0A6.0 6.0 0 1 0 189.0 228.0Z", fill = SolidColor(Color(0xFF111827)), alpha = 1f),
            SettingsArtNode(data = "M170.0 122.0A25.0 25.0 0 1 0 220.0 122.0A25.0 25.0 0 1 0 170.0 122.0Z", fill = SolidColor(Color(0xFFFFFFFF)), alpha = 0.95f),
            SettingsArtNode(data = "M155 180C161 151 174 143 195 143C216 143 229 151 235 180", fill = SolidColor(Color(0xFFDDF7EF)), alpha = 1f, stroke = SolidColor(Color(0xFFFFFFFF)), strokeWidth = 4f, roundCap = true),
            SettingsArtNode(data = "M282 199C314 196 329 176 338 150C348 119 366 95 409 85", fill = null, alpha = 1f, stroke = SolidColor(Color(0xFF00866B)), strokeWidth = 7f, roundCap = true),
            SettingsArtNode(data = "M390 73L419 82L402 107", fill = null, alpha = 1f, stroke = SolidColor(Color(0xFF00866B)), strokeWidth = 7f, roundCap = true, roundJoin = true),
            SettingsArtNode(data = "M308.0 178.0H327.0A8.0 8.0 0 0 1 335.0 186.0V212.0A8.0 8.0 0 0 1 327.0 220.0H308.0A8.0 8.0 0 0 1 300.0 212.0V186.0A8.0 8.0 0 0 1 308.0 178.0Z", fill = SolidColor(Color(0xFFFFFFFF)), alpha = 1f, stroke = SolidColor(Color(0xFF111827)), strokeWidth = 3f),
            SettingsArtNode(data = "M355.0 155.0H374.0A8.0 8.0 0 0 1 382.0 163.0V212.0A8.0 8.0 0 0 1 374.0 220.0H355.0A8.0 8.0 0 0 1 347.0 212.0V163.0A8.0 8.0 0 0 1 355.0 155.0Z", fill = SolidColor(Color(0xFFFFFFFF)), alpha = 1f, stroke = SolidColor(Color(0xFF111827)), strokeWidth = 3f),
            SettingsArtNode(data = "M402.0 128.0H421.0A8.0 8.0 0 0 1 429.0 136.0V212.0A8.0 8.0 0 0 1 421.0 220.0H402.0A8.0 8.0 0 0 1 394.0 212.0V136.0A8.0 8.0 0 0 1 402.0 128.0Z", fill = SolidColor(Color(0xFFFFFFFF)), alpha = 1f, stroke = SolidColor(Color(0xFF111827)), strokeWidth = 3f),
            SettingsArtNode(data = "M313.0 192.0H322.0A4.0 4.0 0 0 1 326.0 196.0V207.0A4.0 4.0 0 0 1 322.0 211.0H313.0A4.0 4.0 0 0 1 309.0 207.0V196.0A4.0 4.0 0 0 1 313.0 192.0Z", fill = SolidColor(Color(0xFFA9DED2)), alpha = 1f),
            SettingsArtNode(data = "M360.0 169.0H369.0A4.0 4.0 0 0 1 373.0 173.0V207.0A4.0 4.0 0 0 1 369.0 211.0H360.0A4.0 4.0 0 0 1 356.0 207.0V173.0A4.0 4.0 0 0 1 360.0 169.0Z", fill = SolidColor(Color(0xFF54BDA6)), alpha = 1f),
            SettingsArtNode(data = "M407.0 142.0H416.0A4.0 4.0 0 0 1 420.0 146.0V207.0A4.0 4.0 0 0 1 416.0 211.0H407.0A4.0 4.0 0 0 1 403.0 207.0V146.0A4.0 4.0 0 0 1 407.0 142.0Z", fill = SolidColor(Color(0xFF00866B)), alpha = 1f),
            SettingsArtNode(data = "M408.0 68.0A9.0 9.0 0 1 0 426.0 68.0A9.0 9.0 0 1 0 408.0 68.0Z", fill = SolidColor(Color(0xFF10A987)), alpha = 0.25f),
            SettingsArtNode(data = "M79.0 199.0A12.0 12.0 0 1 0 103.0 199.0A12.0 12.0 0 1 0 79.0 199.0Z", fill = SolidColor(Color(0xFF007A62)), alpha = 0.13f),
        ))
    }
}
