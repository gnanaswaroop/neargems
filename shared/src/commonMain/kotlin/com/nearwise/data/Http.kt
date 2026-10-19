package com.nearwise.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess

/** The one HTTP client the app uses, on every platform. Image loading shares it. */
fun nearwiseHttpClient(engine: HttpClientEngineFactory<*>): HttpClient =
    HttpClient(engine) {
        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            requestTimeoutMillis = 20_000
        }
        // Wikimedia answers 403 to requests that do not identify the app, so every request carries this.
        defaultRequest { headers.append(HttpHeaders.UserAgent, USER_AGENT) }
    }

/** Thin wrapper over Ktor for the two public APIs used here: a GET and a form POST. */
class Http(private val client: HttpClient) {

    suspend fun get(url: String): String = checked(url, client.get(url))

    suspend fun postForm(url: String, form: String): String =
        checked(
            url,
            client.post(url) {
                contentType(ContentType.Application.FormUrlEncoded)
                setBody(form)
            },
        )

    private suspend fun checked(url: String, response: HttpResponse): String {
        check(response.status.isSuccess()) { "HTTP ${response.status.value} from ${url.substringAfter("://").substringBefore('/')}" }
        return response.bodyAsText()
    }
}

private const val USER_AGENT = "Nearwise/0.1 (personal project)"
