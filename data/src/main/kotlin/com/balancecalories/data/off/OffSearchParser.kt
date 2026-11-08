package com.balancecalories.data.off

import org.json.JSONObject

/**
 * Parses OpenFoodFacts **text search** results.
 *
 * The barcode endpoint and the search endpoint are different services with different shapes, measured on
 * 2026-11-08:
 *
 * | | barcode lookup | text search |
 * |---|---|---|
 * | host | `world.openfoodfacts.org` | `search.openfoodfacts.org` |
 * | path | `/api/v3/product/{code}.json` | `/search?q=…` |
 * | product holder | `product` (object) | `hits` (array) |
 * | count | absent | `count`, `page_size`, `page_count` |
 * | latency | ~1 s | ~0.5 s |
 *
 * Why the other endpoint was rejected: the legacy `/cgi/search.pl` did not answer within 40 s on the same
 * query where this one returned in 0.5 s, so it is unusable inside a UI interaction (docs/02-research.md
 * §3.1.2).
 *
 * A hit is the same object shape as a looked-up product, which is why parsing is delegated to
 * [OffResponseParser.parseProduct] rather than duplicated. Two filters are applied here, and both are
 * deliberate:
 *
 * - **A hit without a code is dropped.** The code is the product's identity; a result that cannot be
 *   stored (and would collide with another codeless product) is not a result we can offer.
 * - **A hit without energy is kept.** It is shown and explained, not hidden: "found, but nobody recorded
 *   calories" is an answer the user can act on, while silence looks like a network failure.
 */
object OffSearchParser {

    fun parse(httpStatus: Int, body: String?): OffSearch {
        if (httpStatus != 200) return OffSearch.Unavailable("HTTP $httpStatus")
        if (body.isNullOrBlank()) return OffSearch.Unavailable("empty response body")

        val root = runCatching { JSONObject(body) }.getOrElse {
            return OffSearch.Unavailable("unreadable JSON: ${it.message}")
        }

        val hits = root.optJSONArray("hits")
            ?: return OffSearch.Unavailable("a search response without a hits array")

        val products = ArrayList<OffProduct>(hits.length())
        for (index in 0 until hits.length()) {
            val hit = hits.optJSONObject(index) ?: continue
            val product = OffResponseParser.parseProduct(hit)
            if (product.code.isNotBlank()) products += product
        }

        // count is the total across all pages, not the number of hits in this one; a UI that shows the
        // page length as "found N" would understate a big result set.
        val total = if (root.has("count") && !root.isNull("count")) root.optInt("count") else products.size
        return OffSearch.Found(totalCount = total, products = products)
    }
}

/** The outcome of a text search, with the failure modes the UI has to tell apart. */
sealed interface OffSearch {

    /**
     * [totalCount] is how many products match in total; [products] is this page.
     *
     * [products] may be shorter than requested, and may be empty when [totalCount] is not — every hit
     * lacked a code, which is rare but possible.
     */
    data class Found(val totalCount: Int, val products: List<OffProduct>) : OffSearch {
        val isEmpty: Boolean get() = products.isEmpty()
    }

    /** The query itself is not worth sending: blank, or one character, which matches half the database. */
    data class Invalid(val query: String) : OffSearch

    /** Network trouble or a server error. The local list is still usable, which is the point. */
    data class Unavailable(val reason: String) : OffSearch
}
