# ScrollFit project review delivery specification

Date: **2026-10-26**. Status: proposed implementation contract; no execution or QA results implied.

Sources: [review](../../PROJECT_REVIEW_AND_PRIORITIES_2026-10-26.md), [task register](../../PROJECT_REVIEW_DELIVERY_TASKS_2026-10-26.md). Companions: [plan](../plans/2026-10-26-project-review-delivery.md), [QA scenarios](../../qa/project-review/2026-10-26-scenarios.md).

## Intent, scope and approaches

Make existing enforcement, camera ownership, recovery and evidence trustworthy before optional expansion. Cover every I01–I12 improvement, F-A–F-G proposal, and the service extraction/navigation and pilot recommendations. Qualification tasks validate existing code; they are not requests to rebuild already implemented guidance, lazy history or accounting.

Approach selected for this planning package: an incremental backlog with shared invariants and task-specific two-phase execution. A single rewrite would discard useful controller/accounting boundaries and obscure regressions. Reusing old task plans without reconciliation would repeat stale SDK assumptions and mix implementation with QA. Existing specifications remain references, with the reconciliation below taking precedence for this package. These are planning decisions, not claims of owner approval of new product policy.

Non-goals: SDK/library upgrades, a controller rewrite, health claims, accounts/backend, camera recording/upload, automatic exercise escalation, leaderboards, or retrospective enforcement inferred from missing observations. Demand-gated features remain optional. Scheduling requires explicit later policy review as its existing spec states.

## Global constraints

- Every task has exactly two delivery phases: Implementation and QA. Implementation performs no Gradle commands, builds, Gradle lint, emulator tests, instrumentation, or physical-device acceptance. Write relevant tests/fixtures during Implementation; execute them during QA.
- Retain the inspected configuration: minSdk 28, compileSdk 37, targetSdk 35, JVM target 17, versionCode 2, versionName `0.2.0-t02`. I10 documents this baseline; it does not automatically bump versions or adopt older plans' targetSdk 37. Any later SDK/version decision is separately scoped and must regenerate affected QA expectations.
- Retain the existing pinned library versions. I01 may add coroutine test/core artifacts at the existing catalog coroutines version `1.10.2`; do not add Android dependencies to `core`.
- `BudgetEngine` remains the accounting authority. Preserve consumed/earned balances, daily/shared caps, session state, award identity and chronological usage cursors. No duplicate awards, no invented unknown-period usage, no unfinished-repetition restoration.
- Keep camera processing offline and bundled. No camera frames, raw landmarks, screen/node text, raw accessibility events or private app timelines in diagnostics or QA artifacts.
- Keep explicit user enablement and visible-tap navigation. Protect Settings, launcher, keyboard, dialer, ScrollFit and unknown surfaces. Dismiss blockers immediately on safe/access-loss transitions, even while persistence is suspended.
- Preserve bounded queue capacity 64 and maximum trusted observation age 5,000 ms unless a separately justified correction changes both contract and tests. Increasing the queue alone is not a latency fix.
- Persian RTL, English and French; light/dark; 48 dp action targets; 320 dp width at 200% font and short landscape 640×360 dp. Keep Start/End/Stop/recovery reachable. Text contrast targets: 4.5:1 normal, 3:1 large; essential guide strokes/icons 3:1 on opaque backing. RTL never mirrors camera geometry a second time.
- New scenario results start NOT RUN. Required FAIL/BLOCKED/NOT RUN prevents task acceptance. Physical recognition/usability targets are targets, not measurements. Preserve dated historical failures and exclusions.

## Source map

Paths below are repository-relative. Existing roots:

| Root | Responsibility |
| --- | --- |
| `app/src/main/java/com/example/scrollfit/monitoring/` | `MonitoringService.kt`, `MonitorRuntime.kt`, `MonitorAccess.kt`, `ForegroundReconciler.kt`, `TargetApps.kt`, `BudgetOverlay.kt`, `MonitoringSetup.kt` |
| `app/src/main/java/com/example/scrollfit/ui/` | `ProductData.kt`, `ProductScreens.kt`, `SettingsScreen.kt`, `HistoryScreen.kt` and formatters/components |
| `app/src/main/java/com/example/scrollfit/challenge/`, `camera/`, `guide/` | Guide journey, challenge identity, camera presentation and accessible actions |
| `core/src/main/kotlin/com/example/scrollfit/core/` | Pure usage/budget/session/pose/outcome contracts and counters; data repository/visibility policy |
| `platform/src/main/kotlin/com/example/scrollfit/platform/` | `pose/MediaPipePoseEstimator.kt`, `pose/CameraPoseSession.kt`, `data/RoomStore.kt`, `data/ExerciseGateDatabase.kt`, `data/PreferencesRepository.kt`, `data/ExerciseGatePersistence.kt` |
| `app/src/main/res/` | `xml/backup_rules.xml`, `xml/data_extraction_rules.xml`; Persian `values`, English `values-en`, French `values-fr` |

