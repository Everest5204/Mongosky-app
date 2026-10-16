package com.mongosky.app

import android.os.Bundle
import android.content.Intent
import androidx.compose.runtime.LaunchedEffect
import com.mongosky.app.notifications.NotificationSystem
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.key
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.mongosky.app.auth.AuthEntryScreen
import com.mongosky.app.auth.LoginViewModel
import com.mongosky.app.auth.TokenStore
import com.mongosky.app.navigation.MainScreen
import com.mongosky.app.passwordreset.PasswordResetViewModel
import com.mongosky.app.signup.SignupViewModel
import com.mongosky.app.theme.MongoskyTheme
import com.mongosky.app.downloadpost.PostDownloadHost
import com.mongosky.app.session.deferSessionEntry

class MainActivity : ComponentActivity() {

    private lateinit var loginViewModel: LoginViewModel

    override fun onStart() {
        super.onStart()
        if (::loginViewModel.isInitialized) loginViewModel.validateSessionInBackground()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        NotificationSystem.receiveIntent(applicationContext, intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        com.mongosky.app.language.AppLanguage.initialize(applicationContext)
        com.mongosky.app.darkmode.AppAppearance.prepareStartup(this)
        super.onCreate(savedInstanceState)
        NotificationSystem.receiveIntent(applicationContext, intent)

        val tokenStore = TokenStore(applicationContext)

        val factory = object : ViewModelProvider.Factory {

            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return when {
                    modelClass.isAssignableFrom(LoginViewModel::class.java) -> {
                        LoginViewModel(tokenStore) as T
                    }

                    modelClass.isAssignableFrom(SignupViewModel::class.java) -> {
                        SignupViewModel() as T
                    }

                    modelClass.isAssignableFrom(PasswordResetViewModel::class.java) -> {
                        PasswordResetViewModel() as T
                    }

                    else -> {
                        throw IllegalArgumentException(
                            "Unknown ViewModel: ${modelClass.name}"
                        )
                    }
                }
            }
        }

        val provider = ViewModelProvider(this, factory)
        loginViewModel = provider[LoginViewModel::class.java]
        val signupViewModel = provider[SignupViewModel::class.java]
        val passwordResetViewModel = provider[PasswordResetViewModel::class.java]

        deferSessionEntry { loginViewModel.uiState.checkingSession }

        setContent {
            com.mongosky.app.language.LanguageProvider {
                MongoskyTheme {
                    val state = loginViewModel.uiState
                    val userName = state.signedInName
                    LaunchedEffect(userName, state.checkingSession, state.restoreError) {
                        if (userName == null && !state.checkingSession && state.restoreError == null) NotificationSystem.clear(applicationContext)
                    }

                    if (userName != null) {
                        PostDownloadHost()
                        key(state.signedInUserId, state.sessionRevision) {
                            MainScreen(
                                userName = userName,
                                signingOut = state.loading,
                                error = state.error,
                                onSignOut = loginViewModel::signOut,
                                loginViewModel = loginViewModel,
                                userId = state.signedInUserId,
                                profileImageUrl = state.signedInProfileImageUrl,
                                onRefreshProfile = loginViewModel::refreshProfile
                            )
                        }
                    } else if (!state.checkingSession) {
                        AuthEntryScreen(
                            loginViewModel = loginViewModel,
                            signupViewModel = signupViewModel,
                            passwordResetViewModel = passwordResetViewModel
                        )
                    }
                }
            }
        }
    }
}
