# ScrollFit UI/UX review and implementation

Reviewed: 2026-10-16. Method: source review only; do not launch the app.

## Intended outcome

Help users quickly understand their remaining usable time, monitoring readiness,
and next action. Preserve Persian RTL, English/French translations, current
accounting, explicit monitoring consent, and coverage/comparison safeguards.

## Suggested improvements

1. **Preserve setup drafts (high priority).** App selections, allowance/cap
   fields, combined ceiling, session length, and search currently use `remember`.
   Preserve unfinished edits through activity recreation and permission trips;
   loading persisted settings must not overwrite restored drafts.
2. **Keep setup actions reachable (high priority).** Save and Enable currently
   follow the complete app list. Place actions in a persistent footer, explain
   prerequisites, and show the individual access/service readiness states.
   Keep Stop available independently of draft validity.
3. **Simplify dashboard hierarchy.** Emphasize usable time remaining in each app
   card. Show used/free/earned time underneath. Expand award accounting on demand.
   Group the combined daily limit and its recorded consumption in a summary card.
   Keep missing monitoring coverage distinct from zero usage.
4. **Use readable, localized durations.** Replace raw seconds in displayed budget
   metrics with hours/minutes/seconds, retaining seconds where they affect a
   decision. Keep existing settings inputs explicitly in seconds; provide a
   readable duration hint. Preserve exact stored milliseconds and validation.
5. **Prioritize active workouts.** Keep progress, tracking guidance, camera, and
   Cancel easy to access; move repeated explanatory text into preparation.
   The current camera preview follows several paragraphs and Cancel follows
   scrolling content. This is a follow-up outside the first implementation pass.
6. **Use consistent navigation icons.** Replace font-dependent Unicode symbols
   with local vector drawables. Keep localized labels and avoid duplicate
   screen-reader announcements.

## Approved first pass

The user approved saving this review and implementing the proposed first pass:
dashboard hierarchy, monitoring setup usability/draft restoration, localized
duration displays, and vector navigation icons. Work in the existing checkout
and preserve unrelated edits. No budget, reward, monitoring, or coverage policy
changes are intended. Workout layout is documented above for a later pass.

Primary files: `ui/ProductScreens.kt`, `ui/ProductComponents.kt`,
`ui/ScrollFitApp.kt`, `monitoring/MonitoringSetup.kt`, new duration resources in
`values`, `values-en`, `values-fr`, and local navigation vector drawables.

## Verification

- Attempt Android compilation and lint without installing or launching the app.
- Check XML parsing, matching locale keys/placeholders, and vector resources.
- Review restored draft initialization, unavailable targets, save/enable/stop
  prerequisites, duration boundaries, and dashboard budget values.
- Record actual results and any environment blockers below after implementation.
- Later device acceptance: narrow screen, large font, light/dark themes,
  Persian RTL, English/French, permission return, rotation, keyboard visibility,
  long app lists, disabled monitoring, exhausted caps, and partial coverage.

## Results

Implementation and verification are in progress. No device verification has
been performed.
