# ScrollFit local storage and transfer policy

Updated: 2026-10-26. Implementation: **complete**. Android cloud restore and device-to-device transfer qualification: **NOT RUN**.

## Policy

ScrollFit does not automatically back up or transfer app state. This includes usage and exercise activity, selected apps, settings, awards and wallet state, monitoring cursors and coverage, future feature stores, and local diagnostics. A deliberate visible-history export remains a separate product decision under F-E.

The application manifest sets `android:allowBackup="false"`. Legacy `fullBackupContent` rules explicitly exclude each app-owned storage domain. Android 12 and newer `dataExtractionRules` explicitly exclude the same domains for both cloud backup and device transfer. The rules include credential-protected and device-protected root, files, databases, shared preferences, and external app files. The root exclusions also cover database sidecars such as SQLite WAL, SHM, and journal files.

## Storage inventory

| Data | Location or owner | Exclusion / restore behavior |
| --- | --- | --- |
| Durable usage, awards, activity, sessions, target configuration, usage cursors and coverage | Room database `exercise-gate.db` under the app database directory | `database`, `root`, and device-protected equivalents are excluded. SQLite sidecars live beside the database and fall under the same paths. |
| App settings, including `monitoring_enabled` | DataStore file `files/datastore/exercise-gate-preferences.preferences_pb` | `file`, `root`, and device-protected equivalents are excluded. |
| Debug-only monitoring spike settings | Shared preference file `t02_spike.xml` | All `sharedpref` and root paths are excluded. |
| Device-local install/timeline marker | `no_backup/scrollfit-install-identity` | Android always excludes `no_backup` data from Auto Backup. It is retained through an ordinary app update and omitted from restore/transfer. |
| Optional local monitor diagnostics | In-memory `MonitorRuntime` state | Never written to disk; cleared when the process ends. |
| Bundled offline pose model | APK asset `pose_landmarker_lite.task` | App code asset, not app data. It is packaged with the application. |

The current app does not create device-protected stores or external app files. Those domains are excluded explicitly to cover later stores and device-protected equivalents.

## Restore and update behavior

On the first launch after a fresh installation, ScrollFit creates its install marker in `noBackupFilesDir`. If the package manager reports a fresh install, the app clears app-owned credential-protected and device-protected data directories plus external app files before opening Room or DataStore. This removes legacy restored balances, preferences, enabled-monitoring state, and cursors. The new install starts with default settings and no imported credit.

When an existing installation receives its first update with this marker, ScrollFit preserves its database, balances, settings, and preferences. The marker creates an install-specific boot timeline, so a cursor from a previous install or another device cannot suppress new observations. A new timeline starts from fresh observations and never backfills the gap. Android package timestamps are used to distinguish a fresh install from an update; OEM restore behavior still requires device qualification.

Access grants remain Android-managed. A restored preference cannot grant Usage Access or bind the accessibility service. Monitoring starts only after the service is connected and its current readiness checks pass. Actual backup and transfer behavior is not accepted from manifest inspection alone.

## QA procedure — not run

Use disposable app data and synthetic values only. Keep the app package name `com.example.scrollfit` and save the exact device, API level, transport, command output, source revision, and resulting app state in the I08 report.

1. **Legacy rules on API 28–30:** install the previous release on a disposable API 28–30 device or emulator, create recognizable synthetic settings/usage/award rows, and trigger a backup with the Android `bmgr` flow. Install the updated APK after uninstalling and allow restore to run before first launch. Confirm the new install has default settings, no imported Room rows or cursor, and a new local identity. Also confirm an ordinary in-place update preserves known balances and settings.
2. **Cloud rules on API 31+:** use a disposable device/emulator with a backup account or Android's LocalTransport. With the previous release installed, run `adb shell bmgr enable true`, `adb shell bmgr transport com.android.localtransport/.LocalTransport`, `adb shell settings put secure backup_local_transport_parameters 'is_encrypted=true'`, and `adb shell bmgr backupnow com.example.scrollfit`. Reinstall the updated APK and inspect both Backup Manager output and app state. With the updated build, run the same backup command and confirm no ScrollFit files are emitted. Restore the standard transport after using LocalTransport.
3. **Device transfer on API 31+:** use two disposable devices for the owner's intended OEM transfer when available. Seed synthetic state on the source, transfer it, then inspect the destination before and after first launch. The destination must have no copied app state and must require fresh access/readiness. If an OEM route is unavailable, record `BLOCKED`; do not infer success from `allowBackup=false` or the XML rules.
4. **Repeatable D2D transport check:** on a disposable Google Play device, follow Android's [official D2D test procedure](https://developer.android.com/identity/data/testingbackup#test-d2d-transfer), which enables `backup_enable_d2d_test_mode`, selects and initializes the D2D transport, runs `bmgr backupnow`, reinstalls, and restores the regular transport afterward. Record unsupported transport commands as blocked.
5. **Historical restore guard (I08-03):** create a synthetic backup with the previous app version containing `monitoring_enabled=true`, a cursor with the same boot count as the destination, and recognizable usage/award rows. Restore onto a fresh installation using the API 31+ cloud procedure. Verify the startup guard removes restored rows before repositories open, the new timeline differs from the old cursor, and no stale blocker or credit appears.

The Android backup guide notes that `allowBackup=false` may disable cloud backup while still permitting device-to-device transfer on some manufacturers. It also specifies separate rules for Android 11 and earlier versus Android 12 and later, which is why both XML files are maintained. See [Auto Backup rules and OEM behavior](https://developer.android.com/identity/data/autobackup) and [testing backup and restore](https://developer.android.com/identity/data/testingbackup).
