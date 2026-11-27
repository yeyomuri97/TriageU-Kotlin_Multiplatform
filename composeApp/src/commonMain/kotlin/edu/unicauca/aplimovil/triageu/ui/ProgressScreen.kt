package edu.unicauca.aplimovil.triageu.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import edu.unicauca.aplimovil.triageu.TriageStore
import edu.unicauca.aplimovil.triageu.domain.AcademicAnalyzer
import edu.unicauca.aplimovil.triageu.domain.AcademicRecommendation
import edu.unicauca.aplimovil.triageu.domain.SubjectProgress
import edu.unicauca.aplimovil.triageu.model.Activity
import edu.unicauca.aplimovil.triageu.model.Priority
import edu.unicauca.aplimovil.triageu.model.Subject
import edu.unicauca.aplimovil.triageu.model.currentTimeZone
import edu.unicauca.aplimovil.triageu.model.daysInMonth
import edu.unicauca.aplimovil.triageu.model.endOfWeek
import edu.unicauca.aplimovil.triageu.model.firstOfMonth
import edu.unicauca.aplimovil.triageu.model.localDate
import edu.unicauca.aplimovil.triageu.model.nowInstant
import edu.unicauca.aplimovil.triageu.model.startOfWeek
import edu.unicauca.aplimovil.triageu.model.today
import edu.unicauca.aplimovil.triageu.model.weekdayInitials
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

private enum class ProgressPeriod(val label: String) {
    WEEK("Semana"),
    MONTH("Mes"),
    ALL("Todo")
}

private fun Activity.dueLocal() =
    dueDate.localDate(currentTimeZone())

private fun filterByPeriod(
    activities: List<Activity>,
    period: ProgressPeriod,
    now: LocalDate
): List<Activity> =
    when (period) {
        ProgressPeriod.ALL -> activities

        ProgressPeriod.WEEK -> {
            val start = now.startOfWeek()
            val end = now.endOfWeek()

            activities.filter {
                it.dueLocal() in start..end
            }
        }

        ProgressPeriod.MONTH -> {
            val start = now.firstOfMonth()
            val end = start.plus(
                now.daysInMonth() - 1,
                DateTimeUnit.DAY
            )

            activities.filter {
                it.dueLocal() in start..end
            }
        }
    }

@Composable
fun ProgressScreen(store: TriageStore) {

    val activities by store.activities.collectAsState()
    val subjects by store.subjects.collectAsState()

    var period by remember {
        mutableStateOf(ProgressPeriod.WEEK)
    }

    val tz = currentTimeZone()
    val nowDate = today(tz)
    val nowMs = nowInstant().toEpochMilliseconds()

    val scoped = remember(
        activities,
        period,
        nowDate
    ) {
        filterByPeriod(
            activities,
            period,
            nowDate
        )
    }

    val total = scoped.size

    val done =
        scoped.count {
            it.completed
        }

    val pending =
        total - done

    val overdue =
        scoped.count {
            !it.completed &&
                    it.dueDate.toEpochMilliseconds() < nowMs
        }

    val highPending =
        scoped.count {
            !it.completed &&
                    it.priority == Priority.HIGH
        }

    val pct =
        if (total == 0) {
            0
        } else {
            (done * 100) / total
        }

    val ratio =
        if (total == 0) {
            0f
        } else {
            done.toFloat() / total
        }

    val recommendations =
        remember(
            activities,
            nowMs
        ) {
            AcademicAnalyzer.recommendations(
                activities = activities,
                nowMs = nowMs
            )
        }

    fun colorFor(name: String): Color =
        subjects
            .firstOrNull {
                it.name == name
            }
            ?.let {
                colorFromHex(it.colorHex)
            }
            ?: TriageGreen

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(
                rememberScrollState()
            )
            .padding(
                start = 20.dp,
                end = 20.dp,
                top = 30.dp,
                bottom = 40.dp
            ),
        verticalArrangement =
            Arrangement.spacedBy(20.dp)
    ) {

        ScreenTitle("Progreso")

        if (recommendations.isNotEmpty()) {
            AcademicRecommendationsCard(
                recommendations
            )
        }

        PeriodPills(period) {
            period = it
        }

        CompletionCard(
            ratio,
            pct,
            done,
            total
        )

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement =
                Arrangement.spacedBy(10.dp)
        ) {

            StatChip(
                modifier =
                    Modifier.weight(1f),
                label = "Pendientes",
                value = "$pending",
                accent = TextSecondary
            )

            StatChip(
                modifier =
                    Modifier.weight(1f),
                label = "Vencidas",
                value = "$overdue",
                accent =
                    if (overdue > 0) {
                        ErrorRed
                    } else {
                        TextSecondary
                    }
            )

            StatChip(
                modifier =
                    Modifier.weight(1f),
                label = "Alta pendiente",
                value = "$highPending",
                accent =
                    if (highPending > 0) {
                        Color(0xFFB45309)
                    } else {
                        TextSecondary
                    }
            )
        }

        WeekDeliveriesCard(
            activities,
            nowDate
        )

        val bySubject =
            remember(scoped) {
                AcademicAnalyzer
                    .subjectProgress(scoped)
            }

        if (bySubject.isNotEmpty()) {

            BySubjectCard(
                rows = bySubject,
                subjects = subjects,
                colorFor = ::colorFor
            )

            val subjectToImprove =
                AcademicAnalyzer
                    .subjectToImprove(
                        bySubject
                    )

            if (subjectToImprove != null) {
                SubjectInsightCard(
                    subjectToImprove
                )
            }
        }

        MotivationCard(
            total = total,
            overdue = overdue,
            highPending = highPending,
            pending = pending,
            pct = pct
        )
    }
}

