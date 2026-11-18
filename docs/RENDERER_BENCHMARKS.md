# Comparing the email renderers

WeasyPrint is the default renderer. OpenHTMLtoPDF remains explicitly selectable.
Check the validation results and rendered pages for the mail formats you use.
Differences in page breaking and font shaping are expected
between layout engines. A smaller PDF or shorter runtime does not establish that
an email was preserved correctly.

The follow-up [performance analysis](RENDERER_PERFORMANCE_ANALYSIS.md) separates
imports, font registration, layout and export, tests actual two-worker PST
concurrency, and records a wider visual review. Its independent measurements do
not replace or combine with the repeated cohort below.

## Reproduce the comparison

Build the creator and standalone Java validator from the same working tree into
temporary directories. A version number alone does not prove that an existing
local validator JAR includes unpublished source changes. Create two otherwise identical configuration files. Both must explicitly
set `accessibility.addPdfUaFeatures` to `true`; select
`rendering.engine: "openhtmltopdf"` in one and `"weasyprint"` in the other. The
WeasyPrint configuration must point to the supported Python environment. The
current creator has no PREMIS generation setting and generates no PREMIS.

```sh
mvn -Deapdf.build.directory=/tmp/eapdf-build package
mvn -f validator-desktop/pom.xml -Deapdf.build.directory=/tmp/eapdf-validator-build verify
```

Pass actual EML, MSG and PST sources from the repository's local `in/` directory.
The following filenames are placeholders; substitute installed local sources:

```sh
python3 scripts/benchmark_renderers.py \
  --creator-jar /tmp/eapdf-build/emailtopdfa-2.2.0-beta.3.jar \
  --validator-jar /tmp/eapdf-validator-build/eapdf-validator-2.2.0-beta.3.jar \
  --openhtml-config /tmp/eapdf-openhtml.json \
  --weasyprint-config /tmp/eapdf-weasyprint.json \
  --sample eml=in/example.eml \
  --sample msg=in/example.msg \
  --sample pst=in/example.pst \
  --evidence output/renderer-comparison-local \
  --documents /tmp/eapdf-renderer-comparison-documents \
  --repeats 3 --workers 1 --jobs 2
```

The evidence and document destinations must be new directories. Conversions use
isolated scratch directories. Cancellation terminates running descendants, skips
queued work and retains partial results with failed/cancelled status; it does not
turn partial coverage into a successful comparison. Each source produces a whole-source 1c collection;
the PST is never scheduled for individual-email extraction. This gives both
renderers the same collection assembly and validation work. Each PDF filename
includes its input type, generic case label, run number and export profile.

The script records the source SHA-256 before and after the run, the source sizes,
the starting commit, creator/validator JAR hashes and configuration hashes. Both
versioned JARs and configuration files are copied into private evidence before measurements, so a developer
rebuilding the original paths cannot change the experiment midway. The
working tree may contain unpublished changes; the script records its tracked patch
and production Java/Python source hashes alongside the manifest. Private source paths and
rendered content stay in local evidence; published summaries use generic case
labels and must not link to unpublished PDFs, logs or screenshots.

## What the numbers mean

| Measurement | Scope and limitations |
| --- | --- |
| Conversion seconds | Complete cold creator invocation, including supervised JVM and optional Python startup. Validation and raster inspection are measured separately. |
| Tree RSS | Sampled sum of resident memory across live descendants, including Python and descendants that create another process session. Shared pages can be counted more than once; brief processes and allocation spikes between samples can be missed. |
| Python RSS | Sampled live Python resident-memory subset, already included in tree RSS. It must not be added to the tree number again. The maximum observed Python process count is also recorded; requested thread/process limits do not guarantee full utilization. |
| CPU seconds | Sampled accumulated user/system CPU of observed processes. Very short processes may be missed; this is not a scheduler-level accounting trace. |
| PDF bytes | Complete final collection, including retained source emails, attachments and indexes. It is not just the renderer's intermediate PDF. |
| Validation | Standalone Java EA-PDF assessment and its bundled veraPDF PDF/A result. Failed, incomplete or unavailable results remain failures in the benchmark. |
| Text, links and tags | Sampled outer document and up to three distinct embedded email PDFs: page text, URI-target fingerprints, font names and Mail_Field/Mail_FieldName/Mail_FieldValue structure counts. Counts alone do not prove semantic tagging. |
| Raster comparison | First three pages by default of those documents, rendered at up to 96 dpi with the largest page dimension capped at 1,600 pixels. Side-by-side pages and absolute pixel differences are retained locally. |

