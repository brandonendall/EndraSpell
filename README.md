# EndraSpell

## Latest test build — Build 28

[**Download signed EndraSpell Test Build 28 (APK)**](https://github.com/brandonendall/EndraSpell/raw/refs/heads/main/downloads/EndraSpell-Test-Build28.apk)

Fresh installs start with one editable **Learner 1**. The learner-selection page includes **Add Learner** and **Remove Learner**. Rename learners through the home menu's Edit Learners option. Removing a learner archives their words and history; restore them from Archived.

The forest-and-gold artwork is unchanged. Automatic voice / My recordings choices and previous-screen Back navigation are included.

### Installation

Requires Android 8.0 or newer. Download the APK on your Android device, open it, and allow installation from your browser if Android asks.

**Build 28 installs separately as EndraSpell Test. Keep your existing app.** Build 27's signing key was unavailable, so this test uses a new app identity and signing key. It starts with separate learner data; older words, recordings, and history remain in the older app and do not transfer automatically.

### Verification

- APK signature verified.
- Four learner-data unit tests passed: fresh-install defaults, rename/archive/restore data preservation, legacy profile migration, and no reseeding after archiving all learners.
- All 59 packaged artwork image files match Build 27 byte for byte.
- On-device testing of this build is still needed.

Version: `0.1.28` (28) · Application ID: `com.endra.spell.test`

SHA-256: `ebe7ca9cf62e22d132af61bf94f436305092b7ace6f26e1d5a0982e5d46dd307`

## Previous approved build

[Download EndraSpell Preview Build 27](https://github.com/brandonendall/EndraSpell/raw/refs/heads/main/downloads/EndraSpell-Preview-Build27.apk)

The downloadable APKs are the authoritative installs. The repository's existing source and automated prototype builds are historical and are not the source snapshots for these APKs. This publication updates the APK download and installation instructions only.
