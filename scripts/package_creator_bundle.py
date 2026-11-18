#!/usr/bin/env python3
"""Package versioned self-contained JARs and public settings, excluding private data."""
import argparse
import hashlib
import io
import json
import os
from pathlib import Path
import shutil
import tempfile
import xml.etree.ElementTree as ET
from zipfile import ZipFile, ZipInfo, ZIP_DEFLATED

ROOT = Path(__file__).resolve().parents[1]


def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def package(directory, platform):
    version = ET.parse(ROOT / 'pom.xml').findtext('{http://maven.apache.org/POM/4.0.0}version')
    with ZipFile(directory / f'emailtopdfa-{version}.jar') as creator:
        with ZipFile(io.BytesIO(creator.read('eapdf/runtime/runtime.zip'))) as included:
            manifest = json.loads(included.read('runtime/weasyprint/' + platform + '/runtime-manifest.json'))
    if manifest['platform'] != platform or manifest['weasyprint'] != '70.0':
        raise RuntimeError('Runtime platform/version mismatch')
    names = {f'emailtopdfa-{version}.jar': 'eapdf.EmailToPDFA',
             f'emailtopdfa-{version}-gui.jar': 'eapdf.EapdfGui',
             f'eapdf-validator-{version}.jar': 'eapdf.ValidatorGui'}
    for name, main in names.items():
        with ZipFile(directory / name) as jar:
            if jar.testzip() or 'Main-Class: ' + main not in jar.read('META-INF/MANIFEST.MF').decode():
                raise RuntimeError('Executable JAR verification failed: ' + name)
            if main != 'eapdf.ValidatorGui':
                embedded = json.loads(jar.read('eapdf/runtime/bundle.json'))
                if embedded['platform'] != platform:
                    raise RuntimeError('Embedded renderer platform mismatch')
                with jar.open('eapdf/runtime/runtime.zip') as stream:
                    if hashlib.file_digest(stream, 'sha256').hexdigest() != embedded['archiveSha256']:
                        raise RuntimeError('Embedded renderer checksum mismatch')
    config = directory / 'config'; config.mkdir(exist_ok=True)
    for name in ['eapdf_config.json', 'signing-digicert.example.json', 'signing-policy.example.json']:
        shutil.copy2(ROOT / 'config' / name, config / name)
    for name in ['LICENSE', 'NOTICE', 'THIRD_PARTY_NOTICES.md']:
        shutil.copy2(ROOT / name, directory / name)
    launcher = directory / 'start-creator.sh'
    launcher.write_text(f'''#!/bin/sh
set -eu
eapdf_app_root=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
cd "$eapdf_app_root"
EAPDF_CONFIG=${{EAPDF_CONFIG:-$eapdf_app_root/config/eapdf_config.json}}
export EAPDF_CONFIG
exec java -jar "$eapdf_app_root/emailtopdfa-{version}-gui.jar" "$@"
'''); launcher.chmod(0o755)
    (directory / 'BUNDLE_README.txt').write_text(f'''EA-PDF {version}: local Linux test build, not published.
Requires Java 21+, Linux x86_64, glibc {manifest['glibcMinimum']}+ and Landlock ABI 3+.
Run the GUI JAR with Java, or use start-creator.sh for the supplied public config.
Python, WeasyPrint, Pango, font libraries and matching standard fonts are included.
No Python/pip/WeasyPrint/font-package installation is needed by users.
The creator JAR works alone: it verifies/extracts its renderer into a private
versioned ~/.cache/eapdf/weasyprint/ directory on first use, then reuses that copy.
Accessibility and embedded searchable indexes default off.
Signing is off in the public default config. Supply your own EAPDF_CONFIG or GUI settings.
Private configurations, credentials, passphrases and source emails are excluded.
Runtime versions, notices, source-package provenance and checksums are inside the JAR.
''')
    archive_name = f'eapdf-{version}-{platform}.zip'; prefix = archive_name[:-4] + '/'
    members = [directory / name for name in names]
    members += [directory / name for name in ['start-creator.sh', 'BUNDLE_README.txt', 'LICENSE', 'NOTICE', 'THIRD_PARTY_NOTICES.md']]
    members += [config / name for name in ['eapdf_config.json', 'signing-digicert.example.json', 'signing-policy.example.json']]
    with tempfile.TemporaryDirectory(prefix='eapdf-app-zip-', dir='/tmp') as temporary:
        pending = Path(temporary) / archive_name
        with ZipFile(pending, 'w', compression=ZIP_DEFLATED, compresslevel=6, allowZip64=True) as zip:
            for path in members:
                relative = path.relative_to(directory).as_posix()
                if '.local.json' in relative or relative.endswith(('.p12', '.pfx', '.passphrase')):
                    raise RuntimeError('Private material cannot enter the application bundle: ' + relative)
                entry = ZipInfo(prefix + relative, date_time=(2026, 11, 17, 0, 0, 0)); entry.create_system = 3
                entry.external_attr = (0o100755 if os.access(path, os.X_OK) else 0o100644) << 16
                zip.writestr(entry, path.read_bytes(), compress_type=ZIP_DEFLATED, compresslevel=6)
        with ZipFile(pending) as zip:
            if zip.testzip(): raise RuntimeError('Application ZIP CRC verification failed')
        destination = directory / archive_name
        shutil.copy2(pending, destination)
        if digest(pending) != digest(destination): raise RuntimeError('Copied application ZIP differs')
        (directory / (archive_name + '.sha256')).write_text(digest(destination) + '  ' + archive_name + '\n')
    print('Verified complete application bundle:', destination)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--directory', type=Path, default=ROOT / 'dist')
    parser.add_argument('--platform', default='linux-x86_64')
    args = parser.parse_args(); package(args.directory.resolve(), args.platform)