The requested default 25 ms sampling interval, largest observed sampling gap and
all memory/time ceilings are recorded. Reading process information adds overhead;
the actual gap can exceed the requested interval. Worker-reported peak resident
memory is retained separately from external Python sampling because these
measurements have different timing and lifetime scopes.
Benchmark jobs run serially by default to reduce interference. Up to two concurrent
jobs can be requested, subject to current memory availability, but those timings
should not be mixed with serial results. Repetitions alternate renderer order to
reduce systematic cache/order bias. There is no statistical claim from a handful
of sources; retain individual runs as well as medians.

Raster RMSE and changed-pixel fractions describe differences, not visual quality.
A font substitution, line-wrap change or different page size can dominate those
numbers even when both outputs are readable. Review headers, long tables,
Unicode/RTL text, inline images, links, page boundaries and the actual collection
children. Front-matter-only screenshots are insufficient. Raster inspection uses
optional PyMuPDF and Pillow; unavailable tools are reported explicitly and do not
become a claim of visual equivalence.

## Measured results — 14 November 2026

The final font-corrected working-tree build was based on commit
`c281f086dfc53679c1580fe9f81655460fda3a0d`. The immutable creator SHA-256 was
`fa703a7f9e5f69656b701dcbd4662a43c2957df34ae04f437f8d499a37500796`;
the matching corrected validator SHA-256 was
`00d5b502aa179d9b3fc16653078fce31eb7586bfe53ee998076aa27d0663c8a9`.
Earlier diagnostic and typography-limited cohorts were retained locally and were
excluded from these figures.

Four original local inputs, covering EML, two MSG cases and one complete PST,
were converted three times with each renderer: **24 conversions and 24 standalone
Java assessments passed EA-PDF and PDF/A-4f checks**. No final source was rejected
or incomplete. Original source SHA-256 values were unchanged. The EML is a small
links/attachment sample, and the HTML MSG is modest correspondence; these are not
claims of broad complex-mail coverage. Rich tables and Unicode are covered
separately by the synthetic acceptance below.

Measurements used Linux x86-64 on an AMD Ryzen 5 2600 (six cores/twelve threads),
Java 21 and Python 3.12.3. Jobs ran serially, requested two creator threads,
allocated a 1 GiB Java heap and configured 512 MiB per WeasyPrint process with a
two-process ceiling. The maximum externally observed simultaneous Python count
was **one**, so these results do not measure a saturated two-Python-worker setup.
Each invocation was cold; no JVM or Python process was reused between samples.

| Case | Renderer | Median seconds | Median sampled peak tree RSS (MiB) | Median PDF bytes | EA-PDF / PDF-A passes |
| --- | --- | ---: | ---: | ---: | ---: |
| EML, links and attachment | OpenHTMLtoPDF | 2.632 | 255.1 | 64,022 | 3 / 3 |
| EML, links and attachment | WeasyPrint 70.0 | 5.351 | 286.5 | 60,816 | 3 / 3 |
| MSG, HTML and attachment | OpenHTMLtoPDF | 3.001 | 284.5 | 550,864 | 3 / 3 |
| MSG, HTML and attachment | WeasyPrint 70.0 | 5.793 | 310.0 | 547,446 | 3 / 3 |
| PST, whole seven-message source | OpenHTMLtoPDF | 4.495 | 460.5 | 387,233 | 3 / 3 |
| PST, whole seven-message source | WeasyPrint 70.0 | 14.807 | 356.9 | 344,253 | 3 / 3 |
| MSG, inline CID image | OpenHTMLtoPDF | 3.161 | 258.6 | 353,907 | 3 / 3 |
| MSG, inline CID image | WeasyPrint 70.0 | 5.522 | 314.8 | 327,831 | 3 / 3 |

WeasyPrint took **1.75–2.03 times** the conversion time on the single-email cases
and **3.29 times** on this PST. Its sampled tree RSS was **9–22% higher** on the
single-email cases and **22.5% lower** on the PST. Final PDF sizes were **0.6–11.1%
smaller**. These are measured tradeoffs for these small inputs, not a general
speed or memory improvement claim.

Externally sampled Python RSS peaked at 70.4 MiB across this cohort. The largest
worker-reported per-process high-water mark was 233.0 MiB. Those two measurements
have different lifetime scopes; neither is an additional amount to add to the
reported tree RSS. Inspect the retained individual runs rather than treating a
sampled RSS maximum as an exact allocation limit.

Both renderers produced one outer collection page per case. The EML and each MSG
had one two-page email child; the PST had seven distinct email children totaling
twelve pages on both renderers. Thus these PDF-size differences did not result
from omitting messages or reducing page counts.

## Visual and semantic comparison

The final CID-image pages were reviewed side by side. Headers, the authored bold
body text, source-file labels, inline-image placement and attachment listings
remain readable. Actual bold font faces are embedded; missing requested weight or
slant is a conversion error rather than a silent regular-face substitution.
The final PST comparison preserves the sampled header columns and body layout.
Native font metrics and antialiasing still differ between engines.

