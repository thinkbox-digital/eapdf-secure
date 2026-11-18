# WeasyPrint renderer

Current source builds default to pinned WeasyPrint 70.0. OpenHTMLtoPDF remains
available by explicitly setting `rendering.engine` to `openhtmltopdf`.
Java still builds the final EA-PDF metadata, document parts, attachment relationships,
collection structure, signatures and embedded search indexes. Authored HTML attachment
directives are disabled so email markup cannot bypass the selected source/isolation
profile. Existing CLI arguments
are unchanged. Results and recommendations are in [renderer benchmarks](RENDERER_BENCHMARKS.md).
Measured startup, font preparation and concurrency costs are in the
[performance analysis](RENDERER_PERFORMANCE_ANALYSIS.md).

## Use the single creator JAR

The Linux creator JARs include Python, WeasyPrint, Pango, HarfBuzz, FreeType,
fontconfig, libseccomp and matching standard font styles. End users download
one versioned creator JAR and run it with Java. No Python, pip, WeasyPrint or
native font installation is needed.

First conversion extracts the included renderer into
`~/.cache/eapdf/weasyprint/<version>-<archive-sha256>/`. The creator verifies
the archive and every file, restores executable permissions and publishes the
cache atomically under a process lock. Directories are private (0700), executable
files 0700 and data files 0600. A new process checks the cache before reusing it;
corrupted files are replaced from the JAR. Subsequent emails reuse the same copy.
The JAR works independently of its current working directory and needs writable
cache space. No renderer downloads occur.

The current test runtime is Linux x86_64, built on Ubuntu 24.04 with glibc 2.39.
It requires glibc 2.39 or later and kernel Landlock ABI 3 or later. Java 21 is
still the application prerequisite. Windows/macOS must select the retained Java
renderer explicitly; a native WeasyPrint bundle for those systems is not supplied.
No renderer or Python package is downloaded during conversion.

Explicit `rendering.pythonExecutable` and `EAPDF_WEASYPRINT_PYTHON` values remain
developer overrides and take precedence. With neither set, the creator checks
the embedded runtime first, a matching sidecar runtime beside the JAR second,
and the project development venv last. A present but
stale bundle fails with an explanation; its manifest must match the JAR's worker
source and pinned WeasyPrint version. All existing kernel restrictions remain.

## Maintainer runtime builds

Build-time Python and native libraries are maintainer requirements only. The
runtime is frozen with pinned PyInstaller using the package's own Python and
native libraries; end-user machines do not supply them. From the project root:

```sh
python3 -m venv /tmp/eapdf-runtime-builder
/tmp/eapdf-runtime-builder/bin/python -I -m pip --isolated install -r requirements-weasyprint-build.lock
/tmp/eapdf-runtime-builder/bin/python -I scripts/package_weasyprint.py
python3 scripts/check_bundled_renderer.py --runtime dist/runtime/weasyprint/linux-x86_64 --output /tmp/eapdf-runtime-check
# Build Java artifacts in a temporary directory, then embed before distribution.
mvn -Deapdf.build.directory=/tmp/eapdf-java-build package
python3 scripts/embed_weasyprint_runtime.py --runtime dist/runtime/weasyprint/linux-x86_64 /tmp/eapdf-java-build/emailtopdfa-2.2.0-beta.3.jar /tmp/eapdf-java-build/emailtopdfa-2.2.0-beta.3-gui.jar
```

The check uses a clean Ubuntu 24.04 container without Python or Pango installed,
an empty executable search path and disabled networking. It verifies that the
worker's Landlock and seccomp restrictions are active. Docker is a verification
tool and is not needed by end users. Staged files are always removed from the
shared `.generator_web_tmp` directory.

