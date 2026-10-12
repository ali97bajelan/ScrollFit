# Room schema exports

KSP exports `com.example.scrollfit.platform.data.ExerciseGateDatabase/2.json`
here on the first successful Android build. Android configuration remains blocked
before KSP; neither the T03 v1 nor T04 v2 schema has been generated or verified.

T04 adds an explicit data-preserving 1→2 migration for `usage_cursors`.
The prepared v1-shape migration fixture is not a substitute for authentic schema
exports: generate v1 from the T03 sources and v2 from current sources once builds
work, retain both, and run MigrationTestHelper against the actual v1 export.
Every later version needs a data-preserving migration and migration tests.
Do not fabricate exports or enable destructive migration/downgrade fallbacks.
