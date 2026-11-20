package edu.unicauca.aplimovil.triageu.platform

import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSBundle
import platform.Foundation.NSURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusEphemeral
import platform.UserNotifications.UNAuthorizationStatusProvisional
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.coroutines.resume

actual fun appVersionLabel(): String {
    val info = NSBundle.mainBundle.infoDictionary ?: return "TriageU"
    val name = info["CFBundleShortVersionString"]?.toString() ?: "?"
    val build = info["CFBundleVersion"]?.toString() ?: "?"
    return "$name ($build)"
}

actual fun openExternalUrl(url: String) {
    val nsUrl = NSURL.URLWithString(url) ?: return
    UIApplication.sharedApplication.openURL(nsUrl, options = emptyMap<Any?, Any>(), completionHandler = null)
}

actual fun sharePlainText(text: String, suggestedFileName: String) {
    val root = UIApplication.sharedApplication.keyWindow?.rootViewController ?: return
    val vc = UIActivityViewController(
        activityItems = listOf(text),
        applicationActivities = null
    )
    root.presentViewController(vc, animated = true, completion = null)
}

actual fun openAppNotificationSettings() {
    openExternalUrl(UIApplicationOpenSettingsURLString)
}

actual suspend fun queryNotificationsEnabled(): Boolean = suspendCancellableCoroutine { cont ->
    UNUserNotificationCenter.currentNotificationCenter().getNotificationSettingsWithCompletionHandler { settings ->
        if (!cont.isActive) return@getNotificationSettingsWithCompletionHandler
        val s = settings?.authorizationStatus
        val ok = s == UNAuthorizationStatusAuthorized ||
            s == UNAuthorizationStatusProvisional ||
            s == UNAuthorizationStatusEphemeral
        cont.resume(ok)
    }
}