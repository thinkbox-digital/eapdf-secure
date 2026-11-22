#!/usr/bin/env python3
"""Freeze the pinned Linux renderer, Python and native libraries for JAR embedding.

Build-time Python/PyInstaller are required only by maintainers. End users run the
resulting executable without installing Python, pip, WeasyPrint or Pango.
"""
import argparse
from datetime import datetime, timezone
import hashlib
import importlib.metadata as metadata
import json
import os
from pathlib import Path
import platform
import re
import shutil
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[1]
WORKER = ROOT / 'src/main/resources/eapdf/weasyprint_worker.py'
PYINSTALLER = '6.22.3'
NATIVE = ['libpango-1.0.so.0', 'libpangoft2-1.0.so.0', 'libgobject-2.0.so.0',
          'libglib-2.0.so.0', 'libharfbuzz.so.0', 'libharfbuzz-subset.so.0',
          'libfontconfig.so.1', 'libfreetype.so.6', 'libseccomp.so.2']


def sha(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def run(command, **options):
    return subprocess.run(command, check=True, **options)


def retain_notices(built, libraries, cache):
    notices = built / 'licenses'; notices.mkdir(exist_ok=True)
    origins = {name: info['buildSource'] for name, info in libraries.items()}
    for library in (built / '_internal').rglob('*.so*'):
        matches = re.findall(r'^\s*' + re.escape(library.name) + r'\s+[^\n]*=>\s*(\S+)', cache, re.M)
        if matches: origins.setdefault(library.name, str(Path(matches[0]).resolve()))
    packages = {}
    for origin in origins.values():
        for candidate in [origin, origin.replace('/usr/lib/', '/lib/', 1)]:
            owner = subprocess.run(['dpkg-query', '-S', candidate], capture_output=True, text=True)
            if owner.returncode == 0: break
        if owner.returncode: continue  # Wheel-owned binaries retain their wheel license metadata.
        package = owner.stdout.split(': ', 1)[0].split(':', 1)[0]
        if package in packages: continue
        details = run(['dpkg-query', '-W', '-f=${binary:Package}\t${Version}\t${source:Package}\t${source:Version}',
                       package], capture_output=True, text=True).stdout
        packages[package] = details.split('\t')
        copyright = Path('/usr/share/doc') / package / 'copyright'
        if copyright.exists(): shutil.copy2(copyright, notices / (package + '-copyright'))
    for item in metadata.distribution('pyinstaller').files or []:
        if str(item).endswith('licenses/COPYING.txt'):
            shutil.copy2(metadata.distribution('pyinstaller').locate_file(item), notices / 'PyInstaller-COPYING.txt')
    (notices / 'SOURCE_PACKAGES.json').write_text(json.dumps(packages, indent=2) + '\n')


def build(destination):
    architecture = platform.machine()
    if sys.platform != 'linux' or architecture not in ('x86_64', 'aarch64'):
        raise RuntimeError('Build this runtime on supported Linux x86_64/aarch64')
    if metadata.version('pyinstaller') != PYINSTALLER:
        raise RuntimeError('Build requires PyInstaller==' + PYINSTALLER)
    versions = {}
    for line in (ROOT / 'requirements-weasyprint.lock').read_text().splitlines():
        if not line.strip() or line.startswith('#'):
            continue
        name, version = line.split('==')
        actual = metadata.version(name)
        if actual != version:
            raise RuntimeError(f'{name}: expected {version}, found {actual}')
        versions[name] = actual
    system = 'linux-' + architecture
    destination = destination.resolve()
    destination.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='eapdf-freeze-', dir='/tmp') as temporary:
        temporary = Path(temporary)
        native = temporary / 'native'; native.mkdir()
        data = temporary / 'data'; data.mkdir()
        fonts = data / 'fonts'; fonts.mkdir()
        for font in (ROOT / 'src/main/resources/assets').glob('*.ttf'):
            shutil.copy2(font, fonts / font.name)
        # Supply matching styles for the standard families; end users need no font packages.
        for directory in ['dejavu', 'liberation2', 'liberation', 'nanum']:
            for font in (Path('/usr/share/fonts/truetype') / directory).glob('*.ttf'):
                if font.name.startswith(('DejaVuSans', 'LiberationSans', 'LiberationSerif', 'LiberationMono', 'NanumGothic')):
                    if not (fonts / font.name).exists(): shutil.copy2(font, fonts / font.name)
        fontconfig = data / 'fontconfig'; fontconfig.mkdir()
        (fontconfig / 'fonts.conf').write_text('''<?xml version="1.0"?>
<!DOCTYPE fontconfig SYSTEM "urn:fontconfig:fonts.dtd">
<fontconfig><dir prefix="relative">../fonts</dir><cachedir prefix="xdg">fontconfig</cachedir>
<alias><family>sans-serif</family><prefer><family>DejaVu Sans</family></prefer></alias>
<alias><family>serif</family><prefer><family>Liberation Serif</family></prefer></alias>
<alias><family>monospace</family><prefer><family>DejaVu Sans Mono</family></prefer></alias>
</fontconfig>\n''')
        libraries = {}
        cache = run(['/sbin/ldconfig', '-p'], capture_output=True, text=True).stdout
        command = [sys.executable, '-I', '-m', 'PyInstaller', '--noconfirm', '--clean', '--onedir',
                   '--name', 'eapdf-weasyprint-worker', '--distpath', str(temporary / 'dist'),
                   '--workpath', str(temporary / 'build'), '--specpath', str(temporary),
                   '--exclude-module', 'tkinter', '--exclude-module', 'PIL.ImageTk',
                   '--add-data', str(fontconfig) + ':fontconfig',
                   '--add-data', str(fonts) + ':fonts',
                   '--add-data', str(ROOT / 'third_party_licenses') + ':licenses/assets']
        for package in ['weasyprint', 'pyphen', 'fontTools', 'PIL']:
            command += ['--collect-all', package]
        for name in versions:
            command += ['--copy-metadata', name]
        for name in NATIVE:
            matches = re.findall(r'^\s*' + re.escape(name) + r'\s+[^\n]*=>\s*(\S+)', cache, re.M)
            if not matches:
                raise RuntimeError('Build host is missing ' + name)
            origin = Path(matches[0]).resolve()
            shutil.copy2(origin, native / name)
            libraries[name] = {'sha256': sha(origin), 'buildSource': str(origin)}
            command += ['--add-binary', str(native / name) + ':.']
        command.append(str(WORKER)); worker_digest = sha(WORKER)
        run(command, cwd=ROOT)
        if sha(WORKER) != worker_digest: raise RuntimeError('Worker source changed during the build; rebuild it')
        built = temporary / 'dist/eapdf-weasyprint-worker'
        env = {'PATH': '/nonexistent', 'HOME': str(temporary), 'LANG': 'C.UTF-8'}
        probe = json.loads(run([str(built / 'eapdf-weasyprint-worker'), '--runtime-info'],
                              env=env, cwd=temporary, capture_output=True, text=True, timeout=30).stdout)
        if probe['weasyprint'] != '70.0' or not probe['frozen']:
            raise RuntimeError('Frozen runtime probe failed')
        manifest = {'format': 1, 'platform': system, 'weasyprint': '70.0',
                    'workerSha256': worker_digest, 'python': probe['python'],
                    'pyinstaller': PYINSTALLER, 'glibcMinimum': probe['glibc']}
        (built / 'runtime-manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
        (built / 'build-info.json').write_text(json.dumps({'date': datetime.now(timezone.utc).isoformat(),
            'packages': versions, 'nativeSeeds': libraries, 'runtime': probe}, indent=2) + '\n')
        retain_notices(built, libraries, cache)
        (built / 'SHA256SUMS').write_text(''.join(sha(p) + '  ' + p.relative_to(built).as_posix() + '\n'
            for p in sorted(built.rglob('*')) if p.is_file() and not p.is_symlink()))
        pending = Path(tempfile.mkdtemp(prefix='.weasyprint-new-', dir=destination.parent))
        backup = destination.with_name(destination.name + '.previous')
        try:
            shutil.copytree(built, pending, dirs_exist_ok=True, symlinks=True)
            if backup.exists(): raise RuntimeError('Previous renderer backup still exists: ' + str(backup))
            if destination.exists(): os.replace(destination, backup)
            try: os.replace(pending, destination)
            except BaseException:
                if backup.exists(): os.replace(backup, destination)
                raise
            if backup.exists(): shutil.rmtree(backup)
        finally:
            if pending.exists(): shutil.rmtree(pending)
        print('Verified bundled renderer:', destination)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, default=ROOT / '.local/runtime/weasyprint' / ('linux-' + platform.machine()))
    build(parser.parse_args().output)
