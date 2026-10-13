# EndraSpell Checkpoint A — Foundation & Data Safety

Baseline: approved Build 29, package `com.endra.spell.test`, version 0.1.29 (29).
Baseline APK SHA-256: `90640a1a2b5e4435a661f8f2262d9315a38cb9d1a89b305490ef057084fd8737`.
The public repository README explicitly identifies its checked-in prototype source as historical. This project uses the Build 29 private recovery source and matching signing key instead.

Build 30 changes only the persistence facade, a new foundation store, build version/test configuration, and acceptance tests. Home, other approved screens, artwork, launcher resources, and the audio implementation are unchanged. No Checkpoint B or later features, new controls, camera import, languages UI, quiz changes, PIN, or public release publication are included.

## Storage and migration

`files/foundation-v1.json` is the authoritative versioned snapshot. Each learner has a permanent ID, display name, archive state, words, lists, and attempts. Existing learner IDs remain unchanged; new learners receive UUIDs. Renaming never changes relationships. Lists, words, and attempts receive stable UUIDs. Removed learners are archived without cascading deletes. Empty/archived profiles never trigger reseeding.

Each learner's word identity is normalized spelling plus language. List memberships retain order. Words have a language, optional English meaning, independent Extra Practice flag, and recording association. A learner has one active spelling week and one persistent active Heart Words list. Archived lists retain their membership and previous history fields. All migrated lists/words/attempts are English. Language fields are extensible; additional language behavior is outside Checkpoint A.

The original SharedPreferences and recording files remain untouched. The new snapshot also retains a complete legacy preference snapshot, including unknown keys. Migration validates all IDs and references before its first write. Malformed legacy data fails closed; it is not replaced with default data. Migration does not repeat once a valid versioned snapshot exists. A newer schema is rejected instead of silently downgraded.

Writes use a flushed temporary file and atomic replacement, with a separately validated last-good snapshot. Failed transactions do not mutate a cached live object. Corrupt current data recovers from the last-good snapshot; if both are unusable, the store retains them and refuses to reset. Recovery can restore the last successfully backed-up state; it cannot reconstruct data absent from both copies. All store operations are synchronized, and separate store instances reload committed data before each transaction.

Legacy attempts lack reliable week IDs. Their complete original fields remain intact, and the new week reference is explicitly marked unknown rather than inventing history. New attempts record the active list ID, word ID, language, category, answer, result, and local calendar date. Listening-only events remain in history but do not affect mastery or miss counts.

## Weekly lists and Heart Words

The existing Save List behavior remains: a changed spelling list archives the previous membership; saving unchanged content creates no duplicate archive. Backend `startNewWeek` archives the active spelling week, creates an empty new week, and preserves Heart Words, attempts, mastery/review status, and recording relationships. No new Start New Week screen/button is added in this checkpoint. Heart Word edits do not archive spelling weeks. Previously archived weeks stay archived even when their words are reused. Shuffle operations on returned lists cannot mutate saved order.

## Mastery and review foundation

Correct spelling on three distinct local calendar dates earns mastery. Same-day repetitions count once. A normal word enters Needs Review after two misses; a historically mastered word enters after one miss. Correct spelling on two distinct dates during review clears Needs Review, preserving historical mastery. A miss during review restarts its recovery dates. The two-miss rule counts misses since the last completed recovery (it does not invent a consecutive-miss requirement). Extra Practice is a separate manual flag that automatic review changes never clear. These are backend foundations; no new status screen is added.

## Recordings

Existing recordings remain at `files/word_recordings/<normalized-word-sha256>.m4a`. The prior app shares these recordings by word across learners; that relationship is explicitly retained as `legacy-shared-word` rather than silently splitting or moving audio. The same word retains its recording when a learner is renamed or a week is archived. The approved audio implementation keeps missing/corrupt recording fallback to Automatic Voice. Actual TTS pronunciation remains dependent on voices installed on the device.

## Build

Use JDK 17, Gradle 8.10.2, Android SDK 35, and the original recovery signing directory as a sibling of EndraSpell. Set `local.properties` to the local SDK path. Run `gradle :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest`. The Android instrumentation APK is for development checks only; install the main debug APK for user testing. Do not publish the signing directory or private recovery archive.
