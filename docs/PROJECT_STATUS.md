# Project status

Code preservation and viewer build, 18 November (local only): this checkout is
retained on `experiment/c2pa-pades`. Normal development continues in the separate
`eapdf-main` clone from GitHub main. PRs #26–#33 are selected for that clone;
other local additions require a separate selection. The experiment, generated
builds, private correspondence, configuration and local signing dependencies have
a verified private backup. Nothing was pushed or published.

Full Chromium **1.22.0-beta.3** is named **EA-PDF Viewer Experimental** and has a
dedicated public identity key. Its title, toolbar and context menu use that name.
The permanent `browser-viewer/local-install/full/chromium` path is unchanged;
load it once under the new identity, then use Reload for later builds. All viewer
editions were rebuilt together; Lite identities remain separate. In an isolated
Chromium profile, the published regular viewer and experimental viewer install
together, render and navigate independently; reloading Experimental retains its
ID and leaves the regular viewer open. All 208 Full unit tests pass.

Bug fix and code improvement, 18 November (unreleased): dependency PRs #26–#33
are applied locally. CI pins checkout 7.0.1, upload-artifact 7.0.2 and
setup-python 7.1.0 by commit. The independent Python inspection environment uses
packaging 26.3, Deprecated 3.0.1, wrapt 2.5.2, jsonschema-specifications 2025.9.1
and typing_extensions 4.16.0. Refreshed provenance changes only those versions
and the requirements-file hash; all extracted records and input hashes match.

Firefox Portfolio checks now wait for enabled controls and activate them in the
same browser task, so a delayed page refit cannot discard the click. A repeated
baseline check lost a sort activation; the repaired check passes eight consecutive
runs. The full Firefox acceptance script and Chromium Portfolio suite pass,
alongside 207 viewer unit tests, 15 Python inspection tests, two inspection
contract tests, frozen-fixture verification and workflow lint/input checks.
These are CI and test changes; application versions and installable viewer builds
are unchanged. GitHub PR state has not been changed and no publication is included.

New code and bug fix, 17 November (local viewer 1.21.0-beta.3, unpublished):
Full now checks bounded offline certificate paths, complete CRLs, authorized OCSP
responses and trusted signature/document timestamps against session-only CA roots
selected by the user. Root files can be added from separate folders; fingerprints
are retained in reports. The button lists .pem/.crt/.cer/.der. Report labels use
plain language and distinguish a valid timestamp token from trust in its provider.
The report starts with guidance to use the Java validator for full archival checks.
A line-by-line report guide is in DIGITAL_SIGNATURES.md. All three viewer editions
are rebuilt together; Lite editions omit security checking.

Verification: 207 viewer unit tests pass, including negative revocation/trust
controls. The reproducible PDF.js build passes 48 local source checks and 142
upstream specs with one existing skip. Offline Chromium and Firefox verify the
real signer and DigiCert timestamp chains using explicitly selected roots and
embedded CRL/OCSP data. Full archival revision analysis remains with Java.
The exact reported PDF reproduces UNKNOWN timestamp trust with only the local
signing CA selected; adding the timestamp provider root makes all three
timestamp trust/status checks pass. The Java validator independently confirms
EA-PDF/PDF-A and requested B-LTA; C2PA ecosystem trust/time remain separately
unknown. The final packages list certificate extensions and use plain report labels.

Updated 17 November 2026 for **creator/validator 2.2.0-beta.3** and
**full/Lite readers 1.2.0-beta.3**.

## This release

- **New reader:** Lite keeps the familiar layout, folders, attachments and CSV/XMP
  export. It leaves out Conversations, Portfolio view, embedded search indexes
  and large-archive support.
- **Bug fix:** Lite can keep scanning collection messages while the user opens one.
  Released documents no longer use up its allowance for open documents.
- **Code improvement:** updated rendering, validation, email and signing libraries.
  Our required PDF/A, PDF/UA and validator patches remain.
