# T11 challenge integration

The next ordered task connects the existing four pose counters to T04's durable
challenge/award policy. Success means both advance and blocker earning can finish
each exercise exactly once, and exercise-only records activity without credit.
English delivery notes and Persian RTL product text follow the established project.

## Approach

Reuse BudgetEngine offers, immutable snapshots and the unique award ledger. A
small Android-free ChallengeSession consumes cumulative detector progress and
owns completion eligibility. Add optional activity recording to the same
completion transaction so activity, status and wallet cannot diverge. Reusing
only UI state would leave lifecycle races untested; a second reward system would
duplicate accounting policy. Neither is needed.

The dashboard opens a selected-target picker before exercise selection. The
blocker displays all four current offers and opens an explicit internal challenge
Activity only on a visible button click. Both routes use the same preparation
screen, disclose reduced rewards/capacity, restore exercise/camera preferences,
and require Start after camera permission. Exercise-only has no target and remains
available at caps. Offer changes require another explicit start with new terms.

The camera trial remains available. In a challenge, cumulative fresh progress is
fed to ChallengeSession; tracking loss keeps completed reps and excludes lost
tracking from active duration. Completion stops the camera and announces only the
transaction result, with actual persisted award and a user-driven target return.

## Interruptions and storage

Pause/background, back/cancel, rotation, lens switch, camera restart and camera
failure end an incomplete challenge. They invalidate completion synchronously,
then cancel its persisted row in application scope. An eligibility guard checked
inside completion rejects work invalidated while database I/O was suspended.
Startup recovery already cancels all active rows. Completed awards survive.
If an award committed before interruption it remains valid; no UI event can
retroactively undo it. Failed completion is retryable with the same identity.

Active duration sums adjacent fresh tracked observations with gaps at most 750 ms;
initialization, tracking loss and background time do not contribute. It is a
conservative observed duration, not a physiological measurement. Activity records
use challenge identity, original day/exercise and completed repetition target.
Cancelled partial activity is discarded, an option expressly allowed by the brief.
Midnight cancellation is checked periodically and again by transactional policy.

## Validation and scope

JVM tests cover both earning modes/all exercises, lost tracking, duration,
interruption before/during completion, duplicate completion, rollback, reduced
offers, caps, midnight and process recovery. Existing synthetic detector tests
remain the evidence for physical-cycle logic. Android debug/release/lint and
instrumentation are attempted; available source checks and phone steps supplement
but never replace Android/device verification. T12 speech, T13 usage sessions,
T14 screen completion and T15 outcomes stay outside this request.

Execution ruling: the task list explicitly says to implement without intermediate
approval. No design/plan approval pause or automatic commit is introduced. Existing
changes and index are preserved; read-only Git metadata means no new worktree.

Concurrent-work adaptation: preserve the owner’s English/French localization
commit. Supply the new challenge strings in those locales and follow device
layout direction, keeping Persian RTL. No existing commit is rewritten.
