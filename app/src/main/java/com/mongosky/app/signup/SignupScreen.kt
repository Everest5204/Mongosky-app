package com.mongosky.app.signup

import android.app.DatePickerDialog
import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mongosky.app.auth.AuthValidation
import com.mongosky.app.signup.SignupRequest
import com.mongosky.app.theme.AuthTheme
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val SignupRed = Color(0xFFFF172B)
private val SignupBlue = Color(0xFF005BFF)
private val SignupBorder = Color(0xFFE2E5EB)
private val SignupMuted = Color(0xFF6B7280)
private val SignupInk = Color(0xFF101218)
private val SignupPill = RoundedCornerShape(percent = 50)

@Composable
fun SignupScreen(
    viewModel: SignupViewModel,
    onBack: () -> Unit,
    onSignIn: () -> Unit
) {
    AuthTheme {
        val name = viewModel.uiState.createdName
        if (name != null) {
            BackHandler(onBack = onSignIn)
            SignupWhitePage(subtitle = "Account created") {
                Text(
                    text = "Welcome, $name",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    color = Color.Black,
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(Modifier.height(24.dp))
                SignupActionButton(label = "Sign in", onClick = onSignIn)
            }
        } else {
            SignupForm(viewModel, onBack, onSignIn)
        }
    }
}

@Composable
private fun SignupForm(
    viewModel: SignupViewModel,
    onBack: () -> Unit,
    onSignIn: () -> Unit
) {
    val state = viewModel.uiState
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val uriHandler = LocalUriHandler.current

    var step by rememberSaveable { mutableStateOf(1) }
    var identity by rememberSaveable { mutableStateOf("") }
    var firstName by rememberSaveable { mutableStateOf("") }
    var lastName by rememberSaveable { mutableStateOf("") }
    var gender by rememberSaveable { mutableStateOf("") }
    var birthdayEpoch by rememberSaveable { mutableStateOf<Long?>(null) }
    var accepted by rememberSaveable { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }
    var genderMenu by remember { mutableStateOf(false) }
    val birthday = birthdayEpoch?.let { LocalDate.ofEpochDay(it) }

    LaunchedEffect(password, step, state.loading) {
        if (step == 2 && password.isEmpty() && !state.loading) step = 1
    }

    BackHandler {
        if (!state.loading) {
            localError = null
            viewModel.clearError()
            if (step == 2) step = 1 else onBack()
        }
    }

    fun clearError() {
        localError = null
        viewModel.clearError()
    }

    fun openPage(path: String) {
        try {
            uriHandler.openUri("https://mongosky.com/$path")
        } catch (_: Exception) {
            localError = "Could not open this page. Please visit mongosky.com/$path."
        }
    }

    val continueToProfile: () -> Unit = {
        if (!state.loading) {
            localError = AuthValidation.credentials(identity, password)
                ?: if (!accepted) "Please accept the Privacy Policy to continue." else null
            if (localError == null) {
                focusManager.clearFocus()
                showPassword = false
                viewModel.clearError()
                step = 2
            }
        }
    }

    SignupWhitePage(subtitle = "Create a new account") {
        if (step == 1) {
            // Enable this button when native Google authentication is connected.
            OutlinedButton(
                onClick = {},
                enabled = false,
                modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp),
                shape = SignupPill,
                border = BorderStroke(1.dp, SignupBorder),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color.White,
                    contentColor = SignupInk,
                    disabledContainerColor = Color.White,
                    disabledContentColor = SignupInk
                ),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        imageVector = SignupGoogleLogo,
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

            Spacer(Modifier.height(28.dp))
            SignupInput(
                value = identity,
                onValueChange = { identity = it; clearError() },
                placeholder = "Email or Phone",
                leadingIcon = SignupMail,
                enabled = !state.loading,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                )
            )
            Spacer(Modifier.height(14.dp))
            SignupInput(
                value = password,
                onValueChange = { password = it; clearError() },
                placeholder = "Password",
                leadingIcon = SignupLock,
                enabled = !state.loading,
                visualTransformation = if (showPassword) VisualTransformation.None
                    else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { continueToProfile() }),
                trailingIcon = {
                    IconButton(
                        onClick = { showPassword = !showPassword },
                        enabled = !state.loading
                    ) {
                        Icon(
                            imageVector = if (showPassword) SignupEyeOff else SignupEye,
                            contentDescription = if (showPassword) "Hide password" else "Show password",
                            tint = SignupMuted,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            )
            Spacer(Modifier.height(18.dp))

            val policyStyles = TextLinkStyles(
                style = SpanStyle(color = SignupBlue, fontWeight = FontWeight.SemiBold)
            )
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(
                    value = accepted,
                    enabled = !state.loading,
                    role = Role.Checkbox,
                    onValueChange = { accepted = it; clearError() }
                ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // The row owns the checkbox action and its 48 dp touch target.
                Box(
                    modifier = Modifier.width(38.dp).heightIn(min = 48.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Surface(
                        modifier = Modifier.size(24.dp),
                        shape = RoundedCornerShape(6.dp),
                        color = if (accepted) SignupRed else Color.White,
                        border = BorderStroke(
                            1.5.dp,
                            if (accepted) SignupRed else SignupMuted
                        )
                    ) {
                        if (accepted) {
                            Icon(
                                imageVector = SignupCheck,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.padding(3.dp)
                            )
                        }
                    }
                }
                Text(
                    text = buildAnnotatedString {
                        append("I agree to the ")
                        withLink(
                            LinkAnnotation.Url("https://mongosky.com/privacy-policy", policyStyles) {
                                openPage("privacy-policy")
                            }
                        ) { append("Privacy Policy") }
                        append(" and have read ")
                        withLink(
                            LinkAnnotation.Url("https://mongosky.com/about", policyStyles) {
                                openPage("about")
                            }
                        ) { append("About Us") }
                        append(".")
                    },
                    modifier = Modifier.weight(1f),
                    color = SignupInk,
                    fontSize = 14.sp,
                    lineHeight = 21.sp
                )
            }
            Spacer(Modifier.height(20.dp))
        } else {
            SignupInput(
                value = firstName,
                onValueChange = { firstName = it; clearError() },
                placeholder = "First name",
                leadingIcon = SignupPerson,
                enabled = !state.loading,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )
            Spacer(Modifier.height(16.dp))
            SignupInput(
                value = lastName,
                onValueChange = { lastName = it; clearError() },
                placeholder = "Last name",
                leadingIcon = SignupPerson,
                enabled = !state.loading,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
            )
            Spacer(Modifier.height(16.dp))
            SignupChoiceButton(
                text = birthday?.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
                    ?: "Date of birth",
                leadingIcon = SignupCalendar,
                hasValue = birthday != null,
                enabled = !state.loading,
                onClick = {
                    focusManager.clearFocus()
                    val initial = birthday ?: LocalDate.now(ZoneOffset.UTC).minusYears(18)
                    val dialog = DatePickerDialog(
                        context,
                        { _, year, month, day ->
                            birthdayEpoch = LocalDate.of(year, month + 1, day).toEpochDay()
                            clearError()
                        },
                        initial.year, initial.monthValue - 1, initial.dayOfMonth
                    )
                    dialog.datePicker.minDate = LocalDate.of(1900, 1, 1)
                        .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                    dialog.datePicker.maxDate = System.currentTimeMillis()
                    dialog.show()
                }
            )
            Spacer(Modifier.height(16.dp))
            Box {
                SignupChoiceButton(
                    text = when (gender) {
                        "male" -> "Male"
                        "female" -> "Female"
                        "other" -> "Other"
                        else -> "Gender"
                    },
                    leadingIcon = SignupPerson,
                    hasValue = gender.isNotEmpty(),
                    enabled = !state.loading,
                    onClick = { focusManager.clearFocus(); genderMenu = true }
                )
                DropdownMenu(
                    expanded = genderMenu,
                    onDismissRequest = { genderMenu = false },
                    containerColor = Color.White,
                    tonalElevation = 0.dp,
                    shape = RoundedCornerShape(20.dp)
                ) {
                    listOf("male" to "Male", "female" to "Female", "other" to "Other")
                        .forEach { (value, label) ->
                            DropdownMenuItem(
                                text = { Text(label, color = Color.Black) },
                                onClick = { gender = value; genderMenu = false; clearError() }
                            )
                        }
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        val message = localError ?: state.error
        if (message != null) {
            Text(
                text = message,
                color = Color(0xFFD60000),
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(12.dp))
        }

        SignupActionButton(
            label = if (step == 1) "Continue" else if (state.loading) "Creating account..." else "Create account",
            enabled = !state.loading,
            loading = state.loading,
            onClick = {
                if (step == 1) {
                    continueToProfile()
                } else {
                    focusManager.clearFocus()
                    val date = birthday
                    if (date == null) {
                        localError = "Select your date of birth."
                    } else {
                        localError = null
                        viewModel.signup(
                            request = SignupRequest(
                                firstName, lastName, identity, password, gender,
                                date.monthValue, date.dayOfMonth, date.year
                            ),
                            policyAccepted = accepted
                        )
                    }
                }
            }
        )

        Spacer(Modifier.height(24.dp))
        Text(
            text = buildAnnotatedString {
                append("Already have an account?  ")
                withLink(
                    LinkAnnotation.Clickable(
                        tag = "sign_in",
                        styles = TextLinkStyles(
                            style = SpanStyle(color = SignupBlue, fontWeight = FontWeight.SemiBold)
                        ),
                        linkInteractionListener = { if (!state.loading) onSignIn() }
                    )
                ) { append("Sign In") }
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .padding(vertical = 12.dp),
            textAlign = TextAlign.Center,
            color = SignupMuted,
            fontSize = 14.sp,
            lineHeight = 21.sp,
            fontWeight = FontWeight.Normal
        )
    }
}

@Composable
private fun SignupWhitePage(
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().imePadding()
        ) {
            val pageHeight = maxHeight
            Column(
                modifier = Modifier.fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = pageHeight)
                    .padding(horizontal = 24.dp, vertical = 40.dp),
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
                        color = SignupMuted,
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
private fun SignupInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    enabled: Boolean,
    leadingIcon: ImageVector? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: (@Composable () -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp),
        enabled = enabled,
        singleLine = true,
        placeholder = { Text(placeholder, color = SignupMuted, fontSize = 16.sp) },
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = SignupInk),
        shape = SignupPill,
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
            focusedBorderColor = SignupBlue,
            unfocusedBorderColor = SignupBorder,
            disabledBorderColor = SignupBorder,
            focusedLeadingIconColor = SignupMuted,
            unfocusedLeadingIconColor = SignupMuted,
            disabledLeadingIconColor = SignupMuted,
            cursorColor = SignupBlue
        )
    )
}

@Composable
private fun SignupChoiceButton(
    text: String,
    enabled: Boolean,
    leadingIcon: ImageVector,
    hasValue: Boolean,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp),
        shape = SignupPill,
        border = BorderStroke(1.dp, SignupBorder),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.White,
            contentColor = SignupInk,
            disabledContainerColor = Color.White,
            disabledContentColor = SignupMuted
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp)
    ) {
        Icon(
            imageVector = leadingIcon,
            contentDescription = null,
            tint = SignupMuted,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Start,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            color = if (hasValue) SignupInk else SignupMuted
        )
        Icon(
            imageVector = SignupChevron,
            contentDescription = null,
            tint = SignupMuted,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun SignupActionButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    loading: Boolean = false
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = SignupPill,
        colors = ButtonDefaults.buttonColors(
            containerColor = SignupRed,
            contentColor = Color.White,
            disabledContainerColor = SignupRed,
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

private val SignupGoogleLogo = ImageVector.Builder(
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

private val SignupEye = signupEyeVector(false)
private val SignupEyeOff = signupEyeVector(true)

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

private val SignupMail = signupOutlineVector(
    "SignupMail",
    "M5 5H19A2 2 0 0 1 21 7V17A2 2 0 0 1 19 19H5A2 2 0 0 1 3 17V7A2 2 0 0 1 5 5Z M3 7L12 13L21 7"
)

private val SignupLock = signupOutlineVector(
    "SignupLock",
    "M7 10V7A5 5 0 0 1 17 7V10 M6 10H18A2 2 0 0 1 20 12V20A2 2 0 0 1 18 22H6A2 2 0 0 1 4 20V12A2 2 0 0 1 6 10Z M12 15V18 M13 14A1 1 0 1 1 11 14A1 1 0 0 1 13 14Z"
)

private val SignupPerson = signupOutlineVector(
    "SignupPerson",
    "M16 7A4 4 0 1 1 8 7A4 4 0 0 1 16 7Z M4 21V19A8 8 0 0 1 20 19V21"
)

private val SignupCalendar = signupOutlineVector(
    "SignupCalendar",
    "M6 5H18A2 2 0 0 1 20 7V19A2 2 0 0 1 18 21H6A2 2 0 0 1 4 19V7A2 2 0 0 1 6 5Z M8 3V7 M16 3V7 M4 11H20 M8 15H10 M14 15H16"
)

private val SignupChevron = signupOutlineVector("SignupChevron", "M6 9L12 15L18 9")
private val SignupCheck = signupOutlineVector("SignupCheck", "M5 12L10 17L19 7")

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF, widthDp = 360, heightDp = 800)
@Composable
private fun SignupPreview() {
    SignupScreen(viewModel = SignupViewModel(), onBack = {}, onSignIn = {})
}
