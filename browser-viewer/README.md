# EA-PDF readers

**Local Full, Lite and Even More Lite builds: 1.15.0-beta.3.**
The published Full/Lite readers remain 1.2.0-beta.3, with creator/validator 2.2.0-beta.3.
Read local PDFs, browse email folders and save attachments. Files stay on your
computer. No Java, server or internet connection is needed by the extensions.

[Download](https://github.com/thinkbox-digital/eapdf/releases/tag/v2.2.0-beta.3) ·
[User guide](../docs/manual/user-manual.html#viewer) · [Privacy](../docs/site/privacy.md)

## Choose a reader

| | Full | Lite |
| --- | --- | --- |
| Message/folder navigation, attachments, CSV and XMP export | Yes | Yes |
| PDF text search and message filtering | Yes | Yes |
| Conversations and Portfolio view | Yes | No |
| Embedded search indexes and large-archive optimizations | Yes | No |
| Export signature and C2PA integrity checks, filename status and report | Yes | No |
| PDF size/page limit | See [full-reader limits](docs/security.md) | 32 MiB / 2,048 pages per PDF |

Lite uses the same reading layout. It still scans message metadata for filtering;
this is separate from the embedded search indexes it leaves out.
The two extensions can be installed together.

**Even More Lite** has three adjustable panes: messages/folders, PDF pages and
embedded assets. It supports asset downloads, attachment links, embedded PDF
opening and known page/object locations. Its application stays under 2,000 physical
source lines. Download/view icons, file categories, message labels and supported
attachment previews use the Full viewer's shared code. Search, filtering controls
and CSV/XMP export are omitted. Full and Lite retain their CSV export.
See [Even More Lite](even-more-lite/README.md) for measured sizes and limits.

## Install

**Chromium:** extract the Chromium ZIP. Open `chrome://extensions`, enable
**Developer mode**, choose **Load unpacked**, and select the folder containing
`manifest.json`. Click the extension icon to open the reader.

For development, use the permanent unpacked folders under
`browser-viewer/local-install/`: `full/chromium`, `lite/chromium` or
`even-more-lite/chromium`. Load the desired folder once. After the next local
build finishes, click **Reload** on that extension's card in `chrome://extensions`
and reopen its viewer tab. If an older installation points to an extracted ZIP,
replace it once with the permanent folder.

**Firefox:** extract the Firefox ZIP. Open `about:debugging#/runtime/this-firefox`,
choose **Load Temporary Add-on**, and select `manifest.json`. Repeat after restarting
Firefox. These beta packages are unsigned.

The corresponding permanent Firefox folders end in `/firefox`. Select their
`manifest.json` once, then use **Reload** in `about:debugging` after a local build.

**Lite web page:** extract the web ZIP, then run:

```sh
python3 -m http.server 8765 --bind 127.0.0.1 --directory /tmp/eapdf-viewer-lite-web
```

Open `http://127.0.0.1:8765/viewer.html`. Use the directory where you extracted the
ZIP. Double-clicking `viewer.html` is not supported.

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

Lite limits decoded attachments to 16 MiB, metadata to 2 MiB, and open child PDFs
to 32 at once and four levels deep. Closing a child returns its allowance, so a
background scan can read more than 32 messages. Use the full reader for larger archives.

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

Packages appear in `releases/`. Both smaller-edition commands also make standalone source ZIPs.
`build:local` advances the viewer minor version, resets patch to zero, rebuilds
all editions and refreshes the permanent unpacked directories in place. Each
invocation creates a new minor version. Beta suffixes are retained. The install
copies are verified and are separate from temporary builds and `dist/`.
Shared fixes belong in this repository; regenerate Lite rather than editing an
exported copy. See [Lite maintenance](lite/README.md) and [code layout](../docs/ARCHITECTURE.md#maintained-lite-viewer-export).

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
supported status evidence. Roots stay in the current session. Full archival policy
and historical validation still require the Java validator with independent evidence. Lite editions omit these checks. See
[supported checks and limits](../docs/DIGITAL_SIGNATURES.md#browser-security-on-open).
