# U3 — Guided setup, health recovery, and saved-state design

Date: 2026-10-17. Status: implementation proposal. Covers **UX03, UX06, UX08, UX09**.

Depends on [U1](2026-10-17-ux-foundation-design.md); supplies setup callbacks consumed by [U2](2026-10-17-ux-workout-design.md). Follow the [shared contract](../../UX_REVIEW_IMPLEMENTATION_2026-10-17.md). Goal: configure one eligible app, understand the plan, connect access, explicitly enable, and reach Home with an accurate saved summary.

## Scope and approach

Keep existing Room target records, DataStore preferences, `TargetSettings` validation, access checks, and `MonitorRuntime` service ownership. Split `MonitoringSetup` into screen content and a small coordinator with injectable persistence/access ports. Do not introduce cross-store transactions or destructive rollback. This slice makes partial application truthful and recoverable; coordinated Room/DataStore persistence is a separate architecture change.

## S1 — Four guided steps and completion

| Step | Required content | Continue/finish rule |
| --- | --- | --- |
| Choose apps | Search, refresh, eligible app icons/names, selected count, missing-app help, unavailable selected cards | At least one currently eligible selection for monitoring; exercise exploration always available |
| Set daily plan | Shared daily limit, common free allowance, session boundary; advanced per-app allowances/caps and zero-base | Valid checked input; advanced values preserve existing config |
| Connect access | Usage access and Accessibility readiness, explanation beside each action, service connection status | Required connections rechecked on resume; saving plan does not require access |
| Review and enable | Actual draft values, cap/ceiling/expiry explanation, missing prerequisites, explicit enable button | Save succeeds, actual saved config rereads, access rechecked, explicit enable succeeds |

Show `Step n of 4` and step names. Back moves to the prior step; Back from step 1 invokes the unsaved-exit policy. A step can be revisited without writing. Existing configured users may jump to a named step; moving around never enables monitoring. Expand advanced controls rather than removing caps/zero-base support. Common allowance edits apply to all selected currently eligible apps only after explicit **Apply to selected apps**; the review lists overrides.

Persist step and draft via saved instance state, including selected unavailable packages, query, advanced expansion, raw duration units/values, baseline, and dirty field identity. Restore across rotation/system permission activities and normal saved-state process recreation. On an explicit discard or a fresh app visit after force-stop, load saved values; promise no indefinite durable draft. Surface **Unsaved changes** whenever fields differ from their baseline.

After confirmed explicit enablement, show current connection status and **Go to Home**. Set `onboardingCompleted = true` in the same DataStore update as `monitoringEnabled = true`; this marks completion of onboarding, not enforcement. A failed/ambiguous enable rereads actual preferences before any success claim. **Explore exercises** can set onboarding complete without changing monitoring/access; route to exercise-only catalog. If that preference write fails, preserve the action as **Continue for now**, allow exercises for this visit, and explain the welcome screen may return.

## S2 — One actionable health card

Separate: current access/connection, earlier observation gap, most recent successfully processed observation, and displayed-data refresh time. Add optional timestamps to `MonitorStatus` for the last processed observation (`lastObservationEpochMs`, `lastObservationElapsedMs`), set only after the monitor serial queue successfully processes the observation and coverage work. Do not set them when Compose reloads preferences, publishes readiness, or retries a UI read. Preserve the previous timestamp when publishing a later failure, but label it historical; reset on new process/boot. A processed observation still does not prove that blocking worked.

Add `ProductSnapshot.refreshedAtEpochMs` only after a successful snapshot read, with the existing day/zone consistency check. Label **Data refreshed …** separately from **Last observation processed …**. Earlier gaps remain a separate notice even after current access is connected; the sticky gap bit must not force a current Disconnected label. No healthy/connected wording promises continuous enforcement.

Health primary-action precedence: saved-data read failed → **Retry saved data**; monitoring disabled → **Enable monitoring** opening Review (no automatic enable); usage missing → **Grant usage access**; Accessibility disabled or service absent → **Check Accessibility**; other runtime failure → **Retry monitoring** using configuration reconciliation. Connected/no error → no recovery button. Show each access action alongside its own row in step 3 even when another has higher card precedence. Catch launch failures and show a retryable message. Re-evaluate on resume, not by having UI query usage events. Stop remains independently available.

