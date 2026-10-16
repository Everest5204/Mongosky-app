package com.mongosky.app.signup

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mongosky.app.R
import com.mongosky.app.auth.AuthValidation
import com.mongosky.app.darkmode.AppColors
import com.mongosky.app.language.appDescription
import com.mongosky.app.theme.AuthTheme

@Composable
fun SignupScreen(
    viewModel: SignupViewModel,
    onBack: () -> Unit,
    onSignIn: () -> Unit,
    showBackButton: Boolean = false,
    onCreatedBack: () -> Unit = onSignIn,
    signedInAs: String? = null
) {
    // Each native document has its own scroll state; Back restores the whole form.
    // Passwords stay only in the existing ViewModel, never in saved instance state.
    var document by rememberSaveable { mutableStateOf<String?>(null) }
    val pages = rememberSaveableStateHolder()
    AuthTheme {
        val openDocument = SignupDocument.fromKey(document)
        if (openDocument != null) {
            pages.SaveableStateProvider("document:${openDocument.key}") {
                SignupDocumentScreen(openDocument, onBack = { document = null })
            }
        } else pages.SaveableStateProvider("signup-form") {
            val name = viewModel.uiState.createdName
            if (name != null) {
                BackHandler(onBack = onCreatedBack)
                SignupWhitePage(
                    subtitle = stringResource(R.string.signup_account_created),
                    onBack = if (showBackButton) onCreatedBack else null
                ) {
                    Text(stringResource(R.string.signup_welcome, name),
                        modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                        color = AppColors.content(SignupInk), style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(24.dp))
                    signedInAs?.let { SignupAccountNotice(it) }
                    SignupActionButton(stringResource(R.string.signup_sign_in), onSignIn)
                    SignupLanguageChooser()
                }
            } else {
                SignupForm(viewModel, onBack, onSignIn, showBackButton, signedInAs,
                    onOpenDocument = { document = it.key })
            }
        }
    }
}

