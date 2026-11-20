package edu.unicauca.aplimovil.triageu.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import edu.unicauca.aplimovil.triageu.model.Activity
import edu.unicauca.aplimovil.triageu.model.Priority
import edu.unicauca.aplimovil.triageu.model.currentTimeZone
import edu.unicauca.aplimovil.triageu.model.daysInMonth
import edu.unicauca.aplimovil.triageu.model.firstOfMonth
import edu.unicauca.aplimovil.triageu.model.localDate
import edu.unicauca.aplimovil.triageu.model.longLabel
import edu.unicauca.aplimovil.triageu.model.monthYearLabel
import edu.unicauca.aplimovil.triageu.model.today
import edu.unicauca.aplimovil.triageu.model.weekdayInitials
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/** Horas de estudio por día a partir de las cuales se marca sobrecarga. */
private const val OVERLOAD_HOURS = 6

private fun tint(priority: String) = when (priority) {
    Priority.HIGH -> Color(0xFFBF3833)
    Priority.MEDIUM -> Color(0xFFD99A1A)
    else -> Color(0xFF73859E)
}

/** Calendario mensual con las entregas y la carga de horas de cada día. */
@Composable
fun ActivityCalendarView(
    activities: List<Activity>,
    subjectColor: (String) -> Color,
    onToggle: (Activity) -> Unit,
    onEdit: (Activity) -> Unit,
    onDelete: (Activity) -> Unit
) {
    val tz = currentTimeZone()
    var month by remember { mutableStateOf(today(tz).firstOfMonth()) }
    var selected by remember { mutableStateOf(today(tz)) }

    fun items(day: LocalDate) = activities
        .filter { it.dueDate.localDate(tz) == day }
        .sortedBy { it.dueDate }

    fun pendingHours(day: LocalDate) =
        items(day).filter { !it.completed }.sumOf { it.durationMin } / 60

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {

        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = { month = month.plus(-1, DateTimeUnit.MONTH) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Mes anterior", tint = TriageGreenDark)
            }
            Text(month.monthYearLabel(), fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = TriageGreenDark)
            IconButton(onClick = { month = month.plus(1, DateTimeUnit.MONTH) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Mes siguiente", tint = TriageGreenDark)
            }
        }

        Column(
            Modifier.fillMaxWidth().triageCard().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(Modifier.fillMaxWidth()) {
                weekdayInitials.forEach {
                    Text(
                        it,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSecondary
                    )
                }
            }

            // Semanas con lunes como primer día.
            val offset = month.dayOfWeek.ordinal
            val cells: List<LocalDate?> =
                List(offset) { null } + List(month.daysInMonth()) { month.plus(it, DateTimeUnit.DAY) }
            cells.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth()) {
                    for (i in 0 until 7) {
                        val day = week.getOrNull(i)
                        Box(Modifier.weight(1f)) {
                            if (day != null) {
                                DayCell(
                                    day = day,
                                    dayItems = items(day),
                                    isSelected = day == selected,
                                    isToday = day == today(tz),
                                    overloaded = pendingHours(day) >= OVERLOAD_HOURS,
                                    onClick = { selected = day }
                                )
                            } else {
                                Box(Modifier.height(48.dp))
                            }
                        }
                    }
                }
            }
        }

        val dayItems = items(selected)
        val hours = pendingHours(selected)

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(
                selected.longLabel().replaceFirstChar { it.uppercase() },
                fontSize = 19.sp,
                fontWeight = FontWeight.SemiBold
            )
            if (hours > 0) {
                Text(
                    "$hours h de carga",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (hours >= OVERLOAD_HOURS) ErrorRed else TriageGreenDark
                )
            }
        }

        if (hours >= OVERLOAD_HOURS) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Filled.Warning, null, tint = ErrorRed, modifier = Modifier.size(18.dp))
                Text("Día sobrecargado: considera adelantar trabajo.", fontSize = 14.sp, color = ErrorRed)
            }
        }

        if (dayItems.isEmpty()) {
            Text("Sin entregas este día.", color = TextSecondary, modifier = Modifier.padding(vertical = 8.dp))
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                dayItems.forEach { a ->
                    ActivityCard(
                        activity = a,
                        subjectColor = subjectColor(a.subject),
                        onToggle = { onToggle(a) },
                        onEdit = { onEdit(a) },
                        onDelete = { onDelete(a) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    day: LocalDate,
    dayItems: List<Activity>,
    isSelected: Boolean,
    isToday: Boolean,
    overloaded: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (overloaded) ErrorRed.copy(alpha = 0.10f) else Color.Transparent)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            Modifier.size(30.dp).clip(CircleShape).background(if (isSelected) TriageGreen else Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "${day.dayOfMonth}",
                fontSize = 16.sp,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    isSelected -> Color.White
                    isToday -> TriageGreen
                    else -> Color.Unspecified
                }
            )
        }
        Row(Modifier.height(6.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            dayItems.take(3).forEach { a ->
                Box(
                    Modifier.size(5.dp).clip(CircleShape)
                        .background(if (a.completed) TextSecondary.copy(alpha = 0.4f) else tint(a.priority))
                )
            }
        }
    }
}
