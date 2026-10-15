# T12 spoken counting and feedback

T12 connects optional audio to the T11 challenge flow, including advance earning, blocker entry, all four exercises and exercise-only completion. `speechEnabled` uses the existing DataStore preference and defaults to enabled. The switch is available before, during and after a challenge; muting stops current and pending output before saving. A failed preference write is visible and can be retried.

`ChallengeFeedback` is Android-free and receives accepted challenge counts, tracking state, monotonic time and persisted completion results. It permits one active message and one latest pending message. Counts expire after one second in the pending slot. Tracking warnings need two seconds of continuous loss and are separated by at least ten seconds from actual playback, including loss/recovery flapping. A queued positioning prompt remains relevant while tracking is lost; recovery discards it and stops an obsolete spoken prompt. Stuck output is stopped after four seconds (twelve for completion).

Completion replaces pending counts and is emitted once, after `ChallengeSession.complete()` returns its saved result. The amount comes exclusively from `CompletionResult.Awarded.award.amountMs`, including reduced awards, and is formatted as exact localized seconds with up to three decimal places. The success screen displays the same amount. Exercise-only success makes no earned-time claim; refusal stops feedback and clears the queue. The audio controller does not write awards or alter counting/accounting policy.

The screen-owned Android adapter uses an installed voice matching the language Android actually chose for UI strings (Persian fallback, English or French). It excludes both network-required and not-installed voices before calling `setVoice`; there is no voice installation/download action or app network permission. Missing/failed/slow initialization falls back to generated local tones and visible text. Count, positioning and completion use distinct short tones; if tone playback fails, visible equivalents remain. Device volume still controls audibility. Muting silences both speech and tones.

Cancellation, Home/background, lock, replacement intents and disposal stop output synchronously. Disposal shuts down TTS, releases tones and removes the adapter’s handler callbacks. Tokens reject late engine/tone callbacks; late initialization never replays earlier counts/results. The feedback timer runs only while the screen is resumed. Changing the audio preference does not replay past events.

Android API choices follow the official [TextToSpeech reference](https://developer.android.com/reference/android/speech/tts/TextToSpeech), [Voice offline flag](https://developer.android.com/reference/android/speech/tts/Voice#isNetworkConnectionRequired()), and [engine voice-data feature contract](https://developer.android.com/reference/android/speech/tts/TextToSpeech.Engine#KEY_FEATURE_NOT_INSTALLED). TTS service visibility uses the narrow `android.intent.action.TTS_SERVICE` query required by the TextToSpeech reference. Offline behavior relies on the installed engine honoring Android’s voice metadata; real engine output requires the phone checks below.

## Verification

- The initial feedback tests failed before the implementation existed: [initial red output](verification/T12-feedback-red.log).
- The refused-completion queue regression failed before its fix: [Gradle red](verification/T12-refusal-gradle-red.log), [standalone Kotlin/JUnit red](verification/T12-refusal-red.log).
- Review found queued warning expiry and cooldown measured from queueing. Both new regressions failed before the fix: [full-suite red](verification/T12-position-suite-red.log), [focused red](verification/T12-position-gradle-red.log).
- Final JVM results: see [Gradle output](verification/T12-gradle-core-tests.log) and [summary](verification/T12-test-summary.txt).
- [Resource and manifest checks](verification/T12-localization-checks.log) verify the three translations, format placeholders, resolved speech languages, narrow query and absence of INTERNET permission. [General structural checks](verification/T12-source-checks.log) cover XML/TOML, resource references, restricted accessibility declarations and whitespace. These do not compile Android sources.
- Android debug/release/lint/instrumentation assembly was attempted and failed at offline AGP 9.4.0 resolution, before compilation: [actual output](verification/T12-build.log). SDK/adb/emulator and physical speech output remain unverified. This invocation does not recheck Google Maven reachability.
- [Phone checklist](T12_PHONE_TEST.md): all device checks pending. The existing [DataStore instrumentation test](../platform/src/androidTest/kotlin/com/example/scrollfit/platform/data/PreferencesTest.kt) already checks muted preference persistence/reopening and remains unrun. JVM tests validate policy and voice filtering; they do not prove Android TTS lifecycle or speaker output.

Rerun core verification with the available temporary toolchain:

```bash
env JAVA_HOME=/tmp/scrollfit-toolchain/jdk-21.0.12.1+1 \
    GRADLE_USER_HOME=/tmp/scrollfit-gradle \
    ./gradlew -p verification/core :core:test --offline --console=plain
```

After restoring Android artifacts/SDK, build and run the prepared persistence tests and the T12 phone checklist. Suggested commit text: [T12_COMMIT_MESSAGE.txt](T12_COMMIT_MESSAGE.txt). No git commit or staging was performed.
