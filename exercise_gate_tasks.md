# Exercise Gate — ordered implementation tasks for Codex

## Execution contract

Read `exercise_gate_codex_brief.md` fully before implementing. It is the authoritative product specification; this file defines implementation order and completion gates. If a genuine conflict exists, ask the owner rather than silently changing scope. All four exercises and both earning modes are required.

Execute tasks in order, respecting dependencies. Do not stop after writing a plan or after every task to request permission. Continue automatically whenever the next task is unblocked. Ask questions in Persian only when an answer materially affects implementation. Use documented configurable defaults otherwise. The owner installs builds and tests on their phone; they do not write code.

Maintain `TASK_STATUS.md` in the repository with one row per task: ID, status (`todo`, `in_progress`, `implemented`, `verified`, `blocked`), verification evidence, blocker, and next action. Record commands and actual results, not invented passes. An implemented feature awaiting hardware testing is not verified. At each meaningful milestone update README and the status file. Preserve existing work and repository instructions.

If a device-dependent task awaits owner feedback, prepare its APK and exact test steps, then continue independent tasks. Do not build all downstream Android enforcement around an unverified assumption. Do not label the full app complete until all required functionality and available checks pass and remaining device checks are explicitly identified. Use meaningful tests for accounting/detector logic; avoid tests that merely mirror UI code.

## T01 — Inspect environment and bootstrap

Depends on: none.

- Inspect repository instructions, existing code, build tools, SDK, emulator/device availability, and network restrictions.
- Ask phone model and Android version if unknown. Continue work that does not require the answer.
- Verify compatible stable Kotlin/Android/Compose/CameraX/Room dependencies against official documentation; choose the on-device pose engine and offline model provisioning strategy.
- Create a minimal buildable native project, module boundaries, navigation shell, README, and TASK_STATUS.md. No backend.

Completion: available build command succeeds and produces an actual debug APK, or the precise environmental blocker is recorded. Dependency/API decisions and verification sources are documented. A build failure is not a completed bootstrap.

## T02 — Prove monitoring and blocking feasibility

Depends on: T01.

- Implement an experimental single-target foreground monitor, special-access flow, short countdown, and Android-supported blocking surface.
- Check foreground transitions, screen off, restricted background launches, settings access, and escape to unrestricted apps. No unrelated accessibility content collection.
- Provide an installable spike build and a short phone test: select an app, grant access, consume a 30-second allowance, observe blocker, exit to another app, and revoke access.
- Record what emulator tests prove versus what the owner's device confirms; revise enforcement approach if needed.

Completion: actual enforcement is demonstrated on the intended device, with permission loss and manufacturer limitations recorded. If device feedback is pending, mark this gate pending and continue independent domain/camera tasks without claiming enforcement reliability.

## T03 — Durable models and preferences

Depends on: T01.

- Implement Room schema/repositories for target configuration, daily usage, challenges, idempotent award ledger, exercise activity, and session state.
- Store preferences for reward rules, per-app award caps, combined usage ceiling, zero-base mode, speech, camera, and last exercise.
- Use explicit integer time units and injectable wall/monotonic clocks. Preserve counters when changing settings or removing/re-adding an app.
- Define migration behavior and atomic operations; deleting history must not replenish today's enforced budget.

Completion: persistence survives restart and meaningful repository checks confirm atomic award identity and preserved current-day enforcement state.

## T04 — Budget and reward engine

Depends on: T03.

- Implement base-first spending, independent app wallets, combined daily consumption, per-app award capacity, midnight reset/expiry, and zero-base mode.
- Implement challenge rule snapshots and exactly-once completion awards. Reduced rewards near the cap must be disclosed before challenge start.
- Support advance and just-in-time earning with identical policy checks. Refuse usable rewards at the combined ceiling; keep exercise-only mode available.
- Prevent duplicate interval charging, credit transfers, double awards, and budget resets on process restart/settings edits.

Completion: tests cover spending boundaries, partial remaining reward capacity, duplicate callbacks, interrupted challenges, midnight during a challenge, clock/date transitions, two-app switching, caps, and re-addition. Explicitly choose/document midnight handling: old-day reward challenges cancel at rollover; activity may still be recorded, with no new-day award from the old challenge.

## T05 — Production monitoring and enforcement integration

Depends on: T02 implementation evidence and T04. Intended-device confirmation remains required for verification.

- Integrate foreground events/reconciliation with durable budget engine. Exclude camera, blocker, screen-off, and unrestricted-app intervals.
- Add permission/service health states, lifecycle recovery, supported reboot behavior, and observed battery-management guidance.
- Enforce per-app and combined ceilings with distinct explanations. Define split-screen/PiP accounting and bounded crash accounting loss.
- Prevent blocking critical/system surfaces and avoid duplicate monitor ownership or tight polling.

Completion: short real-device budgets behave correctly across app switching, screen off, access revocation, process restart, and reboot; unsupported cases are disclosed. Never claim the lock cannot be bypassed.

## T06 — Camera and offline pose pipeline

Depends on: T01.

- Implement CameraX preview, camera permission states, front/rear selection, rotation/mirroring, model initialization, and offline inference.
- Provide normalized landmark/confidence output, bounded frame processing, smoothing helpers, tracking-loss state, and optional unsaved debug overlay.
- Release camera/model resources appropriately; no recordings or frame uploads.

Completion: preview and inference work on a device, camera stops on background/cancel, unavailable camera/model errors are actionable, and offline operation is demonstrated. Model availability must not depend on a hidden first-run network requirement.

