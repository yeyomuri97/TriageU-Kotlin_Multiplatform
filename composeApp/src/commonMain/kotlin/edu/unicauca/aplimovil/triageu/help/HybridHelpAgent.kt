package edu.unicauca.aplimovil.triageu.help

import edu.unicauca.aplimovil.triageu.data.remote.AuthManager
import edu.unicauca.aplimovil.triageu.data.remote.SupabaseConfig
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Primero responde en local; si no hay coincidencia y hay sesión, consulta la Edge Function
 * `help-agent`. Solo se envía la pregunta, el estado de conexión y el número de cambios pendientes
 * (nunca correo, ids ni actividades).
 */
class HybridHelpAgent(
    private val auth: AuthManager,
    private val client: HttpClient,
    private val local: LocalHelpAgent = LocalHelpAgent()
) : HelpAgent {

    @Serializable
    private data class Request(val question: String, val online: Boolean, val pending: Int)

    @Serializable
    private data class Response(val text: String, val topicId: String? = null)

    private val json = Json { ignoreUnknownKeys = true }
    private val cache = HashMap<String, HelpAnswer>()

    override suspend fun ask(question: String, online: Boolean, pending: Int): HelpAnswer {
        val clean = question.trim().take(500)
        local.match(clean)?.let { return it }

        val key = clean.lowercase()
        cache[key]?.let { return it }

        val fallback = local.ask(clean, online, pending)
        if (!online || !SupabaseConfig.isConfigured) return fallback.withNotice()
        val token = try {
            auth.validToken()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        } ?: return fallback   // sin sesión: solo ayuda local, sin aviso de error

        return try {
            val resp = client.post("${SupabaseConfig.url}/functions/v1/help-agent") {
                header("apikey", SupabaseConfig.anonKey)
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                timeout {
                    requestTimeoutMillis = TIMEOUT_MS
                    connectTimeoutMillis = TIMEOUT_MS
                    socketTimeoutMillis = TIMEOUT_MS
                }
                setBody(json.encodeToString(Request.serializer(), Request(clean, online, pending.coerceAtLeast(0))))
            }
            if (!resp.status.isSuccess()) {
                // 429 (límite diario) o 503 (IA no disponible) u otro error.
                return fallback.withNotice(
                    if (resp.status.value == 429) "Llegaste al límite de consultas de hoy; mira estos temas."
                    else NOTICE
                )
            }
            val body = json.decodeFromString(Response.serializer(), resp.bodyAsText())
            HelpAnswer(body.text, body.topicId?.takeIf { HelpTopics.byId(it) != null })
                .also { cache[key] = it }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            fallback.withNotice()
        }
    }

    private fun HelpAnswer.withNotice(message: String = NOTICE) = copy(notice = message)

    private companion object {
        const val TIMEOUT_MS = 8_000L
        const val NOTICE = "Ahora no puedo consultar a la IA; mira estos temas."
    }
}
