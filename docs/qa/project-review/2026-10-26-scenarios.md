# ScrollFit review delivery — QA scenarios

Date: **2026-10-26**. **Execution state: every scenario below is NOT RUN.** No result in the source review or old QA packages is a fresh result for these scenarios.

Read [task register](../../PROJECT_REVIEW_DELIVERY_TASKS_2026-10-26.md), [specification](../../superpowers/specs/2026-10-26-project-review-delivery-design.md) and [two-phase plan / command profiles](../../superpowers/plans/2026-10-26-project-review-delivery.md). All scenario execution belongs to the task's **QA phase**. During **Implementation**, write tests and prepare fixtures/handoff without running Gradle, builds, Gradle lint, emulator/instrumentation or physical-device acceptance.

## Execution and evidence contract

- Methods: **JVM** = deterministic logic/orchestration tests; **Android** = instrumented platform/UI tests; **Phone** = actual device/physical interaction; **Review** = source, documentation, artifact or privacy inspection; **Measure** = repeatable performance/aggregate observation. Emulator evidence cannot substitute for physical pose trials or the intended OEM enforcement gate.
- Start a per-task report at `results/<ID>.md` when QA begins. Each scenario gets PASS / FAIL / BLOCKED / NOT RUN, date/tester, exact source revision and dirty-diff identity, command/exit code or manual steps, expected/actual, device/API/locale/lens/font/theme, evidence path and issue. Record N/A only for a truly inapplicable optional branch with a reason; missing required hardware/access is BLOCKED.
- Store logs/test XML/synthetic UI screenshots under `docs/verification/project-review/<ID>/`. No participant frames, raw landmarks, screen contents from other apps, raw accessibility events, private app timelines or identifiers. Use synthetic app labels in fixtures and aggregate device observations.
- Before device checks, record actual hardware, lens availability, app build/version/variant/hash and install provenance. Intended phone: physical Android 14 / API 34 phone. Let the owner grant Usage Access/accessibility/camera deliberately. Record rejected/denied grants; do not treat permission display as enforcement.
- For accounting, capture synthetic fixture balances/session/cursor/award/activity rows before and after. Compare milliseconds and unique identities, not rounded UI alone. Do not add production reward injection or debug bypasses.
- A task passes only after all required scenarios pass with linked evidence. A process crash is FAIL and leaves unexecuted tests NOT RUN; a partial green XML subset is not a passing full suite. Record discovered/executed/failure/error/skip totals.
- On failure, file the exact reproduction and return the correction to **Implementation**, with tests still deferred until the next **QA** pass. Rerun failed and affected regression cases; invalidate prior evidence for changed behavior. Do not repeatedly run unrelated broad suites without a reason.

Report template (copy during QA):

```markdown
# <ID> QA result
Implementation: <revision, dirty-diff identity, handoff>
QA date/tester: <actual>
Environment: <OS/device/API/lens/locale/theme/font/size>
Artifact: <variant/version/SHA-256/install provenance>
Commands: <exact commands, exit codes, log paths, test totals>

| Scenario / expanded matrix row | Expected | Actual | Result | Evidence / issue |
| --- | --- | --- | --- | --- |
| <ID-01> | <contract> | Not observed | NOT RUN | — |

Accounting/cleanup assertions: <actual before/after or N/A with reason>
Limitations and unrun cases: <actual>
Decision: <PASS / FAIL / BLOCKED; acceptance scope>
```

## I01 — Core test classpath

| ID / method | Given | Action | Expected / required evidence |
| --- | --- | --- | --- |
| I01-01 / JVM | Clean execution-time core source, coroutine imports including CoverageRecorderTest | Run full CORE profile with no filter/exclusion | Compilation succeeds; all current tests including CoverageRecorderTest execute; zero failures/errors; totals/XML and exact command retained |
| I01-02 / Review + JVM | Catalog/core dependency change | Inspect resolved test classpath and `core` main/test imports | Coroutine core/test use version 1.10.2; no Android plugin/runtime introduced; isolated build uses the real core source/tests |
| I01-03 / Review | Fresh core results and old counter-only 114-test record | Reconcile current matrix and history | New totals are actual, excluded historical runs remain labeled; no claim that CORE verifies Android/native behavior |

## I02 — Bitmap ownership

