package com.mongosky.app.ai

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mongosky.app.auth.TokenStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class AiViewModel(context: Context) : ViewModel() {
    private val app=context.applicationContext
    private val tokens=TokenStore(app)
    internal val controller=AiController(viewModelScope,tokens::read)
    private val mutableTheme=MutableStateFlow(AiThemeChoice.SYSTEM)
    internal val theme=mutableTheme.asStateFlow()
    private var themeVersion=0L
    private val themeWrite=Mutex()
    init {
        viewModelScope.launch {
            val version=themeVersion
            val saved=withContext(Dispatchers.IO) {
                app.getSharedPreferences("mongosky_ai_preferences",Context.MODE_PRIVATE).getString("theme",null)
            }
            if(version==themeVersion) mutableTheme.value=AiThemeChoice.entries.firstOrNull { it.name==saved } ?: AiThemeChoice.SYSTEM
        }
    }
    internal fun setDark(dark:Boolean) {
        ++themeVersion
        mutableTheme.value=if(dark) AiThemeChoice.DARK else AiThemeChoice.LIGHT
        viewModelScope.launch(Dispatchers.IO) {
            themeWrite.withLock {
                app.getSharedPreferences("mongosky_ai_preferences",Context.MODE_PRIVATE)
                    .edit().putString("theme",mutableTheme.value.name).apply()
            }
        }
    }
    fun endSession() = controller.endSession()
    override fun onCleared() { controller.endSession();super.onCleared() }
    class Factory(private val context:Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T:ViewModel> create(modelClass:Class<T>):T {
            require(modelClass.isAssignableFrom(AiViewModel::class.java))
            return AiViewModel(context) as T
        }
    }
}
