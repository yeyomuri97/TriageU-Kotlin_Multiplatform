@file:OptIn(ExperimentalMaterial3Api::class)

package edu.unicauca.aplimovil.triageu.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import edu.unicauca.aplimovil.triageu.TriageStore
import edu.unicauca.aplimovil.triageu.model.Activity
import edu.unicauca.aplimovil.triageu.model.Subject
import edu.unicauca.aplimovil.triageu.model.currentTimeZone
import edu.unicauca.aplimovil.triageu.model.endOfWeek
import edu.unicauca.aplimovil.triageu.model.localDate
import edu.unicauca.aplimovil.triageu.model.longLabel
import edu.unicauca.aplimovil.triageu.model.shortLabel
import edu.unicauca.aplimovil.triageu.model.startOfWeek
import edu.unicauca.aplimovil.triageu.model.today
import kotlinx.coroutines.delay
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

private enum class AgendaView { TIMELINE, CALENDAR }

private data class WeekBucket(val title: String, val from: LocalDate, val to: LocalDate)

/** Ventana de agrupación: esta semana, la siguiente y todo lo demás. */
private fun weekBuckets(now: LocalDate): List<WeekBucket> {
    val thisStart = now.startOfWeek()
    val thisEnd = now.endOfWeek()
    val nextStart = thisStart.plus(7, DateTimeUnit.DAY)
    val nextEnd = nextStart.plus(6, DateTimeUnit.DAY)
    return listOf(
        WeekBucket("Esta semana", thisStart, thisEnd),
        WeekBucket("Próxima semana", nextStart, nextEnd),
        WeekBucket("Más adelante", nextStart.plus(7, DateTimeUnit.DAY), LocalDate(9999, 12, 31))
    )
}

private fun Activity.dueDay(tz: kotlinx.datetime.TimeZone = currentTimeZone()) = dueDate.localDate(tz)

private fun inRange(day: LocalDate, from: LocalDate, to: LocalDate) = day >= from && day <= to

/** Días de esta semana con 3 o más entregas pendientes. */
private fun heavyDaysThisWeek(pending: List<Activity>, now: LocalDate): List<Pair<LocalDate, Int>> {
    val start = now.startOfWeek()
    val end = now.endOfWeek()
    return pending
        .map { it.dueDay() }
        .filter { inRange(it, start, end) }
        .groupingBy { it }
        .eachCount()
        .filter { it.value >= 3 }
        .toList()
        .sortedBy { it.first }
}

private class PendingDeleteAgenda(private val commitDelete: (String) -> Unit) {
    var id by mutableStateOf<String?>(null)
        private set

    fun begin(newId: String) {
        commit()
        id = newId
    }

    fun commit() {
        id?.let(commitDelete)
        id = null
    }

    fun undo() {
        id = null
    }
}

