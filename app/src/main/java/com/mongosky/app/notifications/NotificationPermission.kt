package com.mongosky.app.notifications

import com.mongosky.app.darkmode.AppColors

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager

@Composable
internal fun NotificationPermission() {
    val context = LocalContext.current; val lifecycle = LocalLifecycleOwner.current.lifecycle
    val store = remember(context) { NotificationPushStore(context) }
    var enabled by remember { mutableStateOf(NotificationSystem.enabled(context)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        enabled = NotificationSystem.enabled(context); NotificationRegistration.refresh(context)
    }
    DisposableEffect(lifecycle,context) {
        val observer = LifecycleEventObserver { _,event -> if(event==Lifecycle.Event.ON_RESUME) {
            enabled = NotificationSystem.enabled(context); NotificationRegistration.refresh(context)
        } }
        lifecycle.addObserver(observer); onDispose { lifecycle.removeObserver(observer) }
    }
    if (!enabled) Row(Modifier.padding(16.dp,12.dp).fillMaxWidth().background(AppColors.surface(Color(0xFFF1F5F9)),RoundedCornerShape(8.dp)).padding(12.dp),
        verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
        Text("Allow phone notifications for new activity.",Modifier.weight(1f),fontSize=13.sp,color=AppColors.content(NotificationColors.muted))
        TextButton(onClick={
            val activity = context.notificationActivity()
            val missingPermission = Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED
            if(missingPermission && (!store.askedPermission() || (activity!=null && ActivityCompat.shouldShowRequestPermissionRationale(activity,Manifest.permission.POST_NOTIFICATIONS)))) {
                store.notePermissionAsked(); launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,context.packageName))
        }) { Text("Enable",color=AppColors.content(NotificationColors.blue)) }
    }
}
private fun Context.notificationActivity(): Activity? = when(this) { is Activity -> this; is ContextWrapper -> baseContext.notificationActivity(); else -> null }
