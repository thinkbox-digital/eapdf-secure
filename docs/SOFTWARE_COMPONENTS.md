# Software components and line counts

This inventory describes the main parts of the Creator, Java Validator, diagnostic Python validator, and the experimental Full browser viewer. Each file has a plain-English description and measured line counts.

## Totals by program and source category

| Program or category | Files | Nonblank |
| --- | ---: | ---: |
| Creator | 63 | 17,432 |
| Creator distribution | 3 | 278 |
| Creator and Java validator | 3 | 244 |
| Java validator | 44 | 9,597 |
| Java security validator | 3 | 292 |
| Validation rule descriptions and data | 6 | 61,911 |
| Python diagnostic validator | 14 | 11,382 |
| Browser viewer Full | 80 | 6,364 |
| Viewer local build and install tools | 4 | 142 |
| Library patch recipes | 17 | 3,362 |

The experimental checkout maintains the Full browser viewer for Chromium and Firefox. Rule data and library patches are listed separately.

## Lite size estimates

Creator and Java validator estimates below are dated 17 November 2026. Their baselines are 17,240 and 9,822 nonblank source lines respectively, each including 235 shared export-security lines. These are prospective net reductions, allowing for shared-code changes and overlapping controls; they are not implemented savings. Rule data, tests and third-party library source are outside these code counts.

| Proposed edition | Estimated lines removed | Estimated lines remaining | Code reduction |
| --- | ---: | ---: | ---: |
| Creator Lite, GUI and accessibility retained | 1,200–1,400 | 15,800–16,000 | 7–8% |
| Creator Lite, no GUI, accessibility retained | 2,000–2,200 | 15,000–15,200 | 12–13% |
| Creator Lite, GUI retained, no accessibility | 2,500–2,900 | 14,300–14,700 | 15–17% |
| Creator Lite, no GUI or accessibility | 3,300–3,700 | 13,500–13,900 | 19–22% |
| Java validator, no GUI, all current checks retained | 896 | 8,926 | 9.1% |
| Java validator, no GUI or export-security verification | 1,100–1,200 | 8,600–8,700 | 11–12% |
| Java validator, no GUI or export-security verification, untagged PDFs only | 2,000–2,400 | 7,400–7,800 | 20–24% |

Creator Lite would remove embedded search indexes and optional output signing/provenance. Accessibility removal would also retire semantic tagging, structure repair and PDF/UA declarations. All six EA-PDF profiles and all input readers would remain, together with PDF/A support, fonts, DParts, required XMP, attachment/source preservation, bounded processing and output-integrity checks. The existing GUI accounts for 944 lines before overlaps; accessibility removal offers about 1,250–1,500 additional lines. Dependency/compression modeling estimates a Creator JAR reduction of about 18 MB, approximately 59 MB to 41 MB (31%).

The headless validator can remove 896 measured desktop/report-presentation lines, including specification-display helpers. A compact runtime rule catalog could retain rule identifiers, numbers and requirement strengths while leaving the full reference data in the repository. Removing export-security verification reduces resolved libraries from 81 to 37 and estimates about 18.9 MB less in the JAR, approximately 48 MB to 29 MB (40%); security would be reported as unchecked, with metadata recognition retained. The untagged-only edition could additionally remove logical-structure/ISO 32005 checks, but tagged inputs and collection children must produce unsupported/INCOMPLETE results. All six profiles, veraPDF, recursive collection validation, source comparisons, streamed reports, verdict/applicability tracking and resource safeguards remain in these estimates.

## Component totals

| Program | Component | Files | Nonblank |
| --- | --- | ---: | ---: |
| Creator | Window and user controls | 4 | 1,003 |
| Creator | Commands and conversion processes | 9 | 1,562 |
| Creator | Email readers and source reconstruction | 9 | 2,682 |
| Creator | PDF assembly and email appearance | 8 | 3,863 |
| Creator | Default page renderer worker | 1 | 479 |
| Creator distribution | Bundled renderer packaging and verification | 3 | 278 |
| Creator | Accessibility and fonts | 4 | 1,214 |
| Creator | Accessibility font integration | 1 | 11 |
| Creator | Metadata and document organization | 4 | 1,501 |
| Creator | Embedded search indexing | 2 | 448 |
| Creator | Signing and output integrity | 6 | 1,657 |
| Creator | Memory limits and PDF storage | 9 | 839 |
| Creator | Shared settings and models | 3 | 1,683 |
| Creator | PST library integration | 3 | 490 |
| Creator and Java validator | Export security policy and verification | 3 | 244 |
| Java validator | Window and report presentation | 2 | 663 |
| Java validator | Processes and execution | 6 | 1,060 |
| Java security validator | Separate window and security worker | 3 | 292 |
| Java validator | Executable validation rules | 8 | 4,511 |
| Java validator | Rule references and finding records | 2 | 139 |
| Java validator | Source and content evidence | 10 | 965 |
| Java validator | Memory limits and PDF storage | 9 | 574 |
| Java validator | Local veraPDF integration | 7 | 1,685 |
| Validation rule descriptions and data | Shared rule catalog and specification profiles | 6 | 61,911 |
| Python diagnostic validator | Entry point and shared inspection | 1 | 7,080 |
| Python diagnostic validator | Executable rule families | 6 | 3,412 |
| Python diagnostic validator | Reports and engine integration | 5 | 605 |
| Python diagnostic validator | Worker isolation and decode limits | 2 | 285 |
| Browser viewer Full | Window layout and controls | 13 | 1,021 |
| Browser viewer Full | Document opening and navigation | 12 | 1,660 |
| Browser viewer Full | Email and archive interpretation | 18 | 1,020 |
| Browser viewer Full | Search and embedded indexes | 7 | 729 |
| Browser viewer Full | Exports and attachment handling | 7 | 491 |
| Browser viewer Full | Full edition conversation controls | 2 | 122 |
| Browser viewer Full | Export security validation and reporting | 9 | 761 |
| Browser viewer Full | Full edition conversation processing | 3 | 76 |
| Browser viewer Full | Full edition Portfolio controls | 6 | 417 |
| Browser viewer Full | Full edition Portfolio contract | 1 | 21 |
| Browser viewer Full | Browser extension integration | 2 | 46 |
| Viewer local build and install tools | Versioned builds and permanent unpacked installs | 4 | 142 |
| Library patch recipes | PDF.js patches for reader APIs and behavior | 13 | 2,795 |
| Library patch recipes | HTML renderer patches for PDF output | 4 | 567 |

## Creator

### Window and user controls

| Source file | Nonblank | Function |
| --- | ---: | --- |
| [src/main/java/eapdf/EapdfGui.java](../src/main/java/eapdf/EapdfGui.java) | 875 | Shows input, output and conversion settings and controls a conversion run. |
| [src/main/java/eapdf/SigningSettings.java](../src/main/java/eapdf/SigningSettings.java) | 69 | Imports operator-selected signing settings and resolves local evidence paths without retaining the settings filename. |
| [src/main/java/eapdf/GuiRunConfiguration.java](../src/main/java/eapdf/GuiRunConfiguration.java) | 25 | Translates window selections into a checked run configuration. |
| [src/main/java/eapdf/GuiLog.java](../src/main/java/eapdf/GuiLog.java) | 34 | Collects and displays conversion messages in the window. |

