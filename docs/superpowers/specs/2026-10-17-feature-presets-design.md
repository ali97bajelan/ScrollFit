# P1 — Editable plan presets (F02)

Date: 2026-10-17. Status: proposed Stage B follow-up, implementation-ready after U3 acceptance. Read [U3](2026-10-17-ux-setup-design.md) and the [package contract](../../UX_REVIEW_IMPLEMENTATION_2026-10-17.md).

## Intent and selected scope

Let people start with understandable editable settings instead of converting many numbers. Presets fill a draft and never enable monitoring or save automatically. Keep manual setup and **Keep my current plan** equally reachable. Values below are product proposals for review, not exercise prescriptions or measured optimums.

## PR1 — Two explicit presets

| Preset ID / name | Free time per selected app | Shared daily limit | Session boundary | Daily reward cap per app |
| --- | --- | --- | --- | --- |
| SHORT / Short sessions | 600,000 ms | 1,800,000 ms | 180,000 ms | 600,000 ms |
| BALANCED / Balanced | 1,800,000 ms | 3,600,000 ms | 300,000 ms | 1,200,000 ms |

Both keep `zeroBaseEnabled = false` and current configured exercise/reward rules. Earning is an explicit preview option, defaulting to the user's current cap availability; for a new plan use existing earning-enabled defaults. Switching earning off sets draft caps for eligible selected apps to zero; switching back restores the chosen preset cap before any manual overrides. Exercise-only remains available. No preset changes speech, camera, last exercise, onboarding state, or monitoring enabled state.

## PR2 — Preview before draft replacement

In U3 step 2, show preset cards then a preview of each selected app's allowance/cap, shared limit, session length, and earning enabled/disabled. Explain that per-app allowances share the same ceiling and cannot all necessarily be spent in full. If there are manual draft changes, ask **Replace these draft values?** with Apply/Keep editing. Applying affects currently eligible selected apps, ceiling/session/zero-base only; unavailable selected app values remain intact and visible. New app selections made after choosing a preset are seeded from the preset unless the user has edited that app's value. Existing overrides are replaced only by explicit Apply.

## PR3 — Save and accounting

Use `SetupDraft` and `SetupCoordinator`, including U3 partial-failure readback and explicit enable. Preset preview never writes Room or DataStore. Changing settings preserves consumed base/earned usage, awards, session consumed state, challenge identities and usage cursor. Lowered limits take effect through existing engine policy; if today's consumption exceeds a proposed limit, preview **Today's recorded use already exceeds this limit; it does not reset when saved**. Existing active challenge terms remain immutable; completion still revalidates current cap/ceiling.

Acceptance: two-app Balanced preview explains the 60-minute shared ceiling; off means cap zero, not erased earned credit; cancel leaves draft unchanged; apply preserves unavailable apps; failure shows actual saved state. Verify Persian/Arabic numeric input after custom edits and all locales/large text. Pilot observation records setup completion and later manual corrections, with no numeric uplift promise.
