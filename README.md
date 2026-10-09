<p align="center">
  <img src="./pictures/en/logo.png" alt="PocketVault" width="640">
</p>

<p align="center">
  <strong>A strictly offline, transparent, open-source password manager for Android</strong>
</p>

<p align="center">
  <a href="https://github.com/greyfreedom/PocketVault/actions/workflows/ci.yml"><img src="https://github.com/greyfreedom/PocketVault/actions/workflows/ci.yml/badge.svg?branch=main" alt="CI"></a>
  <a href="https://github.com/greyfreedom/PocketVault/tags"><img src="https://img.shields.io/github/v/tag/greyfreedom/PocketVault?sort=semver&amp;label=version" alt="Version"></a>
  <a href="./LICENSE"><img src="https://img.shields.io/github/license/greyfreedom/PocketVault" alt="Apache-2.0 License"></a>
  <a href="./app/build.gradle.kts"><img src="https://img.shields.io/badge/Android-7.0%2B-3DDC84?logo=android&amp;logoColor=white" alt="Android 7.0 or later"></a>
  <a href="./SECURITY.md"><img src="https://img.shields.io/badge/network%20permission-none-2ea44f" alt="No Internet permission"></a>
</p>

<p align="center">
  <a href="https://play.google.com/store/apps/details?id=com.turisla.hellopocket"><img src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png" alt="Get it on Google Play" width="200"></a>
</p>

<p align="center">
  <strong>English</strong> · <a href="README.zh-CN.md">简体中文</a> ·
  <a href="../../releases">GitHub Releases</a> ·
  <a href="docs/privacy_policy.html">Privacy</a> · <a href="SECURITY.md">Security</a> ·
  <a href="CONTRIBUTING.md">Contributing</a> · <a href="LICENSE">Apache-2.0</a>
</p>

<p align="center">
  <a href="./pictures/en/banner.png"><img src="./pictures/en/banner.png" alt="PocketVault product overview" width="900"></a>
</p>

---

PocketVault is a local Android password manager. It does not request `android.permission.INTERNET`, contains no analytics, advertising, telemetry, or crash-reporting SDK, and has no PocketVault account or remote backend.

Vault data is encrypted and processed on the device. Data leaves the app only when the user explicitly exports or shares a backup through an Android system destination.