| ID / method | Given | Action | Expected / required evidence |
| --- | --- | --- | --- |
| I02-01 / Android native | One valid caller-owned blank software bitmap and increasing timestamps | Call native estimate repeatedly with the same bitmap, then access original dimensions/pixels; execute POSE followed by full DEVICE | Original remains unrecycled/readable until caller closes it; wrapper/copy released each call; no recycled-bitmap warning/SIGSEGV; all native tests complete |
| I02-02 / Android + injected factory | Already recycled input; wrapper-construction/inference failures; no-pose result | Exercise each branch, close twice, and attempt estimate after close | Recycled/closed inputs rejected before native call; owned copy released exactly once on every path; caller original preserved on failure; timestamps remain strictly increasing |
| I02-03 / Android | Full-frame crop and partial crop with rotations 0/90/180/270 | Run all eight crop/rotation pairs and an alias-producing no-op transform | Correct upright dimensions/aspect; each distinct bitmap released once by its owner; ImageProxy closed; no read after disposal; anatomy never mirrored by the estimator |
| I02-04 / Android + Phone + Measure | Inference is active and results queued for delivery | Stop/switch/background/close during inference; run ten-minute front/rear mixed use | Model closes after in-flight work; no stale callback/recycled input; camera released on exit; allocation/cadence/thermal observations and copy cost recorded, no unsupported performance claim |

## I05 — Initialization retry

Implementation fixture and handoff: [I03–I05 implementation handoff](implementation-handoff-2026-10-26.md).

| ID / method | Given | Action | Expected / required evidence |
| --- | --- | --- | --- |
| I05-01 / JVM + Android | Boot identity, persistence or metadata fails once (separate rows) | Connect; observe FAILED; clear fault and explicitly retry | INITIALIZING → FAILED → INITIALIZING → READY; worker/collector count one; no service rebind required; permission/enabled unchanged; only eligible fresh processing can acquire lease |
| I05-02 / JVM | Initialization suspended behind a latch | Submit ten retry taps, then release latch | One active attempt, one consumer, no duplicate registration/lease; result accepted only for current generation |
| I05-03 / JVM + Android | Startup/retry suspended | Disable monitoring or disconnect/destroy, then release old completion (each branch) | Disabled/stopped remains authoritative; no resurrected worker/overlay/healthy state; acquired resources cleaned; Stop doesn't wait for suspended storage |
| I05-04 / JVM + persistence | Existing consumed usage, earned balance, session and cursor; startup gap | Recover, then process fresh samples | Existing balances/identities intact; gap remains visible; no downtime spending/backfill; recovery processes only newly trusted intervals |

## I06 — Boundary failures

Implementation handoff: [I06 implementation details and unrun fixtures](implementation-handoff-I06.md).

| ID / method | Given | Action | Expected / required evidence |
| --- | --- | --- | --- |
| I06-01 / JVM + Android | Reconciler throws IllegalStateException rather than SecurityException | Request reconciliation while prior status was healthy | Exception handled at reconciliation boundary; phase RECONCILIATION, failed/stopped eligibility, coverage invalidated, no unhandled child exception or stale healthy state; retry works after fault clears |
| I06-02 / JVM + Android | Usage watcher registers; receiver registration fails | Start and cleanup; separately make unregister throw | Only successful registrations are removed, once; other cleanup still runs after removal error; failed registration cannot publish READY/healthy; no duplicate resource on retry |
| I06-03 / JVM | Cancellable metadata/preference/storage suspension | Cancel service/retry scope; inject representative exceptions in each phase separately | Cancellation propagates; no STORAGE label for every unrelated cause; failure includes sanitized phase/code without exception/private payload; no late presentation |
| I06-04 / JVM + Android | Budget read suspended; blocker present; overlay removal may fail | Switch to Settings/Home/self/revoked access before releasing storage, then simulate removal error | Immediate dismissal attempt occurs before storage completes; old result cannot restore blocker; failed removal retains ownership and prevents duplicate overlay; all cleanup paths remain retryable |

## I07 — Diagnostics

Implementation handoff: [I07 implementation details and unrun fixtures](implementation-handoff-I07.md).

