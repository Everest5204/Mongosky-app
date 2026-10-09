package com.mongosky.app

import android.os.Bundle
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

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
        val loginViewModel = provider[LoginViewModel::class.java]
        val signupViewModel = provider[SignupViewModel::class.java]
        val passwordResetViewModel = provider[PasswordResetViewModel::class.java]

        setContent {
            MongoskyTheme {
                val state = loginViewModel.uiState
                val userName = state.signedInName

                if (userName != null) {
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
                } else {
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
