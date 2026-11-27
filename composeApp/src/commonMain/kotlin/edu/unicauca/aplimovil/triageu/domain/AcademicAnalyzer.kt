package edu.unicauca.aplimovil.triageu.domain

import edu.unicauca.aplimovil.triageu.model.Activity
import edu.unicauca.aplimovil.triageu.model.Priority

data class SubjectProgress(
    val name: String,
    val done: Int,
    val total: Int
) {
    val completionPercent: Int
        get() = if (total == 0) 0 else (done * 100) / total

    val remaining: Int
        get() = total - done
}

data class AcademicRecommendation(
    val activity: Activity,
    val reason: String,
    val status: String
)

object AcademicAnalyzer {

    fun subjectProgress(
        activities: List<Activity>
    ): List<SubjectProgress> {
        return activities
            .groupBy { it.subject }
            .map { (name, list) ->
                SubjectProgress(
                    name = name,
                    done = list.count { it.completed },
                    total = list.size
                )
            }
            .filter { it.total > 0 }
            .sortedByDescending { it.total }
    }

    fun subjectToImprove(
        progress: List<SubjectProgress>
    ): SubjectProgress? {
        return progress.minByOrNull {
            it.done.toFloat() / it.total
        }
    }

    fun recommendations(
        activities: List<Activity>,
        nowMs: Long
    ): List<AcademicRecommendation> {

        val pendingActivities = activities
            .filter { !it.completed }
            .sortedWith(
                compareBy<Activity> {
                    if (it.dueDate.toEpochMilliseconds() < nowMs) {
                        0
                    } else {
                        1
                    }
                }.thenBy {
                    Priority.rank(it.priority)
                }.thenBy {
                    it.dueDate.toEpochMilliseconds()
                }
            )

        return pendingActivities
            .take(3)
            .map { activity ->

                val isOverdue =
                    activity.dueDate.toEpochMilliseconds() < nowMs

                val reason = when {
                    isOverdue ->
                        "Esta actividad ya está vencida. Es importante atenderla cuanto antes."

                    activity.priority == Priority.HIGH ->
                        "Tiene prioridad alta. Conviene dedicarle atención antes que a otras actividades."

                    activity.priority == Priority.MEDIUM ->
                        "Tiene prioridad media. Organiza tiempo para avanzar antes de su entrega."

                    else ->
                        "Aunque tiene prioridad baja, es recomendable avanzar para evitar acumulaciones."
                }

                val status = when {
                    isOverdue ->
                        "VENCIDA"

                    activity.priority == Priority.HIGH ->
                        "PRIORIDAD ALTA"

                    activity.priority == Priority.MEDIUM ->
                        "PRIORIDAD MEDIA"

                    else ->
                        "PRIORIDAD BAJA"
                }

                AcademicRecommendation(
                    activity = activity,
                    reason = reason,
                    status = status
                )
            }
    }
}