| ID / method | Given | Action | Expected / required evidence |
| --- | --- | --- | --- |
| I07-01 / JVM + Review | At least 10,000 sanitized observation/failure events | Record snapshots and inspect schema/output | Last 128 records only; fixed-size aggregate counters correct; bounded memory; no packages/text/raw events/camera/stack trace or unbounded history |
| I07-02 / JVM + Android | Last observed monotonic instant and a changed wall clock/new process/boot | Advance elapsed time; regress wall time; restart | Same-process observation age follows monotonic time; cross-process/boot age Unknown; snapshot scope says since process start; no negative/stale-current age |
| I07-03 / JVM + Measure | Slow processing, queue high-water and dropped commands | Fill/overflow queue, recover and inspect counters/retry results | Accurate high-water/dropped/latency count-total-max, no synchronous storage on diagnostic writes; gap/retry visible without claiming non-target idle is a live selected-app countdown |

## I08 — Backup and transfer

Implementation handoff: [I08 implementation details and unrun fixtures](implementation-handoff-I08.md). Storage policy: [PRIVACY_STORAGE_POLICY.md](../../PRIVACY_STORAGE_POLICY.md).

| ID / method | Given | Action | Expected / required evidence |
| --- | --- | --- | --- |
| I08-01 / Review + Android | Sensitive synthetic state in every used database/file/shared-preference/device-protected domain | Inspect merged manifest/rules and perform legacy/API31+ cloud backup/restore on disposable installs | Explicit exclusions cover all application state including WAL/SHM and feature stores; inspection of actual output/restore confirms absence; flags alone do not pass |
| I08-02 / Phone | API31+ and intended OEM device-to-device transfer available | Transfer disposable synthetic app state and inspect destination | No excluded state copied; fresh permissions/readiness needed; unavailable transfer route BLOCKED, OEM limits named rather than promised away |
| I08-03 / Android | Old synthetic backup contains enabled monitoring, stale cursor and award/usage rows | Restore/inject as a legacy-upgrade fixture on a second install and process fresh observations | Restored preference cannot grant readiness; device-bound cursor cannot suppress new-device usage; no phantom credit/backfill or stale overlay; record how legacy state was safely handled |
| I08-04 / Android + Phone | Current install has known consumed/earned/session/cursor data | Ordinary app update with new backup rules | Existing current-device state unchanged; fresh exclusions don't reset balances; policy copy accurately distinguishes automatic backup, OEM limitations and deliberate F-E export |

## I09 — Callback latency

Implementation handoff and QA-only stage-timing fixture: [I09 handoff](implementation-handoff-I09.md). The fixture reports sample count, median, p95 and max; its printed values are not results until run during QA.

| ID / method | Given | Action | Expected / required evidence |
| --- | --- | --- | --- |
| I09-01 / JVM + Measure | Slow metadata latch and active overlay | Switch target → safe/unknown while latch remains held | Dismissal/invalidation occurs before latch release; callback performs no blocking package/role lookup; no old result reinstates overlay |
| I09-02 / JVM + Android | Package becomes launcher/dialer/keyboard or is removed while snapshot refresh is pending | Deliver same-package and changed-package events; complete older refresh after newer one | Protected/uncertain package is not charged/blocked; old revision ignored; refreshed eligibility comes from current snapshot; critical surface remains usable |
| I09-03 / JVM | Budget worker paused and queue capacity 64 | Submit 65 observations; separately process sample aged 5,001 ms; recover with fresh metadata | Bounded queue, dropped count/gap, stale observations discarded, charging/overlay paused safely; no capacity increase or backfill; fresh recovery succeeds |
| I09-04 / Measure + Phone | Matched baseline/changed builds, workload, device and history | Repeat rapid app switches with slow metadata/storage; measure callback/queue/storage timing | Sample count/median/p95/max and before/after recorded; no callback waiting for metadata, no unresolved safe-surface regression; observed slowdown described without an invented ANR diagnosis |

## I10 — Documentation and provenance

Current source/artifact inventory: [CURRENT_VERIFICATION.md](CURRENT_VERIFICATION.md). The local APK is inventory-only until exact tested source and signing provenance are established.

