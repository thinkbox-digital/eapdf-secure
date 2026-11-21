#!/usr/bin/env python3
"""Refresh the human-readable component inventory; --check rejects stale counts."""
import argparse
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DESTINATION = ROOT / 'docs/SOFTWARE_COMPONENTS.md'
GROUPS = []


def group(tool, title, prefix, rows):
    entries = []
    for row in rows.strip().splitlines():
        name, description = row.split('|', 1)
        entries.append((prefix + name.strip(), description.strip()))
    GROUPS.append((tool, title, entries))


group('Creator', 'Window and user controls', 'src/main/java/eapdf/', '''
EapdfGui.java|Shows input, output and conversion settings and controls a conversion run.
SigningSettings.java|Imports operator-selected signing settings and resolves local evidence paths without retaining the settings filename.
GuiRunConfiguration.java|Translates window selections into a checked run configuration.
GuiLog.java|Collects and displays conversion messages in the window.
''')
group('Creator', 'Commands and conversion processes', 'src/main/java/eapdf/', '''
EmailToPDFA.java|Reads command-line requests and coordinates source selection and conversion.
GuiConversionProcess.java|Starts and stops the separate conversion process used by the window.
ConversionSupervisor.java|Supervises bounded conversion jobs and handles worker failures.
PstProcesses.java|Coordinates separate PST conversion workers.
MboxProcesses.java|Coordinates separate MBOX conversion workers.
MonthlyFilenameArchiveToPdf.java|Groups dated source filenames into monthly PDF archives.
ConversionProgress.java|Records run progress for the window and command-line tools.
ConversionDiagnostic.java|Represents one conversion diagnostic.
ConversionDiagnostics.java|Collects conversion diagnostics and their context.
''')
group('Creator', 'Email readers and source reconstruction', 'src/main/java/eapdf/', '''
EmlReader.java|Reads MIME email files, their headers, bodies and attachments.
MsgReader.java|Reads Outlook message files and preserves native message details.
PstReader.java|Reads Outlook mailbox folders, messages and attachments.
MboxReader.java|Converts messages from an MBOX mailbox.
MboxParser.java|Locates and parses individual messages inside an MBOX file.
MboxReadInventory.java|Tracks which MBOX records were read and retained.
GmailDownloadToPdf.java|Connects to Gmail and converts selected messages and labels.
GmailResumeStore.java|Saves Gmail download progress so interrupted work can resume.
MimeMessageWriter.java|Reconstructs MIME email while retaining original identifiers and attachment bytes.
''')
group('Creator', 'PDF assembly and email appearance', 'src/main/java/eapdf/', '''
PdfMailWriter.java|Owns final PDF assembly, page and message boundaries, structure and profile decisions.
MailHtmlRenderer.java|Prepares email HTML, resolves inline content and renders it into PDF pages.
MailRenderPolicy.java|Applies rendering resource and content policies.
WeasyPrintRenderer.java|Stages trusted resources and runs the default sandboxed WeasyPrint page renderer.
EmbeddedWeasyPrintRuntime.java|Verifies and installs the bundled renderer into a private cache before execution.
WeasyFontFaces.java|Finds and verifies matching font-family style faces for the WeasyPrint renderer.
EmailStyles.java|Scopes source email CSS while preserving selector states and presentation.
ImageOrientation.java|Corrects image orientation when preparing visible content.
''')
group('Creator', 'Default page renderer worker', 'src/main/resources/eapdf/', '''
weasyprint_worker.py|Runs pinned WeasyPrint under Linux filesystem, network, process and memory restrictions.
''')

group('Creator distribution', 'Bundled renderer packaging and verification', 'scripts/', '''
package_weasyprint.py|Freezes Python, WeasyPrint, native font libraries and matching fonts into a relocatable Linux runtime.
embed_weasyprint_runtime.py|Embeds the matching renderer archive and per-file checksums in versioned creator JARs.
check_bundled_renderer.py|Verifies offline rendering in a clean container without installed Python or Pango, with kernel guards active.
''')

