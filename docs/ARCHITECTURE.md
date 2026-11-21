# Code layout

The project has three parts: create PDFs, check PDFs, and read PDFs.
The browser reader runs without Java or a server.
Local distribution uses versioned standalone JARs with checksums. The Linux
creator runtime is embedded in its JARs; the separate Linux application ZIP and
its packaging script were removed on 19 November 2026.

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
| Separate security window | `SecurityValidatorGui`, `SecurityValidatorEngine`, `SecurityValidation` |
| Python validation | `validate_eapdf.py`, `eapdf_validation/` |
| Validation workers | `validation_worker.py`, `pdf_stream_limits.py` |
| Browser reader | `browser-viewer/src/`: `viewer.ts`, `browser-session.ts`, `domain/` |
| Reader panels | `message-tree.ts`, `files-panel.ts`, `search-controller.ts` |
| Full-reader extras | `collection-view.ts`, `portfolio.ts`, `conversations.ts`, `thread-preview.ts` |

## Ownership rules

- The conformance window runs `JavaValidation` with security verification disabled.
  The separate security window runs `SecurityValidation` in a bounded disposable JVM
  without EA-PDF/PDF/A checks. Command-line validation keeps its existing combined
  reports for regeneration. Both apps share public trust-policy/security code;
  security results do not change the conformance window's verdict.
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
- Java supplies a generic, review-required Alt for approved rendered images whose
  descriptions are blank. WeasyPrint requires this for its Figure tags even with
  optional Accessibility disabled; source image and email bytes stay unchanged.
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
- `SemanticStructure` preserves inline linked images through transparent renderer
  wrappers without adding paragraph children to non-grouping links. Genuine block
  paragraphs in grouping links retain their roles and ownership.
- `ValidationReportReader` selects child summaries and relevant failing or
  incomplete checks for the validator details pane, recursively through nested
  collections. Original JSON evidence remains intact in saved reports.
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

Gmail individual-message exports use bounded PDF worker threads inside the
supervised conversion JVM. The caller serializes Gmail downloads and resume
checkpoint writes, reserves conversion memory from actual source sizes, and
drains successful workers before recording completion. Failure or cancellation
stops outstanding workers. Archive preparation preserves selected message order;
its largest actual source size supplies the existing combined-archive and
collection renderer's memory bound. These profiles stage the full selection
before bounded parallel rendering and final outer-PDF assembly/security checks.
This performance fix remains in the secure experimental checkout; no experimental
changes have been transferred to the separate main clone.

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

This integration is retained in the local `experiment/c2pa-pades` checkout.
Its regeneration launcher is `eapdf-regenerate-experimental`; `eapdf-regenerate`
is reserved for the main clone. The experimental runner uses the current ignored
local signing configuration, requires C2PA and PAdES B-LTA, and checks credentials
before clearing outputs. A per-run Java helper verifies every outer PDF before
publication; standalone Java conformance validation uses the same trust policy.
Credentials remain at their configured locations. This workflow is confined to
the experiment and is not copied to main.
Normal development starts from GitHub main in a separate `eapdf-main` clone;
experimental code is not merged there automatically. Full Chromium uses a
dedicated manifest public key and the name **EA-PDF Viewer Experimental**, so
the experimental and main readers can be installed in one browser profile.
The experiment maintains only Full for Chromium and Firefox; both Lite editions
were removed on 19 November 2026. The Full install directories stay in place.

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
downloadable modal report opened only through filename activation. Checks carry
C2PA, PAdES, shared archival, CMS or other categories in both the UI and JSON.
The inline filename badge is separate from its flexible header layout slot. Background
validation never opens the report. It never takes trust anchors from a PDF. Green reports
covered-content integrity; the filename separately qualifies selected-root
certificate checks. Bounded offline trust, revocation and TSA checks are available;
authenticated creator profiles additionally enable historical revision inspection
and archival validation. Foreign profiles skip the extra inspection. Java remains
the general PAdES and policy validator.
See [browser security scope](DIGITAL_SIGNATURES.md#browser-security-on-open).

The independent Python inspection oracle lives in `browser-viewer/reference`.
Its dependency pins and frozen provenance are development evidence, separate from
the shipped browser libraries. Updating those pins requires an explicit provenance
refresh and review of extracted-record differences; tests never accept new
expectations automatically.

## Experimental Full viewer builds

This checkout maintains the Full reader for Chromium and Firefox. Lite and Even
More Lite source exports, packaging, CI and permanent installs were removed at
the user's request. The main clone is unchanged. Generic PDF.js library regression
fixtures remain separate from the reader editions.

`npm --prefix browser-viewer run build:local` advances the minor version and resets
patch to zero for every new local build, using three-part versions without a beta
suffix. It rebuilds
both browser packages and verifies the permanent `local-install/full/chromium`
and `local-install/full/firefox` directories in place for the browser Reload button.

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
No network lookups occur. After authenticated creator recognition, the security
worker requests independent bounded PDF.js snapshots of all saved revisions.
Prefix-bounded streams and object fetches prevent later objects from influencing
earlier snapshots. Canonical protected graphs retain pages, attachments, metadata,
Info and ordinary annotations; only validated invisible timestamps, retained DSS
and authenticated credential payloads receive allowances. Duplicate dictionaries,
repaired cross-references and unsupported layouts cannot pass. The worker checks
historical signer/TSA trust, signed ESS certificate bindings, evidence retention,
time order and C2PA replacements for their respective PDF prefixes. Snapshot facts
are cached per document across certificate selection, without caching trust
verdicts. General PAdES profiles and algorithm-obsolescence policy stay with Java.