| ID / method | Given | Action | Expected / required evidence |
| --- | --- | --- | --- |
| I10-01 / Review | README/status/config/current task register | Cross-check delivery claims, min/compile/target SDK and version | Current overview reflects source and pending QA; baseline 28/37/35, JVM17, versionCode2 / 0.2.0-t02; old target37 assumptions identified; dated history not rewritten |
| I10-02 / Review | Local APK plus saved full/filtered/native XML | Hash artifact; compare source identity, test totals, crash/incomplete records | Inventory doesn't imply current provenance/signing/acceptance; current matrix gives commit/dirty identity, command/date/hash/variant/device; prior 22 executed/one failure and core exclusion remain explicit |
| I10-03 / Review | All new Markdown and any fresh QA reports | Run DOC; trace every review item to spec/plan/scenario and every claimed PASS to evidence | Links/anchors resolve, 22 task IDs covered, two phases each; no prefilled success, skipped case or blocked gate described as qualified |

## I11 — Accessibility and physical use

Expand conditions into explicit rows rather than a single group PASS. The [I11 accessibility matrix](I11-accessibility-matrix.md) has 828 rows for Welcome/setup/health/Home/settings/history/Learn/Position/countdown/workout/help/result/blocker; Persian RTL/English/French; light/dark; portrait 320 dp/landscape 640×360 dp; font 100%/200%; TalkBack; and mute/no-offline-voice/reduced-motion branches. All rows start NOT RUN. Shared I04 rows may reuse evidence only with identical source/build/conditions.

| ID / method | Given | Action | Expected / required evidence |
| --- | --- | --- | --- |
| I11-01 / Android + Phone | Every listed screen at smallest width/largest font and short landscape | Navigate, open keyboard/help/recovery, scroll and activate Start/End/Stop | Essential actions visible/reachable, no cropped/covered action or double inset, 48 dp targets, readable long labels and numbers; preview yields space to controls |
| I11-02 / Android + Phone | TalkBack on with an exhausted budget/disabled Start/failed setup | Traverse and activate screen/blocker actions | Logical order, labels/value/state/reason available; focus reaches Stop/recovery; no focus trap or color-only/error-only cue; actual blocker tested, not Compose fixtures alone |
| I11-03 / Review + Phone | Light/dark, mute/missing offline voice and reduced motion | Read statuses, play/pause demo, perform workout/help/countdown | 4.5:1 normal/3:1 large text, 3:1 essential overlay strokes; visual equivalents; no contradictory overlapping speech; demo paused/static fallback and explicit controls usable |
| I11-04 / Phone | Both lenses, Persian RTL and rotations 0/90/180/270 | Compare preview image rectangle/letterbox and physical limb movement | Front mirrors once, rear not mirrored, anatomy unchanged by RTL, guide aligns inside actual image; no transform mismatch or unreachable camera recovery |

## I04 — Guide qualification

The row-by-row I04/PG06 worksheet is [I04-qualification-matrix.md](../position-guide/I04-qualification-matrix.md). It is preparation only; execute and report each row during QA.

| ID / method | Given | Action | Expected / required evidence |
| --- | --- | --- | --- |
| I04-01 / Android + Phone | PG01 historic 7 PASS / 2 FAIL; current recovery/demo fixes | Re-run affected PG01 cases then PG02–PG05 scenarios | Actual current behavior recorded; fixes not assumed qualified or refiled from old copy alone; practice/countdown never save reps/rewards; stale callbacks rejected |
| I04-02 / Phone | Four exercises × two lenses × advance/blocker/exercise-only plus standalone trials | Execute all 24 journeys and four trial-only journeys, including guide/help/countdown/start/result | Every combination has row/evidence; correct target/terms/actual reward; standalone has no saved activity/credit; unavailable lens shows recovery and remains unavailable for recognition qualification |
| I04-03 / Phone + Measure | Eight exercise/lens pairs and controlled framing | Per pair run three ten-rep valid trials, ten partial/invalid movements, 30 s idle, clipping/offset/occlusion recovery, 10 s stationary trial; >=10 min mixed use | Each valid trial >=9/10 detected and zero extras; invalid/idle zero; readiness achievable within 10 s; manual/app counts, cadence/freezes/thermal/release observed. Counter/gap-threshold failure stays open, not silently tuned |
| I04-04 / Observed usability + Phone | Five consenting first-time participants; full PG06 accessibility/reliability matrix | Ask each to explain a rep and position/start each exercise without coaching; execute PG04 permission/interrupt/save/midnight races and I11 matrix | >=4/5 per exercise explain correctly/start within 60 s and <=2 attempts; stops/failures retained; no images/identifiers. Zero duplicate/practice/stale rewards/false save success; full PG06 report links all evidence |

