# Security Policy

PocketVault stores highly sensitive data. Security reports are welcome, but please do not disclose a suspected vulnerability publicly before it has been investigated.

## Supported versions

Security fixes are provided for the latest official version distributed through Google Play and this repository's GitHub Releases, together with its matching source tag. Older versions may no longer receive fixes; reproduce an issue on the latest version when it is safe to do so.

## Reporting a vulnerability

Email **richonenight@gmail.com** with the subject `PocketVault security report`.

Please include only what is necessary to reproduce and assess the issue:

- affected app version, Android version, and installation source;
- a clear description of the impact and prerequisites;
- minimal reproduction steps or a proof of concept using synthetic data;
- whether the issue affects confidentiality, integrity, availability, authentication, backup import/export, or release verification;
- a safe way to contact you for follow-up.

Do **not** attach a real vault, master password, TOTP secret, signing key, biometric material, or backup containing personal information. Redact device paths, account names, and other user data from screenshots and logs.

Reports will be acknowledged on a best-effort basis. After validation, the maintainer will coordinate a fix and disclosure timeline appropriate to the severity. Please allow a reasonable remediation period before publishing technical details.

## Security model

PocketVault is designed as a strictly local Android application:

- it does not request `android.permission.INTERNET`;
- it contains no analytics, advertising, telemetry, or crash-reporting SDK;
- the master password is not persisted;
- new and re-keyed vaults derive a wrapping key with PBKDF2-HMAC-SHA256 using 600,000 iterations;
- vault content is encrypted with Google Tink Streaming AEAD using AES-256-GCM-HKDF;
- biometric unlock is a convenience mechanism backed by Android Keystore, not a replacement for the master password;
- automatic locking, screenshot protection, authenticated encryption, import validation, and timed clipboard clearing reduce common exposure paths.

The readable `vault_v2.json` configuration contains metadata required before decryption, including the password hint, salt, KDF parameters, and encrypted keyset. Exported backups include this metadata. Password hints must not contain sensitive information.

## Security limitations

Open source and offline operation reduce risk but do not guarantee absolute security. PocketVault cannot fully protect data on a rooted or compromised device, from a malicious keyboard or accessibility service, from an attacker who knows or guesses a weak master password, or after a user exports a backup to an unsafe destination. Undiscovered implementation and dependency vulnerabilities may also exist.

The project has not undergone an independent professional security audit. Do not describe it as formally verified or absolutely secure.

## Public issues

Use public issues for non-sensitive bugs and hardening ideas only. If an issue may expose vault contents, bypass authentication, weaken cryptography, disclose signing material, or enable malicious backup processing, report it privately first.
