# UX review implementation package

Prepared: **2026-10-17**. Source: [product and UX review](PRODUCT_UX_REVIEW_2026-10-16.md).

## Intent and status

Help people voluntarily interrupt selected-app use, understand their available time, and optionally complete camera-based exercise. Exercise-only activity remains useful independently of monitoring. Success means an understandable, recoverable core journey and honest outcomes; awarded minutes and restricted-app engagement are not success metrics.

The requested deliverable is specs and implementation plans. These documents are grounded in the inspected Kotlin/Compose, Room, DataStore, camera, monitoring, and outcome code. They are proposals ready for implementation review, not evidence that the changes have shipped or passed device testing. No product code, existing review, database schema, or task-status file is changed by this package.

**Planning assumptions:** retain the current reward, cancellation, app-eligibility, local-midnight, and history-visibility policies. Use the system-selected supported UI locale and Gregorian dates; Persian-calendar support requires a separate decision. No delivery dates, uplift targets, new telemetry service, or unrequested dependency upgrades are assumed.

## Approach and alternatives

Recommended: deliver the current journey in small vertical slices, with common presentation contracts first. This limits simultaneous lifecycle/persistence changes and lets each slice pass its own acceptance gate. A full UI rewrite would increase regression scope without improving the accounting model. Adding the backlog before correcting the journey would put more flows on unresolved recovery and setup behavior. Neither is included.

## Packages and delivery order

All links below are paired: read the spec before its plan. Each plan lists exact new and existing files, interfaces, tests, commands, and a review boundary. Future features have their own plans rather than being hidden inside UX cleanup.

| Order / ID | Scope | Spec | Plan | Entry gate |
| --- | --- | --- | --- | --- |
| 1 / U1 | Shared presentation, time input/display, production language | [Spec](superpowers/specs/2026-10-17-ux-foundation-design.md) | [Plan](superpowers/plans/2026-10-17-ux-foundation.md) | First implementation slice |
| 2 / U2 | Workout, recovery, reward disclosure, coach, accessibility | [Spec](superpowers/specs/2026-10-17-ux-workout-design.md) | [Plan](superpowers/plans/2026-10-17-ux-workout.md) | U1 contracts available |
| 3 / U3 | Guided setup, health recovery, drafts and app selection | [Spec](superpowers/specs/2026-10-17-ux-setup-design.md) | [Plan](superpowers/plans/2026-10-17-ux-setup.md) | U1; coordinate U2 navigation callbacks |
| 4 / U4 | Compact history and coverage-aware weekly review | [Spec](superpowers/specs/2026-10-17-ux-history-design.md) | [Plan](superpowers/plans/2026-10-17-ux-history.md) | U1; validate U2/U3 core journey first |
| 5 / P1 | Editable plan presets, F02 | [Spec](superpowers/specs/2026-10-17-feature-presets-design.md) | [Plan](superpowers/plans/2026-10-17-feature-presets.md) | U3 save/draft contract; Stage B acceptance |
| 6 / P2 | Personal weekly goals, F03 | [Spec](superpowers/specs/2026-10-17-feature-goals-design.md) | [Plan](superpowers/plans/2026-10-17-feature-goals.md) | U4; pilot validates ongoing value |
| 7 / P3 | On-demand intentional break, F05 | [Spec](superpowers/specs/2026-10-17-feature-break-design.md) | [Plan](superpowers/plans/2026-10-17-feature-break.md) | U3; discovery gate described in spec |
| 8 / P4 | Scheduled plans, F06 | [Spec](superpowers/specs/2026-10-17-feature-schedules-design.md) | [Plan](superpowers/plans/2026-10-17-feature-schedules.md) | Demonstrated demand and explicit policy review |
| 9 / P5 | Local visible-history export, F07 | [Spec](superpowers/specs/2026-10-17-feature-export-design.md) | [Plan](superpowers/plans/2026-10-17-feature-export.md) | U4; demonstrated export use case |
| 10 / P6 | Home-screen snapshot widget, F08 | [Spec](superpowers/specs/2026-10-17-feature-widget-design.md) | [Plan](superpowers/plans/2026-10-17-feature-widget.md) | U1/U3; demonstrated check-without-opening use case |

U2 and U3 can be developed separately after U1, but changes to `ScrollFitApp.kt`, resource files, and shared test setup must be integrated sequentially. P2 and P3 are independent experiments, not prerequisites for P4–P6. The order is a default, not a request to implement every future feature now.

## Complete review traceability

