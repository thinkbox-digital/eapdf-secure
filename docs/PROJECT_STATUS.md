# Project status

**Release verification.** The [package acceptance check](../TECHNICAL_GUIDE.md#release-package-acceptance)
exercises the creator, standalone validator, and packaged readers on the same
six generated profiles. It also checks explicit collection metadata against
validated children. GitHub publication through the acceptance workflow uses the
packages and checksums from the successful job.


Secure 2.4.0-beta.2 updates the creator and validators. Browser Full is 1.35.0
and the desktop viewer is 0.9.0. Multi-file outputs expose an immutable
manifest, and Secure resolves pdf-security from its checked-in Maven repository.
The validator build uses Maven 3.9 or later.
[Release notes](releases/2.4.0-beta.2.md).

The compact Full reader gives the header actions a full-width row below 600 pixels, leaving more vertical space for the PDF.

Speed improvement and GUI update, 19 November 2026 (local secure creator and
validators **2.3.0**, Full reader **1.31.0**, unpublished): Gmail individual-message
exports now overlap serialized downloads with bounded PDF conversion and signing
workers. Actual source sizes reserve heap for pending work; resume checkpoints
remain on the caller and are recorded only after successful conversion. Failure
or cancellation stops pending workers. All six Gmail profiles benefit: archive
preparation retains selection order, and the actual largest source size also
enables the existing bounded renderers for 1m/1mi and 1c/1ci. Those profiles still
stage the full selection before rendering and final assembly/signing.
CLI arguments are unchanged. The Folder / label tooltip states that names are
case sensitive. The GUI keeps completed/total counts visible through all stages
and after finishing/stopping, and omits CPU time while retaining wall time and
the durable process diagnostics.

Measured on six real local EML sources through mocked Gmail downloads, with the
bundled WeasyPrint renderer, Accessibility enabled and optional PREMIS disabled:
one worker took 10.39 seconds; four took 5.72 seconds (1.81 times the throughput).
The six-message 1c comparison took 19.89 seconds with one worker and 10.46 seconds
with four (1.90 times the throughput). These small offline comparisons exclude
Gmail network and signing/TSA costs. All 23 regression outputs across the six
profiles pass standalone Java validation. Retained original sources match their
SHA-256 hashes; isolated outputs omit them. A live four-worker C2PA/B-LTA export
completed all 210 individual PDFs in 275 seconds with exit 0; its timing is
observational rather than a controlled speed comparison.

Verification: creator suite 537 tests (four skipped), then 21 focused final
progress/GUI/Gmail checks, 30 final archive/collection/security/Gmail checks and
ten final GUI checks; validator suite 311 (two skipped), plus both executable
packaging checks; Full viewer 214 unit checks, Chromium, Firefox and 1.30.0 to
1.31.0 Reload checks. The creator/GUI and both validator JARs use stable three-part
2.3.0 names. Full Chromium/Firefox packages and permanent unpacked installs use
1.31.0. These changes remain in the secure experiment; no main-clone transfer or
publication is included.

Bug fix and code improvement, 19 November 2026 (local experiment, unpublished):
collection child-validation details now show child summaries and relevant failed
or incomplete checks, including nested leaf evidence. Saved JSON retains every
check. The reported five-child failure display shrank from 16,144 to 260 lines.
The creator also keeps linked images and transparent inline wrappers from
introducing prohibited paragraph children inside non-grouping links; genuine
block paragraphs and annotation/ParentTree ownership are retained.

Verification: both whole-source collection profiles, 1c and 1ci, pass standalone
Java EA-PDF, PDF/A and applicable ISO 32005 validation, with all 56 children passing
in each. All 56 embedded source hashes match the original collection. The creator
suite passes 532 tests (three skipped); the validator suite passes 311 (two
skipped), with both executable packaging checks passing separately.

Bug fix, same local update: the window title and heading now include Secure.
Gmail's empty-image-description rendering error is reproduced offline and fixed
in the shared WeasyPrint boundary. Approved images with missing, empty or
whitespace-only descriptions receive the existing generic description plus a
review warning. Authored descriptions and original source/image bytes are
preserved. All six missing/blank-description combinations pass with Accessibility
on and off; 35 focused renderer, structure and GUI tests pass. Gmail account
conversion was not rerun. Creator/GUI and Validator JARs and checksums are refreshed
without a version bump or publication.

New code and code improvement, 19 November 2026 (local experiment, unpublished):
the EA-PDF Validator window now runs EA-PDF/PDF/A and applicable ISO 32005 checks
only. The separate `eapdf-security-validator-2.2.0-beta.3.jar` opens a C2PA/PAdES
window with public trust-policy selection, required protection, categorized checks,
JSON reports, file hashes and cancellation. Both apps use bounded worker JVMs.
Existing command-line security checks remain available for regeneration.

Verification: the validator package run passes 307 automated checks, with two
optional GUI checks skipped; both GUI checks pass separately. Both executable
archives pass manifest, dependency, CRC and clean-directory launch checks. All six
signed creator profiles and a renewed archive pass Java conformance/PDF/A and
required B-LTA checks, without AF_IN_NAME_TREE failures. C2PA signature and binding
checks pass; C2PA ecosystem trust/time remain UNKNOWN. Source hashes are unchanged,
and appended bytes are rejected. An older validator JAR was replaced locally.

Bug fix, 19 November 2026 (local reader 1.30.0, unpublished): removed the remaining
standalone-validator guidance sentence from the security report. Both Full browser
packages and permanent local installs are refreshed. All 214 reader unit tests,
offline Chromium security checks, Firefox acceptance and the 1.29.0 to 1.30.0
Reload check pass.
Certificate-import acceptance now waits for the updated report before checking it.

Code removal, 19 November 2026: the Linux application ZIP, checksum, ZIP-only
launcher/readme and packaging script are removed at the user's request.
Local distribution uses versioned standalone JARs with checksums; the Linux
creator JARs already contain the renderer runtime. No publication is included.

Bug fix and code improvement, 19 November 2026 (local reader 1.29.0,
unpublished): the requested overview disclaimer and certificate-prompt sentence
are removed. Security rows and downloaded JSON identify C2PA, PAdES, their shared
archival credentials, CMS when applicable, and other checks. Shared certificate
trust is labelled separately. The filename has its own inline status badge inside
a flexible layout slot, so its highlight no longer fills the header row.
Certificate selection continues to update the existing report in place.

New code, 19 November 2026 (local reader 1.28.0, unpublished): authenticated
creator B-LTA files receive additional browser archival verification. Independent
PDF.js snapshots include unsigned DSS saves; protected document graphs must stay
unchanged. The worker checks ESS signer binding, trusted signing/renewal times,
revision-specific evidence, evidence retention and authenticated C2PA renewals.
Other producers skip the additional inspection. Missing trust/evidence and
unsupported structures cannot establish archival protection. General PAdES and
algorithm-obsolescence policy remain with Java. Certificate selection reuses
structural snapshots and refreshes trust in the existing open report.

Offline Chromium verification passes for six real creator profiles and a renewed
archive with the selected public CA certificates, with no UNKNOWN or FAIL rows.
Source regression tests detect unsigned edits even if later reverted, altered
Info/streams, visible timestamp fields, duplicate keys and broken revision links.
Verification also passes 214 reader unit tests, 51 PDF.js source tests and
142 upstream specs (one canvas-dependent pending case), Chromium compatibility
and offline security regressions, Firefox real/renewed archive checks and the
1.27.0 to 1.28.0 extension reload. Both Full local installs are refreshed.
No publication or main-clone changes are included.

Code improvement, 19 November 2026 (local reader 1.27.0, unpublished): future
extension builds drop the beta suffix and use three numeric version components.
The minor version still advances and patch resets to zero on regeneration.
Legacy beta package versions remain readable so existing installations can update.
Full Chromium and Firefox packages and permanent local installs are rebuilt.
Creator/validator versions and historical published release names are unchanged.

Code removal and code improvement, 19 November 2026 (local viewer 1.26.0-beta.3,
unpublished): the C2PA experiment maintains only the Full reader for Chromium and
Firefox. Lite and Even More Lite source, build/export commands, CI job and local
packages/installations are removed. Main is unchanged. The local build continues
to advance the reader minor version and reset patch to zero on regeneration.
The security report shows Verified labels in green and Not verified or Failed
labels in red, and lists selected trusted certificates in the dialog and downloaded
JSON with subject, issuer, SHA-256 fingerprint and validity dates. The report now uses a summary, compact certificate cards and grouped checks with expandable explanations. Certificate selection and clearing recheck in place while preserving the open dialog, focus, expanded checks and visible position. Offline Chromium
verification with the two selected public CA certificates passes timestamp trust,
revocation and trusted-time checks. At that stage the browser archival/revision row remained Not verified; it is
implemented by the later creator-specific update above. The standalone Java
validator independently passed B-LTA and allowed-change checks for that document. No publication is included.

Bug fix, 19 November 2026 (local experiment, unpublished): legacy EML text
labelled ISO-8859-1 can contain Windows-1252 punctuation. These C1 bytes reached
the renderer as unprintable controls, causing missing-glyph, font-width and PDF/A
failures in message PDFs and their archives/collections. The reader now recovers
fully decodable Windows-1252 text with a TEXT_RECOVERED warning and retains the
original source bytes. Undefined Windows bytes and explicit UTF-8 controls do
not trigger this recovery. The renderer also tolerates a /proc memory-sampling
error when its subprocess finishes within the next 100 ms; live-process errors
still fail. C2PA/PAdES settings and signing order are unchanged.

Verification: 23 focused Java tests, 28 regeneration tests and eight quality
oracle tests pass. The originally failing 16-source group passes PDF/A and
security in all four archive profiles; five of six profile jobs pass full
validation. The remaining 1m count check reports INCOMPLETE for two byte-identical
inputs. At the user's request, one duplicate was removed from the input corpus,
with a local backup, and the oracle now selects the retained identical source.
A fresh 15-source 1m archive passes full Java EA-PDF/PDF/A and requested B-LTA
validation. The failing message also passes standalone 1s/1si validation.
Original retained-source hashes are unchanged. Local versioned creator/GUI JARs
and checksums are refreshed; no version bump or publication is included.

Bug fix and code improvement, 19 November 2026 (local experiment, unpublished):
renamed the experimental regeneration command to `eapdf-regenerate-experimental`.
It uses the current local signing configuration and requires C2PA plus PAdES B-LTA
on every outer PDF. Signing preflight precedes output clearing; security
verification precedes publication, including validation-warning replacements.
Java conformance validation receives the same trust policy and required B-LTA
level. Accessibility and indexes remain enabled; optional PREMIS remains disabled.
See [regeneration operations](CONVERSION_OPERATIONS.md#regenerating-local-outputs).

Verification: 28 focused regression checks pass. Two real EML sources produce
eight PDFs across all six profiles; standalone Java EA-PDF/PDF/A checks and the
requested B-LTA level pass, with C2PA signature and current-file binding verified.
Unsigned and altered PDFs are rejected by the publication check. Source hashes
are unchanged. C2PA ecosystem trust/time remain UNKNOWN. The installed main and
experimental launchers target their respective clones. No bulk regeneration or
publication is included.

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

## Main edition 2.2.0-beta.3

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
[edition guide](https://github.com/thinkbox-digital/eapdf-lite/blob/main/browser-viewer/README.md).

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

The desktop viewer ships as one versioned Java JAR per checkout. It runs
with Java 21 or later and a separate JavaFX runtime for the host platform.

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
