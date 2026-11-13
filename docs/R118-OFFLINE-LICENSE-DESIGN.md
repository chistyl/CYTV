# CYTV R119 — Offline one-device-one-code licensing

## Status
- Development branch: `feature/device-bound-license-r119`
- Base: R118 offline activation implementation, branch `feature/offline-device-license-r118`
- R117 remains the prior launcher baseline. R118 remains the currently tested activation baseline until R119 passes CI and device acceptance.

## Confirmed product requirements
1. Fully offline verification; no account, server, network, or phone is required by the TV.
2. MULTI means multiple independently issued license codes can exist.
3. Each code is bound to exactly one TV device fingerprint and is rejected by other devices.
4. Clearing app data or reinstalling requires reactivation.
5. A normal reboot and in-place APK update preserve activation.
6. No master code, bypass, or shared reusable activation code.

## Device-bound issuance flow
1. On first launch, the TV creates an RSA-2048 key pair in Android Keystore if one does not already exist.
2. The private key is non-exportable and remains on that TV.
3. The activation screen displays the SHA-256 fingerprint of the device public key.
4. The operator transfers that fingerprint to an offline license-issuing computer.
5. The offline generator creates a signed payload containing product, unique license ID, device fingerprint, issue time, expiry, and version.
6. The operator transfers the resulting activation token back to that TV.
7. The TV verifies the RSA-SHA256 signature and exact fingerprint match before storing a local device-bound activation record.

## Token payload
- `v`: token format version.
- `product`: must be `CYTV`.
- `license_id`: unique issuance identifier.
- `device_fingerprint`: 64-character uppercase SHA-256 hex digest of the TV public key.
- `issued_at`: Unix epoch milliseconds.
- `expires_at`: Unix epoch milliseconds; 0 means perpetual.

## Security boundaries and limitations
- The signing private key remains offline and must never enter the repository, APK, or source ZIP.
- The APK contains only the public verification key.
- A token signed for device A fails on device B because the public-key fingerprint differs.
- Clearing app data or reinstalling removes the app's local activation record and Android Keystore identity; a newly generated identity has a different fingerprint and requires a newly issued token.
- This is offline binding, not remote device management. There is no remote revocation or online usage telemetry.
- Device fingerprint is public identifying material, not a secret. Security relies on signature verification and the non-exportable device private key.
- Hardware reset, firmware replacement, or vendor-specific Keystore invalidation can require re-issuance.

## Components
- `LicenseActivity`: displays device fingerprint and accepts the signed token.
- `LicenseManager`: creates/loads device identity, checks fingerprint, and activates.
- `LicenseVerifier`: validates token signature, product, fields, expiry, and device fingerprint.
- `DeviceIdentity`: Android Keystore key pair and public-key fingerprint.
- `LicenseStorage`: local integrity-protected activation record.
- `tools/license-generator/generate.py`: offline signer; requires explicit target fingerprint.

## Acceptance tests
- TV A displays a stable fingerprint across normal reboot.
- Code issued for TV A activates TV A offline.
- Same code is rejected on TV B.
- Altered, malformed, unsigned, wrong-product, expired, and wrong-fingerprint codes are rejected.
- Reboot and in-place APK update preserve activation.
- Clear app data and reinstall require a newly issued code for the new fingerprint.
- Back cannot bypass activation; D-pad navigation works.
- Android 9 / API 26 compatibility is retained.