New file/API names in the plan are proposed interfaces to create, not claims that those symbols already exist. In particular, the older preset spec mentions `SetupDraft`/`SetupCoordinator`; these were not found in the current source inventory. F-B must supply or adapt that behavior in the current `MonitoringSetup` flow.

## I01 — Full core test classpath

Add version-catalog coroutine JVM core/test aliases and use the required test dependencies in `core/build.gradle.kts`. `CoverageRecorderTest.kt` must compile with the complete current suite. No exclusions, deleted tests, counter-only totals or Android runtime stand-ins. QA records executed/failure/error/skip totals and the exact revision. The isolated `verification/core` build is valid for core but proves nothing about Android modules.

## I02 — Bitmap ownership and native cleanup

Planning choice: retain **caller ownership** of the bitmap passed to `PoseEstimator<Bitmap>.estimate`. `MediaPipePoseEstimator` creates an estimator-owned software `ARGB_8888` copy for MediaPipe; closing its wrapper may recycle only that copy. The original remains readable/reusable until its caller releases it. This is simpler for existing callers/tests than silently consuming their input; measure extra allocation in QA before considering a consuming-frame alternative.

Reject recycled input before native entry. Capture dimensions/aspect before disposal. Every successful/error/no-pose path closes the wrapper/copy once, including wrapper construction failure. Timestamp validation remains strictly increasing and inference/close stay serialized. `CameraPoseSession` releases each distinct original/crop/upright bitmap exactly once despite identity aliases, and closes every `ImageProxy`. Shutdown queues model close after in-flight inference; stale delivery stays rejected. QA must repeat real native inference and verify same-input reuse, all rotations, crop aliases and close/error paths, not merely remove the second test invocation.

## I03 — Production monitoring qualification

Prepare and execute current [T02](../../T02_PHONE_TEST.md), [T05](../../T05_PHONE_TEST.md), [T11](../../T11_PHONE_TEST.md), [T13](../../T13_PHONE_TEST.md) and T16 in `exercise_gate_tasks.md`. Use at least two eligible apps. Cover per-app and combined blocking, short-session Continue/Finish, all four exercises through advance and blocker earning, exercise-only/trial isolation, reduced/full/refused rewards, duplicate completions, kill/restart/rebind/reboot, permission loss, midnight and airplane-mode operation.

Include slow storage + Settings/Home/self transition, queue overflow/staleness, prompt races, overlay removal failure, keyboard/dialog/lock/split-screen/PiP and long idle/non-target coverage. The intended qualification device is physical Android 14 / API 34 phone. Emulator results complement it. If unavailable, keep intended-device qualification blocked. Device-bound cursors must not charge guessed downtime or suppress a different device's observations.

## I04 — Guide requalification

Reuse PG01–PG06 scenarios and [PG06 targets](2026-10-24-pg06-qualification-design.md). PG01's recovery copy and paused demo already exist: retest the two recorded failures first, then affected regressions; do not file their old omissions as new implementation work without reproducing them.

Required: 24 physical happy paths (four exercises × front/rear × advance/blocker/exercise-only), four standalone trials, eight exercise/lens recognition rows with three ten-rep trials each (at least 9/10 in each and zero extras), ten invalid/partial movements and 30 seconds idle with zero reps, stationary readiness within ten seconds, and at least ten minutes mixed use. Five consenting first-time participants: at least four of five per exercise explain a rep and begin within 60 seconds and two attempts. Retain stops/failures in denominators. Unavailable lenses get explicit unavailable/recovery results, not a successful recognition result. Preserve isolation, stale-callback, countdown/help, interruption, mirroring and audio checks.

## I05 — Recoverable initialization and orchestration seams

