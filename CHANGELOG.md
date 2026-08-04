# Changelog

All notable changes to PocketVault will be documented in this file.

## [2.4.0] - Unreleased

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
