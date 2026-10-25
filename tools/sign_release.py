#!/usr/bin/env python3
"""Sign outside GitHub with the preserved private key; write public provenance only."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess

p = argparse.ArgumentParser()
p.add_argument('--input', required=True)
p.add_argument('--signer', required=True)
p.add_argument('--credentials', required=True)
p.add_argument('--source-sha', required=True)
p.add_argument('--run-id', required=True, type=int)
p.add_argument('--output', required=True)
a = p.parse_args()
assert re.fullmatch(r'[a-f0-9]{40}', a.source_sha)
private = Path(a.credentials).resolve()
c = json.loads(private.read_text())
output = Path(a.output).resolve()
output.mkdir(parents=True, exist_ok=True)
apk = output / 'Resultados-TV-1.0.0.apk'
env = dict(os.environ, RESULTADOS_STORE_PASSWORD=c['password'])
subprocess.run(['java', '-jar', a.signer, 'sign', '--ks', str(private.parent / c['keystore']),
    '--ks-key-alias', c['alias'], '--ks-pass', 'env:RESULTADOS_STORE_PASSWORD',
    '--key-pass', 'env:RESULTADOS_STORE_PASSWORD', '--v1-signing-enabled', 'true',
    '--v2-signing-enabled', 'true', '--v3-signing-enabled', 'true', '--v4-signing-enabled', 'false',
    '--out', str(apk), a.input], env=env, check=True)
verification = subprocess.check_output(['java', '-jar', a.signer, 'verify', '--verbose', '--print-certs', str(apk)], text=True)
certificate = re.search(r'certificate SHA-256 digest: ([a-f0-9]+)', verification).group(1)
assert certificate == Path('signing/certificate.sha256').read_text().strip()
sha256 = hashlib.sha256(apk.read_bytes()).hexdigest()
metadata = dict(version='1.0.0', version_code=1, package='com.b5solucoes.resultadostv', file=apk.name,
    sha256=sha256, certificate_sha256=certificate, source_sha=a.source_sha, workflow_run_id=a.run_id)
(output / 'release.json').write_text(json.dumps(metadata, indent=2) + '\n')
(output / 'SHA256SUMS.txt').write_text(f'{sha256}  {apk.name}\n')
print(json.dumps({'file': str(apk), 'bytes': apk.stat().st_size, 'sha256': sha256}))
