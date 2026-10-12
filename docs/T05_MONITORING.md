# T05 production monitoring design

Scope: Task 5 only, including a monitoring setup screen and phone-test delivery
instructions. T02 Android execution/device feasibility is still unverified, so
this is an implementation candidate using its narrow accessibility-overlay
approach, with no reliability claim. No git commit is requested.

The main-source system-bound AccessibilityService observes package transitions
only, without text, nodes, window-content retrieval or gestures. Usage Access
provides bounded metadata reconciliation at connection/unlock/resume; it is not
polled continuously. The service serializes observations in a bounded queue and
checkpoints at most once per second while a selected target is active. Android
owns the service binding; no foreground service or background Activity launch is
introduced. Reboot relies on Android rebinding the enabled accessibility service;
if that does not happen, the UI reports stopped and requires user action.

The JVM controller flushes the previous eligible interval at transitions, routes
it through T04 spending, and derives the next block reason from durable budgets.
Screen off/lock, own UI/camera, blocker, unrestricted/critical apps, permission
loss and disabled/stopped service do not start chargeable intervals. A stable
Settings.Global.BOOT_COUNT identity retains cursor deduplication across process
restart. Failure to read a reliable boot identity stops monitoring rather than
using a new process token. Restart/reboot never reset daily counters or infer
usage during unknown downtime.

Wall/monotonic samples split observed intervals at local midnight. A narrowly
separate monitor spending method permits already-observed earlier-day segments
to update their original daily rows; ordinary T04 spending still rejects stale
dates. Old-day challenges cancel and credit does not transfer to the new date.
Clock/time-zone discontinuities discard the uncertain checkpoint interval and
reconcile; they do not manufacture elapsed time or fresh earlier-day allowances.
AndroidWallClock must follow live device time-zone changes.

Only the most recently observed foreground package is chargeable. Split-screen
charges one package, never both; visible unfocused panes and PiP-only time are
excluded when foreground metadata reports another app. Detecting all OEM focus,
keyboard and PiP cases without content access is not guaranteed and needs phone
testing. No unsupported claim of simultaneous-window visibility detection.

Nominal abrupt-kill loss is one checkpoint (1 second) plus callback/scheduling
and storage latency. Trusted observation gaps are capped at 5 seconds; longer
gaps become explicitly unmonitored, without charging inferred downtime. There
is no hard OS-wide loss bound during arbitrary suspension/force stop/OEM kills.
Queue overflow and storage/overlay failure pause monitoring with a visible
service-stopped/error state. Failed removal retains its window handle, aborts
replacement and keeps ownership until removal is confirmed. Safe/system/self
transitions and access loss dismiss immediately on the UI thread, independent
of an outstanding storage read; presentation revisions reject stale decisions. Future outcome screens must not treat gaps as zero.

The production service and debug spike share a process-local ownership lease.
Only one may charge/show overlays. Production setup tells the user to stop the
spike before enabling production; a busy owner is reported as stopped, never
healthy. Critical apps are filtered at both selection and service validation,
including self, system/preinstalled apps, launchers, settings, dialers and enabled
input-method packages (including third-party keyboards).

Monitoring enabled is durable in DataStore, off by default. A failed settings
stream latches monitoring stopped until a fresh read on resume/configuration
retry; foreground callbacks cannot restore healthy status from stale enablement.
Setup catches preference-flow errors, shows a message and exposes retry. The UI separates
disabled, missing access, stopped and healthy states. It exposes validated
allowance/combined-ceiling edits, target selection, explicit enable/disable and
system access links, without reset or test-credit controls. Overlay explanations
distinguish app exhaustion and combined ceiling; Home, quick settings and Stop
remain available. No Xiaomi-specific battery setting is prescribed before an
actual observed failure; record the owner's evidence first.

Implementation checklist:

- [x] Controller, midnight spending adapter and ownership contract/tests.
- [x] Main-source service, access/reconciliation/safety/overlay and live clocks.
- [x] Durable enable preference, setup screen and lifecycle hooks.
- [x] JVM checks, Android build/lint attempt and source configuration checks.
- [x] Independent review, README/status, phone checklist and commit message.


## Actual verification and remaining gates

Delivery record: **2026-10-13 (Asia/Tehran)**; implementation started 2026-10-12.