## T07 — Push-up counter

Depends on: T06 interfaces; physical tuning requires T06 device output.

- Implement side-view down/up cycle using required confident landmarks, hysteresis, smoothing, and plausible transition timing.
- Provide clear camera placement guidance. Count complete repetitions without professional form grading.

Completion: synthetic full cycles count once; jitter, partial cycles, and tracking-loss/reappearance do not create counts. Record device trial results separately.

## T08 — Squat counter

Depends on: T06 interfaces.

- Implement full down/up cycle with front/slight-angle placement guidance and independent thresholds/state.

Completion: full/partial/jitter/tracking-loss tests pass, and a device trial is recorded. No requirement for professionally correct depth or posture.

## T09 — Jumping-jack counter

Depends on: T06 interfaces.

- Implement closed → open → closed cycle with full-body front-view guidance.

Completion: full cycles count once; partial or noisy arm/leg movement and reappearance are tested; device trial recorded.

## T10 — Alternating standing knee-raise counter

Depends on: T06 interfaces.

- Implement one knee lift followed by return as one repetition; track left/right state to support alternating raises and avoid repeated counts while a knee stays raised.
- Provide full-body front-view guidance and tracking-confidence feedback.

Completion: left/right sequences, sustained raised knee, partial movement, jitter, and tracking loss are tested; device trial recorded.

## T11 — Challenge lifecycle and award connection

Depends on: T04, T06–T10.

- Add direct exercise choice from blocker and advance-earning choice from dashboard; select target app before advance earning.
- Capture unique challenge ID, reward terms, and start-day; show repetitions/reward, cancel, progress, and success.
- Complete the award transaction before announcing earned time. Duplicate callbacks/navigation must not create another award.
- Add exercise-only mode with no wallet/award, preserving completed activity separately. Process death cancels incomplete earning challenges; saved awards survive.
- Restore last exercise/camera preference and keep setup steps minimal.

Completion: all four detectors can complete either earning path; cancellation, tracking loss, rotation, backgrounding, duplicate completion, midnight, and process death cannot grant accidental credit. Exercise-only works at caps without granting time.

## T12 — Spoken counting and feedback

Depends on: T11.

- Add offline on-device counting, rate-limited positioning prompts, and actual-reward completion speech.
- Add mute and visible equivalents. Handle unavailable offline Persian TTS with documented sound/visual fallback.
- Avoid overlapping, stale, or duplicated announcements; stop on cancel/background.

Completion: speech-enabled, muted, unavailable-voice, rapid-count, cancel, and background cases verified. No network speech dependency.

## T13 — Short usage sessions

Depends on: T04, T05.

- Persist accumulated target foreground session use with editable default 5-minute boundary.
- Show Continue/Finish prompt without charging prompt time. Continue explicitly resets the session window and spends only existing budget; Finish preserves unused credit and returns safely.
- If app/global budget is exhausted, display budget blocker rather than offering free continuation.

Completion: app switching and restart cannot erase session state; Continue never awards credit or requires exercise when valid credit remains; Finish and prompts do not spend credit. Check session/budget boundary collision.

## T14 — Complete product screens and settings

Depends on: T05, T11–T13.

- Finish onboarding, launchable app picker, limits/reward configuration, dashboard, blocker, exercise, success, history, and settings in accessible Persian RTL UI.
- Show app budget, award capacity, combined ceiling, monitoring health, and clear reason for each blocked action.
- Validate zero/positive settings appropriately; defaults are configurable proposals, not mandatory amounts.
- Add disable monitoring and history deletion controls without deleting current-day enforcement state or breaking records needed for duplicate-award prevention.

Completion: all required paths reachable; mixed Persian/numbers readable; permissions and camera failures recover gracefully; no placeholder primary actions.

## T15 — Daily and weekly outcomes

Depends on: T03, T11, T14.

- Show usage separately from exercise repetitions/active duration and earned minutes, with per-app and combined totals.
- Label unmonitored periods and only compare adequate recorded baselines. No calorie estimates, inferred fitness improvements, or missing-data-as-zero claims.
- Use neutral supportive language; no guilt or destruction of historical progress after a missed day.

Completion: representative data displays consistently with ledger/accounting, including empty history, gaps, rollover, and exercise-only activity.

## T16 — Integrated verification and delivery

Depends on: T01–T15 implementation, with unresolved gates disclosed.

- Run appropriate build, lint, and logic tests. Resolve failures before declaring completion.
- Follow every physical-device acceptance item in the project brief, including two apps, both earning paths, all four exercises, caps, sessions, permission loss, restart/reboot/midnight, camera lifecycle, and offline execution.
- Check for duplicate interval charging, excessive background activity, queued frames, stale speech, stored camera media, or accidental network inference.
- Deliver final debug APK, setup/install instructions, concise architecture description, task status, actual verification results, and known limitations. Keep debug-only bypasses clearly isolated and disabled in normal operation.

Completion: installable artifact exists, automated checks pass, intended-device verification is recorded or explicitly pending, and no requested feature is silently omitted. If owner testing is pending, state precisely which checks remain; label the result a candidate build rather than fully verified.

## Start instruction

Read the project brief, inspect the repository, then start T01. Continue through the ordered tasks without requiring the owner to prompt each step. Keep TASK_STATUS.md current so another Codex session can resume from the first unfinished task without recreating completed work.