The full [PG06](../position-guide/PG06.md) and PG01–PG05 scenarios remain required, including push-up both visible sides, jacks overhead space, knee-raise cycles, permanent denial/settings return, unavailable lens/model, background/process death, Help and midnight/save races. These four groups are routing IDs, not permission to shrink that matrix.

## I03 — Production monitoring and reward loop

The Room-backed deterministic fixture and implementation handoff are listed in [the I03–I05 handoff](implementation-handoff-2026-10-26.md). Fixture results do not replace production service/device QA.

| ID / method | Given | Action | Expected / required evidence |
| --- | --- | --- | --- |
| I03-01 / Phone + Android | Two eligible selected apps, finite per-app base and shared ceiling, production service enabled by owner | Spend in A/B to exhaust each ceiling separately; compare persisted before/after state | Correct per-app/shared blocker reason; no guessed non-target/own UI/blocker-wait use; permission granted alone isn't pass; T05 expected accounting within documented observed checkpoint bounds |
| I03-02 / Phone + persistence | Current exhausted/earning-capacity fixtures for all four exercises | Complete each through advance and blocker (eight earning journeys); also exercise-only and trial paths; replay completion callbacks in fixture | Exactly one durable award/activity per completed eligible challenge; full/reduced/refused cases truthful; re-open target, spend earned time and reblock correctly; exercise-only no credit, trials no records |
| I03-03 / JVM + Phone | Current session prompt near budget exhaustion/midnight/configuration change | Wait, double-tap Continue/Finish, suspend storage, edit policy or roll day; simulate Home failure | Prompt waiting uncharged; current token/day/budget revalidated; no free continuation, duplicate award or later-window reset; Home failure retains prompt/recovery |
| I03-04 / Phone + persistence | Known counters/cursor, active or incomplete challenge | Kill/rebind/restart/reboot; revoke Usage Access/accessibility/camera; cross midnight; complete workflow in airplane mode | Counters/committed awards survive appropriately, incomplete reps do not resume, old-day terms cannot award new day, unknown downtime not charged; gap/recovery honest; offline model/speech fallback works |
| I03-05 / JVM + Android + Phone | Suspended storage/queue overflow/stale sample/removal failure | Change to Settings/Home/self or revoke access before old work completes; inject >64 events and >5,000 ms age | Immediate safe navigation; late work cannot reblock; coverage invalidated; no guessed spending; no duplicate overlay/lease; explicit retry recovers when fault clears |
| I03-06 / Phone + coverage | Long idle/non-target intervals, lock/unlock, keyboard/dialogs, split-screen and PiP | Execute T05/T13 documented foreground-policy cases and inspect coverage summary | Critical surfaces usable; last observed foreground-only policy honored, PiP-only not guessed as foreground; coverage checkpoints/freshness truthful without a live countdown claim; T02/T05/T11/T13/T16 checklist fully mapped |

I03 intended-device acceptance cannot be replaced by a debug-only countdown test or API36 emulator smoke test. Recognition targets also require I04 acceptance. Mark unsupported/unrun OEM cases precisely.

## F-A — Health and recovery center

| ID / method | Given | Action | Expected / required evidence |
| --- | --- | --- | --- |
| F-A-01 / JVM + Android | Granted permissions but disconnected/initializing/failed service (separate rows) | Open health/setup/Home | Each fact distinct; no healthy/guaranteed enforcement from permission alone; last observation Unknown/stale honestly; failed phase/recovery visible |
| F-A-02 / JVM + Phone | Boot/storage/metadata/reconciliation fault then cleared fault | Tap matching recovery repeatedly; separately return from required settings | Correct init retry or ready-state reconciliation runs once; fresh observed processing required; recovery doesn't reset counters or enable without explicit choice |
| F-A-03 / JVM + Phone | Retry/storage suspended and old diagnostic snapshot | Tap Stop, then complete old work; restart process or idle on non-target | Stop immediately pauses; old success cannot restore state; cross-process age Unknown; idle copy doesn't suggest selected-app countdown; local diagnostic scope clear |
| F-A-04 / Android + Phone | Three locales, large font/short landscape/TalkBack | Read and operate all failure/recovery states | Reason/recovery/Stop reachable and announced; no exception text/private payload; no mandatory diagnostic sharing; affected I11 rows pass |

## I12 — History scale

