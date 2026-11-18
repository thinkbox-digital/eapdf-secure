# Signing EA-PDF output

Unreleased integration, 17 November 2026. Protection is optional and off by
default. It applies to the generated outer EA-PDF, including embedded bytes.
Original emails and attachments stay byte-for-byte unchanged; generated collection
children are not signed separately. An extracted child needs its own protection
if independent verification after extraction is required. No visible stamp is added.
Export provenance does not authenticate email senders or establish completeness.

## Settings

In the creator GUI, open **Signing settings...**, select provenance and/or a
protection level, supply certificate and policy settings, then enable **Sign output**
for certificate signing. Conversion requests the password unless a passphrase file
is selected. Local C2PA alone does
not need a PKCS#12 file or **Sign output**.

Use **Signing / trust JSON → Browse…** to select a combined signing JSON. This
prefills the certificate, passphrase-file path, protection, timestamp service and
trust/revocation evidence. Relative paths resolve beside the selected JSON. The
creator retains known values in memory and its private run configuration; it does
not retain or bundle the selected JSON filename, search the home directory or
open the private key/passphrase during import. The referenced key, passphrase and
evidence files must remain available at conversion time.

The Protection choices are:

- **Sign the PDF (CMS_AND_PDF):** a PDF signature without a trusted timestamp.
- **Sign with a trusted timestamp (CMS_AND_PDF_TIMESTAMPED):** adds independent time evidence.
- **Sign for long-term preservation (PADES_B_LTA):** includes C2PA provenance, timestamps and revocation evidence for archival verification and later renewal.

Equivalent settings within an existing `EAPDF_CONFIG` JSON file:

```json
"signing": {
  "enabled": true,
  "pkcs12Path": "/tmp/eapdf-security/signer.p12",
  "passphraseFile": "/tmp/eapdf-security/signer.passphrase",
  "alias": "",
  "reason": "Archive export",
  "c2pa": true,
  "protection": "PADES_B_LTA",
  "policyPath": "/tmp/eapdf-security/policy.json",
  "tsaUrl": "http://timestamp.digicert.com",
  "tsaPolicyOid": ""
}
```

These are placeholder file paths. Supply your own credential,
independently selected trust anchors, current revocation evidence and RFC 3161
service. Timestamped protection defaults to `http://timestamp.digicert.com` when
`tsaUrl` is blank; the GUI fills it when selecting either timestamped level. An
explicit custom URL is retained. Ordinary CMS does not contact a TSA.

Existing conversion arguments are unchanged. Select an optional UTF-8
`passphraseFile` (maximum 4096 bytes; final CR/LF characters are removed), use the
GUI password prompt, or set `EAPDF_SIGNING_PASSWORD`. An explicit GUI/environment
password overrides the file, including an empty password. Only the file's path
belongs in JSON. Never put the password itself in JSON,
logs or command arguments. The GUI clears its password buffer and removes its
temporary run configuration. Privileged local processes can read environment secrets.

| Protection | Result and requirements |
| --- | --- |
| `CMS_AND_PDF` | Detached CMS PDF signature. Default legacy signing remains available without C2PA or a policy. Clock time alone is not trusted time. Leave `tsaUrl` empty. |
| `CMS_AND_PDF_TIMESTAMPED` | CMS plus a verified RFC 3161 signature timestamp. Uses DigiCert by default and requires independent TSA trust evidence. |
| `PADES_B_LTA` | EU DSS archival augmentation, retained validation evidence and document timestamps. Uses DigiCert by default; requires verified signer/TSA trust and fresh revocation evidence. |

`c2pa` is independent of the protection level. With signing disabled it uses a
local ephemeral certificate: binding and cryptographic integrity are checkable,
but identity and trusted time remain unknown. Configured C2PA signing certificates
must meet the library's C2PA credential requirements. PKCS#12 signing checks alias,
dates, key usage, key/certificate matching and supported RSA/EC strength. Hardware
tokens and remote signing are not integrated.

A policy contains local evidence paths relative to that policy file:

```json
{
  "trustCertificates": ["root.pem"],
  "supportingCertificates": ["intermediate.pem"],
  "crls": ["issuer.crl"],
  "ocsps": []
}
```

Certificates and CRLs accept PEM/DER; OCSP files contain encoded responses.
The trust-policy field also accepts the companion project's combined signing
JSON. Its known `protection`, `pkcs12`, `passphraseFile`, `alias`, `verapdf`,
`tsaUrl` and `tsaPolicyOid` fields are ignored by the trust reader. The GUI's
explicit JSON import uses those fields to prefill signing settings as well. Unknown properties
still fail with their names. The Java validator uses its built-in veraPDF engine.
The trust reader never opens credentials or passphrase files.

