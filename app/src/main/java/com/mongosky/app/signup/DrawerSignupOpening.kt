package com.mongosky.app.signup

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Select the full-screen flow immediately; dismiss the now-hidden drawer without exposing Home. */
internal class DrawerSignupOpening(
    private val scope: CoroutineScope,
    private val closeDrawer: suspend () -> Unit,
    private val canOpen: () -> Boolean,
    private val openSignup: () -> Unit
) {
    var isOpening by mutableStateOf(false)
        private set
    private var job: Job? = null
    private var generation = 0

    fun open() {
        if (!scope.isActive || isOpening || !canOpen()) return
        isOpening = true
        try { openSignup() } catch (error: Exception) { isOpening = false; throw error }
        val attempt = generation
        val work = scope.launch(start = CoroutineStart.LAZY) {
            try {
                closeDrawer()
            } finally {
                if (attempt == generation) { isOpening = false; job = null }
            }
        }
        job = work
        work.invokeOnCompletion {
            if (attempt == generation && job === work) { isOpening = false; job = null }
        }
        work.start()
    }
    fun cancel() { generation++; job?.cancel(); job = null; isOpening = false }
}
