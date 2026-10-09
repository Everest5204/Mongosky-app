package com.mongosky.app.signup

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import com.mongosky.app.auth.AuthEntryScreen
import com.mongosky.app.auth.LoginViewModel
import com.mongosky.app.passwordreset.PasswordResetViewModel

/** One native auth history above the signed-in shell; browsing it never signs the user out. */
@Composable
internal fun DrawerSignupRoute(
    viewModel: SignupViewModel,
    loginViewModel: LoginViewModel,
    userName: String,
    onBack: () -> Unit
) {
    val activity = LocalContext.current.signupActivity()
    val owner = checkNotNull(activity as? ViewModelStoreOwner)
    val resetViewModel = remember(owner) {
        ViewModelProvider(owner).get("MongoskyDrawerPasswordReset", PasswordResetViewModel::class.java)
    }
    DisposableEffect(loginViewModel, resetViewModel, activity) {
        onDispose {
            if (activity?.isChangingConfigurations != true) {
                loginViewModel.endSession()
                resetViewModel.reset()
            }
        }
    }
    AuthEntryScreen(loginViewModel, viewModel, resetViewModel,
        startWithSignup = true, onExit = onBack, signedInAs = userName)
}

internal fun Context.signupActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        val base = current.baseContext
        if (base === current) break
        current = base
    }
    return current as? Activity
}
