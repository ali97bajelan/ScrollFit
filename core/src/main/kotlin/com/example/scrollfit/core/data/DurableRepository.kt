package com.example.scrollfit.core.data

import com.example.scrollfit.core.time.WallClock

/** Storage primitives for adapters/contract fixtures. Application callers receive only DataRepository. */
class DurableRepository(private val store: DurableStore, private val wallClock: WallClock) : DataRepository {
    override suspend fun saveTarget(config: TargetConfig) = store.transaction { putTarget(config) }
    override suspend fun targets(): List<TargetConfig> = store.transaction { targets() }
    override suspend fun removeTarget(packageName: String) = store.transaction {
        target(packageName)?.let { putTarget(it.copy(selected = false)) }
        Unit
    }
    override suspend fun dailyUsage(packageName: String, day: String?): DailyUsage = store.transaction {
        val date = day ?: today()
        usage(packageName, date) ?: DailyUsage(packageName, date)
    }
    override suspend fun combinedUsage(day: String?): CombinedUsage = store.transaction {
        val date = day ?: today()
        combined(date) ?: CombinedUsage(date)
    }
    override suspend fun session(packageName: String, day: String?): SessionState = store.transaction {
        val date = day ?: today()
        session(packageName, date) ?: SessionState(packageName, date)
    }
    override suspend fun saveSession(value: SessionState) = store.transaction { putSession(value) }

    /** Caller supplies a policy-approved interval once; updates all three records atomically. */
    suspend fun recordUsage(packageName: String, day: String, baseMs: Long, earnedMs: Long) = store.transaction {
        require(baseMs >= 0 && earnedMs >= 0)
        require(target(packageName)?.selected == true)
        val old = usage(packageName, day) ?: DailyUsage(packageName, day)
        val delta = Math.addExact(baseMs, earnedMs)
        putUsage(old.copy(
            baseConsumedMs = Math.addExact(old.baseConsumedMs, baseMs),
            earnedConsumedMs = Math.addExact(old.earnedConsumedMs, earnedMs),
        ))
        val total = combined(day) ?: CombinedUsage(day)
        putCombined(total.copy(consumedMs = Math.addExact(total.consumedMs, delta)))
        val window = session(packageName, day) ?: SessionState(packageName, day)
        putSession(window.copy(consumedMs = Math.addExact(window.consumedMs, delta)))
    }

    suspend fun recordCoverage(value: CoverageInterval, eligible: () -> Boolean = { true }) = store.transaction {
        if (!eligible()) throw CoverageInvalidated()
        if (coverageHiddenThroughDay()?.let { value.day <= it } == true) return@transaction
        val old = coverageTail(value.recordingSessionId, value.day)
        val compatible = old != null && old.historyVisible && old.timelineId == value.timelineId &&
            old.zoneId == value.zoneId && old.selectedPackages == value.selectedPackages &&
            value.startElapsedMs <= old.endElapsedMs && value.endElapsedMs >= old.startElapsedMs
        val merged = if (compatible) old!!.copy(
            startElapsedMs = minOf(old.startElapsedMs, value.startElapsedMs),
            endElapsedMs = maxOf(old.endElapsedMs, value.endElapsedMs),
            startEpochMs = if (value.startElapsedMs < old.startElapsedMs) value.startEpochMs else old.startEpochMs,
            endEpochMs = if (value.endElapsedMs >= old.endElapsedMs) value.endEpochMs else old.endEpochMs,
        ) else value
        if (!eligible()) throw CoverageInvalidated()
        putCoverage(merged)
        if (!eligible()) throw CoverageInvalidated()
    }

    suspend fun startChallenge(value: Challenge) = store.transaction {
        require(value.status == ChallengeStatus.ACTIVE && value.day == today())
        if (value.packageName != null) require(target(value.packageName)?.selected == true)
        insertChallenge(value)
    }
    override suspend fun challenge(id: String): Challenge? = store.transaction { challenge(id) }

    /** Completion callback must come from verified repetition progress, never from a UI timer. */
    suspend fun awardCompletedChallenge(id: String, repetitions: Int): Award? = store.transaction {
        award(id)?.let { return@transaction it }
        val challenge = challenge(id) ?: return@transaction null
        if (challenge.status != ChallengeStatus.ACTIVE) return@transaction null
        if (challenge.day != today()) {
            putChallenge(challenge.copy(status = ChallengeStatus.CANCELLED))
            return@transaction null
        }
        if (challenge.mode == ChallengeMode.EXERCISE_ONLY || repetitions < challenge.repetitionsRequired) return@transaction null
        val packageName = requireNotNull(challenge.packageName)
        val config = target(packageName) ?: return@transaction null
        if (!config.selected) return@transaction null
        val usage = usage(packageName, challenge.day) ?: DailyUsage(packageName, challenge.day)
        val awarded = Math.addExact(usage.awardedMs, challenge.rewardMs)
        if (awarded > config.dailyAwardCapMs) return@transaction null
        val result = Award(id, packageName, challenge.day, challenge.rewardMs, wallClock.epochMillis())
        insertAward(result)
        putUsage(usage.copy(awardedMs = awarded))
        putChallenge(challenge.copy(status = ChallengeStatus.COMPLETED))
        result
    }

    override suspend fun cancelChallenge(id: String) = store.transaction {
        challenge(id)?.takeIf { it.status == ChallengeStatus.ACTIVE }?.let {
            putChallenge(it.copy(status = ChallengeStatus.CANCELLED))
        }
        Unit
    }
    /** Run once at process startup, before accepting challenge callbacks. Awards remain intact. */
    suspend fun cancelInterruptedChallenges() = store.transaction { cancelActiveChallenges() }
    override suspend fun recordActivity(value: ExerciseActivity) = store.transaction {
        value.challengeId?.let { id ->
            val origin = requireNotNull(challenge(id))
            require(origin.day == value.day && origin.exercise == value.exercise)
        }
        insertActivity(value)
    }
    override suspend fun activities(): List<ExerciseActivity> = store.transaction { activities() }
    override suspend fun visibleHistory(): VisibleHistory = store.transaction {
        VisibleHistory(
            visibleUsages().sortedWith(compareByDescending<DailyUsage> { it.day }.thenBy { it.packageName }),
            visibleCombined().sortedByDescending { it.day },
            activities().sortedWith(compareBy<ExerciseActivity> { it.recordedAtEpochMs }.thenBy { it.id }),
            visibleCoverage(),
            activities().mapNotNull { item -> item.challengeId?.let { id -> challenge(id)?.let { id to it.mode } } }.toMap(),
            hiddenUsageDays(),
            coverageHiddenThroughDay(),
        )
    }
    override suspend fun clearHistory() = store.transaction {
        clearVisibleHistory()
        putCoverageHiddenThroughDay(maxOf(coverageHiddenThroughDay() ?: "0000-01-01", wallClock.localDate().toString()))
    }
    private fun today(): String = wallClock.localDate().toString()
}

class CoverageInvalidated : IllegalStateException("coverage write invalidated")
