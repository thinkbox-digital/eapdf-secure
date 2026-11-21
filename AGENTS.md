Project: Email → EA-PDF generator

Workspace separation, 18 November 2026:
- This repository is the separate EA-PDF Secure edition, developed in the retained local `experiment/c2pa-pades` checkout.
- Continue normal development in the separate `../eapdf-main` clone, based on GitHub `main`.
- Do not merge or copy this experimental branch into that clone without the user's explicit request. The user selected PR fixes #26–#33 for main; other local dependency repairs and unrelated additions remain here until selected separately.
- The C2PA experiment maintains only the Full reader for Chromium and Firefox; Lite and Even More Lite were removed at the user's request on 2026-11-19. Every new reader regeneration must advance its minor version and reset patch to zero.

Build:
mvn package

Main entry point:
src/main/java/eapdf/EmailToPDFA.java

Email source readers:
src/main/java/eapdf/EmlReader.java
src/main/java/eapdf/MsgReader.java
src/main/java/eapdf/PstReader.java
src/main/java/eapdf/GmailDownloadToPdf.java

PDF generation:
src/main/java/eapdf/PdfMailWriter.java

Shared helpers:
src/main/java/eapdf/Common.java
src/main/java/eapdf/Models.java

Goal:
Convert email sources (EML, MSG, PST, Gmail) into EA-PDF compliant files using PDFBox and XMPBox.

Rules:
- Work on EA-PDF creation, validation, and reader development; the user authorized reader development on 2026-11-03
- Do not add new features without the user's express permission
- Keep output text plain and succinct
- Never comment, reply, submit issues or pull requests, publish reviews, or post any other content to any website or external service without the user's explicit permission. Read-only browsing and research do not authorize posting.
- Whenever the user says "push" or "push to GitHub", check open pull requests and address outstanding fixes before pushing.
- Whenever the user requests a push to GitHub, also publish the corresponding GitHub Release with versioned distributable assets and checksums; use prerelease status for beta versions. Retain only the current release and its immediate predecessor; verify the new release assets before deleting older releases.
- Keep GitHub release notes brief: versions, a few user-visible changes, essential setup information, and beta status. Put implementation details, benchmarks, test counts, and audit evidence in project documentation.
- Do not modify CLI arguments unless asked
- Preserve existing debug output
- Prefer minimal targeted edits
- For future file regenerations, enable Accessibility (`accessibility.addPdfUaFeatures: true`), including when using a custom configuration
- For future conversion and example-regeneration runs, disable optional PREMIS (`premis.enabled: false`), including in custom configurations, unless the user requests it.
- For conversion runs, use bounded parallel processing wherever supported and use the standalone Java validator. Retain the Python validator for its regression tests and explicitly requested comparisons or diagnostics.
- Keep example outputs in examples/<input-type>/ and prefix their filenames with the lower-case input type (for example, eml_sample_1s.pdf).
- Use the real-world files under in/ for future conversion regressions. Preserve originals, record source hashes, and collect conversion logs and Java validation reports in temporary storage. Report rejected or incomplete files explicitly. Use synthetic fixtures only to isolate additional edge cases.
- Do not modify files in target/ or debug directories
- Put temporary builds and working copies in /tmp; remove obsolete copies after use and at task checkpoints. Clean only task-owned temporary files; preserve active files and unrelated /tmp contents
- PDF validation staging may use the Docker-shared .generator_web_tmp directory; always remove staged files after success or failure

- For future generated outputs, append the export profile to the filename (for example, `_1s.pdf` or `_1c.pdf`). For full `in/` to `out/` regenerations, choose suitable profiles and include a representative mix of profiles across input types.
- For future bulk `in/` to `out/` generations, publish generated documents directly under `out/eml/`, `out/msg/`, `out/mbox/`, and `out/pst/`, without per-input or other deeper subfolders. Use isolated temporary storage for logs, manifests, progress files, validation reports, and runtime copies. Remove task-owned working files when the task finishes. Keep `output/` empty between tasks. For profiling, corpus runs, packaging, or other tools that normally write there, use a temporary destination where supported and clean up task-owned artifacts after use. Save evidence requested by the user at the requested destination.