group('Creator', 'Accessibility and fonts', 'src/main/java/eapdf/', '''
SemanticStructure.java|Repairs semantic tags, header fields, tables and reading structure for accessible PDFs.
FontSupport.java|Selects fonts and handles character coverage for email rendering.
PdfFontMetadata.java|Adds and repairs PDF font metadata.
ArchiveFontSubsets.java|Manages font subsets across archive documents.
''')
group('Creator', 'Accessibility font integration', 'src/main/java/org/apache/pdfbox/pdmodel/', '''
EapdfFontFinalizer.java|Finalizes embedded PDFBox fonts for saved documents.
''')
group('Creator', 'Metadata and document organization', 'src/main/java/eapdf/', '''
PdfMetadataWriter.java|Writes document, message and preserved-source XMP metadata.
PdfDocumentParts.java|Builds the document-part hierarchy linking messages to pages and content.
PdfAttachmentSupport.java|Plans and embeds attachments and preserved source files.
PdfCollectionSupport.java|Describes collection members, folders and archive segments.
''')
group('Creator', 'Embedded search indexing', 'src/main/java/eapdf/', '''
FullTextSearchIndex.java|Builds the optional embedded full-text search index.
MetadataSearchIndex.java|Builds an embedded index of message metadata for archive searches.
''')
group('Creator', 'Signing and output integrity', 'src/main/java/eapdf/', '''
PdfSigning.java|Applies PDF signatures using configured signing material.
EaPdfSecurity.java|Coordinates C2PA export provenance, timestamped signing and archival renewal before publication.
OutputTransaction.java|Stages completed output and publishes it safely.
PreservationInventory.java|Records source content and checks that conversion preserved the expected material.
PdfCreationValidator.java|Checks newly created PDFs and reconciles preservation records before completion.
MetadataScanner.java|Scans generated metadata for creation-time integrity checks.
''')
group('Creator', 'Memory limits and PDF storage', 'src/main/java/eapdf/', '''
ArchiveResourcePool.java|Shares reusable PDF resources across an archive.
CompactPdfDictionary.java|Stores PDF dictionary entries with reduced bookkeeping.
CompactStreamCache.java|Provides bounded backing storage for PDF streams.
ConversionMemory.java|Tracks conversion memory allowances.
IndexedPdfLoader.java|Loads PDFs through the project object-index storage path.
ObjectKeyIndex.java|Indexes PDF object numbers and generations.
PayloadStore.java|Stores large message and attachment payloads outside ordinary object graphs.
ProcessingLimits.java|Defines processing limits for conversion work.
PstJobBudget.java|Budgets resources for PST conversion jobs.
''')
group('Creator', 'Shared settings and models', 'src/main/java/eapdf/', '''
EapdfConfig.java|Loads and checks conversion configuration.
Common.java|Provides shared email, filename, metadata and conversion helpers.
Models.java|Defines the message and attachment values passed between readers and writers.
''')
group('Creator', 'PST library integration', 'src/main/java/com/pff/', '''
EaPdfPstFile.java|Adapts PST file access for the creator.
EaPdfPstProperties.java|Exposes native PST properties needed for source preservation.
PSTTable7C.java|Provides the local PST table reader implementation.
''')

group('Creator and Java validator', 'Export security policy and verification', 'src/main/java/eapdf/security/', '''
EaPdfC2paProfile.java|Binds export metadata and preserved-file inventories without changing embedded sources or collection membership.
SecurityPolicy.java|Loads independently chosen certificate, CRL and OCSP evidence from local files.
SecurityVerification.java|Reports export integrity, trust and archival protection separately from conformance.
''')

