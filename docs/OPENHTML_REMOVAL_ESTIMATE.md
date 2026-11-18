# Prospective OpenHTMLtoPDF removal

Read-only estimate, 14 November 2026. **No OpenHTMLtoPDF code, dependency,
patch, test or artifact was removed.** Selecting WeasyPrint as default does not
remove the shared OpenHTML dependency.

This snapshot predates the subsequent newsletter footer fixes, which add selector
serialization and worker compatibility code. Counts below remain dated audit
evidence and are not a current net-removal promise.

The audit identifies **342 nonblank first-party lines as direct deletion
candidates**, plus **567 nonblank lines of patch recipe text**. These are gross
opportunities, not an implemented net reduction: equivalent replacements for
shared CSS, font, URI and color behavior are still required.

## Counting method

The method matches [the component inventory](SOFTWARE_COMPONENTS.md): every
nonblank physical line counts, including imports, comments and braces. Blank
lines are excluded. This is not a comment-stripped statement count. Source
ranges below describe the dated working-tree snapshot; the private audit records
file hashes and each selected range. Source line numbers can move after edits.

| Category | Nonblank lines | Physical lines | Treatment |
| --- | ---: | ---: | --- |
| Production Java directly specific to OpenHTML | 174 | 177 | Candidate removal |
| Dedicated build, verification and publication tooling | 155 | 163 | Candidate retirement |
| Dedicated Maven repository/dependency declarations | 13 | 13 | Candidate removal |
| **First-party direct deletion candidates** | **342** | **353** | Gross count before replacement work |
| Four patch recipes | 567 | 624 | Separate maintenance text; includes diff headers, context and removed lines |
| Shared spans containing OpenHTML dependencies | 310 | 318 | Require equivalent behavior; excluded from savings |
| Tests claimed removable | 0 | 0 | Preserve/adapt regression coverage |

Retiring the first-party candidates and recipes would retire **909 nonblank
repository lines**, but only 342 belong to the direct code/configuration/tooling
count. Calling all 909 application source lines would overstate the reduction.

## Direct deletion inventory

| File | Audited line ranges | Nonblank lines | Responsibility that could retire |
| --- | --- | ---: | --- |
| [MailHtmlRenderer.java](../src/main/java/eapdf/MailHtmlRenderer.java#L111) | 11–12, 112, 114–166 | 56 | OpenHTML imports, engine guard and layout/export branch |
| [FontSupport.java](../src/main/java/eapdf/FontSupport.java#L20) | 3, 5, 20–49, 99, 102–103, 105–111, 115, 140, 148 | 45 | Builder font registration, metrics cache and cache-key preparation |
| [MailRenderPolicy.java](../src/main/java/eapdf/MailRenderPolicy.java#L23) | 7–12, 18, 23–44, 177–185, 228–246 | 54 | OpenHTML stream adapters and logger configuration |
| [PdfMailWriter.java](../src/main/java/eapdf/PdfMailWriter.java#L54) | 15–17, 54–55, 60–62, 80–82, 371–373, 499, 501 | 16 | Adapter aliases, logger initializer and OpenHTML archive-font scope |
| [Common.java](../src/main/java/eapdf/Common.java#L501) | 501–502 | 2 | OpenHTML-specific log filtering |
| [WeasyPrintRenderer.java](../src/main/java/eapdf/WeasyPrintRenderer.java#L5) | 5 | 1 | OpenHTML builder import, after normalization is decoupled |
| [build_patched_openhtmltopdf.py](../scripts/build_patched_openhtmltopdf.py) | 1–83 | 77 | Dedicated reproducible renderer rebuild/verification |
| [verify_published_libraries.py](../scripts/verify_published_libraries.py) | 1–35 | 33 | Dedicated renderer package-byte verification |
| [packages.yml](../.github/workflows/packages.yml) | 1–45 | 45 | Dedicated renderer library publication workflow |
| [pom.xml](../pom.xml#L20) | 20–27, 124–128 | 13 | Vendored renderer repository and dependency |

Small reference/documentation cleanups and any compatibility setting changes are
not included. No external publication action was performed by this audit.

## Shared behavior that must survive

WeasyPrint currently calls `FontSupport.configure` through an OpenHTML builder.
That method also normalizes authored font families, applies fallback families
and reports missing glyphs. Its registration/cache parts can retire, but the
normalization behavior needs to remain.

| Shared span | Nonblank lines | Required replacement or retention |
| --- | ---: | --- |
| `EmailStyles.java`, whole file | 199 | CSS scoping and reader visibility decisions; not a removable renderer-only file |
| `FontSupport.java`, 96 and 197–240 | 45 | Builder-bearing signature and computed font-family resolution |
| `MailHtmlRenderer.java`, 507, 555–556 and 590–615 | 29 | Legacy HTML color parsing/normalization |
| `MailRenderPolicy.java`, 17 and 46–81 | 36 | URI interface/default resolver, preserving CID, location, base-URI and resource restrictions |
| `WeasyPrintRenderer.java`, audited configure call | 1 | Replace the dummy builder call after normalization is decoupled |

These 310 lines describe dependency-bearing spans, not 310 additional deletion
savings. Some lines can remain unchanged while their parser/types are replaced.
The amount of new replacement code has not been measured, so an exact net saving
would require a separate implementation and validation pass.

The final Java EA-PDF assembly, attachments, source retention, DParts, metadata,
signing, indexes, structure repair and validation remain. So do the WeasyPrint
worker, font-style verification, subprocess supervision and sandbox.
`PatchedHtmlRendererTests.java` has 110 nonblank lines covering captions,
Unicode, namespaces and MCID ownership; these are preservation regressions to
retain or adapt, not automatic test deletion savings.

## Third-party source is a separate quantity

The two packaged renderer source JARs contain 329 Java files, **65,470 nonblank
lines** (76,579 physical lines). Those are upstream library sources already
excluded from the first-party component baseline. Removing the artifacts would
remove that packaged source, but would not save 65,470 lines of application code.