@Composable
private fun PeriodPills(
    selected: ProgressPeriod,
    onSelect: (ProgressPeriod) -> Unit
) {

    Row(
        horizontalArrangement =
            Arrangement.spacedBy(8.dp)
    ) {

        ProgressPeriod.entries
            .forEach { period ->

                val active =
                    period == selected

                Text(
                    text = period.label,
                    fontSize = 14.sp,
                    fontWeight =
                        if (active) {
                            FontWeight.SemiBold
                        } else {
                            FontWeight.Normal
                        },
                    color =
                        if (active) {
                            Color.White
                        } else {
                            TriageGreenDark
                        },
                    modifier =
                        Modifier
                            .clip(
                                RoundedCornerShape(
                                    20.dp
                                )
                            )
                            .background(
                                if (active) {
                                    TriageGreen
                                } else {
                                    SoftGreen
                                }
                            )
                            .clickable {
                                onSelect(period)
                            }
                            .padding(
                                horizontal = 16.dp,
                                vertical = 8.dp
                            )
                )
            }
    }
}

@Composable
private fun CompletionCard(
    ratio: Float,
    pct: Int,
    done: Int,
    total: Int
) {

    Row(
        Modifier
            .fillMaxWidth()
            .triageCard()
            .padding(20.dp),
        verticalAlignment =
            Alignment.CenterVertically,
        horizontalArrangement =
            Arrangement.spacedBy(16.dp)
    ) {

        Box(
            Modifier.size(120.dp),
            contentAlignment =
                Alignment.Center
        ) {

            Canvas(
                Modifier.fillMaxSize()
            ) {

                val stroke =
                    14.dp.toPx()

                val inset =
                    stroke / 2f

                val arcSize =
                    Size(
                        size.width - stroke,
                        size.height - stroke
                    )

                val topLeft =
                    Offset(
                        inset,
                        inset
                    )

                drawArc(
                    color = SoftGreen,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style =
                        Stroke(
                            width = stroke,
                            cap = StrokeCap.Round
                        )
                )

                if (ratio > 0f) {

                    drawArc(
                        color = TriageGreen,
                        startAngle = -90f,
                        sweepAngle =
                            360f *
                                    ratio.coerceIn(
                                        0f,
                                        1f
                                    ),
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style =
                            Stroke(
                                width = stroke,
                                cap = StrokeCap.Round
                            )
                    )
                }
            }

            Column(
                horizontalAlignment =
                    Alignment.CenterHorizontally
            ) {

                Text(
                    "$pct%",
                    fontSize = 28.sp,
                    fontWeight =
                        FontWeight.Bold,
                    color =
                        TriageGreenDark
                )

                Text(
                    "cumplido",
                    fontSize = 13.sp,
                    color =
                        TextSecondary
                )
            }
        }

        Column(
            verticalArrangement =
                Arrangement.spacedBy(6.dp)
        ) {

            Text(
                "$done de $total",
                fontSize = 26.sp,
                fontWeight =
                    FontWeight.Bold,
                color =
                    TriageGreenDark
            )

            Text(
                "actividades completadas",
                fontSize = 15.sp,
                color =
                    TextSecondary
            )
        }
    }
}

