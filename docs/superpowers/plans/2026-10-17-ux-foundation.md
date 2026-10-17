# U1 Shared Product Presentation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans or, if explicitly selected by the owner, superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Standardize time, theme, action hierarchy, and production vocabulary across existing entry points.

**Architecture:** Pure Kotlin decomposes time and validates unit input. Small Android/Compose adapters render localized values and share semantic styling with the native overlay.

**Tech Stack:** Existing Kotlin/JVM 17, Compose Material 3, Android resources, JUnit 4; BOM-aligned Compose instrumentation tests.

**Spec:** [U1 design](../specs/2026-10-17-ux-foundation-design.md).

## Global Constraints

- Keep minSdk 28, compileSdk/targetSdk 37, and JVM target 17. Use the repository's pinned dependencies; no production-library upgrades are part of these plans.
- All persisted accounting durations are integer milliseconds. Only `BudgetEngine` grants rewards and spends usage; UI projections never change that authority.
- Monitoring requires explicit enablement. Stop remains available with invalid drafts or missing access. Permission connectivity is not proof of enforcement.
- Retain immutable challenge terms, unique award identities, non-restorable interrupted progress, protected-app filtering, local-midnight expiry, and hidden-history safeguards.
- Support Persian (`values`), English (`values-en`), and French (`values-fr`), RTL, localized numbers, ASCII/Persian/Arabic digit entry, and system light/dark themes. Gregorian calendar remains the display policy for this package.
- Keep processing offline and local. Do not collect camera frames, other-app content, or raw app-use histories as product events. Feature discovery uses optional pilot observation and manual aggregate notes.

## Review Focus

- 59,999/60,001 ms and maximum Long input must not round up or overflow (task 1).
- Switching a nonintegral unit or opening a subsecond saved value must not silently change it (task 2).
- Effective French/Persian locale can differ from `Locale.getDefault()` (tasks 1–2).
- Native Stop must work while an asynchronous offer read fails (task 3).
- Long labels and 200% font must not remove primary/recovery actions (tasks 2–3).

Paths below are repository-relative. New files are explicitly marked Create; all others already exist. Add matching new strings in `app/src/main/res/values/ux_strings.xml`, `values-en/ux_strings.xml`, and `values-fr/ux_strings.xml`.

### Task 1: Exact time decomposition and formatting

**Files:** Create `core/src/main/kotlin/com/example/scrollfit/core/settings/DurationDisplay.kt`; create `app/src/main/java/com/example/scrollfit/ui/ProductFormatters.kt`; modify `ProductComponents.kt`, `HistoryScreen.kt`, `ProductScreens.kt` in that UI directory and `app/src/main/java/com/example/scrollfit/feedback/ChallengeAudio.kt`; create `core/src/test/kotlin/com/example/scrollfit/core/settings/DurationDisplayTest.kt` and `app/src/androidTest/java/com/example/scrollfit/ui/ProductFormattersTest.kt`; modify `app/build.gradle.kts` and `gradle/libs.versions.toml` for UI test artifacts.

**Interfaces:** Consumes integer ms and effective Android locale. Produces `DurationParts`, `DurationDisplay.parts(ms: Long): DurationParts`, `ProductFormatters.duration(context: Context, ms: Long): String`, `date(context: Context, day: LocalDate, full: Boolean = false): String`.

- [ ] Add core tests with assertions `parts(59_999) == DurationParts(0,0,59,999)`, `parts(60_001) == DurationParts(0,1,0,1)`, `parts(-1) == DurationParts(0,0,0,0)`, and a `Long.MAX_VALUE` decomposition that reconstructs the original without overflow.

  First red test in `DurationDisplayTest`:

  ```kotlin
  @Test fun splitsBeforeFormattingAtMinuteBoundary() {
      assertEquals(DurationParts(0, 0, 59, 999), DurationDisplay.parts(59_999))
      assertEquals(DurationParts(0, 1, 0, 1), DurationDisplay.parts(60_001))
  }
  ```

- [ ] Run `./gradlew -p verification/core :core:test --tests '*DurationDisplayTest'`; expect failure for the missing API before implementation.
- [ ] Implement the pure decomposition and Android formatter per B1. Replace elapsed-clock/raw-ISO display on the named surfaces; keep persisted IDs untouched.
- [ ] Add `androidx.compose.ui:ui-test-junit4` as `androidTestImplementation` and `androidx.compose.ui:ui-test-manifest` as `debugImplementation`, using the existing Compose BOM. Add instrumented assertions for the B1 examples, Persian numeric locale, French unit ordering, and a context whose resource locale differs from the process default. Use localized resource outputs rather than hard-coded English expectations for other languages.
- [ ] Run the focused core command and `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.scrollfit.ui.ProductFormattersTest`; expect all assertions passing. Device absence is a blocked connected check, not a pass.
- [ ] Review/commit only task files: `feat: share exact localized time presentation`.

### Task 2: Duration editor with safe unit conversion

**Files:** Create `core/src/main/kotlin/com/example/scrollfit/core/settings/DurationInput.kt`, matching `DurationInputTest.kt` under core tests, `app/src/main/java/com/example/scrollfit/ui/DurationSetting.kt`, and `app/src/androidTest/java/com/example/scrollfit/ui/DurationSettingTest.kt`; modify `core/src/main/kotlin/com/example/scrollfit/core/settings/SettingNumbers.kt` only to share digit parsing; modify `app/src/main/java/com/example/scrollfit/ui/SettingsScreen.kt` and `app/src/main/java/com/example/scrollfit/monitoring/MonitoringSetup.kt`.

