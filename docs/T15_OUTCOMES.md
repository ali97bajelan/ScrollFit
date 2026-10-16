# T15 daily and weekly outcomes

T15 adds durable monitoring coverage, coverage-aware daily/weekly usage summaries, exercise-only activity totals and awarded-time totals. Missing monitoring records stay unknown; a zero is shown only when a complete covered day has no matching usage rows. Comparisons require complete calendar periods with the same selected-app scope and time zone.

Coverage is recorded only after successful serialized observations, and it does not alter budget spending. An idle health checkpoint uses the existing monitor queue after 60 seconds without a processed observation. Room migration 2→3 is additive; history deletion stores a cutoff so late same-day coverage, accounting and activity remain hidden while enforcement counters continue.

## Verification status

Per the owner's instruction, no emulator, Gradle test task or instrumentation test was run. The core and Room regression sources are prepared but unexecuted. Physical-device behavior is pending. `python3 verification/check_source.py` and whitespace checks are recorded separately in `docs/verification/T15/`. `:app:assembleDebug` stopped before configuration because this environment has no Java/JAVA_HOME. Android compilation and authentic Room schema export remain unverified.
