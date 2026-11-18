#!/usr/bin/env python3
"""Embed a verified frozen renderer in versioned creator JARs (maintainer build)."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import tempfile
from zipfile import ZipFile, ZipInfo, ZIP_DEFLATED, ZIP_STORED


def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def embed(runtime, jars):
    metadata = json.loads((runtime / 'runtime-manifest.json').read_text())
    platform = metadata['platform']
    if runtime.name != platform or platform not in {'linux-x86_64', 'linux-aarch64'}:
        raise ValueError('Runtime directory/platform mismatch')
    prefix = 'runtime/weasyprint/' + platform + '/'
    with tempfile.TemporaryDirectory(prefix='eapdf-embed-', dir='/tmp') as temporary:
        archive = Path(temporary) / 'runtime.zip'
        files = {}
        with ZipFile(archive, 'w', compression=ZIP_DEFLATED, compresslevel=6) as output:
            for path in sorted(runtime.rglob('*')):
                if not path.is_file():
                    continue
                # PyInstaller aliases become regular files, never extraction-time symlinks.
                if not path.resolve().is_relative_to(runtime.resolve()):
                    raise ValueError('Runtime link leaves the packaged directory')
                name = prefix + path.relative_to(runtime).as_posix()
                files[name] = {'size': path.stat().st_size, 'sha256': digest(path),
                               'executable': os.access(path, os.X_OK)}
                info = ZipInfo(name, date_time=(2026, 11, 17, 0, 0, 0))
                info.external_attr = 0o100600 << 16
                info.compress_type = ZIP_DEFLATED
                with output.open(info, 'w') as target, path.open('rb') as source:
                    shutil.copyfileobj(source, target, 1024 * 1024)
        if sum(item['size'] for item in files.values()) > 512 * 1024 * 1024:
            raise ValueError('Runtime exceeds the extraction limit')
        bundle = {'platform': platform, 'version': metadata['weasyprint'],
                  'archiveSha256': digest(archive), 'files': files}
        for jar in jars:
            pending = jar.with_name(jar.name + '.embedding')
            try:
                with ZipFile(jar) as source, ZipFile(pending, 'w') as output:
                    if 'eapdf/EmbeddedWeasyPrintRuntime.class' not in source.namelist():
                        raise ValueError('Build the creator with embedded-runtime support first')
                    worker = hashlib.sha256(source.read('eapdf/weasyprint_worker.py')).hexdigest()
                    if worker != metadata['workerSha256']:
                        raise ValueError('Frozen renderer and JAR worker sources differ')
                    for entry in source.infolist():
                        if entry.filename.startswith('eapdf/runtime/'):
                            continue
                        with source.open(entry) as reader, output.open(entry, 'w') as writer:
                            shutil.copyfileobj(reader, writer, 1024 * 1024)
                    output.writestr('eapdf/runtime/bundle.json', json.dumps(bundle, sort_keys=True),
                                    compress_type=ZIP_DEFLATED)
                    output.write(archive, 'eapdf/runtime/runtime.zip', compress_type=ZIP_STORED)
                with ZipFile(pending) as check:
                    if check.testzip():
                        raise ValueError('Embedded JAR CRC verification failed')
                pending.replace(jar)
                jar.with_name(jar.name + '.sha256').write_text(digest(jar) + '  ' + jar.name + '\n')
                print('Verified embedded renderer:', jar)
            finally:
                pending.unlink(missing_ok=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--runtime', required=True, type=Path)
    parser.add_argument('jars', nargs='+', type=Path)
    args = parser.parse_args()
    embed(args.runtime.resolve(), [jar.resolve() for jar in args.jars])
