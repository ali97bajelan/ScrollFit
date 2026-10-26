# ScrollFit: product and UI/UX review

Reviewed: **2026-10-16**. Method: **static source review only**.

At review time, this file covered the current working tree, including uncommitted work. No app, emulator, device session, build, or test suite was run; only this review file was added. Layout clipping, perceived performance, contrast, exercise recognition quality, and actual accessibility behavior remain unverified.

## 1. Product assessment

ScrollFit has a clear product idea: help people interrupt habitual app use and optionally earn app time through movement. The current source already supports selected-app budgets, a combined daily ceiling, four exercises, advance earning, earning from the blocker, exercise-only sessions, offline feedback, and coverage-aware history.

**Recommendation: improve the activation and exercise journeys before expanding the feature set.** The accounting model has more detail than a new user should need to learn before receiving value. The largest opportunity is to make the next action obvious: set up one app, understand the time available, take a deliberate break, and recover easily when something interrupts a challenge.

Working audience assumption: people voluntarily managing selected-app use who may choose camera-based movement breaks. No user interviews, funnel data, retention data, or competitor research were supplied. Product impact and effort below are estimates for prioritization, not measured results.

The main product decision to validate is whether exercise is an optional aid to intentional use or the main reason people install ScrollFit. I recommend the first direction, with exercise-only activity as a useful secondary path. Reward volume and minutes spent in restricted apps should not become success metrics by themselves.

## 2. Existing strengths to preserve

- **User control:** monitoring requires explicit enablement, and Stop remains available independently of valid setup drafts.
- **Clearer dashboard:** usable time is already prominent; award details are expandable. Do not repeat the earlier recommendation to introduce these changes.
- **Improved setup:** saveable drafts, app search, persistent action controls, and individual readiness states are already present in source.
- **Credible reward accounting:** immutable challenge terms and an award identity protect against duplicate credit. Exercise-only completion is separate from awarded time.
- **Honest outcomes:** missing observations remain unknown; comparisons require matching scope, time zone, and coverage. Keep these safeguards when adding charts.
- **Localization foundation:** Persian RTL, English, and French resources, localized numbers, selection semantics, and local vector navigation icons are already implemented.
- **Offline feedback:** speech has local sound and visual fallbacks; users can mute it. Retain this behavior when simplifying the workout screen.

Sources: [dashboard and exercises](../app/src/main/java/com/example/scrollfit/ui/ProductScreens.kt), [monitoring setup](../app/src/main/java/com/example/scrollfit/monitoring/MonitoringSetup.kt), [budget engine](../core/src/main/kotlin/com/example/scrollfit/core/budget/BudgetEngine.kt), [outcomes](../core/src/main/kotlin/com/example/scrollfit/core/outcomes/OutcomeAggregator.kt), [audio](../app/src/main/java/com/example/scrollfit/feedback/ChallengeAudio.kt).

The earlier [UI/UX improvements document](UI_UX_IMPROVEMENTS.md) records a first implementation pass. This separate review assesses the remaining opportunities in the current source.

## 3. Priorities at a glance

**P1:** improve before a wider user pilot. **P2:** address in the next usability iteration. **P3:** explore after the core journey works reliably. These are product priorities, not claims of verified runtime defects.

Effort: **S** = focused copy/component work; **M** = a screen or flow change; **L** = lifecycle, persistence, or policy changes. Estimates include implementation complexity, not calendar commitments.

| ID | Priority | Opportunity | Expected benefit | Effort |
| --- | --- | --- | --- | --- |
| UX01 | P1 | Put camera, progress, and exit controls together | Less friction during movement | M |
| UX02 | P1 | Add an explicit interrupted-challenge recovery path | Less frustration and abandonment | M; L for resumable progress |
| UX03 | P1 | Guide first setup and finish with a clear handoff | Better activation | M |
| UX04 | P1 | Explain usable credit before effort is spent | Greater reward trust | M |
| UX05 | P1 | Separate exercise-only copy and resolve dead ends | Clearer choices | S–M |
| UX06 | P1 | Make monitoring problems directly actionable | Faster recovery | M |
| UX07 | P2 | Simplify time entry and duration formatting | Fewer input errors | M |
| UX08 | P2 | Clarify unsaved edits and partial save failures | More confidence in settings | M; L for coordinated persistence |
| UX09 | P2 | Explain why some apps are absent | Less selection confusion | S–M |
| UX10 | P2 | Summarize history and progressively reveal detail | Easier reflection; better scaling | M |
| UX11 | P2 | Share visual tokens across entry points | Consistent appearance | M |
| UX12 | P2 | Add semantics for changing status and progress | More accessible feedback | M |
| UX13 | P2 | Separate release copy from development notes | Clearer, more credible language | S |