> [!IMPORTANT]
> Open source and offline operation reduce risk but do not guarantee absolute security. Read the [security model and limitations](#security-model-and-limitations) and [SECURITY.md](SECURITY.md) before relying on it for sensitive data.

## Features

- **Strictly offline** — no Internet permission, Firebase, advertising, usage analytics, or remote logging.
- **Passwords, secure notes, and payment cards** — store credentials, notes, and masked card details with categories, favorites, and encrypted attachments.
- **Custom fields** — add ordered plain-text or concealed fields to passwords, secure notes, and payment cards.
- **TOTP codes** — add entries manually or scan a QR code locally with the optional camera permission.
- **Local organization** — categories, favorites, list/grid layouts, and local search across non-sensitive metadata and visible text fields. Passwords, card numbers, security codes, TOTP secrets, and concealed custom-field values are never indexed.
- **Password generator** — random passwords plus ordered combinations of fixed text, random digits, letters, and symbols. Reuse an encrypted rule library and independent template snapshots; reorder or duplicate fragments without a fragment-count cap. The last mode and rule composition, including unsaved template edits, are remembered in the encrypted vault; the generator does not persist its results. Rule-generated passwords are limited to 128 Unicode code points, and randomness estimates exclude fixed text.
- **Biometric convenience unlock** — protected by a local Android Keystore wrapping key.
- **Backup and restore** — encrypted import/export plus a bounded automatic history of up to 5 backups and 1 GiB total.
- **Sensitive-screen protection** — global `FLAG_SECURE`, background auto-lock, sensitive clipboard marking, and timed clearing.
- **Seven languages** — English, Simplified Chinese, Spanish, Hindi, Korean, Portuguese, and Vietnamese.

## Generate passwords with rules

1. Open the password generator, select **By rules**, then **Add rules**. Select reusable rules or create a fixed-text, random-digit, random-letter, or random-symbol rule.
2. Arrange the fragments in output order, for example `fixed word → fixed separator → random digits`. Drag the handles or use the move actions; the same rule can appear more than once.
3. Choose **Save as template** to reuse the composition, or **View existing templates** to load one. Templates keep independent copies, so editing or deleting a library rule does not change them.
4. **Clear current rules**, below the save button, clears the active composition and its template association. It does not delete saved rules or templates.

The app remembers the last mode, template association, and actual rule list in the encrypted vault, including unfinished drafts. Switching modes keeps the last rule list. Results are generated anew and cleared on vault lock. Fixed text adds no randomness; at least one fragment must produce different random results, and the total output cannot exceed 128 Unicode code points. See [the 2.7.0 changelog](CHANGELOG.md) for this release's changes.

## Screenshots

The screenshots contain demonstration data only. Click any screenshot to open the original image from [`pictures/en`](pictures/en/).

<p align="center">
  <a href="./pictures/en/listpass.png"><img src="./pictures/en/listpass.png" alt="Password list" width="200"></a>
  <a href="./pictures/en/addpass.png"><img src="./pictures/en/addpass.png" alt="Add password" width="200"></a>
  <a href="./pictures/en/addnote.png"><img src="./pictures/en/addnote.png" alt="Add secure note" width="200"></a>
  <a href="./pictures/en/addcategory.png"><img src="./pictures/en/addcategory.png" alt="Create category" width="200"></a>
</p>

<p align="center">
  <a href="./pictures/en/listtotp.png"><img src="./pictures/en/listtotp.png" alt="TOTP code list" width="200"></a>
  <a href="./pictures/en/addtotp.png"><img src="./pictures/en/addtotp.png" alt="Add TOTP entry" width="200"></a>
  <a href="./pictures/en/random-gene.png"><img src="./pictures/en/random-gene.png" alt="Random password generator" width="200"></a>
  <a href="./pictures/en/rule-gene.png"><img src="./pictures/en/rule-gene.png" alt="Rule-based password generator" width="200"></a>
</p>

## Security design

PocketVault uses a master-password-wrapped random data key:

1. A new vault receives a random salt and a random Google Tink `StreamingAead` keyset.
2. PBKDF2-HMAC-SHA256 derives a key-encryption key from the master password. New, re-keyed, and current-format vaults use 600,000 iterations. The historical 100,000-iteration V2 parameter is read only during a one-time authenticated migration and is never used for new writes.
3. The derived key wraps the random keyset. The master password, derived key, and plaintext keyset are not persisted.
4. Passwords, secure notes, payment cards, custom fields, categories, TOTP entries, generator rules and templates, manifests, and attachments are encrypted with Google Tink Streaming AEAD using AES-256-GCM-HKDF.
5. Changing the master password re-wraps the keyset instead of re-encrypting every vault file.
6. Biometric unlock is only a convenience mechanism: an Android Keystore key unwraps the keyset after system authentication, while the master password remains the recovery source of truth.

The implementation also includes authenticated encryption, associated-data binding, semantic integrity validation, bounded import processing, Zip Slip protection, atomic file commits, and invalidated-session protection. See [SECURITY.md](SECURITY.md) for the disclosure process and security boundaries.

## Backup metadata

Passwords, notes, payment cards, custom fields, TOTP entries, categories, generator rules and templates, attachments, and the encrypted manifest inside an exported `.hpb` backup are protected with AES-256-GCM. The backup container is not completely opaque.

Its readable `vault_v2.json` configuration includes:

- the password hint;
- the random salt and KDF parameters;
- the encrypted Tink keyset;
- version, vault identifier, and integrity-binding metadata.

Do not place sensitive information in a password hint, and store exported backups securely. A supported backup requires the password that protected it when it was created. Version 2.5.1 and later can authenticate and normalize early V2 backups before import; V1 backups remain unsupported. Version 2.7.0 writes internal schema 4, including encrypted generator rules, templates, and the last generator configuration. Schemas 1–3 remain readable; the first write, including automatically remembered generator settings, creates an encrypted backup before the transactional upgrade. Schema 4 vaults and backups require PocketVault 2.7.0 or later; 2.6.0 and earlier cannot read them. PocketVault has no account, escrow key, or master-password recovery service.

## Security model and limitations

PocketVault primarily protects app-private files and exported backups against offline reading after device or file loss. It also reduces routine screenshot, clipboard, and background exposure.

It cannot fully protect against:

- rooted, malware-controlled, or otherwise compromised devices;
- malicious keyboards, accessibility services, or highly privileged apps authorized by the user;
- offline guessing of weak or previously exposed master passwords;
- backups the user exports to an unsafe destination;
- undiscovered implementation, dependency, or supply-chain vulnerabilities;
- binaries that do not correspond to the public source or trusted signing certificate.

## Install

Google Play and this repository's GitHub Releases distribute the same official application identity:

[Get PocketVault on Google Play](https://play.google.com/store/apps/details?id=com.turisla.hellopocket)

[Download the Play-signed Universal APK from GitHub Releases](../../releases)

Both channels use `com.turisla.hellopocket` and the same Play App Signing certificate:

| Channel | Published artifact | Identity |
| --- | --- | --- |
| Google Play | Device-optimized APKs generated from the release AAB | `com.turisla.hellopocket`, Play App Signing |
| GitHub Release | Play-generated signed Universal APK downloaded from the same AAB's Play Console record | The same package and signing certificate |
| Local debug | Developer build with `.debug` suffix | `com.turisla.hellopocket.debug`, debug signing only |

The Google Play and GitHub packages are therefore one application, cannot be installed side by side, and can update one another when Android's version rules allow it. The package name alone is not proof of origin: verify the signing-certificate fingerprint and artifact hash.

The GitHub asset must be the signed Universal APK downloaded from Play Console—not a locally generated APK signed with the upload key, another release key, or a debug key. For each release, publish the exact commit and signed tag, version mapping, AAB SHA-256, Universal APK SHA-256, and Play App Signing certificate fingerprint. The maintainer checklist is in [RELEASING.md](RELEASING.md).

## Build from source

Requirements:

- Android Studio or Android SDK Command-line Tools;
- Android SDK 36;
- JDK 21 for Gradle, Android Lint, and Java/Kotlin compilation; generated bytecode remains compatible with Java 11;
- Git.

No Firebase configuration or `google-services.json` is required:

```bash
# Compile the shared release source variant
./gradlew :app:compileGooglePlayDebugKotlin

# Unit tests and static checks
./gradlew :app:testGooglePlayDebugUnitTest
./gradlew :app:lintGooglePlayDebug
```

To install a local debug build:

```bash
./gradlew :app:installGooglePlayDebug
```

The installed debug package ID is `com.turisla.hellopocket.debug`, so it can coexist with the official release. There is no separate GitHub flavor: the installable GitHub release is produced by Play App Signing from the same `googlePlayRelease` AAB.

Upload signing is read from an untracked `keystore.properties` in the repository root. Never commit a keystore, signing password, `local.properties`, or personal environment configuration.

## Technology

- Kotlin, Jetpack Compose, Material 3
- MVVM, StateFlow, Koin, type-safe Compose Navigation
- Google Tink Streaming AEAD
- Kotlinx Serialization, Protocol Buffers
- Coil for local image/video thumbnails
- kotlin-onetimepassword, ZXing

```text
app/src/main/java/com/turisla/hellopocket/
├── data/       # Vault, backup, preferences, and repositories
├── model/      # Vault and UI models
├── security/   # Tink, Android Keystore, biometrics, and sessions
├── ui/feature/ # Compose feature screens
├── router/     # Type-safe routes
└── di/         # Koin modules
```

## Contributing

- Read [CONTRIBUTING.md](CONTRIBUTING.md), then use issues and pull requests for non-sensitive bugs, features, and documentation improvements.
- Privately report anything that could expose a vault, bypass authentication, weaken cryptography, or affect malicious-backup handling by following [SECURITY.md](SECURITY.md).
- Use synthetic test data only. Never upload a real vault, credential, backup, or unredacted sensitive log.
- Update all seven locale directories when adding user-visible text.

## License

The source code is available under the [Apache License 2.0](LICENSE). See [NOTICE](NOTICE) for attribution, [third-party notices](THIRD_PARTY_NOTICES.md) for dependency licenses, and [TRADEMARKS.md](TRADEMARKS.md) for project-name and artwork guidance. Apache-2.0 does not grant trademark rights to the PocketVault/口袋密本 name, logo, or other brand identifiers.
