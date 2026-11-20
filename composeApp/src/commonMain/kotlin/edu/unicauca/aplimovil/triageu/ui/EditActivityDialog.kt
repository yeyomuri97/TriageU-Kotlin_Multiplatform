@file:OptIn(ExperimentalMaterial3Api::class)

package edu.unicauca.aplimovil.triageu.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import edu.unicauca.aplimovil.triageu.TriageStore
import edu.unicauca.aplimovil.triageu.model.Activity
import edu.unicauca.aplimovil.triageu.model.ActivityTypes
import edu.unicauca.aplimovil.triageu.model.dueInstant
import edu.unicauca.aplimovil.triageu.model.localDate
import edu.unicauca.aplimovil.triageu.model.localTime

/** Hoja a pantalla completa (equivale al `.sheet` de SwiftUI). */
@Composable
fun FullScreenSheet(
    title: String,
    onCancel: () -> Unit,
    cancelLabel: String = "Cancelar",
    action: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)
    ) {
        Surface(Modifier.fillMaxSize(), color = BackgroundGreen) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = onCancel) { Text(cancelLabel, color = TriageGreen) }
                    Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    if (action != null) action() else Spacer(Modifier.width(72.dp))
                }
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
fun EditActivityDialog(store: TriageStore, activity: Activity, onDismiss: () -> Unit) {
    val subjectList by store.subjects.collectAsState()

    var title by remember { mutableStateOf(activity.title) }
    var subject by remember { mutableStateOf(activity.subject) }
    var type by remember { mutableStateOf(activity.type) }
    var date by remember { mutableStateOf(activity.dueDate.localDate()) }
    var hasTime by remember { mutableStateOf(activity.hasDueTime) }
    var time by remember { mutableStateOf(activity.dueDate.localTime()) }
    var weight by remember { mutableStateOf(activity.weightPercent?.toString() ?: "") }
    var preparation by remember { mutableStateOf(activity.preparation) }
    var hours by remember { mutableStateOf(maxOf(1, activity.durationMin / 60)) }

    // Si la materia o el tipo actuales ya no están en las listas, se conservan como opción.
    val names = subjectList.map { it.name }.let { if (subject in it) it else it + subject }
    val types = ActivityTypes.all.let { if (type in it) it else it + type }

    FullScreenSheet(
        title = "Editar actividad",
        onCancel = onDismiss,
        action = {
            TextButton(
                enabled = title.trim().isNotEmpty(),
                onClick = {
                    store.updateActivity(
                        activity.copy(
                            title = title.trim(),
                            subject = subject,
                            type = type,
                            dueDate = dueInstant(date, hasTime, time),
                            hasDueTime = hasTime,
                            weightPercent = weight.toIntOrNull(),
                            preparation = preparation,
                            durationMin = hours * 60
                        )
                    )
                    onDismiss()
                }
            ) { Text("Guardar", color = TriageGreen, fontWeight = FontWeight.SemiBold) }
        }
    ) {
        Text("Información básica", fontSize = 15.sp, color = TextSecondary)
        InputTitle("Materia")
        DropdownField(subject, names, { it }) { subject = it }
        InputTitle("Actividad")
        TriageTextField(title, { title = it })
        InputTitle("Tipo")
        DropdownField(type, types, { it }) { type = it }
        InputTitle("Fecha")
        DateField(date) { date = it }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Definir hora de entrega", fontSize = 17.sp)
            Switch(
                checked = hasTime,
                onCheckedChange = { hasTime = it },
                colors = SwitchDefaults.colors(checkedTrackColor = TriageGreen)
            )
        }
        if (hasTime) TimeField(time) { time = it }

        Text("Priorización", fontSize = 15.sp, color = TextSecondary)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TriageTextField(
                value = weight,
                onValueChange = { v -> weight = v.filter { it.isDigit() }.take(3) },
                modifier = Modifier.weight(1f),
                placeholder = "Peso (opcional)",
                keyboardType = KeyboardType.Number
            )
            Text("%", color = TextSecondary)
        }
        Text("Preparación", fontSize = 17.sp)
        PreparationSelector(preparation) { preparation = it }
        InputTitle("Tiempo estimado")
        HoursStepper(hours, { hours = it }, suffix = "h")
    }
}
