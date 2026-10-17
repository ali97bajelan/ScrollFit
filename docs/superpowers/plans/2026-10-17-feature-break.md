# P3 Intentional Break Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans or, if explicitly selected by the owner, superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an optional camera-free pause with immediate Continue/Finish and no accounting side effects.

**Architecture:** Pure monotonic visit state drives one private activity; main/blocker callbacks launch it without touching automatic session tokens.

**Tech Stack:** Existing Kotlin/JUnit 4, Compose and Android intents; no new runtime dependencies.

**Spec:** [P3 design](../specs/2026-10-17-feature-break-design.md). Record the discovery gate in `docs/UX_BREAK_PHONE_TEST.md` before task 1.

## Global Constraints

- Keep minSdk 28, compileSdk/targetSdk 37, and JVM target 17. Use the repository's pinned dependencies; no production-library upgrades are part of these plans.
- All persisted accounting durations are integer milliseconds. Only `BudgetEngine` grants rewards and spends usage; UI projections never change that authority.
- Monitoring requires explicit enablement. Stop remains available with invalid drafts or missing access. Permission connectivity is not proof of enforcement.
- Retain immutable challenge terms, unique award identities, non-restorable interrupted progress, protected-app filtering, local-midnight expiry, and hidden-history safeguards.
- Support Persian (`values`), English (`values-en`), and French (`values-fr`), RTL, localized numbers, ASCII/Persian/Arabic digit entry, and system light/dark themes. Gregorian calendar remains the display policy for this package.
- Keep processing offline and local. Do not collect camera frames, other-app content, or raw app-use histories as product events. Feature discovery uses optional pilot observation and manual aggregate notes.

## Review Focus

- Continue/Finish at elapsed zero are valid (task 1).
- Wall-clock jump/process recreation cannot earn credit or fabricate completion (tasks 1–2).
- Selected target removed or launch intent missing (task 2).
- Pending automatic session boundary still blocks after Continue (task 3).
- Timer updates must not overwhelm TalkBack (task 2).

### Task 1: Optional pause visit state

**Files:** Create `core/src/main/kotlin/com/example/scrollfit/core/usage/IntentionalBreak.kt` and `core/src/test/kotlin/com/example/scrollfit/core/usage/IntentionalBreakTest.kt`.

**Interfaces:** `BreakStage { PAUSING, READY, FINISHED }`; `IntentionalBreak(startElapsedMs: Long)` exposes `remainingMs(nowElapsedMs: Long): Long`, `stage(nowElapsedMs: Long): BreakStage`, `finish()`; suggested duration=30,000 ms. State has no engine/repository dependency.

- [ ] Test 0→30,000 remaining, 29,999→1, 30,000→0/READY, immediate Finish→FINISHED, time regression clamps safely, and wall-clock input is absent. Run focused `IntentionalBreakTest`; observe missing API failure.

  First red test `suggestedPauseNeverLocksFinish`:

  ```kotlin
  val pause = IntentionalBreak(startElapsedMs = 1_000)
  assertEquals(30_000L, pause.remainingMs(1_000))
  pause.finish()
  assertEquals(BreakStage.FINISHED, pause.stage(1_000))
  ```

- [ ] Implement state with saturating elapsed subtraction; READY alone never records a deliberate finish.
- [ ] Rerun core test; expect pass. Review/commit: `feat: define voluntary intentional-break state`.

### Task 2: Private activity and safe actions

**Files:** Create `app/src/main/java/com/example/scrollfit/breaks/IntentionalBreakActivity.kt`, `IntentionalBreakScreen.kt`, `app/src/androidTest/java/com/example/scrollfit/breaks/IntentionalBreakScreenTest.kt`; modify `app/src/main/AndroidManifest.xml`; update all three `ux_strings.xml`.

**Interfaces:** `IntentionalBreakActivity.intent(context: Context, target: String?): Intent`; activity is `exported=false`. `IntentionalBreakScreen(state: IntentionalBreak, targetLabel: String?, onContinue: () -> Unit, onFinish: () -> Unit, onHome: () -> Unit)` uses U1 theme/formatting. Validate a target against `TargetApps.list` and current selected configs before app launch.

- [ ] Add connected tests for both immediate actions, no target→Home, unavailable target→recoverable error, fake launcher failure→Home fallback, process recreation→fresh suggestion, and a single non-live countdown semantic value. Assert no camera/permission request and no accounting calls through injected launch/navigation ports. Run before implementing and observe failure.
- [ ] Implement I1/I3, lifecycle-owned updates and internal-only activity. Finish sends Home intent then closes; Continue app-launch failure retains choices. Use no background timer/alarm or keep-awake flag.
- [ ] Rerun focused connected tests and source checks; expect pass. Review/commit: `feat: add camera-free break activity`.

### Task 3: Main/blocker entry and automatic-prompt regression

**Files:** Modify `app/src/main/java/com/example/scrollfit/ui/ProductScreens.kt`, `app/src/main/java/com/example/scrollfit/monitoring/BudgetOverlay.kt`, `MonitoringService.kt` as needed only for launch callback; add `app/src/androidTest/java/com/example/scrollfit/breaks/IntentionalBreakIntegrationTest.kt`; extend `core/src/test/kotlin/com/example/scrollfit/core/usage/UsageSessionTest.kt`; create `docs/UX_BREAK_PHONE_TEST.md`.

**Interfaces:** Main and blocker provide `onBreak(target: String?) -> Unit`. Automatic session prompts retain existing `sessionAction(SessionAccess, SessionAction)` callback; it is never invoked by the new screen.

- [ ] Flush any genuinely observed selected-app interval before taking the test baseline, then assert the break flow itself leaves wallet, award ledger, session consumed/pending and cursor unchanged. Assert Continue launch with a pending session still presents the original automatic prompt and exhausted budget still blocks. Run focused tests and observe missing integration failure.
- [ ] Wire Home and budget-blocker Take a break to the activity; retain Finish/Continue actions of automatic prompts exactly as before and their stale-window checks.
- [ ] Run core tests, app unit/build/lint, source checks and connected tests; record speech/mute/TalkBack, background/recreation, locale/large-text and voluntary usefulness observations.
- [ ] Review/commit: `feat: connect intentional breaks without resetting budgets`.

## Exit

I1–I3 pass. Report chosen finishes separately from timeout/abandonment/exercise completion; revisit whether the feature helps before wider release.
