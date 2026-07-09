package com.android.streamhub.feature.jellyfin.di

import okhttp3.Interceptor
import okhttp3.Response
import okio.Buffer

/**
 * Captures the most recent Jellyfin HTTP exchange (redacting the password field) so a failed
 * sign-in can show exactly what was sent/received. The SDK's own exceptions only carry a status
 * code with no response body, and this is someone else's server we have no log access to, so this
 * is the only way to see what's actually different about the app's real request versus a manual
 * curl/browser test that otherwise behaves fine against the same server.
 */
object JellyfinDebugInterceptor : Interceptor {
    @Volatile
    private var lastExchangeRef: String? = null
    val lastExchange: String? get() = lastExchangeRef

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val requestBody = request.body?.let { body ->
            val buffer = Buffer()
            body.writeTo(buffer)
            buffer.readUtf8()
        }.orEmpty().replace(Regex("\"Pw\"\\s*:\\s*\"[^\"]*\""), "\"Pw\":\"***\"")

        val response = chain.proceed(request)
        val responseBody = runCatching { response.peekBody(4096).string() }.getOrNull().orEmpty()

        lastExchangeRef = buildString {
            appendLine("${request.method} ${request.url}")
            appendLine("Request headers: ${request.headers}")
            if (requestBody.isNotBlank()) appendLine("Request body: $requestBody")
            appendLine("Response: ${response.code} ${response.message}")
            appendLine("Response headers: ${response.headers}")
            if (responseBody.isNotBlank()) appendLine("Response body: $responseBody")
        }

        return response
    }
}
