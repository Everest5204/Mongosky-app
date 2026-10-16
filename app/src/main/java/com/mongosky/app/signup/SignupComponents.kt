package com.mongosky.app.signup

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mongosky.app.R
import com.mongosky.app.darkmode.AppColors
import com.mongosky.app.language.appDescription
import com.mongosky.app.language.appText

internal val SignupRed = Color(0xFFFF172B)
internal val SignupBlue = Color(0xFF005BFF)
internal val SignupBorder = Color(0xFFE2E5EB)
internal val SignupMuted = Color(0xFF6B7280)
internal val SignupInk = Color(0xFF101218)
internal val SignupFieldShape = RoundedCornerShape(percent = 50)

@Composable
internal fun SignupWhitePage(
    subtitle: String, onBack: (() -> Unit)? = null, backEnabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(Modifier.fillMaxSize().testTag("signup-page"), color = AppColors.surface(Color.White)) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
            if (onBack != null) SignupBackBar(onBack, backEnabled)
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val pageHeight = maxHeight
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                    .heightIn(min = pageHeight).padding(horizontal = 24.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Column(Modifier.widthIn(max = 420.dp).fillMaxWidth()) {
                        Text("Mongosky", Modifier.fillMaxWidth().semantics { heading() },
                            textAlign = TextAlign.Center, color = AppColors.content(Color.Black),
                            fontSize = 30.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.7).sp)
                        Spacer(Modifier.height(4.dp))
                        Text(subtitle, Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                            color = AppColors.content(SignupMuted), fontSize = 16.sp, lineHeight = 24.sp)
                        Spacer(Modifier.height(26.dp))
                        content()
                    }
                }
            }
        }
    }
}

@Composable
internal fun SignupBackBar(onBack: () -> Unit, enabled: Boolean = true) {
    Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack, enabled = enabled, modifier = Modifier.size(48.dp).testTag("signup-back")) {
            Icon(SignupBack, appDescription("Back"), tint = AppColors.content(SignupInk), modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
internal fun SignupGoogleButton() {
    // Keep existing native Google-auth availability; this update adjusts its UI only.
    OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        shape = SignupFieldShape, border = BorderStroke(1.dp, AppColors.outline(SignupBorder)),
        colors = ButtonDefaults.outlinedButtonColors(
            disabledContainerColor = AppColors.surface(Color.White), disabledContentColor = AppColors.content(SignupMuted)),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
        Image(SignupGoogleLogo, null, Modifier.size(22.dp).alpha(.55f))
        Spacer(Modifier.width(12.dp))
        Text(stringResource(R.string.signup_google), Modifier.weight(1f, fill = false),
            textAlign = TextAlign.Center, fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun SignupInput(
    value: String, onValueChange: (String) -> Unit, placeholder: String, enabled: Boolean,
    leadingIcon: ImageVector? = null, tag: String = "signup-input",
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: (@Composable () -> Unit)? = null
) {
    OutlinedTextField(value, onValueChange,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(SignupFieldShape).testTag(tag)
            .semantics { contentDescription = placeholder },
        enabled = enabled, singleLine = true,
        placeholder = { Text(placeholder, color = AppColors.content(SignupMuted), fontSize = 16.sp) },
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = AppColors.content(SignupInk)),
        shape = SignupFieldShape, keyboardOptions = keyboardOptions, keyboardActions = keyboardActions,
        visualTransformation = visualTransformation,
        leadingIcon = leadingIcon?.let { icon ->
            { Icon(icon, null, Modifier.size(20.dp), tint = AppColors.content(SignupMuted)) }
        }, trailingIcon = trailingIcon,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = AppColors.surface(Color(0xFFF5F6F8)),
            unfocusedContainerColor = AppColors.surface(Color.White), disabledContainerColor = AppColors.surface(Color.White),
            focusedBorderColor = AppColors.outline(SignupBlue), unfocusedBorderColor = AppColors.outline(SignupBorder),
            disabledBorderColor = AppColors.outline(SignupBorder), cursorColor = AppColors.content(SignupBlue)))
}

@Composable
internal fun SignupChoiceButton(
    text: String, label: String, enabled: Boolean, hasValue: Boolean, onClick: () -> Unit,
    modifier: Modifier = Modifier, leadingIcon: ImageVector? = null, tag: String
) {
    OutlinedButton(onClick, enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp).clip(SignupFieldShape).testTag(tag)
            .semantics { contentDescription = label },
        shape = SignupFieldShape, border = BorderStroke(1.dp, AppColors.outline(SignupBorder)),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = AppColors.surface(Color.White),
            contentColor = AppColors.content(SignupInk), disabledContainerColor = AppColors.surface(Color.White),
            disabledContentColor = AppColors.content(SignupMuted)),
        contentPadding = PaddingValues(horizontal = if (leadingIcon == null) 12.dp else 16.dp, vertical = 12.dp)) {
        if (leadingIcon != null) {
            Icon(leadingIcon, null, Modifier.size(20.dp), tint = AppColors.content(SignupMuted))
            Spacer(Modifier.width(12.dp))
        }
        Text(text, Modifier.weight(1f), textAlign = TextAlign.Start,
            fontSize = 16.sp, lineHeight = 24.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = AppColors.content(if (hasValue) SignupInk else SignupMuted))
        Spacer(Modifier.width(4.dp))
        Icon(SignupChevron, null, Modifier.size(16.dp), tint = AppColors.content(SignupMuted))
    }
}

