# Settings Screen Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reorganize the existing Settings screen into four clear sections while preserving all existing settings, persistence, monitoring, and deletion behavior.

**Architecture:** Keep the current single scrollable Compose page and its state/callback flows. Add section cards in `SettingsScreen.kt`, place save feedback immediately after editable settings, and add localized section headings in the existing Persian, English, and French resource files.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Android string resources.

**Spec:** `docs/superpowers/specs/2026-10-26-settings-screen-redesign-design.md`

## Global Constraints

- The screen remains a vertically scrollable page above the existing bottom navigation.
- Existing localized strings should be reused where they fit; add matching Persian, English, and French resources for any new user-facing copy.
- Keep the save transaction, dirty-field handling, delete confirmation, and callbacks intact.
- Do not add gated F-A monitoring recovery or F-B setup presets, change reward calculations, persistence, monitoring lifecycle, history retention, or navigation routes, or add/remove settings.
- Preserve Persian RTL, English and French strings, large text, and accessible touch targets; interactive rows remain at least 48 dp high.
- Implementation and source review will not run Gradle, builds, lint, or device QA. No test run is requested here.

## Review Focus

- Persian RTL: section cards and selection rows rely on Compose layout direction and use no hard-coded left/right placement; inspect the source for logical layout modifiers.
- Long French copy and large text: headings, notices, labels, and feedback remain in wrapping layouts without fixed heights; inspect the changed Compose structure for clipping constraints.
- Monitoring disabled: the health status remains visible while Stop monitoring only appears when monitoring is enabled; inspect the conditional rendering and original action callback.
- Invalid reward inputs: validation remains adjacent to Save settings and prevents the existing save callback; inspect the validity guard and button enablement.
- Asynchronous save and deletion: loading, failure, and success feedback remain near their respective actions while the save transaction and delete confirmation callbacks remain unchanged; inspect each action path.

---

### Task 1: Group Settings controls and localize section headings

**Files:**
- Modify: `app/src/main/java/com/example/scrollfit/ui/SettingsScreen.kt`
- Modify: `app/src/main/res/values/product_strings.xml`
- Modify: `app/src/main/res/values-en/product_strings.xml`
- Modify: `app/src/main/res/values-fr/product_strings.xml`

**Interfaces:**
- Consumes: existing `ProductPage`, `ProductContent`, `MonitoringHealthInfo`, `NumberSetting`, `DurationSetting`, `SelectionRow`, persistence APIs, and existing callbacks.
- Produces: a private section-card composable in `SettingsScreen.kt` and four new string IDs: `settings_section_monitoring`, `settings_section_rewards`, `settings_section_preferences`, and `settings_section_data_privacy`, with Persian, English, and French values.

- [ ] **Step 1: Add the four section headings to each locale resource.** Use concise localized labels for Monitoring, Exercise rewards, Workout preferences, and Data & privacy.
- [ ] **Step 2: Add section cards and regroup the existing controls in `SettingsScreen.kt`.** Keep monitoring status and setup/stop actions together; keep the suggested-defaults notice and each exercise's existing repetition/reward inputs together; keep speech, camera, and next-exercise choices together; place history explanation, delete confirmation action, and setup/privacy destination together.
- [ ] **Step 3: Place Save settings after all editable controls and before Data & privacy.** Keep validation, loading, save failure, and save success feedback next to that action. Keep deletion feedback with the Data & privacy controls. Preserve the current save transaction, dirty-field merge logic, monitoring callback, and delete confirmation flow.
- [ ] **Step 4: Review the source diff against the spec.** Confirm four visible sections, localized headings in all three locales, no new or removed settings, minimum 48 dp action rows, no hard-coded horizontal directions, and unchanged save/delete/monitoring callbacks. Do not run Gradle, builds, lint, tests, or device QA.
