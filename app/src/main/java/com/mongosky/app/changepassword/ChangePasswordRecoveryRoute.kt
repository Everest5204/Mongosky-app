package com.mongosky.app.changepassword

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import com.mongosky.app.passwordreset.PasswordResetScreen
import com.mongosky.app.passwordreset.PasswordResetViewModel
import com.mongosky.app.auth.AuthEntryScreen
import com.mongosky.app.auth.LoginViewModel
import com.mongosky.app.signup.SignupViewModel

/** Reuse the existing native reset flow without replacing the app's signed-in session. */
@Composable
internal fun ChangePasswordRecoveryRoute(
    sessionKey: String, loginViewModel: LoginViewModel, signupViewModel: SignupViewModel,
    onBack: () -> Unit, signedInAs: String
) {
    val activity = checkNotNull(LocalContext.current.passwordActivity())
    val owner = checkNotNull(activity as? ViewModelStoreOwner)
    val holder = remember(owner, sessionKey) {
        ViewModelProvider(owner, RecoveryHolder.Factory)
            .get("MongoskyChangePasswordRecovery:$sessionKey", RecoveryHolder::class.java)
    }
    val model = remember(holder) { holder.model("recovery") }
    val authReset = remember(holder) { holder.model("auth-reset") }
    var authOpen by rememberSaveable(sessionKey) { mutableStateOf(false) }
    DisposableEffect(holder, activity, loginViewModel) {
        onDispose {
            if (!activity.isChangingConfigurations) {
                holder.clearEntry()
                loginViewModel.endSession()
            }
        }
    }
    if (authOpen) {
        AuthEntryScreen(loginViewModel, signupViewModel, authReset, signedInAs = signedInAs,
            onExit = { authOpen = false; loginViewModel.endSession(); authReset.reset() })
        return
    }
    val goBack: () -> Unit = { if (!model.uiState.loading && !model.goBack()) onBack() }
    PasswordPageTransition {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp).heightIn(min = 56.dp)) {
                IconButton(onClick = goBack, enabled = !model.uiState.loading, modifier = Modifier.size(48.dp)) {
                    Icon(ChangePasswordIcons.back, "Return to previous page", tint = Color(0xFF18181B), modifier = Modifier.size(23.dp))
                }
            }
            Box(Modifier.weight(1f)) {
                PasswordResetScreen(model, onSignIn = {
                    if (!model.uiState.loading) {
                        loginViewModel.endSession(); signupViewModel.endSession(); authReset.reset()
                        authOpen = true
                    }
                }, onBack = goBack)
            }
        }
    }
}

private class RecoveryHolder : ViewModel() {
    private val store = ViewModelStore()
    fun model(key: String) = ViewModelProvider(store, object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass == PasswordResetViewModel::class.java)
            return PasswordResetViewModel() as T
        }
    }).get(key, PasswordResetViewModel::class.java)
    fun clearEntry() = store.clear()
    override fun onCleared() { clearEntry() }
    object Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass == RecoveryHolder::class.java)
            return RecoveryHolder() as T
        }
    }
}