## 4. Detailed findings and acceptance criteria

### UX01 — Prioritize the live workout viewport

**Evidence:** [ChallengeScreen.kt](../app/src/main/java/com/example/scrollfit/challenge/ChallengeScreen.kt), lines 70–77, adds audio controls and progress above `CameraScreen`. [CameraScreen.kt](../app/src/main/java/com/example/scrollfit/camera/CameraScreen.kt), lines 151–221, then adds a second title, privacy text, positioning instructions, another progress count, a fixed 360 dp preview, status, camera controls, and finally Close inside a scrolling column.

**Impact hypothesis:** a person exercising at a distance may need to scroll to see tracking guidance or stop. The duplicate progress and repeated preparation text compete with the camera. The source establishes the ordering; visibility on a particular screen is unverified.

**Suggestion:** separate Prepare, Position, Exercise, and Result states. Keep detailed instructions in Prepare. During Exercise, show one large repetition count, the preview, one tracking message, mute, and an always-reachable End action. Fit the preview to available space. Decide camera choice before starting, because changing it currently cancels the challenge.

**Acceptance:** at a narrow width and large text size, progress, essential tracking feedback, and End can be reached without scrolling through instructions. The screen has one progress count. Preparation and privacy details remain available on demand.

### UX02 — Recover from interruptions without forcing users to navigate away

**Evidence:** [ChallengeScreen.kt](../app/src/main/java/com/example/scrollfit/challenge/ChallengeScreen.kt), lines 55–68 and 100–109, interrupts on pause, disposal, or Back and shows an interrupted message. [ChallengeFlow.kt](../app/src/main/java/com/example/scrollfit/challenge/ChallengeFlow.kt), lines 119–143 and 184–197, retains the session after interruption, while `start()` refuses another start when a session exists. [ChallengeSession.kt](../core/src/main/kotlin/com/example/scrollfit/core/challenge/ChallengeSession.kt), lines 13–39, explicitly makes unfinished progress non-restorable. Camera switching also interrupts in [CameraScreen.kt](../app/src/main/java/com/example/scrollfit/camera/CameraScreen.kt), lines 133–135.

**Impact hypothesis:** an interruption after substantial effort feels like lost work, especially when the only immediate path is Close. The cancellation policy is deliberate; changing it requires a product and persistence decision.

**Suggestion:** first add **Start again**, **Choose another exercise**, and **Done**, with the reason and saved-credit status. A restart must create a new challenge identity and fresh offer. Provide a scope-limited keep-screen-awake behavior during active exercise to reduce accidental timeout interruptions. Treat progress-preserving resume as a separate, later feature.

**Acceptance:** interrupted users can begin a fresh challenge from the same screen after reviewing current terms. No cancelled progress earns credit. If a result committed before interruption, recovery displays the existing result rather than encouraging duplicate effort. Keep-awake ends with the workout.

### UX03 — Turn onboarding into a short guided setup

**Evidence:** [ProductScreens.kt](../app/src/main/java/com/example/scrollfit/ui/ProductScreens.kt), lines 120–142, presents five text blocks and two buttons. [MonitoringSetup.kt](../app/src/main/java/com/example/scrollfit/monitoring/MonitoringSetup.kt), lines 180–264, combines access links, global rules, app selection, and per-app edits. Enabling monitoring updates a preference but has no navigation/completion callback, lines 281–286. Onboarding completion is saved only by Open dashboard on Welcome. [ScrollFitApp.kt](../app/src/main/java/com/example/scrollfit/ui/ScrollFitApp.kt), lines 80–99, makes setup Back return to its previous screen.

**Impact hypothesis:** new users must understand several policies before selecting one app, then infer what to do after enablement. A person who finishes setup without choosing Open dashboard can still have incomplete onboarding state.

