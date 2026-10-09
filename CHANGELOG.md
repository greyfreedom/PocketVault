# Changelog

All notable changes to PocketVault will be documented in this file.

## [2.7.0] - 2026-10-09

### Added

- Rule-based password generation from ordered fixed text, random digits, random letters, and random symbols. Select multiple reusable rules, duplicate fragments, and drag to reorder them without a fragment-count cap.
- An encrypted rule library and combination templates with independent rule snapshots, included in vault backups. Templates can be reused, updated, or saved as new copies.
- Local encrypted memory of the last generation mode, selected template, and actual rule order, including unfinished compositions and edits not yet saved to a template. Switching to random generation preserves the last rule composition.
- An English and Simplified Chinese project website, plus a helper that creates, verifies, and pushes signed release tags from the configured app version.

### Changed

- Refreshed rule cards with sequence badges, type icons, clearer content, and separate drag and action controls. The template entry is now “View existing templates”; “Clear current rules” is a button below “Save as template”.
- Advanced the vault's internal schema to version 4 with encrypted `generator_rules.dat`. Schemas 1–3 remain readable; the first write, including remembered generator settings, creates an automatic encrypted backup before a transactional upgrade. Schema 4 vaults and backups require PocketVault 2.7.0 or later; 2.6.0 and earlier cannot read them.
- Rule-generated passwords use cryptographically secure randomness and are limited to 128 Unicode code points. Fixed text contributes no randomness, and generation requires at least one rule with multiple possible random results. The generator keeps results in memory only and clears them when the vault locks.
- Added all generator controls, validation messages, and local-memory feedback in the seven supported app languages.

### Fixed

- Dragging the first visible rule no longer shifts the list viewport or keeps moving the rule after finger movement stops.
- System text-selection copying in generator results and rule editors now uses the sensitive clipboard path and its timed clearing.
- Delayed callbacks from a locked generator session cannot restore results or overwrite settings in a new vault session.

## [2.6.0] - 2026-08-11

### Added

- A dedicated payment-card entry type with encrypted cardholder name, card number, brand, expiration date, security code, notes, categories, attachments, and custom fields. Card numbers and security codes remain masked until explicitly revealed.
- Ordered custom fields for passwords, secure notes, and payment cards. Fields can be plain text or concealed, reordered, copied, removed, and restored while editing.

### Changed

- Unified password, secure-note, payment-card, and manual-TOTP add/edit forms around the same Material 3 section, card, input, selector, and action patterns. Tapping outside an editor now clears focus consistently.
- Extended local search to payment-card holder names and brands, custom-field names, and non-concealed custom-field values. Passwords, card numbers, security codes, TOTP secrets, and concealed custom-field values remain excluded.
- Advanced the encrypted vault's internal schema to version 3. The first write to an older supported schema creates an automatic backup before the transactional upgrade.

### Fixed

- Payment-card number formatting now preserves the logical cursor position, preventing digits from moving behind the cursor while typing or editing.
- Payment-card add and edit screens now use consistent borderless fields, lighter selectors, clearer empty states, and matching category, attachment, notes, and action sections.

## [2.5.1] - 2026-08-06

### Added

- A narrowly scoped, one-time migration for early V2 vaults and backups. The app authenticates core data, TOTP entries, and every referenced attachment before activation, creates an automatic `.hpb` backup of an installed vault, and then atomically switches to the upgraded directory.
- Dedicated messages for a failed safety upgrade and for legacy V2 vaults that require the master password instead of biometric convenience unlock.

### Changed

- Early V2 Keysets wrapped with the historical 100,000-iteration PBKDF2 parameter are accepted only for authenticated migration. The upgraded vault receives a fresh salt, a new vault identifier, associated-data-bound ciphertext, complete authenticated snapshot metadata, and a 600,000-iteration Keyset wrapper.
- Early V2 backup imports are normalized to the current format before they can replace an installed vault. V1 remains unsupported and is never migrated or overwritten.

### Fixed

- A 2.5.0 compatibility regression that reported some valid early V2 vaults as damaged even though their files had not been overwritten.
- Password hints from early V2 configurations are readable again after an incorrect master-password attempt.
- Automatic-backup retention now runs only after a migrated or imported vault is activated successfully, so a failed switch cannot remove older backup history.

## [2.5.0] - 2026-08-06

### Added

- A dedicated local search screen with ranked results across titles, accounts, password notes, and secure-note content. Password values and TOTP secrets are never indexed.
- A continuous HSV color palette for creating and editing categories.
- A release verifier and GitHub publisher that checks package metadata, source tags, signatures, offline manifests, and artifact hashes before publication.

### Changed

- Refreshed the app-wide Material 3 design with clearer typography, spacing, surfaces, icons, dialogs, navigation, list items, and circular add actions.
- Moved password/note type selection and category selection into compact menus so filters no longer require horizontal scrolling.
- Reduced the enforced master-password minimum from 12 to 6 characters. A longer unique passphrase remains strongly recommended because six characters are not sufficient evidence of password strength.
- Upgraded the build stack to Android Gradle Plugin 9.3.1, Gradle 9.5.1, and Kotlin 2.3.21, and standardized Gradle, Android Lint, and compilation on JDK 21 while retaining the Java 11 bytecode target.
- Modernized Google Tink keyset serialization and primitive access, and enabled both code minification and resource shrinking for release builds.

### Removed

- Automatic migration for V1 vaults and early V2 vaults that lack the current authenticated metadata, associated-data binding, or minimum KDF work factor. Migrate and export a fresh backup with 2.4.0 before installing 2.5.0.
- The unused legacy AES-GCM crypto manager and deprecated Tink API paths.

### Fixed

- Search-field text clipping, height changes while typing, and inconsistent result subtitle alignment.
- TOTP reveal animations replaying after switching tabs instead of only after an explicit reveal action.
- Missing show/hide-password control in the backup-import password dialog.
- Oversized or misplaced attachment remove controls and inconsistent icon treatments in list items and dialogs.

## [2.4.0] - 2026-08-04

### Added

- Apache-2.0 open-source release materials, security policy, contribution guidance, and bilingual documentation.
- Public privacy-policy deployment files and an in-app open-source licenses screen.
- CI checks for compilation, unit tests, lint, privacy-policy synchronization, and the absence of network/Firebase declarations.
- Bounded automatic backup retention, safer repository error handling, and hardened vault import validation.

### Changed

- New and re-keyed vaults use 600,000 PBKDF2-HMAC-SHA256 iterations; lower historical parameters remain only for legacy compatibility.
- Google Play and GitHub distribute the same Play App Signing identity; GitHub receives the Play-generated Universal APK from the same uploaded AAB.
- Debug builds use a distinct `.debug` application identifier and cannot overwrite the official release.
- Video thumbnail decoding and Compose side effects were hardened to reduce resource and lifecycle risks.

### Removed

- Android Internet permission.
- Firebase, Crashlytics, analytics, telemetry, and remote log reporting.

### Fixed

- Automatic backup growth is capped at five files and 1 GiB total.
- Repository failures are converted to explicit UI states.
- Several localization and backup-warning inconsistencies.
