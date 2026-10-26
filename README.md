# ScrollFit — Exercise Gate

Native Kotlin/Jetpack Compose app for limiting selected apps and earning time through on-device exercise. Implemented product work includes **T01–T15**: bootstrap, persistence and budgets, production monitoring, offline camera/pose, four exercise counters, challenge-to-award integration, audio feedback, sessions, product UI and history. T16 acceptance is still pending. The UI supports Persian RTL plus English and French.

Position-guide work: [exercise position-guide tasks](docs/EXERCISE_POSITION_GUIDE_TASKS.md) — specifications, implementation plans and QA scenarios for demonstrations, camera framing, start/countdown flow and accessible guidance. The Learn/Position/workout flows are implemented; physical guide qualification remains pending under I04 and I11.

The product specification is in [the brief](exercise_gate_codex_brief.md), ordered work is in [the task list](exercise_gate_tasks.md), and historical T-task results remain in [TASK_STATUS.md](TASK_STATUS.md). The current review-delivery register tracks **I01–I11 implementation as complete with QA NOT RUN**; I09 adds revisioned off-callback safety metadata, I10 reconciles current docs/artifact provenance, and I11 prepares the explicit accessibility matrix and fixtures. See the [review task register](docs/PROJECT_REVIEW_DELIVERY_TASKS_2026-10-26.md), [current verification matrix](docs/qa/project-review/CURRENT_VERIFICATION.md), and [I11 accessibility matrix](docs/qa/project-review/I11-accessibility-matrix.md).

## Current delivery status

The source includes persisted onboarding, setup, health/dashboard, settings and history; production monitoring and a separate debug-only 30-second experiment; four offline exercise counters, challenge/award flows, optional spoken feedback and persistent session prompts. Detailed dated evidence remains linked from [TASK_STATUS.md](TASK_STATUS.md). Implementation status alone does not qualify Android behavior or the intended phone.

An untracked local **release APK is present**, but its recorded metadata says versionCode 1 while current source config says versionCode 2; source revision, signing identity and QA provenance are unknown. It is inventory only, not an accepted release artifact. The current app config is minSdk 28 / compileSdk 37 / targetSdk 35, Java target 17, versionCode 2 / versionName `0.2.0-t02`. See the [current verification matrix](docs/qa/project-review/CURRENT_VERIFICATION.md) for the hash and old test/crash records. I09–I11 builds, tests, instrumentation and phone qualification are NOT RUN.

Historical verification is recorded by task in [TASK_STATUS.md](TASK_STATUS.md). Its latest recorded core result is T14's 279 passing JVM tests; that run predates later T15 and current I09–I11 source. The unfiltered platform instrumentation run recorded on 2026-10-23 executed 22 tests: 21 passed and one failed in native MediaPipe bitmap handling. Current I09–I11 fixtures and device checks remain NOT RUN. The [current matrix](docs/qa/project-review/CURRENT_VERIFICATION.md) distinguishes these historical results from the current dirty source identity.

See [task-specific behavior and historical evidence](TASK_STATUS.md), the [project-review register](docs/PROJECT_REVIEW_DELIVERY_TASKS_2026-10-26.md), and the [current verification matrix](docs/qa/project-review/CURRENT_VERIFICATION.md). Historical evidence logs are local/ignored and may not be present in a fresh checkout.

Intended device: **Xiaomi Mi 11 Lite NE, Android 14 / API 34**, provided by the owner. No manufacturer behavior or device test outcome has been confirmed.

## Build and install

Use Android Studio compatible with AGP 9.4, with **JDK 21** selected as Gradle JDK. Install **Android SDK Platform 37**, **Build Tools 36.0.0** and **Platform Tools** through SDK Manager. Android Studio records the SDK location in untracked `local.properties`. Linux/WSL needs Linux tools; Windows needs Windows tools. Initial builds require access to Google Maven, Maven Central, Gradle Plugin Portal and Gradle distributions.

On Linux/WSL:

```bash
export JAVA_HOME=/path/to/jdk-21
export ANDROID_HOME=/path/to/android-sdk
./gradlew :app:assembleDebug :app:lintDebug :core:test --console=plain
```