**Suggestion:** use a small guided sequence: **Choose apps → Set a daily plan → Connect required access → Review and enable**. Keep advanced per-app caps and zero-base mode expandable. Finish with a clear monitoring-status result and **Go to Home**. Offer **Explore exercises** without requiring monitoring access. Keep privacy explanations beside the relevant permission.

**Acceptance:** setup has a visible current step and resumable draft. Successful enablement offers an explicit handoff to Home and records the appropriate onboarding state. People can explore exercise-only mode without granting unrelated monitoring access.

### UX04 — Make the reward promise match time that can actually be used

**Evidence:** [BudgetEngine.kt](../core/src/main/kotlin/com/example/scrollfit/core/budget/BudgetEngine.kt), lines 205–238, limits usable time to the smaller of the wallet and combined remaining time. Offers are clipped to award capacity, but not to combined remaining time. [ChallengeScreen.kt](../app/src/main/java/com/example/scrollfit/challenge/ChallengeScreen.kt), lines 127–133, displays repetitions, reward, and award capacity without displaying the offer's combined remaining time. The defaults in [Exercise.kt](../core/src/main/kotlin/com/example/scrollfit/core/exercise/Exercise.kt), lines 10–22, give each app 30 free minutes under a shared 60-minute ceiling.

**Concrete example:** with no free or earned time left and only 30 seconds under the combined ceiling, a challenge can offer 120 seconds of credit if award capacity permits. The credit can be awarded, but only 30 seconds is usable before the ceiling stops further use. With two selected apps, their combined default free allowances already equal the shared ceiling; earning reallocates possible use within that ceiling rather than increasing it.

**Suggestion:** show **credit to be awarded**, **time usable after completion**, and **shared daily time remaining** before Start. Explain local-midnight expiry. When earning would add no immediately usable time because the wallet already covers the shared remainder, disclose that clearly. Preserve the policy until research justifies changing it.

**Acceptance:** people can explain the distinction between credit, the daily award cap, and the combined usage ceiling. The 30-second example is accurately disclosed before effort, and success uses the committed award amount.

### UX05 — Give exercise-only mode its own language and direct next actions

**Evidence:** [ChallengeScreen.kt](../app/src/main/java/com/example/scrollfit/challenge/ChallengeScreen.kt), lines 127–130, uses reward terms for all available offers. [BudgetEngine.kt](../core/src/main/kotlin/com/example/scrollfit/core/budget/BudgetEngine.kt), lines 227–229, makes exercise-only offers carry zero reward, so this mode displays reward language with zero seconds. The same screen, lines 111–118 and 152–153, offers no direct setup action when targets are absent and shows text when offers are refused. [ProductScreens.kt](../app/src/main/java/com/example/scrollfit/ui/ProductScreens.kt), lines 103–110, also leads exercise-only cards with the configured reward.

**Suggestion:** exercise-only preparation should say **Complete 10 squats. Save this activity.** Offer an optional explanation of earning mode separately. Add **Choose apps** to the missing-target state and **Exercise without rewards** to cap/ceiling refusals. Use compact button labels such as **Start exercise** and **Earn time**, with fuller explanations nearby.

**Acceptance:** exercise-only screens do not promise or emphasize a reward. Every refusal or empty state offers a relevant next action. Mode changes preserve the user's exercise choice where appropriate.

### UX06 — Make monitoring health a task the user can resolve

**Evidence:** [ProductComponents.kt](../app/src/main/java/com/example/scrollfit/ui/ProductComponents.kt), lines 75–94, shows health and failure text without a corresponding action. [MonitoringSetup.kt](../app/src/main/java/com/example/scrollfit/monitoring/MonitoringSetup.kt), lines 269–275, shows three readiness lines, while permission links are earlier in the scrolling content. [MonitorRuntime.kt](../app/src/main/java/com/example/scrollfit/monitoring/MonitorRuntime.kt), lines 8–11, carries no last-observation timestamp; its gap flag remains sticky through subsequent status publications, lines 27–28.

**Suggestion:** use a compact status card with one contextual action: Enable monitoring, Grant usage access, Check Accessibility, or Retry saved data. Put each access-recovery action beside its readiness state. Distinguish **current connection state**, **earlier monitoring gaps**, and **when the displayed data was refreshed**. A UI refresh timestamp must not be presented as proof of successful observation or enforcement.

