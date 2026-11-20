package edu.unicauca.aplimovil.triageu.model

import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.until

private val weekdays = listOf("lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo")
private val months = listOf(
    "enero", "febrero", "marzo", "abril", "mayo", "junio",
    "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre"
)
private val monthsShort = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")

val weekdayInitials = listOf("L", "M", "M", "J", "V", "S", "D")

fun currentTimeZone(): TimeZone = TimeZone.currentSystemDefault()

fun nowInstant(): Instant = Clock.System.now()

fun today(tz: TimeZone = currentTimeZone()): LocalDate = nowInstant().toLocalDateTime(tz).date

fun Instant.localDate(tz: TimeZone = currentTimeZone()): LocalDate = toLocalDateTime(tz).date

fun Instant.localTime(tz: TimeZone = currentTimeZone()): LocalTime = toLocalDateTime(tz).time

/** "jueves 19 de noviembre" */
fun LocalDate.longLabel(): String = "${weekdays[dayOfWeek.ordinal]} $dayOfMonth de ${months[monthNumber - 1]}"

/** "Noviembre 2026" */
fun LocalDate.monthYearLabel(): String =
    "${months[monthNumber - 1].replaceFirstChar { it.uppercase() }} $year"

/** "19 nov 2026" */
fun LocalDate.shortLabel(): String = "$dayOfMonth ${monthsShort[monthNumber - 1]} $year"

fun LocalTime.label(): String = "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"

fun LocalDate.firstOfMonth(): LocalDate = LocalDate(year, monthNumber, 1)

/** Lunes de la semana ISO que contiene esta fecha. */
fun LocalDate.startOfWeek(): LocalDate = plus(-dayOfWeek.ordinal, DateTimeUnit.DAY)

fun LocalDate.endOfWeek(): LocalDate = startOfWeek().plus(6, DateTimeUnit.DAY)

fun LocalDate.daysInMonth(): Int {
    val first = firstOfMonth()
    return first.until(first.plus(1, DateTimeUnit.MONTH), DateTimeUnit.DAY)
}

/** Mismo criterio que Swift: sin hora definida la entrega es el inicio del día. */
fun dueInstant(date: LocalDate, hasTime: Boolean, time: LocalTime, tz: TimeZone = currentTimeZone()): Instant =
    if (hasTime) LocalDateTime(date, time).toInstant(tz) else date.atStartOfDayIn(tz)
