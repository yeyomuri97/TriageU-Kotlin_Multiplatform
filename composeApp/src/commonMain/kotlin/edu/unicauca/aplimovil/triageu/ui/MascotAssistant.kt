@file:OptIn(
    ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)

package edu.unicauca.aplimovil.triageu.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import edu.unicauca.aplimovil.triageu.TriageStore
import edu.unicauca.aplimovil.triageu.help.HelpAgent
import edu.unicauca.aplimovil.triageu.help.HelpAnswer
import edu.unicauca.aplimovil.triageu.help.HelpTopic
import edu.unicauca.aplimovil.triageu.help.HelpTopics
import edu.unicauca.aplimovil.triageu.help.HybridHelpAgent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private sealed interface ChatMessage {
    data class User(val text: String) : ChatMessage
    data class Tri(val answer: HelpAnswer) : ChatMessage
}

/**
 * Tri flotante: siempre visible en la esquina inferior derecha. Al tocarlo abre el asistente
 * de ayuda. El padre lo coloca (alineación y margen inferior sobre la barra).
 */
@Composable
fun MascotAssistant(
    store: TriageStore,
    modifier: Modifier = Modifier,
    openSignal: Int = 0,
    agent: HelpAgent = remember(store) { HybridHelpAgent(store.auth, store.httpClient) }
) {
    val online by store.isOnline.collectAsState()
    val pending by store.pendingChanges.collectAsState()

    var open by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var answered by remember { mutableStateOf(false) }
    var topic by remember { mutableStateOf<HelpTopic?>(null) }
    var input by remember { mutableStateOf("") }
    val messages = remember { mutableStateListOf<ChatMessage>() }
    val scope = rememberCoroutineScope()

    val pose = when {
        !online -> TriPose.SLEEPING
        loading -> TriPose.THINKING
        pending > 0 -> TriPose.WORRIED
        answered -> TriPose.HAPPY
        open && messages.isEmpty() -> TriPose.WAVE
        else -> TriPose.IDLE
    }

    Mascot(
        pose = pose,
        size = 68.dp,
        modifier = modifier
            .clip(CircleShape)
            .clickable { open = true }
    )

    LaunchedEffect(openSignal) {
        if (openSignal > 0) open = true
    }

    if (!open) return

    fun send(raw: String) {
        val q = raw.trim().take(500)
        if (q.isEmpty() || loading) return
        input = ""
        topic = null
        answered = false
        messages.add(ChatMessage.User(q))
        loading = true
        scope.launch {
            val answer = try {
                agent.ask(q, online, pending)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                HelpAnswer("Ahora no puedo responder. Mira los temas de ayuda.")
            }
            messages.add(ChatMessage.Tri(answer))
            loading = false
            answered = true
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scrollState = rememberScrollState()
    val inputFocus = remember { BringIntoViewRequester() }
    val imeOpen = WindowInsets.isImeVisible

    LaunchedEffect(open) {
        if (open) sheetState.expand()
    }
    LaunchedEffect(imeOpen) {
        if (imeOpen) scrollState.animateScrollTo(scrollState.maxValue)
    }

    ModalBottomSheet(
        onDismissRequest = { open = false; answered = false },
        sheetState = sheetState,
        containerColor = Color.White
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Encabezado
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Mascot(pose, 64.dp)
                Column(Modifier.weight(1f)) {
                    Text("Asistente TriageU", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("¿En qué te ayudo?", fontSize = 15.sp, color = TextSecondary)
                }
                Icon(
                    Icons.Filled.Close, "Cerrar", tint = TextSecondary,
                    modifier = Modifier.size(28.dp).clip(CircleShape).clickable { open = false; answered = false }
                )
            }

            val status = when {
                !online -> "Sin conexión: te muestro ayuda sin internet."
                pending > 0 -> "Tienes $pending ${if (pending == 1) "cambio pendiente" else "cambios pendientes"} por subir."
                else -> null
            }
            status?.let { Text(it, fontSize = 13.sp, color = TextSecondary) }

            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val current = topic
                if (current != null) {
                    TopicSteps(current, onBack = { topic = null })
                } else {
                    HelpTopics.all.forEach { t ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(SoftGreen)
                                .clickable { topic = t }.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(t.title, Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TriageGreenDark)
                            Text("${t.steps.size} pasos", fontSize = 13.sp, color = TextSecondary)
                        }
                    }
                    messages.forEach { m ->
                        when (m) {
                            is ChatMessage.User -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                                Text(
                                    m.text, color = Color.White, fontSize = 15.sp,
                                    modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(TriageGreen)
                                        .padding(horizontal = 14.dp, vertical = 10.dp)
                                )
                            }
                            is ChatMessage.Tri -> Column(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFFF2F4F7)).padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                m.answer.notice?.let { Text(it, fontSize = 13.sp, color = TextSecondary) }
                                Text(m.answer.text, fontSize = 15.sp)
                                HelpTopics.byId(m.answer.topicId)?.let { t ->
                                    Text(
                                        "Ver pasos: ${t.title}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                                        color = TriageGreen, modifier = Modifier.clickable { topic = t }
                                    )
                                }
                            }
                        }
                    }
                    if (loading) Text("Tri está pensando…", fontSize = 14.sp, color = TextSecondary)
                }
            }

            // Campo fijado al borde inferior de la hoja; sube con el teclado.
            Row(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.ime)
                    .navigationBarsPadding()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it.take(500) },
                    modifier = Modifier
                        .weight(1f)
                        .bringIntoViewRequester(inputFocus)
                        .onFocusEvent {
                            if (it.isFocused) scope.launch { inputFocus.bringIntoView() }
                        },
                    placeholder = { Text("Escribe tu pregunta…") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp)
                )
                Box(
                    Modifier.size(48.dp).clip(CircleShape)
                        .background(if (input.isBlank() || loading) TriageGreen.copy(alpha = 0.4f) else TriageGreen)
                        .clickable(enabled = input.isNotBlank() && !loading) { send(input) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, "Enviar", tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun TopicSteps(topic: HelpTopic, onBack: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier.clickable(onClick = onBack),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = TriageGreen)
            Text(topic.title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        topic.steps.forEachIndexed { i, step ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                Box(
                    Modifier.size(26.dp).clip(CircleShape).background(TriageGreen),
                    contentAlignment = Alignment.Center
                ) { Text("${i + 1}", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                Text(step, fontSize = 15.sp, modifier = Modifier.weight(1f))
            }
        }
    }
}
