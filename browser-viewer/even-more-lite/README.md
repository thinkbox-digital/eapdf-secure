# EA-PDF Even More Lite

A reading-only copy of Viewer Lite with a smaller interface and application.
Open a local PDF or drop it into the window. Use Parent, previous/next page,
page number and zoom. Close releases the document; it also cancels an opening.
The left pane shows messages, folders and collection PDFs; the centre displays
pages and the right pane lists embedded assets. Drag either divider or focus it
and use the arrow keys to resize. Messages and Files toggle the side panes.
Click a message to read its body and known assets; All files restores the complete
document inventory. Parent returns to the previous page in the containing PDF.
Scroll normally; text selection and accessible PDF structure remain available.

The download-arrow icon saves exact embedded bytes. The eye icon opens a PDF or
previews supported images/text using the Full viewer's shared preview component.
Hover titles and accessible labels identify both actions. Attachment links open
or preview supported files; other types download. Files are grouped as collection
PDFs, email sources, attachments and supporting files. The total includes all
embedded files, not just email messages. Asset rows show known owners and actual
attachment pages. Go to page
jumps to that attachment's page. Document attachments have no invented page.
The known owning subject appears directly under a filename, as in the Full reader.
Go to page remains available for a recorded attachment-page association. Broken
links are reported explicitly. Scrolling follows the active message in the left
pane without changing the reading position. At an email PDF boundary, another
scroll gesture opens the next/previous email in that same folder and selects it
in the retained folder tree. Folder ends and nested collections stop automatic
continuation.

A collection's cover can have one page while its emails live in separate child
PDFs. The tree lists those PDFs under their folders. Cover-level message metadata
without a body page is not repeated as a misleading unavailable-page row. When a
non-collection PDF genuinely lacks a body-page link, selecting the metadata row
explains that the body page is not identified in that PDF.

Search, filtering controls, CSV/XMP exports, metadata dialogs,
Conversations, Portfolio view and embedded search indexes are omitted.
Collection members use their stored filenames; there is no background scan of
child message subjects. The Full and Lite readers retain their existing features,
including independent CSV export.

Limits: 32 MiB / 2,048 pages per PDF, 16 MiB per decoded asset, 2 MiB metadata
per document and four embedded levels. Only the current document and its parents
are retained; returning to a parent releases the child. Incomplete inventories
show a warning. Application limits do not bound total browser memory.
Unreadable assets and exceeded limits are reported. Downloads retain the original
bytes and warn about conflicting declared sizes/checksums. Embedded PDF opening
requires matching declarations. The source PDF remains unchanged.
No conformance validation, external URL opening, uploads or analytics.

See INSTALL.txt for local installation. Packages are unsigned beta builds.

For a permanent unpacked installation from the main checkout, load
`browser-viewer/local-install/even-more-lite/chromium` once in
`chrome://extensions`. Future local builds refresh that folder; click **Reload**
and reopen the viewer tab. Firefox uses the `firefox/manifest.json` alongside it
and **Reload** in `about:debugging`. An old ZIP-based installation needs a one-time
switch to this folder. The web build lives in the adjacent `web` directory.

## Build

From the main repository's browser-viewer directory:

```sh
npm run build:local
```

This advances the minor version once, resets patch to zero and builds all reader
editions. It uses the Lite exporter, generic PDF.js hooks and packaging pipeline with
maintained overrides in even-more-lite/overrides. Shared fixes remain in src/;
do not edit generated exports. Browser package and source ZIPs, checksums and
provenance appear in releases/. Nothing is published by this command.
The permanent unpacked directories are refreshed after verification. Their paths
stay the same across versions so browser Reload can update the installed reader.

In a standalone source export, with the Node/npm versions in package.json:

```sh
npm ci --ignore-scripts
npm run check
npm run build
npm test
npm run test:browser
npm run test:firefox
npm run package
```

Builds need no Java or backend. Packaging uses Python's standard library.
Use the Full/Lite instructions for reproducing the shared PDF.js hook bundles.
All fonts, character maps and image/color decoders remain packaged for offline
rendering. JavaScript and CSS are minified; source maps and the welcome image
are omitted. The size report compares application bytes, source lines and full
ZIP sizes separately; PDF.js and its rendering assets dominate the download.

## Measured size

Local 1.9.0-beta.3 application source measures **1,973 physical / 1,926 nonblank
lines**, including shared modules, declarations, HTML, CSS and the extension
manifest. Both the build and component inventory reject 2,000 physical lines or
more. Tests, build tools and bundled libraries are outside this application count.
No line minification is used to measure source; package minification is separate.

Historical 1.4.0-beta.3 packages, measured 17 November 2026 before shared preview
controls were restored. Decimal kB/MB:

| Measure | Lite | Even More Lite | Reduction |
| --- | ---: | ---: | ---: |
| Application source, nonblank lines | 4,944 | 1,645 | 66.7% |
| Application JavaScript, CSS and HTML | 336.2 kB | 61.6 kB | 81.7% |
| Complete web ZIP | 5.71 MB | 5.44 MB | 4.8% |
| Unpacked web package | 9.81 MB | 7.85 MB | 20.0% |

The initial 1.3.0-beta.3 prototype measurements below are dated evidence, before
asset downloads and adjustable panes were restored (17 November 2026). Decimal kB/MB:

| Measure | Lite | Even More Lite | Reduction |
| --- | ---: | ---: | ---: |
| Application source, nonblank lines | 4,943 | 1,463 | 70.4% |
| Application JavaScript, CSS and HTML | 336.2 kB | 51.8 kB | 84.6% |
| Complete web ZIP | 5.71 MB | 5.43 MB | 4.9% |
| Unpacked web package | 9.81 MB | 7.84 MB | 20.1% |

Source totals come from the main repository's component inventory and include
shared source used by each edition. Application bytes sum viewer.js, viewer.css
and viewer.html; complete package sizes include rendering libraries, assets,
licenses and build inventories. Even More Lite minifies application and PDF.js
JavaScript, so the byte reductions include minification as well as removed code.
The Chromium and Firefox ZIPs are also about 5.43 MB each. Keeping offline font,
character-map and image-decoder coverage makes the complete ZIP much larger than
the 51.8 kB application. No speed or memory benchmark is claimed by these figures.
