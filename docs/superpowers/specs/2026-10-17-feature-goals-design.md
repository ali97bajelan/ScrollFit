# P2 — Optional weekly activity goals (F03)

Date: 2026-10-17. Status: proposed Stage C experiment. Entry gate: U4 acceptance and a recorded pilot request/usefulness observation for a voluntary activity-day goal. Read the [package contract](../../UX_REVIEW_IMPLEMENTATION_2026-10-17.md).

## Intent and selected scope

Support returning for useful movement rather than collecting restricted-app time. The smallest version supports **activity days per week**, 1..7, or Off. Intentional-finish goals and reminders are later variants, not coupled to P3. No compulsory streaks, missed-day punishment, reward multiplier, calorie estimate, or health prescription.

## G1 — Goal lifecycle

Add optional `WeeklyGoal(targetDays: Int, effectiveFromDay: String)`, target 1..7, in a separate local DataStore repository. Default Off. The week is Monday through Sunday using ISO Gregorian dates. Creating/changing a goal starts a new measurement window on the current local day and does not retroactively count earlier days this week; show **Starts today**. Off removes the current goal, without deleting activity/accounting. Next week starts Monday normally; no carryover or streak.

Store activity's recorded local day as its date identity, consistent with existing history. A device zone change recalculates current week/today but does not rewrite earlier stored dates/timestamps or claim cross-zone usage comparisons. Future-dated records are ignored. Display the goal date range and **Recorded activity days**, without claiming that absence means inactivity outside ScrollFit.

## G2 — Honest progress source

Count each visible date with at least one committed detected-exercise completion once, regardless of reps, rewarded/exercise-only mode, or number of workouts. Preparation, practice, cancelled challenges, non-challenge/manual records, and replayed callbacks do not qualify. Add a repository query joining visible activities to `ChallengeStatus.COMPLETED` challenges inside one transaction; do not infer completion from a nonzero counter or mode mapping alone. No usage coverage is required to count a committed exercise, and no missing usage day becomes zero use.

Eligible range starts at max(Monday of current week, effectiveFromDay), ends today. Progress is distinct dates in that range; display actual day count even if above target, while progress indicator caps at target. Clear history deletes the activities and therefore removes their goal contribution. Warn in Settings deletion explanation that visible goal progress is derived from history; do not recreate activity from retained awards. Goal settings remain unless the person turns them off.

## G3 — Interface and errors

Offer goal choice from weekly review, always optional. Show selected goal, range, committed days and **Edit goal / Turn off** on Home/History. No reminder permission. Saving uses latest-value merge in the separate goal store; failure keeps the draft and offers Retry. Data failure shows **Progress unavailable**, not zero. Accessibility reads count and target; no celebratory auto-speech or automatic navigation. Local opt-in reflection assesses usefulness; no telemetry pipeline.

Acceptance: two completed workouts one day count once; practice/cancelled/manual activities count zero; exercise-only qualifies; hidden/deleted activity disappears; Monday/zone boundary rules are deterministic; goal edit starts today; turning off changes no budgets. Device checks cover copy, TalkBack, three locales and large text.