**Acceptance:** users can identify what is wrong and reach the relevant recovery screen directly. Earlier gaps remain visible without implying that a recovered connection is still disconnected. Connection status does not claim verified enforcement.

### UX07 — Standardize time presentation and reduce arithmetic in settings

**Evidence:** [ProductComponents.kt](../app/src/main/java/com/example/scrollfit/ui/ProductComponents.kt), lines 44–71, formats every duration as hours, minutes, and potentially fractional seconds. [HistoryScreen.kt](../app/src/main/java/com/example/scrollfit/ui/HistoryScreen.kt), line 122, uses elapsed-clock formatting instead. Challenge offers show integer seconds. [product_strings.xml](../app/src/main/res/values-en/product_strings.xml), lines 6 and 10, always includes all three units and gives a fixed 300-second input example. The dashboard prints the raw ISO day in [ProductScreens.kt](../app/src/main/java/com/example/scrollfit/ui/ProductScreens.kt), line 28.

**Suggestion:** share one localized display policy: **30 min**, **1 hr 5 min**, and **45 sec**, with precise values where a reward decision requires them. Offer minutes-first entry or a minutes/seconds selector, backed by the same exact millisecond accounting. Add dynamic conversion hints for the actual draft. Use the selected UI locale for numbers and dates; confirm whether Persian-calendar display is wanted before introducing it.

**Acceptance:** the same amount has a consistent meaning across Home, setup, challenges, and history. Boundary values do not produce an isolated rounded “60 seconds” component. All three locales retain numeric validation, Persian/Arabic digit input, and correct RTL order.

### UX08 — Make saved state, drafts, and save failures understandable

**Evidence:** setup drafts now use `rememberSaveable`, so their absence is no longer a finding. However, setup and [SettingsScreen.kt](../app/src/main/java/com/example/scrollfit/ui/SettingsScreen.kt), lines 47–115, have no explicit unsaved-change exit flow. Settings places Save after all exercise and preference controls. [MonitoringSetup.kt](../app/src/main/java/com/example/scrollfit/monitoring/MonitoringSetup.kt), lines 142–157, saves target edits individually before updating DataStore; a failure can leave a partially applied configuration. Its error message does not explain this possibility. The setup footer, lines 266–294, has several status lines and controls without explicit IME padding.

**Suggestion:** show **Unsaved changes** and a concise saved summary. Warn on discarding edits or provide a clearly defined draft-retention policy. Keep Save reachable in Settings as it already is in setup. On partial failure, re-read the actual saved configuration and explain that some changes may have applied. Collapse healthy readiness details to reduce footer height.

**Acceptance:** users can distinguish saved settings from drafts and understand the outcome of a failed save. Settings actions remain reachable with the keyboard and large text. Verify footer size on a device later; clipping is a risk, not a confirmed observation.

### UX09 — Explain the actual selectable-app filter

**Evidence:** [TargetApps.kt](../app/src/main/java/com/example/scrollfit/monitoring/TargetApps.kt), lines 29–40, excludes every system or updated-system app, disabled apps, and protected packages. [monitoring_strings.xml](../app/src/main/res/values-en/monitoring_strings.xml), line 24, gives only a general system/essential-app explanation. Search has a generic no-match state.

**Impact hypothesis:** a distracting preinstalled app may be missing even though users reasonably expect it to be selectable. That looks like broken search unless the reason is explained.

**Suggestion:** add **Why is an app missing?**, explain the conservative preinstalled-app restriction, and distinguish no search results from no eligible installed apps. Add app icons and a selected-app count for scanning. A future expansion of eligibility needs a separate safety and monitoring-policy design.

**Acceptance:** users understand why a known app is absent and how to refresh the list. Explanatory UI does not quietly change the protected-app filter. Previously selected unavailable apps continue to require explicit removal.

### UX10 — Make history useful immediately and manageable over time

