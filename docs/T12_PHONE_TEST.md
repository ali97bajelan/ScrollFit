# T12 phone checks — spoken counting and feedback

Device: Xiaomi Mi 11 Lite NE / Android 14. **All checks pending; no APK or device execution yet.** Record the installed APK/version, app language, TTS engine/voice, sound volume, observed output and pass/fail for each row. Repeat the challenge checks for advance earning, blocker entry and exercise-only.

| Check | Expected result | Observed |
| --- | --- | --- |
| First challenge, offline Persian voice installed | Audio enabled by default; Persian counts match visible accepted repetitions. | Pending |
| First challenge in airplane mode, no offline Persian voice | No download/network request or setup requirement; visible counts and short count/position/completion tones. | Pending |
| English/French app language | Matching installed offline voice or local tones; corresponding visible text. | Pending |
| Unsupported device language | Speech matches the language Android chose for app strings; no other-language voice reads Persian messages. | Pending |
| Muted before start | No count, warning or completion sound; visible equivalents still work. | Pending |
| Mute during speech; reopen app | Sound stops immediately; pending output does not drain; preference survives reopening. | Pending |
| Unmute halfway through challenge | Future counts speak; earlier repetitions are not replayed. | Pending |
| Rapid valid repetitions | Current count and only the latest fresh pending count; no growing queue or overlapping tones/speech. | Pending |
| Brief tracking loss, then recovery | No spoken prompt before 2 seconds; counts remain correct. | Pending |
| Sustained/flapping loss | Visible guidance; prompts at least 10 seconds apart from actual playback; recovery stops obsolete guidance. | Pending |
| Reduced award near daily award cap | Completion announces the exact saved reward, including fractional seconds, matching the visible result and ledger. | Pending |
| Exercise-only completion | Activity completion announcement; no earned-time claim or wallet change. | Pending |
| Refused award / midnight / storage failure | No success announcement for an unsaved or refused result. | Pending |
| Cancel/Back/Home/lock during speech or queued output | Output stops immediately; returning never replays it or resumes the cancelled run. | Pending |
| Rotate or replace challenge from blocker | Old screen releases TTS/tones; late initialization and playback callbacks remain silent. | Pending |
| Background while completion transaction is pending | A valid committed award may remain; no late/background reward speech. | Pending |
| Engine missing, slow initialization or synthesis error | Tone/visual fallback without crash or delayed replay; mute remains usable. | Pending |
| Device audio unavailable or volume zero | Visible counts, positioning and actual saved reward remain sufficient. | Pending |

Camera and accounting acceptance from T06–T11 remains pending independently. Synthetic JVM sequences cannot verify TTS engine behavior, speaker output, camera accuracy or Android lifecycle callback timing.