- **Code removal:** removed the web workbench and its server dependencies.
- **Documentation:** shorter starting pages and simpler instructions. Regeneration
  instructions are near the end of the README.

[Downloads](https://github.com/thinkbox-digital/eapdf/releases/tag/v2.2.0-beta.3) ·
[Release notes](releases/2.2.0-beta.3.md) · [Code layout](ARCHITECTURE.md)

## What is supported

The creator accepts EML, MSG, PST, MBOX and Gmail. It produces the six EA-PDF
formats described in the [user guide](manual/user-manual.html#basics).
Use the standalone Java validator for normal conversion checks. Python CLI validation remains for regression tests and diagnostics.

Keep the original mailboxes. Isolated output omits sources; it does not remove
private information from the visible email. The creator no longer makes PREMIS
or finding-aid files. The reader's CSV export remains available.

## Latest update

New code and bug fix, 17 November (unreleased): **Full viewer 1.15.0-beta.3** validates export
security offline on open. Supported CMS signatures, RFC 3161 timestamp imprints
and EA-PDF C2PA claims/current-file bindings drive green/red filename states and an
enlarged, centred modal report opened only by activating the filename. Validation
updates the filename without opening a report. Yellow means unsigned or incomplete checking.
The initial implementation left signer trust and revocation unknown. The current
local build adds explicit-root offline checks; full archival policy remains with
Java. Close/reopen cancels verification and stale results. All
local editions are rebuilt at stable install paths; Lite editions omit security
runtime code. No publication is included. See [security scope](DIGITAL_SIGNATURES.md#browser-security-on-open).

Verification: 202 viewer unit tests and 47 PDF.js source checks pass. The
reproducible PDF.js rebuild passes 142 upstream specifications with one existing
OffscreenCanvas skip. Offline Chromium and Firefox checks cover CMS, signature
timestamps, B-LTA document timestamps and a renewed archive; Chromium also covers
changed/appended bytes, unsigned state, cancellation, report download and keyboard
access. Existing extraction/RTL security checks and all Lite build checks pass.

Bug fix and new code, 17 November (unreleased): the Linux creator bundle now
embeds the pinned WeasyPrint/Python runtime, native libraries and matching
standard font styles in the versioned creator JARs. A verified private cache is
extracted on first use and reused across conversions; no local Python
installation or sidecar runtime is needed. A clean, offline
Ubuntu container without Python/Pango renders successfully with Landlock and
seccomp active. The current test build targets Linux x86_64/glibc 2.39 or later.
Accessibility, embedded metadata and full-text search indexes default off; GUI/config can
enable them explicitly. See [runtime distribution](WEASYPRINT.md).
Signing settings now import an explicitly selected combined JSON into memory
without retaining its source filename. Protection choices use plain English with
technical codes in parentheses. B-LTA always enables C2PA provenance. The validator window accepts an optional
independent trust JSON; missing trust remains UNKNOWN.
Verification: 525 creator tests (three skips), 304 validator tests (one skip)
and final focused checks pass. The standalone embedded JAR converts a real EML
and a whole PST with two bounded workers, preserving source hashes. Both outputs
pass standalone Java EA-PDF/PDF/A and B-LTA checks; C2PA signature and binding
pass, while C2PA signer trust/time remain separate UNKNOWN results. The selected
settings JSON was deleted before conversion. Cache reuse, corruption repair and
private permissions pass. No production private credentials were read.

Bug fix, 17 November (unreleased): **Even More Lite 1.9.0-beta.3** follows scrolling
with the selected message in the left pane. The page-ownership map is shared with
Full. Reading position and focus stay in the PDF. Collection children retain the
folder tree; scrolling past an email PDF edge continues to the next/previous
message in that same folder. The Location disclosure is removed; known owning
subjects appear directly below filenames, matching Full. Application source is
**1,973 physical / 1,926 nonblank lines**. The permanent install path is unchanged.

Code improvement, 17 November (unreleased): **Even More Lite 1.8.0-beta.3** trims
file cards to the filename, size and shared view/download icons. Location details
show a meaningful collection folder or owning email; known attachment pages keep
their navigation action. Repeated full paths, PDF object/stream addresses and
relationship/dictionary codes are removed from the reader UI. Application source
is **1,926 physical / 1,879 nonblank lines**. Permanent install paths are retained.

Bug fix / Code improvement, 17 November (unreleased): **Even More Lite 1.7.0-beta.3**
removes misleading unavailable-page rows from collection covers. Emails remain in
their embedded PDFs under the folder tree. File totals include sources and
supporting assets. Download/view icons, file categories, subject/sender/date
labels and supported attachment previews now reuse Full reader components.
Technical PDF relationships and full storage paths are in expandable locations.
Application source measures **1,933 physical / 1,886 nonblank lines**, below the
enforced 2,000-line cap. The new local build refreshes the permanent install
folders; browser Reload retains the extension identity. No publication is included.

Bug fix and code improvement, 17 November (unreleased): the trust reader accepts
known companion signing-configuration fields without opening credentials; unknown
properties still identify the offending names. Timestamped modes default to
DigiCert unless a custom URL is given. GUI/CLI signing can read an optional bounded
UTF-8 passphrase file, with explicit prompt/environment passwords taking priority.
See [configuration layouts](../config/README.md#digital-signatures).

Code improvement, 17 November (unreleased): local reader builds now use
**1.5.0-beta.3**. `npm --prefix browser-viewer run build:local` automatically
advances the minor version, resets patch to zero and rebuilds all three editions.
Verified unpacked extensions remain at stable `browser-viewer/local-install/`
paths. Load the appropriate Chromium directory or Firefox manifest once, then
use the browser's Reload button after future builds and reopen the viewer tab.
The Even More Lite application remains at 1,690 physical source lines; this
change adds build/install tooling. No publication is included.

Code improvement, 17 November (unreleased): **Even More Lite 1.4.0-beta.3** restores
embedded asset downloads and working attachment links within three adjustable,
collapsible panes. Assets show their containing PDF/folder, known message/page
associations and expandable PDF object locations. PDFs open in the reader; other
linked assets download. Annotation IDs now resolve as PDF object references where
required. No search, filtering controls or CSV/XMP export were added.

Measured application source: **1,690 physical / 1,645 nonblank lines**, including
shared code, declarations, HTML, CSS and the extension manifest. Builds and the
component inventory enforce fewer than 2,000 physical lines. All three local
reader editions are rebuilt as 1.4.0-beta.3. Chromium extension/web and Firefox
extension checks cover all six EA-PDF profiles, exact asset bytes, page locations
and attachment/embedded links. A real-source attachment download matches its
independent SHA-256. No publication is included.

Initial prototype, 17 November (1.3.0-beta.3; superseded locally): **Even More Lite** is a local reading-only
copy of Lite. It removes search, filtering, exports and most controls while
retaining page reading, messages/folders, embedded PDF navigation, selectable text
and accessible PDF structure. Other attachments are listed only. Full and Lite
retain their existing controls and CSV export. All three local reader packages
are rebuilt as **1.3.0-beta.3**; nothing has been published.

Measured size: 1,463 nonblank source lines versus Lite's 4,943 (70.4% fewer).
Packaged application JS/CSS/HTML is 51.8 kB versus 336.2 kB; the complete web ZIP
is 5.43 MB versus 5.71 MB. The byte reduction includes minification. Rendering
assets remain packaged, so the complete ZIP reduction is 4.9%. These figures do
not measure speed or browser memory. Build checks verify omitted modules,
provenance and package hashes; Chromium extension/web and Firefox extension
checks cover ordinary PDFs and all six EA-PDF profiles. See the
[edition guide](../browser-viewer/even-more-lite/README.md).

New code, 17 November (unreleased): optional C2PA export provenance, timestamped
CMS and PAdES B-LTA use the shared PDF security coordinator inside the outer-PDF
publication transaction. Original embedded sources remain unchanged. Catalog AF
security metadata stays outside collection membership. Archival renewal publishes
a new file while retaining the original byte prefix. The Java validator reports
security separately from conformance. Full browser security-on-open now verifies
supported CMS/C2PA covered-content integrity and timestamp imprints offline, colours
the filename and opens a downloadable report. Explicit-root certificate and
revocation checks are now available; full archival policy remains with Java. Lite
editions omit security checking.
Local reader builds are updated separately as described above. See
[settings, signing order and limitations](DIGITAL_SIGNATURES.md).

Security verification: 18 profile/protection combinations plus archival renewal
pass standalone Java EA-PDF/PDF/A-4f checks. Fresh EML and PST B-LTA conversions
also pass with unchanged source hashes. Tampering, failed TSA publication, local
untrusted identity and private output permissions are covered. The full creator
run passes 510 checks (three skips); the full validator run passes 303 (one skip),
followed by 88 final focused checks including the new isolated-security regression.
C2PA signer trust remains independently unknown; test credentials/TSA are synthetic.

Code improvement, 17 November (unreleased): worker parent-death detection uses
process-exit notifications. `JobReport` owns synchronized process-diagnostic
imports, and single-message metadata no longer shares a nullable archive branch.
Intentional bounded Gmail retry delays and cross-process file-lock waits have
scoped inspection explanations. Generated upstream PDF.js bundles retain their
bytes; only the single-character-regex style inspection is excluded for them.
Guava is pinned to 33.8.0-jre for CVE-2026-118042. All 506 creator checks complete
without failures or errors, with three optional skips. Maven resolves the fixed
Guava version; the IDE dependency model still reports its earlier cached version.
The full Qodana scan was not rerun.

Bug fix, 17 November (unreleased): OpenHTMLtoPDF 1.1.114-eapdf.1 retains the four
EA-PDF patches and fixes rowspan borders across page breaks. The paired veraPDF
parser 1.31.72 / validation-model 1.31.225 update prevents trailing-data count
overflow, including reproduced false PDF/A-4f passes. FontBox 3.0.8-eapdf.1
backports cmap and recursion bounds, with corrected Type 2 depth accounting.
Creator XMP dates remain Gregorian under Thai and US locales. Tests now use the
existing canonical public PST input, without the deleted duplicate or symlink.
See [dependency repair verification](DEPENDENCY_FIXES_2026-11.md).

Code improvement, 14 November (unreleased): pinned WeasyPrint 70.0 is now the
default page renderer by user request; OpenHTMLtoPDF remains explicitly selectable.
Linux Landlock/seccomp, staged resources, job-shared process slots, Python memory
accounting and strict timeouts protect the worker. Java retains source/isolation,
EA-PDF tagging and publication ownership. Setup and limitations are in
[WeasyPrint](WEASYPRINT.md); measured comparisons are in
[renderer benchmarks](RENDERER_BENCHMARKS.md).

Default-change verification: 20 focused Java tests and five benchmark guard
tests pass. A real-source conversion with no renderer or Python-path override
uses the project-local pinned runtime and passes EA-PDF/PDF/A-4f validation;
explicit OpenHTML selection also passes. Python lookup prefers an explicit
absolute config path, then `EAPDF_WEASYPRINT_PYTHON`, then the working directory's
`.venv-weasyprint/bin/python`. Invalid or unavailable setup fails visibly.

Evaluation: the [OpenHTML removal estimate](OPENHTML_REMOVAL_ESTIMATE.md) identifies
342 directly removable nonblank first-party lines plus 567 lines of patch recipe
text. Another 310 shared dependency-bearing lines need equivalent replacements
and are excluded from savings. No OpenHTML code was removed. Two byte-exact
newsletter children were retained locally from the existing validated archives;
both pass standalone EA-PDF and PDF/A-4f checks. No PST source was reconverted or
scheduled for individual-message exports.

Verification on 14 November: all 24 font-corrected real EML/MSG/PST comparisons
pass standalone Java EA-PDF and veraPDF PDF/A-4f validation; source hashes are
unchanged. Rich synthetic edge fixtures cover wide tables, Unicode, links,
embedded font styles and both source/isolation policies. Actual bold/italic faces
are verified rather than assuming synthetic styles. The validator authenticates
nested Span/Link header ownership without weakening text or ParentTree checks.

The measured latency tradeoff remains: WeasyPrint took
1.75–2.03 times as long for single-email inputs and 3.29 times as long for the
whole PST. Sampled tree RSS was 9–22% higher for single emails and 22.5% lower for
that PST; PDFs were 0.6–11.1% smaller. These are small-corpus measurements, not a
general performance claim. Remaining semantic/accessibility recommendations need
review; the optional engine requires supported Linux guards and matching font faces.
The dated [upstream review](WEASYPRINT_UPSTREAM_ISSUES.md) records current risks,
local mitigations and deferred improvements. No version bump or publication.

Evaluation, 14 November: a follow-up
[performance analysis](RENDERER_PERFORMANCE_ANALYSIS.md) attributes the main local
WeasyPrint cost to repeated imports and font registration, with 48 profiled
worker calls. A controlled existing message-limit setting enabled two workers:
the seven-message PST fell from 13.170 to 9.422 seconds while sampled RSS rose
from 345.1 to 478.0 MiB. The setting also lowers accepted message/resource sizes;
no default or production implementation was changed. All 38 diagnostic outputs
and ten locally retained comparison samples pass EA-PDF and PDF/A-4f validation.
A source-authenticated newsletter comparison found eight source click targets
preserved by WeasyPrint versus four by OpenHTMLtoPDF, showing a fidelity
difference that conformance checks do not detect. Private samples remain local.

Bug fix, 14 November: CSS scoping preserves `:hover` and other selector states;
the pinned Weasy worker corrects inline-color precedence without changing installed
packages. Unavailable images with source alternative text use small centered placeholders
instead of narrow columns of fallback labels. Authored geometry, link targets,
Figure alternative text and original source bytes are retained. The
[footer analysis](RENDERER_PERFORMANCE_ANALYSIS.md#follow-up-newsletter-footer-diagnosis)
records the diagnosis and the explicit missing-image presentation policy.

Footer-fix verification: 46 focused Java tests and the initial 20 actual-runtime
worker/security/cascade tests pass. Both regenerated whole-PST archives and
their authenticated newsletter children pass standalone EA-PDF and PDF/A-4f
validation. The Weasy newsletter retains all eight source click targets and six
Figure descriptions; the footer link is white and all six placeholder aspect
ratios match the authored dimensions under `height:auto`. Both newsletters have
two pages. A subsequent pagination fix selects WeasyPrint's supported
`box-decoration-break: clone` for source-email cells as a normal-priority user
stylesheet. This reserves cell padding during fragmentation: the title and main
body now flow on page 1 without manual paragraph positioning. Retained author
avoidance remains respected. All 24 worker/security/cascade/pagination tests and
46 focused Java tests pass; the newly regenerated Weasy archive and newsletter
pass EA-PDF/PDF/A-4f validation with the eight link targets and six descriptions
preserved. The OpenHTML comparison remains byte-identical.

Unreleased bug fixes, 14 November: malformed inline images no longer force plain-text
fallback; derived filenames handle controls and supplementary Unicode safely. PST/MBOX
provenance metadata no longer adds local source paths. Output replacement preserves
POSIX permissions, cleanup preserves primary errors, and worker errors retain their type.
Collection children report the existing integer size limit clearly.

Code improvement: shared source-profile decisions, scoped command logging/compression,
one export timestamp per job, and removal of unused remote-response and collection paths.
Fresh document identifiers and signing behavior are retained. No version bump or publication.

Verification on 14 November: the versioned creator/GUI package build and creator
regressions pass (one desktop smoke test skipped in the headless session). A real
EML collection with accessibility enabled passes standalone Java EA-PDF/PDF-A checks;
its source hash is unchanged. Existing workspace fixture deletions were preserved;
fixture-dependent checks used an isolated temporary copy. No measured speed or memory
improvement is claimed.

Documentation improvement, 14 November: the [component inventory](SOFTWARE_COMPONENTS.md)
records file and component line counts with plain-English descriptions. It separates
creator controls, accessibility and indexing; validator rule descriptions, checks,
window and processes; and viewer controls from document, search and export work.
Full and Lite have independent totals including the shared source each uses;
Lite export/build tools are listed separately.
Refresh it before every GitHub push; the quality workflow checks for stale counts.

Bug fixes, 14 November: both readers restrict message downloads to
proven body pages unless a complete inspection establishes standalone scope.
Message page exports omit inherited archive files, metadata, annotations and
document-part links. The explicit Portfolio whole-file download remains available. Both PDF.js workers
include Mozilla's RTL ligature correction. Chromium and Firefox checks cover
partial archives, original bytes, Arabic/Latin text, search, highlights and copying.

Both readers use the supplied PNG and SVG artwork. A short [privacy policy](site/privacy.md)
explains local files, optional connections and saved data. The release includes
1280×800 screenshots of the Creator, Validator and both readers with fictional data.

## Checks and limits

Unreleased dependency integration, 14 November: all 19 dependency PRs are
included together. TypeScript 7 builds both readers; the Lite exporter and type
checks use its command-line interface. Acorn selects the shared JavaScript tests.
PyMuPDF 2.1 matched every pixel on the same six baseline pages, so the reference
images and comparison threshold are unchanged. The PDF manual and website also build.

Browser checks now wait for completed indexing, current virtual rows and the
requested Portfolio view. The association check compares complete records by
SHA-256 inside the browser, avoiding a roughly 116 MB test-protocol response.
These changes preserve the existing checks and reader layout. Dependency notices,
runtime inventories and the architecture PDF are refreshed.

Unreleased code improvement, 14 November: the creator now uses Jakarta Mail for
MIME assembly and XMPBox for metadata serialization. Outlook Message Parser is
1.16.3; its missing-date fix replaces our workaround. No dependency was added.
Production Java code fell from 15,720 to 15,539 lines, excluding comments and blank
lines. All input formats and six output profiles remain supported.

Creator checks: 434 passed and 23 optional checks were skipped. A real-input
comparison produced 48 baseline/candidate PDFs across EML, MSG, PST and MBOX.
All passed standalone Java validation; page images and preserved content matched.
The comparison allows fresh message identifiers and changed MIME framing;
the validator checks the resulting sizes and checksums. Original inputs were unchanged.
The custom archive storage remains: replacing it with stock PDFBox increased
retained heap in the storage probes. This is not a general speed or memory claim.

Release checks and library versions are recorded in the
[verification record](releases/2.2.0-beta.3-validation.md).
Earlier counts remain dated evidence in the changelog and release records.

The tested desktop environment is mainly Linux with Java 21. Reader extensions
are unsigned; Firefox installation is temporary. Browser-store distribution,
other desktop platforms, live Gmail onboarding and human accessibility review
still need separate checks. PDF/UA machine checks are not a human review.

A file opening successfully does not prove preservation or conformance.
Large-file limits are not a promise that every allowed file will fit in memory.
No speed or memory improvement is claimed for the
library update.

See [validation limits](VALIDATOR_COVERAGE.md), [security](security/HARDENING.md)
and [configuration](../config/README.md). Private development logs and email samples stay
outside GitHub and the website.
