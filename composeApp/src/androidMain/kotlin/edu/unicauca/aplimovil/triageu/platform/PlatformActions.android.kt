package edu.unicauca.aplimovil.triageu.platform

import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import edu.unicauca.aplimovil.triageu.BuildConfig

actual fun appVersionLabel(): String =
    "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"

actual fun openExternalUrl(url: String) {
    val ctx = AndroidContext.app
    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

actual fun sharePlainText(text: String, suggestedFileName: String) {
    val ctx = AndroidContext.app
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/json"
        putExtra(Intent.EXTRA_SUBJECT, suggestedFileName)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    ctx.startActivity(Intent.createChooser(send, suggestedFileName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

actual fun openAppNotificationSettings() {
    val ctx = AndroidContext.app
    val intent = if (Build.VERSION.SDK_INT >= 26) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
        }
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
    }
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    ctx.startActivity(intent)
}

actual suspend fun queryNotificationsEnabled(): Boolean {
    val ctx = AndroidContext.app
    if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) return false
    if (Build.VERSION.SDK_INT < 26) return true
    val nm = ctx.getSystemService(NotificationManager::class.java) ?: return true
    val ch = nm.getNotificationChannel(ReminderNotifications.CHANNEL_ID) ?: return true
    return ch.importance != NotificationManager.IMPORTANCE_NONE
}