### Commands and conversion processes

| Source file | Nonblank | Function |
| --- | ---: | --- |
| [src/main/java/eapdf/EmailToPDFA.java](../src/main/java/eapdf/EmailToPDFA.java) | 415 | Reads command-line requests and coordinates source selection and conversion. |
| [src/main/java/eapdf/GuiConversionProcess.java](../src/main/java/eapdf/GuiConversionProcess.java) | 68 | Starts and stops the separate conversion process used by the window. |
| [src/main/java/eapdf/ConversionSupervisor.java](../src/main/java/eapdf/ConversionSupervisor.java) | 327 | Supervises bounded conversion jobs and handles worker failures. |
| [src/main/java/eapdf/PstProcesses.java](../src/main/java/eapdf/PstProcesses.java) | 109 | Coordinates separate PST conversion workers. |
| [src/main/java/eapdf/MboxProcesses.java](../src/main/java/eapdf/MboxProcesses.java) | 167 | Coordinates separate MBOX conversion workers. |
| [src/main/java/eapdf/MonthlyFilenameArchiveToPdf.java](../src/main/java/eapdf/MonthlyFilenameArchiveToPdf.java) | 156 | Groups dated source filenames into monthly PDF archives. |
| [src/main/java/eapdf/ConversionProgress.java](../src/main/java/eapdf/ConversionProgress.java) | 251 | Records run progress for the window and command-line tools. |
| [src/main/java/eapdf/ConversionDiagnostic.java](../src/main/java/eapdf/ConversionDiagnostic.java) | 34 | Represents one conversion diagnostic. |
| [src/main/java/eapdf/ConversionDiagnostics.java](../src/main/java/eapdf/ConversionDiagnostics.java) | 35 | Collects conversion diagnostics and their context. |

### Email readers and source reconstruction

| Source file | Nonblank | Function |
| --- | ---: | --- |
| [src/main/java/eapdf/EmlReader.java](../src/main/java/eapdf/EmlReader.java) | 655 | Reads MIME email files, their headers, bodies and attachments. |
| [src/main/java/eapdf/MsgReader.java](../src/main/java/eapdf/MsgReader.java) | 577 | Reads Outlook message files and preserves native message details. |
| [src/main/java/eapdf/PstReader.java](../src/main/java/eapdf/PstReader.java) | 423 | Reads Outlook mailbox folders, messages and attachments. |
| [src/main/java/eapdf/MboxReader.java](../src/main/java/eapdf/MboxReader.java) | 86 | Converts messages from an MBOX mailbox. |
| [src/main/java/eapdf/MboxParser.java](../src/main/java/eapdf/MboxParser.java) | 173 | Locates and parses individual messages inside an MBOX file. |
| [src/main/java/eapdf/MboxReadInventory.java](../src/main/java/eapdf/MboxReadInventory.java) | 53 | Tracks which MBOX records were read and retained. |
| [src/main/java/eapdf/GmailDownloadToPdf.java](../src/main/java/eapdf/GmailDownloadToPdf.java) | 400 | Connects to Gmail and converts selected messages and labels. |
| [src/main/java/eapdf/GmailResumeStore.java](../src/main/java/eapdf/GmailResumeStore.java) | 168 | Saves Gmail download progress so interrupted work can resume. |
| [src/main/java/eapdf/MimeMessageWriter.java](../src/main/java/eapdf/MimeMessageWriter.java) | 147 | Reconstructs MIME email while retaining original identifiers and attachment bytes. |

### PDF assembly and email appearance

| Source file | Nonblank | Function |
| --- | ---: | --- |
| [src/main/java/eapdf/PdfMailWriter.java](../src/main/java/eapdf/PdfMailWriter.java) | 2,053 | Owns final PDF assembly, page and message boundaries, structure and profile decisions. |
| [src/main/java/eapdf/MailHtmlRenderer.java](../src/main/java/eapdf/MailHtmlRenderer.java) | 701 | Prepares email HTML, resolves inline content and renders it into PDF pages. |
| [src/main/java/eapdf/MailRenderPolicy.java](../src/main/java/eapdf/MailRenderPolicy.java) | 293 | Applies rendering resource and content policies. |
| [src/main/java/eapdf/WeasyPrintRenderer.java](../src/main/java/eapdf/WeasyPrintRenderer.java) | 268 | Stages trusted resources and runs the default sandboxed WeasyPrint page renderer. |
| [src/main/java/eapdf/EmbeddedWeasyPrintRuntime.java](../src/main/java/eapdf/EmbeddedWeasyPrintRuntime.java) | 142 | Verifies and installs the bundled renderer into a private cache before execution. |
| [src/main/java/eapdf/WeasyFontFaces.java](../src/main/java/eapdf/WeasyFontFaces.java) | 111 | Finds and verifies matching font-family style faces for the WeasyPrint renderer. |
| [src/main/java/eapdf/EmailStyles.java](../src/main/java/eapdf/EmailStyles.java) | 243 | Scopes source email CSS while preserving selector states and presentation. |
| [src/main/java/eapdf/ImageOrientation.java](../src/main/java/eapdf/ImageOrientation.java) | 52 | Corrects image orientation when preparing visible content. |

### Default page renderer worker

| Source file | Nonblank | Function |
| --- | ---: | --- |
| [src/main/resources/eapdf/weasyprint_worker.py](../src/main/resources/eapdf/weasyprint_worker.py) | 479 | Runs pinned WeasyPrint under Linux filesystem, network, process and memory restrictions. |

### Accessibility and fonts

| Source file | Nonblank | Function |
| --- | ---: | --- |
| [src/main/java/eapdf/SemanticStructure.java](../src/main/java/eapdf/SemanticStructure.java) | 778 | Repairs semantic tags, header fields, tables and reading structure for accessible PDFs. |
| [src/main/java/eapdf/FontSupport.java](../src/main/java/eapdf/FontSupport.java) | 323 | Selects fonts and handles character coverage for email rendering. |
| [src/main/java/eapdf/PdfFontMetadata.java](../src/main/java/eapdf/PdfFontMetadata.java) | 42 | Adds and repairs PDF font metadata. |
| [src/main/java/eapdf/ArchiveFontSubsets.java](../src/main/java/eapdf/ArchiveFontSubsets.java) | 71 | Manages font subsets across archive documents. |

### Accessibility font integration

| Source file | Nonblank | Function |
| --- | ---: | --- |
| [src/main/java/org/apache/pdfbox/pdmodel/EapdfFontFinalizer.java](../src/main/java/org/apache/pdfbox/pdmodel/EapdfFontFinalizer.java) | 11 | Finalizes embedded PDFBox fonts for saved documents. |

