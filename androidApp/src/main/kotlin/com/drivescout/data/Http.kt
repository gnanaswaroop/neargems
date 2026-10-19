package com.drivescout.data

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess

/** Thin wrapper over Ktor for the two public APIs used here: a GET and a form POST. */
class Http(private val client: HttpClient) {

    suspend fun get(url: String): String =
        checked(url, client.get(url) { headers.append(HttpHeaders.UserAgent, USER_AGENT) })

    suspend fun postForm(url: String, form: String): String =
        checked(
            url,
            client.post(url) {
                headers.append(HttpHeaders.UserAgent, USER_AGENT)
                contentType(ContentType.Application.FormUrlEncoded)
                setBody(form)
            },
        )

    private suspend fun checked(url: String, response: HttpResponse): String {
        check(response.status.isSuccess()) { "HTTP ${response.status.value} from ${url.substringAfter("://").substringBefore('/')}" }
        return response.bodyAsText()
    }

    private companion object {
        const val USER_AGENT = "DriveScout/0.1 (personal project)"
    }
}
