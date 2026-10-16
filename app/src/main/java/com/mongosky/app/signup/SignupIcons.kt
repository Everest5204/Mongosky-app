package com.mongosky.app.signup

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

internal val SignupGoogleLogo = ImageVector.Builder(
    name = "SignupGoogleLogo", defaultWidth = 20.dp, defaultHeight = 20.dp,
    viewportWidth = 24f, viewportHeight = 24f
).addPath(
    pathData = addPathNodes("M22.56 12.25c0-.71-.06-1.39-.18-2.05H12v3.88h5.92c-.26 1.25-1.03 2.31-2.19 3.02v2.51h3.54c2.06-1.9 3.29-4.7 3.29-8.36z"),
    fill = SolidColor(Color(0xFF4285F4))
).addPath(
    pathData = addPathNodes("M12 23c2.97 0 5.46-.98 7.28-2.66l-3.54-2.51c-.98.66-2.24 1.05-3.74 1.05-2.87 0-5.3-1.94-6.17-4.54H2.18v2.59C3.99 20.53 7.7 23 12 23z"),
    fill = SolidColor(Color(0xFF34A853))
).addPath(
    pathData = addPathNodes("M5.83 14.34c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.57H2.18A10.95 10.95 0 0 0 1 12.25c0 1.68.4 3.27 1.18 4.68l3.65-2.59z"),
    fill = SolidColor(Color(0xFFFBBC05))
).addPath(
    pathData = addPathNodes("M12 5.62c1.62 0 3.06.56 4.21 1.65l3.15-3.15C17.46 2.34 14.97 1.25 12 1.25c-4.3 0-8.01 2.47-9.82 6.32l3.65 2.59c.87-2.6 3.3-4.54 6.17-4.54z"),
    fill = SolidColor(Color(0xFFEA4335))
).build()

private fun signupEyeVector(hidden: Boolean): ImageVector {
    val builder = ImageVector.Builder(
        name = if (hidden) "SignupEyeOff" else "SignupEye",
        defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    )
    builder.addPath(
        pathData = addPathNodes("M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7S2 12 2 12Z M15 12a3 3 0 1 1-6 0 3 3 0 0 1 6 0"),
        fill = null, stroke = SolidColor(Color(0xFF98A2B3)), strokeLineWidth = 1.8f,
        strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
    )
    if (hidden) builder.addPath(
        pathData = addPathNodes("M3 3L21 21"),
        fill = null, stroke = SolidColor(Color(0xFF98A2B3)), strokeLineWidth = 1.8f,
        strokeLineCap = StrokeCap.Round
    )
    return builder.build()
}

internal val SignupEye = signupEyeVector(false)
internal val SignupEyeOff = signupEyeVector(true)

// Local vectors need no downloaded font, remote image, or icons extension.
private fun signupOutlineVector(name: String, path: String): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).addPath(
        pathData = addPathNodes(path),
        fill = null,
        stroke = SolidColor(SignupMuted),
        strokeLineWidth = 1.8f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ).build()

internal val SignupMail = signupOutlineVector(
    "SignupMail",
    "M5 5H19A2 2 0 0 1 21 7V17A2 2 0 0 1 19 19H5A2 2 0 0 1 3 17V7A2 2 0 0 1 5 5Z M3 7L12 13L21 7"
)

internal val SignupLock = signupOutlineVector(
    "SignupLock",
    "M7 10V7A5 5 0 0 1 17 7V10 M6 10H18A2 2 0 0 1 20 12V20A2 2 0 0 1 18 22H6A2 2 0 0 1 4 20V12A2 2 0 0 1 6 10Z M12 15V18 M13 14A1 1 0 1 1 11 14A1 1 0 0 1 13 14Z"
)

internal val SignupPerson = signupOutlineVector(
    "SignupPerson",
    "M16 7A4 4 0 1 1 8 7A4 4 0 0 1 16 7Z M4 21V19A8 8 0 0 1 20 19V21"
)

internal val SignupCalendar = signupOutlineVector(
    "SignupCalendar",
    "M6 5H18A2 2 0 0 1 20 7V19A2 2 0 0 1 18 21H6A2 2 0 0 1 4 19V7A2 2 0 0 1 6 5Z M8 3V7 M16 3V7 M4 11H20 M8 15H10 M14 15H16"
)

internal val SignupBack = signupOutlineVector("SignupBack", "M15 18L9 12L15 6")
internal val SignupChevron = signupOutlineVector("SignupChevron", "M6 9L12 15L18 9")
internal val SignupCheck = signupOutlineVector("SignupCheck", "M5 12L10 17L19 7")

