package edu.unicauca.aplimovil.triageu.platform

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.updateAll

/** Botón ⟳ del widget: marca «Sincronizando…» y encola WorkManager. */
class SyncActionCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        WidgetSyncState.setSyncing(context, true)
        TriageGlanceWidget().updateAll(context)
        SyncScheduler.enqueueNow(context.applicationContext)
    }
}