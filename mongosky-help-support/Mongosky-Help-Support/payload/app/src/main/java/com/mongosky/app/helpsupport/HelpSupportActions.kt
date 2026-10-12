package com.mongosky.app.helpsupport

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build

internal object HelpSupportActions {
    /** ACTION_SENDTO restricts the handoff to email apps. No send or delivery is claimed. */
    fun openEmail(context: Context, email: SupportEmail): Boolean = try {
        context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse(email.mailto)).apply {
            putExtra(Intent.EXTRA_EMAIL, arrayOf(email.recipient))
            putExtra(Intent.EXTRA_SUBJECT, email.subject)
            putExtra(Intent.EXTRA_TEXT, email.body)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        true
    } catch (_: ActivityNotFoundException) { false }
      catch (_: SecurityException) { false }

    fun copyEmail(context: Context): Boolean = try {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        if (clipboard == null) false else {
            clipboard.setPrimaryClip(ClipData.newPlainText("Mongosky support", HelpSupportContent.CONTACT_EMAIL))
            true
        }
    } catch (_: SecurityException) { false }

    @Suppress("DEPRECATION")
    fun appInfo(context: Context): SupportAppInfo {
        val version = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "Unknown"
        } catch (_: android.content.pm.PackageManager.NameNotFoundException) { "Unknown" }
        return SupportAppInfo(version, Build.VERSION.RELEASE)
    }
}