- Public documentation must not cite or link to unpublished generated documents or evidence under the local output directories. Summarize verified results in tracked documentation; use published examples or release assets for downloadable files. Use explicit temporary destinations in command examples.

- Whenever the user requests a new version or a version bump, also bump the browser viewer version and rebuild the Full Chromium and Firefox packages.
- Whenever the browser viewer is rebuilt, advance its minor version and reset its patch version to zero.
- Future browser extension builds use three-part numeric versions without a beta suffix (for example, `1.27.0`), as requested on 2026-11-19.
- For a new installable local viewer build, use `npm --prefix browser-viewer run build:local`; it advances the minor version once for both Full browser packages and updates permanent unpacked files under `browser-viewer/local-install/` for the browser's Reload button. Keep those install paths stable across updates.
- Representative bulk samples must be balanced by output count: retain only a handful of standalone 1s/1si PDFs per input type, group EML/MSG sources into 1m/1mi archives, and use predominantly 1c/1ci/1m/1mi for PSTs. Preserve every source in the grouped/archive coverage.

- Signature-compatible embedded full-text indexing was authorized on 2026-11-07; see docs/EMBEDDED_SEARCH_INDEX.md for its supported signing workflow and limits.
- Produce distributable JARs only with the project version in their filenames; do not create unversioned creator, GUI, or validator JAR aliases.
- Do not build Linux application ZIPs; distribute the standalone versioned JARs with their bundled runtime and checksums.

- When summarizing fixes, classify the work as Bug fix, Code improvement, New code, Speed improvement, Memory improvement, or another clear top-level category. Distinguish measured improvements from expected ones.

- Keep only the latest versioned creator, GUI, and validator JARs in `dist/`; remove superseded JARs and their checksum files after verifying replacements.
- Keep the Full viewer’s independent CSV metadata export; removing creator finding-aid generation does not remove CSV export.

- The major-version bump and publication were authorized on 2026-11-09: creator/validator 2.0.0-beta.1 and browser viewer 1.0.0-beta.1. Future publication requires user authorization.

- For eapdf-regenerate, both sample and everything must keep each PST/MBOX as whole-source 1m/1mi/1c/1ci archives; never schedule individual-email 1s/1si extraction from those containers. Random EML/MSG archive groups must be bounded (currently 20 sources), cover every selected source and record their membership in run evidence.

- Keep private correspondence and sample contributors anonymous in first-party documentation and release notes. Use generic interoperability labels; do not publish contributor names, company names or private sample filenames. Preserve required legal attribution and original input bytes. Run `python3 scripts/check_public_docs.py` before publication.

- Maintain current handover status in docs/PROJECT_STATUS.md and separation decisions in docs/ARCHITECTURE.md. Keep docs/DEVELOPMENT_STATUS.md and docs/private local and ignored. Update the authoritative user/reference guide when behaviour changes; historical audit counts remain dated evidence.
- Before every push, regenerate the software architecture PDF. Draw arrows downward from each Tool to the Libraries it uses to show their connections.
- Before every push, regenerate `docs/SOFTWARE_COMPONENTS.md` with `python3 scripts/build_component_inventory.py`; review component descriptions and assignments for changed code, and include the refreshed inventory in the push. Verify it with `python3 scripts/build_component_inventory.py --check`.

## Cross-edition improvements

Follow [the shared update workflow](https://github.com/thinkbox-digital/eapdf/blob/main/docs/ARCHITECTURE.md#cross-edition-improvements)
for shared dependencies, EA-PDF rules, and creator or validator fixes. Tell the
user which editions each improvement affects and the status of propagation.