The bundle records runtime versions, native package provenance, copyright texts,
font licenses and SHA-256 checksums. PyInstaller's documented native-library and
runtime behavior is described in its
[usage guide](https://pyinstaller.org/en/stable/usage.html) and
[runtime documentation](https://pyinstaller.org/en/stable/runtime-information.html).

## Developer Python environment

The integration requires **WeasyPrint 70.0** and the exact Python dependencies in
[requirements-weasyprint.lock](../requirements-weasyprint.lock). Version 70 is the
upstream security release that fixes unsafe EPS-image handling and resource-fetcher
filtering. Earlier versions are rejected, even when they offer PDF/A-4f output.
See the [70.0 release](https://github.com/Kozea/WeasyPrint/releases/tag/v70.0) and
[upstream security advisory](https://github.com/Kozea/WeasyPrint/security/advisories/GHSA-r543-q48m-4c9j).

This project's isolated worker supports Linux x86_64 and aarch64 with Landlock ABI 3+
and `libseccomp.so.2`. The measured environment is Linux x86_64 with Python 3.12,
Landlock ABI 8, Pango, HarfBuzz and fontconfig. Other architectures, unavailable
kernel restrictions, missing libraries and version mismatches stop conversion.
There is no unsandboxed fallback. Windows and macOS must explicitly select
`rendering.engine: "openhtmltopdf"` to use the retained Java renderer.

Upstream requires Python 3.10+ and Pango 1.44+. Install your distribution's Python
venv, Pango, PangoFT2, HarfBuzz, HarfBuzz-subset, fontconfig and libseccomp packages.
The [upstream installation guide](https://doc.courtbouillon.org/weasyprint/v70.0/first_steps.html#installation)
provides distribution-specific native package names. Then, from the project root:

```sh
python3 -m venv .venv-weasyprint
.venv-weasyprint/bin/python -I -m pip --isolated install -r requirements-weasyprint.lock
.venv-weasyprint/bin/python -I -m pip --isolated check
.venv-weasyprint/bin/python -I -m weasyprint --version
```

The optional project-local development environment is private and ignored. Launch from the project
root to discover it automatically, or set an absolute Python path as described
below when launching from another directory. `-I` prevents inherited Python paths and user-site
packages from entering the worker. Isolated pip settings prevent an unrelated
user configuration from redirecting the installation. The Java launcher always
uses Python isolated mode and a cleared environment.

## Select and bound rendering

Merge the following into a current configuration, replacing the creator with
your own value. A blank Python setting enables the documented runtime lookup:

```json
{
  "configVersion": 1,
  "creator": "Your archive organization",
  "accessibility": { "addPdfUaFeatures": true },
  "rendering": {
    "engine": "weasyprint",
    "pythonExecutable": "",
    "timeoutSeconds": 120,
    "maxProcesses": 2,
    "memoryMiB": 512,
    "maxOutputMiB": 128
  }
}
```

`engine` accepts `weasyprint` (the default) or `openhtmltopdf`. Explicitly selecting
OpenHTMLtoPDF does not require Python. When selecting WeasyPrint, the executable
is resolved in this order:

1. Nonblank `rendering.pythonExecutable` in the JSON configuration.
2. Nonblank `EAPDF_WEASYPRINT_PYTHON` environment variable.
3. Included JAR runtime, verified and extracted into the private cache.
4. A matching `runtime/weasyprint/<platform>/` beside the JAR (maintainer option).
5. `.venv-weasyprint/bin/python` under the current working directory (development).

Configured and environment paths must be absolute executable paths. An invalid
explicit path is an error; it does not fall through to another path. Only
resources already included in the JAR are extracted automatically. A missing
runtime never selects OpenHTMLtoPDF.
The remaining values shown are the defaults. Accepted ranges are timeout 1–3600
seconds, processes 1–16, Python address space 128–8192 MiB, and rendered intermediate
PDF size 1–1024 MiB. These limits do not increase the creator's other processing
allowances. The worker caps pages at 10,000 and individual fetched resources at
the smaller of the creator's message-byte allowance and one eighth of its Python
address-space limit. Current creator builds have no PREMIS generator or `premis`
configuration key.

Save the configuration as `/tmp/eapdf-weasy-config.json`. For example:

```sh
EAPDF_CONFIG=/tmp/eapdf-weasy-config.json java -Xmx1g -jar dist/emailtopdfa-2.2.0-beta.3.jar \
  --jobs 2 --out /tmp/eapdf-weasy-documents \
  --archive-profile c --archive-output /tmp/eapdf-weasy-documents/eml_archive_1c.pdf \
  /path/to/email.eml
```

Use a JAR built from the current source when testing this unreleased default
change. The existing archive and isolated-source rules still apply. Keep original
inputs and examine warnings and standalone Java validation reports before accepting
an output.

## Resource isolation and failure behavior

Java gives each render a private directory containing XHTML, a manifest, copied
fonts and explicitly approved asset bytes. The worker's URL fetcher accepts only
these exact resource aliases and bounded data URIs. Arbitrary file and network
URLs are denied, including references nested inside SVG. Staged raster responses
use synthetic URLs so WeasyPrint's lazy image handling cannot reopen an original
file path outside the render directory.

Before parsing email HTML or fonts, the worker installs a Landlock filesystem
allowlist and a seccomp syscall filter. Runtime dependencies, font directories and
font configuration are read-only; the render directory is writable. Source-mail
folders, home directories and arbitrary filesystem paths are absent. Socket
creation, networking, executable launches, process creation, cross-process signals
and remote-process access are blocked. Pango
can create native threads after confinement; they inherit both guards. A process
that already has extra threads before confinement is rejected.

Java enforces the wall deadline, interruption, process cleanup and diagnostic-size
bounds. The worker additionally sets CPU, address-space, file-size and core-dump
limits. A fair semaphore limits simultaneous renders in a JVM; supervised jobs
share file-lock slots across child JVMs. Memory admission includes the Python
allowance and remaining potential Java-heap growth against cgroup-aware available
memory. Java samples Python RSS and the worker reports peak RSS separately from
Java heap. Budget both runtimes when choosing `-Xmx` and `maxProcesses`; the Python
allowance is not included in the Java heap setting. Inline resource sizes are
checked before base64 or percent decoding allocates the output buffer.

Exceptions, timeout, resource-limit failures, unavailable sandboxing and WeasyPrint
ERROR diagnostics stop conversion. They cannot silently select OpenHTMLtoPDF or
plain text. The existing Java transaction controls publication; failed renders
leave no partially accepted PDF. Render directories are removed after success and
failure. Review non-fatal CSS/font warnings: a completed render can still differ
from the author's intended appearance.

## Tags, fonts and layout

The worker requests tagged PDF/A-4f output with an sRGB output intent. The final
EA-PDF assembly remains in Java. The pinned API calls are described in the
[WeasyPrint 70 API](https://doc.courtbouillon.org/weasyprint/v70.0/api_reference.html).
A PDF variant setting alone does not establish conformance; validate the final
assembled document.

WeasyPrint normally omits the header-identity attributes used by the creator's
EA-PDF retagging. A small, version-checked bridge preserves those markers on
WeasyPrint's native table-row/cell structure elements. Java then creates
`Mail_Field`, `Mail_FieldName` and `Mail_FieldValue` roles within its existing
message/content hierarchy. Required rendered header identities are checked before
the worker reports success. The importer also accepts WeasyPrint's array-valued
structure root while retaining MCIDs, links and parent relationships.

The worker also applies a job-local compatibility correction for WeasyPrint 70's
inline-style specificity: inline declarations retain their priority over ID-based
selectors, while origin and importance rules remain in effect. Installed Python
package files are not changed. The broader CSS cascade is not replaced.

Pagination stays in WeasyPrint. A normal-priority user stylesheet sets
`box-decoration-break: clone` for source-email table cells, allowing the engine
to reserve padding and borders while splitting cells across pages. Retained
author declarations and explicit page-break avoidance keep their priority;
there are no email-specific positions or paragraph-placement rules. The existing
Java CSS frontend still warns and omits unsupported stylesheet properties,
including stylesheet `box-decoration-break`; an inline source declaration is
retained. This setting does not replace that frontend or promise complete CSS
support.

Unavailable images with nonblank alternative text use a small centered image
pictogram on a transparent SVG canvas, rather than a full-size crossed box.
The original image element keeps its authored dimensions, CSS, link and exact
alternative text in the PDF Figure tag; the visible fallback label is replaced
by the placeholder. Known numeric image dimensions also set the placeholder's
intrinsic aspect ratio, so `height:auto` does not turn wide graphics into squares.
Unknown dimensions use a small bounded default. Empty-alt decorative images keep
the previous omission behavior.
The marker is at most 12 intrinsic units wide/high; with matching authored image
dimensions this is at most 12 CSS pixels. Source CSS that scales an image can
also scale its marker. The surrounding source-sized area and link box remain.
No remote graphics are fetched or recreated. Conversion warnings identify this
fallback, and source email bytes remain unchanged.

The tag bridge, inline-style correction and wide-layout inspection use private
WeasyPrint APIs. Upgrades require
new tag, link, font, text-extraction and final-validation evidence. They must not be
made by loosening the exact version check. Existing Unicode font selection is
reused and approved fonts are copied into the render directory. Real bold, italic
and bold-italic faces are discovered from matching sibling files or a finite set
of trusted Linux font locations. The font family and OpenType weight/style metadata
must match; a filename alone cannot turn regular outlines into bold text.

For the default Latin families, install your distribution's Liberation and DejaVu
font packages (commonly `fonts-liberation` or `fonts-liberation2`, and
`fonts-dejavu-core`). Custom static font families should provide matching
`Family-Regular.ttf`, `Family-Bold.ttf`, `Family-Italic.ttf` and
`Family-BoldItalic.ttf` siblings; oblique variants are also recognized. Discovery
supports static weight classes 100–900 in steps of 100. WeasyPrint does not preserve
synthetic bold/italic from a lone regular face in this integration. A requested
primary style without a matching staged face is an explicit failure. Some fallback
families, including the bundled Droid fallback, only supply regular outlines;
review multilingual styled text and use a fallback family with real matching
faces when needed. Font-package versions can change metrics and pagination.

Tables wider than
the page trigger one bounded width adjustment; excessive or residual overflow is
an explicit failure. Different layout engines can change pagination, line breaks,
shaping and font metrics. Pixel identity is not promised.

## Check an installation

The optional Python regressions exercise the actual pinned runtime and kernel
guards rather than mocks:

```sh
EAPDF_WEASYPRINT_PYTHON=/absolute/path/to/eapdf/.venv-weasyprint/bin/python \
  python3 -m unittest discover -s regression -p 'test_weasyprint_*.py' -v
```

They cover direct filesystem/network/process denial, inherited native-thread
restrictions, staged assets and symlink escapes, nested SVG, authored attachment
directives, wide layout, Python
isolation, memory/page limits and RSS reporting. An unconfigured optional runtime
is explicitly skipped. The broader Java and benchmark checks cover final EA-PDF
assembly and validation; see [renderer benchmarks](RENDERER_BENCHMARKS.md) for the
measured corpus, limitations and renderer recommendation.


See the dated [upstream issue and stable-documentation review](WEASYPRINT_UPSTREAM_ISSUES.md)
for performance, tagging, font and layout risks beyond the measured acceptance corpus.
The stable documentation currently resolves to 70.0; upgrades still require a new
pin and fresh output validation.