@Composable
private fun StatChip(
    modifier: Modifier,
    label: String,
    value: String,
    accent: Color
) {

    Column(
        modifier
            .triageCard(14.dp)
            .padding(
                horizontal = 12.dp,
                vertical = 14.dp
            ),
        horizontalAlignment =
            Alignment.CenterHorizontally,
        verticalArrangement =
            Arrangement.spacedBy(4.dp)
    ) {

        Text(
            value,
            fontSize = 22.sp,
            fontWeight =
                FontWeight.Bold,
            color = accent
        )

        Text(
            label,
            fontSize = 12.sp,
            color =
                TextSecondary,
            textAlign =
                TextAlign.Center
        )
    }
}

@Composable
private fun WeekDeliveriesCard(
    activities: List<Activity>,
    today: LocalDate
) {

    val weekStart =
        today.startOfWeek()

    val days =
        (0..6).map {
            weekStart.plus(
                it,
                DateTimeUnit.DAY
            )
        }

    val counts =
        days.map { day ->

            val dayActivities =
                activities.filter {
                    it.dueLocal() == day
                }

            dayActivities.size to
                    dayActivities.count {
                        it.completed
                    }
        }

    val maxTotal =
        counts
            .maxOfOrNull {
                it.first
            }
            ?.coerceAtLeast(1)
            ?: 1

    Column(
        Modifier
            .fillMaxWidth()
            .triageCard()
            .padding(16.dp),
        verticalArrangement =
            Arrangement.spacedBy(12.dp)
    ) {

        Text(
            "Entregas de la semana",
            fontSize = 18.sp,
            fontWeight =
                FontWeight.SemiBold,
            color =
                TriageGreenDark
        )

        Canvas(
            Modifier
                .fillMaxWidth()
                .height(120.dp)
        ) {

            val barGap =
                8.dp.toPx()

            val chartHeight =
                size.height

            val barWidth =
                (
                        size.width -
                                barGap * 6
                        ) / 7f

            days.forEachIndexed {
                    index,
                    day ->

                val pair =
                    counts[index]

                val total =
                    pair.first

                val completed =
                    pair.second

                val x =
                    index *
                            (
                                    barWidth +
                                            barGap
                                    )

                val totalHeight =
                    if (total == 0) {
                        0f
                    } else {
                        (
                                total.toFloat() /
                                        maxTotal
                                ) *
                                chartHeight *
                                0.92f
                    }

                val completedHeight =
                    if (total == 0) {
                        0f
                    } else {
                        (
                                completed.toFloat() /
                                        maxTotal
                                ) *
                                chartHeight *
                                0.92f
                    }

                val baseY =
                    size.height

                if (totalHeight > 0f) {

                    drawRoundRect(
                        color = SoftGreen,
                        topLeft =
                            Offset(
                                x,
                                baseY -
                                        totalHeight
                            ),
                        size =
                            Size(
                                barWidth,
                                totalHeight
                            ),
                        cornerRadius =
                            CornerRadius(
                                6.dp.toPx(),
                                6.dp.toPx()
                            )
                    )
                }

                if (completedHeight > 0f) {

                    drawRoundRect(
                        color =
                            if (day == today) {
                                TriageGreenDark
                            } else {
                                TriageGreen
                            },
                        topLeft =
                            Offset(
                                x,
                                baseY -
                                        completedHeight
                            ),
                        size =
                            Size(
                                barWidth,
                                completedHeight
                            ),
                        cornerRadius =
                            CornerRadius(
                                6.dp.toPx(),
                                6.dp.toPx()
                            )
                    )
                }
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement =
                Arrangement.SpaceBetween
        ) {

            days.forEachIndexed {
                    index,
                    day ->

                val isToday =
                    day == today

                Text(
                    weekdayInitials[index],
                    fontSize = 13.sp,
                    fontWeight =
                        if (isToday) {
                            FontWeight.Bold
                        } else {
                            FontWeight.Normal
                        },
                    color =
                        if (isToday) {
                            TriageGreenDark
                        } else {
                            TextSecondary
                        },
                    modifier =
                        Modifier.width(36.dp),
                    textAlign =
                        TextAlign.Center
                )
            }
        }

        Row(
            horizontalArrangement =
                Arrangement.spacedBy(16.dp)
        ) {

            LegendDot(
                SoftGreen,
                "Total del día"
            )

            LegendDot(
                TriageGreen,
                "Completadas"
            )
        }
    }
}

@Composable
private fun LegendDot(
    color: Color,
    text: String
) {

    Row(
        verticalAlignment =
            Alignment.CenterVertically,
        horizontalArrangement =
            Arrangement.spacedBy(6.dp)
    ) {

        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )

        Text(
            text,
            fontSize = 12.sp,
            color =
                TextSecondary
        )
    }
}