No required benchmark or raster tools were missing on the tested Linux host,
and the final machine assessments have no unresolved tagging blocker. The secure
WeasyPrint worker requires supported Linux Landlock/seccomp facilities; an
unsupported sandbox, missing pinned Python package or missing requested font
style is an explicit error. These prerequisites limit deployment portability.

In all sampled outer pages and email children, page dimensions/counts, URI target
fingerprints and Mail_Field/Mail_FieldName/Mail_FieldValue counts agreed. Sampled
text agreed exactly except for whitespace inserted at different wrap positions
in one long EML Message-ID; its non-whitespace characters were unchanged. The
largest changed-pixel fraction on a sampled page was about 4.1% at the documented
threshold. This is not a visual-equivalence score. Contact sheets include the
actual email children, and no private correspondence screenshots are published.

A subsequent wider PST review found a newsletter with eight unique source link
targets: WeasyPrint retained all eight, while OpenHTMLtoPDF retained four and
omitted targets on image-only anchors. This member was outside the first-three
child sample above. Both outputs pass conformance checks. See the
[source-authenticated preservation comparison](RENDERER_PERFORMANCE_ANALYSIS.md#viewing-samples-and-preservation-checks)
for the scope and limitations; the initial sampled agreement is not a claim that
all source links are equivalent across renderers.

Linux process high-water counters need an additional qualification: `getrusage().ru_maxrss`
can retain memory usage from before the Python executable started. A local control
with a 128 MiB parent allocation produced a child with roughly 12 MiB `VmHWM` but
roughly 143 MiB `ru_maxrss`. Worker-reported high-water values are therefore raw
process-lifetime diagnostics, not authoritative Python-only peaks. The tables use
external sampled live RSS; that sampling can still miss brief peaks. Enforcement
uses address-space limits and live RSS, not the inherited high-water counter.

## Rich-email acceptance — 14 November 2026

A separate synthetic edge-case email was tested with pinned WeasyPrint 70.0 in
profiles 1s and 1si. Both final PDFs pass the standalone Java EA-PDF assessment
and bundled veraPDF PDF/A-4f checks; each has eight recommendation warnings and
no failed checks. This fixture is additional edge coverage, not a real-source
performance sample.

The acceptance checks cover Greek/CJK extraction, embedded fonts, a live URI
annotation, an authored wide table with merged cells and monospace text, and
Mail_Field/Mail_FieldName/Mail_FieldValue roles. The table remains visible on a
widened page. Source bytes are retained in 1s and omitted in 1si; authored HTML
attachment declarations do not bypass the Java embedding policy.

WeasyPrint exposes a different structure-root representation and transparent
wrappers. The Java adapter imports array-valued roots and normalizes those
wrappers without dropping MCIDs or link ownership. Complete rendered-header
inventory and source/semantic equivalence recommendations remain unverified by
the machine assessment. These results do not certify accessibility or establish
identical layout for arbitrary email.

The renderer integration also exposed a validator ownership bug on real CID-image
and mailbox samples. Header text can legitimately sit in a standard Span or Link
inside a custom Mail_FieldName/Mail_FieldValue element. The previous checker
recognized only the direct marked-content owner and could incorrectly report the
core-field text assessment as incomplete. The validator now follows the bounded,
cycle-checked ownership ancestry from the verified ParentTree owner. Existing
text decoding and field-content checks remain in place; an unavailable assessment
is not converted to PASS. The benchmark must use the rebuilt validator for both
renderers.

## Acceptance and recommendation

WeasyPrint became the default by user decision after this evaluation, prioritizing
upstream standards support and the measured source-link preservation benefit.
OpenHTMLtoPDF remains explicitly selectable: it had lower cold conversion
latency in every measured case and requires no Python renderer deployment.
The WeasyPrint deployment must provide the pinned dependency, actual font
variants and supported sandbox.
The measured PST used less sampled tree RSS with WeasyPrint, but the single-email
cases used more; do not choose it on an assumed universal memory advantage.
Continue validating representative outputs and reviewing their pages and structure.
Never treat successful subprocess exit as a validation PASS. A failed WeasyPrint
conversion must remain visible; substituting the other renderer would invalidate
the comparison and hide a deployment problem.

Use separate decisions for performance and preservation: accept an alternative
only when its validation, Mail_Field semantics, Unicode, fonts, links and layout
meet the archive's requirements. Report missing dependencies, blocked sandbox
support or an unsupported tagging mapping as blockers. Report speed and memory
improvements only from completed comparable runs, with the source coverage and
measurement limitations above.
