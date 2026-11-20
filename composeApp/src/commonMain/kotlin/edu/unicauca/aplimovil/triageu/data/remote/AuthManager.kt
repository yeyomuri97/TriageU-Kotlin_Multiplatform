package edu.unicauca.aplimovil.triageu.data.remote

import com.russhwolf.settings.Settings
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

@Serializable
data class Session(
    val accessToken: String,
    val refreshToken: String,
    val expiresAtMillis: Long,
    val userId: String,
    val email: String
)

/** Autenticación con Supabase Auth (correo + contraseña) vía REST. */
class AuthManager(
    private val client: HttpClient,
    private val secure: Settings
) {
    private val _session = MutableStateFlow(loadSession())
    val session: StateFlow<Session?> = _session.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _infoMessage = MutableStateFlow<String?>(null)
    val infoMessage: StateFlow<String?> = _infoMessage.asStateFlow()

    val isSignedIn: Boolean get() = _session.value != null

    private val refreshMutex = Mutex()

    fun clearMessages() {
        _errorMessage.value = null
        _infoMessage.value = null
    }

    // ---- Acciones públicas ----

    suspend fun signIn(email: String, password: String) =
        authenticate("token?grant_type=password", email, password)

    suspend fun signUp(email: String, password: String) =
        authenticate("signup", email, password)

    suspend fun resetPassword(email: String) {
        val clean = email.trim()
        if (clean.isEmpty()) {
            _errorMessage.value = "Escribe tu correo para recuperar la contraseña."
            return
        }
        _isLoading.value = true
        clearMessages()
        try {
            val (body, status) = post("recover", SupabaseJson.encodeToString(mapOf("email" to clean)))
            if (status in 200..299) {
                _infoMessage.value = "Si el correo existe, te enviamos un enlace para restablecer la contraseña."
            } else {
                _errorMessage.value = translate(errorText(body))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _errorMessage.value = "No se pudo conectar. Revisa tu conexión."
        } finally {
            _isLoading.value = false
        }
    }

    fun signOut() {
        _session.value = null
        secure.remove(KEY_SESSION)
    }

    /** Devuelve un access token vigente (renovándolo si hace falta). */
    suspend fun validToken(): String? {
        val current = _session.value ?: return null
        if (current.expiresAtMillis - nowMs() > 60_000) return current.accessToken
        return refresh()
    }

    // ---- Internos ----

    @Serializable
    private data class TokenResponse(
        @SerialName("access_token") val accessToken: String? = null,
        @SerialName("refresh_token") val refreshToken: String? = null,
        @SerialName("expires_in") val expiresIn: Int? = null,
        val user: User? = null
    ) {
        @Serializable
        data class User(val id: String, val email: String? = null)
    }

    @Serializable
    private data class ErrorResponse(
        val msg: String? = null,
        val message: String? = null,
        @SerialName("error_description") val errorDescription: String? = null
    )

    private fun errorText(body: String): String? = try {
        val e = SupabaseJson.decodeFromString<ErrorResponse>(body)
        e.msg ?: e.message ?: e.errorDescription
    } catch (e: Exception) {
        null
    }

    private suspend fun post(path: String, json: String): Pair<String, Int> {
        val resp = client.post("${SupabaseConfig.url}/auth/v1/$path") {
            header("apikey", SupabaseConfig.anonKey)
            contentType(ContentType.Application.Json)
            setBody(json)
        }
        return resp.bodyAsText() to resp.status.value
    }

    private suspend fun authenticate(path: String, email: String, password: String) {
        val cleanEmail = email.trim()
        if (cleanEmail.isEmpty() || password.length < 6) {
            _errorMessage.value = "Escribe tu correo y una contraseña de al menos 6 caracteres."
            return
        }
        _isLoading.value = true
        clearMessages()
        try {
            val (body, status) = post(
                path,
                SupabaseJson.encodeToString(mapOf("email" to cleanEmail, "password" to password))
            )
            if (status !in 200..299) {
                _errorMessage.value = translate(errorText(body))
                return
            }
            val res = SupabaseJson.decodeFromString<TokenResponse>(body)
            val access = res.accessToken
            val refresh = res.refreshToken
            val user = res.user
            if (access == null || refresh == null || user == null) {
                _infoMessage.value = "Cuenta creada. Revisa tu correo para confirmarla y luego inicia sesión."
                return
            }
            store(
                Session(
                    accessToken = access,
                    refreshToken = refresh,
                    expiresAtMillis = nowMs() + (res.expiresIn ?: 3600) * 1000L,
                    userId = user.id.lowercase(),
                    email = user.email ?: cleanEmail
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _errorMessage.value = "No se pudo conectar. Revisa tu conexión."
        } finally {
            _isLoading.value = false
        }
    }

    private suspend fun refresh(): String? = refreshMutex.withLock {
        val current = _session.value ?: return null
        // Otro llamador pudo renovar mientras esperábamos el candado.
        if (current.expiresAtMillis - nowMs() > 60_000) return current.accessToken
        try {
            val (body, status) = post(
                "token?grant_type=refresh_token",
                SupabaseJson.encodeToString(mapOf("refresh_token" to current.refreshToken))
            )
            if (status !in 200..299) {
                // Refresh token inválido: sesión caducada.
                if (status == 400 || status == 401 || status == 403) signOut()
                return null
            }
            val res = SupabaseJson.decodeFromString<TokenResponse>(body)
            val access = res.accessToken ?: return null
            val newRefresh = res.refreshToken ?: return null
            store(
                current.copy(
                    accessToken = access,
                    refreshToken = newRefresh,
                    expiresAtMillis = nowMs() + (res.expiresIn ?: 3600) * 1000L
                )
            )
            access
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Sin red: usa el token actual por si aún sirve.
            current.accessToken
        }
    }

    private fun store(s: Session) {
        _session.value = s
        secure.putString(KEY_SESSION, SupabaseJson.encodeToString(s))
    }

    private fun loadSession(): Session? {
        val raw = secure.getStringOrNull(KEY_SESSION) ?: return null
        return try {
            SupabaseJson.decodeFromString<Session>(raw)
        } catch (e: Exception) {
            null
        }
    }

    private fun nowMs() = Clock.System.now().toEpochMilliseconds()

    private fun translate(message: String?): String {
        val m = message?.lowercase() ?: return "Ocurrió un error. Inténtalo de nuevo."
        return when {
            m.contains("invalid login") -> "Correo o contraseña incorrectos."
            m.contains("already registered") -> "Ese correo ya tiene una cuenta."
            m.contains("not confirmed") -> "Confirma tu correo antes de iniciar sesión."
            m.contains("password") -> "La contraseña debe tener al menos 6 caracteres."
            else -> message
        }
    }

    private companion object {
        const val KEY_SESSION = "session"
    }
}
