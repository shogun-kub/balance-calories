package com.balancecalories.app.food

import com.balancecalories.app.AppContainer
import com.balancecalories.data.off.OffSearch

/**
 * What an Open Food Facts search is doing, so a screen can say it instead of staying silent.
 *
 * Shared by the products list and the recipe editor: the owner's point (2026-11-13) is that the two places must
 * behave the same, because both are "find a food", and a fallback that exists in one of them is a bug in the other.
 */
internal sealed interface RemoteState {
    data object Idle : RemoteState
    data object Searching : RemoteState
    data class Found(val search: OffSearch.Found) : RemoteState
    data class Failed(val reason: String) : RemoteState
}

/** Below this the query is not a search: OFF's own index returns half the database for one letter. */
internal const val MIN_REMOTE_QUERY = 2

/**
 * How long typing has to settle before the network is asked.
 *
 * The fallback is automatic now, so without a pause every keystroke of a five-letter word would be a request to a
 * free service that asks callers to be reasonable. Half a second is below the point where a person notices.
 */
internal const val REMOTE_DEBOUNCE_MS = 450L

/**
 * Runs the search and folds every failure into a state the screen can print.
 *
 * A network error is not an exception here on purpose: the local answer is already on screen and stays usable, so
 * the remote half reports "could not reach OPEN FOOD FACTS" beside a list that still works (hard rule 11).
 */
internal suspend fun searchOpenFoodFacts(container: AppContainer, asked: String): RemoteState {
    val answer = runCatching { container.offClient.search(asked) }.getOrElse {
        OffSearch.Unavailable(it.message ?: it::class.java.simpleName)
    }
    return when (answer) {
        is OffSearch.Found -> RemoteState.Found(answer)
        is OffSearch.Invalid -> RemoteState.Idle
        is OffSearch.Unavailable -> RemoteState.Failed(answer.reason)
    }
}
