package com.mongosky.app.presentation.passwordreset

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.platform.LocalAutofillManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mongosky.app.ui.theme.AuthTheme

private val ResetRed = Color(0xFFFF172B)
private val ResetBlue = Color(0xFF005BFF)
private val ResetBorder = Color(0xFFE2E5EB)
private val ResetMuted = Color(0xFF6B7280)
private val ResetInk = Color(0xFF101218)
private val ResetPill = RoundedCornerShape(percent = 50)

@Composable
fun PasswordResetScreen(
    viewModel: PasswordResetViewModel,
    onSignIn: () -> Unit
) {
    val state = viewModel.uiState
    val focusManager = LocalFocusManager.current
    val autofillManager = LocalAutofillManager.current

    // Passwords are never placed in rememberSaveable or a saved-state bundle.
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var showConfirmation by remember { mutableStateOf(false) }

    // Keep password fields mounted until their autofill context is handled.
    var displayedStep by remember { mutableStateOf(state.step) }

    LaunchedEffect(state.step) {
        if (displayedStep != state.step) {
            if (displayedStep == PasswordResetStep.NEW_PASSWORD) {
                if (state.step == PasswordResetStep.SUCCESS) {
                    autofillManager?.commit()
                } else {
                    autofillManager?.cancel()
                }
            }
            focusManager.clearFocus()
            password = ""
            confirmation = ""
            showPassword = false
            showConfirmation = false
            displayedStep = state.step
        }
    }

    DisposableEffect(autofillManager, viewModel) {
        onDispose {
            if (viewModel.uiState.step != PasswordResetStep.SUCCESS) {
                autofillManager?.cancel()
            }
        }
    }

    val transitioning = displayedStep != state.step

    val signIn: () -> Unit = {
        if (!viewModel.uiState.loading && !transitioning) {
            if (viewModel.uiState.step != PasswordResetStep.SUCCESS) {
                autofillManager?.cancel()
            }
            focusManager.clearFocus()
            password = ""
            confirmation = ""
            showPassword = false
            showConfirmation = false
            onSignIn()
        }
    }

    val goBack: () -> Unit = {
        if (!viewModel.uiState.loading && !transitioning) {
            if (viewModel.uiState.step != PasswordResetStep.SUCCESS) {
                autofillManager?.cancel()
            }
            focusManager.clearFocus()
            password = ""
            confirmation = ""
            showPassword = false
            showConfirmation = false
            if (!viewModel.goBack()) onSignIn()
        }
    }

    BackHandler(onBack = goBack)

    val submit: () -> Unit = {
        val current = viewModel.uiState
        if (!current.loading && displayedStep == current.step && !current.resetUnconfirmed) {
            focusManager.clearFocus()
            showPassword = false
            showConfirmation = false
            when (current.step) {
                PasswordResetStep.EMAIL -> viewModel.requestCode()
                PasswordResetStep.CODE -> viewModel.verifyCode()
                PasswordResetStep.NEW_PASSWORD -> viewModel.resetPassword(password, confirmation)
                PasswordResetStep.SUCCESS -> signIn()
            }
        }
    }

    val subtitle = when (displayedStep) {
        PasswordResetStep.EMAIL -> "Forgot your password?"
        PasswordResetStep.CODE -> "Verification code"
        PasswordResetStep.NEW_PASSWORD -> "Reset your password"
        PasswordResetStep.SUCCESS -> "Password updated"
    }

    AuthTheme {
        ResetPage(subtitle = subtitle) {
            when (displayedStep) {
                PasswordResetStep.EMAIL -> {
                    ResetHelper(
                        "Enter your account's email address to receive a verification code."
                    )
                    Spacer(Modifier.height(24.dp))
                    ResetInput(
                        value = state.email,
                        onValueChange = { viewModel.updateEmail(it) },
                        placeholder = "Email address",
                        leadingIcon = ResetMail,
                        enabled = !state.loading && !transitioning,
                        autofillType = ContentType.EmailAddress,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { submit() })
                    )
                    ResetError(state.error)
                    Spacer(Modifier.height(24.dp))
                    ResetActionButton(
                        label = when {
                            state.pendingAction == PasswordResetAction.SEND_CODE -> "Sending code..."
                            state.resendSeconds > 0 -> "Continue in ${state.resendSeconds}s"
                            else -> "Continue"
                        },
                        loading = state.pendingAction == PasswordResetAction.SEND_CODE,
                        enabled = !state.loading && !transitioning && state.resendSeconds == 0,
                        onClick = submit
                    )
                    Spacer(Modifier.height(16.dp))
                    ResetTextButton(
                        label = "Back to sign in",
                        enabled = !state.loading && !transitioning,
                        onClick = signIn
                    )
                }

                PasswordResetStep.CODE -> {
                    ResetHelper("Enter the 6-digit verification code for\n${state.email}")
                    Spacer(Modifier.height(24.dp))
                    ResetInput(
                        value = state.code,
                        onValueChange = { viewModel.updateCode(it) },
                        placeholder = "Verification code",
                        enabled = !state.loading && !transitioning,
                        codeField = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.NumberPassword,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { submit() })
                    )
                    ResetError(state.error)
                    Spacer(Modifier.height(24.dp))
                    ResetActionButton(
                        label = if (state.pendingAction == PasswordResetAction.VERIFY_CODE) {
                            "Verifying..."
                        } else "Continue",
                        loading = state.pendingAction == PasswordResetAction.VERIFY_CODE,
                        enabled = !state.loading && !transitioning,
                        onClick = submit
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        onClick = {
                            if (viewModel.uiState.canResend && !transitioning) {
                                focusManager.clearFocus()
                                viewModel.resendCode()
                            }
                        },
                        enabled = state.canResend && !transitioning,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        shape = ResetPill,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = ResetBlue,
                            disabledContentColor = ResetMuted
                        )
                    ) {
                        if (state.pendingAction == PasswordResetAction.RESEND_CODE) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = ResetBlue,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            text = when {
                                state.pendingAction == PasswordResetAction.RESEND_CODE -> "Sending code..."
                                state.resendSeconds > 0 -> "Resend code in ${state.resendSeconds}s"
                                else -> "Resend code"
                            },
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )
                    }
                    ResetTextButton(
                        label = "Change email",
                        enabled = !state.loading && !transitioning,
                        onClick = goBack
                    )
                }

                PasswordResetStep.NEW_PASSWORD -> {
                    if (!state.resetUnconfirmed) {
                        ResetHelper("Use at least 6 characters for your new password.")
                        Spacer(Modifier.height(24.dp))
                    }
                    val editable = !state.loading && !transitioning && !state.resetUnconfirmed
                    ResetPasswordInput(
                        value = password,
                        onValueChange = {
                            if (editable) {
                                password = it
                                viewModel.clearError()
                            }
                        },
                        placeholder = "New password",
                        readOnly = !editable,
                        visible = showPassword,
                        toggleEnabled = !state.loading && !transitioning,
                        onToggle = { showPassword = !showPassword },
                        imeAction = ImeAction.Next
                    )
                    Spacer(Modifier.height(14.dp))
                    ResetPasswordInput(
                        value = confirmation,
                        onValueChange = {
                            if (editable) {
                                confirmation = it
                                viewModel.clearError()
                            }
                        },
                        placeholder = "Confirm new password",
                        readOnly = !editable,
                        visible = showConfirmation,
                        toggleEnabled = !state.loading && !transitioning,
                        onToggle = { showConfirmation = !showConfirmation },
                        imeAction = ImeAction.Done,
                        keyboardActions = KeyboardActions(onDone = { submit() })
                    )
                    ResetError(state.error)
                    Spacer(Modifier.height(24.dp))
                    if (state.resetUnconfirmed) {
                        ResetActionButton(
                            label = "Sign In",
                            enabled = !transitioning,
                            onClick = signIn
                        )
                    } else {
                        ResetActionButton(
                            label = if (state.pendingAction == PasswordResetAction.RESET_PASSWORD) {
                                "Updating password..."
                            } else "Reset Password",
                            loading = state.pendingAction == PasswordResetAction.RESET_PASSWORD,
                            enabled = !state.loading && !transitioning,
                            onClick = submit
                        )
                        Spacer(Modifier.height(8.dp))
                        ResetTextButton(
                            label = "Back",
                            enabled = !state.loading && !transitioning,
                            onClick = goBack
                        )
                    }
                }

                PasswordResetStep.SUCCESS -> {
                    ResetHelper("You can now sign in with your new password.")
                    Spacer(Modifier.height(24.dp))
                    ResetActionButton(
                        label = "Sign In",
                        enabled = !transitioning,
                        onClick = signIn
                    )
                }
            }
        }
    }
}

