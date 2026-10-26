# I04 position-guide qualification matrix

Status: **IMPLEMENTED — QA NOT RUN**. This is an execution worksheet for I04/PG06, not a result report. Every row starts `NOT RUN`; retain failures and stops in the totals. Do not store participant identities or camera frames.

## Current-source retests

| Row | Current source question | Required evidence | Status |
| --- | --- | --- | --- |
| PG01-02R | Does the localized recovery cue now appear in each of the four guide screens with camera denied and airplane mode enabled? | Four exercise × locale screenshots or screen observations; no permission prompt/download | NOT RUN |
| PG01-07R | Are paused-by-default Play/Pause/Previous/Next controls available, correctly labeled, and stopped when leaving the guide? | Play, pause, step selection, leave/re-enter; selected still label and 800 ms advance | NOT RUN |
| PG02/PG03 regressions | Do framing/readiness and the physical camera transform remain valid with the current guide/demo code? | PG02 and PG03 IDs below, including real front/rear and rotation rows | NOT RUN |
| PG04/PG05 regressions | Are start, countdown, save, interruption, audio and accessibility policies preserved? | PG04/PG05 IDs below and saved-row inspection through test fixtures | NOT RUN |

The historical PG01 `FAIL` rows remain historical. These retest IDs do not overwrite the dated result in [PG01](PG01.md).

## 24 physical journey rows

Run each exercise/lens/entry combination from terms through saved result. An unavailable lens is `BLOCKED` for the physical journey and remains unavailable in recognition results; recovery behavior is still recorded.

| Exercise | Lens | Entry path | Scenario references | Status | Evidence / issue |
| --- | --- | --- | --- | --- | --- |
| Push-up | Front | Advance | PG01, PG02, PG03, PG04, PG05, PG06-02 | NOT RUN | — |
| Push-up | Front | Blocker | PG01, PG02, PG03, PG04, PG05, PG06-02 | NOT RUN | — |
| Push-up | Front | Exercise-only | PG01, PG02, PG03, PG04, PG05, PG06-02 | NOT RUN | — |
| Push-up | Rear | Advance | PG01, PG02, PG03, PG04, PG05, PG06-02 | NOT RUN | — |
| Push-up | Rear | Blocker | PG01, PG02, PG03, PG04, PG05, PG06-02 | NOT RUN | — |
| Push-up | Rear | Exercise-only | PG01, PG02, PG03, PG04, PG05, PG06-02 | NOT RUN | — |
| Squat | Front | Advance | PG01, PG02, PG03, PG04, PG05, PG06-02 | NOT RUN | — |
| Squat | Front | Blocker | PG01, PG02, PG03, PG04, PG05, PG06-02 | NOT RUN | — |
| Squat | Front | Exercise-only | PG01, PG02, PG03, PG04, PG05, PG06-02 | NOT RUN | — |
| Squat | Rear | Advance | PG01, PG02, PG03, PG04, PG05, PG06-02 | NOT RUN | — |
| Squat | Rear | Blocker | PG01, PG02, PG03, PG04, PG05, PG06-02 | NOT RUN | — |
| Squat | Rear | Exercise-only | PG01, PG02, PG03, PG04, PG05, PG06-02 | NOT RUN | — |
| Jumping jack | Front | Advance | PG01, PG02, PG03, PG04, PG05, PG06-02 | NOT RUN | — |
| Jumping jack | Front | Blocker | PG01, PG02, PG03, PG04, PG05, PG06-02 | NOT RUN | — |
| Jumping jack | Front | Exercise-only | PG01, PG02, PG03, PG04, PG05, PG06-02 | NOT RUN | — |
| Jumping jack | Rear | Advance | PG01, PG02, PG03, PG04, PG05, PG06-02 | NOT RUN | — |
| Jumping jack | Rear | Blocker | PG01, PG02, PG03, PG04, PG05, PG06-02 | NOT RUN | — |
| Jumping jack | Rear | Exercise-only | PG01, PG02, PG03, PG04, PG05, PG06-02 | NOT RUN | — |
| Alternating knee raise | Front | Advance | PG01, PG02, PG03, PG04, PG05, PG06-02 | NOT RUN | — |
| Alternating knee raise | Front | Blocker | PG01, PG02, PG03, PG04, PG05, PG06-02 | NOT RUN | — |
| Alternating knee raise | Front | Exercise-only | PG01, PG02, PG03, PG04, PG05, PG06-02 | NOT RUN | — |
| Alternating knee raise | Rear | Advance | PG01, PG02, PG03, PG04, PG05, PG06-02 | NOT RUN | — |
| Alternating knee raise | Rear | Blocker | PG01, PG02, PG03, PG04, PG05, PG06-02 | NOT RUN | — |
| Alternating knee raise | Rear | Exercise-only | PG01, PG02, PG03, PG04, PG05, PG06-02 | NOT RUN | — |