group('Java validator', 'Window and report presentation', 'validator-desktop/src/main/java/eapdf/', '''
ValidatorGui.java|Shows EA-PDF/PDF/A conformance progress and findings without running security verification.
ValidationReportReader.java|Reads completed reports for display in the window.
''')
group('Java validator', 'Processes and execution', 'validator-desktop/src/main/java/eapdf/', '''
JavaValidatorEngine.java|Runs validation in a separate JVM and handles progress, timeout and cancellation.
ValidationWorker.java|Launches the bounded command-line validation worker.
ValidationRuntime.java|Chooses runtime budgets, process commands and temporary storage.
ValidationSession.java|Tracks work files, completion and failed or interrupted runs.
CollectionValidationPool.java|Schedules bounded validation of collection children.
JavaValidation.java|Coordinates inspection, veraPDF checks, rule dispatch, child documents and final results.
''')
group('Java security validator', 'Separate window and security worker', 'validator-desktop/src/main/java/eapdf/', '''
SecurityValidatorGui.java|Shows independent C2PA and PAdES verification, trust policy, protection level and downloadable reports.
SecurityValidatorEngine.java|Runs a bounded disposable security JVM with timeout, cancellation and scratch cleanup.
SecurityValidation.java|Verifies export security without conformance checks and records file hashes and check categories.
''')
group('Java validator', 'Executable validation rules', 'validator-desktop/src/main/java/eapdf/', '''
EaPdfChecks.java|Checks EA-PDF profile and document-level requirements.
MetadataChecks.java|Checks XMP declarations and message metadata consistency.
AttachmentChecks.java|Checks embedded files, attachment records and source preservation evidence.
DocumentPartChecks.java|Checks document-part structure and page ownership links.
LogicalStructureChecks.java|Checks tagged PDF structure and its correspondence with email content.
PageContentChecks.java|Checks evidence in visible page content and email fields.
MailboxChecks.java|Checks mailbox and address values against email requirements.
RuleApplicability.java|Decides which rules apply and why other rules do not apply.
''')
group('Java validator', 'Rule references and finding records', 'validator-desktop/src/main/java/eapdf/', '''
SpecificationReferences.java|Looks up requirement references for rule results.
ReportFindings.java|Accumulates findings and produces report summaries.
''')
group('Java validator', 'Source and content evidence', 'validator-desktop/src/main/java/eapdf/', '''
ContentOwnership.java|Determines which message owns rendered content.
MarkedFileAssociations.java|Finds associated files linked through marked page content.
MimeSourceStore.java|Reads and stores MIME source evidence for comparisons.
OutlookSource.java|Reads original Outlook message evidence.
OutlookRtf.java|Decodes Outlook RTF body evidence.
PageTextStore.java|Stores extracted page text for reuse by checks.
PdfAssociationScanner.java|Finds associated files throughout the PDF.
SourceHeaders.java|Reads and compares preserved source headers.
StructureRoles.java|Interprets tagged PDF structure roles.
UnicodeCasefold.java|Normalizes Unicode case for evidence comparisons.
''')
group('Java validator', 'Memory limits and PDF storage', 'validator-desktop/src/main/java/eapdf/', '''
BoundedGlyphCache.java|Limits cached glyph information during inspection.
BoundedPdfStreams.java|Limits decoded PDF stream data.
CompactPdfDictionary.java|Provides compact dictionary storage for loaded PDFs.
DecodedObjectStreamCache.java|Caches decoded object streams within a memory allowance.
IndexedPdfLoader.java|Loads PDF objects through indexed storage.
InspectionBudget.java|Limits inspection work and retained evidence.
ObjectKeyIndex.java|Indexes PDF objects without ordinary per-object map overhead.
StructureOwnershipBudget.java|Limits structure ownership traversal and evidence.
ValidationResourceCache.java|Reuses validation resources within the allowed budget.
''')
group('Java validator', 'Local veraPDF integration', 'validator-desktop/src/main/java/org/verapdf/', '''
as/filters/ASInFilter.java|Provides the local veraPDF stream-decoding filter implementation.
cos/COSDictionary.java|Provides the local veraPDF dictionary implementation.
gf/model/impl/pd/GFPDContentStream.java|Adapts veraPDF content-stream inspection.
parser/XrefStreamParser.java|Handles cross-reference streams with local storage safeguards.
pdfa/validation/validators/BaseValidator.java|Provides the local veraPDF validation loop and resource handling.
pdfa/validation/validators/SpillableIdSet.java|Moves large validation identifier sets to disk when needed.
pd/structure/StructureElementAccessObject.java|Adapts veraPDF access to logical structure elements.
''')
group('Validation rule descriptions and data', 'Shared rule catalog and specification profiles', '', '''
validator_rule_text.json|Stores human-readable rule descriptions, applicability, evidence requirements and specification references shared by validators.
config/verapdf/ISO-32005.xml|Defines the project veraPDF profile for PDF 2.0 structure namespace checks.
config/verapdf/ISO-32005.json|Records the custom profile identity and associated rule metadata.
config/verapdf/upstream/PDFUA-2-ISO32005.xml|Retains the upstream profile used as the structure-check reference.
validator-desktop/src/main/resources/validator/specification-references.json|Stores packaged specification reference data for Java reports.
validator-desktop/src/main/resources/validator/unicode-casefold.json|Stores Unicode case-fold mappings used in evidence comparisons.
''')