@Composable
private fun ResetPage(subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().imePadding()
        ) {
            val viewportHeight = maxHeight
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                    .heightIn(min = viewportHeight).padding(horizontal = 24.dp, vertical = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Column(modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth()) {
                    Text(
                        text = "Mongosky",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        color = Color.Black,
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 32.sp,
                        lineHeight = 40.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.8).sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = subtitle,
                        modifier = Modifier.fillMaxWidth().semantics {
                            liveRegion = LiveRegionMode.Polite
                        },
                        textAlign = TextAlign.Center,
                        color = ResetMuted,
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 18.sp,
                        lineHeight = 26.sp,
                        fontWeight = FontWeight.Normal
                    )
                    Spacer(Modifier.height(32.dp))
                    content()
                }
            }
        }
    }
}

@Composable
private fun ResetHelper(message: String) {
    Text(
        text = message,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
        color = ResetMuted,
        fontSize = 14.sp,
        lineHeight = 21.sp
    )
}

@Composable
private fun ResetError(message: String?) {
    if (message != null) {
        Spacer(Modifier.height(12.dp))
        Text(
            text = message,
            modifier = Modifier.fillMaxWidth().semantics {
                liveRegion = LiveRegionMode.Polite
            },
            color = Color(0xFFD60000),
            fontSize = 14.sp,
            lineHeight = 21.sp
        )
    }
}

