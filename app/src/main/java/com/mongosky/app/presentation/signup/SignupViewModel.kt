package com.mongosky.app.presentation.signup

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mongosky.app.core.AuthValidation
import com.mongosky.app.data.model.SignupRequest
import com.mongosky.app.data.remote.SignupApi
import com.mongosky.app.data.remote.SignupException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.io.IOException

data class SignupUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val createdName: String? = null
)

class SignupViewModel : ViewModel() {

    private val api = SignupApi()

    var uiState by mutableStateOf(SignupUiState())
        private set

    fun clearError() {
        if (!uiState.loading) {
            uiState = uiState.copy(error = null)
        }
    }

    fun reset() {
        if (!uiState.loading) {
            uiState = SignupUiState()
        }
    }

    fun signup(
        request: SignupRequest,
        policyAccepted: Boolean
    ) {
        if (uiState.loading || uiState.createdName != null) {
            return
        }

        if (!policyAccepted) {
            uiState = SignupUiState(
                error = "Accept the Privacy Policy to continue."
            )
            return
        }

        val validationError = AuthValidation.signup(request)

        if (validationError != null) {
            uiState = SignupUiState(
                error = validationError
            )
            return
        }

        uiState = SignupUiState(loading = true)

        viewModelScope.launch {
            try {
                val result = api.signup(request)

                val name = (
                        "${result.firstName} ${result.lastName}"
                        ).trim().ifBlank {
                        request.firstName.trim()
                    }

                uiState = SignupUiState(
                    createdName = name
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: SignupException) {
                uiState = SignupUiState(
                    error = error.message
                        ?: "Could not create account."
                )
            } catch (_: IOException) {
                uiState = SignupUiState(
                    error = "Could not confirm account creation. " +
                            "Check your internet. If you already submitted, " +
                            "try signing in before retrying."
                )
            } catch (_: Exception) {
                uiState = SignupUiState(
                    error = "Unable to create account. Please try again."
                )
            }
        }
    }
}