### Metadata and document organization

| Source file | Nonblank | Function |
| --- | ---: | --- |
| [src/main/java/eapdf/PdfMetadataWriter.java](../src/main/java/eapdf/PdfMetadataWriter.java) | 285 | Writes document, message and preserved-source XMP metadata. |
| [src/main/java/eapdf/PdfDocumentParts.java](../src/main/java/eapdf/PdfDocumentParts.java) | 124 | Builds the document-part hierarchy linking messages to pages and content. |
| [src/main/java/eapdf/PdfAttachmentSupport.java](../src/main/java/eapdf/PdfAttachmentSupport.java) | 393 | Plans and embeds attachments and preserved source files. |
| [src/main/java/eapdf/PdfCollectionSupport.java](../src/main/java/eapdf/PdfCollectionSupport.java) | 699 | Describes collection members, folders and archive segments. |

### Embedded search indexing

| Source file | Nonblank | Function |
| --- | ---: | --- |
| [src/main/java/eapdf/FullTextSearchIndex.java](../src/main/java/eapdf/FullTextSearchIndex.java) | 332 | Builds the optional embedded full-text search index. |
| [src/main/java/eapdf/MetadataSearchIndex.java](../src/main/java/eapdf/MetadataSearchIndex.java) | 116 | Builds an embedded index of message metadata for archive searches. |

### Signing and output integrity

| Source file | Nonblank | Function |
| --- | ---: | --- |
| [src/main/java/eapdf/PdfSigning.java](../src/main/java/eapdf/PdfSigning.java) | 250 | Applies PDF signatures using configured signing material. |
| [src/main/java/eapdf/EaPdfSecurity.java](../src/main/java/eapdf/EaPdfSecurity.java) | 152 | Coordinates C2PA export provenance, timestamped signing and archival renewal before publication. |
| [src/main/java/eapdf/OutputTransaction.java](../src/main/java/eapdf/OutputTransaction.java) | 212 | Stages completed output and publishes it safely. |
| [src/main/java/eapdf/PreservationInventory.java](../src/main/java/eapdf/PreservationInventory.java) | 357 | Records source content and checks that conversion preserved the expected material. |
| [src/main/java/eapdf/PdfCreationValidator.java](../src/main/java/eapdf/PdfCreationValidator.java) | 587 | Checks newly created PDFs and reconciles preservation records before completion. |
| [src/main/java/eapdf/MetadataScanner.java](../src/main/java/eapdf/MetadataScanner.java) | 99 | Scans generated metadata for creation-time integrity checks. |

### Memory limits and PDF storage

| Source file | Nonblank | Function |
| --- | ---: | --- |
| [src/main/java/eapdf/ArchiveResourcePool.java](../src/main/java/eapdf/ArchiveResourcePool.java) | 96 | Shares reusable PDF resources across an archive. |
| [src/main/java/eapdf/CompactPdfDictionary.java](../src/main/java/eapdf/CompactPdfDictionary.java) | 79 | Stores PDF dictionary entries with reduced bookkeeping. |
| [src/main/java/eapdf/CompactStreamCache.java](../src/main/java/eapdf/CompactStreamCache.java) | 179 | Provides bounded backing storage for PDF streams. |
| [src/main/java/eapdf/ConversionMemory.java](../src/main/java/eapdf/ConversionMemory.java) | 87 | Tracks conversion memory allowances. |
| [src/main/java/eapdf/IndexedPdfLoader.java](../src/main/java/eapdf/IndexedPdfLoader.java) | 90 | Loads PDFs through the project object-index storage path. |
| [src/main/java/eapdf/ObjectKeyIndex.java](../src/main/java/eapdf/ObjectKeyIndex.java) | 33 | Indexes PDF object numbers and generations. |
| [src/main/java/eapdf/PayloadStore.java](../src/main/java/eapdf/PayloadStore.java) | 99 | Stores large message and attachment payloads outside ordinary object graphs. |
| [src/main/java/eapdf/ProcessingLimits.java](../src/main/java/eapdf/ProcessingLimits.java) | 100 | Defines processing limits for conversion work. |
| [src/main/java/eapdf/PstJobBudget.java](../src/main/java/eapdf/PstJobBudget.java) | 76 | Budgets resources for PST conversion jobs. |

### Shared settings and models

| Source file | Nonblank | Function |
| --- | ---: | --- |
| [src/main/java/eapdf/EapdfConfig.java](../src/main/java/eapdf/EapdfConfig.java) | 162 | Loads and checks conversion configuration. |
| [src/main/java/eapdf/Common.java](../src/main/java/eapdf/Common.java) | 1,379 | Provides shared email, filename, metadata and conversion helpers. |
| [src/main/java/eapdf/Models.java](../src/main/java/eapdf/Models.java) | 142 | Defines the message and attachment values passed between readers and writers. |

### PST library integration

| Source file | Nonblank | Function |
| --- | ---: | --- |
| [src/main/java/com/pff/EaPdfPstFile.java](../src/main/java/com/pff/EaPdfPstFile.java) | 42 | Adapts PST file access for the creator. |
| [src/main/java/com/pff/EaPdfPstProperties.java](../src/main/java/com/pff/EaPdfPstProperties.java) | 34 | Exposes native PST properties needed for source preservation. |
| [src/main/java/com/pff/PSTTable7C.java](../src/main/java/com/pff/PSTTable7C.java) | 414 | Provides the local PST table reader implementation. |


## Creator distribution

### Bundled renderer packaging and verification

| Source file | Nonblank | Function |
| --- | ---: | --- |
| [scripts/package_weasyprint.py](../scripts/package_weasyprint.py) | 147 | Freezes Python, WeasyPrint, native font libraries and matching fonts into a relocatable Linux runtime. |
| [scripts/embed_weasyprint_runtime.py](../scripts/embed_weasyprint_runtime.py) | 72 | Embeds the matching renderer archive and per-file checksums in versioned creator JARs. |
| [scripts/check_bundled_renderer.py](../scripts/check_bundled_renderer.py) | 59 | Verifies offline rendering in a clean container without installed Python or Pango, with kernel guards active. |


## Creator and Java validator

### Export security policy and verification

| Source file | Nonblank | Function |
| --- | ---: | --- |
| [src/main/java/eapdf/security/EaPdfC2paProfile.java](../src/main/java/eapdf/security/EaPdfC2paProfile.java) | 121 | Binds export metadata and preserved-file inventories without changing embedded sources or collection membership. |
| [src/main/java/eapdf/security/SecurityPolicy.java](../src/main/java/eapdf/security/SecurityPolicy.java) | 58 | Loads independently chosen certificate, CRL and OCSP evidence from local files. |
| [src/main/java/eapdf/security/SecurityVerification.java](../src/main/java/eapdf/security/SecurityVerification.java) | 65 | Reports export integrity, trust and archival protection separately from conformance. |