@Composable
private fun BySubjectCard(
    rows: List<SubjectProgress>,
    subjects: List<Subject>,
    colorFor: (String) -> Color
) {

    Column(
        Modifier
            .fillMaxWidth()
            .triageCard()
            .padding(16.dp),
        verticalArrangement =
            Arrangement.spacedBy(14.dp)
    ) {

        Text(
            "Por materia",
            fontSize = 18.sp,
            fontWeight =
                FontWeight.SemiBold,
            color =
                TriageGreenDark
        )

        rows.forEach { row ->

            val color =
                colorFor(row.name)

            Column(
                verticalArrangement =
                    Arrangement.spacedBy(6.dp)
            ) {

                Row(
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {

                    Box(
                        Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(color)
                    )

                    Text(
                        row.name,
                        Modifier
                            .padding(
                                start = 8.dp
                            )
                            .weight(1f),
                        fontSize = 16.sp,
                        fontWeight =
                            FontWeight.Medium
                    )

                    Text(
                        "${row.done}/${row.total}",
                        fontSize = 14.sp,
                        color =
                            TextSecondary
                    )
                }

                LinearProgressIndicator(
                    progress = {
                        if (row.total == 0) {
                            0f
                        } else {
                            row.done.toFloat() /
                                    row.total
                        }
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(
                                RoundedCornerShape(
                                    3.dp
                                )
                            ),
                    color = color,
                    trackColor =
                        SoftGreen
                )
            }
        }
    }
}

@Composable
private fun SubjectInsightCard(
    subject: SubjectProgress
) {

    val completion =
        subject.completionPercent

    val remaining =
        subject.remaining

    Column(
        Modifier
            .fillMaxWidth()
            .triageCard()
            .padding(16.dp),
        verticalArrangement =
            Arrangement.spacedBy(10.dp)
    ) {

        Row(
            verticalAlignment =
                Alignment.CenterVertically,
            horizontalArrangement =
                Arrangement.spacedBy(8.dp)
        ) {

            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(
                        Color(0xFFFFF1D6)
                    ),
                contentAlignment =
                    Alignment.Center
            ) {

                Text(
                    "!",
                    fontSize = 20.sp,
                    fontWeight =
                        FontWeight.Bold,
                    color =
                        Color(0xFFB45309)
                )
            }

            Text(
                "Materia que necesita atención",
                fontSize = 17.sp,
                fontWeight =
                    FontWeight.SemiBold,
                color =
                    TriageGreenDark
            )
        }

        Text(
            subject.name,
            fontSize = 20.sp,
            fontWeight =
                FontWeight.Bold,
            color =
                TriageGreenDark
        )

        Text(
            "Tiene el menor porcentaje de cumplimiento entre tus materias en este periodo.",
            fontSize = 14.sp,
            color =
                TextSecondary
        )

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement =
                Arrangement.SpaceBetween,
            verticalAlignment =
                Alignment.CenterVertically
        ) {

            Text(
                "Cumplimiento",
                fontSize = 14.sp,
                color =
                    TextSecondary
            )

            Text(
                "$completion%",
                fontSize = 17.sp,
                fontWeight =
                    FontWeight.Bold,
                color =
                    Color(0xFFB45309)
            )
        }

        LinearProgressIndicator(
            progress = {
                completion / 100f
            },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(
                        RoundedCornerShape(
                            4.dp
                        )
                    ),
            color =
                Color(0xFFD97706),
            trackColor =
                SoftGreen
        )

        Text(
            "$remaining ${
                if (remaining == 1) {
                    "actividad pendiente"
                } else {
                    "actividades pendientes"
                }
            } de ${subject.total}",
            fontSize = 14.sp,
            fontWeight =
                FontWeight.Medium,
            color =
                TextSecondary
        )
    }
}