Replace the single `ready` boolean as the sole startup explanation with lifecycle states `STOPPED`, `INITIALIZING`, `FAILED`, `READY`. Permission/enabled/connected/processing remain separate facts. Explicit retry works from failed boot identity, persistence or metadata setup without waiting for Android to rebind. Concurrent retries coalesce into one initialization attempt and one command consumer; initialization itself never grants the monitoring lease or enables monitoring.

Inject monotonic/wall time, boot identity, access, foreground lookup, overlay, persistence and safety lookup through a small service-owned adapter boundary. Keep the existing controller/presentation/readiness policies. A generation invalidates partial initialization and late work on disable/disconnect. Cleanup cancels attempt/collector/ticks, releases the lease only under the existing overlay-removal safety contract, and never resets durable counters. Successful retry starts fresh observations and records the gap; no backfill.

## I06 — Failure boundaries and cleanup

Guard listener/receiver registration, foreground reconciliation, preferences, metadata, queue processing, budget/coverage and navigation boundaries. Always rethrow coroutine cancellation. Track successful registrations so partial startup unregisters only acquired resources, once. Cleanup attempts all remaining resources even if one unregister throws. Map failures to phase/category, not universally STORAGE; do not expose exception text or private payloads in UI.

An unexpected reconciliation or registration exception stops eligibility, invalidates generation/coverage, dismisses safely and publishes failed/stopped health. Retained overlay ownership on removal failure remains conservative. Failure classification and retry must not leave a stale healthy status or spawn an unhandled child exception.

## I07 — Bounded local diagnostics

Planning default: process-local ring of **128** sanitized records, plus fixed-size aggregate counters. Record monotonic failure time, failure phase/code, last successful processed-observation age, retry attempts/results, queue high-water mark, dropped count and aggregate count/total/max processing latency. No package labels/IDs, raw events, screen text, camera data, stack traces or unbounded logs. Process restart resets diagnostics; UI labels this scope. Monotonic age is Unknown across a new process/boot, never a negative wall-time calculation.

Metrics are optional local diagnostics, not telemetry. Writes must not suspend accounting or grow with event volume. Non-target/idle status must not promise live countdown freshness; successful reconciliation/checkpoint and last selected-app observation are distinguishable. A later support export is outside this task.

## I08 — Backup and transfer policy

Proposed privacy default for this package: **exclude all ScrollFit application state from automatic cloud backup and device-to-device transfer**, including usage/activity, selected packages, settings, awards, cursors, schedules/goals/widget caches if later added, and any diagnostics. Set an explicit manifest policy and explicit legacy/new rule exclusions for all used storage domains; inventory actual database/DataStore paths, WAL/SHM and device-protected equivalents. `allowBackup` alone is insufficient acceptance evidence for transfer behavior.

Fresh install/restore on a second device requires fresh access/readiness and no imported device cursor. Existing data on the current device remains intact after application update. If a historical backup is injected for testing, restored monitoring-enabled state/cursor cannot establish readiness or suppress new-device usage. Document platform/OEM limitations honestly; an untested transfer mechanism stays blocked. Manual visible-history export is a separate F-E decision. Confirm this proposed policy before executing I08; this documentation task does not alter backup settings.

## I09 — Callback and metadata latency

Use I07 diagnostics plus a QA-only timing harness to measure callback duration, queue delay, metadata lookup and persistence latency under rapid app switching and injected slow lookups. Establish before/after measurements with the same fixture/device and report sample count, median/p95/max; no invented ANR or performance claim.

Callbacks synchronously invalidate old presentation and return before package/role queries. Cache an immutable safety snapshot, refresh off-main on package/role/input-method/configuration changes, and conservatively withhold charging/blocking while eligibility is uncertain. Refresh results carry a revision/generation. A stale snapshot cannot block a newly protected package or resurrect a dismissed overlay. Keep the bounded queue and five-second rule.

## I10 — Current documentation and artifact identity

README becomes a concise current overview with supported flow, source status and links to detailed dated evidence. Keep historical results intact in their dated locations. A current verification matrix records revision and dirty-source identity, command, date, environment/device/API, artifact variant/version/SHA-256, discovered/executed/failure/skip counts, native crash/incomplete run, result and evidence path. Never equate APK existence with tested provenance or a signed release candidate. Match actual min/compile/target SDK and version configuration; record any unresolved identity decision instead of changing it implicitly.

