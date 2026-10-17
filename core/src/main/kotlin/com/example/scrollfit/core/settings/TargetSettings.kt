package com.example.scrollfit.core.settings

import com.example.scrollfit.core.data.TargetConfig

data class TargetEdits(val removed: Set<String>, val saved: List<TargetConfig>)

/** Validate the entire draft before writing. Missing apps retain their config until explicit removal. */
object TargetSettings {
    fun edits(current: List<TargetConfig>, selected: Set<String>, available: Set<String>,
        allowances: Map<String, String>, caps: Map<String, String>): TargetEdits {
        val existing = current.associateBy { it.packageName }
        require(selected.all { it in available || existing[it]?.selected == true })
        val saved = selected.filter { it in available }.sorted().map { name ->
            (existing[name] ?: TargetConfig(name)).copy(selected = true,
                baseAllowanceMs = requireNotNull(SettingNumbers.milliseconds(allowances[name] ?: "", false)),
                dailyAwardCapMs = requireNotNull(SettingNumbers.milliseconds(caps[name] ?: "", false)))
        }
        return TargetEdits(current.filter { it.selected && it.packageName !in selected }.map { it.packageName }.toSet(), saved)
    }

    /** Builds edits from already validated millisecond drafts without rounding untouched values. */
    fun editsInMilliseconds(current: List<TargetConfig>, selected: Set<String>, available: Set<String>,
        allowancesMs: Map<String, Long>, capsMs: Map<String, Long>): TargetEdits {
        val existing = current.associateBy { it.packageName }
        require(selected.all { it in available || existing[it]?.selected == true })
        val saved = selected.filter { it in available }.sorted().map { name ->
            val allowance = requireNotNull(allowancesMs[name]).also { require(it >= 0L) }
            val cap = requireNotNull(capsMs[name]).also { require(it >= 0L) }
            (existing[name] ?: TargetConfig(name)).copy(
                selected = true,
                baseAllowanceMs = allowance,
                dailyAwardCapMs = cap,
            )
        }
        return TargetEdits(current.filter { it.selected && it.packageName !in selected }.map { it.packageName }.toSet(), saved)
    }
}
