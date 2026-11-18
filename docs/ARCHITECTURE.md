# Code layout

The project has three parts: create PDFs, check PDFs, and read PDFs.
The browser reader runs without Java or a server.

[Architecture diagram (PDF)](EA-PDF_Software_Architecture.pdf) ·
[Component descriptions and line counts](SOFTWARE_COMPONENTS.md)

## Where to work

| Part | Main files |
| --- | --- |
| Local email readers | `src/main/java/eapdf/`: `EmlReader`, `MsgReader`, `PstReader`, `MboxReader` |
| Gmail reader | `GmailDownloadToPdf` |
| Conversion | `EmailToPDFA`, `PdfMailWriter` |
| HTML rendering | `MailHtmlRenderer`, `MailRenderPolicy` |
| Metadata, attachments, collections | `PdfMetadataWriter`, `PdfAttachmentSupport`, `PdfCollectionSupport` |
| Conversion workers and saved output | `ConversionSupervisor`, `OutputTransaction`, `PreservationInventory` |
| Creator window | `EapdfGui`, `GuiConversionProcess`, `GuiRunConfiguration` |
| Java validation | `validator-desktop/src/main/java/eapdf/`: `JavaValidation`, `ValidationSession`, check classes |
| Validator window and reports | `ValidatorGui`, `ValidationReportReader`, `ReportFindings` |
| Python validation | `validate_eapdf.py`, `eapdf_validation/` |
| Validation workers | `validation_worker.py`, `pdf_stream_limits.py` |
| Browser reader | `browser-viewer/src/`: `viewer.ts`, `browser-session.ts`, `domain/` |
| Reader panels | `message-tree.ts`, `files-panel.ts`, `search-controller.ts` |
| Full-reader extras | `collection-view.ts`, `portfolio.ts`, `conversations.ts`, `thread-preview.ts` |

## Ownership rules

- `PdfMailWriter` keeps control of the final PDF, document parts and logical structure.
  Rendering and metadata helpers do not publish files or own the final document.
- Worker imports enter `JobReport` through its synchronized diagnostic method,
  sharing the metadata budget with staging and release. Worker lifetime follows
  the supervisor's process-exit notification independently of rendering progress.
- `MailHtmlRenderer` selects the configured page renderer. WeasyPrint is the
  default and OpenHTMLtoPDF remains explicitly selectable. `WeasyPrintRenderer`
  stages approved bytes/fonts and invokes the pinned
  Python worker under Linux Landlock/seccomp. Python produces intermediate tagged
  pages only. Java retains final EA-PDF metadata, source/isolation, namespace and
  publication ownership; the worker disables HTML-authored PDF attachments.
- Header identity crosses the WeasyPrint boundary through a version-pinned tag
  bridge. `SemanticStructure` imports either renderer's Document roots and repairs
  Mail_Field roles and marked-content ownership. No engine error falls back to a
  different renderer. Job-shared file locks bound Python concurrency across JVMs;
  Python address-space/RSS allowances are separate from Java heap allowances.
- Source CSS scoping retains dynamic pseudo-classes on their original selector
  compounds. The pinned worker corrects inline-style specificity job-locally;
  installed packages are not modified. Missing images with source alternative
  text use a constant neutral SVG while retaining authored geometry, link targets
  and Figure alternative text. Blank-alt omissions retain their prior behavior.
- Pagination remains the renderer's responsibility. A normal-priority user
  stylesheet selects native `box-decoration-break: clone` for source-email cells
  so WeasyPrint reserves fragment padding and borders. No custom paragraph
  positioning or newsletter-specific page-break rules are applied; preserved
  author CSS keeps its precedence.
- Python selection honors an explicit absolute configuration path first, then
  the absolute `EAPDF_WEASYPRINT_PYTHON` environment path, then the embedded
  runtime, a sidecar runtime beside the JAR and the project-local
  `.venv-weasyprint/bin/python` under the working directory. Invalid explicit
  paths fail rather than falling through. Runtime installation is separate from
  conversion; the creator never installs Python packages automatically.
