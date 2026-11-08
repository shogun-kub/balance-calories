package com.balancecalories.data.off

import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The client's judgement calls — validate before sending, never retry a 404, retry transient failures a
 * bounded number of times, always identify ourselves — tested through a scripted transport.
 */
class OpenFoodFactsClientTest {

    private class ScriptedTransport(
        private val script: List<() -> OffHttpResponse>,
    ) : OffTransport {

        val requests = mutableListOf<Pair<String, Map<String, String>>>()
        private var index = 0

        override suspend fun get(url: String, headers: Map<String, String>): OffHttpResponse {
            requests += url to headers
            val step = script.getOrElse(index) { { OffHttpResponse(500, "script exhausted") } }
            index++
            return step()
        }
    }

    /** Convenience for a scripted answer. */
    private fun reply(status: Int, body: String?): () -> OffHttpResponse = { OffHttpResponse(status, body) }

    /** Convenience for a scripted transport failure. */
    private fun failure(message: String): () -> OffHttpResponse = { throw IOException(message) }

    private fun client(transport: OffTransport, maxAttempts: Int = 3) = OpenFoodFactsClient(
        transport = transport,
        throttle = RequestThrottle(minIntervalMs = 0),
        maxAttempts = maxAttempts,
        sleeper = { },
    )

    private val foundBody = """
        {"status":1,"product":{"code":"4012345678901","product_name":"Test bar",
        "nutriments":{"energy-kcal_100g":200}}}
    """.trimIndent()

    @Test
    fun aNonBarcodeNeverLeavesThePhone() = runBlocking {
        val transport = ScriptedTransport(emptyList())

        val result = client(transport).lookup("abc123")

        assertTrue("expected Invalid, got $result", result is OffLookup.Invalid)
        assertEquals("no request should have been made", 0, transport.requests.size)
    }

    @Test
    fun aKnownProductIsFoundOnTheFirstRequest() = runBlocking {
        val transport = ScriptedTransport(listOf(reply(200, foundBody)))

        val result = client(transport).lookup("4012345678901")

        val product = (result as OffLookup.Found).product
        assertEquals("Test bar", product.name)
        assertEquals(200.0, product.energy!!.kcal, 1e-9)
        assertEquals(1, transport.requests.size)
    }

    @Test
    fun theRequestIdentifiesTheAppAndAsksOnlyForNeededFields() = runBlocking {
        val transport = ScriptedTransport(listOf(reply(200, foundBody)))

        client(transport).lookup("4012345678901")

        val (url, headers) = transport.requests.single()
        assertTrue("URL should carry the barcode: $url", url.contains("/api/v3/product/4012345678901.json"))
        assertTrue("URL should restrict fields: $url", url.contains("fields="))
        assertEquals(OpenFoodFactsClient.DEFAULT_USER_AGENT, headers["User-Agent"])
        assertEquals("application/json", headers["Accept"])
    }

    @Test
    fun notFoundIsNotRetried() = runBlocking {
        val transport = ScriptedTransport(
            listOf(reply(404, """{"status":0,"status_verbose":"product not found"}""")),
        )

        val result = client(transport).lookup("2573468086162")

        assertEquals(OffLookup.NotFound, result)
        assertEquals("a 404 is an answer, not a failure to retry", 1, transport.requests.size)
    }

    @Test
    fun aTransientServerErrorIsRetriedAndCanSucceed() = runBlocking {
        val transport = ScriptedTransport(
            listOf(
                reply(503, "busy"),
                reply(200, foundBody),
            ),
        )

        val result = client(transport).lookup("4012345678901")

        assertTrue(result is OffLookup.Found)
        assertEquals(2, transport.requests.size)
    }

    @Test
    fun retriesAreBoundedAndEndAsUnavailable() = runBlocking {
        val busy = List(5) { reply(503, "busy") }
        val transport = ScriptedTransport(busy)

        val result = client(transport, maxAttempts = 3).lookup("4012345678901")

        assertTrue("expected Unavailable, got $result", result is OffLookup.Unavailable)
        assertEquals("must stop at maxAttempts", 3, transport.requests.size)
    }

    @Test
    fun aNetworkFailureIsRetried() = runBlocking {
        val transport = ScriptedTransport(
            listOf(
                failure("connection reset"),
                reply(200, foundBody),
            ),
        )

        val result = client(transport).lookup("4012345678901")

        assertTrue(result is OffLookup.Found)
        assertEquals(2, transport.requests.size)
    }

    @Test
    fun aClientErrorOtherThan404IsNotRetried() = runBlocking {
        val transport = ScriptedTransport(listOf(reply(403, "forbidden")))

        val result = client(transport).lookup("4012345678901")

        assertTrue(result is OffLookup.Unavailable)
        assertEquals("retrying our own bad request repeats the mistake", 1, transport.requests.size)
    }

    @Test
    fun throttlingSpacesRequestsOut() {
        var clock = 1_000L
        val slept = mutableListOf<Long>()
        val throttle = RequestThrottle(
            minIntervalMs = 6_000,
            now = { clock },
            sleeper = { waited -> slept += waited; clock += waited },
        )

        assertEquals(0, slept.size)
        runBlocking { throttle.awaitSlot() }
        assertEquals("first request never waits", 0, slept.size)

        clock += 1_000 // one second later, another request wants to go
        runBlocking { throttle.awaitSlot() }
        assertEquals(listOf(5_000L), slept)
    }

    @Test
    fun barcodeNormalisationAcceptsRealCodesAndRejectsJunk() {
        assertEquals("4012345678901", OpenFoodFactsClient.normalizeBarcode("4012345678901"))
        assertEquals("4012345678901", OpenFoodFactsClient.normalizeBarcode(" 4012-3456-78901 "))
        assertEquals("12345678", OpenFoodFactsClient.normalizeBarcode("12345678"))
        assertEquals(null, OpenFoodFactsClient.normalizeBarcode("1234567")) // too short
        assertEquals(null, OpenFoodFactsClient.normalizeBarcode("123456789012345")) // too long
        assertEquals(null, OpenFoodFactsClient.normalizeBarcode("40123456789O1")) // letter O
        assertEquals(null, OpenFoodFactsClient.normalizeBarcode(""))
    }
}
