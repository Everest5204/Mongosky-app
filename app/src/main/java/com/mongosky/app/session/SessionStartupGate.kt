package com.mongosky.app.session

import android.view.View
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity

/** Keep Android's launch window until routing is known; never draw an auth/checking-page flash. */
internal fun ComponentActivity.deferSessionEntry(isRestoring: () -> Boolean) {
    val content = findViewById<View>(android.R.id.content)
    content.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
        override fun onPreDraw(): Boolean {
            if (isRestoring()) return false
            if (content.viewTreeObserver.isAlive) content.viewTreeObserver.removeOnPreDrawListener(this)
            return true
        }
    })
}
