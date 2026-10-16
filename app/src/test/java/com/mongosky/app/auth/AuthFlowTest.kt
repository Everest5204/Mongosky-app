package com.mongosky.app.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import com.mongosky.app.session.SavedLoginSession
import com.mongosky.app.signup.SignupDataSource
import com.mongosky.app.signup.SignupException
import com.mongosky.app.signup.SignupViewModel
import java.io.IOException
import java.util.Base64
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthFlowTest {
    private val dispatcher = StandardTestDispatcher()
    private val models = ViewModelStore()
    private var modelCount = 0
    @Before fun before() { Dispatchers.setMain(dispatcher) }
    @After fun after() { models.clear(); Dispatchers.resetMain() }
    private fun <T : ViewModel> keep(model: T): T { models.put("model" + modelCount++, model); return model }

    @Test fun coldStartShowsSavedHomeBeforeValidationCompletes() = runTest(dispatcher) {
        val account = testAccount()
        val store = MemoryLoginStore(SavedLoginSession(account.token, account))
        val pending = CompletableDeferred<AuthSession>()
        val backend = FakeLoginBackend(account).apply { onValidate = { pending.await() } }
        val model = keep(LoginViewModel(store, backend))
        runCurrent()
        assertEquals(account.userId, model.uiState.signedInUserId)
        assertFalse(model.uiState.checkingSession)
        assertFalse(model.uiState.loading)
        assertEquals(0, backend.logins)
        assertEquals(1, backend.validations)
        assertEquals(0, backend.profiles)
    }

    @Test fun offlineValidationKeepsSavedSessionAndHome() = runTest(dispatcher) {
        val account = testAccount()
        val store = MemoryLoginStore(SavedLoginSession(account.token, account))
        val backend = FakeLoginBackend(account).apply { onValidate = { throw IOException("Offline") } }
        val model = keep(LoginViewModel(store, backend))
        runCurrent()
        assertEquals(account.userId, model.uiState.signedInUserId)
        assertEquals(account.token, store.saved?.token)
        assertNull(model.uiState.error)
        assertEquals(0, store.clears)
    }

    @Test fun confirmedUnauthorizedSessionIsCleared() = runTest(dispatcher) {
        val account = testAccount()
        val store = MemoryLoginStore(SavedLoginSession(account.token, account))
        val backend = FakeLoginBackend(account).apply { onValidate = { throw SessionExpiredException() } }
        val model = keep(LoginViewModel(store, backend))
        runCurrent()
        assertNull(model.uiState.signedInName)
        assertNull(store.saved)
        assertEquals(1, store.clears)
        assertTrue(model.uiState.error.orEmpty().contains("expired"))
    }

    @Test fun expiredTokenDoesNotWaitForNetwork() = runTest(dispatcher) {
        val payload = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"exp\":1}".toByteArray())
        val account = testAccount("header.$payload.signature")
        val store = MemoryLoginStore(SavedLoginSession(account.token, account))
        val backend = FakeLoginBackend(account)
        val model = keep(LoginViewModel(store, backend))
        runCurrent()
        assertNull(model.uiState.signedInUserId)
        assertNull(store.saved)
        assertEquals(0, backend.validations)
    }

    @Test fun firstSuccessfulLoginSavesAndEntersHomeWithoutSessionGate() = runTest(dispatcher) {
        val store = MemoryLoginStore()
        val backend = FakeLoginBackend()
        val model = keep(LoginViewModel(store, backend, restoreOnStart = false))
        model.login("test@example.test", "a safe password")
        runCurrent()
        assertEquals(backend.account.userId, model.uiState.signedInUserId)
        assertEquals(backend.account.token, store.saved?.token)
        assertEquals(1, backend.logins)
        assertEquals(0, backend.validations)
        assertEquals(1, store.saves)
        assertNull(model.uiState.error)
    }

    @Test fun repeatedLoginTapsHaveOnlyOneActiveRequest() = runTest(dispatcher) {
        val pending = CompletableDeferred<AuthSession>()
        val backend = FakeLoginBackend().apply { onLogin = { pending.await() } }
        val store = MemoryLoginStore()
        val model = keep(LoginViewModel(store, backend, restoreOnStart = false))
        repeat(5) { model.login("test@example.test", "a safe password") }
        runCurrent()
        assertEquals(1, backend.logins)
        pending.complete(backend.account)
        runCurrent()
        assertEquals(1, store.saves)
        assertEquals(backend.account.userId, model.uiState.signedInUserId)
    }

    @Test fun failedPersistenceDoesNotPretendLoginSucceeded() = runTest(dispatcher) {
        val store = MemoryLoginStore().apply { failedSaves = 1 }
        val model = keep(LoginViewModel(store, FakeLoginBackend(), restoreOnStart = false))
        model.login("test@example.test", "a safe password")
        runCurrent()
        assertNull(model.uiState.signedInUserId)
        assertNull(store.saved)
        assertTrue(model.uiState.error.orEmpty().contains("save"))
        assertFalse(model.uiState.error.orEmpty().contains("internet"))
    }

    @Test fun closedLoginCannotCommitLateNetworkResult() = runTest(dispatcher) {
        val pending = CompletableDeferred<AuthSession>()
        val backend = FakeLoginBackend().apply { onLogin = { withContext(NonCancellable) { pending.await() } } }
        val store = MemoryLoginStore()
        val model = keep(LoginViewModel(store, backend, restoreOnStart = false))
        model.login("test@example.test", "a safe password")
        runCurrent()
        model.endSession()
        pending.complete(backend.account)
        runCurrent()
        assertNull(store.saved)
        assertNull(model.uiState.signedInName)
        assertEquals(0, store.saves)
    }

    @Test fun logoutPreventsOldValidationFromRestoringAccount() = runTest(dispatcher) {
        val account = testAccount()
        val pending = CompletableDeferred<AuthSession>()
        val backend = FakeLoginBackend(account).apply {
            onValidate = { withContext(NonCancellable) { pending.await() } }
        }
        val store = MemoryLoginStore(SavedLoginSession(account.token, account))
        val model = keep(LoginViewModel(store, backend))
        runCurrent()
        model.signOut()
        runCurrent()
        pending.complete(account)
        runCurrent()
        assertNull(store.saved)
        assertNull(model.uiState.signedInUserId)
        val reopened = keep(LoginViewModel(store, backend))
        runCurrent()
        assertNull(reopened.uiState.signedInUserId)
        assertFalse(reopened.uiState.checkingSession)
    }

    @Test fun legacyOpaqueSessionIsMigratedOnlyOnce() = runTest(dispatcher) {
        val account = testAccount()
        val store = MemoryLoginStore(SavedLoginSession(account.token))
        val backend = FakeLoginBackend(account)
        val first = keep(LoginViewModel(store, backend))
        runCurrent()
        assertEquals(account.userId, first.uiState.signedInUserId)
        assertEquals(account.userId, store.saved?.account?.userId)
        assertEquals(1, backend.validations)
        backend.onValidate = { CompletableDeferred<AuthSession>().await() }
        val reopened = keep(LoginViewModel(store, backend))
        runCurrent()
        assertEquals(account.userId, reopened.uiState.signedInUserId)
        assertFalse(reopened.uiState.checkingSession)
    }

    @Test fun signupUsesReturnedSessionAndNeverRequestsAnotherLogin() = runTest(dispatcher) {
        val store = MemoryLoginStore()
        val backend = FakeLoginBackend()
        val login = keep(LoginViewModel(store, backend, restoreOnStart = false))
        var signups = 0
        val signup = keep(SignupViewModel(SignupDataSource { signups++; backend.account }, login::acceptCreatedAccount))
        signup.signup(testSignup(), policyAccepted = true)
        runCurrent()
        assertEquals(1, signups)
        assertEquals(0, backend.logins)
        assertEquals(0, backend.validations)
        assertEquals(backend.account.token, store.saved?.token)
        assertEquals(backend.account.userId, login.uiState.signedInUserId)
        assertTrue(signup.uiState.sessionCommitted)
        assertNull(signup.uiState.error)
    }

    @Test fun signupStorageRetryNeverCreatesTheAccountAgain() = runTest(dispatcher) {
        val store = MemoryLoginStore().apply { failedSaves = 1 }
        val backend = FakeLoginBackend()
        val login = keep(LoginViewModel(store, backend, restoreOnStart = false))
        var signups = 0
        val signup = keep(SignupViewModel(SignupDataSource { signups++; backend.account }, login::acceptCreatedAccount))
        signup.signup(testSignup(), true)
        runCurrent()
        assertFalse(signup.uiState.outcomeUnknown)
        assertFalse(signup.uiState.sessionCommitted)
        assertNull(login.uiState.signedInUserId)
        assertNotNull(signup.uiState.error)
        signup.signup(testSignup(), true)
        runCurrent()
        assertEquals(1, signups)
        assertEquals(2, store.saves)
        assertEquals(backend.account.userId, login.uiState.signedInUserId)
        assertTrue(signup.uiState.sessionCommitted)
    }

    @Test fun signupDoesNotCompleteUntilTheReturnedSessionHasReachedHome() = runTest(dispatcher) {
        val store = MemoryLoginStore()
        val backend = FakeLoginBackend()
        val login = keep(LoginViewModel(store, backend, restoreOnStart = false))
        val gate = CompletableDeferred<Unit>()
        val signup = keep(SignupViewModel(SignupDataSource { backend.account }, { result ->
            gate.await()
            login.acceptCreatedAccount(result)
        }))
        signup.signup(testSignup(), true)
        runCurrent()
        assertTrue(signup.uiState.loading)
        assertFalse(signup.uiState.sessionCommitted)
        assertNull(login.uiState.signedInUserId)
        gate.complete(Unit)
        runCurrent()
        assertTrue(signup.uiState.sessionCommitted)
        assertEquals(backend.account.userId, login.uiState.signedInUserId)
        assertEquals(backend.account.token, store.saved?.token)
        assertEquals(0, backend.logins)
    }

    @Test fun ambiguousSignupNeverAutomaticallyRepeatsCreation() = runTest(dispatcher) {
        var signups = 0
        var commits = 0
        val signup = keep(SignupViewModel(SignupDataSource {
            signups++; throw SignupException("Unknown result", outcomeUnknown = true)
        }, { commits++ }))
        signup.signup(testSignup(), true)
        runCurrent()
        signup.signup(testSignup(), true)
        runCurrent()
        assertEquals(1, signups)
        assertEquals(0, commits)
        assertTrue(signup.uiState.outcomeUnknown)
    }

    @Test fun drawerSignupSwitchesOnlyAfterSuccessfulSessionCommit() = runTest(dispatcher) {
        val old = testAccount("old-token", "1123456789abcdef01234567")
        val fresh = testAccount("new-token", "2123456789abcdef01234567")
        val store = MemoryLoginStore(SavedLoginSession(old.token, old))
        val backend = FakeLoginBackend(old)
        val root = keep(LoginViewModel(store, backend))
        runCurrent()
        var cleanups = 0
        val drawer = keep(LoginViewModel(store, backend, restoreOnStart = false,
            commitSession = { root.commitAuthenticatedSession(it) { cleanups++ } }))
        backend.account = fresh
        val signup = keep(SignupViewModel(SignupDataSource { fresh }, drawer::acceptCreatedAccount))
        signup.signup(testSignup(), true)
        runCurrent()
        assertEquals(fresh.userId, root.uiState.signedInUserId)
        assertEquals(fresh.token, store.saved?.token)
        assertEquals(1, cleanups)
        assertEquals(0, backend.logins)
    }

    @Test fun cancelledSignupCannotSignInLate() = runTest(dispatcher) {
        val pending = CompletableDeferred<AuthSession>()
        var commits = 0
        val signup = keep(SignupViewModel(SignupDataSource {
            withContext(NonCancellable) { pending.await() }
        }, { commits++ }))
        signup.signup(testSignup(), true)
        runCurrent()
        signup.endSession()
        pending.complete(testAccount())
        runCurrent()
        assertEquals(0, commits)
        assertFalse(signup.uiState.sessionCommitted)
    }

    @Test fun signupDoubleTapDoesNotCreateDuplicateAccounts() = runTest(dispatcher) {
        val pending = CompletableDeferred<AuthSession>()
        var signups = 0
        var commits = 0
        val signup = keep(SignupViewModel(SignupDataSource { signups++; pending.await() }, { commits++ }))
        repeat(4) { signup.signup(testSignup(), true) }
        runCurrent()
        assertEquals(1, signups)
        pending.complete(testAccount())
        runCurrent()
        signup.signup(testSignup(), true)
        runCurrent()
        assertEquals(1, signups)
        assertEquals(1, commits)
    }

    @Test fun editingAnAlreadyCreatedSignupCannotSilentlyCommitAnotherIdentity() = runTest(dispatcher) {
        val store = MemoryLoginStore().apply { failedSaves = 1 }
        val login = keep(LoginViewModel(store, FakeLoginBackend(), restoreOnStart = false))
        var signups = 0
        val signup = keep(SignupViewModel(SignupDataSource { signups++; testAccount() }, login::acceptCreatedAccount))
        signup.signup(testSignup(), true)
        runCurrent()
        signup.signup(testSignup().copy(emailOrPhone = "different@example.test"), true)
        runCurrent()
        assertEquals(1, signups)
        assertNull(login.uiState.signedInUserId)
        assertNotNull(signup.uiState.error)
    }
}
