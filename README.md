# EndraSpell

## Approved development source — Checkpoint B, Build 34

This branch contains the approved Checkpoint A and B implementation, including the rectangular worksheet crop and normal-sized post-crop preview. Build 34 (`0.1.34`) passed 48 unit tests, phone and tablet crop/OCR checks, and an in-place upgrade check with saved foundation data unchanged. The Scan Word List screen and the Read Words → Review & Confirm → Save → Practice flow were approved after testing on a real device on October 13, 2026.

The signed Build 34 test APK uses the existing `com.endra.spell.test` app identity and private test signing key. The key is **not** in this repository. A standard GitHub Actions debug APK uses a different signing key and must not be installed over a saved-data test installation. Keep and install the separately supplied signed test APK for upgrades.

Build 29 remains available as the prior approved GitHub release and download. Its source commit is preserved by the `build29-approved` branch. This source update does not publish Build 34 as a public release.

## Public download — Build 29

[Download EndraSpell Build 29 (APK)](https://github.com/brandonendall/EndraSpell/raw/refs/heads/main/downloads/EndraSpell-Build29.apk)

Build 29 is the approved Build 28 app with its display name and version updated. It requires Android 8.0 or newer. Install directly over Build 28 without uninstalling to retain its learner data. Older Build 27 used a different app identity and does not transfer data automatically.

Build 29 APK SHA-256: `90640a1a2b5e4435a661f8f2262d9315a38cb9d1a89b305490ef057084fd8737`
