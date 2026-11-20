@file:OptIn(ExperimentalMaterial3Api::class)

package edu.unicauca.aplimovil.triageu.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import edu.unicauca.aplimovil.triageu.model.Activity
import edu.unicauca.aplimovil.triageu.model.Priority
import edu.unicauca.aplimovil.triageu.model.label
import edu.unicauca.aplimovil.triageu.model.localDate
import edu.unicauca.aplimovil.triageu.model.localTime
import edu.unicauca.aplimovil.triageu.model.shortLabel
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

/** Tarjeta blanca con borde verde claro (el patrón repetido en toda la app iOS). */
fun Modifier.triageCard(radius: Dp = 18.dp, border: Dp = 1.dp): Modifier {
    val shape = RoundedCornerShape(radius)
    return this
        .clip(shape)
        .background(Color.White)
        .border(BorderStroke(border, BorderGreen), shape)
}

@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, fontSize = 34.sp, fontWeight = FontWeight.Bold, modifier = modifier)
}

@Composable
fun InputTitle(text: String) {
    Text(text, fontSize = 17.sp, modifier = Modifier.fillMaxWidth())
}

// ---------- Campos ----------

@Composable
fun TriageTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
    password: Boolean = false,
    singleLine: Boolean = true
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(placeholder, color = TextSecondary) },
        singleLine = singleLine,
        shape = RoundedCornerShape(15.dp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = TriageGreen,
            unfocusedBorderColor = BorderGreen,
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White,
            cursorColor = TriageGreen
        )
    )
}

@Composable
fun FieldBox(onClick: () -> Unit, content: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(15.dp))
            .background(Color.White)
            .border(BorderStroke(1.5.dp, BorderGreen), RoundedCornerShape(15.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        content()
    }
}

/** Equivalente del `Menu { ... } label: { SelectionBox }` de SwiftUI. */
@Composable
fun <T> DropdownField(
    text: String,
    options: List<T>,
    label: (T) -> String,
    onSelect: (T) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        FieldBox(onClick = { open = true }) {
            Text(text, fontSize = 17.sp)
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = TextSecondary)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(label(option)) },
                    onClick = {
                        onSelect(option)
                        open = false
                    }
                )
            }
        }
    }
}

@Composable
fun DateField(value: LocalDate, onChange: (LocalDate) -> Unit) {
    var open by remember { mutableStateOf(false) }
    FieldBox(onClick = { open = true }) {
        Text(value.shortLabel(), fontSize = 17.sp)
        Icon(Icons.Filled.CalendarMonth, contentDescription = null, tint = TextSecondary)
    }
    if (open) {
        // El DatePicker trabaja en UTC: se usa la medianoche UTC de la fecha.
        val state = rememberDatePickerState(
            initialSelectedDateMillis = value.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        onChange(Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.UTC).date)
                    }
                    open = false
                }) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancelar") } }
        ) {
            DatePicker(state = state)
        }
    }
}

@Composable
fun TimeField(value: LocalTime, onChange: (LocalTime) -> Unit) {
    var open by remember { mutableStateOf(false) }
    FieldBox(onClick = { open = true }) {
        Text(value.label(), fontSize = 17.sp)
    }
    if (open) {
        val state = rememberTimePickerState(
            initialHour = value.hour,
            initialMinute = value.minute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    onChange(LocalTime(state.hour, state.minute))
                    open = false
                }) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancelar") } },
            text = { TimePicker(state = state) }
        )
    }
}

/** Selector "Muy poco 1 2 3 4 5 Muy bien". */
@Composable
fun PreparationSelector(value: Int, onChange: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Muy\npoco", fontSize = 11.sp, color = TextSecondary)
        (1..5).forEach { n ->
            val selected = value == n
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (selected) SoftGreen else Color.White)
                    .border(
                        BorderStroke(if (selected) 2.dp else 1.dp, if (selected) TriageGreen else BorderGreen),
                        CircleShape
                    )
                    .clickable { onChange(n) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "$n",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (selected) TriageGreenDark else Color.Unspecified
                )
            }
        }
        Text("Muy\nbien", fontSize = 11.sp, color = TextSecondary)
    }
}