- Collection helpers call the writer to create child PDFs. They share its rules
  for attachments and source preservation.
- Command entry points own scoped console capture, the PDFBox compression default
  and process exit status. Library PDF writing does not replace global streams.
  `Common` shares the validated profile-to-source rule and worker-failure unwrapping.
- Render policy rejects external resources directly without a process-wide URL cache.
  Collection children use disk-backed storage; saved-file collection repair remains
  in place. Cleanup failures are suppressed behind an existing conversion failure.
- Validator Core Field assessment follows connected, verified structure ownership
  through nested spans and links. It checks PDF text objects without rewriting
  renderer trees or weakening source, ParentTree or correspondence checks.
- `viewer.ts` owns the active document, selection, navigation and cancellation.
  Panels use that shared state; they do not create another active selection.
- `browser-session.ts` owns PDF workers, caches and extracted-file lifetimes.
  Keep parsing behind the documented PDF.js APIs.
- Python's root modules keep the command-line and public import interfaces.
  Separate modules hold report formatting, engine adapters and rule families.

## Packaged renderer boundary

The Linux creator JAR embeds a frozen `eapdf-weasyprint-worker`, Python,
WeasyPrint, native font libraries and standard font-family styles. The creator
verifies the embedded archive and per-file checksums, extracts into a private
versioned cache under a process lock, restores executable permissions and
atomically publishes the directory. New processes verify before reuse and
replace corrupted cache copies. Platform, engine version and worker-source
digest are checked before launch. No end-user dependency installation is needed.
Explicit Python overrides, sidecar runtimes and the project venv remain
maintainer/development options.

The worker still receives only staged input/assets and enforces Landlock,
seccomp, deadlines and memory limits. Its executable and internal modules are
allowed read-only for frozen imports; this grants no access to source mail,
home directories or unrelated files. Java retains final EA-PDF assembly and
the signing/publication boundary. Embedded search indexes and Accessibility are
opt-in. Signing JSON is imported only when selected in the GUI; known values
and resolved evidence paths are retained in memory and the private run config,
without retaining or bundling the selected source JSON filename.

## Export security boundary

`EaPdfSecurity` coordinates optional C2PA, timestamped CMS and PAdES B-LTA through
`digital.thinkbox:pdf-security`. `PdfSigning` retains the existing default CMS
workflow. Security runs after root repairs and indexing, inside the existing
publication transaction. Only the outer generated EA-PDF is protected. Original
email, attachment and generated child bytes are not rewritten or separately signed.
The outer signature covers their embedded bytes without asserting source identity.

Shared `eapdf.security` classes define the EA-PDF C2PA assertion, operator-selected
offline trust evidence and read-only security results. Security files use catalog
AF associations outside the embedded-file name tree; they cannot become collection
members. Preservation exceptions recognize only the exact bounded profile metadata.

The library owns reservation and signing order. CMS covers the completed C2PA
manifest. B-LTA appends evidence, a renewed C2PA manifest and a final document
timestamp in its coordinated sequence. Renewal publishes a new destination and
retains the original byte prefix. No edits follow signing except publication checks.
Advanced security may invalidate the optional full-text fingerprint; ordinary
search remains the safe fallback. The fingerprint rules are not relaxed.

The Java validator reports export security separately from EA-PDF/PDF/A rules,
with independently selected trust anchors and offline revocation evidence. Missing
trust never changes failed integrity checks to a pass. Failed security or an unmet
requested protection level gives CLI exit 2 without changing conformance findings.