- Final JVM command: `env JAVA_HOME=/tmp/scrollfit-toolchain/jdk-21.0.12.1+1 GRADLE_USER_HOME=/tmp/scrollfit-gradle ./gradlew -p verification/core :core:test --offline --console=plain`.
  Exit **0**, `BUILD SUCCESSFUL in 1m 18s`: **71 tests, 0 failures/errors/skips**.
  17 controller tests, 5 failure-state tests, 49 prior budget/repository/spike tests.
  [Log](verification/T05-core-tests.log), [totals](verification/T05-test-summary.txt),
  [controller XML](verification/T05-TEST-com.example.scrollfit.core.usage.MonitoringControllerTest.xml),
  [failure XML](verification/T05-TEST-com.example.scrollfit.core.usage.MonitorFailureTest.xml).
- Initial new-type tests failed compilation before implementation; the failure
  helper tests similarly failed compilation before extraction. These are not
  claimed as behavioral failure reproduction. Independent review identified four
  Important paths: overlay replacement after failed removal, stale enablement/
  uncaught setup collection after a preference-flow error, safe-surface dismissal
  delayed behind storage, and silent usage loss when storage crossed midnight.
  All four paths were addressed in source and covered by JVM guards.
- The midnight-during-storage case was observed failing **1 test / 1 failure**
  before its fix, then passed in the full suite. [Red log](verification/T05-midnight-regression-red.log).
  A separate temporary copy under `/tmp/scrollfit-t05-regression-probe` deliberately
  reintroduced unsafe window removal/replacement, deferred immediate dismissal
  and stale readiness. Its 5 failure tests produced **4 expected failures**; this
  confirms sensitivity without modifying the workspace. [Fault log](verification/T05-failure-regression-probe.log),
  [fault XML](verification/T05-failure-probe.xml). The genuine source passes all five.
- Android command: `env JAVA_HOME=/tmp/scrollfit-toolchain/jdk-21.0.12.1+1 GRADLE_USER_HOME=/tmp/scrollfit-gradle ./gradlew :app:assembleDebug :app:assembleRelease :app:lintDebug :platform:assembleDebugAndroidTest :platform:connectedDebugAndroidTest --offline --console=plain`.
  Exit **1**, `BUILD FAILED in 2s`: offline AGP 9.4.0 resolution failed before
  compilation, manifest merge, KSP, lint or instrumentation. [Log](verification/T05-build.log).
  **No APK exists.** No current Google-server reachability check was performed.
- The Android DataStore file-reopen/concurrent-edit test now includes saved
  `monitoringEnabled`; it remains **unrun**, together with all Room tests. No
  database schema change is added in T05: the retained Room v2 cursor is reused.
- XML/TOML/resource references, protected binding, narrow service capabilities,
  bounded queue, preference-readiness/presentation routing and absence of service
  Activity launches passed structural source checks. [Output](verification/T05-source-checks.log),
  [whitespace check](verification/T05-whitespace.log). These do not prove Android
  compilation, Android coroutine/callback ordering, actual overlay removal or OEM behavior.
- Every [T05 phone check](T05_PHONE_TEST.md) is pending, including two apps, both
  ceiling reasons, permission revocation, screen off, re-add/settings, restart,
  reboot, midnight and split-screen/PiP. The T02 feasibility gate is still pending;
  no real-device reliability is assumed. Camera-to-reward and session prompts
  remain T11/T13. No Xiaomi battery-management failure has yet been observed.

T05 is **implemented with JVM verification; Android/device verification pending**.
Generated message: [T05_COMMIT_MESSAGE.txt](T05_COMMIT_MESSAGE.txt). No git commit
was created by this task.

Official API decisions (checked 2026-10-12): system-bound
[service lifecycle](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService),
[accessibility overlay](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#TYPE_ACCESSIBILITY_OVERLAY),
[bounded usage queries](https://developer.android.com/reference/android/app/usage/UsageStatsManager#queryEvents(long,%20long)),
[boot counter](https://developer.android.com/reference/android/provider/Settings.Global#BOOT_COUNT),
and [background Activity launch restrictions](https://developer.android.com/guide/components/activities/secure-bal).

Enabled-keyboard safety uses [enabled input-method metadata](https://developer.android.com/reference/android/view/inputmethod/InputMethodManager#getEnabledInputMethodList()), checked 2026-10-13; it does not read typing content.
