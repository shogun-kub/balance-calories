package com.balancecalories.core.energy

/**
 * When the app is allowed to change the daily target without asking.
 *
 * The default is to **ask**, and that is a product decision rather than a cautious one: a target that moves on its
 * own is a number the person did not agree to, and the first time it moves for a reason they cannot see, they stop
 * trusting every other number on the screen. So the automation is opt-in, and opting in is a single switch on the
 * screen that shows the evidence.
 *
 * What is *not* negotiable, whatever the switch says: an estimate below [TdeeConfidence.MEDIUM] never moves a
 * target. A low-confidence estimate describes how much was logged, not how the person metabolises.
 */
object TdeeApplyPolicy {

    /**
     * Whether the target may be updated with no click from the user.
     *
     * [autoApplyEnabled] is the person's stored preference. The confidence gate applies in both directions: with
     * the switch **on**, only a medium or high estimate is applied; with it **off**, nothing is applied without a
     * tap.
     */
    fun shouldApplyAutomatically(
        confidence: TdeeConfidence,
        autoApplyEnabled: Boolean,
    ): Boolean = autoApplyEnabled && confidence >= TdeeConfidence.MEDIUM

    /** Whether an estimate is worth offering as a button at all. */
    fun isOfferable(confidence: TdeeConfidence): Boolean = confidence >= TdeeConfidence.LOW
}