group('Python diagnostic validator', 'Entry point and shared inspection', '', '''
validate_eapdf.py|Provides the diagnostic CLI, shared evidence helpers, remaining checks and coordination of rule families.
''')
group('Python diagnostic validator', 'Executable rule families', 'eapdf_validation/', '''
pdf_rules.py|Checks PDF versions, pages, catalog entries, output intents and fonts.
xmp_rules.py|Checks XMP declarations, metadata values and namespace requirements.
embedding_rules.py|Checks embedded files, source sets and collection contents.
structure_rules.py|Checks document parts, logical structure and content-set correspondence.
message_rules.py|Compares message metadata and source email evidence.
media_rules.py|Checks source and rendered media types and content ownership.
''')
group('Python diagnostic validator', 'Reports and engine integration', '', '''
eapdf_validation/__init__.py|Marks the shared validation package.
eapdf_validation/report_model.py|Accumulates findings and decides combined report status.
eapdf_validation/reporting.py|Formats validation reports for readers.
eapdf_validation/engines.py|Runs external veraPDF and structure-profile checks.
rule_documentation.py|Formats rule conditions and requirement references in report pages.
''')
group('Python diagnostic validator', 'Worker isolation and decode limits', '', '''
validation_worker.py|Runs diagnostic validation with memory, disk and worker limits.
pdf_stream_limits.py|Bounds stream decoding and PDF inspection resources.
''')

