package com.neres.composeaplication.data

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.util.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json

@Serializable
data class UsuarioRecord(
    val Id: Int? = null,
    val user: String? = null,
    val password: String? = null,
    val status: String? = null,
    @SerialName("CODFUNC") val codFunc: Int? = null
)

@Serializable
data class UsuariosResponse(
    val list: List<UsuarioRecord> = emptyList()
)

object NocoDBApi {

    private const val BASE_URL = ""
    private const val TOKEN = ""

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(json)
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 15_000
        }
        install(Logging) {
            level = LogLevel.INFO
            logger = Logger.DEFAULT
        }
        defaultRequest {
            header("xc-token", TOKEN)
            accept(ContentType.Application.Json)
        }
    }

    suspend fun autenticarUsuario(user: String, password: String): UsuarioRecord? =
        runCatching {
            val whereExpression = buildString {
                append("(user,eq,")
                append(user.encodeURLQueryComponent())
                append(")")
                append("~and")
                append("(password,eq,")
                append(password.encodeURLQueryComponent())
                append(")")
            }

            val httpResponse = client.get("$BASE_URL/records") {
                parameter("where", whereExpression)
                parameter("limit", 1)
            }

            if (!httpResponse.status.isSuccess()) {
                println("⚠️ Erro HTTP ao autenticar: ${httpResponse.status}")
                println("⚠️ Corpo: ${httpResponse.bodyAsText()}")
                return@runCatching null
            }

            val contentType = httpResponse.contentType()
            if (contentType?.match(ContentType.Application.Json) != true) {
                println("⚠️ Resposta não é JSON: ${contentType ?: "desconhecido"}")
                return@runCatching null
            }

            val payload = httpResponse.bodyAsText()
            val response = json.decodeFromString<UsuariosResponse>(payload)

            response.list.firstOrNull()
        }.onFailure {
            println("⚠️ Erro ao autenticar usuário: ${it.message}")
        }.getOrNull()

    suspend fun listarUsuarios(limit: Int = 100): List<UsuarioRecord> =
        runCatching {
            val httpResponse = client.get("$BASE_URL/records") {
                parameter("limit", limit)
            }

            if (!httpResponse.status.isSuccess()) {
                println("⚠️ listarUsuarios: HTTP ${httpResponse.status}")
                println("⚠️ Corpo: ${httpResponse.bodyAsText()}")
                return@runCatching emptyList()
            }

            if (httpResponse.contentType()?.match(ContentType.Application.Json) != true) {
                println("⚠️ listarUsuarios: conteúdo não JSON (${httpResponse.contentType()})")
                return@runCatching emptyList()
            }

            val payload = httpResponse.bodyAsText()
            json.decodeFromString<UsuariosResponse>(payload).list
        }.onFailure {
            println("⚠️ listarUsuarios falhou: ${it.message}")
        }.getOrDefault(emptyList())
}