| ID / method | Given | Action | Expected / required evidence |
| --- | --- | --- | --- |
| I12-01 / Measure | Small baseline plus 365/1,095-day histories with 20 packages and mixed coverage | Load/scroll/aggregate repeatedly on intended device; log query count, peak memory, p95 load | Compare against proposed 1 s p95/stalled-interaction/per-row-query trigger; record actual repetitions/environment. No optimization claim before measurements; no-change decision acceptable when justified |
| I12-02 / JVM + Android | Same histories with removed apps, hidden days/deletion cutoff, empty days and mixed zones/scopes | Compare full-range baseline to bounded/batched implementation if triggered | Visible rows/totals/comparison decisions identical for requested windows; hidden data absent; current day/unknown/partial/complete semantics preserved; no per-row target query pattern after batching |
| I12-03 / Android + Measure | History clear/midnight/zone change while range load suspended | Resume old read; navigate between weekly windows | Stale results not exposed, deletion not undone, both comparison windows requested; budgets/session/cursor unchanged; before/after performance and correctness both recorded |

## F-B — Presets

| ID / method | Given | Action | Expected / required evidence |
| --- | --- | --- | --- |
| F-B-01 / JVM + Android | Two selected apps; no committed draft write | Preview SHORT then BALANCED, cancel, Keep current | SHORT 600000/1800000/180000/600000 ms; BALANCED 1800000/3600000/300000/1200000 ms (allowance/shared/session/cap). Shared ceiling explained; preview/cancel write nothing |
| F-B-02 / JVM + Phone | Manual draft edits, unavailable selected app and per-app override | Apply replacement only after confirmation; add another app; toggle earning off/on | Unavailable app retained, new app seeded without replacing manually edited values; explicit Apply controls replacement; earning off cap zero, saved earned credit intact; camera/speech/reward/monitoring unchanged |
| F-B-03 / JVM + persistence | Current-day usage above proposed limit, active challenge and pending session | Save lower preset; fail second persistence step; retry/read actual state | No reset of usage/credit/cursor/session or mutation of active challenge terms; lower limit may block; actual saved state shown on partial failure; explicit enable separate |
| F-B-04 / Android + Phone | Persian/Arabic digit custom edits, English/French and 200% font/TalkBack | Preview/edit/save invalid, overflow and valid values | Existing numeric validation works; no overflow/permissive default; reachable manual/Keep current/save/recovery; user can explain shared ceiling and earning state |

## F-C — Voluntary break

| ID / method | Given | Action | Expected / required evidence |
| --- | --- | --- | --- |
| F-C-01 / JVM + Phone | New visit with/without valid target | Continue/Finish at elapsed 0; separately wait 30000 ms | Both actions available immediately; expiry only changes suggested-pause state; no automatic navigation/sound/permission; no camera required |
| F-C-02 / JVM + persistence | Existing wallet/session/pending prompt and removed/unselected target variants | Open/leave break, continue to target, fail launch/Home | Zero challenge/activity/award/session-reset writes; normal blocker can reappear; arbitrary/invalid package never launched; failure retains Retry/Home; preserved accounting |
| F-C-03 / Android + Phone | Pausing visit with mute/TalkBack/large font | Background/resume, recreate process, change clock/reboot, Finish | Live-process elapsed uses monotonic time; recreated suggestion fresh and explicit; no fabricated completion/credit; accessible actions, no per-second announcement flood |

## F-D — Goals and reflection

| ID / method | Given | Action | Expected / required evidence |
| --- | --- | --- | --- |
| F-D-01 / JVM + Room | Two completed workouts same date, exercise-only on another, cancelled/practice/manual/hidden/future rows | Create activity-day goal and calculate visible qualifying dates | Distinct visible COMPLETED challenge dates only; exercise-only qualifies; others excluded; no usage coverage needed to count actual committed exercise |
| F-D-02 / JVM + Android | Goal 1..7, Monday/week boundary, existing earlier-week activity, zone change | Edit/turn off goal; cross week/zone; inject store failure | Changes start today; Monday reset window, no streak/carryover; stored date identities unchanged; Off changes no history/budget; failure unavailable, not zero |
| F-D-03 / JVM + persistence | Goal progress and reflection choices across >12 ISO week/zone keys | Submit Useful/Not useful/Skip, reopen same week, clear history | Once-per-week optional prompt; Skip suppresses that week; latest 12 only; clear removes visible contributions/reflections, keeps goal configuration; retained awards do not reconstruct progress |
| F-D-04 / Android + Phone | Opted-out and opted-in users, all locales/TalkBack/large text | Navigate Home/History/edit/reflection and decline | Off/default and opt-out preserved, accessible progress/target/range, no reward escalation/mandatory free text/telemetry/health claim or inferred outside-app inactivity |

