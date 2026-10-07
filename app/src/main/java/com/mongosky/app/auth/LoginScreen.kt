package com.mongosky.app.auth

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
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mongosky.app.auth.TokenStore
import com.mongosky.app.theme.AuthTheme

private val LoginRed = Color(0xFFFF172B)
private val LoginBlue = Color(0xFF005BFF)
private val LoginBorder = Color(0xFFE2E5EB)
private val LoginMuted = Color(0xFF6B7280)
private val LoginInk = Color(0xFF101218)
private val LoginPill = RoundedCornerShape(percent = 50)

@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    onSignup: () -> Unit,
    onForgotPassword: (() -> Unit)? = null
) {
    val state = viewModel.uiState
    val focusManager = LocalFocusManager.current
    val autofillManager = LocalAutofillManager.current
    val context = LocalContext.current.applicationContext
    val tokenStore = remember(context) { TokenStore(context) }

    var emailOrPhone by rememberSaveable { mutableStateOf("") }
    var rememberMe by rememberSaveable { mutableStateOf(true) }
    // Passwords remain in memory and are excluded from saved state.
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }
    var hideRemoteError by remember { mutableStateOf(false) }

    LaunchedEffect(state.error) {
        hideRemoteError = false
    }

    fun edited() {
        localError = null
        hideRemoteError = true
    }

    val submit: () -> Unit = {
        val current = viewModel.uiState
        if (
            !current.loading && !current.checkingSession &&
            current.restoreError == null && current.signedInName == null
        ) {
            val identity = emailOrPhone.trim()
            localError = when {
                identity.isEmpty() -> "Enter your email or phone number."
                password.isEmpty() -> "Enter your password."
                else -> null
            }
            if (localError == null) {
                focusManager.clearFocus()
                showPassword = false
                hideRemoteError = false
                tokenStore.setRememberMeForNextLogin(rememberMe)
                viewModel.login(identity, password)
            }
        }
    }

    val openSignup: () -> Unit = {
        val current = viewModel.uiState
        if (
            !current.loading && !current.checkingSession &&
            current.restoreError == null && current.signedInName == null
        ) {
            autofillManager?.cancel()
            focusManager.clearFocus()
            showPassword = false
            onSignup()
        }
    }

    val openPasswordReset: () -> Unit = {
        val current = viewModel.uiState
        val open = onForgotPassword
        if (
            open != null && !current.loading && !current.checkingSession &&
            current.restoreError == null && current.signedInName == null
        ) {
            autofillManager?.cancel()
            focusManager.clearFocus()
            password = ""
            showPassword = false
            localError = null
            hideRemoteError = true
            open()
        }
    }

    AuthTheme {
        val name = state.signedInName
        val restoreError = state.restoreError

        LoginPage(subtitle = "Sign In to your Account") {
            when {
                state.checkingSession -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            color = LoginRed,
                            strokeWidth = 2.dp
                        )
                        Text("Checking your session...", color = LoginMuted, fontSize = 14.sp)
                    }
                }

                restoreError != null -> {
                    LoginError(restoreError)
                    state.error?.takeIf { it != restoreError }?.let {
                        Spacer(Modifier.height(12.dp))
                        LoginError(it)
                    }
                    Spacer(Modifier.height(20.dp))
                    LoginActionButton(
                        label = "Retry",
                        loading = state.loading,
                        onClick = { viewModel.restoreSession() }
                    )
                    TextButton(
                        onClick = { viewModel.signOut() },
                        enabled = !state.loading,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Back to sign in", color = LoginBlue)
                    }
                }

                name != null -> {
                    Text(
                        text = "Welcome, $name",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        color = Color.Black
                    )
                    state.error?.let {
                        Spacer(Modifier.height(16.dp))
                        LoginError(it)
                    }
                    Spacer(Modifier.height(24.dp))
                    LoginActionButton(
                        label = if (state.loading) "Signing out..." else "Sign out",
                        loading = state.loading,
                        onClick = { viewModel.signOut() }
                    )
                }

                else -> {
                    LoginGoogleButton()
                    Spacer(Modifier.height(18.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HorizontalDivider(Modifier.weight(1f), color = LoginBorder)
                        Text(
                            "or",
                            modifier = Modifier.padding(horizontal = 14.dp),
                            color = LoginMuted,
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        )
                        HorizontalDivider(Modifier.weight(1f), color = LoginBorder)
                    }
                    Spacer(Modifier.height(18.dp))

                    LoginInput(
                        value = emailOrPhone,
                        onValueChange = { emailOrPhone = it; edited() },
                        placeholder = "Email or Phone",
                        leadingIcon = LoginMail,
                        enabled = !state.loading,
                        autofillType = ContentType.Username + ContentType.EmailAddress,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next
                        )
                    )
                    Spacer(Modifier.height(14.dp))
                    LoginInput(
                        value = password,
                        onValueChange = { password = it; edited() },
                        placeholder = "Password",
                        leadingIcon = LoginLock,
                        enabled = !state.loading,
                        autofillType = ContentType.Password,
                        visualTransformation = if (showPassword) VisualTransformation.None
                            else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { submit() }),
                        trailingIcon = {
                            IconButton(
                                onClick = { showPassword = !showPassword },
                                enabled = !state.loading
                            ) {
                                Icon(
                                    imageVector = if (showPassword) LoginEyeOff else LoginEye,
                                    contentDescription = if (showPassword) "Hide password" else "Show password",
                                    modifier = Modifier.size(24.dp),
                                    tint = LoginMuted
                                )
                            }
                        }
                    )

                    val message = localError ?: state.error?.takeUnless { hideRemoteError }
                    if (message != null) {
                        Spacer(Modifier.height(12.dp))
                        LoginError(message)
                    }

                    Spacer(Modifier.height(12.dp))
                    LoginOptions(
                        rememberMe = rememberMe,
                        enabled = !state.loading,
                        onRememberMeChange = { rememberMe = it },
                        canResetPassword = onForgotPassword != null,
                        onForgotPassword = openPasswordReset
                    )
                    Spacer(Modifier.height(16.dp))

                    LoginActionButton(
                        label = if (state.loading) "Signing in..." else "Sign In",
                        loading = state.loading,
                        onClick = submit
                    )
                    Spacer(Modifier.height(24.dp))
                    Text(
                        text = buildAnnotatedString {
                            append("Do not have an account?  ")
                            withLink(
                                LinkAnnotation.Clickable(
                                    tag = "sign_up",
                                    styles = TextLinkStyles(
                                        style = SpanStyle(
                                            color = LoginBlue,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    ),
                                    linkInteractionListener = { openSignup() }
                                )
                            ) { append("Sign Up") }
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .padding(vertical = 12.dp),
                        textAlign = TextAlign.Center,
                        color = LoginMuted,
                        fontSize = 14.sp,
                        lineHeight = 21.sp,
                        fontWeight = FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
private fun LoginPage(subtitle: String, content: @Composable ColumnScope.() -> Unit) {
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
                        fontSize = 32.sp,
                        lineHeight = 40.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.8).sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = subtitle,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        color = LoginMuted,
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
private fun LoginInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    enabled: Boolean,
    leadingIcon: ImageVector,
    autofillType: ContentType,
    keyboardOptions: KeyboardOptions,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: (@Composable () -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp).semantics {
            contentType = autofillType
        },
        enabled = enabled,
        singleLine = true,
        placeholder = { Text(placeholder, color = LoginMuted, fontSize = 16.sp) },
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = LoginInk),
        shape = LoginPill,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        visualTransformation = visualTransformation,
        leadingIcon = {
            Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(22.dp))
        },
        trailingIcon = trailingIcon,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White,
            disabledContainerColor = Color.White,
            focusedBorderColor = LoginBlue,
            unfocusedBorderColor = LoginBorder,
            disabledBorderColor = LoginBorder,
            focusedLeadingIconColor = LoginMuted,
            unfocusedLeadingIconColor = LoginMuted,
            disabledLeadingIconColor = LoginMuted,
            cursorColor = LoginBlue
        )
    )
}

