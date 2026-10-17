# ScrollFit app bug review — 2026-10-17

## Test context

- The Android emulator `emulator-5554` had `com.example.scrollfit/.MainActivity` as its top-resumed activity.
- Installed build: `0.2.0-t02`, `targetSdk 37`, debuggable.
- A filtered query of the latest 500 logcat entries returned no `AndroidRuntime` errors.
- Interactive screen testing could not be completed. The computer-use surface returned no app windows and reported native controls disabled. Findings below come from source inspection and need hands-on confirmation on the running screen.
- The existing instrumented test only checks that the package name is `com.example.scrollfit`; it does not cover product behavior.

## Findings

### P1 — Active workout puts the camera preview and exit action below a long scroll

**Evidence:** [`ChallengeScreen.kt`](../app/src/main/java/com/example/scrollfit/challenge/ChallengeScreen.kt) stacks feedback and progress above `CameraScreen`. [`CameraScreen.kt`](../app/src/main/java/com/example/scrollfit/camera/CameraScreen.kt) puts its title, privacy copy, exercise instructions, trial notice, and repetition count before a 360 dp preview; camera selection, status, and Close camera follow it in the same vertically scrolling column.

**Impact:** On shorter viewports or with larger text, the user may need to scroll to frame the camera and reach camera controls or leave an active workout. This makes the movement flow harder to use while exercising.

**Reproduction to confirm:** Start any rewarded challenge and inspect the active workout at default and 200% font scale. Check whether the preview and Close camera action are reachable without scrolling.

**Expected:** Keep the live preview and a clear exit action visible together; place supporting copy and secondary controls where they do not push these actions below the fold.

**Status:** Source-confirmed layout risk; device reproduction pending.

### P2 — Repetition and tracking changes have no explicit screen-reader progress semantics

**Evidence:** Rep count is rendered as ordinary text in [`ChallengeScreen.kt`](../app/src/main/java/com/example/scrollfit/challenge/ChallengeScreen.kt). Repetition count and camera tracking state are also plain `Text` nodes in [`CameraScreen.kt`](../app/src/main/java/com/example/scrollfit/camera/CameraScreen.kt); these changing values do not declare progress-range or live-region semantics.

**Impact:** TalkBack users may not learn that a rep was counted or that tracking was lost unless they navigate back to the text. The current structure does not provide a coherent progress value for assistive technology.

**Reproduction to confirm:** Start a challenge with TalkBack enabled, keep focus elsewhere, complete a repetition, then move out of frame. Check whether progress and tracking loss are announced without repeated manual navigation.

**Expected:** Expose the repetition count as progress and announce meaningful tracking/completion changes without announcing every camera update.

**Status:** Source-confirmed semantics gap; TalkBack behavior pending.

### P2 — History renders and rescans all saved days eagerly

**Evidence:** [`HistoryScreen.kt`](../app/src/main/java/com/example/scrollfit/ui/HistoryScreen.kt) iterates every older day into the page in one pass. Each `DayCard` also filters the full activity list for its day, repeating that scan for every rendered day.

**Impact:** As history grows, opening History can compose many cards at once and perform work proportional to days multiplied by saved activities. This can make the screen slower and increase memory use for long-term users.

**Reproduction to confirm:** Seed several years of daily history, then open and scroll History while measuring initial load and frame jank.

**Expected:** Compose older entries lazily or in pages, and index activity records by day once before rendering.

**Status:** Source-confirmed scalability risk; performance reproduction pending.

## Follow-up

Retest the three findings through the visible emulator UI, including a small viewport, enlarged text, and TalkBack. The emulator was running during this review, but the available computer-use surface could not attach to its screen, so no visual or gesture-based repro is claimed.
