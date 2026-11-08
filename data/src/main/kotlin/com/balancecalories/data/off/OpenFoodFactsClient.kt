package com.balancecalories.data.off

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.net.URLEncoder

/**
 * Talks to OpenFoodFacts: barcode lookup and text search.
 *
 * The response contracts are documented in [OffResponseParser] and [OffSearchParser]; this class adds the
 * parts that need judgement:
 *
 * - **Validation first.** A barcode that is not 8–14 digits never leaves the phone, and a search query
 *   shorter than [MIN_QUERY_LENGTH] characters never does either.
 * - **404 is an answer, not an error.** It means "OFF does not have this product", which the caller may
 *   cache so the same miss is not repeated on every scan.
 * - **Retries are bounded and only for transient failures** (network errors, 429, 5xx). A 404 is never
 *   retried, and neither is a malformed response — repeating an unchanged question cannot change the
 *   answer.
 * - **The User-Agent always identifies us.** A request without it is a bug, per AGENTS.md.
 * - **One request at a time, six seconds apart** ([RequestThrottle]), shared by both endpoints, because
 *   the published quota is per client and not per endpoint.
 */
class OpenFoodFactsClient(
    private val transport: OffTransport,
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val apiVersion: String = DEFAULT_API_VERSION,
    private val searchUrl: String = DEFAULT_SEARCH_URL,
    private val userAgent: String = DEFAULT_USER_AGENT,
    private val throttle: RequestThrottle = RequestThrottle(),
    private val maxAttempts: Int = 3,
    private val backoffBaseMs: Long = 700,
    private val sleeper: suspend (Long) -> Unit = { delay(it) },
) {

    /** Requests actually made, for the debug screen and tests. */
    var requestCount: Int = 0
        private set

    /** Looks one barcode up. */
    suspend fun lookup(rawBarcode: String): OffLookup {
        val barcode = normalizeBarcode(rawBarcode) ?: return OffLookup.Invalid(rawBarcode)
        val url = "$baseUrl/api/$apiVersion/product/$barcode.json?fields=$FIELDS"

        return when (val outcome = request(url)) {
            is Outcome.GaveUp -> OffLookup.Unavailable(outcome.reason)
            is Outcome.Answered ->
                if (outcome.status == 200 || outcome.status == 404) {
                    OffResponseParser.parse(outcome.status, outcome.body)
                } else {
                    // A 4xx that is not 404 is our fault; retrying repeats the mistake.
                    OffLookup.Unavailable("HTTP ${outcome.status}")
                }
        }
    }

    /**
     * Searches products by name.
     *
     * Pages are one-based here because that is how the endpoint counts; [pageSize] is capped at
     * [MAX_PAGE_SIZE] so a caller cannot ask for the whole database by accident.
     */
    suspend fun search(rawQuery: String, page: Int = 1, pageSize: Int = DEFAULT_PAGE_SIZE): OffSearch {
        val query = rawQuery.trim()
        if (query.length < MIN_QUERY_LENGTH) return OffSearch.Invalid(rawQuery)
        val size = pageSize.coerceIn(1, MAX_PAGE_SIZE)
        val url = "$searchUrl/search?q=${encode(query)}&page=${page.coerceAtLeast(1)}&page_size=$size&fields=$FIELDS"

        return when (val outcome = request(url)) {
            is Outcome.GaveUp -> OffSearch.Unavailable(outcome.reason)
            is Outcome.Answered -> OffSearchParser.parse(outcome.status, outcome.body)
        }
    }

    /** One GET with the shared retry and throttle policy. */
    private suspend fun request(url: String): Outcome {
        var lastReason = "no attempt was made"
        for (attempt in 0 until maxAttempts) {
            throttle.awaitSlot()
            requestCount++

            val response = try {
                transport.get(url, headers())
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                lastReason = "network error: ${error.message ?: error::class.java.simpleName}"
                if (attempt == maxAttempts - 1) return Outcome.GaveUp(lastReason)
                sleeper(backoffDelayMs(attempt))
                continue
            }

            val retryable = response.statusCode == 429 || response.statusCode in 500..599
            if (retryable) {
                lastReason = "HTTP ${response.statusCode}"
                if (attempt == maxAttempts - 1) return Outcome.GaveUp(lastReason)
                sleeper(backoffDelayMs(attempt))
                continue
            }

            return Outcome.Answered(response.statusCode, response.body)
        }
        return Outcome.GaveUp(lastReason)
    }

    private sealed interface Outcome {
        data class Answered(val status: Int, val body: String?) : Outcome
        data class GaveUp(val reason: String) : Outcome
    }

    private fun headers(): Map<String, String> = mapOf(
        "User-Agent" to userAgent,
        "Accept" to "application/json",
    )

    private fun backoffDelayMs(attempt: Int): Long = backoffBaseMs * (attempt + 1)

    /** `q` is the user's own text, possibly Cyrillic; percent-encoding it is not optional. */
    private fun encode(query: String): String = URLEncoder.encode(query, "UTF-8")

    companion object {
        const val DEFAULT_BASE_URL = "https://world.openfoodfacts.org"

        /**
         * Text search lives on its own host. Measured 2026-11-08: 0.5 s here against a legacy
         * `/cgi/search.pl` that had not answered after 40 s (docs/02-research.md §3.1.2).
         */
        const val DEFAULT_SEARCH_URL = "https://search.openfoodfacts.org"

        /** v3 is the endpoint OpenFoodFacts recommends; the parser also understands v2's shape. */
        const val DEFAULT_API_VERSION = "v3"
        const val DEFAULT_USER_AGENT = "BalanceCalories/0.1.0 (Android)"

        /** Two characters is the shortest query that is a search rather than a database dump. */
        const val MIN_QUERY_LENGTH = 2
        const val DEFAULT_PAGE_SIZE = 20
        const val MAX_PAGE_SIZE = 50

        /** Asking for the fields we use instead of the whole product keeps the payload in the low kilobytes. */
        const val FIELDS =
            "code,product_name,product_name_ru,brands,quantity,serving_size,serving_quantity," +
                "serving_quantity_unit,nutriments,image_url,image_front_url,last_modified_t," +
                "nutrition_data_per,no_nutrition_data"

        /** Delegates to [com.balancecalories.core.food.Barcode]; kept here for callers that already have it. */
        fun normalizeBarcode(raw: String): String? = com.balancecalories.core.food.Barcode.normalize(raw)
    }
}