Full browser security-on-open uses a separate, cancellable cryptography worker.
PDF.js exposes bounded active signature dictionaries; the application verifies CMS,
timestamp imprints and supported C2PA bindings and presents a filename state and
downloadable modal report opened only through filename activation. Background
validation never opens the report. It never takes trust anchors from a PDF. Green reports
covered-content integrity; the filename separately qualifies selected-root
certificate checks. Bounded offline trust, revocation and TSA checks are available;
full archival policy remains with Java. Lite uses an empty security-controller adapter and omits these libraries.
See [browser security scope](DIGITAL_SIGNATURES.md#browser-security-on-open).

The independent Python inspection oracle lives in `browser-viewer/reference`.
Its dependency pins and frozen provenance are development evidence, separate from
the shipped browser libraries. Updating those pins requires an explicit provenance
refresh and review of extracted-record differences; tests never accept new
expectations automatically.

## Maintained Lite viewer export

Both editions share the message-download scope decision. Original-byte message
downloads require a declared standalone profile, complete relevant inspection,
one recognized message and ownership of every page. Otherwise only proven body
pages can be exported. An opt-in page-only PDF.js extraction mode drops inherited
files, metadata, annotations, structure and DPart back-references. Its distinct
worker action fails closed against older workers. Default upstream extraction and
the explicit Portfolio whole-file action retain their behavior.
Both patch recipes include upstream PDF.js PR #22521 as patch 0010 for RTL glyph
ordering; this correction does not introduce the Full reader's archive optimizations.

The full reader is the source for shared fixes. `npm run build:lite` selects its
shared modules and substitutes small adapters for features Lite omits. Do not edit
generated exports by hand.

The exporter asks the TypeScript compiler's command-line interface for the module
graph after applying Lite overrides, including type-only imports. Acorn selects
the shared JavaScript tests. Type-contract tests also use the compiler interface.
These tools are used during development and are not bundled into either reader.

Lite keeps the reading layout, message/folder navigation, attachments, CSV/XMP
export and normal PDF text search. It omits Conversations, Portfolio presentation,
embedded search indexes and large-archive optimizations. Its collection metadata
scan still supports filtering.

The generic PDF.js hooks expose document parts, metadata, associated files and
collection membership. They do not depend on the EA-PDF user interface. Lite keeps
upstream cross-reference storage and parsed-object caching. Its limits are in the
[reader guide](../browser-viewer/README.md).

Each export records source and file hashes. Changed hook inputs require reviewed
adaptations and a reproduced bundle. This is a local extension, not PDF.js approval.
The [archive comparison](PDFJS_LARGE_ARCHIVE_COMPARISON.md) records earlier tests;
it does not establish normal-viewer speed gains or upstream acceptance.

## Even More Lite reader

The local 1.15.0-beta.3 builds include a small reading-only edition under
`browser-viewer/even-more-lite/`. It uses the maintained Lite exporter and build
pipeline with a second override layer. The compiler selects only the modules
needed by its smaller entry point. Search, filtering controls, metadata exports,
metadata dialogs and background collection-subject scans are absent.
Full and Lite keep their independent CSV export.

The edition shares generic PDF.js hooks, EA-PDF interpretation, page ownership,
bounded scrolling, password entry, embedded navigation and accessible structure.
Its smaller document session retains the active ancestor chain and releases a
child when returning to its parent. It has no export caches or background workers
beyond PDF.js loading/rendering. A compact asset panel displays containing PDF/
proven owners and actual attachment-page locations. Known subjects appear directly
under filenames. It shares bounded byte extraction between downloads and child PDF
opening. Annotation links resolve PDF.js reference IDs; embedded document links
can also download non-PDF targets. Unknown page locations are never inferred from
message body ranges. Shared resizer/download helpers provide adjustable panes
and browser downloads without the larger FilesPanel dependency graph. Shared
icons, file categories, subject/sender/date labels and AttachmentViewer provide
the Full reader's action presentation and supported image/text previews. Preview
CSS is selected from the maintained Full stylesheet during export. Collection
metadata without an outer-PDF body page is not duplicated as an unavailable tree
row; collection members remain directly navigable.
The Full and minimal readers share the page-ownership map builder. Scrolling
updates selection without navigating back to the first body page. The minimal
reader keeps the immediate collection tree while reading children and uses the
shared folder-membership rules for next/previous email continuation at page edges.
Extension identities are separate, with no site or context-menu permissions.

Builds and inventory checks enforce fewer than 2,000 physical application-source
lines, including shared files, declarations, HTML, CSS and the extension manifest.
Tests, build tools and bundled dependencies are counted separately.

The application and PDF.js bundles are minified during packaging; pinned hook
inputs remain byte-for-byte unchanged and verified before that transformation.
All offline rendering assets and required license sources remain. Source exports,
package hashes and component totals cover this edition independently. See the
[edition guide and measured sizes](../browser-viewer/even-more-lite/README.md).

`scripts/build-local.mjs` advances the minor version and resets patch to zero
before producing each new installable local build. Full, Lite and Even More Lite
share that version. `scripts/local-install.mjs` verifies build hashes and manifest
versions before refreshing permanent directories under `browser-viewer/local-install/`.
It keeps each directory in place and removes only obsolete files from its prior
build inventory. Browser Reload therefore uses the same extension path across
versions. These install copies survive temporary-build cleanup and Full's `dist/`
rebuilds; they are ignored generated files, separate from maintained source.

## Library patches

The creator delegates MIME assembly to Jakarta Mail / Angus Mail and XMP
serialization to XMPBox. Our code still maps email fields, EA-PDF profiles,
source files and document parts. Reconstructed messages keep stable boundaries,
original attachment bytes and supplied identifiers; missing dates and identifiers
are not invented. Opaque message and multipart attachments bypass automatic
parsing. XMP serialization retains the required namespaces and FOAF layout.

Outlook Message Parser 1.16.3 supplies the missing-date fix. Native timestamp
precision and other preservation repairs remain local until equivalent upstream
behavior is verified. Keep the custom archive storage: stock PDFBox was more
memory-intensive in the measured stream-cache and large-archive loading probes.

The retained OpenHTMLtoPDF 1.1.114 renderer keeps four unchanged EA-PDF patches, recorded in `third_party/openhtmltopdf/lock.json`.
A clean rebuild must reproduce their commit and artifact hashes.

The Java validator keeps local storage, decoding, cross-reference and deferred
resource changes. New upstream interruption, progress and dictionary fixes are
included. Parser 1.31.72 and validation-model 1.31.225 are paired because the
post-EOF count API changed to `long`. Recheck each override whenever veraPDF changes.

Creator and Java validator resolve the same local FontBox 3.0.8-eapdf.1 artifact.
Its three-class backport stays in a separate, reproducible dependency artifact;
application code does not duplicate parser classes or alter validation rules.
`third_party/fontbox/lock.json` pins the official inputs and reviewed patches.

Python accepts only the existing allowed PDF/A profiles, allowing the new
capitalization of their built-in names. Custom profiles still require an exact
name, hash, rule count and matching engine versions.

## Keep changes focused

Move code when it gives a responsibility a clear owner. A shorter central file
does not necessarily mean less code, faster execution or lower memory use.
Keep public interfaces, original inputs and useful debug output intact.

See [project status](PROJECT_STATUS.md), [contribution rules](../CONTRIBUTING.md)
and the [documentation map](README.md).

The former web workbench and its deployment files are removed. The Java validator
is the normal checking tool; Python CLI code remains for regressions and diagnostics.

Rebuild the diagram with `scripts/build_architecture_diagram.py`, passing the
current creator and validator JARs. It reads versions and rule counts from source
and draws arrows downward from each tool to its libraries. Regenerate it before
every push.


## Browser offline trust boundary

Full's security worker uses independently selected, session-only CA roots and
bounded embedded DSS certificates/CRLs/OCSP responses. PDF.js extracts evidence
through its bounded stream decoder; it makes no trust decisions. The worker
validates one bounded certificate path, constraints, validity and fresh signed
revocation evidence. Delegated OCSP responders require issuer authorization and
OCSP signing usage. TSA trust additionally requires exclusive critical timestamp
usage; only a verified timestamp can supply trusted signing time. Root fingerprints
are recorded in JSON reports. Embedded certificates never become trust anchors.
No network lookups occur. Full historical evidence and archival revision semantics
remain the Java validator's responsibility. Lite editions omit this module.