Ready-to-copy layouts are in
[creator settings](../config/signing-digicert.example.json) and
[trust evidence](../config/signing-policy.example.json). Replace the placeholder
paths and retain real trust/revocation evidence. Machine-specific `config/*.local.json`
files are ignored by Git.

Optional `verificationTime` is an ISO instant for reproducible historical checks;
ordinary verification uses current time. The document never selects trust anchors.
Verification stays offline, without fetching AIA, CRL or OCSP URLs. Creation
contacts the configured TSA with its digest request, not PDF or correspondence.
Failed requested protection, missing evidence or TSA failure stops publication.

## Required signing order

1. Finish content, repairs, preservation checks and optional indexing. Add C2PA
   reservations and policy through catalog AF associations, outside collection
   membership and email attachment lists.
2. For CMS, reserve the final PDF signature layout and byte ranges, complete C2PA
   with the required exclusions, then sign. CMS covers the completed C2PA bytes.
3. For B-LTA, the shared coordinator creates the baseline signature and signature
   timestamp, adds DSS evidence and archival timestamps, then appends its final
   C2PA and document-timestamp reservations. It completes C2PA before filling the
   final document timestamp. Do not append C2PA independently after that timestamp.
4. Verify requested protection and preservation before publishing the staged
   result. A failure leaves an existing destination unchanged.

`digital.thinkbox:pdf-security` owns this order. Its companion-library update adds
`C2paProfile.associatedFilesOnly()`, preserves existing catalog AF associations,
adds the policy modification date and accepts a generic export reason. Existing
profiles and five-argument options remain supported. Build/install the updated
companion module and parent before building EA-PDF; the original consumer ZIP
predates these hooks.

The assertion binds XMP and a SHA-256 inventory of preserved files. This describes
the exported container without modifying or separately signing source files.
The EA-PDF specification's C2PA note permits associated security files; they are
not new collection members. Exact bounded metadata is recognized during creation.

Default CMS retains its signature-compatible full-text index. C2PA and archival
revisions can invalidate that index's exact file binding; the viewer then uses
ordinary search. The binding rules are not relaxed.

## Verification and renewal

The standalone Java validator adds a separate `security` object and
`summary.security_status` alongside conformance results. Its window shows the
status and an **Export security** details tab. Select an independently chosen
policy in the window's **Trust JSON (optional)** field before validation, or use
`EAPDF_SECURITY_POLICY` or `-Deapdf.security.policy=...`. The validator reads only
public trust evidence from combined profiles; it never opens the signing key or
passphrase file. Optionally require a level
with `EAPDF_SECURITY_REQUIRED_PROTECTION` or `-Deapdf.security.requiredProtection=...`.
These settings add no CLI arguments.

Checks distinguish integrity, requested protection, trust, revocation, timestamps
and C2PA binding. `UNKNOWN` is not a trust pass. Local C2PA and missing evidence
retain unknown conclusions; a valid PDF signature does not automatically establish
C2PA signer trust. Failed security, verification errors or an unmet requested level
produce exit 2 even when conformance passes. Unsigned documents remain conformance
inputs without a required level. Python validation does not assess this security.

The public creator API renews verified B-LTA using configured `PADES_B_LTA`
settings and the password environment variable:

```java
eapdf.EaPdfSecurity.renew(
    java.nio.file.Path.of("/tmp/archive_1c.pdf"),
    java.nio.file.Path.of("/tmp/archive-renewed_1c.pdf"));
```

Renewal requires a new destination. It appends evidence, refreshes C2PA when present
and seals the result with a final document timestamp. Input bytes remain unchanged
and form a prefix of the result. Renew while evidence and algorithms still support
verification; renewal is not scheduled automatically. This is not qualified-signature
or certified PAdES status. No production key or certificate is supplied.

## Verification evidence

Verified 17 November 2026: all six EA-PDF profiles across all three protection
levels pass creation/preservation checks and standalone Java EA-PDF/PDF/A-4f
validation (18 combinations). A renewed collection also passes, retaining the
input byte prefix. Fresh real-source EML and PST conversions with C2PA/B-LTA pass
the packaged validator and retain their source hashes. Tests cover tampering,
TSA failure without publication, local untrusted credentials, security metadata
ambiguity and output permissions. Packaged negative controls retain conformance
PASS while correctly reporting security FAIL and exit 2 for a modified signed byte
and for an unsigned document when B-LTA is required. The full creator suite passes 510 tests
(three optional skips); the full validator run passes 303 (one optional skip),
followed by 88 focused final checks including the additional security regression.
The companion library passes 20 tests. Production TSA credentials and independent
C2PA-tool interoperability have not been exercised; test trust material is synthetic.

## Browser security on open

