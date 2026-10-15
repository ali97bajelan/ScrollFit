# T14 product screens and settings

Scope: T14, the next task after T13. T15 weekly outcomes/coverage and T16
integrated delivery remain outside this request. Existing changes and the index
are preserved. No git commit, publication or installation is performed.

## Product behavior

- Welcome explains local camera processing, special access, explicit monitoring
  enablement, editable default limits and bypass limitations. Explicit dashboard
  entry saves onboarding completion in DataStore; subsequent launches start at
  Home. Read failures expose retry instead of silently accepting defaults.
- Home reads today's actual repository/budget-engine values: per-app recorded
  consumption, free/earned balances, total awarded, award cap/capacity and usable
  time; combined recorded use, ceiling and remaining time; monitoring health and
  failure details. Removed/unavailable apps and capped rewards have explanations.
  Earn opens the selected app directly. Exercise-only remains available at caps.
- The exercise catalog reads the saved rules for all four exercises and opens
  durable exercise-only challenges with the selected exercise. It no longer
  exposes temporary, unsaved trials as the primary exercise action.
- App monitoring setup supports app search, multiple selections, free allowance
  and daily award cap per app, combined ceiling, short-session boundary and
  zero-base mode. Required access has explicit recovery controls and enablement
  explanations. Previously configured unavailable apps remain selected until
  explicitly removed; changing unrelated settings does not remove them.
  Metadata refreshes on resume or explicit refresh while retaining field drafts.
- Settings edits all four repetition/reward rules, preferred camera, next
  exercise and speech preference. Unedited preference fields are preserved from
  the latest DataStore value. Exercise preference drafts use saveable state.
  Stop monitoring uses the existing pause/write/reconciliation path.
- Daily history reads only visible stored usage, combined totals and activity,
  including records for removed targets. Usage and exercise appear separately.
  Missing records are described as unrecorded rather than zero usage; no baseline,
  calorie, fitness or full-day coverage inference is made. Weekly outcomes and
  durable coverage remain T15.
- Delete local history requires an explicit in-app confirmation, deletes
  exercise activity and hides existing usage history. Enforcement counters,
  wallets, sessions, challenges, award identities and cursors are retained.
  Already-hidden current-day accounting stays hidden after further spending;
  dashboard enforcement figures remain visible. New records on later days can
  appear normally. This preserves the existing repository deletion policy.
- New product strings are supplied in Persian, English and French. Numeric
  fields accept ASCII/Persian/Arabic digits, explicitly state seconds, use LTR
  text direction and reject signs, fractions and overflow. Allowances/award caps
  accept zero; ceiling, session, rewards and repetition targets must be positive.
  Persian summary values use directional isolation. Selection rows expose role,
  checked/selected state and labels with at least 48 dp height; decorative
  navigation symbols are hidden from accessibility semantics.

Dashboard/history refresh on resume, saved preference changes, monitoring status
changes, explicit refresh and an observed date change. Their UI engine never
submits spending intervals. Reads crossing midnight fail/retry rather than
publish mixed dates. No dependencies, permissions, Room tables or migrations
were added. Room adds read-only queries; DataStore adds a backward-compatible
onboarding key defaulting to false.

Target fields are fully validated before any write. Each Room operation and the
DataStore preference edit retain their existing transactional semantics; a
multi-app settings save is not one transaction across Room and DataStore. A
storage failure can leave some valid target settings saved; the UI reports failure
and allows retry, without resetting any enforcement state.

## Verification

Use `python3 verification/check_source.py` for source/resource checks and
`./gradlew -p verification/core :core:test --rerun-tasks --console=plain` for the
project-native JVM suite. `verification/run_core.py` is a dependency-free Python
fallback that compiles the same actual Kotlin sources/tests with a separately
provisioned Java/Kotlin/JUnit toolchain; it downloads nothing.

- Final command: `env JAVA_HOME=/tmp/scrollfit-toolchain/jdk-21.0.12.1+1 SCROLLFIT_KOTLIN_COMPILER_DIR=/tmp/scrollfit-toolchain/kotlin python3 verification/run_core.py`.
  Exit **0**: 44 actual Kotlin source/test files, 16 JUnit classes, **279 tests
  passed**, zero failures. This is 267 existing tests plus 5 numeric-input,
  4 target-selection and 3 visible-history checks.
  [Actual log](verification/T14-core-tests.log), [summary](verification/T14-test-summary.txt).
- The isolated selection regression probe restores silent deselection in a
  temporary copy of `TargetSettings`: the removal predicate uses
  `selected.intersect(available)` instead of `selected`. Its 4 tests produce
  **1 expected failure**, `unrelatedSaveRetainsUnavailableSelectionAndTerms`.
  The actual source checkout stays fixed. [Probe log](verification/T14-selection-regression-probe.log).
- `python3 verification/check_source.py` checks XML/TOML, resource references,
  service/Activity restrictions, whitespace and all **47** product string keys and
  format placeholders in each locale. [Actual output](verification/T14-source-checks.log).
- Android command: `env JAVA_HOME=/tmp/scrollfit-toolchain/jdk-21.0.12.1+1 GRADLE_USER_HOME=/tmp/scrollfit-gradle ./gradlew :app:assembleDebug :app:assembleRelease :app:lintDebug :platform:assembleDebugAndroidTest --offline --console=plain`.
  [Actual build output](verification/T14-build.log).
- Existing file-backed Room and DataStore test fixtures now also check visible
  history deletion/reopen and onboarding default/save/reopen. These Android tests
  are prepared, **unrun**, and are not counted as JVM passes.

Android compilation, lint, KSP/schema export, file-backed Room/DataStore tests
and all phone/TalkBack/font-scale checks require the Android build environment
and device; source checks and JVM tests do not establish those results.

Official reference used for selection semantics and parent click handling:
[Compose accessibility defaults](https://developer.android.com/develop/ui/compose/accessibility/api-defaults).
Real accessibility behavior remains a phone check.

## Review

An independent read-only reviewer inspected the new product screens,
repository/Room queries, preferences, numeric parsing and navigation. No Critical
issue or definite Kotlin/Compose compile defect was found by source inspection.
Two Important findings were corrected: unavailable targets were silently removed
on unrelated saves, and app metadata stayed stale after returning to setup.
The `TargetSettings` regression covers preservation and explicit removal;
resume metadata refresh preserves unsaved fields. Speech rows now use toggle
semantics/minimum height. Exercise draft preferences use saveable state;
decorative navigation glyphs are excluded from semantics. Android execution
remains necessary to verify these UI behaviors.

See [pending phone checks](T14_PHONE_TEST.md) and
[generated commit message](T14_COMMIT_MESSAGE.txt).
