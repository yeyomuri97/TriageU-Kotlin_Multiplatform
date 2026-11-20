package edu.unicauca.aplimovil.triageu.platform

import android.content.Context

internal const val WIDGET_PREFS = "triageu_widget"
internal const val WIDGET_KEY = "widgetItems"
private const val KEY_SYNCING = "widget_syncing"
private const val KEY_LAST_SYNC = "widget_last_sync_label"

data class WidgetSyncUiState(val syncing: Boolean, val lastSyncLabel: String?)

object WidgetSyncState {

    fun read(context: Context): WidgetSyncUiState {
        val p = context.getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE)
        return WidgetSyncUiState(
            syncing = p.getBoolean(KEY_SYNCING, false),
            lastSyncLabel = p.getString(KEY_LAST_SYNC, null)
        )
    }

    fun write(context: Context, syncing: Boolean, lastSyncLabel: String?) {
        context.getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_SYNCING, syncing)
            .putString(KEY_LAST_SYNC, lastSyncLabel)
            .apply()
    }

    fun setSyncing(context: Context, syncing: Boolean) {
        val prev = read(context)
        write(context, syncing, prev.lastSyncLabel)
    }

    fun clearSyncing(context: Context) {
        val prev = read(context)
        write(context, syncing = false, lastSyncLabel = prev.lastSyncLabel)
    }
}