These checks belong to the retained experimental Full viewer, named
**EA-PDF Viewer Experimental**. Its Chromium identity is separate from the regular
published viewer. Install the experimental checkout's
`browser-viewer/local-install/full/chromium` directory with **Load unpacked** once;
later local builds keep that path and identity for **Reload**. The regular viewer
can remain installed alongside it.

The report begins with guidance to use the standalone EA-PDF Validator with an
independently selected trust JSON for full verification, including revocation and
trusted timestamps. Browser UNKNOWN for these checks does not establish that
CRLs or timestamps are missing. Creation uses the selected CRLs and TSA; the
browser does not load the creator's local JSON or local filesystem paths.
For browser trust checks, open the report and choose **Choose trusted roots…**.
Add public CA certificates in PEM/DER format, including from separate folders;
no private key is needed. Selection applies to this session and can be cleared.
Embedded PDF certificates are never automatically trusted.

A limited Chromium microbenchmark on 17 November 2026 parsed and verified the
signatures on two local CRLs (1,015 bytes total): 30 measured iterations,
3.6 ms median and 8.5 ms at the 95th percentile on the development machine.
This excludes full path building, OCSP, historical timestamp and archival
validation; it is not an end-to-end verification benchmark.


The Full viewer checks the opened outer PDF automatically in an offline background
worker. Its filename turns green when supported cryptographic signatures and
current-file coverage verify, and red when a check fails. Yellow identifies
unsigned or incomplete/unsupported checks. Validation never opens the report.
Click the filename, or focus it and press Enter, to open the centred modal security
report. Its larger window uses up to 960 pixels of width and 85% of the viewport
height, bounded on smaller screens.
The report can be downloaded as JSON, including the file SHA-256 and individual
checks. Closing/reopening the file cancels work and rejects stale results.
Embedded navigation retains the outer file's result; the outer protection covers
embedded bytes, and does not imply an extracted child is independently signed.
Lite and Even More Lite omit security validation and reporting.

Supported checks include SHA-256 detached CMS with RSA/RSA-PSS or ECDSA,
RFC 3161 signature/document timestamp signatures and imprints, and the Creator's
32 KiB EA-PDF C2PA profile with ES256/PS256 COSE signatures. C2PA assertion hashes
and current PDF binding are checked; arbitrary exclusion ranges are rejected.
Application metadata/inventory assertions are signed but not separately reconciled
in the browser. Historical signatures are checked over their recorded revisions;
the final signature must cover the current file. Unsupported algorithms, encrypted
files, discovery limits and the 120-second deadline cannot produce a green result.
PDF.js supplies bounded signature facts (64 signatures, 4,096 form fields,
32 levels, 1 MiB per signature); cryptographic file hashing uses 1 MiB chunks.

Green means covered-content integrity. The filename qualifies whether offline
certificate checks also passed. With independently selected CA roots, the worker
checks bounded X.509 paths, certificate validity/constraints and RSA 2048+ or
P-256/P-384/P-521 keys. It checks complete issuer-signed CRLs and authorized signed
OCSP responses, matching certificate identifiers and fresh evidence windows.
Revoked certificates fail; missing, stale, forged, unsupported or incomplete
evidence cannot produce a revocation pass. Delta/indirect/scoped CRLs are not
supported. OCSP responses without a nextUpdate remain unknown.

For trusted timestamps, both the signed imprint and the TSA path, exclusive
critical timeStamping usage and revocation must verify at the token's time.
Verified signature timestamps supply the signer certificate check time; otherwise
its current validity uses the browser clock, which is not trusted signing time.
C2PA signing certificate paths are checked separately from C2PA ecosystem trust
lists, which are not implemented. No certificate or TSA URLs are fetched.

Limits are 32 selected roots/1 MiB total, 32 entries per DSS evidence category,
1 MiB per evidence stream, 4 MiB combined evidence and eight certificates per path.
Ambiguous/excessive paths and unsupported critical extensions remain unknown.
Allowed incremental modifications, full historical evidence handling and the
PAdES B-LTA archival level still require the Java validator and independent policy.
Export protection does not authenticate original email senders or certify conformance.


## Reading the security report

Verified means the stated check passed. Not verified means evidence, independent
trust or supported browser checking is incomplete. Failed means a check detected
a problem. Information describes scope and is not a failed check. The JSON uses
PASS, UNKNOWN, FAIL and NOT_APPLICABLE for these four states. Overall PASS refers
to supported file-integrity checks; read individual trust/time results as well.

A root certificate is a CA certificate that you independently choose to trust.
**Choose trusted roots (.pem, .crt, .cer, .der)…** accepts public certificate
files. It does not need a PKCS#12 signing key or a passphrase. Add roots from
separate folders as needed; the selections accumulate for the session.
For a document signed under a local CA and timestamped by DigiCert, both the local
CA root and DigiCert's applicable root are needed. A CRL describes certificate
status; it cannot replace the root needed to establish who signed that CRL.