## I11 — Accessibility and physical controls

Qualify actual Compose screens and accessibility blocker, not just semantics fixtures. Welcome/setup/health/dashboard/settings/history/Learn/Position/countdown/workout/help/result and blocker must support the global layout/locale/contrast matrix. Verify logical TalkBack order, meaningful disabled/recovery descriptions, reachable Start/End/Stop, no essential color/sound-only cue, mute/missing offline voice/reduced motion and camera transforms independent of RTL. Fix reproduced problems in the owning screen only; do not redesign unrelated flows. Share exact evidence rows with I04 rather than an aggregate accessibility PASS.

## I12 — History scaling

Profile before changing queries. Planning fixture: 365 and 1,095 dates, 20 selected/removed packages, sparse/complete/partial/unknown coverage and history deletion cutoffs. Capture load/aggregate time, query count, peak memory and frame behavior, alongside today's small-history baseline. Conditional optimization trigger for this plan: repeated full-range loads exceed 1 second p95 on the intended device, cause visible stalled interaction, or exhibit per-row target-set queries. These are proposed engineering thresholds, not recorded performance.

If triggered, add inclusive bounded-date repository reads and batched coverage-target resolution; keep the already-lazy UI. Weekly comparison still reads both required windows, coverage scope/zone rules remain identical, and deletion cannot re-expose hidden rows. If no trigger occurs, deliver measured evidence and explicitly retain the existing queries. No paging-library requirement.

## F-A — Monitoring health and recovery center

Expand current setup/health UI with distinct permission enabled, service connected, initializing/failed/ready, actual recent processing and gap states. Show last-confirmed observation age or Unknown, sanitized phase-specific reason and the matching recovery action. Connected/permission granted must never mean guaranteed enforcement. Retry calls I05 startup retry when needed, otherwise configuration/reconciliation; missing access opens the appropriate user-controlled setup. Stop remains reachable and immediately pauses eligibility before durable disable. Optional diagnostics are local and labeled since-process-start. Provide Persian/English/French copy and accessibility descriptions.

## F-B — Editable setup presets

Reuse [preset spec](2026-10-17-feature-presets-design.md), reconciling its assumed setup coordinator with actual source. SHORT: allowance 600,000 ms/app, shared ceiling 1,800,000 ms, session 180,000 ms, cap 600,000 ms/app. BALANCED: 1,800,000 / 3,600,000 / 300,000 / 1,200,000 ms respectively. Both set zero-base off, preserve exercise/reward rules and leave monitoring/speech/camera unchanged.

Preview fills a draft only, explains shared limits and consumed time, asks before replacing manual changes, preserves unavailable app values and explicit per-app overrides when adding apps. Earning off sets draft cap zero without erasing earned credit. Save uses validated existing persistence, actual-state readback on partial failure and separate explicit enable. Manual/Keep current stays reachable. No existing U3 implementation is assumed; include required draft/coordinator behavior in F-B if absent.

## F-C — On-demand intentional break

Reuse [break spec](2026-10-17-feature-break-design.md): optional 30,000 ms suggestion; Continue/Finish work immediately and timer expiry never navigates. Revalidate an optional selected target. No camera, credit, challenge/activity, session reset or `BudgetEngine.resolveSession` call. Continue may encounter the normal blocker again; failed launch retains recovery. Process recreation offers a fresh suggestion. Finish requests Home with ScrollFit fallback. Keep automatic short-session tokens entirely separate.

## F-D — Activity-day goals and weekly reflection

Reuse [goals spec](2026-10-17-feature-goals-design.md): Off by default, target 1..7 days, Monday–Sunday, changes start today. Count distinct visible dates joined to committed COMPLETED exercise challenges; exercise-only qualifies, practice/cancelled/manual/hidden/future rows do not. Clear history removes contribution without deleting goal settings or reconstructing activity from retained awards. Errors show unavailable, not zero. Zone changes do not rewrite recorded dates. No reward escalation or reminders.

Reconciliation with the review's broader proposal: first version supports activity-day goals only; intentional-finish goals need a separate reliable record source and remain deferred. Add an optional once-per-week local reflection prompt with Useful / Not useful / Skip, no required free text. Store at most the latest 12 ISO-week-plus-zone choices locally in the goal store, removable with history deletion; no identifiers, telemetry, health claims or auto-speech. Skipping suppresses that week's prompt. Goals themselves stay Off unless explicitly chosen.

