#!/usr/bin/env python3
"""Install the signed production build, open it on TV and verify its remote UI."""
import json
from pathlib import Path
import subprocess
import time
import xml.etree.ElementTree as ET

package = 'com.b5solucoes.resultadostv'
directory = Path('dist/release-qa')
directory.mkdir(parents=True, exist_ok=True)

def adb(*args):
    return subprocess.check_output(['adb', *args], text=True)

def hierarchy():
    adb('shell', 'uiautomator', 'dump', '/sdcard/resultados-tv.xml')
    text = adb('shell', 'cat', '/sdcard/resultados-tv.xml')
    return text, ET.fromstring(text)

adb('shell', 'wm', 'size', '1280x720')
adb('install', '-r', 'release-assets/Resultados-TV-1.0.0.apk')
adb('logcat', '-c')
launch = adb('shell', 'am', 'start', '-W', '-n', package + '/.MainActivity')
assert 'Error' not in launch, launch
deadline = time.monotonic() + 60
text = ''
while time.monotonic() < deadline:
    text, root = hierarchy()
    labels = [node.get('text', '') for node in root.iter('node')]
    if any('Fonte: TSE' in t for t in labels) and not any('Consultando' in t for t in labels):
        break
    time.sleep(2)
assert any('Resultados' == node.get('text') for node in root.iter('node')), 'Production interface did not open'
assert any('Eleição' in node.get('text', '') for node in root.iter('node')), 'Election selector missing'
assert 'CANDIDATURA TESTE' not in text, 'Test fixture leaked into the production build'
(directory / '01-producao.xml').write_text(text)
with (directory / '01-producao.png').open('wb') as output:
    subprocess.run(['adb', 'exec-out', 'screencap', '-p'], stdout=output, check=True)
# Initial focus is the election selector, so OK must open its TV dialog.
adb('shell', 'input', 'keyevent', '23')
time.sleep(1)
text, root = hierarchy()
assert any(node.get('text') == 'Escolher eleição' for node in root.iter('node')), 'Remote OK did not open selector'
adb('shell', 'input', 'keyevent', '4')
time.sleep(1)
text, root = hierarchy()
assert any(node.get('text') == 'Resultados' for node in root.iter('node')), 'Remote Back did not restore production screen'
crash = adb('logcat', '-d', '-b', 'crash')
(directory / 'crash-log.txt').write_text(crash)
assert package not in crash, crash
report = {'package': package, 'signed_release_installed': True, 'launch': True,
          'remote_ok_and_back': True, 'test_fixtures_absent': True, 'crash': False,
          'source_may_be_temporarily_unavailable': True}
(directory / 'verification.json').write_text(json.dumps(report, indent=2) + '\n')
print(json.dumps(report))
