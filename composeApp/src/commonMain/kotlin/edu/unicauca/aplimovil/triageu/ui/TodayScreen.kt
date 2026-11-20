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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import edu.unicauca.aplimovil.triageu.model.Priority
import edu.unicauca.aplimovil.triageu.model.longLabel
import edu.unicauca.aplimovil.triageu.model.nowInstant
import edu.unicauca.aplimovil.triageu.model.today
import kotlinx.coroutines.delay
import androidx.compose.runtime.collectAsState

private enum class StatusFilter(val label: String) {
    ALL("Todas"), PENDING("Pendientes"), DONE("Completadas")
}

private enum class SortMode(val label: String) {
    DUE("Fecha de entrega"), PRIORITY("Prioridad"), CREATED("Más recientes")
}

/** Borrado diferido con "Deshacer" (5 s), igual que en la versión iOS. */
private class PendingDelete(private val commitDelete: (String) -> Unit) {
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
fun TodayScreen(store: TriageStore) {
    val activities by store.activities.collectAsState()
    val subjects by store.subjects.collectAsState()

    var editing by remember { mutableStateOf<Activity?>(null) }
    var searchText by remember { mutableStateOf("") }
    var subjectFilter by remember { mutableStateOf<String?>(null) }
    var priorityFilter by remember { mutableStateOf<String?>(null) }
    var statusFilter by remember { mutableStateOf(StatusFilter.ALL) }
    var sortMode by remember { mutableStateOf(SortMode.DUE) }

    val pending = remember { PendingDelete { store.deleteActivity(it) } }
    val pendingNow by rememberUpdatedState(pending)

    LaunchedEffect(pending.id) {
        if (pending.id != null) {
            delay(5_000)
            pending.commit()
        }
    }
    DisposableEffect(Unit) { onDispose { pendingNow.commit() } }

    val visible = activities.filter { it.id != pending.id }
    val pendingCount = visible.count { !it.completed }

    val query = searchText.trim()
    val filtered = visible
        .filter { a ->
            (subjectFilter == null || a.subject == subjectFilter) &&
                (priorityFilter == null || a.priority == priorityFilter) &&
                when (statusFilter) {
                    StatusFilter.ALL -> true
                    StatusFilter.PENDING -> !a.completed
                    StatusFilter.DONE -> a.completed
                } &&
                (query.isEmpty() ||
                    a.title.contains(query, ignoreCase = true) ||
                    a.subject.contains(query, ignoreCase = true))
        }
        .let { list ->
            when (sortMode) {
                SortMode.DUE -> list.sortedBy { it.dueDate }
                SortMode.PRIORITY -> list.sortedWith(
                    compareBy<Activity> { Priority.rank(it.priority) }.thenBy { it.dueDate }
                )
                SortMode.CREATED -> list.sortedByDescending { it.createdAt }
            }
        }

    val hasActiveFilters =
        subjectFilter != null || priorityFilter != null || statusFilter != StatusFilter.ALL || searchText.isNotEmpty()

    fun colorFor(subject: String): Color =
        subjects.firstOrNull { it.name == subject }?.let { colorFromHex(it.colorHex) } ?: TriageGreen

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 30.dp, bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(greeting(), fontSize = 34.sp, fontWeight = FontWeight.Bold)
                Text(today().longLabel(), fontSize = 17.sp, color = TextSecondary)
            }

            val nowMs = nowInstant().toEpochMilliseconds()
            val overdueCount = visible.count { !it.completed && it.dueDate.toEpochMilliseconds() < nowMs }
            val (triPose, triMessage) = triMood(visible.size, pendingCount, overdueCount)
            MascotSays(triPose, triMessage)

            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(SoftGreen).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("Hoy tienes:", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TriageGreenDark)
                Text("$pendingCount actividades pendientes", fontSize = 19.sp)
                if (visible.isNotEmpty()) {
                    Text("${visible.size} actividades registradas", fontSize = 17.sp, color = TextSecondary)
                }
            }

            Text("Tu plan recomendado", fontSize = 25.sp, fontWeight = FontWeight.Bold)

            if (visible.isEmpty()) {
                EmptyState(
                    icon = { Icon(Icons.AutoMirrored.Filled.List, null, tint = TriageGreen, modifier = Modifier.padding(4.dp)) },
                    title = "Todavía no tienes actividades.",
                    subtitle = "Agrégalas usando el botón +; yo te acompaño."
                )
            } else {
                // Búsqueda
                Row(
                    Modifier.fillMaxWidth().triageCard(12.dp).padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Filled.Search, null, tint = TextSecondary)
                    Box(Modifier.weight(1f)) {
                        androidx.compose.foundation.text.BasicTextField(
                            value = searchText,
                            onValueChange = { searchText = it },
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 16.sp, color = Color(0xFF101828)),
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (searchText.isEmpty()) {
                            Text("Buscar actividad o materia", color = TextSecondary, fontSize = 16.sp)
                        }
                    }
                    if (searchText.isNotEmpty()) {
                        Icon(
                            Icons.Filled.Close, "Limpiar búsqueda", tint = TextSecondary,
                            modifier = Modifier.clickable { searchText = "" }
                        )
                    }
                }

                // Filtros
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MenuChip(subjectFilter ?: "Materia", active = subjectFilter != null) { close ->
                        DropdownMenuItem(text = { Text("Todas las materias") }, onClick = { subjectFilter = null; close() })
                        subjects.forEach { s ->
                            DropdownMenuItem(text = { Text(s.name) }, onClick = { subjectFilter = s.name; close() })
                        }
                    }
                    MenuChip(priorityFilter?.let { Priority.label(it) } ?: "Prioridad", active = priorityFilter != null) { close ->
                        DropdownMenuItem(text = { Text("Toda prioridad") }, onClick = { priorityFilter = null; close() })
                        Priority.all.forEach { p ->
                            DropdownMenuItem(text = { Text(Priority.label(p)) }, onClick = { priorityFilter = p; close() })
                        }
                    }
                    MenuChip(statusFilter.label, active = statusFilter != StatusFilter.ALL) { close ->
                        StatusFilter.entries.forEach { f ->
                            DropdownMenuItem(text = { Text(f.label) }, onClick = { statusFilter = f; close() })
                        }
                    }
                    MenuChip("Orden: ${sortMode.label}", active = false) { close ->
                        SortMode.entries.forEach { m ->
                            DropdownMenuItem(text = { Text(m.label) }, onClick = { sortMode = m; close() })
                        }
                    }
                    if (hasActiveFilters) {
                        Text(
                            "Limpiar",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = ErrorRed,
                            modifier = Modifier.clickable {
                                subjectFilter = null
                                priorityFilter = null
                                statusFilter = StatusFilter.ALL
                                searchText = ""
                            }.padding(horizontal = 6.dp)
                        )
                    }
                }

                if (filtered.isEmpty()) {
                    Text(
                        "Ninguna actividad coincide con los filtros.",
                        color = TextSecondary,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 30.dp)
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        filtered.forEach { a ->
                            ActivityCard(
                                activity = a,
                                subjectColor = colorFor(a.subject),
                                onToggle = { store.toggleCompleted(a) },
                                onEdit = { editing = a },
                                onDelete = { pending.begin(a.id) }
                            )
                        }
                    }
                }
            }
        }

        // Aviso "Actividad eliminada · Deshacer"
        AnimatedVisibility(
            visible = pending.id != null,
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
                    modifier = Modifier.clickable { pending.undo() }
                )
            }
        }
    }

    editing?.let { activity ->
        EditActivityDialog(store = store, activity = activity, onDismiss = { editing = null })
    }
}
