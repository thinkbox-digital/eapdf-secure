# Browser reader development

## Build

Use the Node.js/npm versions listed in `package.json`, then run from this folder:

```sh
npm ci
npm run check
npm test
npm run build:local
```

Packages appear in `releases/` for Chromium and Firefox.
`build:local` advances the viewer minor version, resets patch to zero, rebuilds
both Full browser packages and refreshes the permanent unpacked directories in place. Each
invocation creates a new minor version. Future builds use three-part versions without a beta suffix. The install
copies are verified and are separate from temporary builds and `dist/`.
See [code layout](../docs/ARCHITECTURE.md) for maintained components.

## Tests

`npm test` runs the full unit and Chromium suites. `npm run test:firefox` covers
Firefox. The independent PDF inspection checks use Python and
`reference/requirements.txt`; run them with `npm run test:m1`.
The inspection provenance records exact package versions and source hashes. After
changing those requirements, install the complete pins in an isolated CPython 3.12
environment and run `python -B reference/fixtures.py --write`. Review the manifest
and extracted-record differences, then run `npm run test:m1` and
`npm run fixtures:check` with that environment on `PATH`. Tests never refresh
their expected records automatically.
Browser tests need the corresponding browser and driver installed. Use
`CHROMIUM_PATH`, `FIREFOX_PATH` and `GECKODRIVER_PATH` when they are outside the default locations.

For PDF.js API and fixture details, see the [inspection contract](docs/inspection-schema.md).

Build uses TypeScript and PDF.js **6.5.438**.
PDF.js upstream commit: `0a4153349dc473ba950fc17941c5ca7eda43c1de`.

## Full viewer export security

Full checks supported PDF/C2PA signatures and covered bytes automatically on open.
The filename is green for verified content integrity, red for failed checks and
yellow for unsigned or incomplete checking. Click the filename to open the larger,
centred security report and download JSON evidence. The report opens only on user
activation. Select public CA roots (.pem/.crt/.cer/.der) in the report for offline
certificate-chain, CRL/OCSP and trusted-timestamp checks. The file must contain
supported status evidence. Roots stay in the current session. The supported creator B-LTA workflow also receives historical revision and evidence checks. General PAdES profiles and archival policy still require the Java validator. See
[supported checks and limits](../docs/DIGITAL_SIGNATURES.md#browser-security-on-open).

The security report lists the independently selected trusted certificates, with
subject, issuer, SHA-256 fingerprint and validity dates. The downloaded JSON keeps
the same list. Verified labels are green; Not verified and Failed labels are red.

Security checks are grouped as C2PA, PAdES, their shared archival checks, CMS when applicable, and Other checks. Downloaded JSON records the same categories. Only the filename receives the coloured highlight; the header row keeps its normal background. Adding or clearing trusted
certificates updates the open report in place, preserving focus and expanded rows.

For the authenticated creator B-LTA profile, the security report also verifies
saved revisions, protected content, historical trust/evidence and credential
renewals. Other producers skip those additional checks. Select your public CA
certificates in the report; unsupported or missing evidence remains Not verified.