@Composable
internal fun SignupPolicy(
    accepted: Boolean, enabled: Boolean, onAcceptedChange: (Boolean) -> Unit,
    onOpenDocument: (SignupDocument) -> Unit
) {
    val prefix = stringResource(R.string.signup_policy_prefix)
    val privacy = stringResource(R.string.signup_privacy)
    val middle = stringResource(R.string.signup_policy_middle)
    val about = stringResource(R.string.signup_about)
    val suffix = stringResource(R.string.signup_policy_suffix)
    val links = TextLinkStyles(SpanStyle(color = AppColors.content(SignupBlue), fontWeight = FontWeight.SemiBold))
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("signup-policy")
        .toggleable(accepted, enabled = enabled, role = Role.Checkbox, onValueChange = onAcceptedChange),
        verticalAlignment = Alignment.CenterVertically) {
        // A 16 dp visual checkbox beside the text; the row keeps its 48 dp touch target.
        Box(Modifier.width(28.dp).heightIn(min = 48.dp), contentAlignment = Alignment.CenterStart) {
            Checkbox(accepted, onCheckedChange = null, enabled = enabled,
                modifier = Modifier.size(20.dp).scale(.8f),
                colors = CheckboxDefaults.colors(checkedColor = AppColors.fill(SignupRed),
                    uncheckedColor = AppColors.content(SignupMuted), checkmarkColor = AppColors.content(Color.White)))
        }
        Text(buildAnnotatedString {
            append(prefix)
            withLink(LinkAnnotation.Clickable("signup-privacy", links) { if (enabled) onOpenDocument(SignupDocument.PRIVACY) }) { append(privacy) }
            append(middle)
            withLink(LinkAnnotation.Clickable("signup-about", links) { if (enabled) onOpenDocument(SignupDocument.ABOUT) }) { append(about) }
            append(suffix)
        }, Modifier.weight(1f), color = AppColors.content(SignupInk), fontSize = 14.sp, lineHeight = 21.sp)
    }
}

@Composable
internal fun SignupActionButton(label: String, onClick: () -> Unit, enabled: Boolean = true, loading: Boolean = false) {
    Button(onClick, enabled = enabled && !loading,
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("signup-submit"), shape = SignupFieldShape,
        colors = ButtonDefaults.buttonColors(containerColor = AppColors.fill(SignupRed), contentColor = AppColors.content(Color.White),
            disabledContainerColor = AppColors.fill(SignupRed), disabledContentColor = AppColors.content(Color.White)),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(18.dp), color = AppColors.content(Color.White), strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
        }
        Text(label, fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    }
}

@Composable
internal fun SignupError(message: String?) {
    if (message != null) {
        Text(appText(message), color = AppColors.content(Color(0xFFD60000)), style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.testTag("signup-error").semantics { liveRegion = LiveRegionMode.Polite })
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
internal fun SignupAccountNotice(name: String) {
    Text(stringResource(R.string.signup_signed_in_notice, name), Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
        color = AppColors.content(SignupMuted), fontSize = 13.sp, lineHeight = 19.sp)
    Spacer(Modifier.height(12.dp))
}

@Composable
internal fun SignupSignInFooter(enabled: Boolean, onSignIn: () -> Unit) {
    val prompt = stringResource(R.string.signup_already_account)
    val label = stringResource(R.string.signup_sign_in)
    val styles = TextLinkStyles(SpanStyle(color = AppColors.content(SignupBlue), fontWeight = FontWeight.SemiBold))
    Text(buildAnnotatedString {
        append(prompt); append("  ")
        withLink(LinkAnnotation.Clickable("signup-sign-in", styles) { if (enabled) onSignIn() }) { append(label) }
    }, Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = 12.dp).testTag("signup-sign-in"),
        textAlign = TextAlign.Center, color = AppColors.content(SignupMuted), fontSize = 14.sp, lineHeight = 21.sp)
}