group('Browser viewer Full', 'Window layout and controls', 'browser-viewer/src/', '''
viewer.html|Defines the reader window, toolbar and panel markup.
viewer.css|Defines the reader layout, colors and styles.
viewer-elements.ts|Collects typed references to window controls.
viewer-icons.ts|Supplies icons used by window controls.
message-tree.ts|Displays messages and folders with keyboard and virtual-list support.
files-panel.ts|Displays files and message-linked attachments.
file-category.ts|Assigns shared labels to email sources, attachments and supporting files.
message-label.ts|Builds the shared subject, sender and date labels for message rows.
metadata-dialog.ts|Displays document and message metadata.
pane-resizer.ts|Lets users resize reader panels.
password-prompt.ts|Asks for passwords when opening protected PDFs.
virtual-list.ts|Shows only visible rows in long lists.
row-heights.ts|Tracks row sizes for virtual scrolling.
''')
group('Browser viewer Full', 'Document opening and navigation', 'browser-viewer/src/', '''
viewer.ts|Coordinates the active document, selection, window events and navigation.
viewer-profile.ts|Selects the Full reader feature profile.
browser-session.ts|Owns PDF workers, open documents, caches and extracted-file lifetimes.
inspection-session.ts|Tracks inspection state and cancellation.
pdfjs-adapter.ts|Connects the reader to public PDF.js loading and document APIs.
navigation.ts|Resolves document and message navigation requests and maps pages to proven message owners.
embedded-navigation.ts|Navigates into embedded PDF documents.
linked-opening.ts|Handles linked document opening requests.
opening-destination.ts|Resolves the requested initial page or destination.
scrolling-pages.ts|Manages page rendering and scrolling through the active PDF.
structure-tree.ts|Checks structure-tree size and cycles before accessible DOM presentation.
document-kind.ts|Classifies the opened document for reader behavior.
''')
group('Browser viewer Full', 'Email and archive interpretation', 'browser-viewer/src/', '''
domain/inspection.d.ts|Defines the inspection data contract.
domain/observations.ts|Defines observed PDF facts used by interpretation.
domain/interpret.ts|Interprets PDF observations as EA-PDF messages and relationships.
domain/xmp.ts|Reads XMP metadata into interpretation values.
domain/hashes.ts|Provides hashes used to identify interpreted content.
interpret-worker.ts|Runs interpretation away from the window thread.
message-index.ts|Builds the navigable message index.
message-identifiers.ts|Normalizes and compares message identifiers.
collection-data.ts|Reads collection members and folder information.
collection-names.ts|Chooses readable collection and member names.
files-message-order.ts|Orders files according to their message relationships.
archive-stats.ts|Summarizes archive contents and counts.
retained-size.ts|Estimates retained data for resource budgeting.
source-email.ts|Locates preserved original email sources.
supplement-links.ts|Identifies and follows supplementary document links.
attachment-types.ts|Defines attachment data and relationships.
pdfjs-assets.d.ts|Declares the PDF.js asset API used by the reader.
pdfjs-document-parts.d.ts|Declares the document-part API added to PDF.js.
''')
group('Browser viewer Full', 'Search and embedded indexes', 'browser-viewer/src/', '''
search-controller.ts|Coordinates search controls, workers, filters and result lists.
document-search.ts|Searches text within the active document.
collection-search.ts|Searches messages across collection members.
metadata-search.ts|Matches message metadata against user searches.
embedded-index.ts|Loads and checks an embedded search index.
fulltext-index.ts|Queries the embedded full-text index.
page-text.ts|Extracts and prepares page text for searching.
''')
group('Browser viewer Full', 'Exports and attachment handling', 'browser-viewer/src/', '''
attachment-viewer.ts|Opens supported attachments in a dedicated viewing window.
downloads.ts|Coordinates browser downloads.
bounded-download.ts|Limits data retained while preparing downloads.
blob-hash.ts|Hashes browser file data without retaining a second full copy.
message-download.ts|Chooses proven message scope and prepares source or body-page exports.
metadata-csv.ts|Converts message metadata into independent CSV output.
metadata-export.ts|Prepares downloadable metadata, including CSV and XMP.
''')
group('Browser viewer Full', 'Full edition conversation controls', 'browser-viewer/src/', '''
conversations.ts|Displays and controls the Conversations view.
thread-preview.ts|Displays a preview of a conversation thread.
''')
group('Browser viewer Full', 'Export security validation and reporting', 'browser-viewer/src/', '''
security-controller.ts|Starts cancellable offline validation on open and displays filename states and a downloadable modal report opened through filename activation.
security-worker.ts|Runs cryptographic checks away from the reading interface.
security-validation.ts|Checks signatures, timestamps, C2PA and authenticated creator archival eligibility; foreign files skip additional revision checks.
security-archival.ts|Verifies creator revision continuity, protected content, ESS signer binding, historical trust/evidence and credential renewals.
security-cms.ts|Verifies bounded CMS signatures with included certificates, keeping certificate trust separate.
security-trust.ts|Checks bounded offline certificate paths, CRL and OCSP evidence against explicitly selected CA roots.
security-c2pa.ts|Verifies supported EA-PDF COSE claims, assertion digests and safe current-file bindings.
security-cbor.ts|Reads the bounded CBOR subset used by the supported C2PA export profile.
security-types.ts|Defines security facts, individual checks and report states.
''')
group('Browser viewer Full', 'Full edition conversation processing', 'browser-viewer/src/', '''
conversation-contract.ts|Defines the conversation feature boundary used by the window.
thread-graph.ts|Groups messages into conversation relationships.
thread-worker.ts|Builds conversation results away from the window thread.
''')
group('Browser viewer Full', 'Full edition Portfolio controls', 'browser-viewer/src/', '''
collection-view.ts|Controls the optional collection presentation feature.
portfolio.ts|Coordinates Portfolio presentation and interaction.
portfolio-view.ts|Builds Portfolio view controls and presentation.
portfolio-scatter.ts|Lays out a scatter presentation of collection items.
portfolio-splitter.ts|Controls resizable Portfolio regions.
portfolio-thumbnails.ts|Prepares Portfolio item thumbnails.
''')
group('Browser viewer Full', 'Full edition Portfolio contract', 'browser-viewer/src/', '''
collection-view-contract.ts|Defines the optional collection-view interface for the main reader.
''')
group('Browser viewer Full', 'Browser extension integration', 'browser-viewer/src/', '''
extension/background.ts|Handles extension background events and reader opening.
extension/manifest.json|Declares extension entry points, permissions and packaged resources.
''')
group('Viewer local build and install tools', 'Versioned builds and permanent unpacked installs', 'browser-viewer/scripts/', '''
build-local.mjs|Advances the viewer minor version and rebuilds the locally installable Full browser packages.
local-install.mjs|Verifies build bytes and updates permanent unpacked reader directories in place.
local-reload-smoke.mjs|Checks an actual extension reload across two versions at one fixed install path.
extension-version.mjs|Maps package versions to browser versions and advances minor versions.
''')

