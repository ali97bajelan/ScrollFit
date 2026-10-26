# Settings screen redesign

**Status: proposed; awaiting approval.**

## Intent

Make the existing ScrollFit Settings screen easier to scan and use by grouping related controls, giving monitoring status a clear place, and separating sensitive history actions from everyday preferences. Keep the screen consistent with the app's Material 3 Compose UI and preserve current save and persistence behavior.

## Goals

- Let users quickly find monitoring status, exercise reward rules, workout preferences, and local data actions.
- Keep the current monitoring setup and stop actions visible and understandable.
- Keep the reward disclaimer and the history deletion consequences close to their related controls.
- Preserve Persian RTL, English and French strings, large text, and accessible touch targets.

## Non-goals

- Add the gated F-A monitoring recovery center or F-B setup presets from the project review.
- Change reward calculations, persistence, monitoring lifecycle, history retention, or navigation routes.
- Add new settings or remove existing settings.

## Approaches

1. **One page with grouped sections — recommended.** Organize the existing controls into Monitoring, Exercise rewards, Workout preferences, and Data & privacy sections on the current scrollable screen. This improves hierarchy while preserving the current navigation and single save flow.
2. **Settings hub with subpages.** Put a summary for each group on the first screen and navigate into separate pages. This shortens each page but adds routes and hides less-frequent controls behind extra taps.
3. **Collapsed sections.** Keep one page and put each group in an accordion. This reduces initial scroll length but hides controls and makes validation and save feedback harder to discover.

**Proposed choice:** approach 1. No layout preference was supplied, so the recommendation favors the smallest change that gives every existing setting a clear home.

## Proposed screen structure

1. **Monitoring:** a card containing the current health message and failure/gap details, followed by the existing App monitoring and budgets action and, when enabled, the existing Stop monitoring action.
2. **Exercise rewards:** the existing suggested-defaults explanation and all exercise repetition/reward inputs, grouped under each exercise name. Keep one Save settings action after the editable settings.
3. **Workout preferences:** the speech switch, preferred camera choices, and default exercise choices, using the existing values and selection behavior.
4. **Data & privacy:** the existing delete-history explanation and confirmation action, visually separated from routine controls, plus the existing setup/privacy destination.

Keep inline loading, save error, validation, and success feedback associated with the save area. Use existing theme colors and typography, clear section headings, comfortable spacing, and at least 48 dp interactive rows. Preserve RTL through Compose layout direction rather than hard-coded left/right placement.

## Constraints and remaining calls

- The screen remains a vertically scrollable page above the existing bottom navigation.
- Existing localized strings should be reused where they fit; add matching Persian, English, and French resources for any new user-facing copy.
- Keep the save transaction, dirty-field handling, delete confirmation, and callbacks intact.
- Implementation and source review will not run Gradle, builds, lint, or device QA, following the task document's implementation/QA split. No test run is requested here.
- The implementation may settle minor visual details within the existing theme; it may not add gated behavior or alter product rules.