## Standalone trials

Confirm no saved challenge, activity or award for a standalone trial. Keep these separate from exercise-only challenges, which may save activity but never award credit.

| Exercise | Status | Evidence / issue |
| --- | --- | --- |
| Push-up | NOT RUN | — |
| Squat | NOT RUN | — |
| Jumping jack | NOT RUN | — |
| Alternating knee raise | NOT RUN | — |

## Eight exercise/lens recognition rows

For each row, run three separate ten-rep complete-movement trials. Each trial must detect at least 9/10 with zero extras. Also record ten partial/invalid movements and 30 seconds idle; both must produce zero reps. Keep manual and app counts separate.

| Exercise | Lens | Trial 1 / 10 | Trial 2 / 10 | Trial 3 / 10 | Partial / idle | Status |
| --- | --- | --- | --- | --- | --- | --- |
| Push-up | Front | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN |
| Push-up | Rear | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN |
| Squat | Front | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN |
| Squat | Rear | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN |
| Jumping jack | Front | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN |
| Jumping jack | Rear | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN |
| Alternating knee raise | Front | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN |
| Alternating knee raise | Rear | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN |

## Locale, layout and accessibility baseline

Each row is a locale × theme × layout profile combination. Exercise-specific controls are inspected in the physical journey rows above; this table records the shared Learn/Position/countdown/workout/help/result layout and accessibility cases.

