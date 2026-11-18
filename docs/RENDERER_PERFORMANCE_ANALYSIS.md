# Renderer performance analysis

Measured assessment, 2026-11-14. WeasyPrint is pinned to **70.0** and is now the default by user decision; OpenHTMLtoPDF remains explicitly selectable. On this host, repeated WeasyPrint imports and font registration cost substantially more than email layout and PDF export. The measurements below predate the default change; no speed optimization was implemented. See [renderer benchmarks](RENDERER_BENCHMARKS.md), [WeasyPrint setup](WEASYPRINT.md) and [upstream issue review](WEASYPRINT_UPSTREAM_ISSUES.md).

## What the existing measurements establish

The original worker `elapsedSeconds` timer starts immediately before layout. It excludes Python/WeasyPrint imports, sandbox installation, `FontConfiguration`, HTML parsing and construction of the font stylesheet. That stylesheet registers `@font-face` resources before the timer starts. Its value therefore cannot be treated as total Python time or subtracted from Java wall time to identify Java overhead. See the [worker source](../src/main/resources/eapdf/weasyprint_worker.py).

Collection runs launch two Python workers for a single-email collection (message and cover), and eight for a seven-message PST collection (seven messages and cover). The real-world inputs in the new phase cohort had no width overflow and required no second layout.

## Measured attribution

This follow-up uses a new cohort with temporary phase instrumentation and separate uninstrumented concurrency trials. It is distinct from the earlier 24-run speed table. Three repeats were collected for each phase case/profile; 48 instrumented workers covered EML, MSG, PST and a rich synthetic fixture. The following are independent per-worker medians, not an additive partition:

| Worker phase | Median seconds |
| --- | ---: |
| WeasyPrint import | 0.659 |
| CSS/font-face registration | 0.211 |
| FontConfiguration construction | 0.029 |
| Layout | 0.106 |
| PDF export | 0.050 |
| Kernel sandbox installation | 0.007 |

Every profiled worker received 22 font descriptors referencing 18 staged files, totaling 13,192,824 bytes. This confirms repeated font preparation, including for covers. It does not establish how much could be removed while preserving all configured styles and fallback coverage.

The seven-message PST collection makes the repeated costs especially visible. Its independent per-run medians were:

| Scope | Median seconds |
| --- | ---: |
| Whole conversion wall time | 13.594 |
| Java renderer calls, including worker waits | 10.970 |
| Python imports, summed across eight workers | 5.377 |
| CSS/font registration, summed across workers | 1.733 |
| FontConfiguration construction, summed across workers | 0.242 |
| Layout, summed across workers | 1.060 |
| PDF export, summed across workers | 0.449 |
| Kernel sandbox installation, summed across workers | 0.061 |
| Java font staging | 0.367 |
| Other conversion residual | 2.521 |

The renderer total contains its Python and Java phases; do not add it to those rows. Java font discovery (0.270 s) and copying (0.088 s) are nested within font staging. The residual includes parsing, assembly, checks, publication and startup outside the renderer timer; it is not an assembly-only measurement. Independently computed medians need not sum to the median whole conversion.

An import-only diagnostic had a median 0.016 s for plain Python and 0.712 s for Python importing WeasyPrint. The latter used `-X importtime`, so it is a diagnostic with instrumentation overhead. In its first trace, WeasyPrint's cumulative import was 0.696 s, including `text.fonts` at 0.394 s and `text.ffi` at 0.342 s. Those cumulative entries are nested and must not be summed. The normal worker import median above independently supports the importance of this phase.

A deliberately overwide rich fixture did exercise fitting: its first layout median was 0.159 s and its additional layout 0.053 s. This is a real conditional cost, but it does not explain the real-world cohort's overhead. Font subsetting, tags and image handling remain included in the measured PDF-export phase; this run did not isolate their individual shares.

## Concurrency and memory trade-off

A separate seven-message PST experiment used the uninstrumented production creator, `-Xmx1024m`, two available processors and two requested jobs. Each cell below is the median of three repeats. RSS was sampled across the JVM and descendants; the Python column is the sampled aggregate for Python processes. These are sums of resident sets, not unique physical memory, and sampling can miss short peaks.

