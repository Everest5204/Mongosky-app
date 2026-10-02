package com.mongosky.app.presentation.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.mongosky.app.presentation.LoginScreen
import com.mongosky.app.presentation.LoginViewModel
import com.mongosky.app.presentation.passwordreset.PasswordResetScreen
import com.mongosky.app.presentation.passwordreset.PasswordResetViewModel
import com.mongosky.app.presentation.signup.SignupScreen
import com.mongosky.app.presentation.signup.SignupViewModel

@Composable
fun AuthEntryScreen(
    loginViewModel: LoginViewModel,
    signupViewModel: SignupViewModel,
    passwordResetViewModel: PasswordResetViewModel? = null
) {
    // Only the page name is saved. Reset codes and passwords stay out of saved state.
    var route by rememberSaveable { mutableStateOf(LOGIN_ROUTE) }
    val loginState = loginViewModel.uiState
    val resetViewModel = passwordResetViewModel

    val canShowChildPage = !loginState.loading && !loginState.checkingSession &&
        loginState.restoreError == null && loginState.signedInName == null

    fun canOpenChildPage(): Boolean {
        val current = loginViewModel.uiState
        return !current.loading && !current.checkingSession &&
            current.restoreError == null && current.signedInName == null
    }

    when {
        canShowChildPage && route == SIGNUP_ROUTE -> {
            val returnToLogin: () -> Unit = {
                if (!signupViewModel.uiState.loading) {
                    signupViewModel.reset()
                    route = LOGIN_ROUTE
                }
            }
            SignupScreen(
                viewModel = signupViewModel,
                onBack = returnToLogin,
                onSignIn = returnToLogin
            )
        }

        canShowChildPage && route == RESET_ROUTE && resetViewModel != null -> {
            PasswordResetScreen(
                viewModel = resetViewModel,
                onSignIn = {
                    if (!resetViewModel.uiState.loading) {
                        resetViewModel.reset()
                        route = LOGIN_ROUTE
                    }
                }
            )
        }

        else -> {
            val openPasswordReset: (() -> Unit)? = if (resetViewModel == null) {
                null
            } else {
                {
                    if (
                        canOpenChildPage() && !resetViewModel.uiState.loading &&
                        !signupViewModel.uiState.loading
                    ) {
                        signupViewModel.reset()
                        resetViewModel.reset()
                        route = RESET_ROUTE
                    }
                }
            }

            LoginScreen(
                viewModel = loginViewModel,
                onSignup = {
                    if (
                        canOpenChildPage() && !signupViewModel.uiState.loading &&
                        resetViewModel?.uiState?.loading != true
                    ) {
                        resetViewModel?.reset()
                        signupViewModel.reset()
                        route = SIGNUP_ROUTE
                    }
                },
                onForgotPassword = openPasswordReset
            )
        }
    }
}

private const val LOGIN_ROUTE = "login"
private const val SIGNUP_ROUTE = "signup"
private const val RESET_ROUTE = "password_reset"