# Patch text is counted separately: context and removed lines are not live code.
for title, directory in (
    ('PDF.js patches for reader APIs and behavior', 'browser-viewer/vendor/pdfjs-patches'),
    ('HTML renderer patches for PDF output', 'third_party/openhtmltopdf/patches'),
):
    entries = []
    descriptions = {
        '0001-document-parts': 'Exposes document parts and their page relationships to the reader.',
        '0002-document-assets': 'Exposes embedded and associated document assets.',
        '0003-large-archives': 'Adds Full reader storage and loading changes for large archives.',
        '0004-inspection-yield': 'Lets inspection yield so the browser remains responsive.',
        '0005-zero-width-xref': 'Handles zero-width cross-reference stream fields.',
        '0006-collection-presentation': 'Exposes collection presentation information.',
        '0007-collection-navigation': 'Exposes collection membership and folder navigation.',
        '0008-metadata-extraction': 'Exposes metadata needed for reader inspection and export.',
        '0009-named-opening': 'Supports opening documents at named destinations.',
        '0010-rtl-ligatures': 'Backports corrected extraction order for right-to-left ligatures.',
        '0011-page-only-extraction': 'Adds isolated page-only extraction for scoped message downloads.',
        '0013-creator-archival': 'Exposes prefix-bounded historical creator revisions and protected-object graphs; rejects ambiguous structures and visible timestamp changes.',
        '0012-security-facts': 'Exposes bounded signature dictionaries and decoded DSS certificates/CRLs/OCSP without applying application trust policy.',
        '0001-Add-PDF-A-4-conformance-support': 'Adds PDF/A-4 rendering support.',
        '0002-Keep-inter-word-gaps-extractable-under-negative-lett': 'Preserves extractable word gaps under negative letter spacing.',
        '0003-Add-PDF-UA-2-conformance-support': 'Adds PDF/UA-2 rendering support.',
        '0004-Disable-GSUB-ligature-substitution-so-all-glyphs-map': 'Keeps glyphs mapped to text by disabling GSUB ligature substitution.',
    }
    for path in sorted((ROOT / directory).glob('*.patch')):
        entries.append((path.relative_to(ROOT).as_posix(), descriptions[path.stem]))
    GROUPS.append(('Library patch recipes', title, entries))


def counts(name):
    lines = (ROOT / name).read_bytes().splitlines()
    return len(lines), sum(bool(line.strip()) for line in lines)


