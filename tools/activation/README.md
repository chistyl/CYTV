# CYTV Offline Activation (R130)

The launcher verifies a signed token locally with RSA-SHA256. The APK contains only the public key. The private key must remain offline and must never be committed, uploaded to GitHub, or packaged into an APK/source ZIP.

## Generate a token

```bash
python tools/activation/generate_activation.py --key /secure/path/cytv-activation-private.pem --license-id STANDARD
```

The resulting token is reusable on multiple devices. Each device stores the signed token locally after first activation. Clearing app data or reinstalling removes the local activation record and requires entering the token again.

## Security boundary

This is offline activation, not DRM. A reusable offline token can be copied by anyone who obtains it, and a rooted or reverse-engineered device can patch local checks. Disabling Android backup and keeping the private signing key offline reduce casual copying and key compromise, but cannot guarantee anti-tamper protection on a fully controlled device.