| Review item | Owning spec requirement | Owning plan task(s) |
| --- | --- | --- |
| UX01 live viewport | U2 W1, W4 | U2 tasks 3–4 |
| UX02 interruption recovery | U2 W2 | U2 tasks 2, 4 |
| UX03 guided onboarding | U3 S1 | U3 tasks 1, 4 |
| UX04 honest reward promise | U2 W3 | U2 tasks 1, 4 |
| UX05 exercise-only language/actions | U2 W3, W5 | U2 tasks 1, 4 |
| UX06 actionable monitoring health | U3 S2 | U3 tasks 3–4 |
| UX07 time and date presentation | U1 B1–B2 | U1 tasks 1–2 |
| UX08 saved state and partial failure | U3 S3 | U3 tasks 1–2, 4 |
| UX09 app eligibility explanation | U3 S4 | U3 task 4 |
| UX10 manageable history | U4 H1–H3 | U4 tasks 1–3 |
| UX11 shared visual system | U1 B3 | U1 task 3 |
| UX12 dynamic accessibility | U2 W4 | U2 task 4 |
| UX13 production copy | U1 B4 | U1 task 4 |
| F01 camera coach/practice | U2 W1 | U2 tasks 3–4 |
| F02 editable presets | P1 PR1–PR3 | P1 tasks 1–2 |
| F03 weekly goals | P2 G1–G3 | P2 tasks 1–3 |
| F04 weekly review | U4 H4 | U4 tasks 1–3 |
| F05 intentional break | P3 I1–I3 | P3 tasks 1–3 |
| F06 schedules | P4 SC1–SC4 | P4 tasks 1–4 |
| F07 export | P5 E1–E3 | P5 tasks 1–3 |
| F08 widget | P6 V1–V3 | P6 tasks 1–3 |

## Shared engineering and release contract

- Keep minSdk 28, compileSdk/targetSdk 37, and JVM target 17. Use the repository's pinned dependencies; no production-library upgrades are part of these plans.
- All persisted accounting durations are integer milliseconds. Only `BudgetEngine` grants rewards and spends usage; UI projections never change that authority.
- Monitoring requires explicit enablement. Stop remains available with invalid drafts or missing access. Permission connectivity is not proof of enforcement.
- Retain immutable challenge terms, unique award identities, non-restorable interrupted progress, protected-app filtering, local-midnight expiry, and hidden-history safeguards.
- Support Persian (`values`), English (`values-en`), and French (`values-fr`), RTL, localized numbers, ASCII/Persian/Arabic digit entry, and system light/dark themes. Gregorian calendar remains the display policy for this package.
- Keep processing offline and local. Do not collect camera frames, other-app content, or raw app-use histories as product events. Feature discovery uses optional pilot observation and manual aggregate notes.
- Keep unrelated working-tree files intact. Existing untracked release/schema/IDE artifacts are not inputs to overwrite, stage, or delete.

## Verification and evidence

Plans distinguish checks to run during implementation from checks performed while writing this package. **No app build, runtime test, or device acceptance is claimed here.**

Implementation baseline commands, from repository root:

```bash
python3 verification/check_source.py
./gradlew -p verification/core :core:test
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
./gradlew :platform:connectedDebugAndroidTest :app:connectedDebugAndroidTest
```

`verification/core` runs actual core sources without Android configuration. `verification/run_core.py` is an offline fallback only when its documented Java/compiler/JUnit environment is present. Do not substitute source checks for compilation or connected tests. Resolve environment failures as blockers and record the exact command and reason; do not report them as product failures or passing tests.

U1 introduces BOM-aligned Compose UI instrumentation dependencies before any plan refers to Compose UI tests. Every implementation task first runs its new behavioral test and observes the intended failure, then implements and reruns it. Copy-only cleanup uses source/resource validation and manual inspection rather than a test that mirrors literal implementation text. Each task ends in a scoped review/commit; do not include unrelated files.

Release acceptance requires a phone/emulator record with app build, OS, locale, font scale, theme, expected/actual behavior, and pass/fail. Cover the owner's documented Xiaomi Mi 11 Lite NE / Android 14 if available; also exercise API 28 and API 37 behavior. Use 320 dp available width, 200% font scale, keyboard visible, English/French/Persian, RTL, TalkBack with app speech on/off, and both challenge entry points. Report contrast, recognition quality, clipping, and performance only after measuring them.

## Pilot and later-feature gates

Stage A exits when someone can select one app, explicitly enable connected monitoring, explain credit versus usable time, complete or abandon a workout, and recover without assistance. Stage B exits after locale, editing, history, theme, keyboard, and accessibility acceptance. U4's optional weekly reflection can be used during Stage C; F03/F05 require observed usefulness before wider rollout. Stage D features F06–F08 require a recorded user problem and a signed-off policy spec before their implementation steps begin.

Record activation, first committed workout success by mode/exercise, interruptions by coarse reason, fresh-challenge recovery, chosen finishes, exercise-only completions, comparison eligibility, and reliability failures using opt-in pilot notes. A result recovered from storage is not a fresh workout success. Unknown coverage never counts as zero use or an improvement. There is no analytics pipeline or numeric uplift target in this scope.

## Handoff

Begin with U1, then U2 and U3. Review the paired written spec and plan before implementation; these artifacts are the reviewable output requested in this task. Later-feature defaults are explicit design proposals and retain the gates above. Execution has not been started or delegated.
