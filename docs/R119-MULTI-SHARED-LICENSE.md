# CYTV R119 — Multi shared offline activation codes

## Confirmed product decision
- Verification mode: fully offline.
- Code model: MULTI shared-code.
- Reuse: each valid signed code may activate an unlimited number of CYTV installations/devices.
- Data reset: clearing app data or reinstalling requires activation again.
- Normal reboot and in-place APK update preserve local activation.

## Token model
Each independently issued token has a unique `license_id` and is signed with the same offline RSA-SHA256 issuer key. The verifier accepts any token with a valid signature and valid CYTV payload; it must not whitelist a single license ID or bind the token to a device identifier.

The same token is intentionally reusable on any number of devices. A device-specific Android Keystore key protects only the local activation record and proves local possession; it is not used to count, register, or limit activations.

## Security boundaries
- Keep the RSA private key offline and outside the repository, APK, source archive, and CI.
- Ship only the public verification key in the APK.
- Reject plaintext shortcuts, malformed payloads, invalid signatures, wrong product IDs, unsupported versions, invalid timestamps, and expired tokens.
- Because verification is offline, shared tokens are bearer credentials: holders can copy and reuse them. There is no device-count enforcement, central usage ledger, or remote revocation.
- Local device credentials are not a substitute for server-side anti-sharing. They protect local activation state from simple copying/tampering.

## R119 implementation scope
1. Preserve the R118 activation gate and its lifecycle crash guard.
2. Keep the generic signed-payload verifier and offline generator.
3. Add automated coverage proving that two independently signed license IDs are both accepted by the same verifier.
4. Keep the activation state device-local and ensure the signed shared token is not stored as the device identity.
5. Do not merge into R117 or replace the current R118 real-device test baseline without explicit acceptance.

## Acceptance matrix
| Scenario | Expected |
|---|---|
| Shared code A on TV 1 | Activates |
| Same shared code A on TV 2 | Activates |
| Independently issued shared code B | Activates |
| Invalid or altered code | Rejected |
| Expired code | Rejected |
| Wrong product / unsupported version | Rejected |
| Reboot after activation | Remains activated |
| In-place APK update | Remains activated |
| Clear app data | Re-authentication required |
| Uninstall and reinstall | Re-authentication required |

## Compatibility
Maintain the existing Android API 26+ baseline, Android 9 support, offline-only verification, D-pad navigability, and the existing R117 launcher behavior after successful activation.