## F-E — History export

| ID / method | Given | Action | Expected / required evidence |
| --- | --- | --- | --- |
| F-E-01 / JVM golden files | Visible snapshot with empty/unknown/partial coverage, special text and Long extrema | Serialize version-1 CSV/JSON; parse/round-trip values | Exact existing schema/header/order/UTF-8/CRLF rules; decimal-string integers preserve precision; unknown != zero; formula-leading text escaped reversibly; no locale-dependent machine numbers |
| F-E-02 / JVM + Android | Private retained award/cursor/hidden/deleted rows plus visible rows | Export selected range; clear history while picker open, then confirm final snapshot | Only final visible snapshot included; stale staged snapshot invalidated; final range/count/time confirmed after picker even if count unchanged; no private/camera/diagnostic data |
| F-E-03 / Android + provider | Active owned copy and simultaneous history-clear | Order copy-start vs clear; cancel or fail destination/stream close/full storage | Clear waits only for owned copy/cancel without monitor/Room lock; close failure never says success; partial-file warning and best-effort cleanup only of newly created destination |
| F-E-04 / Android + Phone + Review | Picker cancellation/process death and local/provider destinations | Cancel/restart/retry; inspect cache and compare file with visible source | Staging purged on all exits/startup; no auto-resumed export or accounting writes; provider-sync/previous-export deletion limits explained; accessible final confirmation in all locales |

## F-F — Widget

| ID / method | Given | Action | Expected / required evidence |
| --- | --- | --- | --- |
| F-F-01 / JVM + Phone | One to three apps, known snapshot and delayed host rendering | Render at age 0/300001 ms, cross day/zone/boot; delay host refresh | Timestamp/cutoff/uncertainty always visible; older label on next render, never promised exact flip; cross-day/zone removes stale numbers; no summed wallets/live countdown/current connection from cached status |
| F-F-02 / Android + Phone | Two widgets with different selections, removed/unselected app and hostile IDs/extras | Configure/edit/remove each; send invalid refresh/configuration IDs | Instances independent; 1..3 selection enforced, unavailable slot retained; invalid IDs rejected, immutable explicit intents; no arbitrary target launch/budget write/enable |
| F-F-03 / JVM + Android | Slow store, rapid spend/change callbacks, failed async work | Refresh; exceed 8000 ms; emit spending triggers <60000 ms apart | Timeout publishes unavailable and always finishes broadcast; periodic request 1800000 ms; spending refresh throttled; Stop/disconnect/clear/completion hooks truthful, no extra usage observer |
| F-F-04 / Phone | API28 and available newer/intended launcher, RTL/theme/TalkBack | Resize/refresh/open, reboot/kill and resume | Responsive 48 dp actions and snapshot descriptions; current access rechecked; no stale healthy claim; launcher delivery/battery limitations recorded |

## F-G — Schedules

| ID / method | Given | Action | Expected / required evidence |
| --- | --- | --- | --- |
| F-G-01 / JVM | Two fixed-ID rules, minute/weekday/limit boundaries | Validate ordinary/overnight overlap, equal endpoints, empty weekdays, nonpositive/overflow limits, third rule; save draft/activate/manual | Invalid configs rejected; start inclusive/end exclusive; overnight owned by start weekday; gaps manual; draft never activates; monitoring and consumed/credit/session state unchanged |
| F-G-02 / JVM | Asia/Tehran and DST zone (e.g. America/New_York), captured epoch/zone | Resolve gap/repeated hour, zone shift/clock regression/local midnight | Both repeated local minutes resolve consistently; skipped minutes have no fabricated duration; no extra wallet/day reset beyond engine midnight; stored historical day IDs unchanged |
| F-G-03 / JVM + Room | Two-second observed interval straddles rule boundary, existing consumption, queued old revision | Split/spend; edit schedule before processing; inject ambiguous epoch mapping/missed observations | Correct chronological segments use captured revision; no retroactive edit or last-rule-for-whole-interval error; unknown mapping produces gap not guessed spending; cursor prevents duplicates |
| F-G-04 / JVM + Phone | Lower/higher shared/session limits, current offer/active challenge | Transition rules, Continue/Finish, start/complete challenge | No refill/reset; lower ceiling can block, higher exposes only whole-day remainder; shorter session may prompt; immutable terms and current capacity both honored; all engine/UI/quotes agree |
| F-G-05 / Android + Phone | Unreadable/malformed schedule while monitoring or exercise-only UI open | Retry/Stop/open exercise-only; restore readable store | Effective-policy decisions pause with clear recovery; no permissive silent fallback; Stop and exercise-only remain usable; no alarm enables monitoring; affected I03 accounting gates rerun |