@Composable
fun AgendaScreen(store: TriageStore) {
    val activities by store.activities.collectAsState()
    val subjects by store.subjects.collectAsState()
    val tz = currentTimeZone()
    val now = today(tz)

    var view by remember { mutableStateOf(AgendaView.TIMELINE) }
    var subjectFilter by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<Activity?>(null) }

    val pendingDelete = remember { PendingDeleteAgenda { store.deleteActivity(it) } }
    val pendingDeleteNow by rememberUpdatedState(pendingDelete)
    LaunchedEffect(pendingDelete.id) {
        if (pendingDelete.id != null) {
            delay(5_000)
            pendingDelete.commit()
        }
    }
    DisposableEffect(Unit) { onDispose { pendingDeleteNow.commit() } }

    val visible = activities.filter { it.id != pendingDelete.id && !it.completed }
    val filtered = visible
        .filter { subjectFilter == null || it.subject == subjectFilter }
        .sortedBy { it.dueDate }

    fun colorFor(name: String): Color =
        subjects.firstOrNull { it.name == name }?.let { colorFromHex(it.colorHex) } ?: TriageGreen

    val heavy = heavyDaysThisWeek(visible, now)

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 30.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            ScreenTitle("Agenda")
            Text("Lo que se te viene encima", fontSize = 17.sp, color = TextSecondary)

            SubjectStrip(subjects, subjectFilter, visible, ::colorFor) { subjectFilter = it }

            if (heavy.isNotEmpty()) {
                val (day, count) = heavy.maxByOrNull { it.second }!!
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFFFFF4E5)).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Warning, null, tint = Color(0xFFB45309), modifier = Modifier.size(22.dp))
                        Text("Semana con carga alta", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF92400E))
                    }
                    Text(
                        "El ${day.longLabel()} tienes $count entregas. Intenta adelantar algo antes.",
                        fontSize = 15.sp,
                        color = Color(0xFF78350F)
                    )
                    MascotSays(TriPose.THINKING, "Podemos repartir el trabajo: empieza por lo más urgente.", size = 56.dp)
                }
            }

            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                AgendaView.entries.forEachIndexed { index, v ->
                    val label = if (v == AgendaView.TIMELINE) "Por semana" else "Calendario"
                    SegmentedButton(
                        selected = view == v,
                        onClick = { view = v },
                        shape = SegmentedButtonDefaults.itemShape(index, AgendaView.entries.size)
                    ) { Text(label) }
                }
            }

            if (view == AgendaView.CALENDAR) {
                ActivityCalendarView(
                    activities = filtered.ifEmpty { visible },
                    subjectColor = ::colorFor,
                    onToggle = { store.toggleCompleted(it) },
                    onEdit = { editing = it },
                    onDelete = { pendingDelete.begin(it.id) }
                )
            } else if (filtered.isEmpty()) {
                EmptyState(
                    icon = { Mascot(TriPose.IDLE, 100.dp) },
                    title = if (subjectFilter != null) "Nada pendiente en esta materia." else "No tienes entregas pendientes.",
                    subtitle = "Cuando agregues actividades con fecha, aparecerán aquí por semana.",
                    topPadding = 24.dp
                )
            } else {
                val overdue = filtered.filter { it.dueDay(tz) < now.startOfWeek() }
                if (overdue.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Atrasadas", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = ErrorRed)
                        overdue.forEach { a -> AgendaRow(a, colorFor(a.subject)) { editing = a } }
                    }
                }
                val buckets = weekBuckets(now)
                buckets.forEach { bucket ->
                    val section = filtered.filter {
                        val d = it.dueDay(tz)
                        inRange(d, bucket.from, bucket.to)
                    }
                    if (section.isEmpty()) return@forEach
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(bucket.title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TriageGreenDark)
                        section.forEach { a ->
                            AgendaRow(a, colorFor(a.subject)) { editing = a }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = pendingDelete.id != null,
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Row(
                Modifier
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Black.copy(alpha = 0.85f))
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Actividad eliminada", color = Color.White)
                Text(
                    "Deshacer",
                    color = Color(0xFF9FE0C0),
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { pendingDelete.undo() }
                )
            }
        }
    }

    editing?.let { activity ->
        EditActivityDialog(store = store, activity = activity, onDismiss = { editing = null })
    }
}

@Composable
private fun SubjectStrip(
    subjects: List<Subject>,
    selected: String?,
    pending: List<Activity>,
    colorFor: (String) -> Color,
    onSelect: (String?) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SubjectChip("Todas", selected == null, TriageGreen, pending.size) { onSelect(null) }
        subjects.forEach { s ->
            val n = pending.count { it.subject == s.name }
            SubjectChip(s.name, selected == s.name, colorFor(s.name), n) { onSelect(s.name) }
        }
    }
}

@Composable
private fun SubjectChip(label: String, active: Boolean, dot: Color, count: Int, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (active) SoftGreen else Color.White)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
        Text(label, fontSize = 14.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal, color = TriageGreenDark)
        if (count > 0) {
            Text("$count", fontSize = 12.sp, color = TextSecondary)
        }
    }
}

@Composable
private fun AgendaRow(activity: Activity, subjectColor: Color, onOpen: () -> Unit) {
    val tz = currentTimeZone()
    val day = activity.dueDate.localDate(tz)
    Row(
        Modifier
            .fillMaxWidth()
            .triageCard(14.dp)
            .clickable(onClick = onOpen)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            Modifier.clip(RoundedCornerShape(10.dp)).background(SoftGreen).padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("${day.dayOfMonth}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TriageGreenDark)
            Text(day.shortLabel().substringAfter(' ').take(3), fontSize = 12.sp, color = TextSecondary)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(activity.title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(subjectColor))
                Text(activity.subject, fontSize = 14.sp, color = TextSecondary)
                Text("· ${activity.type}", fontSize = 14.sp, color = TextSecondary)
            }
        }
        Text(
            activity.priority.uppercase(),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = when (activity.priority) {
                edu.unicauca.aplimovil.triageu.model.Priority.HIGH -> ErrorRed
                edu.unicauca.aplimovil.triageu.model.Priority.MEDIUM -> Color(0xFFB45309)
                else -> TextSecondary
            }
        )
    }
}