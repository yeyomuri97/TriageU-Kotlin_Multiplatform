package edu.unicauca.aplimovil.triageu.platform

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import edu.unicauca.aplimovil.triageu.R
import edu.unicauca.aplimovil.triageu.model.label
import edu.unicauca.aplimovil.triageu.model.shortLabel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private fun prefs(ctx: Context) = ctx.getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE)

/** Guarda el resumen (mismo JSON que iOS) y pide al widget que se redibuje. */
class AndroidWidgetPublisher(private val ctx: Context) : WidgetPublisher {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun publish(json: String) {
        prefs(ctx).edit().putString(WIDGET_KEY, json).apply()
        scope.launch { TriageGlanceWidget().updateAll(ctx) }
    }

    override fun clear() {
        prefs(ctx).edit().remove(WIDGET_KEY).apply()
        scope.launch { TriageGlanceWidget().updateAll(ctx) }
    }
}

class TriageGlanceWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val items = WidgetData.parse(prefs(context).getString(WIDGET_KEY, null))
        val sync = WidgetSyncState.read(context)
        provideContent { WidgetContent(items, sync) }
    }
}

class TriageWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TriageGlanceWidget()
}

private val green = ColorProvider(Color(0xFF3F6B59))
private val secondary = ColorProvider(Color(0xFF667085))
private val primaryText = ColorProvider(Color(0xFF101828))

@androidx.compose.runtime.Composable
private fun WidgetContent(items: List<WidgetItem>, sync: WidgetSyncUiState) {
    Column(
        modifier = GlanceModifier.fillMaxSize().background(Color(0xFFF8FBF9)).padding(12.dp)
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                provider = ImageProvider(R.drawable.tri_widget),
                contentDescription = "Tri",
                modifier = GlanceModifier.size(36.dp)
            )
            Column(modifier = GlanceModifier.padding(start = 6.dp, end = 4.dp)) {
                Text(
                    text = if (items.isEmpty()) "TriageU" else "TriageU · ${items.size}",
                    style = TextStyle(color = green, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                )
                val status = when {
                    sync.syncing -> "Sincronizando…"
                    sync.lastSyncLabel != null -> sync.lastSyncLabel
                    else -> null
                }
                status?.let {
                    Text(
                        text = it,
                        style = TextStyle(color = secondary, fontSize = 11.sp),
                        maxLines = 1
                    )
                }
            }
            Text(
                text = "⟳",
                style = TextStyle(color = green, fontSize = 20.sp, fontWeight = FontWeight.Bold),
                modifier = GlanceModifier
                    .size(40.dp)
                    .clickable(actionRunCallback<SyncActionCallback>())
                    .padding(4.dp)
            )
        }
        if (items.isEmpty()) {
            Text(
                text = "Sin actividades pendientes",
                style = TextStyle(color = secondary, fontSize = 13.sp),
                modifier = GlanceModifier.fillMaxWidth().padding(top = 8.dp)
            )
        } else {
            items.take(3).forEach { item ->
                val due = Instant.fromEpochMilliseconds(item.dueDate).toLocalDateTime(TimeZone.currentSystemDefault())
                val whenText = due.date.shortLabel() + if (item.hasDueTime) " ${due.time.label()}" else ""
                Column(modifier = GlanceModifier.fillMaxWidth().padding(top = 6.dp)) {
                    Text(
                        text = item.title,
                        maxLines = 1,
                        style = TextStyle(color = primaryText, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    )
                    Text(
                        text = "${item.subject} · $whenText",
                        maxLines = 1,
                        style = TextStyle(color = secondary, fontSize = 11.sp)
                    )
                }
            }
        }
    }
}