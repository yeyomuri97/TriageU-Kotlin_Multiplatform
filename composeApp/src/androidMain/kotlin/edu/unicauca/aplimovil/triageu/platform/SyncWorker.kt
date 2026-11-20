package edu.unicauca.aplimovil.triageu.platform

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import edu.unicauca.aplimovil.triageu.TriageApplication
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** Sincroniza con Supabase en segundo plano y actualiza el widget. */
class SyncWorker(
    private val appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): ListenableWorker.Result {
        val app = appContext.applicationContext as? TriageApplication
            ?: return ListenableWorker.Result.failure()

        val store = app.store
        if (!store.auth.isSignedIn) {
            WidgetSyncState.clearSyncing(appContext)
            TriageGlanceWidget().updateAll(appContext)
            return ListenableWorker.Result.failure()
        }

        val ok = try {
            store.syncNow()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }

        appContext.getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(WIDGET_KEY, store.snapshotForWidget())
            .apply()

        val updated = if (ok) formatUpdatedLabel() else WidgetSyncState.read(appContext).lastSyncLabel
        WidgetSyncState.write(appContext, syncing = false, lastSyncLabel = updated)
        TriageGlanceWidget().updateAll(appContext)

        return when {
            ok -> ListenableWorker.Result.success()
            runAttemptCount >= MAX_ATTEMPTS - 1 -> ListenableWorker.Result.failure()
            else -> ListenableWorker.Result.retry()
        }
    }

    private fun formatUpdatedLabel(): String {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val hm = "${now.hour.toString().padStart(2, '0')}:${now.minute.toString().padStart(2, '0')}"
        return "Actualizado $hm"
    }

    private companion object {
        /** Máximo de intentos (0..2 = 3 ejecuciones) antes de fallar. */
        const val MAX_ATTEMPTS = 3
    }
}