## Java validator

### Window and report presentation

| Source file | Nonblank | Function |
| --- | ---: | --- |
| [validator-desktop/src/main/java/eapdf/ValidatorGui.java](../validator-desktop/src/main/java/eapdf/ValidatorGui.java) | 549 | Shows EA-PDF/PDF/A conformance progress and findings without running security verification. |
| [validator-desktop/src/main/java/eapdf/ValidationReportReader.java](../validator-desktop/src/main/java/eapdf/ValidationReportReader.java) | 114 | Reads completed reports for display in the window. |

### Processes and execution

| Source file | Nonblank | Function |
| --- | ---: | --- |
| [validator-desktop/src/main/java/eapdf/JavaValidatorEngine.java](../validator-desktop/src/main/java/eapdf/JavaValidatorEngine.java) | 103 | Runs validation in a separate JVM and handles progress, timeout and cancellation. |
| [validator-desktop/src/main/java/eapdf/ValidationWorker.java](../validator-desktop/src/main/java/eapdf/ValidationWorker.java) | 68 | Launches the bounded command-line validation worker. |
| [validator-desktop/src/main/java/eapdf/ValidationRuntime.java](../validator-desktop/src/main/java/eapdf/ValidationRuntime.java) | 140 | Chooses runtime budgets, process commands and temporary storage. |
| [validator-desktop/src/main/java/eapdf/ValidationSession.java](../validator-desktop/src/main/java/eapdf/ValidationSession.java) | 159 | Tracks work files, completion and failed or interrupted runs. |
| [validator-desktop/src/main/java/eapdf/CollectionValidationPool.java](../validator-desktop/src/main/java/eapdf/CollectionValidationPool.java) | 177 | Schedules bounded validation of collection children. |
| [validator-desktop/src/main/java/eapdf/JavaValidation.java](../validator-desktop/src/main/java/eapdf/JavaValidation.java) | 413 | Coordinates inspection, veraPDF checks, rule dispatch, child documents and final results. |

### Executable validation rules

| Source file | Nonblank | Function |
| --- | ---: | --- |
| [validator-desktop/src/main/java/eapdf/EaPdfChecks.java](../validator-desktop/src/main/java/eapdf/EaPdfChecks.java) | 987 | Checks EA-PDF profile and document-level requirements. |
| [validator-desktop/src/main/java/eapdf/MetadataChecks.java](../validator-desktop/src/main/java/eapdf/MetadataChecks.java) | 1,571 | Checks XMP declarations and message metadata consistency. |
| [validator-desktop/src/main/java/eapdf/AttachmentChecks.java](../validator-desktop/src/main/java/eapdf/AttachmentChecks.java) | 280 | Checks embedded files, attachment records and source preservation evidence. |
| [validator-desktop/src/main/java/eapdf/DocumentPartChecks.java](../validator-desktop/src/main/java/eapdf/DocumentPartChecks.java) | 209 | Checks document-part structure and page ownership links. |
| [validator-desktop/src/main/java/eapdf/LogicalStructureChecks.java](../validator-desktop/src/main/java/eapdf/LogicalStructureChecks.java) | 397 | Checks tagged PDF structure and its correspondence with email content. |
| [validator-desktop/src/main/java/eapdf/PageContentChecks.java](../validator-desktop/src/main/java/eapdf/PageContentChecks.java) | 773 | Checks evidence in visible page content and email fields. |
| [validator-desktop/src/main/java/eapdf/MailboxChecks.java](../validator-desktop/src/main/java/eapdf/MailboxChecks.java) | 81 | Checks mailbox and address values against email requirements. |
| [validator-desktop/src/main/java/eapdf/RuleApplicability.java](../validator-desktop/src/main/java/eapdf/RuleApplicability.java) | 213 | Decides which rules apply and why other rules do not apply. |

### Rule references and finding records

| Source file | Nonblank | Function |
| --- | ---: | --- |
| [validator-desktop/src/main/java/eapdf/SpecificationReferences.java](../validator-desktop/src/main/java/eapdf/SpecificationReferences.java) | 84 | Looks up requirement references for rule results. |
| [validator-desktop/src/main/java/eapdf/ReportFindings.java](../validator-desktop/src/main/java/eapdf/ReportFindings.java) | 55 | Accumulates findings and produces report summaries. |

### Source and content evidence

| Source file | Nonblank | Function |
| --- | ---: | --- |
| [validator-desktop/src/main/java/eapdf/ContentOwnership.java](../validator-desktop/src/main/java/eapdf/ContentOwnership.java) | 90 | Determines which message owns rendered content. |
| [validator-desktop/src/main/java/eapdf/MarkedFileAssociations.java](../validator-desktop/src/main/java/eapdf/MarkedFileAssociations.java) | 64 | Finds associated files linked through marked page content. |
| [validator-desktop/src/main/java/eapdf/MimeSourceStore.java](../validator-desktop/src/main/java/eapdf/MimeSourceStore.java) | 70 | Reads and stores MIME source evidence for comparisons. |
| [validator-desktop/src/main/java/eapdf/OutlookSource.java](../validator-desktop/src/main/java/eapdf/OutlookSource.java) | 291 | Reads original Outlook message evidence. |
| [validator-desktop/src/main/java/eapdf/OutlookRtf.java](../validator-desktop/src/main/java/eapdf/OutlookRtf.java) | 51 | Decodes Outlook RTF body evidence. |
| [validator-desktop/src/main/java/eapdf/PageTextStore.java](../validator-desktop/src/main/java/eapdf/PageTextStore.java) | 53 | Stores extracted page text for reuse by checks. |
| [validator-desktop/src/main/java/eapdf/PdfAssociationScanner.java](../validator-desktop/src/main/java/eapdf/PdfAssociationScanner.java) | 112 | Finds associated files throughout the PDF. |
| [validator-desktop/src/main/java/eapdf/SourceHeaders.java](../validator-desktop/src/main/java/eapdf/SourceHeaders.java) | 157 | Reads and compares preserved source headers. |
| [validator-desktop/src/main/java/eapdf/StructureRoles.java](../validator-desktop/src/main/java/eapdf/StructureRoles.java) | 53 | Interprets tagged PDF structure roles. |
| [validator-desktop/src/main/java/eapdf/UnicodeCasefold.java](../validator-desktop/src/main/java/eapdf/UnicodeCasefold.java) | 24 | Normalizes Unicode case for evidence comparisons. |

### Memory limits and PDF storage

