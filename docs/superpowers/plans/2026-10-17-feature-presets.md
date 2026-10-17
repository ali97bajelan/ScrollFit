# P1 Editable Presets Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans or, if explicitly selected by the owner, superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Offer two editable setup presets with a truthful preview and explicit application.

**Architecture:** A pure preset catalog/projection transforms U3 drafts; existing coordinator handles all persistence and activation.

**Tech Stack:** Existing Kotlin/JUnit 4, Compose, Room/DataStore via U3; no new runtime dependencies.

**Spec:** [P1 design](../specs/2026-10-17-feature-presets-design.md). Entry: U3 and Stage B acceptance.

## Global Constraints

- Keep minSdk 28, compileSdk/targetSdk 37, and JVM target 17. Use the repository's pinned dependencies; no production-library upgrades are part of these plans.
- All persisted accounting durations are integer milliseconds. Only `BudgetEngine` grants rewards and spends usage; UI projections never change that authority.
- Monitoring requires explicit enablement. Stop remains available with invalid drafts or missing access. Permission connectivity is not proof of enforcement.
- Retain immutable challenge terms, unique award identities, non-restorable interrupted progress, protected-app filtering, local-midnight expiry, and hidden-history safeguards.
- Support Persian (`values`), English (`values-en`), and French (`values-fr`), RTL, localized numbers, ASCII/Persian/Arabic digit entry, and system light/dark themes. Gregorian calendar remains the display policy for this package.
- Keep processing offline and local. Do not collect camera frames, other-app content, or raw app-use histories as product events. Feature discovery uses optional pilot observation and manual aggregate notes.

## Review Focus

- Applying a preset to two apps must not imply that both wallets bypass the shared ceiling (task 2).
- Turning earning off must preserve existing earned credit/awards (tasks 1–2).
- Unavailable selected apps must retain values (task 1).
- Manual edits/new selections after applying must seed or override predictably (task 1).
- Lowered limits/partial save failures must not look like a reset (task 2).

### Task 1: Catalog and pure draft application

**Files:** Create `core/src/main/kotlin/com/example/scrollfit/core/settings/PlanPreset.kt`, `core/src/test/kotlin/com/example/scrollfit/core/settings/PlanPresetTest.kt`, `app/src/main/java/com/example/scrollfit/monitoring/PresetDraft.kt`, and `app/src/test/java/com/example/scrollfit/monitoring/PresetDraftTest.kt`.

**Interfaces:** `PresetId { SHORT, BALANCED }`; `PlanPreset(id: PresetId, baseAllowanceMs: Long, combinedCeilingMs: Long, sessionBoundaryMs: Long, dailyAwardCapMs: Long)`; `PlanPresets.get(id: PresetId): PlanPreset`. `PresetDraft.apply(draft: SetupDraft, preset: PlanPreset, eligible: Set<String>, earning: Boolean): SetupDraft` replaces only PR2-owned fields. `PresetDraft.seedNewSelection(draft: SetupDraft, packageName: String, preset: PlanPreset, earning: Boolean, manuallyEditedPackages: Set<String>): SetupDraft` seeds newly selected packages while respecting manual overrides.

- [ ] Add exact-value catalog assertions from PR1 and draft tests for unavailable preservation, off cap=0, earning-on restoration, manual override/new app seeding, and unchanged speech/reward rules/enabled state. Run focused core/app tests; observe missing API failures.

  Name the first catalog test `balancedMatchesExistingPlanDefaults`:

  ```kotlin
  val balanced = PlanPresets.get(PresetId.BALANCED)
  assertEquals(1_800_000L, balanced.baseAllowanceMs)
  assertEquals(3_600_000L, balanced.combinedCeilingMs)
  assertEquals(300_000L, balanced.sessionBoundaryMs)
  assertEquals(1_200_000L, balanced.dailyAwardCapMs)
  ```

- [ ] Implement catalog and draft transformation with no persistence dependency. Keep all amounts in milliseconds and initialize U1 duration drafts exactly.
- [ ] Rerun `./gradlew -p verification/core :core:test --tests '*PlanPresetTest'` and `./gradlew :app:testDebugUnitTest --tests '*PresetDraftTest'`; expect pass. Review/commit: `feat: define editable app-use presets`.

### Task 2: Preview, explicit apply, and persistence regression

**Files:** Create `app/src/main/java/com/example/scrollfit/monitoring/PresetChooser.kt`, `app/src/androidTest/java/com/example/scrollfit/monitoring/PresetChooserTest.kt`; modify U3 `SetupContent.kt`; add regression cases to `core/src/test/kotlin/com/example/scrollfit/core/budget/BudgetEngineTest.kt` and U3 `SetupCoordinatorTest.kt`; update the three `ux_strings.xml`; append `docs/UX_SETUP_PHONE_TEST.md`.

**Interfaces:** `PresetChooser(draft: SetupDraft, eligible: Set<String>, onApply: (SetupDraft) -> Unit)` consumes task 1 and emits drafts only. Confirmation belongs to the chooser; persistence remains exclusively U3 Save/Enable.

- [ ] Add UI assertions for Balanced/two-app shared warning, no writes during preview, Cancel leaves original draft equal, Replace confirmation for dirty drafts, cap-zero explanation retaining earned credit, and exceeded-today warning. Add engine regression: preset save changes config but not usage/awards/cursor/session count. Add coordinator partial-failure case. Run focused tests before wiring and observe failure.
- [ ] Implement PR2/PR3 in setup step 2; use U1 duration display/input and U3 saved-summary/failure states. Do not add automatic monitoring activation.
- [ ] Run source checks, core tests, app unit/build/lint, connected `PresetChooserTest` and platform budget persistence tests. Record locale/RTL/large text and opt-in setup observation/corrections without storing app histories as analytics.
- [ ] Review/commit: `feat: preview and apply setup presets explicitly`.

## Exit

PR1–PR3 pass; preset values remain configurable proposals, and pilot evidence determines whether they help setup.
