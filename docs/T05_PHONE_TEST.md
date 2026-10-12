# T05 phone verification — pending

Intended device: Xiaomi Mi 11 Lite NE, Android 14. Every item below is pending.
No T05 APK exists: Android configuration fails before compilation at offline AGP
9.4.0 resolution. Source/JVM results do not demonstrate Android enforcement.

Once dependencies/SDK work, run:

```bash
./gradlew :app:assembleDebug :app:assembleRelease :app:lintDebug :core:test :platform:connectedDebugAndroidTest --console=plain
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The APK path is expected only after a successful build; it is not an existing
artifact. Open **پایش و بودجهٔ برنامه‌ها** from onboarding/dashboard/settings.
Stop the T02 30-second experiment before starting production monitoring. Grant
Usage Access and enable **پایش برنامه‌ها در اسکرول‌فیت** in Accessibility. If
Android restricts sideloaded Accessibility access, review the system's app-info
restricted-settings control deliberately; follow the system UI rather than
assuming all sideloaded packages have the same flow.

1. Select two ordinary installed apps, A and B. Set A to 30 seconds, B to 45
   seconds, and combined ceiling to 50 seconds. Keep zero-base off. Save, then
   explicitly enable monitoring. Check that disabled, missing-access, stopped
   and connected states describe the actual service/access state.
2. Use A for about 10 seconds, switch to B for about 10 seconds, then return to
   ScrollFit. A and B should retain independent consumption; combined remaining
   should reflect both. Time in ScrollFit, launcher, settings, unrestricted apps
   and screen off/locked must not reduce those balances.
3. Exhaust A while combined budget remains. Check the app-specific explanation;
   system navigation, Home, quick settings and unrestricted apps stay usable.
   Return to A and confirm the blocker reappears. Waiting on the blocker does
   not consume B or earned time.
4. Continue with B until the combined total reaches 50 seconds. Both apps must
   show the combined-ceiling explanation. Settings edits preserve consumed
   totals; raising/lowering a limit must not reset consumption or replay an
   already accounted interval.
5. Deselect/reselect A and clear visible history through an available future
   control (currently only repository-level deletion exists). Neither operation
   replenishes today's usage or removes award/cursor identity. Repository
   deletion is covered by JVM checks; no UI deletion path is claimed in T05.
6. While A is active, revoke Usage Access through system settings. Overlay must
   disappear and health must show access missing, with navigation usable. Restore
   access and return to A; reconcile a fresh interval without charging permission
   downtime. Repeat by disabling Accessibility and then re-enabling it.
7. Restart the process and reopen. Today's counters survive, incomplete challenges
   cancel, and no consumption is inferred for unknown downtime. Reboot with saved
   monitoring enabled: observe whether Android binds the service. If not, the UI
   reports stopped and requires Accessibility/user action. No background receiver
   starts an Activity or service, and no exact OEM restart behavior is assumed.
8. Cross local midnight while A is in use and separately while blocked. Verify
   old/new daily consumption rows, fresh base allowance, expired old credit and
   cancellation of old-day reward challenges. Large clock or time-zone changes
   must not invent usage; check the unmonitored-gap message. Returning to an
   earlier date retains that day's counters.
9. Test split-screen and PiP. Expected policy: count only the latest observed
   foreground package, never both panes. PiP-only time is excluded when another
   package is foreground. Record actual keyboard/dialog/system-panel event
   ordering and any mismatch; this method cannot guarantee every OEM focus case.
10. Try to run the T02 spike at the same time. One monitor must report the owner
    busy state; no duplicate overlays or simultaneous ownership. Stop it, then
    reopen production setup to reconcile.
11. Disable production monitoring. No further interval is charged, overlay is
    removed, counters/unused credit remain intact, and access can be revoked.
    Critical/system/preinstalled apps, launchers, settings, dialers and enabled keyboards must be
    absent from selection and must never be blocked by stale configuration.
12. Check behavior after prolonged suspension/battery restriction. A long or
    uncertain gap is unmonitored; do not call it zero usage. Nominal abrupt-kill
    loss is roughly one 1-second checkpoint plus scheduling/storage delay, with
    trusted gaps capped at 5 seconds. Arbitrary OEM suspension has no hard bound.

Rewarded-credit enforcement additionally needs T11 detector integration; T05 has
no button that grants production credit. Exercise-only/camera trials remain later
tasks. Synthetic JVM checks cover the engine/controller policy only.

Record actual elapsed time, screenshots of status/reasons, device build, granted
access and any navigation/OEM failures. No battery-management failure has yet
been observed on this phone. Give battery-setting guidance only after identifying
the actual observed restriction. Do not claim the limit cannot be bypassed.

Official references checked 2026-10-12:

- [System accessibility-service lifecycle](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService)
- [Accessibility overlays](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#TYPE_ACCESSIBILITY_OVERLAY)
- [Usage-event reconciliation](https://developer.android.com/reference/android/app/usage/UsageStatsManager#queryEvents(long,%20long))
- [BOOT_COUNT](https://developer.android.com/reference/android/provider/Settings.Global#BOOT_COUNT)
- [Background Activity launch restrictions](https://developer.android.com/guide/components/activities/secure-bal)
