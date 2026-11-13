# CYTV R118 — Offline device licensing design

## Status
- Branch: `feature/offline-device-license-r118`
- Base: R117, commit `a64977bd22fba919c08ccba36c1025e0683530f9`
- This document records the implementation contract. It does not claim that licensing is implemented.

## Confirmed product requirements
1. Activation is fully offline; the TV must not need an account, network connection, server, or phone.
2. One signed activation code may be reused on multiple devices (multi-device/shared code).
3. Clearing app data or reinstalling the APK removes local activation and requires activation again.
4. A normal reboot must not require activation again.
5. Existing R117 launcher behaviour remains unchanged after successful activation.

## Security and data model
- The app embeds only a public verification key. The signing private key must remain outside the repository, APK, and source ZIP.
- Activation codes contain a versioned, canonical payload and an RSA-SHA256 signature. Payload fields: product identifier, license identifier, issue time, optional expiry time, and format version.
- The signed code is device-independent by design, so the same code can activate multiple TVs. Offline multi-device codes cannot enforce a device-count limit or be remotely revoked.
- After successful verification, the app creates a local device-bound activation record using a non-exportable Android Keystore key and app-private storage.
- The local record is integrity-protected and checked on every launch. Missing, invalid, expired, or mismatched records return to activation.
- App backup remains disabled (`android:allowBackup="false"`) so activation state is not migrated as a backup.

## Startup flow
1. MainActivity performs the license gate before inflating or exposing the launcher UI.
2. If no valid local activation exists, launch the landscape activation screen and finish/hold the launcher activity.
3. The activation screen accepts a pasted or typed signed code and validates it entirely offline.
4. On success, persist the device-bound local record and continue to the launcher.
5. On cancellation or invalid input, do not expose the home screen.

## Android compatibility
- Target the existing minSdk/API 26 baseline; support Android 9 devices.
- Use Android Keystore APIs available on API 23+ and RSA/SHA-256 primitives available on the supported Android runtime.
- Keep activation UI D-pad navigable: focus order, visible focus state, OK/Enter activation, Back does not bypass the gate.
- Do not add network permission or background polling for licensing.

## Planned components
- `LicenseActivity`: offline activation UI.
- `LicenseManager`: local activation lifecycle and expiry checks.
- `LicenseVerifier`: payload parsing and public-key signature verification.
- `DeviceIdentity`: create/load the non-exportable Keystore key.
- `LicenseStorage`: app-private activation record persistence.
- `StartupGuard`: enforce the gate before launcher content is shown.
- `tools/license-generator/`: offline signing utility; private key supplied from a file outside version control.

## Acceptance tests
- Valid code activates with airplane mode enabled.
- The same valid code activates a second clean installation/device.
- Invalid, altered, malformed, wrong-product, and expired codes are rejected.
- Reboot preserves activation.
- Clear app data and reinstall both require activation again.
- Back, Home, task switching, and relaunch cannot expose the launcher before activation.
- D-pad can reach and activate the input controls on a TV remote.
- Existing R117 launcher tests and UI flows remain unchanged after activation.

## Explicit limitations
A fully offline shared code cannot prove how many devices have used it, prevent copying to additional devices, or support remote revocation. Those capabilities require an online authority and are outside this R118 design.
