# App bug fixes specification — 2026-10-17

Status: implementation applied in the working tree; Android/UI/device validation remains incomplete.

Source: [app bug review](../../APP_BUG_REVIEW_2026-10-17.md). Companion: [implementation plan](../plans/2026-10-17-app-bug-fixes.md).

## Intent and scope

Resolve the three reported risks without requiring the wider UX or feature backlog: keep an active workout usable in a small viewport, expose meaningful exercise state to assistive technology, and avoid eager history composition and repeated full-history scans. Preserve existing challenge, reward, cancellation, and history-visibility policies.

The review reports source findings, not completed interactive reproduction. This specification uses the same distinction: layout and TalkBack acceptance, camera parity, and performance measurements remain implementation gates. No bug fix, successful build, or device result is claimed by these documents.

The source review describes reported risks rather than verified interactive reproductions. Code now covers the planned fixes, but this status does not claim that Android compilation, accessibility behavior, camera behavior, or device performance has passed; see [the validation record](../../APP_BUG_FIX_VALIDATION_2026-10-17.md).

## Traceability and inspected causes

| ID / priority | Source finding and cause | Requirement | Plan tasks |
| --- | --- | --- | --- |
| BUG-01 / P1 | `ChallengeScreen` places feedback/count above a scrolling `CameraScreen`; that screen places instructions above a fixed 360 dp preview and Close below it | B1 | 1, 5 |
| BUG-02 / P2 | Both screens expose changing counts/status as ordinary text, with no explicit progress or announcement policy | B2 | 2, 5 |
| BUG-03 / P2 | `HistoryScreen` eagerly emits all days inside scrolling `ProductPage`, rescans activities per card, and rebuilds app labels per day | B3 | 3, 4, 5 |

Additional relevant cause for BUG-03: `OutcomeAggregator.summarize` calls `dayOutcome` for every date; each call filters full usage/activity/coverage lists. Coverage and comparison helpers also filter full coverage lists. A lazy list alone will leave this preparation cost in place.

## Approach and existing proposals

Recommended: three independently reviewable fixes, with the workout extraction before its accessibility integration and history preparation before its lazy UI. Retain current engine/repository interfaces.

Alternatives considered:

- Implement the complete [U2 workout](2026-10-17-ux-workout-design.md) and [U4 history](2026-10-17-ux-history-design.md) proposals first. This also addresses the findings but couples urgent fixes to practice, recovery, reward disclosure, charts, paging, and reflection.
- Only reorder camera text and wrap history in a lazy list. This is smaller but leaves duplicate counts, camera ownership embedded in presentation, and repeated aggregation scans.

This package is a focused subset of U2 W4 and U4 H3, with the aggregator optimization made explicit. It does not replace those documents. If their implementations already exist when execution starts, reuse their camera surface, accessibility policy, lazy list, and test dependencies; add missing regression coverage rather than creating parallel components. If this package lands first, later U2/U4 work must retain its acceptance cases and reuse the extracted surface.

## Global constraints

- Keep minSdk 28, compileSdk/targetSdk 37, and JVM target 17. Use pinned dependencies; no production dependency upgrade is required.
- Only `BudgetEngine` grants rewards. Retain immutable challenge terms, unique award identities, fresh camera counters, and current interruption/local-midnight rules.
- Keep camera processing offline and local. Do not record or upload frames or add analytics.
- Support Persian (`values`), English (`values-en`), French (`values-fr`), RTL, localized numbers, and light/dark themes.
- Preserve hidden-history rules, unknown versus known-zero coverage, comparison blocking reasons, and removed-app package-name fallback.
- Keep unrelated working-tree files intact. Do not modify existing UX proposals, release artifacts, exported schemas, or task status as part of these fixes.

## B1 — Visible workout preview and exit

### Layout contract

Both rewarded and exercise-only active challenges show one count, one tracking message, a flexible live preview, a full-row audio toggle, Help, and a persistent End action. End uses the existing interruption-and-close path. The standalone temporary camera trial uses the same bounded layout with Close camera, its own count, and its existing no-award explanation.