| Renderer | Message size cap | Wall seconds | Total peak RSS MiB | Python peak RSS MiB | Observed concurrent Python workers |
| --- | --- | ---: | ---: | ---: | ---: |
| OpenHTMLtoPDF | Default 64 MiB | 3.658 | 436.5 | 0 | 0 |
| OpenHTMLtoPDF | 32 MiB | 3.548 | 496.5 | 0 | 0 |
| WeasyPrint | Default 64 MiB | 13.170 | 345.1 | 68.8 | 1 |
| WeasyPrint | 32 MiB | 9.422 | 478.0 | 134.0 | 2 |

The memory admission rule bounds render threads using heap size divided by sixteen times the configured message limit; requesting two jobs does not override it. At a 1 GiB heap, the default 64 MiB cap permits one render thread; 32 MiB permits two. See [render admission](../src/main/java/eapdf/PdfMailWriter.java).

For this PST, the lower cap reduced WeasyPrint median wall time by 28.5% while increasing total sampled peak RSS by 38.5%. It also lowers the maximum accepted message size and staged-resource allowance and can reject inputs accepted under the default. It is a workload-specific speed/memory/admission trade-off, not a general default recommendation. OpenHTMLtoPDF's smaller timing difference does not establish a material speed improvement from three repeats. All twelve trials pass standalone Java EA-PDF and bundled veraPDF PDF/A-4f validation, preserving the whole seven-message source.

## Viewing samples and preservation checks

Five matched pairs were generated locally with the uninstrumented creator: a
small EML, HTML MSG, CID-image MSG, synthetic rich-layout EML, and whole-source
seven-message PST. The four single-email cases use direct-view 1s PDFs; the PST
uses a complete 1c collection. Side-by-side previews include the actual email
pages and three selected PST members. These private correspondence samples stay
local and ignored; they are not published documentation assets.

All ten samples pass standalone Java EA-PDF and bundled veraPDF PDF/A-4f checks.
The 26 phase/control outputs and twelve concurrency outputs also pass: **48 newly
generated PDFs, zero rejected or incomplete outputs**. Original source hashes
remain unchanged. These repeated outputs cover a small corpus, not 48 independent
source documents. Accessibility was enabled and optional PREMIS was absent.

The three real direct-view samples have identical normalized extracted text and
URI sets across engines. Both rich-layout PDFs have two pages and preserve the
checked Greek/CJK/accented text, real bold faces, monospace, merged wide table
and live links. Page width differs slightly: 820.988 points with OpenHTMLtoPDF
versus 822 points with WeasyPrint. Neither engine clips the checked right edge.

A wider PST review found a preservation difference beyond the first three
children sampled in the original benchmark. One HTML newsletter contains ten
anchor instances with eight unique canonical click targets. Its OpenHTMLtoPDF
PDF retains four targets; its WeasyPrint PDF retains all eight, without adding
non-source targets. The four missing targets belong to image-only anchors with
alternative text. The WeasyPrint output displays additional alternative-text
fallback links. Pairing was authenticated by byte-identical embedded source EML,
identical original HTML and original message headers; generated internal
Mail_GUID values differ between exports and are not a cross-export identity.

Both newsletter PDFs still pass conformance validation. This demonstrates why a
PASS and equal page counts cannot establish complete source-to-rendered semantic
equivalence. The additional links make WeasyPrint preferable for that specific
preservation check; they do not establish a general quality ranking. Layout and
alternative-text fallback differ visibly. Rich-layout samples retain eight
recommendation warnings each; PST samples retain fifteen. Human semantic and
accessibility review remains necessary. No required benchmark, validation or
raster tool was missing on this host.

### Follow-up newsletter footer diagnosis

A subsequent visual review found poor footer fallback layout. Remote source
images are blocked by policy, but the renderer retains their alternative text
and dimensions. Long labels wrap inside the source's 75-pixel logo box and
19-pixel social-icon boxes, creating narrow vertical text. The retained click
targets therefore do not imply a satisfactory visual result.

The footer also exposes two independent CSS defects. The project's current
OpenHTML-based selector serialization drops `:hover`, making those declarations
unconditional. Separately, a bounded sandboxed reproduction on pinned WeasyPrint
70.0 shows an ID-qualified author selector overriding a normal inline color.
Class-only and plain-selector controls preserve the inline color, and a genuine
`:hover` rule remains inactive. Together, the defects turn an explicitly white
footer link into dark teal, reducing contrast against the dark footer.

