# Third-Party Notices

This project is licensed under Apache License 2.0 for the project code in this
repository unless otherwise noted.

This file summarizes third-party components bundled into the build or required
by default runtime workflows. It is an engineering notice file, not legal
advice.

Both runnable JARs merge upstream notices, license variants and service
providers. Project notices and exact DejaVu/Liberation font license notices
are included under `META-INF/eapdf/`. GUI and CLI shading use fresh thin
inputs, avoiding duplicate implementation classes from shading a fat JAR
twice. Redistribution/source/relinking review remains release-specific.

## Dependency License Summary

### Apache License 2.0

- `com.drewnoakes:metadata-extractor:2.22.0` (JPEG Exif orientation metadata)

- `com.pff:java-libpst:0.9.3`
- `org.simplejavamail:outlook-message-parser:1.16.3`
- `com.github.bbottema:rtf-to-html:2.0.3`
- `org.apache.poi:poi:6.0.0`
- `org.apache.poi:poi-ooxml:6.0.0`
- `org.apache.pdfbox:pdfbox:3.0.8`
- `org.apache.pdfbox:xmpbox:3.0.8`
- `com.google.api-client:google-api-client:2.9.2`
- `com.google.oauth-client:google-oauth-client:1.39.0`
- `com.google.oauth-client:google-oauth-client-jetty:1.39.0`
- `com.google.oauth-client:google-oauth-client-java6:1.39.0`
- `com.google.http-client:google-http-client-gson:2.2.0`
- `com.google.http-client:google-http-client:2.2.0`
- `com.google.code.gson:gson:2.15.0`
- `com.google.apis:google-api-services-gmail:v1-rev20261106-2.9.2`

### Bouncy Castle License (MIT-style)

- `org.bouncycastle:bcpkix-jdk18on:1.87`, `bcprov-jdk18on:1.87`, and
  `bcutil-jdk18on:1.87` provide CMS digital signatures. Their upstream license
  is bundled under `META-INF/LICENSE` in the runnable JARs.
  See https://www.bouncycastle.org/licence.html.

### BSD 3-Clause

- `com.adobe.xmp:xmpcore:6.1.11`, transitive metadata-extractor dependency. Its published POM declares BSD 3-Clause; the JAR has no standalone license file. The Adobe upstream BSD notice is supplied in `third_party_licenses/Adobe-XMP-Core-BSD-3-Clause.txt`.

### MIT

- `org.jsoup:jsoup:1.24.1`
- `com.beetstra.jutf7:jutf7:1.0.0` supplies legacy UTF-7 email decoding.
  Its copyright and permission notice are bundled in
  `third_party_licenses/jutf7-MIT.txt`. Source: https://jutf7.sourceforge.net/.

### EPL 2.0 / EDL 1.0 / GPL 2 with Classpath Exception

- `jakarta.mail:jakarta.mail-api:2.1.5`

### EPL 2.0

- `org.eclipse.angus:angus-mail:2.0.5`

### EDL 1.0

- `jakarta.activation:jakarta.activation-api:2.1.4`
- `org.eclipse.angus:angus-activation:2.0.3`

### LGPL 2.1 or later

- `io.github.openhtmltopdf:openhtmltopdf-pdfbox:1.1.114-eapdf.1`

This is the one runtime dependency that makes commercial redistribution more
complex than a pure Apache/MIT/EPL stack. The project itself can still be
licensed under Apache-2.0, but redistributed fat jars that include
`openhtmltopdf` need to respect the LGPL terms for that component.

## Python And Test Dependencies

- pypdf 7.1.0 and ReportLab: BSD-3-Clause
- pikepdf 10.18.1: MPL-2.0
- JUnit Jupiter 6.1.3 (Java tests only): EPL-2.0

These Python versions are the minimums required by the requirement files, not a
lockfile for every deployed environment. The renderer remains LGPL-2.1-or-later
after migration to its community-maintained Maven coordinates. The current
version audit and upstream links are in the README's dependency tables.

## Assets and Fonts

### ThinkBox branding assets

Files under [`assets/`](assets/) such as:

- `thinkbox_footer.png`
- `thinkbox_footer_optimized.png`
- `thinkbox_footer_scaled.png`
- `thinkbox_logo.jpg`

should be treated as proprietary unless separately licensed by their owner.

### Liberation Sans

[`assets/LiberationSans-Regular.ttf`](assets/LiberationSans-Regular.ttf) appears
to be an open-source Liberation Sans font build, but this repository should
carry the exact upstream font license text before broader redistribution.

## Recommended Release Accompaniments

Outstanding: the license-text directory below is currently a checklist, not a
complete collection of license files. The shaded-jar build only transforms the
manifest and does not merge colliding dependency license/notice resources.
Review the actual release package before redistribution; this summary alone is
not evidence that all notices and LGPL obligations have been satisfied.

For private or commercial release packaging, include:

- `LICENSE`
- `NOTICE`
- this file
- `docs/THIRD_PARTY_LICENSES.md`
- the standard license texts listed in [`third_party_licenses/`](third_party_licenses/)

## Apache-Licensed Replacement Path For `openhtmltopdf`

There is no known Apache-licensed drop-in replacement in this project’s current
architecture for HTML/CSS to PDF rendering.

The closest Apache-licensed path is:

1. Apache FOP
   - official Apache project
   - Apache License 2.0
   - supports PDF output and PDF/A features
   - but it is an XSL-FO renderer, not an HTML/CSS renderer
2. A custom renderer built directly on Apache PDFBox
   - also Apache License 2.0
   - but requires maintaining the layout engine in-house

So the realistic replacement story is:

- if you want to stay HTML/CSS-based with minimal rewrite, there is no clean
  Apache drop-in identified here
