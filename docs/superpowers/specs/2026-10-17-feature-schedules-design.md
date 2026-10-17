# P4 — Explicit scheduled plans (F06)

Date: 2026-10-17. Status: **demand- and policy-gated proposal** for Stage D. This is an exact proposed first-version contract, not an approved change to accounting. Before implementation, record a demonstrated workday/evening use case and owner review of SC1–SC4 in `docs/UX_SCHEDULES_PHONE_TEST.md`. Read the [package contract](../../UX_REVIEW_IMPLEMENTATION_2026-10-17.md).

## Intent, alternatives and selected minimum

Let an explicitly activated plan vary shared limit and session length by local time. Choose deterministic on-observation resolution rather than exact background alarms. Do not schedule monitoring enablement or require new alarm permissions. Android can delay inexact alarms, so a timer alone cannot establish the policy applicable to an observation. See [Android alarm scheduling](https://developer.android.com/develop/background-work/services/alarms).

## SC1 — Rules, activation, overlap and gaps

Support at most two named rules, Workday and Evening, plus existing saved manual settings as fallback. A rule has ID, selected ISO weekdays, start/end local minute of day, shared ceiling ms, and session boundary ms. Rules vary only these two limits; per-app allowance/cap, earning/reward rules, selected apps, zero-base and speech/camera remain global. Daily accounting still means the whole local calendar day, not a per-rule allocation.

Minutes are 0..1439; start==end is rejected. An active schedule requires at least one rule, unique fixed IDs, nonempty weekdays, and positive limits; inactive drafts may contain no rules. An overnight interval belongs to its starting weekday; split it for overlap validation against adjacent weekdays. Intervals are start-inclusive/end-exclusive. Reject overlapping rules, including overlap created by overnight wrap; do not invent precedence. Gaps use manual fallback. Names are localized labels on fixed IDs, not an unbounded free-text schedule system.

Saving drafts does not activate. Explicit **Activate schedule** previews current/next rule, changed limits, fallback behavior and retained consumption, then enables schedule selection only. Monitoring remains exactly as explicitly saved. **Use manual plan** disables schedule selection and restores manual limits without resetting counters. Onboarding/preset/manual editor must show when a schedule overrides manual limits. A preset changes fallback only, with that fact disclosed.

## SC2 — Calendar and zone contract

Rules follow the current device `ZoneId`, not a fixed home zone. Resolve each instant by its actual local weekday/minute. In a spring-forward gap, skipped local minutes have no duration; the first real instant uses its matching rule/fallback. In a fall-back overlap, both instances of repeated local minutes use the same matching rule; no counters or sessions reset at either occurrence. Recompute next transition after zone/clock/date changes and on every policy decision; never rewrite persisted date IDs from old zones.

Local midnight retains existing engine expiration/cancellation rules. An overnight rule does not extend yesterday's wallet. A schedule transition does not refill allowance, awarded credit, cap or session. Lowering a limit below already consumed time blocks further use through existing rules. Raising it exposes only the remaining allowance under that whole-day limit. A shorter session boundary can prompt immediately if existing session consumption already exceeds it.

## SC3 — One policy resolver, observed interval boundaries

Add pure `ScheduledPlans.resolve(base: AppPreferences, schedule: ScheduleConfig, instant: Instant, zone: ZoneId): EffectivePlan` and `transitionsBetween(start: Instant, end: Instant, zone: ZoneId, schedule: ScheduleConfig): List<Instant>`. `EffectivePlan` contains effective preferences, active rule ID and next transition. Use it in the persistence engine's effective-policy adapter, monitor configuration, setup review, Home and earning quote. Keep manual preferences independently readable; the engine resolves scheduled limits lazily only for operations that need usage/reward limits. Exercise-only offer/start/completion and saved-result lookup bypass schedule reads because this schedule changes none of their terms. Do not persist effective limits over the manual fallback.

Extend observed-interval input with actual start/end epoch mapping and captured zone; the monitor owns this provenance. Split confidently observed segments at rule transitions and local midnight. Spending must resolve the scheduled preferences at each segment's policy instant, rather than applying the last rule retroactively to an entire checkpoint. Continue using the chronological usage cursor and maximum five-second observed checkpoint safeguard. Missing/ambiguous clock mapping is a gap, never guessed usage. Schedule settings are read as one immutable revision per checkpoint; edits take effect at the next checkpoint after the edit, without rewriting previously accounted intervals. Keep configuration revision and timeline identity in the monitor command so stale queued observations cannot adopt a new edit accidentally.

Budget/offer/start/completion use the effective policy at decision time. Active challenge terms remain immutable; completion still revalidates current shared capacity and target/cap. If a transition changes an offer, show changed terms before Start. If the shared limit is exhausted at completion, show the existing refusal/recovery rather than silently changing the promised credit. No schedule grants a new award.

## SC4 — Errors and visible truth

Show active rule, next transition including zone, and manual fallback. When monitoring is disabled say schedule selected / monitoring off; never imply enforcement. A schedule-store read failure makes effective policy unavailable: pause monitoring decisions with an actionable saved-data failure and do not silently fall back to a more permissive plan. Engine reads throw rather than returning fabricated limits; Stop and exercise-only exploration remain available using independently loaded exercise rules. Malformed stored schedule is a recovery error, not permission to reset it or budgets. Saving is atomic in a dedicated schedule DataStore; no Room migration is required merely to store schedules.

## Acceptance and gate

Test ordinary/overnight overlap, empty weekdays, equal times, midnight, DST gaps/overlaps, clock regression, zone changes, two-second observations straddling a rule transition, edit revision changes, missed observations, decreasing/increasing whole-day limits, and session consumed preservation. Verify all engine/UI entry points resolve the same active policy. No alarm/background task can enable monitoring, refill counters or restore cancelled progress.

This feature remains gated until demand and the exact calendar/accounting behavior are reviewed. The paired plan is ready for that reviewed scope; implementing only a clock-triggered preferences overwrite is not compliant with this spec.