@Composable
private fun SignupForm(
    viewModel: SignupViewModel,
    onBack: () -> Unit,
    onSignIn: () -> Unit,
    showBackButton: Boolean,
    signedInAs: String?,
    onOpenDocument: (SignupDocument) -> Unit
) {
    val state = viewModel.uiState
    val focus = LocalFocusManager.current
    var step by rememberSaveable { mutableIntStateOf(1) }
    var identity by rememberSaveable { mutableStateOf("") }
    var firstName by rememberSaveable { mutableStateOf("") }
    var lastName by rememberSaveable { mutableStateOf("") }
    var gender by rememberSaveable { mutableStateOf("") }
    var month by rememberSaveable { mutableStateOf<Int?>(null) }
    var day by rememberSaveable { mutableStateOf<Int?>(null) }
    var year by rememberSaveable { mutableStateOf<Int?>(null) }
    var accepted by rememberSaveable { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }
    var chooseGender by remember { mutableStateOf(false) }
    val password = viewModel.passwordDraft
    val birthday = SignupBirthday(month, day, year)

    LaunchedEffect(password, step, state.loading) {
        if (step == 2 && password.isEmpty() && !state.loading) step = 1
    }
    fun clearError() { localError = null; viewModel.clearError() }
    fun updateBirthday(next: SignupBirthday) {
        month = next.month; day = next.day; year = next.year; clearError()
    }
    val goBack: () -> Unit = {
        if (!state.loading) {
            clearError()
            if (step == 2) step = 1 else onBack()
        }
    }
    BackHandler(onBack = goBack)
    val continueToProfile: () -> Unit = {
        if (!state.loading) {
            localError = AuthValidation.credentials(identity, password)
                ?: if (!accepted) "Please accept the Privacy Policy to continue." else null
            if (localError == null) {
                focus.clearFocus(); showPassword = false; viewModel.clearError(); step = 2
            }
        }
    }

    SignupWhitePage(
        subtitle = stringResource(if (step == 1) R.string.signup_create_account else R.string.signup_complete_profile),
        onBack = if (showBackButton || step == 2) goBack else null,
        backEnabled = !state.loading
    ) {
        if (step == 1) {
            SignupGoogleButton()
            Spacer(Modifier.height(20.dp))
            SignupInput(identity, { identity = it; clearError() },
                placeholder = stringResource(R.string.signup_email_phone), enabled = !state.loading,
                leadingIcon = SignupMail, tag = "signup-identity",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next))
            Spacer(Modifier.height(12.dp))
            SignupInput(password, { viewModel.updatePassword(it); clearError() },
                placeholder = stringResource(R.string.signup_password), enabled = !state.loading,
                leadingIcon = SignupLock, tag = "signup-password",
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { continueToProfile() }),
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }, enabled = !state.loading,
                        modifier = Modifier.size(48.dp)) {
                        Icon(if (showPassword) SignupEyeOff else SignupEye,
                            appDescription(if (showPassword) "Hide password" else "Show password"),
                            tint = AppColors.content(SignupMuted), modifier = Modifier.size(22.dp))
                    }
                })
            Spacer(Modifier.height(12.dp))
            SignupPolicy(accepted, enabled = !state.loading,
                onAcceptedChange = { accepted = it; clearError() },
                onOpenDocument = { focus.clearFocus(); showPassword = false; onOpenDocument(it) })
            Spacer(Modifier.height(18.dp))
        } else {
            SignupInput(firstName, { firstName = it; clearError() },
                stringResource(R.string.signup_first_name), enabled = !state.loading,
                leadingIcon = SignupPerson, tag = "signup-first-name",
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next))
            Spacer(Modifier.height(12.dp))
            SignupInput(lastName, { lastName = it; clearError() },
                stringResource(R.string.signup_last_name), enabled = !state.loading,
                leadingIcon = SignupPerson, tag = "signup-last-name",
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }))
            Spacer(Modifier.height(12.dp))
            val genders = listOf(
                SignupChoice("male", stringResource(R.string.signup_male)),
                SignupChoice("female", stringResource(R.string.signup_female)),
                SignupChoice("other", stringResource(R.string.signup_other))
            )
            val genderLabel = stringResource(R.string.signup_gender)
            SignupChoiceButton(
                text = genders.firstOrNull { it.value == gender }?.label ?: genderLabel,
                label = genderLabel, hasValue = gender.isNotEmpty(), enabled = !state.loading,
                leadingIcon = SignupPerson, tag = "signup-gender",
                onClick = { focus.clearFocus(); chooseGender = true })
            if (chooseGender) SignupChoiceDialog(genderLabel, genders, gender,
                onDismiss = { chooseGender = false }, onSelect = {
                    gender = it; chooseGender = false; clearError()
                })
            Spacer(Modifier.height(18.dp))
            SignupBirthdayFields(birthday, enabled = !state.loading, onValueChange = ::updateBirthday)
            Spacer(Modifier.height(22.dp))
        }

        SignupError(localError ?: state.error)
        SignupActionButton(
            label = stringResource(when {
                state.loading -> R.string.signup_creating_account
                step == 1 -> R.string.signup_continue
                else -> R.string.signup_create_button
            }),
            enabled = !state.loading && (step == 1 || !state.outcomeUnknown), loading = state.loading,
            onClick = {
                if (step == 1) continueToProfile() else {
                    focus.clearFocus()
                    localError = birthday.validationError()
                    if (localError == null) {
                        val date = checkNotNull(birthday.dateOrNull())
                        viewModel.signup(SignupRequest(firstName, lastName, identity, password,
                            gender, date.monthValue, date.dayOfMonth, date.year), policyAccepted = accepted)
                    }
                }
            })
        Spacer(Modifier.height(16.dp))
        signedInAs?.let { SignupAccountNotice(it) }
        SignupSignInFooter(enabled = !state.loading, onSignIn = onSignIn)
        SignupLanguageChooser(enabled = !state.loading)
    }
}