| Source file | Nonblank | Function |
| --- | ---: | --- |
| [validator-desktop/src/main/java/eapdf/BoundedGlyphCache.java](../validator-desktop/src/main/java/eapdf/BoundedGlyphCache.java) | 27 | Limits cached glyph information during inspection. |
| [validator-desktop/src/main/java/eapdf/BoundedPdfStreams.java](../validator-desktop/src/main/java/eapdf/BoundedPdfStreams.java) | 106 | Limits decoded PDF stream data. |
| [validator-desktop/src/main/java/eapdf/CompactPdfDictionary.java](../validator-desktop/src/main/java/eapdf/CompactPdfDictionary.java) | 79 | Provides compact dictionary storage for loaded PDFs. |
| [validator-desktop/src/main/java/eapdf/DecodedObjectStreamCache.java](../validator-desktop/src/main/java/eapdf/DecodedObjectStreamCache.java) | 40 | Caches decoded object streams within a memory allowance. |
| [validator-desktop/src/main/java/eapdf/IndexedPdfLoader.java](../validator-desktop/src/main/java/eapdf/IndexedPdfLoader.java) | 94 | Loads PDF objects through indexed storage. |
| [validator-desktop/src/main/java/eapdf/InspectionBudget.java](../validator-desktop/src/main/java/eapdf/InspectionBudget.java) | 14 | Limits inspection work and retained evidence. |
| [validator-desktop/src/main/java/eapdf/ObjectKeyIndex.java](../validator-desktop/src/main/java/eapdf/ObjectKeyIndex.java) | 33 | Indexes PDF objects without ordinary per-object map overhead. |
| [validator-desktop/src/main/java/eapdf/StructureOwnershipBudget.java](../validator-desktop/src/main/java/eapdf/StructureOwnershipBudget.java) | 156 | Limits structure ownership traversal and evidence. |
| [validator-desktop/src/main/java/eapdf/ValidationResourceCache.java](../validator-desktop/src/main/java/eapdf/ValidationResourceCache.java) | 25 | Reuses validation resources within the allowed budget. |

### Local veraPDF integration

| Source file | Nonblank | Function |
| --- | ---: | --- |
| [validator-desktop/src/main/java/org/verapdf/as/filters/ASInFilter.java](../validator-desktop/src/main/java/org/verapdf/as/filters/ASInFilter.java) | 126 | Provides the local veraPDF stream-decoding filter implementation. |
| [validator-desktop/src/main/java/org/verapdf/cos/COSDictionary.java](../validator-desktop/src/main/java/org/verapdf/cos/COSDictionary.java) | 446 | Provides the local veraPDF dictionary implementation. |
| [validator-desktop/src/main/java/org/verapdf/gf/model/impl/pd/GFPDContentStream.java](../validator-desktop/src/main/java/org/verapdf/gf/model/impl/pd/GFPDContentStream.java) | 235 | Adapts veraPDF content-stream inspection. |
| [validator-desktop/src/main/java/org/verapdf/parser/XrefStreamParser.java](../validator-desktop/src/main/java/org/verapdf/parser/XrefStreamParser.java) | 218 | Handles cross-reference streams with local storage safeguards. |
| [validator-desktop/src/main/java/org/verapdf/pdfa/validation/validators/BaseValidator.java](../validator-desktop/src/main/java/org/verapdf/pdfa/validation/validators/BaseValidator.java) | 412 | Provides the local veraPDF validation loop and resource handling. |
| [validator-desktop/src/main/java/org/verapdf/pdfa/validation/validators/SpillableIdSet.java](../validator-desktop/src/main/java/org/verapdf/pdfa/validation/validators/SpillableIdSet.java) | 179 | Moves large validation identifier sets to disk when needed. |
| [validator-desktop/src/main/java/org/verapdf/pd/structure/StructureElementAccessObject.java](../validator-desktop/src/main/java/org/verapdf/pd/structure/StructureElementAccessObject.java) | 69 | Adapts veraPDF access to logical structure elements. |


## Java security validator

### Separate window and security worker

| Source file | Nonblank | Function |
| --- | ---: | --- |
| [validator-desktop/src/main/java/eapdf/SecurityValidatorGui.java](../validator-desktop/src/main/java/eapdf/SecurityValidatorGui.java) | 159 | Shows independent C2PA and PAdES verification, trust policy, protection level and downloadable reports. |
| [validator-desktop/src/main/java/eapdf/SecurityValidatorEngine.java](../validator-desktop/src/main/java/eapdf/SecurityValidatorEngine.java) | 79 | Runs a bounded disposable security JVM with timeout, cancellation and scratch cleanup. |
| [validator-desktop/src/main/java/eapdf/SecurityValidation.java](../validator-desktop/src/main/java/eapdf/SecurityValidation.java) | 54 | Verifies export security without conformance checks and records file hashes and check categories. |


## Validation rule descriptions and data

### Shared rule catalog and specification profiles

| Source file | Nonblank | Function |
| --- | ---: | --- |
| [validator_rule_text.json](../validator_rule_text.json) | 10,226 | Stores human-readable rule descriptions, applicability, evidence requirements and specification references shared by validators. |
| [config/verapdf/ISO-32005.xml](../config/verapdf/ISO-32005.xml) | 19,648 | Defines the project veraPDF profile for PDF 2.0 structure namespace checks. |
| [config/verapdf/ISO-32005.json](../config/verapdf/ISO-32005.json) | 24 | Records the custom profile identity and associated rule metadata. |
| [config/verapdf/upstream/PDFUA-2-ISO32005.xml](../config/verapdf/upstream/PDFUA-2-ISO32005.xml) | 20,786 | Retains the upstream profile used as the structure-check reference. |
| [validator-desktop/src/main/resources/validator/specification-references.json](../validator-desktop/src/main/resources/validator/specification-references.json) | 11,226 | Stores packaged specification reference data for Java reports. |
| [validator-desktop/src/main/resources/validator/unicode-casefold.json](../validator-desktop/src/main/resources/validator/unicode-casefold.json) | 1 | Stores Unicode case-fold mappings used in evidence comparisons. |


## Python diagnostic validator

### Entry point and shared inspection

| Source file | Nonblank | Function |
| --- | ---: | --- |
| [validate_eapdf.py](../validate_eapdf.py) | 7,080 | Provides the diagnostic CLI, shared evidence helpers, remaining checks and coordination of rule families. |

### Executable rule families

| Source file | Nonblank | Function |
| --- | ---: | --- |
| [eapdf_validation/pdf_rules.py](../eapdf_validation/pdf_rules.py) | 126 | Checks PDF versions, pages, catalog entries, output intents and fonts. |
| [eapdf_validation/xmp_rules.py](../eapdf_validation/xmp_rules.py) | 426 | Checks XMP declarations, metadata values and namespace requirements. |
| [eapdf_validation/embedding_rules.py](../eapdf_validation/embedding_rules.py) | 804 | Checks embedded files, source sets and collection contents. |
| [eapdf_validation/structure_rules.py](../eapdf_validation/structure_rules.py) | 1,357 | Checks document parts, logical structure and content-set correspondence. |
| [eapdf_validation/message_rules.py](../eapdf_validation/message_rules.py) | 452 | Compares message metadata and source email evidence. |
| [eapdf_validation/media_rules.py](../eapdf_validation/media_rules.py) | 247 | Checks source and rendered media types and content ownership. |

### Reports and engine integration

