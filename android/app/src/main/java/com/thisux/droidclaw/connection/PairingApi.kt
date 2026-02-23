package com.thisux.droidclaw.connection

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ClaimRequest(val code: String)

@Serializable
data class ClaimResponse(val apiKey: String, val wsUrl: String)

@Serializable
data class ClaimError(val error: String)

object PairingApi {
    private val json = Json { ignoreUnknownKeys = true }

    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(json)
        }
    }

    /**
     * Claim a 6-digit pairing code from the server.
     * Returns the API key and WebSocket URL on success.
     */
    suspend fun claim(serverBaseUrl: String, code: String): Result<ClaimResponse> {
        return try {
            // Convert wss:// URL to https:// for the REST call
            val httpBase = serverBaseUrl
                .replace("wss://", "https://")
                .replace("ws://", "http://")
                .trimEnd('/')

            val response = client.post("$httpBase/pairing/claim") {
                contentType(ContentType.Application.Json)
                setBody(ClaimRequest(code = code))
            }

            val body = response.bodyAsText()

            if (response.status.value in 200..299) {
                val result = json.decodeFromString<ClaimResponse>(body)
                Result.success(result)
            } else {
                val error = try {
                    json.decodeFromString<ClaimError>(body).error
                } catch (_: Exception) {
                    "Invalid or expired code"
                }
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Connection failed: ${e.message}"))
        }
    }
}
