package com.balancecalories.data.off

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Keeps our requests to at most one per [minIntervalMs].
 *
 * OpenFoodFacts publishes **15 product requests a minute per IP** (docs/02-research.md §3.2) but sends no
 * `X-RateLimit-*` headers with a response — verified against the live API on 2026-11-08 — so there is
 * nothing to react to and staying under the limit is our job. One request per six seconds is 10/min:
 * comfortably below the quota, and invisible next to a human scanning one barcode.
 */
class RequestThrottle(
    private val minIntervalMs: Long = DEFAULT_MIN_INTERVAL_MS,
    private val now: () -> Long = System::currentTimeMillis,
    private val sleeper: suspend (Long) -> Unit = { delay(it) },
) {

    private val mutex = Mutex()
    private var lastRequestAtMs = 0L

    suspend fun awaitSlot() {
        mutex.withLock {
            val current = now()
            val elapsed = current - lastRequestAtMs
            if (lastRequestAtMs != 0L && elapsed < minIntervalMs) {
                sleeper(minIntervalMs - elapsed)
            }
            lastRequestAtMs = now()
        }
    }

    companion object {
        /** 10 requests a minute against a published quota of 15: slow on purpose, still imperceptible. */
        const val DEFAULT_MIN_INTERVAL_MS = 6_000L
    }
}