| Source file | Nonblank | Function |
| --- | ---: | --- |
| [eapdf_validation/__init__.py](../eapdf_validation/__init__.py) | 1 | Marks the shared validation package. |
| [eapdf_validation/report_model.py](../eapdf_validation/report_model.py) | 77 | Accumulates findings and decides combined report status. |
| [eapdf_validation/reporting.py](../eapdf_validation/reporting.py) | 209 | Formats validation reports for readers. |
| [eapdf_validation/engines.py](../eapdf_validation/engines.py) | 291 | Runs external veraPDF and structure-profile checks. |
| [rule_documentation.py](../rule_documentation.py) | 27 | Formats rule conditions and requirement references in report pages. |

### Worker isolation and decode limits

| Source file | Nonblank | Function |
| --- | ---: | --- |
| [validation_worker.py](../validation_worker.py) | 142 | Runs diagnostic validation with memory, disk and worker limits. |
| [pdf_stream_limits.py](../pdf_stream_limits.py) | 143 | Bounds stream decoding and PDF inspection resources. |


## Browser viewer Full

### Window layout and controls

| Source file | Nonblank | Function |
| --- | ---: | --- |
| [browser-viewer/src/viewer.html](../browser-viewer/src/viewer.html) | 88 | Defines the reader window, toolbar and panel markup. |
| [browser-viewer/src/viewer.css](../browser-viewer/src/viewer.css) | 423 | Defines the reader layout, colors and styles. |
| [browser-viewer/src/viewer-elements.ts](../browser-viewer/src/viewer-elements.ts) | 5 | Collects typed references to window controls. |
| [browser-viewer/src/viewer-icons.ts](../browser-viewer/src/viewer-icons.ts) | 7 | Supplies icons used by window controls. |
| [browser-viewer/src/message-tree.ts](../browser-viewer/src/message-tree.ts) | 171 | Displays messages and folders with keyboard and virtual-list support. |
| [browser-viewer/src/files-panel.ts](../browser-viewer/src/files-panel.ts) | 125 | Displays files and message-linked attachments. |
| [browser-viewer/src/file-category.ts](../browser-viewer/src/file-category.ts) | 9 | Assigns shared labels to email sources, attachments and supporting files. |
| [browser-viewer/src/message-label.ts](../browser-viewer/src/message-label.ts) | 7 | Builds the shared subject, sender and date labels for message rows. |
| [browser-viewer/src/metadata-dialog.ts](../browser-viewer/src/metadata-dialog.ts) | 88 | Displays document and message metadata. |
| [browser-viewer/src/pane-resizer.ts](../browser-viewer/src/pane-resizer.ts) | 23 | Lets users resize reader panels. |
| [browser-viewer/src/password-prompt.ts](../browser-viewer/src/password-prompt.ts) | 12 | Asks for passwords when opening protected PDFs. |
| [browser-viewer/src/virtual-list.ts](../browser-viewer/src/virtual-list.ts) | 48 | Shows only visible rows in long lists. |
| [browser-viewer/src/row-heights.ts](../browser-viewer/src/row-heights.ts) | 15 | Tracks row sizes for virtual scrolling. |

### Document opening and navigation

| Source file | Nonblank | Function |
| --- | ---: | --- |
| [browser-viewer/src/viewer.ts](../browser-viewer/src/viewer.ts) | 705 | Coordinates the active document, selection, window events and navigation. |
| [browser-viewer/src/viewer-profile.ts](../browser-viewer/src/viewer-profile.ts) | 4 | Selects the Full reader feature profile. |
| [browser-viewer/src/browser-session.ts](../browser-viewer/src/browser-session.ts) | 386 | Owns PDF workers, open documents, caches and extracted-file lifetimes. |
| [browser-viewer/src/inspection-session.ts](../browser-viewer/src/inspection-session.ts) | 82 | Tracks inspection state and cancellation. |
| [browser-viewer/src/pdfjs-adapter.ts](../browser-viewer/src/pdfjs-adapter.ts) | 72 | Connects the reader to public PDF.js loading and document APIs. |
| [browser-viewer/src/navigation.ts](../browser-viewer/src/navigation.ts) | 51 | Resolves document and message navigation requests and maps pages to proven message owners. |
| [browser-viewer/src/embedded-navigation.ts](../browser-viewer/src/embedded-navigation.ts) | 32 | Navigates into embedded PDF documents. |
| [browser-viewer/src/linked-opening.ts](../browser-viewer/src/linked-opening.ts) | 32 | Handles linked document opening requests. |
| [browser-viewer/src/opening-destination.ts](../browser-viewer/src/opening-destination.ts) | 28 | Resolves the requested initial page or destination. |
| [browser-viewer/src/scrolling-pages.ts](../browser-viewer/src/scrolling-pages.ts) | 247 | Manages page rendering and scrolling through the active PDF. |
| [browser-viewer/src/structure-tree.ts](../browser-viewer/src/structure-tree.ts) | 12 | Checks structure-tree size and cycles before accessible DOM presentation. |
| [browser-viewer/src/document-kind.ts](../browser-viewer/src/document-kind.ts) | 9 | Classifies the opened document for reader behavior. |

### Email and archive interpretation

| Source file | Nonblank | Function |
| --- | ---: | --- |
| [browser-viewer/src/domain/inspection.d.ts](../browser-viewer/src/domain/inspection.d.ts) | 281 | Defines the inspection data contract. |
| [browser-viewer/src/domain/observations.ts](../browser-viewer/src/domain/observations.ts) | 13 | Defines observed PDF facts used by interpretation. |
| [browser-viewer/src/domain/interpret.ts](../browser-viewer/src/domain/interpret.ts) | 185 | Interprets PDF observations as EA-PDF messages and relationships. |
| [browser-viewer/src/domain/xmp.ts](../browser-viewer/src/domain/xmp.ts) | 140 | Reads XMP metadata into interpretation values. |
| [browser-viewer/src/domain/hashes.ts](../browser-viewer/src/domain/hashes.ts) | 54 | Provides hashes used to identify interpreted content. |
| [browser-viewer/src/interpret-worker.ts](../browser-viewer/src/interpret-worker.ts) | 16 | Runs interpretation away from the window thread. |
| [browser-viewer/src/message-index.ts](../browser-viewer/src/message-index.ts) | 65 | Builds the navigable message index. |
| [browser-viewer/src/message-identifiers.ts](../browser-viewer/src/message-identifiers.ts) | 51 | Normalizes and compares message identifiers. |
| [browser-viewer/src/collection-data.ts](../browser-viewer/src/collection-data.ts) | 6 | Reads collection members and folder information. |
| [browser-viewer/src/collection-names.ts](../browser-viewer/src/collection-names.ts) | 12 | Chooses readable collection and member names. |
| [browser-viewer/src/files-message-order.ts](../browser-viewer/src/files-message-order.ts) | 39 | Orders files according to their message relationships. |
| [browser-viewer/src/archive-stats.ts](../browser-viewer/src/archive-stats.ts) | 24 | Summarizes archive contents and counts. |
| [browser-viewer/src/retained-size.ts](../browser-viewer/src/retained-size.ts) | 16 | Estimates retained data for resource budgeting. |
| [browser-viewer/src/source-email.ts](../browser-viewer/src/source-email.ts) | 17 | Locates preserved original email sources. |
| [browser-viewer/src/supplement-links.ts](../browser-viewer/src/supplement-links.ts) | 27 | Identifies and follows supplementary document links. |
| [browser-viewer/src/attachment-types.ts](../browser-viewer/src/attachment-types.ts) | 5 | Defines attachment data and relationships. |
| [browser-viewer/src/pdfjs-assets.d.ts](../browser-viewer/src/pdfjs-assets.d.ts) | 31 | Declares the PDF.js asset API used by the reader. |
| [browser-viewer/src/pdfjs-document-parts.d.ts](../browser-viewer/src/pdfjs-document-parts.d.ts) | 38 | Declares the document-part API added to PDF.js. |