- if you want an Apache-only redistribution story, the most credible migration
  target is Apache FOP, but that requires rewriting the rendering layer from
  HTML/CSS templates to XSL-FO

## Source Pointers

The public `in/attached-message.pst` fixture comes from Apache Tika. Its pinned upstream source, license link, and SHA-256 are recorded in `src/test/resources/eapdf/email/attached-message.pst.md`. Maven copies it into the test classpath; it is not packaged in release jars.

- OpenHTMLtoPDF license file in the upstream repository:
  `https://github.com/openhtmltopdf/openhtmltopdf/blob/main/LICENSE`
- Apache FOP project site:
  `https://xmlgraphics.apache.org/fop/`
- Apache FOP license page:
  `https://xmlgraphics.apache.org/fop/license.html`

### Droid Sans Fallback Full

The application bundles the unmodified `DroidSansFallbackFull.ttf` font for Chinese and Japanese glyph coverage. Copyright 2006–2010 Google Corp.; Droid is a trademark of Google Corp. Licensed under Apache License 2.0. See [the font notice and license](third_party_licenses/DroidSansFallback-LICENSE.txt).

### Nanum Gothic

The application bundles the unmodified `NanumGothic-Regular.ttf` font for Korean glyph coverage. Copyright (c) 2010, NHN Corporation. Licensed under the SIL Open Font License 1.1. The [copyright and license](third_party_licenses/NanumGothic-OFL.txt) and [pinned source and SHA-256](third_party_licenses/NanumGothic-provenance.json) are packaged with the font.

### Bundled DejaVu Sans Mono

`src/main/resources/assets/DejaVuSansMono.ttf` is DejaVu Sans Mono 2.37. It is used for plain-text email and code blocks. Its Bitstream Vera license and DejaVu notices are included in `third_party_licenses/DejaVu-LICENSE.txt` and packaged in both JARs.

### Bundled Liberation Serif

`src/main/resources/assets/LiberationSerif-Regular.ttf` is Liberation Serif 2.1.5, used for serif email text. Its SIL Open Font License 1.1 notice is included in `third_party_licenses/Liberation-OFL-1.1.txt` and packaged in both JARs.

The creator's `IndexedPdfLoader` adapts Apache PDFBox 3.0.8 object-stream loading
under the Apache License 2.0, retaining the source license header. It shares the
outer parser's object-key index; PDFBox parsing and leniency remain in use.

The local `StructureElementAccessObject` adaptation, originally based on veraPDF parser 1.31.71 and rechecked with 1.31.72,
reuses the same parent-number-tree lookup wrapper within an immutable document.
It preserves number-tree lookup and malformed-tree behavior, and clears the
thread-local reference after each upstream check. The adapted MPL 2.0 source is
bundled under META-INF/eapdf/modified-verapdf-sources.

## Patched OpenHTMLtoPDF build

The core and PDFBox modules use community OpenHTMLtoPDF 1.1.114 with four
unmodified patches from Jia Liang's HTML-to-PDF-UA repository. Version
`1.1.114-eapdf.1` identifies this derivative; it is not an upstream release.
Patch sources, immutable provenance, checksums and corresponding source JARs
are supplied under `third_party/openhtmltopdf/` and `third_party/maven/`.
The upstream LGPL-2.1-or-later license continues to apply. Rebuilding and
resolution instructions are in `docs/RENDERER.md`.

## Offline full-text indexing

The Java creator uses the existing Apache PDFBox dependency for text extraction.
PDF.js and its notices remain part of the separately distributed browser viewer.

## FontBox backport

FontBox 3.0.8-eapdf.1 is a local Apache-2.0 derivative of 3.0.8. PDFBOX-6297
bounds cmap groups and fixes a glyph-index boundary; PDFBOX-6308 limits Type 1/2
subroutine depth. The local follow-up restores the Type 2 depth on return and
skips overlapping groups while retaining U+0000 mappings. The original notices,
full corresponding source JAR, patch provenance and checksums are supplied in
`third_party/fontbox/` and `third_party/maven/`. Rebuild with
`python3 scripts/build_patched_fontbox.py --rebuild`. PDFBox remains 3.0.8.

## Embedded Linux renderer runtime

The Linux creator JARs include a frozen WeasyPrint 70.0 worker, CPython and
native font libraries. These resources are verified and extracted into a private
cache for execution. Pinned Python/build dependencies are recorded in
`requirements-weasyprint.lock` and `requirements-weasyprint-build.lock`.
Runtime package versions, native source-package provenance and file checksums
are retained with the executable. Python distribution license metadata, font
notices, native copyright texts and PyInstaller's COPYING text are retained in
the runtime's `_internal/` and `licenses/` directories. Retain these materials
with the runtime when copying a complete application bundle.

## PDF security library dependency

`digital.thinkbox:pdf-security:0.1.0` uses Apache License 2.0 and brings the
EU DSS 6.5 PDFBox backend and offline providers under LGPL-2.1-or-later,
COSE-JAVA 1.1.0 under BSD-3-Clause, and PeterO CBOR and supporting libraries
under their retained upstream terms. Library notices, license texts and DSS
source JARs remain in `META-INF/pdf-security/` inside creator and Java validator packages.
Retain those materials and the ability to rebuild/relink modified LGPL modules.
The unmodified 0.1.0 library, generated test fixtures and their Maven metadata
are vendored in `third_party/maven/digital/thinkbox/` with SHA-256 checksums.
The fixtures generate keys at test time. The library JAR retains its original
Apache license and attribution under `META-INF/pdf-security/`.
EA-PDF enables these capabilities only through explicit export-security settings;
see `docs/DIGITAL_SIGNATURES.md`. The companion source module includes the
collection-preserving profile hook and its signing/renewal regression test.
