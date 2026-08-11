# Release and verification process

Google Play and this repository's GitHub Releases distribute one official application identity: `com.turisla.hellopocket`, signed with the same Play App Signing key. The GitHub asset is the signed Universal APK downloaded from Play Console for the exact AAB uploaded to Google Play. There is no separately built or signed GitHub flavor.

## Before a release

1. Confirm the new `versionCode` is greater than every artifact in every Play Console track.
2. Update `versionName`, `CHANGELOG.md`, the privacy policy revision date, supported-version documentation, localized Play release notes, and store screenshots when the UI has materially changed.
3. Keep `CHANGELOG.md` marked `Unreleased` during candidate work; replace it with the actual publication date before creating the signed source tag.
4. Run the repository checks, including the release R8 and resource-optimization path:

   ```bash
   ./gradlew :app:compileGooglePlayDebugKotlin
   ./gradlew :app:testGooglePlayDebugUnitTest
   ./gradlew :app:lintGooglePlayRelease
   ./gradlew :app:optimizeGooglePlayReleaseResources
   cmp app/src/main/assets/privacy_policy.html docs/privacy_policy.html
   ```

5. Perform a Play-signed upgrade test over the latest production version using a synthetic vault. A debug build is a separate application and is not an upgrade-path test.
6. Verify passwords, secure notes, payment cards, custom fields, TOTP, categories, attachments, biometrics, password changes, export, import, automatic backup, and backup restoration after the upgrade.
7. For a release that changes vault compatibility, verify both paths: the newest supported vault and backup must open successfully, while deliberately unsupported formats must fail without modifying the original data.
8. Confirm the merged release manifest contains no Internet or network-state permission and no Firebase or Crashlytics component.
9. Review runtime dependencies and update `THIRD_PARTY_NOTICES.md` and the in-app notices when a dependency family or license changes.
10. Confirm that GitHub will receive only the Play-generated Universal APK—not a locally signed APK.

The version-specific candidate checklist for this release is [`docs/release-checklists/2.6.0.md`](docs/release-checklists/2.6.0.md), and copy-ready Play Console text is in [`docs/release-notes/2.6.0.md`](docs/release-notes/2.6.0.md).

## Source and artifact traceability

For each production release:

1. Create a signed Git tag matching the version, such as `v2.6.0`.
2. Build the `googlePlayRelease` AAB from that exact tag with the untracked upload-signing configuration.
3. Record the AAB SHA-256 digest, then upload that exact AAB to Google Play.
4. In Play Console, open **Test and release → Latest releases and bundles**, select the uploaded bundle, open **Downloads**, and download the signed Universal APK.
5. Verify that the Universal APK uses package name `com.turisla.hellopocket` and the expected **Play App Signing certificate** SHA-256 fingerprint.
6. Record the Universal APK SHA-256 digest.
7. Create the GitHub Release from the same signed tag and attach only that Play-generated Universal APK.
8. Publish the tag, commit ID, versionCode, versionName, AAB SHA-256, Universal APK SHA-256, and Play App Signing certificate SHA-256 fingerprint in the release notes.
9. Retain the Play Console release record so the uploaded AAB and downloaded Universal APK remain traceable.

Useful local verification commands:

```bash
shasum -a 256 app-googlePlay-release.aab PocketVault-universal.apk
apksigner verify --verbose --print-certs PocketVault-universal.apk
```

The repository also provides a reusable verifier and GitHub Release publisher. Place the exact uploaded AAB and the Play Console Universal APK in `app/googlePlay/release/` using the standard names, then run:

```bash
# Verify artifacts and generate release notes without uploading anything.
./scripts/publish-github-release.sh

# After reviewing the generated notes, publish the GitHub Release and attach only the APK.
./scripts/publish-github-release.sh --publish
```

The script derives the expected package, version code, version name, and tag from `app/build.gradle.kts`. It verifies archive integrity, the AAB upload signature, the APK signature, package/version metadata, the offline manifest, the source tag, and the Play App Signing certificate before calculating hashes. The configured Play App Signing certificate SHA-256 fingerprint is:

```text
80:D6:DE:92:50:30:84:33:55:BE:73:7D:49:F8:F6:A4:16:85:5A:B6:CD:59:44:5A:AC:93:F2:7C:64:10:E7:F1
```

Publishing requires the GitHub CLI. Install it once with `brew install gh`, authenticate with `gh auth login`, and continue using the same script for later releases. Use `--help` to see custom artifact and release-note options.

Never upload a locally generated APK signed with the upload key, another release key, or a debug key. Even with the same package name, a different certificate is not the same Android application identity and cannot update the Google Play installation.

The upload certificate and Play App Signing certificate may be different. The fingerprint communicated to users must be the Play App Signing certificate shown by Play Console, because both Google Play and the downloadable Universal APK use that installed-app identity.

## Privacy rollout

Before changing Google Play Data safety to “no data collected or shared,” confirm that no currently distributed version in production, open testing, or closed testing contains Firebase or another reporting SDK. Publish the new privacy-policy URL before submitting the Play update.

## Key safety

- Keep keystores and `keystore.properties` outside version control and CI logs.
- Store CI secrets with least privilege and require two-factor authentication on maintainer accounts.
- If only an upload key is compromised, follow the Play Console upload-key reset process rather than treating it as the Play App Signing key.