@Composable
private fun LoginActionButton(label: String, loading: Boolean = false, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = !loading,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = LoginPill,
        colors = ButtonDefaults.buttonColors(
            containerColor = LoginRed,
            contentColor = Color.White,
            disabledContainerColor = LoginRed,
            disabledContentColor = Color.White
        ),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp)
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
        }
        Text(
            label,
            fontSize = 17.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun LoginError(message: String) {
    Text(
        text = message,
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        color = Color(0xFFD60000),
        fontSize = 14.sp,
        lineHeight = 21.sp
    )
}

@Composable
private fun LoginGoogleButton() {
    // Native Google authentication will enable this button once connected.
    OutlinedButton(
        onClick = {},
        enabled = false,
        modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp),
        shape = LoginPill,
        border = BorderStroke(1.dp, LoginBorder),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.White,
            contentColor = LoginInk,
            disabledContainerColor = Color.White,
            disabledContentColor = LoginInk
        ),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                imageVector = LoginGoogleLogo,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(14.dp))
            Text(
                text = "Continue with Google",
                modifier = Modifier.weight(1f, fill = false),
                textAlign = TextAlign.Center,
                fontSize = 17.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

private val LoginGoogleLogo = ImageVector.Builder(
    name = "LoginGoogleLogo", defaultWidth = 20.dp, defaultHeight = 20.dp,
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

private fun loginEyeVector(hidden: Boolean): ImageVector {
    val builder = ImageVector.Builder(
        name = if (hidden) "LoginEyeOff" else "LoginEye",
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

private val LoginEye = loginEyeVector(false)
private val LoginEyeOff = loginEyeVector(true)

@Composable
private fun LoginOptions(
    rememberMe: Boolean,
    enabled: Boolean,
    onRememberMeChange: (Boolean) -> Unit,
    canResetPassword: Boolean,
    onForgotPassword: () -> Unit
) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        if (maxWidth < 296.dp * fontScale) {
            Column(modifier = Modifier.fillMaxWidth()) {
                LoginRememberOption(rememberMe, enabled, onRememberMeChange)
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    LoginForgotButton(enabled && canResetPassword, onForgotPassword)
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                LoginRememberOption(rememberMe, enabled, onRememberMeChange)
                LoginForgotButton(enabled && canResetPassword, onForgotPassword)
            }
        }
    }
}

@Composable
private fun LoginRememberOption(
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.heightIn(min = 48.dp).toggleable(
            value = checked,
            enabled = enabled,
            role = Role.Checkbox,
            onValueChange = onCheckedChange
        ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(24.dp),
            shape = RoundedCornerShape(6.dp),
            color = if (checked) LoginRed else Color.White,
            border = BorderStroke(1.5.dp, if (checked) LoginRed else LoginMuted)
        ) {
            if (checked) {
                Icon(
                    imageVector = LoginCheck,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.padding(3.dp)
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text("Remember me", color = LoginInk, fontSize = 14.sp, lineHeight = 21.sp)
    }
}

@Composable
private fun LoginForgotButton(enabled: Boolean, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        shape = LoginPill,
        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 12.dp),
        colors = ButtonDefaults.textButtonColors(
            contentColor = LoginBlue,
            disabledContentColor = LoginMuted
        )
    ) {
        Text("Forgotten password?", fontSize = 14.sp, lineHeight = 21.sp)
    }
}

private fun loginOutlineVector(name: String, path: String): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).addPath(
        pathData = addPathNodes(path),
        fill = null,
        stroke = SolidColor(LoginMuted),
        strokeLineWidth = 1.8f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ).build()

private val LoginMail = loginOutlineVector(
    "LoginMail",
    "M5 5H19A2 2 0 0 1 21 7V17A2 2 0 0 1 19 19H5A2 2 0 0 1 3 17V7A2 2 0 0 1 5 5Z M3 7L12 13L21 7"
)

private val LoginLock = loginOutlineVector(
    "LoginLock",
    "M7 10V7A5 5 0 0 1 17 7V10 M6 10H18A2 2 0 0 1 20 12V20A2 2 0 0 1 18 22H6A2 2 0 0 1 4 20V12A2 2 0 0 1 6 10Z M12 15V18 M13 14A1 1 0 1 1 11 14A1 1 0 0 1 13 14Z"
)

private val LoginCheck = loginOutlineVector("LoginCheck", "M5 12L10 17L19 7")
