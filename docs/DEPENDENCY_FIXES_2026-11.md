# November dependency repairs

Unreleased source changes, checked 17 November 2026. Application and viewer
versions are unchanged. These checks do not establish independent certification.

## Bug fixes

- OpenHTMLtoPDF **1.1.114-eapdf.1** rebases the four unchanged EA-PDF patches onto
  upstream 1.1.114, including [PR #286](https://github.com/openhtmltopdf/openhtmltopdf/pull/286).
  Paginated rowspan cell fragments now align with adjacent rows. No PDF/A failure
  was attributed to the old border defect.
- veraPDF parser **1.31.72** and validation-model **1.31.225** are updated together.
  Both published source artifacts contain the `long` post-EOF count changes from
  [parser commit 7ac45e1](https://github.com/veraPDF/veraPDF-parser/commit/7ac45e14b5df5e183039b809c0440a38a788fdc3)
  and [model commit 6616f38](https://github.com/veraPDF/veraPDF-validation/commit/6616f3892577abd7a5c64e85bbe15e98498e185b).
  Runtime version verification also requires the new pair. Feature-reporting and
  metadata-fixer follow the model's 1.31.225 dependency set; core remains 1.31.56.
- Creator date formatting explicitly uses `Locale.ROOT`, keeping Gregorian years
  and ASCII digits in message dates and final XMP under `th_TH` and `en_US`.
- Creator and Java validator both use **FontBox 3.0.8-eapdf.1**. PDFBox remains
  3.0.8. This is a local derivative, not an Apache release. The backport applies
  [PDFBOX-6297](https://issues.apache.org/jira/browse/PDFBOX-6297) /
  [commit 1f6099d](https://github.com/apache/pdfbox/commit/1f6099df314778094cc805b130eea4bc6acb39e9)
  and [PDFBOX-6308](https://issues.apache.org/jira/browse/PDFBOX-6308) /
  [commit 4d4e48d](https://github.com/apache/pdfbox/commit/4d4e48d106b27fe397fbd9c25cc1555777589bef).
  A reviewed follow-up decrements the Type 2 counter in `finally`, so sequential
  calls do not consume the nesting allowance. It also skips overlapping cmap
  groups and initializes the prior group boundary to -1 to preserve U+0000.

## Verification

- Before the update, four of six EA-PDF pixel checks failed for rowspan border
  alignment. All six pass after the update. All 14 upstream rowspan tests pass,
  covering split rows, breaks between rows, repeated headers/footers, spacing,
  empty rows, vertical alignment and multi-page spans with both border models.
- Final EA-PDF regressions retain all 60 row labels and spanning-cell text, repeat
  headers on every page, and pass preservation checks. The separate and collapsed
  outputs both pass standalone Java EA-PDF and PDF/A-4f validation.
- A clean PDF/A-4f control with 127, 128, 255, 256 and 500 appended bytes is tested
  with LF and CRLF endings. The old pair gave false PDF/A-4f passes for 255 and
  256 appended bytes with LF; the updated pair reports exact counts and rejects
  every padded case. Both clean controls pass, without linkage errors.
- Final EA-PDF XMP date and message-date tests pass under US and Thai locales.
  Existing Unicode spacing, multilingual text, embedded-font, PDF/UA-2 namespace,
  caption and transparency-group checks pass with the explicit OpenHTML engine.
- FontBox tests use killable JVMs with a ten-second deadline. They cover repeated
  format-12/13 groups, overlapping groups, excessive group counts, U+0000,
  `glyphId == numGlyphs`, self/mutual and mixed local/global recursion, and valid
  sequential calls exceeding 20. Depth 20 is accepted and depth 21 is rejected.
- Public upstream cmap attachments and a format-12 fixture built with the upstream
  generator are parsed directly and embedded unchanged in PDFs. Patched direct
  parsing and embedded extraction/rendering finish within a 15-second deadline.
  Recursive CFF fixtures produce controlled nested-subroutine errors when embedded
  in PDFs; a valid 40-call CFF control retains its text and renders successfully.
  Standalone Java validation finishes for every embedded probe. These deliberately
  minimal PDFs lack EA-PDF/PDF/A identification and receive FAIL, not PASS.
- Creator runs with the adversarial test fonts terminate with errors and publish
  no PDFs; they do not time out or report `StackOverflowError`. This does not prove
  every font reaches the affected parser: unsupported OTF and incomplete fixture
  fonts may be rejected earlier. A real EML conversion with ordinary fonts passes
  standalone Java EA-PDF/PDF/A-4f validation; source bytes are hashed and retained.
- Both vendored dependencies reproduce their artifact hashes from pinned inputs.
  The FontBox build replaces only the three reviewed classes and their compiler
  generated nested classes; other upstream class bytes remain unchanged.
- Full creator verification: 506 tests, no failures or errors, three skipped.
  Full validator verification: 302 tests, no failures or errors, one skipped,
  plus two successful executable-JAR packaging checks. The added depth-boundary
  assertions also pass in a separate five-case FontBox run.

## Reproduction

Use JDK 21, Git, Maven and Python 3. Build and verify the pinned artifacts:

```sh
python3 scripts/build_patched_openhtmltopdf.py --rebuild
python3 scripts/build_patched_fontbox.py --rebuild
mvn -Deapdf.build.directory=/tmp/eapdf-dependency-creator verify
mvn -f validator-desktop/pom.xml -Deapdf.build.directory=/tmp/eapdf-dependency-validator verify
```

`RowspanBorderTests`, `LocaleDateTests`, `FontboxBackportTests` and
`TrailingDataTests` retain the targeted regressions. `FontboxProbe.java` provides
direct parsing, raw-font embedding and PDF extraction/rendering probes for fonts
supplied by the operator. Always run adversarial probes in a subprocess with an
external deadline. Original public font inputs, their hashes and private conversion
logs remain local; no private correspondence is published with this record.

## Code improvement: one PST fixture

The existing public PST input is the canonical fixture for native-file tests,
worker tests and Maven's test classpath. Deleted duplicate resource and symlink
paths are no longer required. Provenance and the original source hash remain in
the fixture notice. No original input bytes were changed.