def render():
    paths = [name for _, _, entries in GROUPS for name, _ in entries]
    if len(paths) != len(set(paths)):
        raise ValueError('A maintained source file is listed more than once')
    expected = set()
    for directory, suffixes in (
        ('src/main/java', {'.java'}),
        ('src/main/resources/eapdf', {'.py'}),
        ('validator-desktop/src/main/java', {'.java'}),
        ('browser-viewer/src', {'.ts', '.html', '.css', '.json'}),
        ('eapdf_validation', {'.py'}),
    ):
        expected.update(p.relative_to(ROOT).as_posix() for p in (ROOT / directory).rglob('*')
                        if p.is_file() and p.suffix in suffixes)
    missing = expected - set(paths)
    if missing:
        raise ValueError('Describe new source files before refreshing: ' + ', '.join(sorted(missing)))
    groups = GROUPS
    measured = {(tool, name): counts(name) for tool, _, entries in groups for name, _ in entries}
    text = ['# Software components and line counts', '',
            'This inventory describes the main parts of the Creator, Java Validator, diagnostic Python validator, '
            'and the experimental Full browser viewer. Each file has a plain-English description and measured line counts.', '',
            '## Totals by program and source category', '',
            '| Program or category | Files | Nonblank |',
            '| --- | ---: | ---: |']
    tools = list(dict.fromkeys(tool for tool, _, _ in groups))
    for tool in tools:
        names = [n for t, _, es in groups if t == tool for n, _ in es]
        text.append(f'| {tool} | {len(names):,} | {sum(measured[tool, n][1] for n in names):,} |')
    text += ['', 'The experimental checkout maintains the Full browser viewer for Chromium and Firefox. Rule data and library patches are listed separately.', '',
             '## Lite size estimates', '',
             'Creator and Java validator estimates below are dated 17 November 2026. Their baselines are 17,240 and 9,822 nonblank source lines respectively, each including 235 shared export-security lines. '
             'These are prospective net reductions, allowing for shared-code changes and overlapping controls; they are not implemented savings. Rule data, tests and third-party library source are outside these code counts.', '',
             '| Proposed edition | Estimated lines removed | Estimated lines remaining | Code reduction |',
             '| --- | ---: | ---: | ---: |',
             '| Creator Lite, GUI and accessibility retained | 1,200–1,400 | 15,800–16,000 | 7–8% |',
             '| Creator Lite, no GUI, accessibility retained | 2,000–2,200 | 15,000–15,200 | 12–13% |',
             '| Creator Lite, GUI retained, no accessibility | 2,500–2,900 | 14,300–14,700 | 15–17% |',
             '| Creator Lite, no GUI or accessibility | 3,300–3,700 | 13,500–13,900 | 19–22% |',
             '| Java validator, no GUI, all current checks retained | 896 | 8,926 | 9.1% |',
             '| Java validator, no GUI or export-security verification | 1,100–1,200 | 8,600–8,700 | 11–12% |',
             '| Java validator, no GUI or export-security verification, untagged PDFs only | 2,000–2,400 | 7,400–7,800 | 20–24% |', '',
             'Creator Lite would remove embedded search indexes and optional output signing/provenance. Accessibility removal would also retire semantic tagging, structure repair and PDF/UA declarations. '
             'All six EA-PDF profiles and all input readers would remain, together with PDF/A support, fonts, DParts, required XMP, attachment/source preservation, bounded processing and output-integrity checks. '
             'The existing GUI accounts for 944 lines before overlaps; accessibility removal offers about 1,250–1,500 additional lines. '
             'Dependency/compression modeling estimates a Creator JAR reduction of about 18 MB, approximately 59 MB to 41 MB (31%).', '',
             'The headless validator can remove 896 measured desktop/report-presentation lines, including specification-display helpers. A compact runtime rule catalog could retain rule identifiers, numbers and requirement strengths while leaving the full reference data in the repository. '
             'Removing export-security verification reduces resolved libraries from 81 to 37 and estimates about 18.9 MB less in the JAR, approximately 48 MB to 29 MB (40%); security would be reported as unchecked, with metadata recognition retained. '
             'The untagged-only edition could additionally remove logical-structure/ISO 32005 checks, but tagged inputs and collection children must produce unsupported/INCOMPLETE results. '
             'All six profiles, veraPDF, recursive collection validation, source comparisons, streamed reports, verdict/applicability tracking and resource safeguards remain in these estimates.', '']
    text += ['## Component totals', '', '| Program | Component | Files | Nonblank |', '| --- | --- | ---: | ---: |']
    for tool, title, entries in groups:
        text.append(f'| {tool} | {title} | {len(entries):,} | {sum(measured[tool, n][1] for n, _ in entries):,} |')
    for tool in tools:
        text += ['', f'## {tool}', '']
        for t, title, entries in groups:
            if t != tool:
                continue
            text += [f'### {title}', '', '| Source file | Nonblank | Function |',
                     '| --- | ---: | --- |']
            for name, description in entries:
                _, nonblank = measured[tool, name]
                text.append(f'| [{name}](../{name}) | {nonblank:,} | {description} |')
            text.append('')
    text += ['## Keeping this inventory current', '',
             'The generator measures maintained source files directly. '
             ''
             'Before every GitHub push, run `python3 scripts/build_component_inventory.py` and include the refreshed file in the changes being pushed. '
             'When responsibilities change, update the descriptions and group assignments in that script. '
             'New Java, viewer or Python package source files must be described before regeneration succeeds.', '',
             'Run `python3 scripts/build_component_inventory.py --check` to verify coverage and reject stale counts. '
             'GitHub conversion-quality checks run this command on pull requests and pushes to main. '
             'The output is deterministic: unchanged source and descriptions produce the same document.', '',
             'See [code ownership and boundaries](ARCHITECTURE.md) and [current project status](PROJECT_STATUS.md).', '']
    return '\n'.join(text)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true', help='fail if the inventory needs refreshing')
    args = parser.parse_args()
    result = render()
    if args.check:
        if not DESTINATION.is_file() or DESTINATION.read_text(encoding='utf-8') != result:
            parser.exit(1, 'Component inventory is stale; run python3 scripts/build_component_inventory.py\n')
        print('Component inventory counts and source coverage are current.')
    else:
        DESTINATION.write_text(result, encoding='utf-8')
        print('Updated docs/SOFTWARE_COMPONENTS.md')


if __name__ == '__main__':
    main()
