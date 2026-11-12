package com.balancecalories.app

/**
 * What the app knows about the stored active profile.
 *
 * Three states, not one nullable id, because `null` used to mean both "nothing is stored" and "the read has
 * not answered yet". On a cold start the profiles query can answer first, and the repair below then treated
 * the *unread* setting as an *absent* one, overwriting a chosen profile with the first row in the table.
 * On the owner's phone that arrival order put him on `Test` (the older profile) after he had explicitly
 * chosen his own (P-39).
 */
internal sealed interface ActiveProfileRead {
    /** The settings read has not answered yet. Nothing may be decided, and nothing may be written. */
    data object Loading : ActiveProfileRead

    /** The read answered: [id] is the stored profile, or null when the phone has never stored one. */
    data class Loaded(val id: Long?) : ActiveProfileRead
}

/**
 * The active profile id to keep: the stored one when it still exists, otherwise the first available.
 *
 * Deliberately free of Android and of the database, so the repair rule has a test: the bug this replaces was
 * a repair effect that could not tell "unread" from "absent", and a device-only test would never catch it.
 */
internal fun repairedActiveId(stored: Long?, available: List<Long>): Long? {
    if (available.isEmpty()) return null
    return stored?.takeIf { it in available } ?: available.first()
}
