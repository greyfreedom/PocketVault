# Contributing to PocketVault

Thank you for helping improve PocketVault. Because this project handles credentials and encrypted backups, privacy and data safety take priority over convenience.

## Before opening an issue

- Use public issues for ordinary bugs, feature requests, documentation, and hardening ideas.
- Follow [`SECURITY.md`](SECURITY.md) for anything that could expose vault data, bypass authentication, weaken cryptography, leak signing material, or exploit backup import.
- Use synthetic data only. Never upload a real vault, credential, TOTP secret, master password, backup, or unredacted sensitive log.

## Development setup

Requirements:

- JDK 17 toolchain; Gradle itself is supported on JDK 17–25
- Android SDK 36
- Git

Common checks:

```bash
./gradlew :app:compileGooglePlayDebugKotlin
./gradlew :app:testGooglePlayDebugUnitTest
./gradlew :app:lintGooglePlayDebug
./gradlew :app:optimizeGooglePlayReleaseResources
```

No Firebase configuration or `google-services.json` is required.

## Pull requests

- Keep changes focused and explain their security and migration impact.
- Add or update tests for behavior changes.
- Treat every vault-format change as security-sensitive. Preserve compatibility with a reviewed migration, or document and test an explicit fail-closed compatibility boundary before merging.
- Never add Internet permission, telemetry, analytics, advertising, or remote logging without an explicit project decision and corresponding privacy review.
- Add user-visible text to all seven locale directories: English, Simplified Chinese, Spanish, Hindi, Korean, Portuguese, and Vietnamese.
- Update `CHANGELOG.md`, documentation, privacy disclosures, and third-party notices when applicable.

## Licensing

By submitting a contribution, you agree that it may be distributed under the repository's Apache License 2.0. You must have the right to submit the code, text, or asset and must preserve required third-party attribution.
