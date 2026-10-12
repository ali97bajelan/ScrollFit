# Exercise Gate task status

Updated: 2026-10-12 (Asia/Tehran). Current authorization: **T01 only**, per the owner's explicit request; the attached documents' automatic continuation instruction does not expand this request. Intended device: Xiaomi Mi 11 Lite NE / Android 14, supplied by owner. No physical-device results recorded.

| ID | Status | Verification evidence | Blocker | Next action |
| --- | --- | --- | --- | --- |
| T01 | blocked | Persian Compose shell and core/platform boundaries prepared; official sources documented. Gradle 9.6.0 and Temurin 21.0.12.1 run successfully. XML/TOML/string-reference/wrapper/whitespace checks passed. Actual assembleDebug/lintDebug/core:test invocation exited 1: AGP 9.4.0 plugin resolution failed before compilation. [Build log](docs/verification/T01-build.log). No APK. | Google Maven downloads returned HTTP 404; Android SDK/adb/emulator absent and official SDK downloads also returned HTTP 404. Compilation and device behavior are unverified. | Restore access to official Google artifacts and install SDK Platform 37/Build Tools 36.0.0/Platform Tools, then rerun the documented build/check command and deliver the actual APK. |
| T02 | todo | Not run. | Depends on T01; outside current request. | Prove a 30-second single-target monitoring/blocking spike on the owner's device. |
| T03 | todo | Not run. | Outside current request. | Implement Room schema, transactional repositories and DataStore preferences. |
| T04 | todo | Not run. | Depends on T03; outside current request. | Implement and test durable budget/reward policies. |
| T05 | todo | Not run. | Depends on T02/T04; outside current request. | Integrate durable accounting and verified enforcement approach. |
| T06 | todo | Engine/model strategy chosen in T01; camera and inference not implemented or tested. | Outside current request; model not provisioned. | Bundle checksum-verified model and implement offline CameraX pipeline. |
| T07 | todo | Not run. | Depends on T06; outside current request. | Implement independent push-up counter and meaningful sequence tests. |
| T08 | todo | Not run. | Depends on T06; outside current request. | Implement independent squat counter and meaningful sequence tests. |
| T09 | todo | Not run. | Depends on T06; outside current request. | Implement jumping-jack cycles and meaningful sequence tests. |
| T10 | todo | Not run. | Depends on T06; outside current request. | Implement alternating knee-raise cycles and meaningful sequence tests. |
| T11 | todo | Not run. | Depends on T04/T06–T10; outside current request. | Connect both earning modes and exercise-only activity to exactly-once awards. |
| T12 | todo | Not run. | Depends on T11; outside current request. | Implement offline speech and visual/sound fallback. |
| T13 | todo | Not run. | Depends on T04/T05; outside current request. | Implement persistent session windows and Continue/Finish. |
| T14 | todo | Navigation shell only; no product functionality claimed. | Depends on T05/T11–T13; outside current request. | Complete accessible Persian UI and actual settings/actions. |
| T15 | todo | Not run. | Depends on T03/T11/T14; outside current request. | Implement separate usage/activity outcomes with monitoring coverage. |
| T16 | todo | Not run. | Depends on earlier implementation; outside current request. | Run automated and intended-device acceptance and deliver actual APK. |

## T01 environment and checks

- Repository had extensive pre-existing CRLF-only changes, including `.idea` and both specification files. Those were preserved. Only `gradlew` was normalized to LF, with `.gitattributes` to keep it usable on Linux.
- `command -v java javac adb sdkmanager gradle`: none present; `java -version`: command not found. No SDK or tool caches found in the inspected Linux or standard Windows user SDK/Gradle locations. No adb/emulator tooling available, so attached-device enumeration and emulator tests could not run.
- Initial `bash gradlew --version`: failed on CRLF shell syntax before reaching Java. After normalization, `bash -n gradlew`: passed.
- Sandboxed `curl -I https://dl.google.com/android/repository/repository2-1.xml` and Gradle server probe: failed with `curl: (6) Could not resolve host`.
- Outside sandbox: official SDK indexes (`repository2-1.xml`, `repository2-3.xml`), command-line tools (`commandlinetools-linux-13114758_latest.zip`) and Google Maven AGP POMs (9.4.1 and documented stable 9.4.0) returned HTTP 404. `maven.google.com` redirect also failed. This is an observed download blocker, not proof that the documented releases do not exist. Gradle GitHub distribution, Adoptium JDK and Maven Central Kotlin POM endpoints were reachable.
- Downloaded and unpacked official Temurin 21 to `/tmp/scrollfit-toolchain/jdk-21.0.12.1+1`; cache for this run is `/tmp/scrollfit-gradle`.
- Python `tomllib` parsed the version catalog and checked its version references; `xml.etree.ElementTree` parsed all main resource/manifest XML, including platform manifest: passed. These are source checks, not compilation or Android lint.
- `git diff --check` initially flagged existing CRLF line endings. Final `git -c core.whitespace=cr-at-eol diff --check`: passed (exit 0), preserving the owner's existing CRLF files.
- Build/check command: `env JAVA_HOME=/tmp/scrollfit-toolchain/jdk-21.0.12.1+1 GRADLE_USER_HOME=/tmp/scrollfit-gradle ./gradlew :app:assembleDebug :app:lintDebug :core:test --console=plain --stacktrace`. Result: exit **1**, `BUILD FAILED in 6m 43s`; `com.android.application:com.android.application.gradle.plugin:9.4.0` could not be resolved from Google, Maven Central or Gradle Plugin Portal. Build stopped during root-project configuration, before any Kotlin compilation, lint or tests. Full output is retained in [T01-build.log](docs/verification/T01-build.log). No APK files found under app/core/platform. T01 is **blocked**, not a completed bootstrap.
- Final `./gradlew --version` with the documented temporary JAVA_HOME/GRADLE_USER_HOME: exit **0**, Gradle 9.6.0 and Temurin 21.0.12.1; [actual output](docs/verification/T01-gradle-version.log). The Kotlin 2.3.21 shown by this command is Gradle's embedded scripting compiler, not the project KGP/Compose compiler selection.
- Final source validation also confirmed every UI `R.string` exists and TASK_STATUS has all 16 task rows: passed.
- No unit tests were added for display-only changes. Existing template tests do not verify exercise/accounting behavior; substantive logic tests belong to their implementation tasks. No camera, enforcement or physical-device verification is claimed.