- The active screen has no outer vertical scroll. Long privacy/instruction copy moves to a scrollable in-app Help sheet; preparation retains the information before Start. Help does not replace or dispose the camera or restart the counter. Dismissing Help restores focus to its launcher.
- Measure controls before allocating preview space. Use a vertical arrangement normally and a side-by-side preview/control arrangement for wide, short constraints. Remove the fixed 360 dp preview height.
- Acceptance sizes are **320 × 480 dp** and **640 × 320 dp** of available content space, after system/host insets, at **100% and 200% font scale**. At these sizes the preview occupies at least **96 × 96 dp**, and the count, tracking text, audio toggle, Help, and End/Close are wholly reachable without scrolling the workout. Action touch targets are at least **48 × 48 dp**. Do not shrink system text or truncate essential control labels to meet this contract.
- For still smaller constraints, pin End/Close outside a scrollable secondary-controls region and shrink the preview first. Do not claim simultaneous visibility at arbitrary viewport sizes. Help may temporarily cover the preview; closing the workout must remain available from the sheet.
- Apply safe insets once at the host boundary. Test both the main navigation route and `ChallengeActivity`; avoid doubling host and child padding.

### Camera ownership contract

Extract the preview/session lifecycle from the instruction-heavy wrapper into `CameraViewport`. It receives exercise, camera choice, and a run key; owns one `CameraPoseSession` and fresh counter per run; emits `CameraPoseState`; and neither persists preferences nor awards rewards.

Keep existing CameraX viewport/projection, front mirroring, push-up alternate landmarks, debug overlay, raw-frame counting, permission refresh, disposal, and stale-callback rejection. Ignore the entire callback after disposal, including visual state. Recomposition, audio toggling, and opening Help do not rebind the camera. Active challenge identity is the run key; standalone trials use a new key for an explicit restart/lens change.

The challenge host forwards fresh progress to the existing `ChallengeSession.accept` exactly once and then updates `ChallengeFlow`. The standalone wrapper manages preferences, permission requests, lens/retry actions, and temporary count. Active challenge camera changes/retries continue to interrupt; no new in-place rewarded restart is introduced. Camera/model failures retain current recovery actions.

**B1 acceptance:** Tests show preview and exit bounds inside both target viewports without a scroll gesture, exactly one visible count, and one callback into cancellation when End is activated. Fake-camera tests prove Help/recomposition do not recreate a run and late callbacks cannot change the next run. Device checks confirm real preview framing and release on exit/background for all four exercises and both lenses.

## B2 — Progress and meaningful announcements

### Progress semantics

- Active challenge count exposes one semantics node with `ProgressBarRangeInfo(current, 0f..required.toFloat(), steps = 0)` and a localized state description equivalent to **3 of 10 repetitions**. Clamp the exposed value to the immutable target; engine progress remains authoritative.
- Hide duplicate child count semantics. Do not expose preview/debug geometry as traversable controls. Focus order follows count, status, audio, Help, End, respecting RTL reading order.
- A standalone trial has no target: expose its localized count as a state description without inventing a finite progress range.
- Do not mark per-frame or every-repetition updates as a live region. The progress node always exposes the latest count when focused; tracking/error/completion changes provide unsolicited notifications.

### Announcement policy

Map camera states to `INITIALIZING`, `SEARCHING`, `TRACKING`, `LOST`, or `ERROR`. SEARCHING and TRACKING_LOST represent the same lost-position announcement category. Emit tracking loss/recovery only after the category is stable for **1,000 ms**. Repeated frames in one category never reannounce it. Brief loss/recovery shorter than 1,000 ms cancels the pending change. Use monotonic time and cancel pending work on run change, pause, and disposal.

Use one localized, polite live-region status node. Its text follows the stable announcement category; preview indicators can update immediately. Camera/model/permission errors bypass the delay and announce once per distinct error transition. The committed completion result announces once per challenge identity, with the actual awarded amount or exercise-only success wording. Reaching the target while saving does not announce success; failed storage never announces a reward. Stable recomposition cannot replay completion.

When touch exploration is enabled, suppress the app's own repetition, position, and completion voice/tones so they do not overlap TalkBack. Preserve the saved speech preference and visible audio toggle. Effective audio is `speechEnabled && !touchExplorationEnabled`; enabling touch exploration stops current output and pending audio immediately, without writing preferences. Disabling it restores eligibility for future feedback only, without replaying old feedback. Observe accessibility changes while resumed and remove listeners on disposal.

**B2 acceptance:** Compose tests inspect the progress range and localized state description, single count, polite status node, and audio switch semantics. Clock-controlled unit tests prove 999/1,000 ms boundaries, deduplication, rapid flapping, error bypass, and reset. Real TalkBack checks keep focus on End while a repetition, sustained loss/recovery, error, and saved completion occur; record what is announced and confirm no competing app audio. Semantics tests alone cannot prove speech delivery.