## S3 — Draft, save, failure, and exit contract

Setup and Settings both show a saved-summary header, dirty indicator, and reachable Save in an IME-aware action region. Invalid drafts disable Save/Enable, never Stop. Healthy readiness details collapse to keep the footer short. Use `imePadding` and system insets once, allow content to scroll above actions, and choose vertical buttons when labels do not fit. Do not assert footer fit until tested.

Back/tab navigation/About/setup links leaving a dirty editor offer **Save changes**, **Discard changes**, or **Keep editing**. With invalid input, Save is disabled, other choices remain. Leaving for a permission activity retains the draft and is not a discard. Intercept both system Back and app navigation; prevent duplicate navigation while a dialog/save is pending. Successful save updates the baseline. Explicit discard reloads the latest saved snapshot rather than a stale original.

`SetupCoordinator.save(draft)` validates the complete draft before writing; performs the existing target removals/saves and preference-only plan update; notifies `MonitorRuntime.configurationChanged()` in finally; then rereads targets and preferences. On any write failure, reread both stores and show **Some changes may have been saved. Review saved values before trying again.** Display the reread summary beside the retained draft. Recalculate dirty fields against actual saved state. If reread fails, mark saved state Unknown, disable Enable and further Save, retain the draft, and offer **Reload saved values**. Stop still works. Do not claim all-or-nothing or roll back usage/credit.

Settings owns only changed reward rules/camera/exercise/speech; merge them into the latest preferences inside `update`. Preserve speech/camera changes made by other screens for untouched fields. Restore dirty/baseline state together; never initialize a remembered baseline from an unrelated later snapshot. On a failed DataStore edit, reread before claiming nothing applied or resetting dirty state. A successful write never changes active challenge terms.

Enable and Stop are explicit commands separate from Save. Use an activation generation so Stop synchronously invalidates a pending Enable; recheck that generation in the DataStore transform. Stop first calls `pauseForDisable`, then saves `monitoringEnabled = false`. A settings/storage error must not silently re-enable monitoring. Explain if disabling the saved preference failed; keep the immediate pause and offer Retry Stop. No invalid draft or reread failure blocks the Stop action.

## S4 — App eligibility and discovery

Keep `TargetApps.list`/`protectedPackages` policy unchanged: launchable third-party apps only, excluding system and updated-system apps, disabled apps, self/system settings/navigation/dialer/permission controller, and active keyboards. Do not add `QUERY_ALL_PACKAGES` or expose an unsafe override.

**Why is an app missing?** explains the preinstalled-app restriction and protected surfaces, with Refresh. Distinguish **No eligible installed apps** from **No apps match this search**. Show a selected count independent of search, a label and icon (decorative; no duplicate TalkBack label), and package fallback when metadata fails. Fetch icons off the UI thread using a small screen-lifetime cache; do not persist bitmaps. Never remove an unavailable selected app automatically; keep an unavailable card with explicit Remove and saved values, including its allowance/cap. Refresh and permission returns preserve raw drafts and seed only newly seen apps.

## Acceptance

- New setup enables one app, records onboarding completion only after a confirmed command, and offers Go to Home. Explore exercises needs no monitoring access.
- Permission return, rotation and saved-state recreation preserve the current step and raw draft. Explicit exit/discard is predictable from setup and Settings.
- Target-write success + preference-write failure displays actual mixed state; failed reread shows Unknown, retained draft and recovery. Stop invalidates a suspended Enable and remains reachable.
- Returning from access settings refreshes readiness; an earlier gap never masks a recovered current connection. Observation and UI refresh timestamps are independently truthful.
- Search-empty/eligible-empty/unavailable-selected are distinct, and preinstalled/protected policy has not expanded.
- At 320 dp, 200% font, keyboard visible, French/Persian, users can Save, Keep editing, or Stop. TalkBack can identify each step, action, and selected app.

Use injected failing ports for save stages and suspended enable races, pure draft/action-priority tests, Compose navigation tests, and actual device access-return/keyboard/large-text checks. No atomic cross-store persistence or reliable-enforcement claim is added.