**Interfaces:** Consumes task 1 formatters. Produces `DurationUnit`, `DurationInput.parse(text: String, unit: DurationUnit, positive: Boolean): Long?` and `DurationSetting(value: String, unit: DurationUnit, onValueChange: (String) -> Unit, onUnitChange: (DurationUnit) -> Unit, label: Int, positive: Boolean, enabled: Boolean)`. The containing draft retains the original ms value until a field edit; reward seconds are converted only after validation.

- [ ] Add tests asserting Persian `۵` minutes → 300,000; Arabic `٣٠` seconds → 30,000; positive zero → null; nonnegative zero → 0; negative/blank/fraction/exponent/overflow → null.
- [ ] Run `./gradlew -p verification/core :core:test --tests '*DurationInputTest'`; expect the missing API failure.
- [ ] Implement the parser and editor. Seed integral minute values as minutes, other whole-second values as seconds, and preserve exact unedited subsecond values. Provide dynamic hints from the actual draft, replacing the fixed 300-second example. Keep repetition fields unchanged.
- [ ] Add UI tests: 90 seconds → choosing minutes preserves 90/SECONDS and explains nonintegral conversion; 120 seconds → 2/MINUTES; invalid draft hides hint/disables Save; opening and saving an untouched 60,001 ms allowance preserves 60,001. Check hint numeric locale using a French/Persian resource context and reachable Save with IME at 200% font.
- [ ] Run focused core tests plus `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.scrollfit.ui.DurationSettingTest`; expect pass. Run existing `SettingNumbersTest` to preserve all digit scripts.
- [ ] Review/commit task files: `feat: add minutes-first duration editing`.

### Task 3: Shared theme and native overlay hierarchy

**Files:** Create `app/src/main/java/com/example/scrollfit/ui/ScrollFitTheme.kt`, `ProductTokens.kt`, `app/src/main/java/com/example/scrollfit/monitoring/OverlayStyle.kt`, and `app/src/androidTest/java/com/example/scrollfit/monitoring/OverlayPresentationTest.kt`; modify `app/src/main/java/com/example/scrollfit/ui/ScrollFitApp.kt`, `app/src/main/java/com/example/scrollfit/challenge/ChallengeActivity.kt`, and `app/src/main/java/com/example/scrollfit/monitoring/BudgetOverlay.kt`.

**Interfaces:** Produces `ScrollFitTheme(content: @Composable () -> Unit)`, `ProductTokens`, `OverlayStyle.resolve(context: Context): OverlayStyle`. Extract `buildOverlayContent(context: Context, label: String, reason: BlockReason, offers: List<Pair<Exercise, OfferResult>>, offerError: Boolean, style: OverlayStyle, actions: OverlayActions): View`. `OverlayActions` holds `onTakeBreak: () -> Unit`, `onEarn: (Exercise?) -> Unit` (null means exercise-only), `onContinue: (() -> Unit)?`, `onOpenPanel: () -> Unit`, and `onStop: () -> Unit`; session Finish is bound to onTakeBreak by the adapter. Inject these callbacks/offer state to test without a real accessibility window, retaining adapter-level current-window guards. Preserve `BudgetOverlay.show/dismiss/isCurrent` public contracts.

- [ ] Add instrumented view assertions for action order, minimum targets, primary/secondary roles, light/dark role values, and Stop invocation with an offer-loading error. Assert a replaced-window callback cannot launch an old target; retain existing `OverlayState` tests.
- [ ] Run the focused connected test command for `com.example.scrollfit.monitoring.OverlayPresentationTest`; observe the intended hierarchy/style failure before editing.
- [ ] Implement B3 with a vertically wrapping action group, shared scheme in both activities, explicit native button tints, and unchanged system navigation/stale-window safeguards.
- [ ] Rerun connected tests and `./gradlew -p verification/core :core:test --tests '*MonitorFailureTest'`. Inspect both activities and overlay in light/dark, French/Persian, 320 dp width, and 200% font; record contrast measurements and action reachability.
- [ ] Review/commit task files: `feat: align themes and blocker actions`.

### Task 4: Resource vocabulary and release verification

**Files:** Modify reachable copy in `app/src/main/res/values/monitoring_strings.xml`, `camera_strings.xml`, `product_strings.xml`, and corresponding `values-en`/`values-fr` files; update new `ux_strings.xml` files; modify `verification/check_source.py`; create `docs/UX_FOUNDATION_PHONE_TEST.md`.

**Interfaces:** Resource names remain stable for callers unless their usage inventory proves removal safe. Consumes B4 canonical concepts and all previous task behavior.

- [ ] Inventory main/debug/release Kotlin, XML and string-name lookups with `rg -n 'monitor_healthy|monitor_limits_notice|camera_model_error|getIdentifier|R\.string' app/src`. Record indirect/uncertain keys before removing unused strings.
- [ ] Apply B4 copy consistently in all locales. Add retry/app-info actions to camera error presentation where the existing state supports them; do not add reinstall as an ordinary action. Move testing notes into the new phone checklist.
- [ ] Extend locale-key/placeholder validation to all touched resource bundles, including plural items and new `ux_strings.xml`. Keep this a structural check; do not write literal-copy mirror tests.
- [ ] Run `python3 verification/check_source.py`, `./gradlew -p verification/core :core:test`, and `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug`; expect zero failures. Record connected/manual checks separately with actual results.
- [ ] Review/commit task files: `fix: clarify production language and recovery advice`.

## Exit and handoff

U1 exits when B1–B4 acceptance and the recorded UI/device checks pass. Read U2/U3 next. This plan does not start implementation or select delegated execution.
