# CYTV R118 — Cloud First-Launch Activation

## Baseline

- Repository: `chistyl/CYTV`
- Base release: R117
- Base commit: `a64977bd22fba919c08ccba36c1025e0683530f9`
- Working branch: `feature/cloud-first-launch-activation-r118`
- Pull request: to be opened after implementation and CI validation; keep as Draft.

## User-approved behavior

1. A fresh installation must show an activation screen before the launcher desktop is accessible.
2. The administrator opens a mobile-friendly web page and presses one button to generate a six-digit numeric code.
3. The code expires five minutes after issuance and can be redeemed once only.
4. The TV submits the code to the cloud service. A successful redemption permanently activates that installation.
5. A previously activated installation must launch offline and must not require recurring online checks.
6. Uninstall/reinstall or factory reset requires activation again.

## Proposed architecture

- Cloud API: Cloudflare Worker.
- Persistent state: Cloudflare D1.
- Admin web page: mobile-first page served by the Worker or a static site.
- Android client: native Java code in `launcher/app`, integrated before normal Home UI initialization.
- Local activation: signed activation receipt stored with Android Keystore protection. Do not treat a plain SharedPreferences boolean as sufficient authorization.

## Security requirements

- The six-digit code is generated with a cryptographically secure random generator.
- Store only a server-side hash of the code; never log or return it after initial generation.
- Bind a code to the first installation/device challenge that redeems it; enforce expiry and one-time use atomically in D1.
- Rate-limit code generation, redemption attempts, and administrator login.
- Keep administrator credentials and signing secrets only in Cloudflare secrets; never embed them in the APK or public web assets.
- Use HTTPS only.
- The activation receipt must be verifiable offline and bound to an installation key generated on-device.
- Do not perform a recurring network check after activation.

## Important platform limitation

Android app-private data is normally removed by uninstall and factory reset, so the app will request activation again in those cases. A normal reinstall that uses Android backup/restore must not silently restore an activation flag; backup of activation state must be disabled or explicitly excluded.

A factory reset/reinstall cannot be detected reliably if a TV vendor restores app-private state or device identity from a backup. The implementation should fail closed when the local installation key/receipt pair is missing or inconsistent, and document vendor-specific restore behavior.

## Implementation sequence

1. Add activation UI and gate launcher entry before building the desktop.
2. Implement a device installation key and signed offline activation receipt.
3. Deploy Worker endpoints for administrator login, code generation, and atomic redemption.
4. Add a mobile administrator page with generate/copy/countdown/status.
5. Add automated tests for expiry, single-use, wrong-code rate limits, installation binding, and offline launch.
6. Verify fresh install, reboot, network loss, app data clear, uninstall/reinstall, and factory-reset behavior.
7. Build APK and complete source ZIP; keep R117 untouched and do not merge the R118 Draft PR before acceptance.

## Configuration required before live cloud integration

- A Cloudflare account with permission to create Workers and D1.
- A chosen public hostname/domain for the admin page and API, or approval to use a Cloudflare-provided workers.dev hostname.
- Cloudflare secrets set through the dashboard/CLI; no passwords or API tokens should be committed to Git.