**Evidence:** [HistoryScreen.kt](../app/src/main/java/com/example/scrollfit/ui/HistoryScreen.kt), lines 25–56, displays summaries, comparisons, seven detailed daily cards, and every older day in a scrolling `ProductPage`. [ProductComponents.kt](../app/src/main/java/com/example/scrollfit/ui/ProductComponents.kt), lines 24–30, uses a regular Column, rather than lazy composition. [OutcomeAggregator.kt](../core/src/main/kotlin/com/example/scrollfit/core/outcomes/OutcomeAggregator.kt), lines 15–17, synthesizes all seven recent days even when there are no records. Its comparisons correctly need complete comparable periods.

**Suggestion:** start with a compact seven-day overview, coverage labels, and a separate exercise summary. Expand one day on demand; load older dates progressively. For first use, show a friendly action such as **Complete your first exercise** instead of leading with seven unknown days and unavailable comparisons. Explain which periods qualify for a comparison.

**Acceptance:** missing data has its own visual treatment and is never a zero-height “zero usage” bar. Current-day partial data is labeled. Comparisons retain the existing eligibility rules. Long history uses lazy/paged presentation; any performance improvement must be measured later.

### UX11 — Share a visual system across the main app, blocker, and challenge entry

**Evidence:** [ScrollFitApp.kt](../app/src/main/java/com/example/scrollfit/ui/ScrollFitApp.kt), line 54, selects light/dark Material colors. The blocker-launched [ChallengeActivity.kt](../app/src/main/java/com/example/scrollfit/challenge/ChallengeActivity.kt), line 36, uses a bare `MaterialTheme` without that selection. [BudgetOverlay.kt](../app/src/main/java/com/example/scrollfit/monitoring/BudgetOverlay.kt), lines 65–66 and 118–122, hard-codes a dark background, white text, and native view styling.

**Suggestion:** define common semantic colors, spacing, typography, and action hierarchy for Compose and the native overlay. Apply one theme policy to both activities. On the blocker, visually prioritize **Take a break** and the relevant earning option; group system tools and Stop as clearly labeled secondary controls while preserving their availability.

**Acceptance:** identical states have consistent labels, colors, and action importance across entry points. Light/dark themes, long French labels, and Persian RTL are checked later. Do not claim a contrast pass from source inspection alone.

### UX12 — Give changing UI states accessible meaning

**Evidence:** challenge progress and camera tracking are plain Text nodes in [ChallengeScreen.kt](../app/src/main/java/com/example/scrollfit/challenge/ChallengeScreen.kt), lines 73–74, and [CameraScreen.kt](../app/src/main/java/com/example/scrollfit/camera/CameraScreen.kt), lines 189–191. They have no explicit live-region or progress-range semantics. Challenge target/exercise rows use radio roles but lack the `selectableGroup` containers already used by Settings. The audio switch has a description, but its adjacent label is not the shared toggleable row used elsewhere.

**Suggestion:** expose progress as a coherent semantic value and announce important tracking/error/completion transitions without reading every camera update. Add selection-group semantics and consistent full-row toggle behavior. Coordinate with the app's spoken counting to avoid overlapping announcements. Keep important state visible when sound is muted.

**Acceptance:** TalkBack can identify each group and selected item, reach End predictably, and understand tracking loss and completion. Test with speech on and off; source semantics alone cannot establish the outcome.

### UX13 — Clean up production language and recovery advice

**Evidence:** the live `monitor_healthy` and `monitor_limits_notice` resources contain device-testing and manufacturer investigation notes in [monitoring_strings.xml](../app/src/main/res/values-en/monitoring_strings.xml), lines 8 and 47. `camera_model_error` recommends reinstalling in [camera_strings.xml](../app/src/main/res/values-en/camera_strings.xml), line 16, while this is an app with local records. Several older strings say exercise rewards or history are not implemented, but the inspected main-source UI does not reference those keys; they are stale resources, not confirmed visible defects.

**Suggestion:** keep user-facing limits and privacy explanations concise and actionable. Keep release-verification notes in delivery documentation or an explicitly labeled testing area until validated. Standardize the vocabulary for allowance, earned time, daily reward cap, and shared daily limit across Persian, English, and French. Prefer retry and diagnostics before reinstall advice; explain local-data consequences if reinstall remains a last resort.

**Acceptance:** every reachable message matches current behavior, gives a useful next action, and uses consistent terms. Unused resources are checked for indirect/debug references before removal. Simplifying copy must not turn an unverified connection into a promise of reliable enforcement.

