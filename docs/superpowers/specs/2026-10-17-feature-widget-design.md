# P6 — Honest home-screen status widget (F08)

Date: 2026-10-17. Status: demand-gated Stage D proposal. Entry: U1/U3 acceptance and an observed need to check time without opening another app. Read the [package contract](../../UX_REVIEW_IMPLEMENTATION_2026-10-17.md).

## Intent and minimum

Show a captured budget for one to three explicitly chosen selected apps, plus Open ScrollFit and Refresh. Multiple widget instances have independent selection. Use native RemoteViews/AppWidgetProvider, avoiding a new widget UI dependency. Android's [widget update documentation](https://developer.android.com/develop/ui/views/appwidgets/advanced) specifies that periodic widget updates do not support intervals below 30 minutes. Therefore this is a timestamped snapshot, never a live countdown or enforcement indicator.

## V1 — Snapshot model and truth

Per app, show label/package fallback and captured `Budget.usableMs` using U1 formatter. Never sum app wallets into a single available total because they share the combined ceiling. Include captured day/zone, **Snapshot at [date/time] — may have changed**, and an absolute freshness cutoff `capturedAt + 300,000 ms` as **Refresh after [time]**. A rendered snapshot older than five minutes is labeled **Older snapshot**; new render passes recompute that classification. A host that has not rerendered still shows its original timestamp/cutoff and uncertainty text; no promise that a label flips automatically at five minutes.

States: Unconfigured, Loading, Snapshot, Older snapshot, Monitoring off, Access disconnected, Data unavailable, and App unavailable. Health and data freshness are separate. A prior numeric value may remain only under an explicit older/unavailable snapshot label; on a new day/zone, remove old numeric values and show Refresh. Never imply that cached `HEALTHY` verifies the current process/service. Re-evaluate current access/service on update and mark prior connection state as historical. If schedule feature P4 is installed, show active rule name and captured effective limit; otherwise use saved manual policy.

## V2 — Refresh and lifecycle

Request system updates at 1,800,000 ms, acknowledging delivery is inexact. Also refresh on explicit widget Refresh, configuration, app resume, relevant saved plan/enable/Stop change, committed result, monitor disconnect, and history clear. Monitor spending can request a throttled refresh at most once per 60,000 ms per widget group; no extra service or usage observer is introduced. Read current persisted budgets through the existing engine in read-only mode. No widget can spend, grant rewards, enable monitoring, reset sessions, or launch a restricted app.

Provider async work has an 8,000 ms timeout, always finishes its broadcast result, and publishes Data unavailable on failure. Retain cache metadata only for explaining older values, never as permission truth. A process/boot restart invalidates cached current-connection claims; refresh before showing current status. Use boot identity/elapsed time to detect cached boot mismatch and wall-clock regression. No notification, exact alarm, or battery-exemption request. System/launcher delays remain visible through the timestamp/uncertainty design.

## V3 — Configuration and interaction

Configuration activity lists only currently selected eligible apps, with maximum three. Empty selection cannot finish; offer Open setup. If a configured app becomes unselected/unavailable, keep its slot labeled Unavailable until explicitly edited; do not substitute another app or silently delete the choice. Widget removal deletes only that instance's local configuration/cache.

All click intents are explicit and immutable, keyed by widget ID. Open ScrollFit navigates to Home; recovery links may open setup/Review but never enable anything. The host-facing configuration activity validates the supplied appWidgetId against IDs owned by this provider and returns RESULT_CANCELED for invalid IDs. Internal refresh receiver rejects unknown IDs; hostile extras cannot choose an arbitrary app or change budgets. Keep private callbacks internal and protect any required host-facing exported component by input validation.

Use responsive layouts with at least 48 dp clickable areas, no essential color-only meaning, effective app locale, RTL and long-label truncation with accessible full labels. Native light/dark styling uses U1 semantic roles. The content description includes each value's snapshot timestamp and status rather than announcing it as remaining live time.

Acceptance: zero vs data failure distinguish; stale/cross-day/zone/boot cache cannot masquerade as current; three app rows do not imply summed availability; Refresh/Stop/disconnect update honestly; two widgets remain independent; missing app and invalid intents are safe. Record launcher resizing, API 28/37 behavior, process kill/reboot, delayed delivery, themes/locale/TalkBack and battery observations before release. No refresh-frequency/performance gain is claimed from source inspection.
