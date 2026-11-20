package edu.unicauca.aplimovil.triageu.notifications

import edu.unicauca.aplimovil.triageu.model.nowInstant
import platform.Foundation.NSError
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotification
import platform.UserNotifications.UNNotificationPresentationOptions
import platform.UserNotifications.UNNotificationPresentationOptionBanner
import platform.UserNotifications.UNNotificationPresentationOptionSound
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter
import platform.UserNotifications.UNUserNotificationCenterDelegateProtocol
import platform.darwin.NSObject

/** Muestra el aviso aunque la app esté abierta (igual que `NotificationService.Delegate`). */
private class ForegroundDelegate : NSObject(), UNUserNotificationCenterDelegateProtocol {
    override fun userNotificationCenter(
        center: UNUserNotificationCenter,
        willPresentNotification: UNNotification,
        withCompletionHandler: (UNNotificationPresentationOptions) -> Unit
    ) {
        withCompletionHandler(UNNotificationPresentationOptionBanner or UNNotificationPresentationOptionSound)
    }
}

// `UNUserNotificationCenter.delegate` es débil: hay que retener el delegado.
private val delegate = ForegroundDelegate()

class IosReminderScheduler : ReminderScheduler {

    private val center get() = UNUserNotificationCenter.currentNotificationCenter()

    init {
        center.delegate = delegate
    }

    override suspend fun requestPermission() {
        center.requestAuthorizationWithOptions(
            UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge
        ) { _: Boolean, _: NSError? -> }
    }

    override fun schedule(items: List<ScheduledReminder>) {
        val now = nowInstant()
        for (r in items) {
            val seconds = (r.at.toEpochMilliseconds() - now.toEpochMilliseconds()) / 1000.0
            if (seconds <= 0.0) continue

            val content = UNMutableNotificationContent().apply {
                title = r.title
                body = r.body
                sound = UNNotificationSound.defaultSound
            }
            val trigger = UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(
                timeInterval = maxOf(seconds, 1.0),
                repeats = false
            )
            val request = UNNotificationRequest.requestWithIdentifier(r.id, content, trigger)
            center.addNotificationRequest(request, withCompletionHandler = null)
        }
    }

    override fun cancel(ids: List<String>) {
        center.removePendingNotificationRequestsWithIdentifiers(ids)
    }

    override fun cancelAll() {
        center.removeAllPendingNotificationRequests()
    }
}