@Composable
private fun MotivationCard(
    total: Int,
    overdue: Int,
    highPending: Int,
    pending: Int,
    pct: Int
) {

    val message =
        when {
            total == 0 ->
                "Empieza tu plan" to
                        "Aún no hay actividades en este periodo. Usa el botón + para crear la primera."

            overdue > 0 ->
                "Revisa lo vencido" to
                        "Tienes $overdue ${
                            if (overdue == 1) {
                                "actividad vencida"
                            } else {
                                "actividades vencidas"
                            }
                        }. Complétalas o ajústales la fecha."

            pending == 0 ->
                "¡Todo al día!" to
                        "Has completado todas las actividades de este periodo. Buen trabajo."

            highPending > 0 ->
                "Prioriza lo urgente" to
                        "Tienes $highPending ${
                            if (highPending == 1) {
                                "actividad"
                            } else {
                                "actividades"
                            }
                        } de prioridad alta pendiente. Enfócate en ellas primero."

            else ->
                "Buen ritmo" to
                        "Llevas un $pct% de cumplimiento en este periodo. Sigue así, paso a paso."
        }

    val title =
        message.first

    val body =
        message.second

    Column(
        Modifier
            .fillMaxWidth()
            .clip(
                RoundedCornerShape(
                    18.dp
                )
            )
            .background(SoftGreen)
            .padding(18.dp),
        verticalArrangement =
            Arrangement.spacedBy(8.dp)
    ) {

        Text(
            title,
            fontSize = 19.sp,
            fontWeight =
                FontWeight.SemiBold,
            color =
                TriageGreenDark
        )

        Text(
            body,
            fontSize = 15.sp,
            color =
                TextSecondary
        )
    }
}

@Composable
private fun AcademicRecommendationsCard(
    recommendations:
    List<AcademicRecommendation>
) {

    Column(
        Modifier
            .fillMaxWidth()
            .triageCard()
            .padding(16.dp),
        verticalArrangement =
            Arrangement.spacedBy(14.dp)
    ) {

        Row(
            verticalAlignment =
                Alignment.CenterVertically,
            horizontalArrangement =
                Arrangement.spacedBy(8.dp)
        ) {

            Text(
                "✦",
                fontSize = 25.sp,
                color =
                    Color(0xFFD97706)
            )

            Column {

                Text(
                    "Recomendaciones académicas",
                    fontSize = 17.sp,
                    fontWeight =
                        FontWeight.Bold,
                    color =
                        TriageGreenDark
                )

                Text(
                    "Actividades que requieren tu atención",
                    fontSize = 12.sp,
                    color =
                        TextSecondary
                )
            }
        }

        recommendations
            .forEachIndexed {
                    index,
                    recommendation ->

                val activity =
                    recommendation.activity

                Column(
                    Modifier.fillMaxWidth(),
                    verticalArrangement =
                        Arrangement.spacedBy(6.dp)
                ) {

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement =
                            Arrangement.SpaceBetween,
                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {

                        Text(
                            "${index + 1}. ${activity.title}",
                            modifier =
                                Modifier.weight(1f),
                            fontSize = 15.sp,
                            fontWeight =
                                FontWeight.SemiBold,
                            color =
                                TriageGreenDark
                        )
                    }

                    Text(
                        activity.subject,
                        fontSize = 12.sp,
                        color =
                            TextSecondary
                    )

                    Text(
                        recommendation.status,
                        fontSize = 11.sp,
                        fontWeight =
                            FontWeight.Bold,
                        color =
                            when (
                                recommendation.status
                            ) {
                                "VENCIDA" ->
                                    ErrorRed

                                "PRIORIDAD ALTA" ->
                                    Color(0xFFB45309)

                                "PRIORIDAD MEDIA" ->
                                    Color(0xFFCA8A04)

                                else ->
                                    TriageGreen
                            }
                    )

                    Text(
                        recommendation.reason,
                        fontSize = 13.sp,
                        color =
                            TextSecondary
                    )
                }

                if (
                    index !=
                    recommendations.lastIndex
                ) {

                    androidx.compose.material3
                        .HorizontalDivider(
                            color =
                                SoftGreen
                        )
                }
            }

        Text(
            "Las recomendaciones se actualizan automáticamente cuando cambian tus actividades.",
            fontSize = 11.sp,
            color =
                TextSecondary
        )
    }
}
