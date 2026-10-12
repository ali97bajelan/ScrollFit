package com.example.scrollfit.spike

import android.content.Context
import android.content.SharedPreferences
import com.example.scrollfit.core.spike.SpikeCountdown

/** Debug test state only. Explicitly starting a NEW test resets its 30 seconds. */
class SpikeStore(context: Context) {
    val preferences: SharedPreferences = context.getSharedPreferences("t02_spike", Context.MODE_PRIVATE)
    val targetPackage: String? get() = preferences.getString("target", null)
    val enabled: Boolean get() = preferences.getBoolean("enabled", false)
    val consumedMillis: Long get() = preferences.getLong("consumed_ms", 0)
        .coerceIn(0, SpikeCountdown.ALLOWANCE_MILLIS)

    fun selectTarget(packageName: String) {
        check(!enabled)
        check(preferences.edit().putString("target", packageName).commit())
    }

    fun startNewTest() {
        check(!enabled)
        check(targetPackage != null)
        check(preferences.edit().putLong("consumed_ms", 0).putBoolean("enabled", true).commit())
    }

    fun stop() { check(preferences.edit().putBoolean("enabled", false).commit()) }

    fun persistConsumption(consumedMillis: Long): Boolean =
        preferences.edit().putLong("consumed_ms", consumedMillis).commit()
}
