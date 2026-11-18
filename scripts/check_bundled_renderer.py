#!/usr/bin/env python3
"""Render in a clean, offline Ubuntu container without Python or Pango installed."""
import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import uuid

ROOT = Path(__file__).resolve().parents[1]


def check(runtime, output):
    output.mkdir(parents=True, exist_ok=True)
    shared = ROOT / '.generator_web_tmp'; shared.mkdir(exist_ok=True)
    name = 'eapdf-renderer-check-' + uuid.uuid4().hex[:12]
    with tempfile.TemporaryDirectory(prefix='bundled-renderer-', dir=shared) as temporary:
        temporary = Path(temporary); app = temporary / 'runtime'; job = temporary / 'job'; job.mkdir()
        shutil.copytree(runtime, app, symlinks=True)
        shutil.copy2(app / '_internal/fonts/DejaVuSans.ttf', job / 'font.ttf')
        shutil.copy2(app / '_internal/fonts/DejaVuSans-Bold.ttf', job / 'bold.ttf')
        (job / 'in.html').write_text('<html lang="en"><head><title>Bundled renderer</title>'
            '<style>body{font-family:ArchiveFont}</style></head><body><h1>Bundled renderer</h1>'
            '<p>Offline Python and Pango: café Ελληνικά.</p></body></html>')
        request = {'version': 1, 'memoryMiB': 512, 'timeoutSeconds': 30, 'maxOutputMiB': 16,
            'maxResourceBytes': 67108864, 'maxPages': 100, 'fitWideContent': False,
            'fonts': [{'file': name, 'family': 'ArchiveFont', 'weight': weight, 'style': 'normal'}
                      for name, weight in [('font.ttf', 400), ('bold.ttf', 700)]],
            'assets': {}, 'baseUri': 'eapdf://message/', 'headerTagMarker': ''}
        (job / 'request.json').write_text(json.dumps(request))
        command = ['docker', 'run', '--rm', '--name', name, '--network', 'none', '--read-only',
            '--security-opt', 'seccomp=unconfined', '--cap-drop', 'ALL', '--pids-limit', '64',
            '--memory', '1g', '--cpus', '2', '--user', f'{os.getuid()}:{os.getgid()}',
            '--tmpfs', '/tmp:size=128m', '--mount', f'type=bind,source={app},target=/app,readonly',
            '--mount', f'type=bind,source={job},target=/job', '--workdir', '/job',
            '--env', 'PATH=/nonexistent', '--entrypoint', '/bin/sh', 'ubuntu:24.04', '-c',
            'test ! -x /usr/bin/python3 || exit 91\n'
            'test ! -x /usr/local/bin/python3 || exit 92\n'
            'if /sbin/ldconfig -p | /bin/grep -q libpango; then exit 93; fi\n'
            'exec /app/eapdf-weasyprint-worker request.json']
        try:
            result = subprocess.run(command, capture_output=True, text=True, timeout=60)
            (output / 'container.log').write_text(result.stdout + result.stderr)
            if result.returncode:
                raise RuntimeError(f'Bundled render failed ({result.returncode}): {result.stderr[-3000:]}')
            measured = json.loads((job / 'metrics.json').read_text())
            assert measured['bundledRuntime'] and measured['seccomp'] and measured['landlockAbi'] >= 3
            image = subprocess.check_output(['docker', 'image', 'inspect', 'ubuntu:24.04', '--format', '{{.Id}}'], text=True).strip()
            measured.update(status='PASS', containerImage=image, installedPython=False,
                            installedPango=False, network='none', path='/nonexistent')
            (output / 'verification.json').write_text(json.dumps(measured, indent=2) + '\n')
            shutil.copy2(job / 'out.pdf', output / 'renderer-probe.pdf')
            print('PASS: packaged renderer without installed Python/Pango, offline, Landlock and seccomp active.')
        finally:
            subprocess.run(['docker', 'rm', '-f', name], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--runtime', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args(); check(args.runtime.resolve(), args.output.resolve())