## 5. Suggested feature backlog

These are proposals, not implemented functionality or validated demand. The inspected product UI and preferences do not expose these as complete user-facing features. Ship the P1 journey improvements first.

| ID | Feature and smallest useful version | Product hypothesis | Priority / effort | Dependency and success signal |
| --- | --- | --- | --- | --- |
| F01 | **Camera setup coach and practice.** A framing guide, stable-tracking readiness state, and optional short practice before a rewarded challenge. No practice credit. | Successful positioning before effort reduces failed first workouts. | P1 / M | UX01–02. Measure time to stable tracking and first challenge completion; practice reps must not leak into reward progress. |
| F02 | **Editable plan presets.** A few clearly described app-use plans with a preview of per-app allowance, shared ceiling, session length, and optional earning. | People can begin without configuring many numbers. | P2 / M | UX03–04, UX07. Measure setup completion and later corrections; changing presets preserves usage and credit. Treat values as configurable proposals, not exercise prescriptions. |
| F03 | **Personal weekly goals.** Choose activity days or intentional session finishes; show progress without compulsory streaks. Optional reminders can follow later. | Goals give a reason to return beyond obtaining more app time. | P2 / M | Honest activity/outcome data. Measure goal participation, completed breaks, and voluntary usefulness feedback. Unknown monitoring days remain unknown; no automatic reward escalation. |
| F04 | **Coverage-aware weekly review.** A small visual usage summary, exercise-only achievements, comparison eligibility, and one optional reflection question. | Reflection helps people adjust their own plan. | P2 / M | UX10 and existing comparison safeguards. Measure whether users understand the report and deliberately adjust plans; show eligible-day counts. |
| F05 | **On-demand intentional break.** A shortcut to a short non-camera pause with Continue or Finish, available independently of the automatic session boundary. No exercise credit by default. | The product remains useful when movement or camera use is inconvenient. | P2 discovery / M | Build on the session prompt. Measure chosen finishes and reported usefulness. Any future credit for this mode needs its own explicit policy and must remain distinct from detected exercise. |
| F06 | **Scheduled plans.** A limited workday/evening schedule with a visible next transition and explicit activation. | App-use intentions vary by context. | P3 / L | Validate demand first. Define midnight, time-zone, overlapping-rule, and existing-wallet behavior before implementation. A schedule must not silently enable monitoring or reset accounting. |
| F07 | **Local history export.** User-triggered CSV/JSON export of visible activity and usage with units, time zone, and coverage metadata. | People can review their own progress outside the app without an account. | P3 / M | Clear history visibility and retention rules. Exclude hidden/deleted history and camera data. Measure successful exports and requested use cases. |
| F08 | **Home-screen status widget.** Remaining usable time for chosen apps plus a direct action to open ScrollFit. Include freshness and disconnected states. | Users can check their plan without entering a distracting app. | P3 / M–L | UX04 and UX06. Define refresh, stale-data, and failure behavior first; never imply a live countdown when displaying an old snapshot. |

An in-app language/theme preference can follow if people request it; the app already has locale resources and system theme support in its main entry point. Social feeds, leaderboards, calorie estimates, cloud accounts, and monetization add complexity without evidence that they solve the current activation and workout problems.

## 6. Recommended delivery order

| Stage | Scope | Exit criterion |
| --- | --- | --- |
| A — Make the core loop understandable | UX01–06, focused copy corrections, F01 | A new user can configure one app, understand a reward, complete or abandon an exercise, and recover from interruption without assistance. |
| B — Make daily use comfortable | UX07–13, then F02 | Settings, status, and history are understandable across all supported locales and realistic screen/text sizes. |
| C — Validate ongoing value | F03–05 in small iterations | Evidence of useful intentional breaks and better perceived control, alongside acceptable recognition and monitoring reliability. |
| D — Expand from demonstrated demand | F06–08 | Each feature has a demonstrated use case and defined lifecycle/accounting behavior. |

Do not attach delivery dates or numerical uplift targets until implementation capacity and baseline measurements are known. The current source should receive build and physical-device validation before a wider pilot; that work was outside this review.

## 7. Measurement plan

**Value hypothesis:** people feel more in control of selected-app use and find the breaks useful. Validate this with a brief, optional weekly reflection plus observed intentional finishes and exercise-only activity. Treat retention as supporting evidence, not proof of benefit.

