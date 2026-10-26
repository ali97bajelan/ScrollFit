# T02 — phone verification

Target: physical Android 14 phone. **No APK currently exists; do these steps only after a successful debug build is delivered.** This is a candidate feasibility experiment. Completing source or JVM tests does not confirm phone enforcement.

The product UI is Persian. The dashboard test button is «تست پایش ۳۰ ثانیه‌ای». Accessibility service label: «اسکرول‌فیت — تست مسدودسازی».

## Install and special access

1. Install `app-debug.apk` from `app/build/outputs/apk/debug/` after BUILD SUCCESSFUL, then open ScrollFit and enter the dashboard.
2. Open the 30-second test screen. Before enabling it, use Home, Back and Settings normally; nothing should be blocked.
3. Open Usage Access settings from the test screen and allow ScrollFit. Return; its usage-access status should change to granted.
4. Open Accessibility settings and enable the ScrollFit test service, reading the service explanation first. Return; accessibility access and actual service connection should both be shown.
5. If Android reports restricted settings, inspect ScrollFit's App info overflow menu for **Allow restricted settings**, then try again. [Google's instructions](https://support.google.com/android/answer/12623953?hl=en) describe this sideloading flow; menu placement on this phone is unverified.
6. Choose one installed non-system app. If a preinstalled app is absent, use a downloaded app: the spike deliberately excludes all system/preinstalled apps plus launchers/settings/dialers. Only one target is selectable.

## Thirty-second test

7. Tap **Start new 30-second test** («شروع تست جدید ۳۰ ثانیه‌ای»). The test is explicit and separate from future daily budgets. Keep ScrollFit visible for 10 seconds: remaining test time should stay at 30 seconds.
8. Tap **Open selected app**. A small ScrollFit banner should show roughly 30 seconds and count down while you interact with the selected app. The banner must not consume target taps.
9. After about 10 seconds, go Home and use an unrestricted app for at least 10 seconds. Return to ScrollFit: roughly 20 seconds should remain. Reopen the target; the countdown should continue from that remainder rather than reset.
10. While the target is visible with time left, turn the screen off for at least 10 seconds. Unlock. The allowance should not have fallen by that screen-off duration. Record the before/after values and any delayed banner/re-block behavior.
11. Consume the remaining allowance in the target. At approximately 30 total target-use seconds, a full blocking surface should appear. Target taps should not pass through; system Home/navigation should remain usable. The blocker should state that exercise/rewards are not implemented.
12. Tap the blocker's **Home** control («رفتن به خانهٔ گوشی»), open an unrestricted app and confirm it stays usable. Reopen the selected target: it should block again with zero remaining time.
13. Use the blocker's **system panel** button («باز کردن پنل تنظیمات گوشی») or normal system gestures. Open device Settings and confirm Settings stays usable. The service opens no Activity from the background; this button requests Android's quick-settings panel. If navigation fails, test Home/gestures and Stop, and report the failure.

## Revoke access and stop

14. With the test armed, revoke Usage Access through device Settings. The overlay should disappear and ScrollFit should report missing usage access rather than claim enforcement. The target should be usable while access is absent.
15. Restore Usage Access without starting a new test. The stored allowance must remain exhausted; reopen the target and check re-blocking. Access restoration alone must not award 30 seconds.
16. Revoke the Accessibility service. Confirm the overlay goes away, target/system navigation remain usable, and ScrollFit reports missing accessibility access or a stopped service. Distinguish a checked permission from an actual service connection.
17. Re-enable the service, reopen the target and check that saved consumption is retained. Then tap **Stop test** from ScrollFit or the blocker. The target should remain usable. Stop leaves permissions granted; you can revoke both through Settings afterward.
18. Only if you explicitly want another trial, tap **Start new test**. This debug-only action resets the experimental allowance and is labeled accordingly; it is not a production budget control.

## Additional observations

- Try the same transitions with both gesture and button navigation if available. Record stuck overlays, touches passing through, banners covering controls, repeated flashes and missing window events.
- Leave an unrestricted app open, return to the target and note whether the connected service was stopped by the phone. Report any observed manufacturer battery-management message or setting; no manufacturer-specific workaround has been confirmed yet.
- Restart the ScrollFit process during a partially used test and inspect remaining time/service status after Android reconnects. Known consumption is persisted approximately once per second and at transitions; unknown downtime is paused. Abrupt kills may lose the last persistence interval plus scheduling delay. Force stop is a supported escape, and automatic recovery is not promised.
- Reboot recovery, split-screen and PiP are outside this spike's supported accounting mode. If tested, report observations without treating them as production passes. T05 will define and verify those policies.
- Confirm that camera permission is never requested during this test and that there are no exercise/reward claims.

## Verification record

Automated: 10 pure countdown tests pass; structural source checks pass. Android compilation/lint: blocked by Android plugin downloads and missing SDK. Emulator: not run; no emulator/adb available. Intended phone: **all checks above pending**.

When reporting results, include the app used, OS/build label, whether both accesses were granted and service connected, observed total target seconds, screen-off behavior, re-block result, ability to reach unrestricted apps/Settings, revocation behavior and any manufacturer restriction. Record failures as failures, then revise the approach before treating enforcement as reliable.
