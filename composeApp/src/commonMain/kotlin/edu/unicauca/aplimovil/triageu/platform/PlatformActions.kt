package edu.unicauca.aplimovil.triageu.platform

/** Versión legible para el usuario (nombre + build). */
expect fun appVersionLabel(): String

expect fun openExternalUrl(url: String)

/** Comparte texto (p. ej. exportación JSON) con la hoja nativa de compartir. */
expect fun sharePlainText(text: String, suggestedFileName: String)

expect fun openAppNotificationSettings()

/** Consulta si las notificaciones están permitidas (puede tardar un instante en iOS). */
expect suspend fun queryNotificationsEnabled(): Boolean