The diagnosis was reproduced without network access. The subsequent footer fix
preserves dynamic pseudo-classes during scoping and applies a narrow job-local
inline-specificity correction on the pinned worker. Missing images with nonblank
alternative text now use a constant neutral SVG; authored dimensions, source
alternative text in Figure tags and click targets remain. Visible fallback
labels are replaced by the placeholder, so extracted visible text changes.
The initial full-size crossed-box design was subsequently reduced to a small
centered pictogram within the unchanged image canvas; source-sized X marks are
not used.
Known numeric dimensions preserve intrinsic aspect ratio under `height:auto`.
Empty-alt decorative omissions retain their previous behavior. Remote images
are not fetched or recreated. The original sample and diagnostic evidence are
retained separately from regenerated newsletter outputs. Performance tables above
predate these fixes and were not rerun for this change.

The final whole-source regression passes EA-PDF/PDF/A-4f checks for both archives
and both extracted newsletter children. WeasyPrint retains all eight source link
targets and all six exact Figure descriptions; the footer call-to-action is
white. All six known image aspect ratios survive `height:auto` through the full
Java-to-Python pipeline. Visual review confirms readable footer text and no
vertical fallback-label columns. The initial fixed-footer sample still deferred
the main body to page 2. The pagination follow-up below resolves that gap.

### Library pagination setting

Tracing the exact staged newsletter input showed that WeasyPrint completed the
cell's content layout, then deferred the whole body row when its padding made
the row overflow. Neither source avoidance rules nor a generated rule requiring
whole rows explained the gap. Diagnostic height and avoidance overrides did not
resolve it.

The fix uses WeasyPrint's public `box-decoration-break: clone` setting for source
email cells, as a normal-priority user stylesheet. The engine then reserves
fragment padding and splits the content normally. No custom paragraph positions,
font shrinking or newsletter-specific selectors are used. Retained author rules
keep priority, including explicit keep-together rules. The existing Java CSS
frontend still warns and omits unsupported stylesheet properties; this change
does not replace that parser.

The regenerated newsletter now has the title and body text together on page 1,
with its closing/footer on page 2. The new whole-PST archive and extracted
newsletter pass EA-PDF/PDF/A-4f validation, retain all eight source click targets
and six descriptions, and preserve source hashes. Four additional guarded
regressions cover the original failure and explicit author opt-outs. Earlier
comparison and performance evidence remains dated; timings were not rerun.

## Candidate costs and relevance