### Search and embedded indexes

| Source file | Nonblank | Function |
| --- | ---: | --- |
| [browser-viewer/src/search-controller.ts](../browser-viewer/src/search-controller.ts) | 257 | Coordinates search controls, workers, filters and result lists. |
| [browser-viewer/src/document-search.ts](../browser-viewer/src/document-search.ts) | 93 | Searches text within the active document. |
| [browser-viewer/src/collection-search.ts](../browser-viewer/src/collection-search.ts) | 53 | Searches messages across collection members. |
| [browser-viewer/src/metadata-search.ts](../browser-viewer/src/metadata-search.ts) | 2 | Matches message metadata against user searches. |
| [browser-viewer/src/embedded-index.ts](../browser-viewer/src/embedded-index.ts) | 94 | Loads and checks an embedded search index. |
| [browser-viewer/src/fulltext-index.ts](../browser-viewer/src/fulltext-index.ts) | 170 | Queries the embedded full-text index. |
| [browser-viewer/src/page-text.ts](../browser-viewer/src/page-text.ts) | 60 | Extracts and prepares page text for searching. |

### Exports and attachment handling

| Source file | Nonblank | Function |
| --- | ---: | --- |
| [browser-viewer/src/attachment-viewer.ts](../browser-viewer/src/attachment-viewer.ts) | 176 | Opens supported attachments in a dedicated viewing window. |
| [browser-viewer/src/downloads.ts](../browser-viewer/src/downloads.ts) | 18 | Coordinates browser downloads. |
| [browser-viewer/src/bounded-download.ts](../browser-viewer/src/bounded-download.ts) | 31 | Limits data retained while preparing downloads. |
| [browser-viewer/src/blob-hash.ts](../browser-viewer/src/blob-hash.ts) | 41 | Hashes browser file data without retaining a second full copy. |
| [browser-viewer/src/message-download.ts](../browser-viewer/src/message-download.ts) | 31 | Chooses proven message scope and prepares source or body-page exports. |
| [browser-viewer/src/metadata-csv.ts](../browser-viewer/src/metadata-csv.ts) | 135 | Converts message metadata into independent CSV output. |
| [browser-viewer/src/metadata-export.ts](../browser-viewer/src/metadata-export.ts) | 59 | Prepares downloadable metadata, including CSV and XMP. |

### Full edition conversation controls

| Source file | Nonblank | Function |
| --- | ---: | --- |
| [browser-viewer/src/conversations.ts](../browser-viewer/src/conversations.ts) | 2 | Displays and controls the Conversations view. |
| [browser-viewer/src/thread-preview.ts](../browser-viewer/src/thread-preview.ts) | 120 | Displays a preview of a conversation thread. |

### Export security validation and reporting

| Source file | Nonblank | Function |
| --- | ---: | --- |
| [browser-viewer/src/security-controller.ts](../browser-viewer/src/security-controller.ts) | 179 | Starts cancellable offline validation on open and displays filename states and a downloadable modal report opened through filename activation. |
| [browser-viewer/src/security-worker.ts](../browser-viewer/src/security-worker.ts) | 11 | Runs cryptographic checks away from the reading interface. |
| [browser-viewer/src/security-validation.ts](../browser-viewer/src/security-validation.ts) | 108 | Checks signatures, timestamps, C2PA and authenticated creator archival eligibility; foreign files skip additional revision checks. |
| [browser-viewer/src/security-archival.ts](../browser-viewer/src/security-archival.ts) | 112 | Verifies creator revision continuity, protected content, ESS signer binding, historical trust/evidence and credential renewals. |
| [browser-viewer/src/security-cms.ts](../browser-viewer/src/security-cms.ts) | 58 | Verifies bounded CMS signatures with included certificates, keeping certificate trust separate. |
| [browser-viewer/src/security-trust.ts](../browser-viewer/src/security-trust.ts) | 167 | Checks bounded offline certificate paths, CRL and OCSP evidence against explicitly selected CA roots. |
| [browser-viewer/src/security-c2pa.ts](../browser-viewer/src/security-c2pa.ts) | 79 | Verifies supported EA-PDF COSE claims, assertion digests and safe current-file bindings. |
| [browser-viewer/src/security-cbor.ts](../browser-viewer/src/security-cbor.ts) | 34 | Reads the bounded CBOR subset used by the supported C2PA export profile. |
| [browser-viewer/src/security-types.ts](../browser-viewer/src/security-types.ts) | 13 | Defines security facts, individual checks and report states. |

### Full edition conversation processing

| Source file | Nonblank | Function |
| --- | ---: | --- |
| [browser-viewer/src/conversation-contract.ts](../browser-viewer/src/conversation-contract.ts) | 4 | Defines the conversation feature boundary used by the window. |
| [browser-viewer/src/thread-graph.ts](../browser-viewer/src/thread-graph.ts) | 70 | Groups messages into conversation relationships. |
| [browser-viewer/src/thread-worker.ts](../browser-viewer/src/thread-worker.ts) | 2 | Builds conversation results away from the window thread. |

### Full edition Portfolio controls

| Source file | Nonblank | Function |
| --- | ---: | --- |
| [browser-viewer/src/collection-view.ts](../browser-viewer/src/collection-view.ts) | 81 | Controls the optional collection presentation feature. |
| [browser-viewer/src/portfolio.ts](../browser-viewer/src/portfolio.ts) | 94 | Coordinates Portfolio presentation and interaction. |
| [browser-viewer/src/portfolio-view.ts](../browser-viewer/src/portfolio-view.ts) | 149 | Builds Portfolio view controls and presentation. |
| [browser-viewer/src/portfolio-scatter.ts](../browser-viewer/src/portfolio-scatter.ts) | 11 | Lays out a scatter presentation of collection items. |
| [browser-viewer/src/portfolio-splitter.ts](../browser-viewer/src/portfolio-splitter.ts) | 42 | Controls resizable Portfolio regions. |
| [browser-viewer/src/portfolio-thumbnails.ts](../browser-viewer/src/portfolio-thumbnails.ts) | 40 | Prepares Portfolio item thumbnails. |