/** Stepper "- N horas +". */
@Composable
fun HoursStepper(value: Int, onChange: (Int) -> Unit, range: IntRange = 1..12, suffix: String = "horas") {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .triageCard(15.dp, 1.5.dp)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = { if (value > range.first) onChange(value - 1) }) {
            Text("−", fontSize = 24.sp, color = TriageGreenDark)
        }
        Text("$value $suffix", fontSize = 18.sp)
        IconButton(onClick = { if (value < range.last) onChange(value + 1) }) {
            Text("+", fontSize = 24.sp, color = TriageGreenDark)
        }
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, enabled: Boolean = true, content: (@Composable () -> Unit)? = null) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(15.dp))
            .background(if (enabled) TriageGreen else TriageGreen.copy(alpha = 0.5f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 17.dp),
        contentAlignment = Alignment.Center
    ) {
        if (content != null) content()
        else Text(text, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ---------- Chips con menú (filtros) ----------

@Composable
fun MenuChip(
    label: String,
    active: Boolean,
    showArrow: Boolean = true,
    content: @Composable (close: () -> Unit) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(if (active) TriageGreen else SoftGreen)
                .clickable { open = true }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = if (active) Color.White else TriageGreenDark
            )
            if (showArrow) {
                Spacer(Modifier.width(4.dp))
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (active) Color.White else TriageGreenDark
                )
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            content { open = false }
        }
    }
}

// ---------- Tarjeta de actividad ----------

private fun priorityColor(p: String) = when (p) {
    Priority.HIGH -> Color(0xFF9F3A38)
    Priority.MEDIUM -> Color(0xFF8A5A00)
    else -> Color(0xFF475569)
}

private fun priorityBackground(p: String) = when (p) {
    Priority.HIGH -> Color(0xFFFDEDEC)
    Priority.MEDIUM -> Color(0xFFFFF4D6)
    else -> Color(0xFFF1F5F9)
}

@Composable
fun ActivityCard(
    activity: Activity,
    subjectColor: Color,
    onToggle: () -> Unit,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    tz: TimeZone = edu.unicauca.aplimovil.triageu.model.currentTimeZone()
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .triageCard()
            .padding(18.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(9.dp).clip(CircleShape).background(subjectColor))
                Spacer(Modifier.width(6.dp))
                Text(activity.subject, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextSecondary)
            }

            Text(
                "PRIORIDAD ${activity.priority}",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = priorityColor(activity.priority),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(priorityBackground(activity.priority))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )

            Text(activity.title, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)

            Text("${activity.durationMin} min recomendados", fontSize = 16.sp, color = TriageGreen)

            val date = activity.dueDate.localDate(tz).shortLabel()
            val time = if (activity.hasDueTime) " · ${activity.dueDate.localTime(tz).label()}" else ""
            val weight = activity.weightPercent?.let { " · $it % de la nota" } ?: ""
            Text("$date$time$weight", fontSize = 15.sp, color = TextSecondary)

            Text("Preparación: ${activity.preparation}/5", fontSize = 15.sp, color = TextSecondary)
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            IconButton(onClick = onToggle) {
                Icon(
                    if (activity.completed) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                    contentDescription = if (activity.completed) "Completada" else "Pendiente",
                    modifier = Modifier.size(31.dp),
                    tint = if (activity.completed) TriageGreen else TextSecondary
                )
            }
            if (onEdit != null || onDelete != null) {
                var open by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { open = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Opciones", tint = TextSecondary)
                    }
                    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                        if (onEdit != null) {
                            DropdownMenuItem(text = { Text("Editar") }, onClick = { open = false; onEdit() })
                        }
                        if (onDelete != null) {
                            DropdownMenuItem(
                                text = { Text("Eliminar", color = ErrorRed) },
                                onClick = { open = false; onDelete() }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyState(icon: @Composable () -> Unit, title: String, subtitle: String, topPadding: Dp = 45.dp) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = topPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        icon()
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Text(subtitle, color = TextSecondary)
    }
}
