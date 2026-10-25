#!/usr/bin/env python3
"""Only publish a correctly signed APK from a successful build and device-test run."""
import glob
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess

m = json.loads(Path('release-assets/release.json').read_text())
assert m['version'] == '1.0.0' and m['version_code'] == 1
assert m['file'] == 'Resultados-TV-1.0.0.apk'
assert m['package'] == 'com.b5solucoes.resultadostv'
assert re.fullmatch(r'[a-f0-9]{40}', m['source_sha'])
assert isinstance(m['workflow_run_id'], int) and m['workflow_run_id'] > 0
apk = Path('release-assets') / m['file']
assert hashlib.sha256(apk.read_bytes()).hexdigest() == m['sha256']
repo = os.environ['GITHUB_REPOSITORY']
run = json.loads(subprocess.check_output(['gh', 'api', f'/repos/{repo}/actions/runs/{m["workflow_run_id"]}']))
assert run['head_sha'] == m['source_sha']
assert run['conclusion'] == 'success' and run['status'] == 'completed'
assert run['path'] == '.github/workflows/android.yml'
signer = sorted(glob.glob(os.path.join(os.environ['ANDROID_HOME'], 'build-tools', '*', 'apksigner')))[-1]
verification = subprocess.check_output([signer, 'verify', '--verbose', '--print-certs', str(apk)], text=True)
certificate = re.search(r'certificate SHA-256 digest: ([a-f0-9]+)', verification).group(1)
assert certificate == m['certificate_sha256'] == Path('signing/certificate.sha256').read_text().strip()
aapt = str(Path(signer).parent / 'aapt')
badging = subprocess.check_output([aapt, 'dump', 'badging', str(apk)], text=True)
assert "package: name='com.b5solucoes.resultadostv' versionCode='1' versionName='1.0.0'" in badging
assert 'android.intent.category.LEANBACK_LAUNCHER' in subprocess.check_output([aapt, 'dump', 'xmltree', str(apk), 'AndroidManifest.xml'], text=True)
subprocess.run(['gh', 'run', 'download', str(m['workflow_run_id']), '--repo', repo, '--name', 'Resultados-TV-unsigned', '--dir', 'original-build'], check=True)
# Signature replacement is allowed; all application entries must match the tested CI build byte for byte.
import zipfile
with zipfile.ZipFile('original-build/app-release-unsigned.apk') as original, zipfile.ZipFile(apk) as signed:
    names = [n for n in original.namelist() if not n.startswith('META-INF/') and not n.endswith('/')]
    assert names
    signed_names = [n for n in signed.namelist() if not n.startswith('META-INF/') and not n.endswith('/')]
    assert set(names) == set(signed_names), 'Unexpected entries in the signed APK'
    for name in names:
        assert original.read(name) == signed.read(name), f'Changed application entry: {name}'
tag = 'v' + m['version']
exists = subprocess.run(['gh', 'release', 'view', tag, '--repo', repo], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode == 0
if not exists:
    subprocess.run(['gh', 'release', 'create', tag, '--repo', repo, '--target', m['source_sha'],
        '--title', 'Resultados TV 1.0.0', '--notes-file', 'release-assets/notes.md', str(apk),
        'release-assets/SHA256SUMS.txt', 'release-assets/release.json'], check=True)
else:
    subprocess.run(['gh', 'release', 'upload', tag, '--repo', repo, '--clobber', str(apk),
        'release-assets/SHA256SUMS.txt', 'release-assets/release.json'], check=True)
