package com.mongosky.app.signup

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable

/** Keep a native callback registered while busy, so Back cannot fall through to activity exit. */
@Composable
internal fun SignupFlowBackHandler(navigation: SignupNavigation, hasExit: Boolean, busy: Boolean,
    onBack: () -> Unit) {
    BackHandler(enabled = navigation.canGoBack || hasExit || busy) { if (!busy) onBack() }
}

/** Only route names and entry IDs are saved; form values and credentials are not part of navigation. */
internal enum class SignupPage { LOGIN, SIGNUP, RESET }
internal data class SignupPageEntry(val id: Int, val page: SignupPage)

@ConsistentCopyVisibility
internal data class SignupNavigation private constructor(
    val entries: List<SignupPageEntry>, private val nextId: Int
) {
    val current: SignupPageEntry get() = entries.last()
    val canGoBack: Boolean get() = entries.size > 1
    val hasSignup: Boolean get() = entries.any { it.page == SignupPage.SIGNUP }

    fun openSignup(): SignupNavigation {
        if (current.page != SignupPage.LOGIN) return this
        val existing = entries.indexOfLast { it.page == SignupPage.SIGNUP }
        return if (existing >= 0) copy(entries = entries.take(existing + 1)) else push(SignupPage.SIGNUP)
    }
    fun openSignIn(): SignupNavigation = if (current.page == SignupPage.SIGNUP) push(SignupPage.LOGIN) else this
    fun openReset(): SignupNavigation = if (current.page == SignupPage.LOGIN) push(SignupPage.RESET) else this
    fun back(busy: Boolean = false): SignupNavigation =
        if (busy || !canGoBack) this else copy(entries = entries.dropLast(1))
    private fun push(page: SignupPage) = copy(entries = entries + SignupPageEntry(nextId, page), nextId = nextId + 1)

    fun savedRoutes(): List<String> = listOf(nextId.toString()) + entries.map { "${it.id}:${it.page.name}" }
    companion object {
        fun initial(returnToSignupOnBack: Boolean = false, startWithSignup: Boolean = false): SignupNavigation {
            if (startWithSignup) return SignupNavigation(listOf(SignupPageEntry(0, SignupPage.SIGNUP)), 1)
            val root = SignupNavigation(listOf(SignupPageEntry(0, SignupPage.LOGIN)), 1)
            return if (returnToSignupOnBack) root.openSignup().openSignIn() else root
        }
        fun restored(values: List<String>): SignupNavigation? = runCatching {
            require(values.size in 2..5)
            val next = values.first().toInt()
            val entries = values.drop(1).map {
                val parts = it.split(':'); require(parts.size == 2)
                SignupPageEntry(parts[0].toInt(), SignupPage.valueOf(parts[1]))
            }
            require(entries.first().id == 0)
            require(entries.all { it.id >= 0 && it.id < next } && entries.map { it.id }.distinct().size == entries.size)
            require(entries.zipWithNext().all { (a, b) -> a.id < b.id })
            require(entries.map { it.page } in setOf(
                listOf(SignupPage.LOGIN), listOf(SignupPage.LOGIN, SignupPage.SIGNUP),
                listOf(SignupPage.LOGIN, SignupPage.RESET),
                listOf(SignupPage.LOGIN, SignupPage.SIGNUP, SignupPage.LOGIN),
                listOf(SignupPage.LOGIN, SignupPage.SIGNUP, SignupPage.LOGIN, SignupPage.RESET),
                listOf(SignupPage.SIGNUP), listOf(SignupPage.SIGNUP, SignupPage.LOGIN),
                listOf(SignupPage.SIGNUP, SignupPage.LOGIN, SignupPage.RESET)))
            SignupNavigation(entries, next)
        }.getOrNull()
    }
}