| Locale | Theme | Profile | PG03 / PG05 refs | Status | Evidence / issue |
| --- | --- | --- | --- | --- | --- |
| Persian RTL | Light | Portrait 320 dp, 100% font | PG03-01–06, PG05-01–13 | NOT RUN | — |
| Persian RTL | Light | Portrait 320 dp, 200% font | PG03-01–06, PG05-01–13 | NOT RUN | — |
| Persian RTL | Light | Landscape 640×360 dp, 100% font | PG03-01–06, PG05-01–13 | NOT RUN | — |
| Persian RTL | Light | Landscape 640×360 dp, 200% font | PG03-01–06, PG05-01–13 | NOT RUN | — |
| Persian RTL | Dark | Portrait 320 dp, 100% font | PG03-01–06, PG05-01–13 | NOT RUN | — |
| Persian RTL | Dark | Portrait 320 dp, 200% font | PG03-01–06, PG05-01–13 | NOT RUN | — |
| Persian RTL | Dark | Landscape 640×360 dp, 100% font | PG03-01–06, PG05-01–13 | NOT RUN | — |
| Persian RTL | Dark | Landscape 640×360 dp, 200% font | PG03-01–06, PG05-01–13 | NOT RUN | — |
| English | Light | Portrait 320 dp, 100% font | PG03-01–06, PG05-01–13 | NOT RUN | — |
| English | Light | Portrait 320 dp, 200% font | PG03-01–06, PG05-01–13 | NOT RUN | — |
| English | Light | Landscape 640×360 dp, 100% font | PG03-01–06, PG05-01–13 | NOT RUN | — |
| English | Light | Landscape 640×360 dp, 200% font | PG03-01–06, PG05-01–13 | NOT RUN | — |
| English | Dark | Portrait 320 dp, 100% font | PG03-01–06, PG05-01–13 | NOT RUN | — |
| English | Dark | Portrait 320 dp, 200% font | PG03-01–06, PG05-01–13 | NOT RUN | — |
| English | Dark | Landscape 640×360 dp, 100% font | PG03-01–06, PG05-01–13 | NOT RUN | — |
| English | Dark | Landscape 640×360 dp, 200% font | PG03-01–06, PG05-01–13 | NOT RUN | — |
| French | Light | Portrait 320 dp, 100% font | PG03-01–06, PG05-01–13 | NOT RUN | — |
| French | Light | Portrait 320 dp, 200% font | PG03-01–06, PG05-01–13 | NOT RUN | — |
| French | Light | Landscape 640×360 dp, 100% font | PG03-01–06, PG05-01–13 | NOT RUN | — |
| French | Light | Landscape 640×360 dp, 200% font | PG03-01–06, PG05-01–13 | NOT RUN | — |
| French | Dark | Portrait 320 dp, 100% font | PG03-01–06, PG05-01–13 | NOT RUN | — |
| French | Dark | Portrait 320 dp, 200% font | PG03-01–06, PG05-01–13 | NOT RUN | — |
| French | Dark | Landscape 640×360 dp, 100% font | PG03-01–06, PG05-01–13 | NOT RUN | — |
| French | Dark | Landscape 640×360 dp, 200% font | PG03-01–06, PG05-01–13 | NOT RUN | — |

Additional branches remain individually required: TalkBack on/off; mute; absent offline voice; paused/static demo; front/rear × 0°/90°/180°/270° camera geometry; permission denial/settings return; unavailable lens/model; clipped, offset, near/far and occluded framing; countdown/background/process death; midnight and save races; ten-second stationary readiness; ten-minute mixed use. Use PG03-07–09, PG04-01–15 and PG05-01–14 and record a distinct result for each applicable row.

## First-use participant aggregates

Use five consenting first-time participants. Use only session codes P1–P5, no names, images or identifying notes. Keep unsuccessful and stopped attempts in the denominator. Each participant completes all four exercise tasks without coaching.

| Participant code | Exercise | Explains one-rep rule | Ready/start ≤60 s | Attempts ≤2 | Stopped / issue | Status |
| --- | --- | --- | --- | --- | --- | --- |
| P1 | Push-up | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN |
| P1 | Squat | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN |
| P1 | Jumping jack | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN |
| P1 | Alternating knee raise | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN |
| P2 | Push-up | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN |
| P2 | Squat | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN |
| P2 | Jumping jack | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN |
| P2 | Alternating knee raise | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN |
| P3 | Push-up | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN |
| P3 | Squat | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN |
| P3 | Jumping jack | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN |
| P3 | Alternating knee raise | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN |
| P4 | Push-up | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN |
| P4 | Squat | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN |
| P4 | Jumping jack | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN |
| P4 | Alternating knee raise | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN |
| P5 | Push-up | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN |
| P5 | Squat | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN |
| P5 | Jumping jack | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN |
| P5 | Alternating knee raise | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN |

## Environment and acceptance record

| Field | Value |
| --- | --- |
| Source revision | Record at QA start |
| Dirty source identity | Record at QA start |
| Device / Android API | NOT RUN; intended Xiaomi Mi 11 Lite NE / API 34 if available |
| Build variant / artifact SHA-256 | NOT RUN |
| Test commands and exit codes | NOT RUN |
| Front/rear lens availability | NOT RUN |
| Tester / date | NOT RUN |
| Open defects and blocked rows | NOT RUN |

Create the result report only during QA. This worksheet does not qualify the guide or replace the PG01–PG06 records.
