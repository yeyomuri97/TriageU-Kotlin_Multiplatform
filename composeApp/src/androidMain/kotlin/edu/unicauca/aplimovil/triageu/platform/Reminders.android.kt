package edu.unicauca.aplimovil.triageu.platform

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.russhwolf.settings.Settings
import edu.unicauca.aplimovil.triageu.R
import edu.unicauca.aplimovil.triageu.notifications.ReminderScheduler
import edu.unicauca.aplimovil.triageu.notifications.ScheduledReminder

internal object ReminderNotifications {
    const val CHANNEL_ID = "reminders"
    const val EXTRA_ID = "id"
    const val EXTRA_TITLE = "title"
    const val EXTRA_BODY = "body"

    fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    ctx.getString(R.string.reminders_channel),
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        }
    }
}

/**
 * Programa avisos con AlarmManager (inexactos: sin permiso de alarmas exactas).
 * Al abrir la app se reprograman todos, igual que `rescheduleAll` en iOS.
 */
class AndroidReminderScheduler(
    private val ctx: Context,
    private val prefs: Settings
) : ReminderScheduler {

    private val alarmManager get() = ctx.getSystemService(AlarmManager::class.java)

    /** En Android el permiso se pide desde MainActivity. */
    override suspend fun requestPermission() {}

    private fun intentFor(id: String) =
        Intent(ctx, ReminderReceiver::class.java).setData(Uri.parse("triageu://reminder/$id"))

    override fun schedule(items: List<ScheduledReminder>) {
        val am = alarmManager ?: return
        val tracked = trackedIds().toMutableSet()
        for (r in items) {
            val intent = intentFor(r.id)
                .putExtra(ReminderNotifications.EXTRA_ID, r.id)
                .putExtra(ReminderNotifications.EXTRA_TITLE, r.title)
                .putExtra(ReminderNotifications.EXTRA_BODY, r.body)
            val pi = PendingIntent.getBroadcast(
                ctx, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, r.at.toEpochMilliseconds(), pi)
            tracked += r.id
        }
        saveTracked(tracked)
    }

    override fun cancel(ids: List<String>) {
        val am = alarmManager ?: return
        for (id in ids) cancelOne(am, id)
        saveTracked(trackedIds() - ids.toSet())
    }

    override fun cancelAll() {
        val am = alarmManager ?: return
        trackedIds().forEach { cancelOne(am, it) }
        saveTracked(emptySet())
    }

    private fun cancelOne(am: AlarmManager, id: String) {
        val pi = PendingIntent.getBroadcast(
            ctx, 0, intentFor(id), PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return
        am.cancel(pi)
        pi.cancel()
    }

    private fun trackedIds(): Set<String> =
        prefs.getString(KEY_TRACKED, "").split('\n').filter { it.isNotBlank() }.toSet()

    private fun saveTracked(ids: Set<String>) = prefs.putString(KEY_TRACKED, ids.joinToString("\n"))

    private companion object {
        const val KEY_TRACKED = "scheduledReminderIds"
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ReminderNotifications.ensureChannel(context)
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val id = intent.getStringExtra(ReminderNotifications.EXTRA_ID) ?: return
        val notification = Notification.Builder(context, ReminderNotifications.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_triage)
            .setContentTitle(intent.getStringExtra(ReminderNotifications.EXTRA_TITLE))
            .setContentText(intent.getStringExtra(ReminderNotifications.EXTRA_BODY))
            .setAutoCancel(true)
            .build()
        // Si el usuario no concedió POST_NOTIFICATIONS, el sistema simplemente la descarta.
        nm.notify(id.hashCode(), notification)
    }
}
