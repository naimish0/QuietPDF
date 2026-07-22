# Technical validation

The focused validator checks required files, candidate size ladders, 512×512 Play uploads, PNG
decoding, opaque 32-bit RGBA, explicit sRGB chunks, the 1024 KB limit, transparent/pre-rounded
corners, adaptive XML parsing, foreground/background/monochrome resolution, legacy density exports,
placeholder-like filenames, competitor raster filenames, and asset-inventory completeness.

Command:

```bash
python3 play-store-assets/icon-v3/tools/validate_icon_assets.py
```

Result: **PASS** — production and experiment icons are 512×512 opaque RGBA sRGB PNGs, adaptive
foreground/background/monochrome references resolve, all legacy density exports and installed WebP
fallbacks exist, and the inventory is complete. The production Play icon is 13,382 bytes.

Android verification:

```bash
./gradlew :app:processDebugResources :app:processDebugMainManifest :app:assembleDebug :app:lintDebug :app:testDebugUnitTest
./gradlew :app:assembleDebugAndroidTest
./gradlew :app:processReleaseResources
```

All three commands completed with `BUILD SUCCESSFUL`. Debug and release resource processing, manifest merge, debug
assembly, Android lint, unit tests, and instrumented-test APK compilation passed.

A focused `LauncherIconTest` run was attempted on a connected Samsung SM-S928B running Android 16
(API 36). The runner executed zero tests because installation was rejected with
`INSTALL_FAILED_UPDATE_INCOMPATIBLE`: an existing `com.rameshta.quietpdf` package had a different
signing key. The existing package was deliberately not uninstalled because that could delete user
data. No emulator executable was available, so Android 8, Android 12, Android 13, and clean-install
Android 16 launcher/splash device checks remain manual coverage.

No Play Console upload is performed by this validation.
