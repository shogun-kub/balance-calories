package com.balancecalories.app.sync

import com.balancecalories.app.AppContainer
import com.balancecalories.core.energy.TdeeApplyPolicy
import com.balancecalories.core.energy.TargetCalculator
import com.balancecalories.core.profile.Profile
import com.balancecalories.core.time.DayWindow
import com.balancecalories.data.repo.AdaptiveTdeeRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Applies an adaptive target when the person has asked for that.
 *
 * One place for the rule, because two callers share it: the History screen when it opens, and the background sync
 * when it finishes. If they each implemented "should I apply this", they would eventually disagree, and the
 * symptom would be a target that changes on one path but not the other — the worst kind of inconsistency, because
 * nothing on screen would explain it.
 *
 * The confidence gate is inside [TdeeApplyPolicy], not here: a low-confidence estimate never moves a target even
 * with automation switched on.
 */
class AdaptiveTargetUpdater(private val container: AppContainer) {

    /**
     * Returns the target that was written, or null when nothing was applied.
     *
     * Null is the common case: the switch is off by default, and even when it is on the evidence is often thin.
     */
    suspend fun applyIfAllowed(
        profile: Profile,
        zone: ZoneId,
    ): Int? {
        val weight = container.body.latestWeightKg(profile.id) ?: return null
        val today = DayWindow.dateOf(Instant.now(), zone, profile.dayStartHour)
        val current = TargetCalculator.seed(
            profile = profile.toEnergyProfile(weight),
            goalRateKgPerWeek = profile.goalRateKgPerWeek,
            on = today,
        )
        val outcome: AdaptiveTdeeRepository.Outcome = container.adaptiveTdee.outcome(
            profile = profile,
            bmrKcal = current.bmrKcal,
            currentTargetKcal = current.kcal,
            zone = zone,
        )
        if (!outcome.changesTarget) return null

        val autoApply = container.settings.tdeeAutoApply(profile.id)
        if (!TdeeApplyPolicy.shouldApplyAutomatically(outcome.estimate.confidence, autoApply)) return null

        val applied = container.adaptiveTdee.apply(outcome, profile.id, today)
        return if (applied) outcome.proposedTargetKcal else null
    }

    /** The local day the app is in, so a caller that needs the same "today" does not compute its own. */
    fun today(profile: Profile, zone: ZoneId): LocalDate =
        DayWindow.dateOf(Instant.now(), zone, profile.dayStartHour)
}