## M01 — Blocker navigation helper

| ID / method | Given | Action | Expected / required evidence |
| --- | --- | --- | --- |
| M01-01 / Android + Phone | Visible blocker on API34/35/36+ (separate rows) | Explicitly tap app/challenge destinations and repeat taps | Existing creator/sender branches and immutable one-shot semantics retained; correct target/exercise, no stacked destination Activities, no automatic/background launch |
| M01-02 / Android | Blocker not current/visible, or different destination pending | Send stale tap/cancelled intent/replaced destination | No stale navigation; request codes/extras cannot launch wrong destination; helper cannot mutate budget/enable monitoring |
| M01-03 / Phone + injected Android | Send throws or platform silently rejects launch | Attempt launch, then separately let destination resume | Failed/rejected launch retains blocker/recovery; only actual destination resume releases expected blocker; Home/Stop remain reachable |

## M02 — Service extraction

| ID / method | Given | Action | Expected / required evidence |
| --- | --- | --- | --- |
| M02-01 / Review | I05–I09 seams/regression QA complete | Inspect remaining service complexity; document extract/no-change decision | Focused benefit/ownership identified, no rewrite or duplicate core controller; no-change result acceptable with actual review rationale |
| M02-02 / JVM + Android | Refactored coordinator if undertaken; old race fixture sequences | Replay observation/session/retry/configuration/disable/cleanup order, slow store, overflow and stale tickets | Same decisions/counters/order and bounded queue; no stale overlay/new worker/duplicate lease; cancellation/removal-failure safety unchanged |
| M02-03 / Phone + full regression | Refactored integrated build | Rerun I03 affected lifecycle/launch cases and I05–I09 regressions | Fresh integrated evidence matches tested artifact; previous qualification not silently reused after extraction; Android lifecycle still owned by service |

## P01 — Pilot

| ID / method | Given | Action | Expected / required evidence |
| --- | --- | --- | --- |
| P01-01 / Review | I01–I11/F-A evidence, optional opt-in protocol | Check entry gates, consent/stop instructions and aggregate worksheet | No unresolved P0 or missing intended baseline passed off as pilot-ready; no participant identifiers/frames/event pipeline; metric denominators and observation limits defined |
| P01-02 / Observed use | Consenting owner/participants with accepted build | Observe first setup/challenge by exercise/mode, framing, counts, interruptions, recovery and deliberate finishes | Actual attempts/stops/time windows/participant count retained, optional local aggregates only; awarded minutes/increased restricted use not success proxy; missing coverage not inferred as reduced use/health benefit |
| P01-03 / Review | Aggregate observations and opt-out/usefulness comments | Assess setup/recovery clarity and proposed break/goals/export/widget/schedule needs | Concrete demand documented before optional gates open; no fabricated uplift or automatic feature activation; defects route to scoped Implementation then QA |

## Required matrix expansion and final decision

Create individual rows during QA for every required combination; do not mark a whole scenario group PASS from one representative trial. Minimum explicit expansions include I02's eight crop/rotation pairs, I04's 24 journeys/eight recognition sets/four trials/five participant attempts per exercise, I11's screen/locale/layout matrix, I03's two ceiling reasons/eight earning paths and M01 API branches if that refactor is undertaken.

Current-product qualification requires unfiltered automated gates, intended-device enforcement, required PG06 targets, accessibility acceptance and current provenance. Optional feature rows can remain GATED/NOT RUN without implying those features shipped. For any waiver or unsupported behavior, state exact scope and impact; a P0 failure or required missing result cannot become PASS through documentation.
