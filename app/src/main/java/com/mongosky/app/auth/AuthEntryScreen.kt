package com.mongosky.app.auth

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import com.mongosky.app.passwordreset.PasswordResetScreen
import com.mongosky.app.passwordreset.PasswordResetViewModel
import com.mongosky.app.signup.SignupBackBar
import com.mongosky.app.signup.SignupFlowBackHandler
import com.mongosky.app.signup.SignupNavigation
import com.mongosky.app.signup.SignupPage
import com.mongosky.app.signup.SignupPageTransition
import com.mongosky.app.signup.SignupScreen
import com.mongosky.app.signup.SignupViewModel
import com.mongosky.app.signup.signupActivity

@Composable
fun AuthEntryScreen(
    loginViewModel: LoginViewModel,
    signupViewModel: SignupViewModel,
    passwordResetViewModel: PasswordResetViewModel? = null,
    returnToSignupOnBack: Boolean = false,
    startWithSignup: Boolean = false,
    onExit: (() -> Unit)? = null,
    signedInAs: String? = null
) {
    var navigation by rememberSaveable(returnToSignupOnBack, startWithSignup, stateSaver = SignupNavigationSaver) {
        mutableStateOf(SignupNavigation.initial(returnToSignupOnBack, startWithSignup))
    }
    val pageState = rememberSaveableStateHolder()
    var forward by remember { mutableStateOf(true) }
    val focusManager = LocalFocusManager.current
    val activity = LocalContext.current.signupActivity()
    val loginState = loginViewModel.uiState
    val resetViewModel = passwordResetViewModel

    DisposableEffect(signupViewModel, activity) {
        onDispose { if (activity?.isChangingConfigurations != true) signupViewModel.endSession() }
    }

    fun busy(): Boolean {
        val state = loginViewModel.uiState
        return state.loading || state.checkingSession || signupViewModel.uiState.loading ||
            resetViewModel?.uiState?.loading == true
    }
    fun canOpenChildPage(): Boolean {
        val state = loginViewModel.uiState
        return !busy() && state.restoreError == null && state.signedInName == null
    }
    fun move(next: SignupNavigation) {
        if (next == navigation) return
        focusManager.clearFocus()
        val removed = navigation.entries.filter { old -> next.entries.none { it.id == old.id } }
        forward = next.entries.size > navigation.entries.size
        navigation = next
        removed.forEach { pageState.removeState(it.id) }
    }
    fun goBack() {
        if (busy()) return
        val leaving = navigation.current.page
        if (!navigation.canGoBack) {
            onExit?.invoke()
            return
        }
        move(navigation.back())
        if (leaving == SignupPage.SIGNUP) signupViewModel.reset()
        if (leaving == SignupPage.RESET) resetViewModel?.reset()
    }

    val canShowChildPage = !loginState.loading && !loginState.checkingSession &&
        loginState.restoreError == null && loginState.signedInName == null
    val entry = navigation.current
    val page = if (canShowChildPage && (entry.page != SignupPage.RESET || resetViewModel != null))
        entry.page else SignupPage.LOGIN
    val backEnabled = !busy()
    // Consume Back during requests too, so the activity cannot exit through a disabled form.
    SignupFlowBackHandler(navigation, hasExit = onExit != null, busy = busy()) {
        goBack()
    }

    key(entry.id, page) {
        SignupPageTransition(forward) {
            pageState.SaveableStateProvider(entry.id) {
                when (page) {
                    SignupPage.SIGNUP -> SignupScreen(
                        viewModel = signupViewModel,
                        onBack = ::goBack,
                        onSignIn = { if (canOpenChildPage()) move(navigation.openSignIn()) },
                        showBackButton = true,
                        onCreatedBack = ::goBack,
                        signedInAs = signedInAs
                    )
                    SignupPage.RESET -> {
                        if (resetViewModel != null) Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                            SignupBackBar(::goBack, enabled = backEnabled)
                            Box(Modifier.weight(1f)) {
                                PasswordResetScreen(viewModel = resetViewModel,
                                    onSignIn = ::goBack, onBack = ::goBack)
                            }
                        }
                    }
                    SignupPage.LOGIN -> Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                        if (navigation.canGoBack || onExit != null) SignupBackBar(::goBack, enabled = backEnabled)
                        Box(Modifier.weight(1f)) {
                            LoginScreen(
                                viewModel = loginViewModel,
                                onSignup = {
                                    if (canOpenChildPage()) {
                                        if (!navigation.hasSignup) signupViewModel.reset()
                                        resetViewModel?.reset()
                                        move(navigation.openSignup())
                                    }
                                },
                                onForgotPassword = if (resetViewModel == null) null else {
                                    {
                                        if (canOpenChildPage()) {
                                            resetViewModel.reset()
                                            move(navigation.openReset())
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

private val SignupNavigationSaver = listSaver<SignupNavigation, String>(
    save = { it.savedRoutes() }, restore = { SignupNavigation.restored(it) }
)
