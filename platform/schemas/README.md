# Room schema exports

KSP exports `com.example.scrollfit.platform.data.ExerciseGateDatabase/1.json`
here on the first successful Android build. That build is currently blocked
before KSP; no schema JSON has been generated or verified.

Commit the generated v1 schema before introducing another database version.
Every later version needs a data-preserving migration and migration tests.
Do not fabricate exports or enable destructive migration/downgrade fallbacks.