On Windows PowerShell with the Windows JDK/SDK configured:

```powershell
.\gradlew.bat :app:assembleDebug :app:lintDebug :core:test --console=plain
```

The expected debug artifact after **BUILD SUCCESSFUL** is `app/build/outputs/apk/debug/app-debug.apk`. Check the current verification matrix before choosing any local APK; the inventoried release APK has a versionCode mismatch and is not a qualified build. With USB debugging enabled:

```bash
adb devices -l
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Follow [the T05 phone test](docs/T05_PHONE_TEST.md) for production integration and [the T02 phone test](docs/T02_PHONE_TEST.md) for the isolated experiment. The full product acceptance checklist remains in the brief for T16.

To run real core sources/tests without configuring Android modules:

```bash
./gradlew -p verification/core :core:test --console=plain
```

The isolated settings use the same `core/build.gradle.kts`, source files, tests and version catalog; they do not replace Android verification. For this session the temporary paths are `/tmp/scrollfit-toolchain/jdk-21.0.12.1+1` and `/tmp/scrollfit-gradle`; they are not part of the repository and may be deleted by the host. Java and Kotlin bytecode targets are both 17.

## T02 implementation and limits

The experiment is **off by default** and available through the dashboard's 30-second test button in debug builds. Its AccessibilityService declaration and experimental implementation are in `app/src/debug`; release sources contain a no-op spike bridge. T05 adds a separate main-source production service, required Usage Access permission and limited picker queries to both build types. Built manifest/release isolation remains unverified. Both services share a single ownership lease.

Select one launchable non-system app, grant both required accesses, then explicitly start a new test. System and preinstalled apps, this app, launchers, settings and dialers are excluded conservatively. There is no `QUERY_ALL_PACKAGES` permission. Both current pickers conservatively exclude system/preinstalled apps.

The system-bound AccessibilityService receives only `TYPE_WINDOW_STATE_CHANGED`. Its configuration disables window-content retrieval and gestures and declares `isAccessibilityTool=false`: this is a self-control experiment. Code reads event package metadata, never event text, source nodes or another app's screen tree. Temporary usage-event reconciliation on unlock reads only foreground/background metadata; it neither logs nor stores other apps' package history. Limited package queries follow [Android package visibility guidance](https://developer.android.com/training/package-visibility/declaring).

The blocking surface uses [TYPE_ACCESSIBILITY_OVERLAY](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#TYPE_ACCESSIBILITY_OVERLAY), supported for a connected service. It stays within the app window area, does not request fullscreen/immersive mode, and leaves system navigation available. Home and quick-settings actions are triggered by visible buttons. The T02 spike never starts an Activity from the background. T11 production blocker buttons use an explicit user-triggered PendingIntent with Android visibility/opt-in checks; see [launch handling and limitations](docs/T11_CHALLENGES.md). Special-access screens and the target app are opened only from the visible Activity.

A monotonic countdown charges prior eligible intervals once. The intended signals pause it outside the selected app, while the screen is off/locked, while the blocker is shown, and when required access is missing. Small non-touchable banners update at most once per second, only while target use is observed; there is no continuous usage-statistics polling, foreground service, wake lock or boot receiver. [UsageEvents queries](https://developer.android.com/reference/android/app/usage/UsageStatsManager#queryEvents(long,%20long)) are bounded to five minutes. [Screen broadcasts](https://developer.android.com/develop/background-work/background-tasks/broadcasts) and UI lifecycle callbacks trigger pauses/reconciliation.

Spike state uses private SharedPreferences for target, enabled state and consumed milliseconds, persisted on ticks/transitions. Reconnection retains consumption and pauses unknown downtime; monitoring is not assumed healthy merely because its permission is enabled. An abrupt process kill can lose approximately the last one-second persistence interval plus scheduling/storage delay; callback delays and actual OEM behavior are unverified. This store is not the production daily budget or award ledger.

**Start new test resets the experimental allowance.** That control is debug-only, explicitly labeled, and grants no production credit. Stop removes the spike overlay without changing access permissions. Permission loss, service disconnect, overlay/storage failure and interruption have visible states; an unavailable blocker is not reported as successful enforcement.

Only a single fullscreen target is in scope for this spike. Split-screen, PiP, window/dialog/keyboard event ordering and metadata suppression need real-device investigation; event observation can fail or pause conservatively. No reliable reboot recovery is claimed. Force stop, revocation and uninstall can bypass limits. Emergency/system navigation and unrestricted apps must remain usable. Battery restrictions have not been observed yet; record them before prescribing Xiaomi-specific changes.

## T03 persistence and verification

T03 adds Room records for target configuration, app/combined daily usage, immutable challenge snapshots, a unique award ledger, exercise activity and session state. DataStore stores reward rules, combined ceiling, zero-base mode, speech, camera, last exercise and session boundary. Per-app award caps live in Room configuration so award transactions read them consistently. App removal/re-addition and settings edits preserve counters. Clearing visible history preserves accounting records and award identities. The application owns the stores, and startup repository access cancels interrupted challenges.

The 12 new JVM checks validate atomic repository contracts, rollback, duplicate identity, preserved current-day counters and history semantics with an in-memory test adapter. They do **not** prove Room persistence. Seven file-backed Room tests and one DataStore restart/concurrent-edit test are prepared but have not run. The actual Android build/check attempt exited 1 at offline AGP resolution, before compilation, KSP, lint or instrumentation; no APK or generated schema exists. T03 is **implemented, not verified**. T04 eligibility/spending is described below; T11 now integrates camera challenges with those awards.

T03 introduced Room v1 with export enabled and no destructive migration/downgrade fallback; T04 extends it to v2. Both authentic schema exports must be retained once Android builds work. Future versions require data-preserving migrations and tests. Stored durations use integer milliseconds, with the existing reward settings explicitly in seconds. Wall and monotonic clocks are injectable. Details, API boundaries, deletion semantics and commands are in [T03_PERSISTENCE.md](docs/T03_PERSISTENCE.md). Evidence: [JVM log](docs/verification/T03-core-tests.log), [repository JUnit XML](docs/verification/T03-repository-tests.xml), [Android attempt](docs/verification/T03-build.log). A generated commit message is in [T03_COMMIT_MESSAGE.txt](docs/T03_COMMIT_MESSAGE.txt); no commit was requested.

## T04 budget and reward engine

The Android-free engine spends base time before app-specific earned credit,
clips spending to the combined ceiling, and updates app/combined/session counters
in one transaction. Reward offers disclose reduced per-app award capacity before
start. Both earning modes share policy, immutable rule snapshots and exactly-once
completion. Exercise-only remains available at caps and grants no time. Midnight
expires usable credit and cancels old-day challenges; earlier daily records stay
intact, including when the date moves backwards.

A persisted cursor per device boot rejects duplicate and overlapping intervals
across app switching, process restart and history deletion. T05 supplies the
stable boot identity, eligible chronological intervals and midnight splitting.
Android execution remains unverified; T11 now connects camera callbacks and
user-facing reward disclosure to this engine. The T02 spike still uses its experimental countdown.

The application exposes a data repository facade for settings, history and reads;
spending/challenge creation/awards go through the budget engine. The 27 new JVM
cases cover policy, rollover reads, concurrent callbacks and full rollback in a
serialized in-memory store; they do not prove SQLite/Room behavior.

Room v2 adds only `usage_cursors` with an explicit preserving 1→2 migration. Actual
Room/KSP compilation, schema exports and migration/restart/concurrency tests remain
pending the Android build environment. See [engine policy and evidence](docs/T04_BUDGET_ENGINE.md)
and [generated commit message](docs/T04_COMMIT_MESSAGE.txt). No git commit was created
by this task.

## T05 production monitoring candidate

Open **پایش و بودجهٔ برنامه‌ها** from onboarding, dashboard or settings. Select
safe apps, edit each allowance and the combined ceiling in seconds, save, grant
Usage Access and the production Accessibility service, and explicitly enable
monitoring. Zero-base is optional. Saving, removing/re-adding apps, disabling or
restarting preserves consumption. Production controls contain no reset or credit
bypass. The T02 experiment must be stopped before production takes ownership.

The main-source service observes package transitions only and reconciles bounded
UsageEvents metadata on connection/unlock/resume. It charges T04 wallets and
combined/session rows through one serialized controller; screen off/locked,
blocker, own UI/camera and unrestricted/critical-app periods are excluded by the
observed signals. App-budget and combined-ceiling overlays have distinct Persian
explanations, Home, quick-settings and Stop controls. Enabled keyboards, including third-party input methods, are also excluded from
both pickers and event eligibility. Safe-surface/access-loss
signals dismiss immediately rather than wait for storage, and stale worker
results cannot restore the blocker. Failed removal retains its window handle,
refuses replacement and holds ownership until removal can be confirmed.

Durable DataStore enablement defaults to off. UI health distinguishes disabled,
missing access, stopped and connected. Failed preference collection pauses the
service until a fresh read; setup handles read failures and offers retry. A
1-second target-only checkpoint and bounded 64-observation queue avoid continuous
history polling. A 5-second maximum trusted gap reports longer/uncertain periods
as unmonitored. Nominal kill loss is one checkpoint plus scheduling/storage
latency; arbitrary OS suspension has no hard loss bound.

BOOT_COUNT supplies same-boot cursor identity; failure to obtain it pauses
monitoring. After reboot Android must bind the enabled accessibility service;
otherwise user action is needed. No boot receiver, foreground service or
background Activity start is added. Midnight segments update their original
rows, including when storage crosses midnight. Large clock/time-zone changes
report a gap, and the Android wall clock follows the live device zone.

Split-screen charges only the latest observed foreground package, never both
panes. PiP-only time is excluded when metadata identifies another foreground
app. OEM focus, dialog/keyboard ordering, permission loss and service rebinding
remain physical-device checks. No Xiaomi battery restriction has been observed;
no manufacturer-specific setting is prescribed. This remains bypassable through
permission removal, force stop, uninstall or some OEM behavior.

The **71 JVM tests pass**, including the 22 new controller/failure cases. A
separate temporary fault probe confirmed four failure regressions detect unsafe
removal, stale enablement and deferred safe-surface dismissal; it did not alter
workspace sources. Android debug/release compilation, lint, Room/KSP and all
instrumentation remain blocked at offline AGP resolution. No APK or successful
phone enforcement is claimed. [Design/evidence](docs/T05_MONITORING.md),
[phone steps](docs/T05_PHONE_TEST.md), [commit message](docs/T05_COMMIT_MESSAGE.txt).

## T06 offline camera and pose candidate

From an exercise card, open **باز کردن دوربین** to request camera access,
select rear/front and see positioning/tracking feedback. Camera and last
exercise selections persist. Permission denial, camera/model failure and
storage errors offer recovery. A debug-only point overlay stays in memory.
T07–T10 now add temporary trial counts for all four exercises;
T11 adds the challenge lifecycle and awards described below.

CameraX preview and latest-frame analysis share an owned lifecycle and
viewport. One background worker runs the bundled MediaPipe Lite model with
CPU VIDEO inference; frames are upright and unmirrored, and only the front
preview/overlay mirrors. Generation checks reject stopped callbacks.
Camera use cases unbind on pause/exit/switch, model close follows current
inference, and display rotation refreshes the session. Confidence checks,
optional smoothing, tracking-loss feedback and stale-result rejection have
18 JVM checks. Actual preview alignment, native inference, performance and
background release remain phone tests.

The checksum-verified 5.8 MB Lite float16 version 1 asset is now bundled.
Build preparation and initialization require its pinned hash. There is no
runtime download, recording or frame upload. Android build/lint/native
instrumentation remain blocked at AGP resolution; no APK or airplane-mode
first-launch pass is claimed. [Implementation/evidence](docs/T06_CAMERA_POSE.md),
[phone steps](docs/T06_PHONE_TEST.md), [commit message](docs/T06_COMMIT_MESSAGE.txt).

## T07 push-up counter and trial

The independent `PushUpCounter` detects a stable up → down → up cycle from
one confident shoulder/elbow/wrist/hip side. It corrects projected elbow
angles for the upright image aspect ratio, smooths angles and uses distinct
up/down thresholds, stable endpoint dwell and minimum transition times.
Partial cycles, jitter, too-fast movement, expired cycles, missing required
joints and frame gaps cannot finish an old movement. Completed counts
survive tracking loss during the running trial.

Open the push-up camera screen for fixed side-view guidance and visible
completed repetitions. Counting consumes raw fresh inference on the serial
worker before UI coalescing; delayed tracking-loss feedback preserves
cumulative counts while hiding stale geometry. Camera restart, background,
lens change, retry, exit or display rotation resets this temporary trial.
It grants no time and writes no challenge/activity history; T11 owns that
integration.

All **21 new push-up tests and 89 existing JVM tests pass**. Android build,
lint and instrumentation still stop at offline AGP 9.4.0 resolution. No APK,
phone trial or measured accuracy is claimed; thresholds require device
tuning. [Detector defaults and evidence](docs/T07_PUSH_UP_COUNTER.md),
[pending phone checks](docs/T07_PHONE_TEST.md),
[generated commit message](docs/T07_COMMIT_MESSAGE.txt).

## T08 squat counter and trial

The independent `SquatCounter` counts stable standing → lowered → standing
cycles with both shoulders, hips, knees and ankles visible. Each leg's
hip-to-ankle height is normalized by torso length and compared with its own
standing reference. This supports front views where projected knee angles
may remain straight during lowering; knee angles confirm standing.
A bounded median from at least three standing observations fixes calibration
until loss/reset. Smoothing, hysteresis and plausible timing reject partial
cycles, single-leg movement, jitter, fast transitions and reappearance.
Completed repetitions survive tracking loss within the running trial.

Open the squat camera screen for Persian full-body front/slight-angle
guidance and completed counts. The existing serial camera worker feeds raw
fresh samples to the detector before UI coalescing. Camera restart resets
this temporary trial. T08 grants no reward and records no activity history;
T11 now provides a separate durable challenge entry.

All **31 squat tests and 110 existing JVM checks pass**, including three
standing-calibration outlier regressions that failed before their fixes.
Android build/lint/instrumentation still stop at offline AGP resolution;
no APK or physical-device result is available. Thresholds remain provisional
and require an actual phone trial. [Detector policy/evidence](docs/T08_SQUAT_COUNTER.md),
[pending phone checks](docs/T08_PHONE_TEST.md),
[generated commit message](docs/T08_COMMIT_MESSAGE.txt).

## T09 jumping-jack counter and trial

`JumpingJackCounter` counts stable closed → open → closed cycles from
confident shoulders, wrists, hips and ankles. Both arms and outward feet
must qualify in the same raw and smoothed pose. Aspect-corrected arm angles
and torso-normalized ankle separation use independent hysteresis, EMA,
dwell and timing. Partial/asymmetric motion, noise, too-fast transitions,
expired open holds and tracking-loss reappearance do not complete a count.
Completed repetitions survive loss within the trial; camera restart resets it.

Open Exercises → **پروانه** → **باز کردن دوربین** for Persian full-body
front-view placement guidance and completed counts. This temporary trial
grants no reward; T11 now supplies separate durable challenges and awards.

**30 new sequence tests plus 141 existing JVM checks pass (171 total)**,
including 33 ms sampling, transformations, asynchronous arm/leg noise,
loss/reappearance and reset. An isolated deliberate-fault probe confirms
four failure cases detect removed safeguards. Android compilation/lint
remain blocked at offline AGP 9.4.0 resolution; no APK or recorded physical
trial exists. Thresholds are provisional. [Detector policy/evidence](docs/T09_JUMPING_JACK_COUNTER.md),
[pending phone checks](docs/T09_PHONE_TEST.md),
[generated commit message](docs/T09_COMMIT_MESSAGE.txt).

## T10 alternating knee-raise counter and trial

`AlternatingKneeRaiseCounter` counts stable standing → left or right knee
lift → standing cycles with confident bilateral shoulders, hips, knees and
ankles. Knee height relative to torso length identifies the lifted side;
standing knee angles and knee heights confirm the supporting leg and the
both-leg return. Aspect correction, independent EMA, raw/smoothed endpoint
agreement, hysteresis, dwell and timing reject partial motion, jitter and
isolated spikes. Alternating raises and complete same-side cycles count;
switching sides without standing, bilateral lifts and expired holds discard
incomplete movement. Tracking loss retains completed trial counts and
requires a fresh cycle after stable standing.

The existing camera screen routes serial raw poses into this detector and
shows Persian full-body front guidance, confidence feedback, completed count
and a temporary/no-award notice. Camera restart resets the trial. All 203
JVM checks pass, including 32 knee-raise tests covering both sides, sustained
holds, partial returns, jitter, confidence loss, timing and transformations.
Android compilation/lint/instrumentation remain blocked at offline AGP
9.4.0 resolution. No APK or recorded phone trial exists; thresholds remain
provisional. [Detector policy/evidence](docs/T10_KNEE_RAISE_COUNTER.md),
[pending phone checks](docs/T10_PHONE_TEST.md),
[generated commit message](docs/T10_COMMIT_MESSAGE.txt).

## T11 challenge lifecycle and awards

From the dashboard, choose **Earn time for an app**, select a configured safe
target, choose any exercise, inspect its repetitions/reward/capacity, grant camera
access and Start. The production blocker offers all four exercises directly,
preselecting its target and exercise in the same preparation flow. Last exercise
and camera are restored. Changed offers require another explicit Start.
**Exercise only** works without a target or credit at either cap. New challenge
text includes Persian, English and French resources and follows locale direction.

Fresh cumulative detector progress preserves completed reps through tracking loss.
Cancel/back, background, rotation, lens/retry, camera failure and navigation end
unfinished challenges. UUID, terms and start-day are durable. Completion records
activity, status, award and wallet atomically and rechecks eligibility after
suspended I/O. Success shows the actual saved reward and an explicit return button.
Storage failure can retry the same identity. Startup recovery cancels incomplete
rows and keeps committed awards. Observed active duration excludes tracking loss
and long frame gaps. Cancelled partial activity is discarded.

Blocker entry uses a user-triggered immutable PendingIntent with visibility opt-ins;
Activity reuse prevents stacked challenge screens. Device launch acceptance and
camera behavior remain pending. **221 JVM tests pass**; Android build/lint and
instrumentation exit 1 at AGP 9.4.0 resolution before compilation. No APK exists.
[Policy and evidence](docs/T11_CHALLENGES.md), [phone checklist](docs/T11_PHONE_TEST.md),
[commit message](docs/T11_COMMIT_MESSAGE.txt). No git commit was created by this
session; concurrent owner commits were preserved.

## Architecture

| Module | Responsibility |
| --- | --- |
| `:app` | MainActivity, Persian Compose navigation, main T05 service/access/overlay/setup, T06 camera screen, T11 challenge preparation/success/Activity and debug-only T02 experiment. |
| `:core` | Android-free Kotlin/JVM contracts for pose/counters/clocks/monitoring/blocking, four exercise types and configurable proposals; separate experimental countdown with tests. Durable models/repository operations are implemented in T03; T04 adds transactional spending, reward policy and interval identity; T05 adds the monitoring controller, presentation/failure guards and ownership lease. T06 adds pose confidence/smoothing, frame admission and preview projection helpers; T07–T10 add independent push-up, squat, jumping-jack and alternating knee-raise detectors. T11 adds lifecycle progress/invalidation and atomic completion activity. |
| `:platform` | Live-zone Android clock adapters, durable monitoring preference and pinned CameraX/Room/DataStore/MediaPipe dependencies. Room/DataStore persistence is implemented in T03 with Android verification pending; T06 adds the bundled offline model, CameraX lifecycle/session and MediaPipe pose adapter; hardware verification is pending. |

Dependencies flow `app → platform → core` and `app → core`. Time units are explicit. There is no backend or DI framework. Each future exercise detector will have independent replaceable state. T03 added Room v1 and KSP; T04 adds v2 with a preserving cursor migration. Actual schema exports await successful Android builds.

## Dependency decisions and official sources

Documentation checked on **2026-10-12**. These are pinned documented choices; final Android artifact resolution and compatibility remain unverified because the build is blocked.

| Component | Selection | Source |
| --- | --- | --- |
| AGP / Gradle / SDK | 9.4.0 / 9.6.0 / compile and target 37; existing minimum 28 retained | [AGP compatibility](https://developer.android.com/build/releases/agp-9-4-0-release-notes) |
| Kotlin / Compose compiler | 2.4.10, with explicit KGP classpath; AGP built-in Kotlin | [Kotlin releases](https://kotlinlang.org/docs/releases.html), [built-in Kotlin](https://developer.android.com/build/migrate-to-built-in-kotlin), [compiler compatibility](https://developer.android.com/jetpack/androidx/releases/compose-kotlin) |
| Compose | Stable BOM 2026.09.00, Material 3 | [Compose BOM](https://developer.android.com/develop/ui/compose/bom) |
| Activity / Navigation | 1.13.0 / 2.10.2 | [Activity](https://developer.android.com/jetpack/androidx/releases/activity), [Navigation](https://developer.android.com/jetpack/androidx/releases/navigation) |
| CameraX | 1.6.2 for camera2/lifecycle/view | [CameraX](https://developer.android.com/jetpack/androidx/releases/camera) |
| Room / DataStore Preferences | 2.8.5 / 1.2.1 | [Room](https://developer.android.com/jetpack/androidx/releases/room), [DataStore](https://developer.android.com/jetpack/androidx/releases/datastore) |
| KSP | 2.3.12 for Room schema processing; actual Android compatibility remains uncompiled | [Official release](https://github.com/google/ksp/releases/tag/2.3.12) |
| Coroutines Android | 1.10.2 | [Official release](https://github.com/Kotlin/kotlinx.coroutines/releases/tag/1.10.2) |
| Pose engine | MediaPipe Tasks Vision 1.0.0 | [Official Google sample](https://github.com/google-ai-edge/mediapipe-samples/blob/main/examples/pose_landmarker/android/app/build.gradle), [pose guide](https://developers.google.com/edge/mediapipe/solutions/vision/pose_landmarker/android), [Android requirements](https://developers.google.com/edge/mediapipe/solutions/setup_android) |

Pose provisioning choice: Lite float16 model version 1, one body and CPU delegate. T06 obtained the [official model](https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_lite/float16/1/pose_landmarker_lite.task) with checksum/provenance and upstream Apache 2.0 attribution recorded, bundled as `platform/src/main/assets/pose_landmarker_lite.task`. The model is **downloaded and bundled in source**, with build and initialization integrity gates. APK packaging and first camera launch after installation in airplane mode remain unverified. There will be no first-run runtime download, recordings or frame uploads. [ML Kit pose](https://developers.google.com/ml-kit/vision/pose-detection/android) was evaluated; its beta status and the explicit MediaPipe asset workflow informed this choice.

## Configurable product proposals

Per-app base allowance: 30 minutes/day. Combined usage ceiling: 60 minutes/day. Per-app daily award cap: 20 minutes awarded, including already consumed awards. Short session: 5 minutes. Zero-base mode: off. Speech: on. Suggested camera: rear. Rewards: 2 push-ups, 10 squats, 20 jumping jacks or 20 alternating knee raises, each for 2 minutes. These are editable product proposals, not exercise prescriptions or equivalence claims.

T04 rules: local-midnight allowance reset/credit expiry; old-day reward challenges cancel at rollover without new-day awards. Elapsed use uses monotonic time. Deliberate device-clock abuse is not hardened in v1. T05 defines split-screen/PiP, reboot and accounting-loss behavior above; actual device results remain pending.


## T12 offline audio feedback

Live challenges use an installed offline voice in the language Android selected for UI strings (Persian, English or French). Speech defaults to on; its switch saves through the existing DataStore preference and is available before, during and after a challenge. Missing/failed offline speech uses short local count, positioning and completion tones, with visible equivalents. No network voice or download is selected.

Counts keep only the latest fresh pending repetition. Tracking prompts need two seconds of sustained loss and are at least ten seconds apart from playback. Completion announces the exact committed award, including reduced credit; exercise-only completion makes no reward claim. Mute, cancel, background and disposal clear output, and late callbacks cannot replay it. TTS/speaker/lifecycle behavior remains pending physical-device verification. See [T12 details](docs/T12_FEEDBACK.md).


## T13 short usage sessions

Monitoring setup includes a positive session length in seconds, default 300
(five minutes), applied independently to each selected app. Foreground time
accumulates through app switches, screen pauses and process restarts. Partial
windows and pending prompts survive monitoring toggles, history deletion and
remove/re-add; local midnight starts fresh daily rows. The debug-only T02
experiment keeps its independent countdown.

At a session boundary the production overlay offers Continue and Finish. Waiting
at the prompt consumes no time. Continue uses existing budget without exercise
or awards; Finish requests Home and preserves unused base/earned credit. A failed
Home request keeps the prompt. App/global budget exhaustion takes priority,
including when it coincides with the session boundary. Editing the boundary does
not dismiss a pending prompt. Stale/duplicate taps cannot reset a later window.

Session, wallet, combined total and interval cursor commit together in the
existing Room schema; no migration is added. Checkpoint persistence retains the
existing bounded possible loss after abrupt termination: roughly the last
one-second checkpoint plus scheduler/storage delay. Unknown downtime is never
backfilled. Overlay/navigation timing, OEM behavior, file-backed persistence and
Android compilation still require verification. See [T13 behavior/evidence](docs/T13_SESSIONS.md),
[pending phone checks](docs/T13_PHONE_TEST.md), and [commit text](docs/T13_COMMIT_MESSAGE.txt).

## T14 product screens and settings

Home now shows durable per-app usage, remaining free/earned/usable time, today's
awards and capacity, combined ceiling and monitoring health. Earn preselects its
app; Exercises opens saved exercise-only challenges for all four exercises using
the current configured rules. Daily history shows visible recorded usage and
exercise activity separately, including removed-app records. Missing monitoring
data is not treated as zero usage. Weekly outcomes and persisted coverage remain
T15.

Setup adds search and editable per-app award caps. Unavailable configured apps
remain selected until explicitly removed, and app metadata refreshes on resume.
Allowances/caps accept zero; ceilings, sessions, rewards and repetition targets
require positive whole numbers with overflow validation and Persian/Arabic digit
support. Settings saves all four reward rules, camera/speech/exercise choices and
offers monitoring disablement and confirmed history deletion that preserves
current enforcement and award identities. Welcome completion persists. Product
controls and explanations are localized in Persian, English and French.

Run `python3 verification/check_source.py` for structural checks. Prefer
`./gradlew -p verification/core :core:test` for JVM tests; the repository also
includes `verification/run_core.py` for a separately provisioned compiler/JUnit
toolchain when Android configuration is unavailable. These checks do not verify
Android UI, Room persistence, accessibility or enforcement on a phone. See
[T14 behavior/evidence](docs/T14_PRODUCT_UI.md),
[pending phone checks](docs/T14_PHONE_TEST.md), and
[commit text](docs/T14_COMMIT_MESSAGE.txt).

## T15 daily and weekly outcomes

History now presents a rolling seven-day view with daily usage, activity, awarded
credit and monitoring coverage. Recorded usage stays separate from exercise
activity and awarded time. Unknown periods are labeled as not recorded; a
coverage-gated comparison appears only when both periods have complete matching
scope and time zone. Coverage writes do not charge usage. See
[T15 behavior and verification status](docs/T15_OUTCOMES.md), the
[pending phone checklist](docs/T15_PHONE_TEST.md), and
[proposed commit text](docs/T15_COMMIT_MESSAGE.txt).
