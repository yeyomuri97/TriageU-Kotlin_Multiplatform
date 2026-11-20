package edu.unicauca.aplimovil.triageu.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.timeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString

/** Respuesta HTTP no exitosa del servidor (equivale al NSError dominio "Supabase"). */
class SupabaseHttpException(val code: Int, message: String) : Exception(message)

/** Un único HttpClient por app; el motor (OkHttp / Darwin) lo aporta cada plataforma. */
fun createHttpClient(): HttpClient = HttpClient {
    install(HttpTimeout) {
        requestTimeoutMillis = 20_000
        connectTimeoutMillis = 20_000
    }
}

/** Cliente mínimo de Supabase (PostgREST). Las escrituras pasan por `SyncQueue`. */
class RemoteApi(
    private val client: HttpClient,
    private val auth: AuthManager
) {
    private suspend fun token(): String {
        if (!SupabaseConfig.isConfigured) throw SupabaseHttpException(401, "Supabase no configurado")
        return auth.validToken() ?: throw SupabaseHttpException(401, "Sin sesión")
    }

    private suspend fun send(
        table: Table,
        method: HttpMethod,
        configure: HttpRequestBuilder.() -> Unit = {}
    ): String {
        val bearer = token()
        val resp = client.request {
            this.method = method
            url("${SupabaseConfig.url}/rest/v1/${table.path}")
            header("apikey", SupabaseConfig.anonKey)
            header(HttpHeaders.Authorization, "Bearer $bearer")
            contentType(ContentType.Application.Json)
            configure()
        }
        val text = resp.bodyAsText()
        if (!resp.status.isSuccess()) throw SupabaseHttpException(resp.status.value, text)
        return text
    }

    suspend fun pushActivity(dto: ActivityDto) =
        push(Table.ACTIVITIES, SupabaseJson.encodeToString(dto))

    suspend fun pushSubject(dto: SubjectDto) =
        push(Table.SUBJECTS, SupabaseJson.encodeToString(dto))

    private suspend fun push(table: Table, body: String) {
        send(table, HttpMethod.Post) {
            header("Prefer", "resolution=merge-duplicates,return=minimal")
            setBody(body)
        }
    }

    suspend fun delete(table: Table, id: String) {
        send(table, HttpMethod.Delete) {
            url { parameters.append("id", "eq.${id.lowercase()}") }
        }
    }

    suspend fun fetchActivities(): List<ActivityDto> {
        val text = send(Table.ACTIVITIES, HttpMethod.Get) { url { parameters.append("select", "*") } }
        return SupabaseJson.decodeFromString(ListSerializer(ActivityDto.serializer()), text)
    }

    suspend fun fetchSubjects(): List<SubjectDto> {
        val text = send(Table.SUBJECTS, HttpMethod.Get) { url { parameters.append("select", "*") } }
        return SupabaseJson.decodeFromString(ListSerializer(SubjectDto.serializer()), text)
    }
}
