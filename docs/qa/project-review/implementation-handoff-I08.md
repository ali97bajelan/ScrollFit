# I08 implementation handoff

Status: **IMPLEMENTED — QA NOT RUN**  
Base revision: `1500806` (`feat(monitoring): implement I03-I05 review preparation`)  
Dirty source identity: `70fb5a9240be6db27193b39239a9c388b453ef4c9a34aaea01810e8173f0f4a2` (SHA-256 of the 26 changed/relevant added files, with handoff files and unrelated pre-existing untracked evidence excluded).

## Changed files

- `app/src/main/AndroidManifest.xml`
- `app/src/main/res/xml/backup_rules.xml`
- `app/src/main/res/xml/data_extraction_rules.xml`
- `app/src/main/java/com/example/scrollfit/ScrollFitApplication.kt`
- `app/src/main/java/com/example/scrollfit/monitoring/MonitorAccess.kt`
- `app/src/main/java/com/example/scrollfit/monitoring/MonitorDependencies.kt`
- `platform/src/main/kotlin/com/example/scrollfit/platform/data/LocalInstallIdentity.kt`
- `platform/src/main/kotlin/com/example/scrollfit/platform/data/RestoreStateCleaner.kt`
- `platform/src/androidTest/kotlin/com/example/scrollfit/platform/data/RestoreSafetyTest.kt`
- `docs/PRIVACY_STORAGE_POLICY.md`

## Implementation

Automatic backup is disabled in the manifest. Both legacy backup rules and Android 12+ extraction rules explicitly exclude credential-protected and device-protected root, file, database, shared-preference and external domains from cloud backup and device transfer.

The storage inventory identifies the Room database and sidecars, DataStore file, debug spike preferences, in-memory diagnostics and device-local marker. A fresh package installation clears imported app data before Room/DataStore is opened; an ordinary update preserves balances and settings. A marker under `noBackupFilesDir` supplies an install-specific boot timeline, so a restored cursor cannot collide by matching Android boot count. New timelines start with fresh observations and never backfill.

The startup guard uses package install/update timestamps to distinguish a fresh install from an update. OEM behavior and actual cloud/D2D transports remain unqualified. Device rules and manifest inspection alone do not count as transfer acceptance.

## Fixtures written, not run

- `restoredInstallGetsFreshDeviceLocalTimelineAndReadiness`
- `firstUpdateOfExistingInstallationPreservesReadinessState`
- `independentRestoreMarkersCannotReuseTheSameBootCursor`
- `restoreCleanupRemovesImportedStoresAndKeepsTheLocalMarker`

These Android-runner fixtures are in `RestoreSafetyTest.kt`. No backup, restore, APK build, or device command was run.

## QA remaining

Scenarios: I08-01 through I08-04. Follow [PRIVACY_STORAGE_POLICY.md](../../PRIVACY_STORAGE_POLICY.md) for the storage inventory and repeatable cloud/D2D procedure. Build and instrumentation profiles:

- `./gradlew :app:assembleDebug :app:assembleRelease :app:lintDebug :app:assembleDebugAndroidTest :platform:assembleDebugAndroidTest --console=plain`
- `./gradlew :app:connectedDebugAndroidTest :platform:connectedDebugAndroidTest --console=plain`

Run legacy API 28–30 backup coverage, API 31+ cloud and D2D procedures, and the intended OEM transfer if available. Compare balances on an ordinary update and inspect actual restore contents on a disposable second installation. Unsupported transfer routes remain BLOCKED.