## B3 — Lazy history and indexed preparation

### Rendering and data flow

Keep the existing History summaries, comparisons, ordering, day-card detail, refresh/loading/error states, and full visible-history read. Replace its `ProductPage` scroll host with one bounded `LazyColumn`.

- Emit header/summary sections as separate items, then recent and older days as individual items keyed `day:<ISO date>` with content type `day`. Recent and older ranges remain disjoint. No nested scrolling column and no single item containing every older card.
- Build the app-name map once per snapshot. `DayCard` receives only `DayOutcome`, `hasActivity: Boolean`, and the shared app-name map; it cannot access `VisibleHistory` or rescan it.
- Prepare `OutcomeSummary` and the activity-presence date set once per immutable snapshot/day/zone on `Dispatchers.Default`. Publish them together only while the snapshot generation is current. Recomposition and scroll do no aggregation. A changed snapshot, local day, or zone invalidates preparation; disposal cancels delivery. Preserve current load/error behavior, with no stale result published after refresh/history clear.
- A new snapshot starts with a fresh list state rather than restoring a stale index into removed/reordered data; recomposition of the same snapshot preserves scroll position. The list remains scrollable while unchanged loaded data is displayed.

### Indexing contract

Keep the public `OutcomeAggregator.summarize(history, today, currentZone): OutcomeSummary` signature and all business values. Introduce a private/internal per-call date index for usage, combined rows, activities, and coverage. Preserve source ordering inside each date and the existing first combined-row behavior. Resolve each `DayOutcome` once per date, including missing recent/comparison dates, then reuse it.

Day calculations, coverage assessment, scope/zone checks, and weekly activity totals consume indexed slices, never the original full lists. Retain the global hidden-through marker and hidden-day membership set; do not manufacture visible activity or coverage. Preserve exact overflow behavior and comparison-block precedence.

Expected preparation work is one pass per input list, sorting distinct dates, plus each date's existing coverage-interval sorting and per-exercise reduction. Do not claim constant memory or a strict linear bound: full-history reads, the index, and summary still retain all records/dates. Database paging and U4's 15-date initial/14-date older windows are explicitly deferred; they remain a separate implementation option if measurement shows repository cost dominates.

**B3 acceptance:** Existing outcome/coverage tests pass unchanged. New equivalence cases cover hidden history, today/future dates, coverage-only dates, unknown/zero, zone/scope changes, exercise-only modes, removed apps, and unsorted inputs. A deterministic list-visit counter demonstrates that multiplying recorded dates does not multiply full-list visits. With 1,095 recorded dates and 21,900 activities, an initial lazy composition does not materialize every older card; scrolling reaches the oldest date without duplicate keys or missing detail.

## Validation and release evidence

Capture a pre-fix reproduction and rerun the same cases after implementation. For BUG-03, use the same device/build type, fixture, and navigation path for five cold entries and five full-list scrolls per variant. Report median snapshot-read, preparation, and first-content times, peak memory, composed card count, and slow-frame fraction separately. Use test-only fixtures/probes, not production telemetry. Record device/refresh rate and the frame deadline used. Do not declare a speedup from lazy-list source shape alone or set an unsupported absolute timing target.

Release closure requires all B1–B3 functional/structural acceptance, passing relevant automated checks, real camera/TalkBack evidence, and no unexplained performance regression in the seeded comparison. If another bottleneck dominates, record it and leave the performance claim open rather than expanding silently into database paging.

Use Persian/English/French, RTL, light/dark, both target viewport/font combinations, both challenge entry points, rewarded/exercise-only/trial modes, and API 28/API 37 where available. Record build, OS/device, locale, font scale, expected/actual result, and pass/fail or blocked for each check in `docs/APP_BUG_FIX_VALIDATION_2026-10-17.md` during implementation. No such runtime evidence exists in this planning package.

## Non-goals and remaining uncertainty

No counter-threshold tuning, practice mode, challenge recovery redesign, new reward projections, history charts/reflection, database migration/paging, telemetry service, or unrelated app polish. Camera recognition quality and TalkBack utterance timing require physical testing; seeded performance depends on actual hardware and records. The acceptance sizes and debounce interval above are proposed engineering defaults grounded in the existing UX plans, not observations from the uncompleted interactive review.
