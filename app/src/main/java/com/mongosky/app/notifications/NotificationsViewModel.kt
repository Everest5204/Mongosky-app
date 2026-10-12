package com.mongosky.app.notifications

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mongosky.app.auth.TokenStore

internal class NotificationsViewModel(private val app: Context, private val tokenStore: TokenStore) : ViewModel() {
    val controller = NotificationsController(viewModelScope, NotificationApi(), NotificationSse(), tokenStore::read,
        onAuthenticated = { owner, token ->
            NotificationSystem.activate(app, owner, token)
            NotificationRegistration.refresh(app)
        }, onExpired = { NotificationSystem.clear(app) })
    private var owner = ""
    fun startSession(id:String) { owner=id; controller.startSession(id) }
    fun retry() { controller.startSession(owner); controller.refresh() }
    fun endSession() { controller.endSession(); NotificationSystem.clear(app) }
    override fun onCleared() { controller.endSession() }
    class Factory(context:Context) : ViewModelProvider.Factory {
        private val app = context.applicationContext
        @Suppress("UNCHECKED_CAST") override fun <T:ViewModel> create(modelClass:Class<T>):T {
            require(modelClass.isAssignableFrom(NotificationsViewModel::class.java))
            return NotificationsViewModel(app,TokenStore(app)) as T
        }
    }
}
