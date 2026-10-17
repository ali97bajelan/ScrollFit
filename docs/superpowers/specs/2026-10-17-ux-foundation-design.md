# U1 — Shared product presentation design

Date: 2026-10-17. Status: implementation proposal. Covers **UX07, UX11, UX13**.

Read the [package contract](../../UX_REVIEW_IMPLEMENTATION_2026-10-17.md) and [source review](../../PRODUCT_UX_REVIEW_2026-10-16.md). Intent: make the same time, state, and action mean the same thing in Home, setup, history, challenges, and the native blocker. This is a presentation slice; policy and counter thresholds remain unchanged.

## Existing boundaries and selected approach

`ProductComponents.duration` and `HistoryScreen` currently format durations differently. `SettingNumbers` accepts whole seconds in three digit scripts. `ScrollFitApp` selects a Material color scheme; `ChallengeActivity` does not share it; `BudgetOverlay` constructs native views. Keep these entry points and add small common adapters. Do not replace the overlay with a new Compose/window lifecycle or rewrite navigation.

## B1 — Exact, localized duration and date display

Add pure `DurationParts(hours: Long, minutes: Int, seconds: Int, milliseconds: Int)` and `DurationDisplay.parts(ms: Long): DurationParts` under `core/settings`. Clamp negative display inputs to zero; never mutate stored values. Split integer milliseconds before rendering, so 59,999 ms is 59.999 seconds rather than a rounded 60-second component. Render nonzero components in descending units; render zero as localized `0 sec`. Whole minutes omit seconds. Fractions use up to three decimal digits, trimming trailing zeroes. Examples in English: 45,000 → `45 sec`; 1,800,000 → `30 min`; 3,900,000 → `1 hr 5 min`; 60,001 → `1 min 0.001 sec`.

Add Android `ProductFormatters.duration(context: Context, ms: Long): String` and `date(context: Context, day: LocalDate, full: Boolean = false): String`. Use the effective resource locale (`context.resources.configuration.locales[0]`) for numbers, date formatting, and bidi wrapping. Use localized unit resources/plurals and no raw string concatenation across translated sentences. Date display uses `java.time` localized Gregorian dates; ISO day identifiers remain persisted and available to export. All presentation entry points, including committed reward result text, use the same formatter. TTS can retain its grammatical reward sentence while sharing exact numeric decomposition.

## B2 — Minutes-first entry without precision loss

Add `DurationUnit { MINUTES, SECONDS }` and `DurationInput.parse(text: String, unit: DurationUnit, positive: Boolean): Long?`. Accept only trimmed nonnegative whole numbers in ASCII, Persian, or Arabic digits, using checked integer multiplication by 60,000 or 1,000. Blank, signs, decimal separators, exponent notation, and overflow return null. Positive fields reject zero; allowances/caps permit zero.

Add shared Compose `DurationSetting(value: String, unit: DurationUnit, onValueChange: (String) -> Unit, onUnitChange: (DurationUnit) -> Unit, label: Int, positive: Boolean, enabled: Boolean)` with a localized conversion hint derived from the valid draft. Invalid drafts show a field error and no misleading conversion. Whole-second saved values not divisible by a minute open in seconds. A saved subsecond value displays exactly and remains unchanged until explicitly edited; the UI must not rewrite a loaded millisecond value through a seconds round-trip. Switching units converts only when integral; otherwise preserve the old unit/value and explain that the value cannot be represented as a whole number in the other unit. Reward rules still persist whole seconds and repetitions still use `positiveInt`.

## B3 — Theme and action hierarchy

Add `ScrollFitTheme(content: @Composable () -> Unit)` used by both activities. Use the existing system light/dark Material 3 policy. Add `ProductTokens` for spacing (4, 8, 12, 16, 24 dp), 48 dp minimum interactive height, and semantic roles: surface, onSurface, primary/onPrimary, secondary, error, outline. Compose consumes Material scheme roles; `OverlayStyle.resolve(context)` maps the same light/dark policy to native colors and scaled text. Resolve native button/background colors explicitly; do not rely on a default native button theme matching Compose.

Blocker order: reason/app name; primary **Take a break** (Home action for budget blocks, Finish for a session prompt); **Earn time** when offered; **Continue** for a valid session prompt; **Exercise without rewards**; then a secondary group for **Open ScrollFit** and **Stop monitoring**. Preserve system navigation, existing session tokens, stale-result guards, and the immediate Stop path. U2 adds the expanded reward disclosure; U1 only establishes common styling and action order.

Use flexible wrapping/vertical action layouts for narrow widths, large fonts, and long French labels. State is never conveyed by color alone. Theme parity and contrast require device/render verification; source tokens do not constitute a contrast pass.

## B4 — Production vocabulary and recovery copy

Canonical English concepts: **Free time** = base allowance; **Earned time** = unspent awarded credit; **Daily reward cap** = award capacity rule; **Shared daily limit** = combined consumption ceiling; **Usable now** = minimum of wallet and shared remainder. Do not rename persisted enum/constants solely to match copy.

Replace live device-investigation language in `monitor_healthy` and `monitor_limits_notice` with concise connection/limitations explanations. Connected copy says access is connected and monitoring can have gaps; it never guarantees blocking. Move delivery investigation notes into the package's device acceptance record. Camera model errors offer Retry, then app information/diagnostics; remove reinstall as the default action. If any remaining support text mentions reinstall, explicitly explain local-record loss first.

Inventory string references in main, debug, release, XML, and generated/indirect lookup code before deleting stale keys. Retain uncertain keys with a documented reason rather than guessing. Add/update matching Persian, English, and French keys and placeholders together; translations must preserve cap/ceiling distinctions. No in-app language/theme settings or Persian-calendar change is included.

## Acceptance and review

1. All duration surfaces give identical values for 0, 999, 45,000, 59,999, 60,000, 60,001, 3,900,000, and `Long.MAX_VALUE` ms; no component independently rounds to 60 seconds.
2. `DurationInput.parse("۵", MINUTES, true)` is 300,000; Arabic `٣٠` seconds is 30,000; overflow/invalid input never throws. Unit switching does not truncate.
3. Main and blocker-launched challenge share effective theme, locale, and action semantics; native overlay has equivalent roles and 48 dp targets.
4. Blocker Stop and system navigation work even if offers fail to load. Native overlay callbacks still reject replaced windows.
5. No reachable release copy includes development investigation notes, obsolete feature claims, or a default destructive recovery suggestion. All three locales retain placeholder parity.

Automate arithmetic/input tests and UI semantics/action assertions. Verify color contrast, label wrapping, keyboard, TalkBack, and physical screen appearance separately. This document records no runtime results.
