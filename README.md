# EA-PDF Secure

**Your email—complete, authentic, accessible, structured, secure.**

EA-PDF is a specification for archiving email in PDF/A. It supports individual messages, combined archives and collections, with attachments, email metadata and optional original source messages.

Turn saved emails and mailboxes into PDFs you can read, search and archive. Supports EML, MSG, PST, MBOX and Gmail.

[User manual](docs/manual/user-manual.html) · [PDF manual](docs/EA-PDF_User_Manual.pdf) · [Release downloads](https://github.com/thinkbox-digital/eapdf-secure/releases/tag/v2.3.2)

## Download

| App | Use it to |
| --- | --- |
| [Desktop Viewer](https://github.com/thinkbox-digital/eapdf-secure/releases/download/v2.3.2/eapdf-desktop-viewer-secure-0.8.0.jar) | Open PDFs, browse email and attachments, and save files. |
| [Creator](https://github.com/thinkbox-digital/eapdf-secure/releases/download/v2.3.2/emailtopdfa-2.3.2-gui.jar) | Convert email to PDF in a desktop window. |
| [Validator](https://github.com/thinkbox-digital/eapdf-secure/releases/download/v2.3.2/eapdf-validator-2.3.2.jar) | Check a PDF and save a report. |
| [Security Validator](https://github.com/thinkbox-digital/eapdf-secure/releases/download/v2.3.2/eapdf-security-validator-2.3.2.jar) | Review signatures, timestamps and provenance. |
| Full browser reader for [Chromium](https://github.com/thinkbox-digital/eapdf-secure/releases/download/v2.3.2/eapdf-viewer-1.33.0-chromium.zip) or [Firefox](https://github.com/thinkbox-digital/eapdf-secure/releases/download/v2.3.2/eapdf-viewer-1.33.0-firefox.zip) | Read PDFs in your browser. |

## Get started

Install a desktop version of Java 21 or later. Double-click the downloaded JAR to open the Creator, Desktop Viewer, and Validator. If an archive app opens, use **Open with** and choose Java.

Open the Creator, choose an email file or mailbox, and select where to save your PDFs. Use the Security Validator to review signatures and provenance.

To read a PDF, double-click the Desktop Viewer JAR, then choose **Open PDF…** or drag the PDF into the window. To check it, open the Validator and select the PDF.

The Secure Creator includes a Linux x86-64 renderer. See the user manual for platform details.

## Choose an Output

| Format | Result |
| --- | --- |
| **1s** | One PDF per message, with its source and attachments. |
| **1m** | Two or more messages in one continuous PDF. |
| **1c** | A collection containing separately openable message PDFs. |
| **1si / 1mi / 1ci** | The same layouts with original sources and native attachment contents omitted. |

Visible text, addresses, metadata and inline images can remain in the isolated formats. Review the PDF before sharing it.
For PST, MBOX and Gmail, retained sources are exported messages. Keep the original mailbox separately.

All formats use PDF 2.0 / PDF/A-4f. See the [format guide](docs/manual/user-manual.html#basics) and [examples](examples/).

Other editions are [Main](https://github.com/thinkbox-digital/eapdf), [Lite](https://github.com/thinkbox-digital/eapdf-lite). Developers can use the [technical guide](TECHNICAL_GUIDE.md).

[Apache License 2.0](LICENSE)
