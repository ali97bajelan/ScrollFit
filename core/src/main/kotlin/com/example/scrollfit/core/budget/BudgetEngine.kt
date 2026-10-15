package com.example.scrollfit.core.budget

import com.example.scrollfit.core.data.*
import com.example.scrollfit.core.exercise.Exercise
import com.example.scrollfit.core.time.*

/**
 * Atomic policy operations; UI/monitoring code must use these rather than raw repository writes.
 * timelineId identifies a device boot and must remain stable through process restart.
 * Only the monitor owns this engine's chronological foreground interval stream.
 */
class BudgetEngine(
    private val store: DurableStore,
    private val wallClock: WallClock,
    private val monotonicClock: MonotonicClock,
    private val timelineId: String,
    private val preferences: suspend () -> AppPreferences,
) {
    init { require(timelineId.isNotBlank()) }

    private suspend fun <T> policy(beforeCommit: () -> Unit = {},
        block: suspend DataTransaction.(String, AppPreferences) -> T): T = store.transaction {
        // Read one preference snapshot and then date, after any suspended I/O.
        val prefs = preferences().let { it.copy(rewardRules = it.rewardRules.toMap()) }
        val day = wallClock.localDate().toString()
        cancelChallengesOutsideDay(day)
        val result = block(day, prefs)
        // Also reconcile read-only/early-return operations whose I/O crossed a date boundary.
        cancelChallengesOutsideDay(wallClock.localDate().toString())
        beforeCommit()
        result
    }

    suspend fun budget(packageName: String): Budget = policy { day, prefs ->
        readForCurrentDay(day) { current -> budgetFor(packageName, current, prefs) }
    }

    suspend fun spend(interval: UsageInterval): SpendResult = spendForPolicy(interval, observed = false)

    /** Monitor-only: flush a sampled segment across midnight into yesterday's retained rows. */
    suspend fun spendObserved(interval: UsageInterval): SpendResult {
        require(interval.endElapsedMs - interval.startElapsedMs <= 5000) { "Observed checkpoint exceeds five seconds" }
        return spendForPolicy(interval, observed = true)
    }

    private suspend fun spendForPolicy(interval: UsageInterval, observed: Boolean): SpendResult = policy { day, prefs ->
        require(interval.endElapsedMs <= monotonicClock.elapsedRealtimeMillis()) { "Future interval" }
        val through = cursor(timelineId)?.throughElapsedMs ?: 0
        if (interval.endElapsedMs <= through) return@policy SpendResult(disposition = SpendDisposition.DUPLICATE)
        // Even rejected/over-budget tails must not be re-applied after a settings edit.
        putCursor(UsageCursor(timelineId, interval.endElapsedMs))
        val yesterday = java.time.LocalDate.parse(day).minusDays(1).toString()
        if (interval.day != day && !(observed && interval.day == yesterday)) {
            return@policy SpendResult(disposition = SpendDisposition.WRONG_DAY)
        }
        val accountingDay = interval.day
        val budget = budgetFor(interval.packageName, accountingDay, prefs)
        if (!spendingDateStillValid(day, accountingDay, observed)) return@policy SpendResult(disposition = SpendDisposition.WRONG_DAY)
        if (budget.blockedBy == BudgetBlock.TARGET_UNAVAILABLE) {
            return@policy SpendResult(disposition = SpendDisposition.TARGET_UNAVAILABLE)
        }
        val elapsed = interval.endElapsedMs - maxOf(interval.startElapsedMs, through)
        val spent = minOf(elapsed, budget.usableMs)
        val base = minOf(spent, budget.baseRemainingMs)
        val earned = spent - base
        if (spent > 0) {
            val usage = usage(interval.packageName, accountingDay) ?: DailyUsage(interval.packageName, accountingDay)
            val combined = combined(accountingDay) ?: CombinedUsage(accountingDay)
            val session = session(interval.packageName, accountingDay) ?: SessionState(interval.packageName, accountingDay)
            if (!spendingDateStillValid(day, accountingDay, observed)) return@policy SpendResult(disposition = SpendDisposition.WRONG_DAY)
            putUsage(usage.copy(
                baseConsumedMs = Math.addExact(usage.baseConsumedMs, base),
                earnedConsumedMs = Math.addExact(usage.earnedConsumedMs, earned),
            ))
            putCombined(combined.copy(consumedMs = Math.addExact(combined.consumedMs, spent)))
            putSession(session.copy(consumedMs = Math.addExact(session.consumedMs, spent)))
        }
        SpendResult(base, earned, SpendDisposition.ACCOUNTED)
    }

    suspend fun offer(packageName: String?, exercise: Exercise, mode: ChallengeMode): OfferResult = policy { day, prefs ->
        readForCurrentDay(day) { current -> offerFor(packageName, exercise, mode, current, prefs) }
    }

    /** Caller must display this exact offer before calling start; changed terms require redisplay. */
    suspend fun start(id: String, displayed: RewardOffer): StartResult = policy { day, prefs ->
        val current = offerFor(displayed.packageName, displayed.exercise, displayed.mode, day, prefs)
        if (!stillToday(day)) {
            return@policy StartResult.OfferChanged(readForCurrentDay(wallClock.localDate().toString()) { currentDay ->
                offerFor(displayed.packageName, displayed.exercise, displayed.mode, currentDay, prefs)
            })
        }
        if (current != OfferResult.Available(displayed)) return@policy StartResult.OfferChanged(current)
        val challenge = Challenge(id, displayed.packageName, day, displayed.exercise, displayed.mode,
            displayed.repetitionsRequired, displayed.rewardMs, wallClock.epochMillis())
        insertChallenge(challenge) // Unique identity cannot replace an existing snapshot.
        StartResult.Started(challenge)
    }

    /** Only detector progress completes. Activity, ledger, wallet and status commit together. */
    suspend fun complete(id: String, repetitions: Int, activeDurationMs: Long = 0,
        eligible: () -> Boolean = { true }): CompletionResult {
        require(repetitions >= 0 && activeDurationMs >= 0)
        var completedDay: String? = null
        return try { policy(beforeCommit = {
            completedDay?.let { ensureCompletionEligible(it, eligible) }
        }) { day, prefs ->
            award(id)?.let { return@policy CompletionResult.Awarded(it) }
            val challenge = challenge(id) ?: return@policy CompletionResult.Refused(RewardBlock.MISSING)
            if (!stillToday(day)) return@policy CompletionResult.Refused(RewardBlock.INACTIVE)
            if (challenge.mode == ChallengeMode.EXERCISE_ONLY && challenge.status == ChallengeStatus.COMPLETED) {
                return@policy CompletionResult.ExerciseOnlyCompleted
            }
            if (challenge.status != ChallengeStatus.ACTIVE || challenge.day != day) {
                return@policy CompletionResult.Refused(RewardBlock.INACTIVE)
            }
            if (repetitions < challenge.repetitionsRequired) return@policy CompletionResult.Refused(RewardBlock.INCOMPLETE)
            if (challenge.mode == ChallengeMode.EXERCISE_ONLY) {
                completedDay = day
                ensureCompletionEligible(day, eligible)
                putChallenge(challenge.copy(status = ChallengeStatus.COMPLETED))
                recordCompletionActivity(challenge, repetitions, activeDurationMs)
                ensureCompletionEligible(day, eligible)
                return@policy CompletionResult.ExerciseOnlyCompleted
            }
            val packageName = requireNotNull(challenge.packageName)
            val budget = budgetFor(packageName, day, prefs)
            val usage = usage(packageName, day) ?: DailyUsage(packageName, day)
            if (!stillToday(day)) return@policy CompletionResult.Refused(RewardBlock.INACTIVE)
            if (budget.blockedBy == BudgetBlock.TARGET_UNAVAILABLE) return@policy CompletionResult.Refused(RewardBlock.TARGET_UNAVAILABLE)
            if (budget.combinedRemainingMs == 0L) return@policy CompletionResult.Refused(RewardBlock.COMBINED_CEILING)
            if (challenge.rewardMs > budget.awardCapacityMs) return@policy CompletionResult.Refused(RewardBlock.AWARD_CAP)
            ensureCompletionEligible(day, eligible)
            completedDay = day
            val result = Award(id, packageName, day, challenge.rewardMs, wallClock.epochMillis())
            insertAward(result)
            putUsage(usage.copy(awardedMs = Math.addExact(usage.awardedMs, challenge.rewardMs)))
            putChallenge(challenge.copy(status = ChallengeStatus.COMPLETED))
            recordCompletionActivity(challenge, repetitions, activeDurationMs)
            ensureCompletionEligible(day, eligible)
            CompletionResult.Awarded(result)
        } } catch (_: CompletionInvalidated) {
            CompletionResult.Refused(RewardBlock.INACTIVE)
        }
    }

    private class CompletionInvalidated : RuntimeException()
    private fun ensureCompletionEligible(day: String, eligible: () -> Boolean) {
        if (!eligible() || wallClock.localDate().toString() != day) throw CompletionInvalidated()
    }
    private suspend fun DataTransaction.recordCompletionActivity(challenge: Challenge, repetitions: Int, durationMs: Long) {
        insertActivity(ExerciseActivity("challenge:${challenge.id}", challenge.id, challenge.day,
            challenge.exercise, repetitions, durationMs, wallClock.epochMillis()))
    }

    private suspend fun DataTransaction.budgetFor(packageName: String, day: String, prefs: AppPreferences): Budget {
        val config = target(packageName)
        val usage = usage(packageName, day) ?: DailyUsage(packageName, day)
        val base = if (prefs.zeroBaseEnabled) 0L else remaining(config?.baseAllowanceMs ?: 0, usage.baseConsumedMs)
        val earned = usage.awardedMs - usage.earnedConsumedMs
        val capacity = remaining(config?.dailyAwardCapMs ?: 0, usage.awardedMs)
        val combined = remaining(prefs.combinedCeilingMs, combined(day)?.consumedMs ?: 0)
        // Saturate the wallet sum: usable time is still bounded by a positive Long ceiling.
        val wallet = if (base > Long.MAX_VALUE - earned) Long.MAX_VALUE else base + earned
        val block = when {
            config?.selected != true -> BudgetBlock.TARGET_UNAVAILABLE
            combined == 0L -> BudgetBlock.COMBINED_CEILING
            wallet == 0L -> BudgetBlock.APP_BUDGET
            else -> null
        }
        return Budget(packageName, day, base, earned, usage.awardedMs, capacity, combined,
            if (config?.selected == true) minOf(wallet, combined) else 0, block)
    }

    private suspend fun DataTransaction.offerFor(packageName: String?, exercise: Exercise, mode: ChallengeMode,
        day: String, prefs: AppPreferences): OfferResult {
        val rule = prefs.rewardRules.getValue(exercise)
        if (mode == ChallengeMode.EXERCISE_ONLY) {
            require(packageName == null)
            return OfferResult.Available(RewardOffer(null, day, exercise, mode, rule.repetitions, 0, 0, 0, 0))
        }
        if (packageName.isNullOrBlank()) return OfferResult.Refused(RewardBlock.TARGET_UNAVAILABLE)
        val budget = budgetFor(packageName, day, prefs)
        if (budget.blockedBy == BudgetBlock.TARGET_UNAVAILABLE) return OfferResult.Refused(RewardBlock.TARGET_UNAVAILABLE)
        if (budget.combinedRemainingMs == 0L) return OfferResult.Refused(RewardBlock.COMBINED_CEILING)
        if (budget.awardCapacityMs == 0L) return OfferResult.Refused(RewardBlock.AWARD_CAP)
        val configured = Math.multiplyExact(rule.rewardSeconds, 1000)
        return OfferResult.Available(RewardOffer(packageName, day, exercise, mode, rule.repetitions,
            minOf(configured, budget.awardCapacityMs), configured, budget.awardCapacityMs, budget.combinedRemainingMs))
    }

    private fun remaining(limit: Long, consumed: Long) = if (consumed >= limit) 0L else limit - consumed

    private suspend fun DataTransaction.spendingDateStillValid(day: String, accountingDay: String, observed: Boolean): Boolean {
        val current = wallClock.localDate().toString()
        if (current != day) cancelChallengesOutsideDay(current)
        if (!observed) return current == day
        return accountingDay == current || accountingDay == java.time.LocalDate.parse(current).minusDays(1).toString()
    }

    private suspend fun <T> DataTransaction.readForCurrentDay(initialDay: String, read: suspend (String) -> T): T {
        var day = initialDay
        while (true) {
            val result = read(day)
            val observed = wallClock.localDate().toString()
            if (observed == day) return result
            cancelChallengesOutsideDay(observed)
            day = observed
        }
    }

    /** Storage reads can suspend across midnight; validate again before the decision writes. */
    private suspend fun DataTransaction.stillToday(day: String): Boolean {
        val current = wallClock.localDate().toString()
        if (current == day) return true
        cancelChallengesOutsideDay(current)
        return false
    }
}