| Candidate | Primary evidence | Status for this integration |
| --- | --- | --- |
| Interpreter/import startup per worker | [#2073](https://github.com/Kozea/WeasyPrint/issues/2073) discusses substantial import cost; its closure delegates lazy loading to applications. [Official first steps](https://doc.courtbouillon.org/weasyprint/v70.0/first_steps.html#command-line) suggest long-lived Python processes to amortize startup. | Confirmed large cost: 0.659 s per worker median, repeated for messages and covers. Outside the old worker timer. |
| Font configuration and registration | [70.0 font source](https://github.com/Kozea/WeasyPrint/blob/v70.0/weasyprint/text/fonts.py#L73) initializes Fontconfig during import and again for `FontConfiguration`; each new face descriptor fetches/writes font bytes and registers native configuration. | Registration measured 0.211 s per worker median; FontConfiguration 0.029 s. Java staging is a separate cost. Savings from avoiding duplicate descriptors remain unmeasured. |
| A second full layout for wide content | The [worker](../src/main/resources/eapdf/weasyprint_worker.py) conditionally rerenders after measuring overflow. The [public API](https://doc.courtbouillon.org/weasyprint/v70.0/api_reference.html#weasyprint.HTML.render) distinguishes layout from export. | Forty-five profiled worker calls used one layout; the three rich-fixture calls used two. This does not explain the original zero-overflow cohort. `Document.write_pdf` does not itself initiate another HTML layout. |
| Font embedding/subsetting and shaping | [Font export source](https://github.com/Kozea/WeasyPrint/blob/v70.0/weasyprint/pdf/fonts.py#L204) selects native HarfBuzz-Subset when available, otherwise FontTools. [#2443](https://github.com/Kozea/WeasyPrint/issues/2443) explains why older native libraries can cause slow CJK subsetting. | This host has HarfBuzz/Subset 8.3.0, above the 4.1 threshold. Total export was much smaller than import/registration in this cohort; the individual subsetting share was not isolated. |
| CSS cascade and table algorithms | Exact-70 reports [#3001](https://github.com/Kozea/WeasyPrint/issues/3001) and [#2991](https://github.com/Kozea/WeasyPrint/issues/2991) describe quadratic table wrapping and many stylesheet blocks. [#2980](https://github.com/Kozea/WeasyPrint/issues/2980) reports repeated inline styles missing cache reuse. | Input-dependent risks, particularly for table-heavy HTML email. Upstream reports do not establish their contribution to the selected local samples. |
| Image decode/encode work | [#2982](https://github.com/Kozea/WeasyPrint/issues/2982) describes sequential PNG stream construction and the memory cost of parallelizing it. | Potentially significant for image-heavy mail; measure local image functions before proposing concurrency or quality changes. |
| Tags, PDF/A processing and PDF serialization | [70.0 PDF generation](https://github.com/Kozea/WeasyPrint/blob/v70.0/weasyprint/pdf/__init__.py#L311) embeds fonts, creates image resources, adds tags and applies the selected variant. | The progress label “Adding PDF metadata” covers more than XMP. Profile these operations; do not disable required tags or conformance features to improve the comparison. |
| Java preparation/import/validation | The optional renderer stages resources/fonts and imports the worker PDF before normal EA-PDF processing. | Separate Java staging, worker wall time, PDF import/postprocessing and standalone validation. End-to-end time alone cannot identify the renderer's internal bottleneck. |

Upstream [performance guidance](https://doc.courtbouillon.org/weasyprint/v70.0/common_use_cases.html#improve-rendering-speed-and-memory-use) identifies CSS cascade, multi-page tables and image/font optimization trade-offs. It does not support a universal explanation that Python is the cause of slower rendering.

## Reproduction and phase profiling

Use the exact locked environment described in [setup](WEASYPRINT.md). Keep profiling code, instrumented JARs, extracted inputs and build output under task-owned `/tmp` paths. Preserve original real-world EML/MSG/PST bytes and record source hashes. Use generic case labels in public summaries.

An import-only diagnostic isolates one startup component without rendering private content:

```sh
/usr/bin/time -v -o /tmp/eapdf-weasy-import-wall.txt \
  /tmp/eapdf-weasy-research-venv/bin/python -I -X importtime \
  -c 'import weasyprint' 2>/tmp/eapdf-weasy-importtime.log
```

This does not measure font stylesheet registration or reproduce the sandboxed worker. Record Python, WeasyPrint, Pango, HarfBuzz/Subset and Fontconfig versions alongside it.

For representative inputs, instrument temporary copies of the worker and its Java adapter, then package those classes and that resource into a temporary creator JAR. The measured diagnostic JAR SHA-256 was `8c168a506b3d0f9c95568e1f64d356fc9e60687827f0e646ef7009f68ca64ea5`. Production sources remained unchanged. Use monotonic wall timers around imports, sandbox installation, `FontConfiguration`, HTML parse, font CSS registration, each layout call, overflow inspection, and PDF export. Within export, optionally time font subsetting, image stream generation and tag construction. Record worker count, font descriptors/unique staged files, layout count, pages, output bytes and peak RSS. Do not log message text or resource contents.

Compare both engines with identical source selection, profile, accessibility, fonts, PREMIS setting, bounded concurrency and validation policy. Run timed cases without competing build/profile work; alternate engine order, repeat runs and state cache/warm-up conditions. Retain whole-process wall/CPU/RSS measurements in addition to phase timers. Keep validator time separate and validate final PDFs with the standalone Java validator and veraPDF. Review paired sample PDFs visually; a faster output with missing content or styles is not a valid improvement.

Profiler instrumentation itself has overhead. Use uninstrumented runs for headline timings and phase profiles to explain those timings. Report medians/ranges and distinguish cold process startup from repeated work inside an existing JVM. The phase measurements above explain the observed workload; they do not predict every HTML email or native-library stack.

## Upstream maintenance and upgrade burden

Release activity was checked on 2026-11-14. The dependency in this repository is
`io.github.openhtmltopdf:openhtmltopdf-pdfbox:1.1.113-eapdf.1`, a local patched
build of the active `openhtmltopdf/openhtmltopdf` community fork. The original
`danfickle/openhtmltopdf` repository is a separate lineage; its activity should
not be used to describe the dependency now in use. See
[renderer provenance](RENDERER.md) and the [application POM](../pom.xml).

| Upstream | Verified release activity | Implication |
| --- | --- | --- |
| WeasyPrint | [70.0](https://github.com/Kozea/WeasyPrint/releases/tag/v70.0), 2026-09-08; [69.0](https://github.com/Kozea/WeasyPrint/releases/tag/v69.0), 2026-06-02; [68.1](https://github.com/Kozea/WeasyPrint/releases/tag/v68.1), 2026-02-06 | Active releases; upgrades still require rechecking rendering and the pinned private tagging/layout bridge. |
| OpenHTMLtoPDF community fork | [1.1.113](https://github.com/openhtmltopdf/openhtmltopdf/releases/tag/1.1.113), 2026-11-12; [1.1.110](https://github.com/openhtmltopdf/openhtmltopdf/releases/tag/1.1.110), 2026-11-07; [1.1.108](https://github.com/openhtmltopdf/openhtmltopdf/releases/tag/1.1.108), 2026-11-01 | Recent fixes and contributed CSS/accessibility work; release activity does not establish a PDF/A-4 roadmap or committed feature development. |

The fork's maintainers have explicitly qualified its future development. In the
[PDF/A-4 discussion on 2025-04-06](https://github.com/openhtmltopdf/openhtmltopdf/issues/108#issuecomment-2781647741),
repository member `zacharysiegel` stated that there was no financial backing or
plan for new features, recommending migration or self-implementation for PDF/A-4
while offering help with contributions. In a
[2025-09-24 explanation](https://github.com/openhtmltopdf/openhtmltopdf/issues/92#issuecomment-3330666179),
he described volunteer maintenance and recommended browser-based alternatives
for most projects needing broader HTML/CSS support.

These are dated statements, not an immutable description of November 2026
policy: subsequent releases include contributed features. They nevertheless
prevent treating frequent releases as a promise of future standards support.
This application already carries four local source patches, including PDF/A-4
and PDF/UA-2 work, which require rebasing and revalidation. See
[patch provenance](RENDERER.md).

For future standards development, WeasyPrint is the stronger candidate to
investigate, given its [built-in PDF/A-4f and PDF/UA options](https://doc.courtbouillon.org/weasyprint/v70.0/api_reference.html#weasyprint.DEFAULT_OPTIONS).
That is an engineering assessment, not a guarantee of funding, support or
roadmap delivery. It also retains the private tagging/layout bridge upgrade
burden. OpenHTMLtoPDF remains an explicitly selected, faster alternative;
its measured latency advantage does not resolve its newsletter link-preservation
limitation or the local standards patch burden. WeasyPrint preserved those
source links in the measured case, and its initialization costs offer specific
optimization targets. The subsequent user-authorized default change selects
WeasyPrint without removing OpenHTMLtoPDF or changing either version pin.

## Recommendations from the measured attribution

Prioritize experiments reducing repeated imports and redundant face/alias preparation, including cover-specific needs, while retaining required font styles. These are expected opportunities, not implemented or measured speed improvements. Long-lived workers or shared caches change isolation, cleanup and resource ownership and need a separate design decision. In particular, [#2986](https://github.com/Kozea/WeasyPrint/issues/2986) reports a Fontconfig lifetime leak with WeasyPrint 70.0 and Pango below 1.54; it was closed without a downstream workaround. This host's Pango 1.52.1 falls in that reported range. Process-per-job operation bounds accumulation; pooling must not assume the same lifecycle behavior.

If images dominate, the documented [job-local disk cache](https://doc.courtbouillon.org/weasyprint/v70.0/api_reference.html#weasyprint.DEFAULT_OPTIONS) is a candidate RSS experiment with disk and cleanup bounds. If layout dominates, reproduce the relevant upstream case before changing authored CSS. Any optimization must preserve final EA-PDF/PDF/A validation, semantic tags, Unicode, links and visual content.