@Composable
private fun ResetInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardOptions: KeyboardOptions,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    leadingIcon: ImageVector? = null,
    autofillType: ContentType? = null,
    codeField: Boolean = false,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: (@Composable () -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp).semantics {
            contentDescription = placeholder
            if (autofillType != null) contentType = autofillType
        },
        enabled = enabled,
        readOnly = readOnly,
        singleLine = true,
        placeholder = {
            Text(
                text = placeholder,
                modifier = Modifier.fillMaxWidth(),
                color = ResetMuted,
                fontSize = 16.sp,
                textAlign = if (codeField) TextAlign.Center else TextAlign.Start
            )
        },
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            color = ResetInk,
            fontSize = if (codeField) 22.sp else 16.sp,
            lineHeight = if (codeField) 28.sp else 24.sp,
            letterSpacing = if (codeField) 4.sp else 0.sp,
            textAlign = if (codeField) TextAlign.Center else TextAlign.Start,
            fontWeight = if (codeField) FontWeight.SemiBold else FontWeight.Normal
        ),
        shape = ResetPill,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        visualTransformation = visualTransformation,
        leadingIcon = leadingIcon?.let { icon ->
            { Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp)) }
        },
        trailingIcon = trailingIcon,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White,
            disabledContainerColor = Color.White,
            focusedBorderColor = ResetBlue,
            unfocusedBorderColor = ResetBorder,
            disabledBorderColor = ResetBorder,
            disabledTextColor = ResetInk,
            focusedLeadingIconColor = ResetMuted,
            unfocusedLeadingIconColor = ResetMuted,
            disabledLeadingIconColor = ResetMuted,
            cursorColor = ResetBlue
        )
    )
}

@Composable
private fun ResetPasswordInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    readOnly: Boolean,
    visible: Boolean,
    toggleEnabled: Boolean,
    onToggle: () -> Unit,
    imeAction: ImeAction,
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    ResetInput(
        value = value,
        onValueChange = onValueChange,
        placeholder = placeholder,
        readOnly = readOnly,
        leadingIcon = ResetLock,
        autofillType = ContentType.NewPassword,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = imeAction
        ),
        keyboardActions = keyboardActions,
        visualTransformation = if (visible) VisualTransformation.None
            else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = onToggle, enabled = toggleEnabled) {
                Icon(
                    imageVector = if (visible) ResetEyeOff else ResetEye,
                    contentDescription = if (visible) "Hide password" else "Show password",
                    modifier = Modifier.size(24.dp),
                    tint = ResetMuted
                )
            }
        }
    )
}

@Composable
private fun ResetActionButton(
    label: String,
    enabled: Boolean,
    loading: Boolean = false,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = ResetPill,
        colors = ButtonDefaults.buttonColors(
            containerColor = ResetRed,
            contentColor = Color.White,
            disabledContainerColor = if (loading) ResetRed else ResetRed.copy(alpha = 0.45f),
            disabledContentColor = Color.White
        ),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp)
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = Color.White,
                strokeWidth = 2.dp
            )
            Spacer(Modifier.width(10.dp))
        }
        Text(
            text = label,
            fontSize = 17.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ResetTextButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        shape = ResetPill,
        colors = ButtonDefaults.textButtonColors(
            contentColor = ResetBlue,
            disabledContentColor = ResetMuted
        )
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
    }
}

private fun resetEyeVector(hidden: Boolean): ImageVector {
    val builder = ImageVector.Builder(
        name = if (hidden) "ResetEyeOff" else "ResetEye",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    )
    builder.addPath(
        pathData = addPathNodes(
            "M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7S2 12 2 12Z " +
                "M15 12a3 3 0 1 1-6 0 3 3 0 0 1 6 0"
        ),
        fill = null,
        stroke = SolidColor(Color(0xFF98A2B3)),
        strokeLineWidth = 1.8f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    )
    if (hidden) {
        builder.addPath(
            pathData = addPathNodes("M3 3L21 21"),
            fill = null,
            stroke = SolidColor(Color(0xFF98A2B3)),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round
        )
    }
    return builder.build()
}

private val ResetEye = resetEyeVector(false)
private val ResetEyeOff = resetEyeVector(true)

// Small local vectors render without a font or image download.
private fun resetOutlineVector(name: String, path: String): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).addPath(
        pathData = addPathNodes(path),
        fill = null,
        stroke = SolidColor(ResetMuted),
        strokeLineWidth = 1.8f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ).build()

private val ResetMail = resetOutlineVector(
    "ResetMail",
    "M5 5H19A2 2 0 0 1 21 7V17A2 2 0 0 1 19 19H5A2 2 0 0 1 3 17V7A2 2 0 0 1 5 5Z M3 7L12 13L21 7"
)

private val ResetLock = resetOutlineVector(
    "ResetLock",
    "M7 10V7A5 5 0 0 1 17 7V10 M6 10H18A2 2 0 0 1 20 12V20A2 2 0 0 1 18 22H6A2 2 0 0 1 4 20V12A2 2 0 0 1 6 10Z M12 15V18 M13 14A1 1 0 1 1 11 14A1 1 0 0 1 13 14Z"
)
