package com.balancecalories.data.off

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/**
 * Looks products up by barcode on OpenFoodFacts.
 *
 * The response contract is documented in [OffResponseParser]; this class adds the parts that need
 * judgement:
 *
 * - **Validation first.** A barcode that is not 8–14 digits never leaves the phone.
 * - **404 is an answer, not an error.** It means "OFF does not have this product", which the caller may
 *   cache so the same miss is not repeated on every scan.
 * - **Retries are bounded and only for transient failures** (network errors, 429, 5xx). A 404 is never
 *   retried, and neither is a malformed response — repeating an unchanged question cannot change the
 *   answer.
 * - **The User-Agent always identifies us.** A request without it is a bug, per AGENTS.md.
 */
class OpenFoodFactsClient(
    private val transport: OffTransport,
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val apiVersion: String = DEFAULT_API_VERSION,
    private val userAgent: String = DEFAULT_USER_AGENT,
    private val throttle: RequestThrottle = RequestThrottle(),
    private val maxAttempts: Int = 3,
    private val backoffBaseMs: Long = 700,
    private val sleeper: suspend (Long) -> Unit = { delay(it) },
) {

    /** Requests actually made, for the debug screen and tests. */
    var requestCount: Int = 0
        private set

    suspend fun lookup(rawBarcode: String): OffLookup {
        val barcode = normalizeBarcode(rawBarcode) ?: return OffLookup.Invalid(rawBarcode)
        val url = "$baseUrl/api/$apiVersion/product/$barcode.json?fields=$FIELDS"

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
                if (attempt == maxAttempts - 1) return OffLookup.Unavailable(lastReason)
                sleeper(backoffDelayMs(attempt))
                continue
            }

            when {
                response.statusCode == 200 || response.statusCode == 404 ->
                    return OffResponseParser.parse(response.statusCode, response.body)

                response.statusCode == 429 || response.statusCode in 500..599 -> {
                    lastReason = "HTTP ${response.statusCode}"
                    if (attempt == maxAttempts - 1) return OffLookup.Unavailable(lastReason)
                    sleeper(backoffDelayMs(attempt))
                }

                // 4xx that is not 404 and not 429: our request is wrong, retrying repeats the mistake.
                else -> return OffLookup.Unavailable("HTTP ${response.statusCode}")
            }
        }
        return OffLookup.Unavailable(lastReason)
    }

    private fun headers(): Map<String, String> = mapOf(
        "User-Agent" to userAgent,
        "Accept" to "application/json",
    )

    private fun backoffDelayMs(attempt: Int): Long = backoffBaseMs * (attempt + 1)

    companion object {
        const val DEFAULT_BASE_URL = "https://world.openfoodfacts.org"

        /** v3 is the endpoint OpenFoodFacts recommends; the parser also understands v2's shape. */
        const val DEFAULT_API_VERSION = "v3"
        const val DEFAULT_USER_AGENT = "BalanceCalories/0.1.0 (Android)"

        /** Asking for six fields instead of the whole product keeps the payload in the low kilobytes. */
        const val FIELDS =
            "code,product_name,product_name_ru,brands,quantity,serving_size,serving_quantity," +
                "serving_quantity_unit,nutriments,image_url,image_front_url,last_modified_t," +
                "nutrition_data_per,no_nutrition_data"

        private const val MIN_LENGTH = 8
        private const val MAX_LENGTH = 14

        /** Digits only, 8–14 long; anything else is not a retail barcode and is rejected locally. */
        fun normalizeBarcode(raw: String): String? {
            val trimmed = raw.trim().replace(" ", "").replace("-", "")
            if (trimmed.length !in MIN_LENGTH..MAX_LENGTH) return null
            if (!trimmed.all { it in '0'..'9' }) return null
            return trimmed
        }
    }
}