The table follows a report with one PDF signature and two document timestamps.
Numbers identify the order of the PDF signature/timestamp records; they are not
scores. C2PA identifies the signed provenance record.

| JSON check | What it does | Where it looks | Meaning of the result |
| --- | --- | --- | --- |
| Selected trust policy | Records the trusted CA certificates selected. | The public root files chosen in the report. | PASS confirms a selection; it does not establish every signer’s trust. |
| PDF signature 1 | Checks the PDF signature against its protected content. | The signature, certificate and signed PDF byte ranges. | PASS means the first signed version matches; later changes need separate checks. |
| Signature timestamp 1 certificate trust | Connects the timestamp provider to a trusted CA. | Timestamp certificates, PDF supporting certificates and selected roots. | UNKNOWN means a trusted chain could not be established. |
| Signature timestamp 1 revocation | Checks whether the timestamp provider’s certificate was withdrawn. | Embedded signed CRL/OCSP evidence and the verified certificate chain. | Cannot pass until the provider’s certificate chain is trusted. |
| Signature timestamp 1 | Decides whether the signing time is trusted. | Timestamp signature, matching fingerprint, provider chain and status evidence. | UNKNOWN can coexist with a valid token signature when provider trust is incomplete. |
| PDF signature 1 certificate trust | Connects the PDF signer to a selected CA. | PDF signing certificate, supporting certificates and roots. | PASS applies at the check time shown; without a trusted signature timestamp this uses current device time. |
| PDF signature 1 revocation | Checks whether the signing certificate was withdrawn. | Signed CRL/OCSP evidence stored in the PDF. | PASS means supported fresh signed evidence covers the non-root certificates. |
| Document timestamp 2 | Checks a timestamp protecting an earlier saved version. | Its signed timestamp token and the protected PDF bytes. | PASS verifies the token signature and matching file fingerprint. |
| Document timestamp 2 certificate trust | Connects that timestamp provider to a selected CA. | Timestamp certificates, supporting certificates and roots. | UNKNOWN means the provider’s trusted chain is incomplete. |
| Document timestamp 2 revocation | Checks that provider’s certificate status. | Embedded CRL/OCSP evidence and the trusted chain. | UNKNOWN means status could not be established. |
| Trusted timestamp 2 | Decides whether that recorded time is trusted. | The verified token, provider chain, certificate usage and revocation results. | All these checks must pass. |
| Document timestamp 3 | Checks the timestamp covering the current complete file. | Its token and protected bytes through the end of the file. | PASS means its signature and fingerprint match the current file. |
| Document timestamp 3 certificate trust | Connects the final timestamp provider to a selected CA. | Timestamp certificates, supporting certificates and roots. | UNKNOWN means the provider’s trusted chain is incomplete. |
| Document timestamp 3 revocation | Checks that provider’s certificate status. | Embedded CRL/OCSP evidence and the trusted chain. | UNKNOWN means status could not be established. |
| Trusted timestamp 3 | Decides whether the final recorded time is trusted. | Token, provider chain, certificate usage and revocation results. | Requires all timestamp trust checks to pass. |
| C2PA claim signature | Checks the signed provenance record and its assertion hashes. | The embedded C2PA record and its signing certificate. | PASS verifies the record’s signature; it does not authenticate original email senders. |
| C2PA file binding | Checks that the provenance record belongs to this PDF. | Its signed file hash and the current PDF bytes. | PASS binds the record to this file, including embedded content. |
| C2PA signing certificate path | Connects the provenance signer to a selected CA. | Its certificate chain, PDF supporting certificates and roots. | PASS is an X.509 chain result; public C2PA ecosystem trust lists are separate. |
| C2PA signing certificate revocation | Checks whether that signing certificate was withdrawn. | Embedded signed CRL/OCSP evidence. | PASS means supported fresh status evidence verified. |
| Offline evidence scope | Explains the evidence sources and limits. | Viewer settings and available embedded evidence. | Information: no certificate-status or timestamp server is contacted. |
| PAdES archival level and allowed revisions | Checks full long-term protection and permitted later changes. | Complete PDF revision history and archival evidence. | Browser result stays UNKNOWN; use the standalone Java validator. |
| Certificate dates (older reports) | Compares certificate validity dates with device time. | Certificate start/end dates and the browser clock. | Older UNKNOWN wording did not mean the dates were outside their ranges. Replaced by an informational device-date reference. |

The current viewer also reports **Signature timestamp 1 signature** separately:
this verifies the timestamp token and its link to the signing event even when
trust in the timestamp provider remains incomplete. Its displayed time is labelled
as a recorded time until the trust checks pass.
