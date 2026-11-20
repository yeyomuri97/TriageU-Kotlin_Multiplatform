package edu.unicauca.aplimovil.triageu.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import edu.unicauca.aplimovil.triageu.model.localTime
import edu.unicauca.aplimovil.triageu.model.nowInstant
import edu.unicauca.aplimovil.triageu.resources.Res
import edu.unicauca.aplimovil.triageu.resources.tri_happy
import edu.unicauca.aplimovil.triageu.resources.tri_idle
import edu.unicauca.aplimovil.triageu.resources.tri_sleeping
import edu.unicauca.aplimovil.triageu.resources.tri_thinking
import edu.unicauca.aplimovil.triageu.resources.tri_wave
import edu.unicauca.aplimovil.triageu.resources.tri_worried
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/** Poses de Tri, el oso de anteojos que acompaña al usuario. */
enum class TriPose(val res: DrawableResource) {
    IDLE(Res.drawable.tri_idle),
    WAVE(Res.drawable.tri_wave),
    HAPPY(Res.drawable.tri_happy),
    THINKING(Res.drawable.tri_thinking),
    WORRIED(Res.drawable.tri_worried),
    SLEEPING(Res.drawable.tri_sleeping)
}

/** Imagen de Tri con una suave animación de "respiración". */
@Composable
fun Mascot(pose: TriPose, size: Dp = 96.dp, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "tri")
    val breathe = transition.animateFloat(
        initialValue = 1f,
        targetValue = if (pose == TriPose.SLEEPING) 1.03f else 1.045f,
        animationSpec = infiniteRepeatable(
            tween(if (pose == TriPose.SLEEPING) 3200 else 2200, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "breathe"
    )
    Image(
        painter = painterResource(pose.res),
        contentDescription = "Tri, tu compañero",
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = breathe.value
                scaleY = breathe.value
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
            }
    )
}

/** Tri junto a un globo de diálogo con un mensaje cálido. */
@Composable
fun MascotSays(pose: TriPose, message: String, modifier: Modifier = Modifier, size: Dp = 92.dp) {
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Mascot(pose, size)
        Column(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 18.dp))
                .background(SoftGreen)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(message, fontSize = 16.sp, color = TriageGreenDark)
        }
    }
}

/** Saludo según la hora del día. */
fun greeting(): String = when (nowInstant().localTime().hour) {
    in 5..11 -> "¡Buenos días!"
    in 12..18 -> "¡Buenas tardes!"
    else -> "¡Buenas noches!"
}

/** Pose y mensaje de Tri según el estado de las actividades. */
fun triMood(total: Int, pending: Int, overdue: Int): Pair<TriPose, String> {
    val hour = nowInstant().localTime().hour
    return when {
        total == 0 -> TriPose.WAVE to "¡Hola! Soy Tri. Estoy aquí para acompañarte; empecemos agregando tu primera actividad."
        overdue > 0 -> TriPose.WORRIED to "Tienes $overdue ${if (overdue == 1) "actividad atrasada" else "actividades atrasadas"}. Tranquilo, lo resolvemos juntos, paso a paso."
        pending == 0 -> TriPose.HAPPY to "¡Lo lograste! Todo al día. Mereces un buen descanso."
        hour >= 22 || hour < 5 -> TriPose.SLEEPING to "Ya es tarde... descansar también es parte del plan. Mañana seguimos."
        pending >= 6 -> TriPose.THINKING to "Hay bastante por hacer. Empieza por una sola, yo te acompaño."
        else -> TriPose.IDLE to "Vas muy bien. Tienes $pending ${if (pending == 1) "pendiente" else "pendientes"}, ¡un paso a la vez!"
    }
}
