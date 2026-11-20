@file:OptIn(ExperimentalLayoutApi::class)

package edu.unicauca.aplimovil.triageu

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import edu.unicauca.aplimovil.triageu.data.remote.SupabaseConfig
import edu.unicauca.aplimovil.triageu.ui.ActivityFormState
import edu.unicauca.aplimovil.triageu.ui.AboutScreen
import edu.unicauca.aplimovil.triageu.ui.AuthScreen
import edu.unicauca.aplimovil.triageu.ui.BackgroundGreen
import edu.unicauca.aplimovil.triageu.ui.MascotAssistant
import edu.unicauca.aplimovil.triageu.ui.NewActivityScreen
import edu.unicauca.aplimovil.triageu.ui.ProfileScreen
import edu.unicauca.aplimovil.triageu.ui.ProgressScreen
import edu.unicauca.aplimovil.triageu.ui.AgendaScreen
import edu.unicauca.aplimovil.triageu.ui.TextSecondary
import edu.unicauca.aplimovil.triageu.ui.TodayScreen
import edu.unicauca.aplimovil.triageu.ui.TriageGreen
import edu.unicauca.aplimovil.triageu.ui.TriageTheme

@Composable
fun App(store: TriageStore) {
    TriageTheme {
        val session by store.auth.session.collectAsState()
        // El login es opcional: se abre desde Perfil y se cierra solo al iniciar sesión.
        var showAuth by remember { mutableStateOf(false) }
        var showAbout by remember { mutableStateOf(false) }
        var assistantSignal by remember { mutableStateOf(0) }
        LaunchedEffect(session) { if (session != null) showAuth = false }

        Box(
            Modifier
                .fillMaxSize()
                .background(BackgroundGreen)
                .safeDrawingPadding()
        ) {
            if (showAuth && SupabaseConfig.isConfigured && session == null) {
                AuthScreen(store, onBack = { showAuth = false })
            } else {
                MainContent(
                    store,
                    showAbout,
                    { showAbout = it },
                    onLoginTap = { showAuth = true },
                    onOpenAssistant = { assistantSignal++ }
                )
            }

            // Tri: siempre visible abajo a la derecha, por encima de la barra inferior.
            val barVisible = !(showAuth && SupabaseConfig.isConfigured && session == null) &&
                !showAbout && !WindowInsets.isImeVisible
            MascotAssistant(
                store = store,
                openSignal = assistantSignal,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 12.dp, bottom = if (barVisible) 80.dp else 12.dp)
            )
        }
    }
}

private enum class Tab { TODAY, AGENDA, NEW, PROGRESS, PROFILE }

@Composable
private fun MainContent(
    store: TriageStore,
    showAbout: Boolean,
    setShowAbout: (Boolean) -> Unit,
    onLoginTap: () -> Unit,
    onOpenAssistant: () -> Unit
) {
    var tab by remember { mutableStateOf(Tab.TODAY) }
    // El formulario sobrevive al cambio de pestaña, como el TriageViewModel de iOS.
    val form = remember { ActivityFormState() }

    if (showAbout) {
        AboutScreen(onBack = { setShowAbout(false) })
        return
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            when (tab) {
                Tab.TODAY -> TodayScreen(store)
                Tab.AGENDA -> AgendaScreen(store)
                Tab.NEW -> NewActivityScreen(store, form)
                Tab.PROGRESS -> ProgressScreen(store)
                Tab.PROFILE -> ProfileScreen(
                    store,
                    onAboutTap = { setShowAbout(true) },
                    onLoginTap = onLoginTap,
                    onOpenAssistant = onOpenAssistant
                )
            }
        }
        if (!WindowInsets.isImeVisible) {
            BottomBar(selected = tab, onSelect = { tab = it })
        }
    }
}

@Composable
private fun BottomBar(selected: Tab, onSelect: (Tab) -> Unit) {
    // La barra mide 70 dp; el botón "+" (72 dp) queda centrado sobre su borde superior.
    Box(Modifier.fillMaxWidth().height(106.dp)) {
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(70.dp)
                .shadow(8.dp)
                .background(Color.White)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BarItem("Inicio", Icons.Filled.Home, selected == Tab.TODAY, Modifier.weight(1f)) { onSelect(Tab.TODAY) }
            BarItem("Agenda", Icons.Filled.CalendarMonth, selected == Tab.AGENDA, Modifier.weight(1f)) { onSelect(Tab.AGENDA) }
            Box(Modifier.weight(1f))
            BarItem("Progreso", Icons.Filled.TrendingUp, selected == Tab.PROGRESS, Modifier.weight(1f)) { onSelect(Tab.PROGRESS) }
            BarItem("Perfil", Icons.Filled.Person, selected == Tab.PROFILE, Modifier.weight(1f)) { onSelect(Tab.PROFILE) }
        }

        Box(
            Modifier
                .align(Alignment.TopCenter)
                .size(72.dp)
                .shadow(8.dp, CircleShape)
                .clip(CircleShape)
                .background(TriageGreen)
                .clickable { onSelect(Tab.NEW) },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Nueva actividad", tint = Color.White, modifier = Modifier.size(32.dp))
        }
    }
}

@Composable
private fun BarItem(label: String, icon: ImageVector, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clickable(onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        val color = if (selected) TriageGreen else TextSecondary
        Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(24.dp))
        Text(label, fontSize = 12.sp, color = color)
    }
}
