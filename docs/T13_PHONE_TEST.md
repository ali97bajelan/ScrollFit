# T13 phone acceptance — pending

Intended phone: physical Android 14 phone. No results have been recorded.
An APK is not available: the Android build is blocked before compilation at AGP
resolution. Run these checks after a real debug APK can be built and installed.

1. Open Monitoring setup, select two safe launchable apps A and B, grant the
   existing access and enable monitoring. Set the session length to 10 seconds,
   each base allowance to at least 120 seconds and combined ceiling to 600
   seconds. Save. Relaunch setup and confirm the session setting persists.
2. Use A for about 6 seconds, switch to B for about 3 seconds, visit Home, then
   return to A. A should prompt after its remaining roughly 4 seconds. B must
   retain its own partial session. Record observed timing and displayed budgets.
3. Leave A's prompt visible for 30 seconds. Confirm neither app usage nor the
   combined total advances. Screen off/on, Quick Settings and returning from an
   unrestricted app must keep the prompt pending and consume no prompt time.
4. Tap Continue once. A should resume immediately without camera, exercise or
   new credit. Its next prompt should follow a fresh 10 seconds of foreground
   use. Rapid duplicate taps must not skip the following session.
5. At a prompt tap Finish. Home should appear; unused base/earned time and the
   combined total must remain unchanged. Reopen A: it gets a fresh session using
   only its existing balance. If Home navigation fails, the prompt must remain.
6. Repeat with zero base allowance and banked exercise credit. Continue must
   spend that existing credit without another challenge or another award.
7. At a pending prompt kill/reopen the app process, reconnect the service, then
   reboot and restore monitoring if Android requires it. Return to A: the prompt
   must persist. Also test a partial session through restart. Unknown downtime
   must not be backfilled; document any last-checkpoint loss separately.
8. At a prompt increase session length and save. The pending prompt must remain.
   Continue starts the newly configured length. Lower a partial window below
   already accumulated use: a prompt must appear without resetting usage.
   Try zero, negative, blank and overflowing settings; none may save. Verify
   Persian/Arabic digits and the default 300-second setting.
9. Make both app allowance and session length 10 seconds with no earned credit.
   At the collision show the app-budget blocker, with no free Continue. Repeat
   with the combined ceiling as the binding limit while unused credit remains:
   show the combined blocker and retain credit. Earning credit at the app cap
   must still leave a pending session requiring explicit Continue.
10. Check local midnight with a partial session and a pending prompt. New-day
    windows/budgets start normally; old credit expires. Record actual results.
11. Switch from a pending prompt to setup, remove/re-add its app or delete visible
    history when that control is available. Today's pending/partial session and
    enforced usage must survive. Disabling/re-enabling monitoring also preserves
    them. Permission loss must report unavailable enforcement and preserve data.
12. Repeat prompt controls in Persian RTL, English and French, including large
    text, screen rotation, keyboard/system navigation and rapid app switching.
    Unrestricted apps, Home, emergency/system surfaces must remain accessible.

Record APK identity, OS build, timestamps, A/B observed timings, before/after
wallet and combined totals, prompt state after restart, and any OEM intervention.
Do not mark T13 verified until these results and the Android persistence checks
are actually recorded.
