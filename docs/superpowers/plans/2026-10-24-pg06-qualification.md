# PG06 Position Guide Qualification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to execute this plan task-by-task; use superpowers:subagent-driven-development only if explicitly selected. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Establish actual automated, device and first-use evidence before claiming the guide is ready.

**Architecture:** Reuse PG01–PG05 automated tests and task-specific scenario IDs. Maintain a human-readable report separating logic, device accuracy, accessibility and usability; failed targets create explicit defects.

**Tech Stack:** Existing Gradle/JUnit/Compose instrumentation, source checks, manual phone trials and Markdown reports.

**Spec:** [PG06](../specs/2026-10-24-pg06-qualification-design.md); [shared requirements](../../EXERCISE_POSITION_GUIDE_TASKS.md); [QA scenarios](../../qa/position-guide/PG06.md).

## Global Constraints

- No frame recording, screenshot telemetry, raw landmark persistence or participant images in evidence.
- Record PASS / FAIL / BLOCKED / NOT RUN with revision, environment, expected and actual outcome.
- Missing build/device access does not count as passing; inherited README evidence is not a fresh result.
- Release requires zero award/isolation failures, >=9/10 recognized reps in each controlled trial with zero extras, and >=4/5 first-time participants succeeding for each exercise within 60 seconds and two positioning attempts.

## Review Focus

- One exercise/lens/entry combination is silently omitted (steps 1, 3).
- Synthetic projection tests pass while real front preview is mirrored twice (step 3).
- Readiness cannot be achieved at real inference cadence (step 3).
- Failed/aborted participant attempts disappear from denominators (step 4).
- Old logs or unavailable hardware are presented as fresh success (steps 2, 5).

## Task: Evidence and release decision

**Files:** Create `docs/qa/position-guide/QUALIFICATION_REPORT.md` during execution; use all `docs/qa/position-guide/PG01.md`–`PG06.md`; store local logs under `docs/verification/position-guide/`. Modify `README.md` and `docs/EXERCISE_POSITION_GUIDE_TASKS.md` only to reflect actual implementation and verification status. Preserve historical logs/status notes.

**Interfaces:** Consumes PG01–PG05 tests and scenario IDs. Produces a report with command exit codes, 24-row entry-path matrix, 8-row accuracy matrix, layout/assistive coverage, anonymized participant aggregates and open defects. No product API changes.

- [ ] **1. Prepare report without fabricated results.** List all 24 combinations, all eight exercise/lens accuracy rows, all task scenario IDs and required layout/accessibility cases as NOT RUN. Record actual revision/device/API, lens availability and test environment. Default target is the README's Android 14 / API 34 device if available; substitutions are named.
- [ ] **2. Execute automated checks.** Run `python3 verification/check_source.py`; `./gradlew -p verification/core :core:test --console=plain`; `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:lintDebug :platform:assembleDebugAndroidTest :app:assembleDebugAndroidTest --console=plain`; then `./gradlew :app:connectedDebugAndroidTest :platform:connectedDebugAndroidTest --console=plain` with a connected device. Save commands, exit codes and test totals. If configuration/device fails, record exact failing stage and continue independent documentation/available checks. Do not install/update dependencies merely to mask a blocked result without appropriate scope.
- [ ] **3. Run physical QA.** Execute PG01–PG05 scenarios and PG06 matrix: front/rear, four exercises, three entry paths, trial isolation, push-up sides, jack overhead reach, clipping/offset/distance/occlusion, rotations, permission/errors, interruption/midnight/storage races, 320 dp/200% font, landscape/RTL, TalkBack/mute/offline voice. Run three 10-rep trials and idle/partial tests for each exercise/lens; record manual and detected counts. Include a ten-second stationary framing trial and ten-minute mixed session to expose cadence/release/freezes.
- [ ] **4. Observe first-use comprehension.** Use five consenting first-time participants without coaching. For each exercise record Learn-open time, time to ready/start, positioning attempts, whether the participant explains one rep correctly, completion/stop and confusing prompts. Keep all attempts in aggregates. Offer to stop without penalty. Do not store participant frames or identifying details. Compare each exercise to >=4/5 within 60 seconds and two attempts.
- [ ] **5. Triage and retest only affected cases.** Map every defect to scenario/task/actual evidence. If thresholds change, update PG02 spec and tests and rerun impacted framing/cadence cases; if counter logic needs work, record a separate scoped fix and keep qualification failing until verified. Repeat failed and directly affected regression tests, not unrelated exhaustive trials without cause.
- [ ] **6. Publish honest status in repo.** Fill report with PASS/FAIL/BLOCKED/NOT RUN, limitations and P0 defects. Mark task/register/README accurately; unresolved P0 or missing required evidence prevents a qualified claim. Run Markdown link/whitespace checks on changed docs. Suggested checkpoint `docs: record exercise guide qualification evidence`, under session authorization.

**Exit:** Evidence exists for each acceptance target; all required P0 cases pass before declaring the guide qualified. This plan does not authorize installing to a user's personal device, collecting participant media or changing accounting policy beyond the active implementation session's scope.
