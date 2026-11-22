# EA-PDF Secure

**Your email—complete, authentic, accessible, structured, secure.**

[EA-PDF](docs/reference/EA-PDF-v1.pdf) is a specification for archiving email in PDF/A. It supports individual messages, combined archives and collections, with attachments, email metadata and optional original source messages.

Turn saved emails and mailboxes into PDFs you can read, search and archive.
Supports **EML, MSG, PST, MBOX and Gmail**.

[User guide](docs/manual/user-manual.html) · [PDF manual](docs/EA-PDF_User_Manual.pdf) · [Downloads](https://github.com/thinkbox-digital/eapdf-secure/releases/tag/v2.3.2)

**Secure 2.3.2** includes the creator, conformance validator, security validator
and Full reader 1.33.0 for Chromium and Firefox.
Java applications need **Java 21 or later**. The readers run locally in your browser.

The creator defaults to WeasyPrint 70.0 and
embeds its pinned Python runtime, native font libraries and standard fonts in
the Linux creator JARs. First use verifies and extracts the renderer into a private
versioned cache; later conversions reuse it. Users need Java, with no Python or
WeasyPrint installation. Supported Linux sandbox facilities are required.
Local distribution uses the versioned standalone JARs and checksums; a separate
Linux application ZIP is no longer produced.
See [renderer setup](docs/WEASYPRINT.md). OpenHTMLtoPDF remains available by
explicitly setting `rendering.engine` to `openhtmltopdf`, including on Windows
and macOS.

Accessibility and embedded searchable indexes are off by default. They can be enabled explicitly;
ordinary viewer search remains available.
Gmail exports use bounded parallel conversion/rendering across all six profiles.
The creator keeps completed/total message counts visible through conversion,
signing and saving. Gmail label names and IDs are case sensitive.

Optional C2PA export provenance, timestamped
signing and PAdES B-LTA protect the generated outer EA-PDF while preserving embedded
sources. The conformance validator window checks EA-PDF/PDF/A only. A separate
local Security Validator app checks C2PA and PAdES; see [running the apps](validator-desktop/README.md#run).
Command-line Java validation retains separate security results. See
[settings, signing order and renewal](docs/DIGITAL_SIGNATURES.md). Browser checks
on file open are available in Full, with explicit-root offline trust, revocation
and trusted-timestamp checks.

## Editions

- [EA-PDF Main](https://github.com/thinkbox-digital/eapdf) — The full creator, validator and browser readers.
- [EA-PDF Lite](https://github.com/thinkbox-digital/eapdf-lite) — An EML command-line creator, Java validation and the Even More Lite reader.
- **EA-PDF Secure** — The experimental C2PA provenance and PAdES signing and verification edition.

## Download

Read local PDFs and EA-PDF archives in the standalone Linux viewer. See the
[desktop viewer guide](desktop-viewer/README.md) for release packages and launch instructions.

| Tool | What it does |
| --- | --- |
| [Creator](https://github.com/thinkbox-digital/eapdf-secure/releases/download/v2.3.2/emailtopdfa-2.3.2-gui.jar) | Convert email using a desktop window. |
| [Command-line creator](https://github.com/thinkbox-digital/eapdf-secure/releases/download/v2.3.2/emailtopdfa-2.3.2.jar) | Convert email from a terminal or script. |
| [Validator](https://github.com/thinkbox-digital/eapdf-secure/releases/download/v2.3.2/eapdf-validator-2.3.2.jar) | Check the finished PDFs and save a report. |
| [Security Validator](https://github.com/thinkbox-digital/eapdf-secure/releases/download/v2.3.2/eapdf-security-validator-2.3.2.jar) | Verify C2PA provenance, PDF signatures, timestamps and PAdES archival protection. |
| [Desktop viewer](https://github.com/thinkbox-digital/eapdf-secure/releases/download/v2.3.2/eapdf-desktop-viewer-secure-0.8.0.jar) | Read EA-PDF archives and PDF Portfolios in a Java 21+ desktop app. |
| Full reader: [Chromium](https://github.com/thinkbox-digital/eapdf-secure/releases/download/v2.3.2/eapdf-viewer-1.33.0-chromium.zip) / [Firefox](https://github.com/thinkbox-digital/eapdf-secure/releases/download/v2.3.2/eapdf-viewer-1.33.0-firefox.zip) | Read messages, folders and attachments; includes Conversations, Portfolio view and larger-archive support. |

The [release page](https://github.com/thinkbox-digital/eapdf-secure/releases/tag/v2.3.2)
also has the configuration ZIP, PDF manual and SHA-256 checksums.
Browser extensions are unsigned; Firefox installation is temporary.

## Get started

1. Download the Creator JAR and extract the configuration ZIP beside it.
2. Start the Creator:

   ```sh
   java -jar emailtopdfa-2.3.2-gui.jar
   ```

3. Choose your email files, output folder and format, then convert.
4. Open the finished PDF in the Validator and read its report.
5. Use either reader to browse messages and attachments. See [installation](browser-viewer/README.md).

Keep the original emails and mailboxes. A PDF does not replace every property of
the original format. Validation can find problems; it does not prove that every
aspect of the email or its appearance was preserved.

## Choose an output

| Format | Result |
| --- | --- |
| **1s** | One PDF per message, with its source and attachments. |
| **1m** | Two or more messages in one continuous PDF. |
| **1c** | A collection containing separately openable message PDFs. |
| **1si / 1mi / 1ci** | The same layouts with original sources and native attachment contents omitted. |

The isolated formats do **not** anonymize or redact email. Visible text, addresses,
metadata and inline images can remain. For PST, MBOX and Gmail, retained sources
are exported messages; keep the original mailbox separately.

All formats use PDF 2.0 / PDF/A-4f. See the [format guide](docs/manual/user-manual.html#basics)
for details and [examples](examples/) for sample files.

## From the command line

```sh
java -Xmx2g -jar emailtopdfa-2.3.2.jar --jobs 2 --out /tmp/eapdf-output message.eml
```

For a collection, add `--archive-profile c --archive-output /tmp/eapdf-output/archive_1c.pdf`.
Use `m`, `mi` or `ci` for the other archive formats. See
[conversion instructions](docs/CONVERSION_OPERATIONS.md), [Gmail setup](docs/GMAIL.md)
and [configuration](config/README.md).

## Reload the local reader

In Chromium, choose **Load unpacked** once for
`browser-viewer/local-install/full/chromium` at the new checkout location.
From `browser-viewer`, `npm run build:local` refreshes that same folder. Use
**Reload** on the extension card and reopen the viewer tab. The Firefox build
is in `browser-viewer/local-install/full/firefox`.

## Build and contribute

Use Java 21 and Maven. The source checkout includes the pinned PDF security
dependency and its Maven metadata for both Java builds.

```sh
mvn -Deapdf.build.directory=/tmp/eapdf-build verify
```

Reader builds need Node.js; see the [reader guide](browser-viewer/README.md#build).

[Tests](https://github.com/thinkbox-digital/eapdf-secure/actions/workflows/quality.yml) ·
[Contribute](CONTRIBUTING.md) · [Project status](docs/PROJECT_STATUS.md) ·
[Code layout](docs/ARCHITECTURE.md) · [Architecture diagram (PDF)](docs/EA-PDF_Software_Architecture.pdf) · [Report a security problem](SECURITY.md) ·
[Library packages](docs/PACKAGES.md) · [Privacy](docs/site/privacy.md)

## Regenerate local outputs

Maintainers can rebuild local samples with `scripts/eapdf-regenerate-experimental sample`.
This experimental command requires C2PA and PAdES B-LTA using the current local
signing configuration. The main clone keeps `eapdf-regenerate`.
The `everything` option processes all local inputs. These commands create files
under `out/` and keep logs separately. Read the
[regeneration instructions](docs/CONVERSION_OPERATIONS.md#regenerating-local-outputs)
before running them.

## Release package acceptance

Run the same acceptance check locally with the candidate JARs and viewer ZIPs.
Use JDK 21, Python 3.12 with `pikepdf`, and the browser viewer's Node dependencies
and Playwright Chromium. For Firefox set `FIREFOX_PATH` and `GECKODRIVER_PATH`
if the executables are outside PATH. For the desktop JAR use a graphical session
or `xvfb-run` and set `JAVAFX_LIB` when JavaFX is installed separately.

```sh
python3 -B scripts/check_release_packages.py \
  --creator "$CREATOR_JAR" --validator "$VALIDATOR_JAR" \
  --viewer "$CHROMIUM_ZIP" --viewer "$FIREFOX_ZIP" \
  --desktop-viewer "$DESKTOP_JAR" --output "$(mktemp -d)/acceptance"
```

Pass each browser or web ZIP with a separate `--viewer` argument. The gate
generates all six profiles from dedicated EML fixtures, checks PDF structure
and payloads, requires standalone EA-PDF and veraPDF PDF/A-4f success, rejects
missing and invalid collection children, and opens the same PDFs in the
package readers. Successful checks write `SHA256SUMS` beside the logs and reports.

For a GitHub release, dispatch **Release package acceptance** on the
`v<VERSION>` tag with **publish** enabled. Its publish job uploads the
verified packages after rechecking their hashes.

## License

[Apache License 2.0](LICENSE). Bundled libraries have their own licenses;
see [notices](THIRD_PARTY_NOTICES.md) and the [dependency list](docs/security/runtime-inventory/DEPENDENCY_LICENSES.md).
