# QuietPDF Build Versioning

The canonical app version is declared in the root `gradle.properties` file:

```properties
VERSION_CODE=2
VERSION_NAME=1.1.0
```

## Release rules

- Increase `VERSION_CODE` for every bundle uploaded to Google Play. Play Console never accepts a
  version code that was already uploaded.
- Use `VERSION_NAME` for the user-visible semantic version: `MAJOR.MINOR.PATCH`.
- Increase `MAJOR` for incompatible product changes, `MINOR` for user-facing features, and `PATCH`
  for fixes that do not add a feature.
- A prerelease suffix such as `1.2.0-beta.1` is accepted when needed.
- Never derive a release version from Git state. The branch and release workflow belongs to the
  user, while these explicit properties keep local and CI builds deterministic.

Both properties can be overridden for a controlled CI build:

```bash
./gradlew :app:bundleRelease -PVERSION_CODE=3 -PVERSION_NAME=1.1.1
```

## Output filenames

APK packaging tasks write versioned files directly to their normal Android build directories:

- `assembleDebug` → `outputs/apk/debug/QuietPDF-debug-1.1.0-2.apk`
- `assembleRelease` →
  `outputs/apk/release/QuietPDF-release-1.1.0-2.apk`

`bundleRelease` also places `QuietPDF-release-1.1.0-2.aab` in
`app/build/outputs/versioned/`; AGP's `app-release.aab` remains in the standard bundle directory.
The filename is generated from the same validated version properties used by the manifest and
About screen. The Play upload artifact is the release AAB.

The build stops during configuration if the version code is outside the Android/Play range or if
the version name is not semantic. Settings → About reads `BuildConfig.VERSION_NAME`, so it always
shows the version packaged in the installed app.
