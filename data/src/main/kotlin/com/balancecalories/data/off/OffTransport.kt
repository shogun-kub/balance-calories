package com.balancecalories.data.off

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** One HTTP answer, reduced to what the client needs to decide what happened. */
data class OffHttpResponse(val statusCode: Int, val body: String?)

/**
 * The narrow HTTP seam.
 *
 * It exists so the client's retry, 404 and rate-limit behaviour can be tested without a network — the
 * tests drive a scripted transport and assert on the sequence of requests. A test that needs the real
 * OpenFoodFacts host is not a unit test, it is a weather report.
 */
interface OffTransport {
    suspend fun get(url: String, headers: Map<String, String>): OffHttpResponse
}

/** Plain `HttpURLConnection`: three GET requests do not justify an HTTP client dependency. */
class HttpUrlOffTransport(
    private val connectTimeoutMs: Int = 10_000,
    private val readTimeoutMs: Int = 20_000,
) : OffTransport {

    override suspend fun get(url: String, headers: Map<String, String>): OffHttpResponse =
        withContext(Dispatchers.IO) {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                instanceFollowRedirects = true
                // Identity encoding keeps the body readable in a logcat trace if something goes wrong.
                setRequestProperty("Accept-Encoding", "identity")
                headers.forEach { (name, value) -> setRequestProperty(name, value) }
            }
            try {
                val status = connection.responseCode
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val body = stream?.bufferedReader()?.use { it.readText() }
                OffHttpResponse(statusCode = status, body = body)
            } catch (e: IOException) {
                throw e
            } finally {
                connection.disconnect()
            }
        }
}
