# EA-PDF readers

This edition builds **EA-PDF Viewer Experimental**. Its Full Chromium
extension has a dedicated public identity key, allowing installation alongside the
regular viewer. Load `local-install/full/chromium` with **Load unpacked** once;
future builds keep that folder and identity for **Reload**. The page title, toolbar
name and context-menu label identify the experimental viewer. The Secure edition maintains the Full reader for Chromium and Firefox.

**Full reader 1.32.0** ships with EA-PDF Secure 2.3.1.
Read local PDFs, browse email folders and save attachments. Files stay on your
computer. No Java, server or internet connection is needed by the extensions.

[Download](https://github.com/thinkbox-digital/eapdf-secure/releases/tag/v2.3.0) ·
[User guide](../docs/manual/user-manual.html#viewer) · [Privacy](../docs/site/privacy.md)

## Full reader

The Full reader includes message/folder navigation, attachment downloads, CSV/XMP
export, text search, filtering, Conversations, Portfolio view, embedded search
indexes, large-archive support and export-security verification.

## Install

**Chromium:** extract the Chromium ZIP. Open `chrome://extensions`, enable
**Developer mode**, choose **Load unpacked**, and select the folder containing
`manifest.json`. Click the extension icon to open the reader.

For development, use the permanent unpacked folders under
`browser-viewer/local-install/full/chromium`. Load that folder once. After the next local
build finishes, click **Reload** on that extension's card in `chrome://extensions`
and reopen its viewer tab. If an older installation points to an extracted ZIP,
replace it once with the permanent folder.

**Firefox:** extract the Firefox ZIP. Open `about:debugging#/runtime/this-firefox`,
choose **Load Temporary Add-on**, and select `manifest.json`. Repeat after restarting
Firefox. These local packages are unsigned.

The corresponding permanent Firefox folders end in `/firefox`. Select their
`manifest.json` once, then use **Reload** in `about:debugging` after a local build.

## Use

- Click **Open PDF** or drop a file into the reader.
- Select a message or folder on the left. Scroll or use page controls to read.
- Filter by subject, sender, recipients or date. PDF text search is separate.
- Use **View** to open an embedded PDF, image or text file. Use **Parent** to return.
- Download attachments, message PDFs, selected metadata as CSV, or original XMP.
  Other attachment types use normal browser downloads.
- In the full reader, enable **Conversations** to group related messages. Only
  links found in email metadata are confirmed; optional subject grouping is inferred.
- The full reader also offers Portfolio layouts for ordinary PDF collections.

Incomplete scans and unsupported files show a warning. A readable PDF has not
necessarily passed validation. Original files are unchanged. External actions and
embedded scripts do not run.

## Limits

See [security and limits](docs/security.md), [browser support](docs/compatibility.md),
and the [PDF.js patch guide](docs/pdfjs-patch-maintenance.md) for technical details.

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