| Measure | Definition | Interpretation |
| --- | --- | --- |
| Activation | First-time users who select an eligible app and explicitly enable monitoring with required access connected / first-time users entering setup | Locates setup friction; connection is not proof of enforcement. |
| First workout success | First started challenges with a committed completion / first started challenges, split by exercise and mode | Separates reward/activity success from a visual count reaching its target. |
| Interruption burden | Interrupted challenge attempts / started attempts, with a coarse reason | Shows whether lifecycle behavior is undermining the core experience. |
| Recovery success | Interrupted attempts followed by a fresh successful challenge in the same visit / interrupted attempts | Evaluates UX02. A saved result discovered during recovery is tracked separately. |
| Intentional breaks | Deliberate Finish choices and completed exercise-only sessions, reported separately | Measures chosen breaks without rewarding extra restricted-app use. |
| Usage outcomes | Recorded usage changes only for periods allowed by the current comparison rules | Report comparison eligibility and coverage alongside results; do not infer reduction from missing data. |
| Reliability | Observation gaps, failed result saves, launch failures, and reported false/missed reps | Guardrails for any engagement or completion gains. |

These are proposed measurements; this review does not establish an analytics pipeline or baseline. Start with opt-in pilot observation or local diagnostics. Any future telemetry needs an explicit data design; keep camera frames, other-app content, and raw app-use histories out of ordinary product events.

## 8. Later validation checklist — not performed

- Ask a small pilot group to explain the difference between free time, earned time, award capacity, and the shared ceiling before completing a challenge.
- Observe first setup, permission return, enablement, and the return to Home without assistance.
- Try the 120-second reward / 30-second shared-remainder case, zero allowance, zero award cap, exhausted ceiling, and local-midnight expiry.
- Exercise with the phone at a realistic distance; check framing, visible progress, lost tracking, mute, End, camera failure, timeout, rotation, and backgrounding.
- Check first-run history, deleted history, partial coverage, changed app selections, changed time zone, and substantial older history.
- Check narrow screens, large fonts, keyboard visibility, TalkBack, long app names, Persian RTL, English/French, and light/dark appearance through both challenge entry points.
- Confirm saved-credit recovery, fresh challenge identities, and history deletion semantics before changing any interruption or reward policy.

## 9. Review boundaries

The evidence is the source linked above, especially the product screens, monitoring setup/blocker, challenge lifecycle, camera UI, reward model, outcome aggregation, and Persian/English/French resources. Existing documentation provided context; implementation observations were checked against current code.

This assessment records source-level observations and product hypotheses from the 2026-10-16 review. Future feature choices should be refined with users and tested on the actual app.

## 10. Implementation status

Updated **2026-10-17**. Status below means implemented in source; it does not mean build or device acceptance has passed.

| Review item | Planned task | Status |
| --- | --- | --- |
| UX07 — Time presentation and entry | U1 tasks 1–2 (B1–B2) | Implemented in source: [localized duration formatter](../app/src/main/java/com/example/scrollfit/ui/ProductFormatters.kt) and [minutes-first editor](../app/src/main/java/com/example/scrollfit/ui/DurationSetting.kt). |
| UX11 — Shared theme and blocker actions | U1 task 3 (B3) | Implemented in source: [shared theme](../app/src/main/java/com/example/scrollfit/ui/ScrollFitTheme.kt), [product tokens](../app/src/main/java/com/example/scrollfit/ui/ProductTokens.kt), and [native blocker styling and actions](../app/src/main/java/com/example/scrollfit/monitoring/BudgetOverlay.kt). Build, UI, contrast, and device validation remain pending; no Gradle or emulator checks were run for this task. |
| UX13 — Production language and recovery copy | U1 task 4 (B4) | Implemented in source: localized production vocabulary, cautious monitoring status, and reachable camera model-error recovery with retryable interruption persistence in Persian, English, and French; [source checker](../verification/check_source.py) passes and checks touched resource parity. Build and [phone acceptance](UX_FOUNDATION_PHONE_TEST.md) remain pending; no Gradle tests or emulator checks were run for this task. |

U1 source implementation tasks B1–B4 are complete; the device acceptance checks remain pending.