## F-E — Deliberate local history export

Reuse the full [export contract](2026-10-17-feature-export-design.md), including its exact version-1 schema/headers, decimal-string JSON integers, deterministic order and reversible CSV formula escaping. Export only a final immutable visible-history/range snapshot after destination selection and explicit final confirmation. Coordinate history-clear with staging/copy, never export private retained award/cursor/diagnostic/camera data, and never claim unknown history as zero. Use Storage Access Framework; destination providers may sync according to the user's settings. Purge staging on all exits/startup; stream-close success precedes success UI. Preserve large integer values, actual coverage zones and missing historical zones as Unknown.

## F-F — Timestamped status widget

Reuse [widget contract](2026-10-17-feature-widget-design.md): native RemoteViews/AppWidgetProvider, one to three selected apps per independent widget. Read-only engine snapshots, never a sum of wallets or a live countdown. Include timestamp and absolute five-minute freshness cutoff; a host need not rerender precisely then. Refresh period 1,800,000 ms, spending-triggered refresh at most once per 60,000 ms, async work timeout 8,000 ms. Cross-day/zone/boot invalidates numeric currentness. Distinguish monitoring off/disconnected, stale and unavailable. Validate owned widget IDs and immutable explicit intents; no widget action enables monitoring, spends, rewards or bypasses blocking.

## F-G — Scheduled plans

Reuse [full schedule contract SC1–SC4](2026-10-17-feature-schedules-design.md); no clock-driven overwrite of manual preferences. Maximum two fixed-ID named rules, nonempty ISO weekdays, start/end minute 0..1439, unequal endpoints, positive ceiling/session, start-inclusive/end-exclusive, overnight start-day ownership, no overlaps; gaps use saved manual fallback. Draft save does not activate monitoring or schedules. Explicit activation/manual restoration preserves whole-day consumption, earned credit, caps and session consumed state.

One pure resolver supplies engine/UI/quotes. Split confidently observed intervals at actual calendar/policy boundaries with captured epoch/zone and immutable configuration revision. DST/zone/clock changes never fabricate time, reset credit or retroactively apply an edit. Unknown mappings become gaps. Malformed/unreadable schedule stops effective-policy decisions with recovery; Stop and exercise-only remain available. Active challenge terms remain immutable; completion revalidates capacity. Preserve the existing explicit demand/policy review gate before execution.

## M01 — Consolidated blocker navigation

After baseline navigation QA, extract duplicated PendingIntent construction into one helper for app/challenge destinations. Preserve explicit visible-blocker tap, immutable one-shot intents, request-code/destination separation and all current API 34/35/36+ branches. Retain blocker until destination actually resumes; failed or silently rejected launch keeps recovery. No new background launch authority or automatic launch. This is a conditional maintainability task, not a reported runtime bug.

## M02 — Conditional service decomposition

After orchestration regression coverage passes, inspect whether the I05 seams already make the service understandable. If additional extraction is justified, move serial coordination, safety adapter and diagnostics behind focused files; leave Android lifecycle/registration in the service and accounting in the existing core controller/engine. Preserve queue ordering, cancellation, generation/tickets and overlay-removal lease safety. Do not duplicate the controller. A documented no-change decision is acceptable if further extraction has no measurable maintenance benefit.

## P01 — Owner-led pilot and optional measurement

After required baseline QA, prepare an opt-in local pilot recording first setup, first completed challenge by exercise/mode, framing time/attempts, manual versus detected counts, interrupted attempts, recovery time and deliberate session finishes. Use anonymized aggregate notes; no event pipeline, raw packages/frames or participant identifiers. Keep stopped attempts and denominator definitions. Separate T02's debug spike from production enforcement. Evaluate F-B setup clarity and validate F-C/F-D usefulness before expanding; export/widget/schedules require concrete needs. Awarded minutes/increased restricted-app use are not primary success metrics. Missing coverage cannot establish reduced use or improved health.

## Open decisions and acceptance ownership

Execution owner reviews proposed bitmap-copy cost, backup exclusions, diagnostic retention and I12 engineering thresholds before the relevant implementation. The plan provides concrete defaults so these choices are reviewable; it does not claim they were already approved. Demand gates are real entry conditions. QA owners report required failures, unknowns and unsupported device behavior explicitly. No product change is authorized by a green Markdown check.
