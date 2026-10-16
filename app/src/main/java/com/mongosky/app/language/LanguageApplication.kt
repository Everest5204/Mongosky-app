package com.mongosky.app.language

import android.app.Application

/** Restore the same preference for activities and background notification services. */
class LanguageApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLanguage.initialize(this)
    }
}