### Full edition Portfolio contract

| Source file | Nonblank | Function |
| --- | ---: | --- |
| [browser-viewer/src/collection-view-contract.ts](../browser-viewer/src/collection-view-contract.ts) | 21 | Defines the optional collection-view interface for the main reader. |

### Browser extension integration

| Source file | Nonblank | Function |
| --- | ---: | --- |
| [browser-viewer/src/extension/background.ts](../browser-viewer/src/extension/background.ts) | 14 | Handles extension background events and reader opening. |
| [browser-viewer/src/extension/manifest.json](../browser-viewer/src/extension/manifest.json) | 32 | Declares extension entry points, permissions and packaged resources. |


## Viewer local build and install tools

### Versioned builds and permanent unpacked installs

| Source file | Nonblank | Function |
| --- | ---: | --- |
| [browser-viewer/scripts/build-local.mjs](../browser-viewer/scripts/build-local.mjs) | 20 | Advances the viewer minor version and rebuilds the locally installable Full browser packages. |
| [browser-viewer/scripts/local-install.mjs](../browser-viewer/scripts/local-install.mjs) | 55 | Verifies build bytes and updates permanent unpacked reader directories in place. |
| [browser-viewer/scripts/local-reload-smoke.mjs](../browser-viewer/scripts/local-reload-smoke.mjs) | 52 | Checks an actual extension reload across two versions at one fixed install path. |
| [browser-viewer/scripts/extension-version.mjs](../browser-viewer/scripts/extension-version.mjs) | 15 | Maps package versions to browser versions and advances minor versions. |


## Library patch recipes

### PDF.js patches for reader APIs and behavior

| Source file | Nonblank | Function |
| --- | ---: | --- |
| [browser-viewer/vendor/pdfjs-patches/0001-document-parts.patch](../browser-viewer/vendor/pdfjs-patches/0001-document-parts.patch) | 376 | Exposes document parts and their page relationships to the reader. |
| [browser-viewer/vendor/pdfjs-patches/0002-document-assets.patch](../browser-viewer/vendor/pdfjs-patches/0002-document-assets.patch) | 622 | Exposes embedded and associated document assets. |
| [browser-viewer/vendor/pdfjs-patches/0003-large-archives.patch](../browser-viewer/vendor/pdfjs-patches/0003-large-archives.patch) | 763 | Adds Full reader storage and loading changes for large archives. |
| [browser-viewer/vendor/pdfjs-patches/0004-inspection-yield.patch](../browser-viewer/vendor/pdfjs-patches/0004-inspection-yield.patch) | 70 | Lets inspection yield so the browser remains responsive. |
| [browser-viewer/vendor/pdfjs-patches/0005-zero-width-xref.patch](../browser-viewer/vendor/pdfjs-patches/0005-zero-width-xref.patch) | 40 | Handles zero-width cross-reference stream fields. |
| [browser-viewer/vendor/pdfjs-patches/0006-collection-presentation.patch](../browser-viewer/vendor/pdfjs-patches/0006-collection-presentation.patch) | 107 | Exposes collection presentation information. |
| [browser-viewer/vendor/pdfjs-patches/0007-collection-navigation.patch](../browser-viewer/vendor/pdfjs-patches/0007-collection-navigation.patch) | 160 | Exposes collection membership and folder navigation. |
| [browser-viewer/vendor/pdfjs-patches/0008-metadata-extraction.patch](../browser-viewer/vendor/pdfjs-patches/0008-metadata-extraction.patch) | 40 | Exposes metadata needed for reader inspection and export. |
| [browser-viewer/vendor/pdfjs-patches/0009-named-opening.patch](../browser-viewer/vendor/pdfjs-patches/0009-named-opening.patch) | 10 | Supports opening documents at named destinations. |
| [browser-viewer/vendor/pdfjs-patches/0010-rtl-ligatures.patch](../browser-viewer/vendor/pdfjs-patches/0010-rtl-ligatures.patch) | 164 | Backports corrected extraction order for right-to-left ligatures. |
| [browser-viewer/vendor/pdfjs-patches/0011-page-only-extraction.patch](../browser-viewer/vendor/pdfjs-patches/0011-page-only-extraction.patch) | 107 | Adds isolated page-only extraction for scoped message downloads. |
| [browser-viewer/vendor/pdfjs-patches/0012-security-facts.patch](../browser-viewer/vendor/pdfjs-patches/0012-security-facts.patch) | 70 | Exposes bounded signature dictionaries and decoded DSS certificates/CRLs/OCSP without applying application trust policy. |
| [browser-viewer/vendor/pdfjs-patches/0013-creator-archival.patch](../browser-viewer/vendor/pdfjs-patches/0013-creator-archival.patch) | 266 | Exposes prefix-bounded historical creator revisions and protected-object graphs; rejects ambiguous structures and visible timestamp changes. |

### HTML renderer patches for PDF output

| Source file | Nonblank | Function |
| --- | ---: | --- |
| [third_party/openhtmltopdf/patches/0001-Add-PDF-A-4-conformance-support.patch](../third_party/openhtmltopdf/patches/0001-Add-PDF-A-4-conformance-support.patch) | 109 | Adds PDF/A-4 rendering support. |
| [third_party/openhtmltopdf/patches/0002-Keep-inter-word-gaps-extractable-under-negative-lett.patch](../third_party/openhtmltopdf/patches/0002-Keep-inter-word-gaps-extractable-under-negative-lett.patch) | 26 | Preserves extractable word gaps under negative letter spacing. |
| [third_party/openhtmltopdf/patches/0003-Add-PDF-UA-2-conformance-support.patch](../third_party/openhtmltopdf/patches/0003-Add-PDF-UA-2-conformance-support.patch) | 361 | Adds PDF/UA-2 rendering support. |
| [third_party/openhtmltopdf/patches/0004-Disable-GSUB-ligature-substitution-so-all-glyphs-map.patch](../third_party/openhtmltopdf/patches/0004-Disable-GSUB-ligature-substitution-so-all-glyphs-map.patch) | 71 | Keeps glyphs mapped to text by disabling GSUB ligature substitution. |

## Keeping this inventory current

The generator measures maintained source files directly. Before every GitHub push, run `python3 scripts/build_component_inventory.py` and include the refreshed file in the changes being pushed. When responsibilities change, update the descriptions and group assignments in that script. New Java, viewer or Python package source files must be described before regeneration succeeds.

Run `python3 scripts/build_component_inventory.py --check` to verify coverage and reject stale counts. GitHub conversion-quality checks run this command on pull requests and pushes to main. The output is deterministic: unchanged source and descriptions produce the same document.

See [code ownership and boundaries](ARCHITECTURE.md) and [current project status](PROJECT_STATUS.md).
