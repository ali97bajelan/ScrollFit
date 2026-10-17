"""Structural source checks; these do not compile Android sources."""
from pathlib import Path
import re
import subprocess
import tomllib
import xml.etree.ElementTree as ET

repo = Path(__file__).resolve().parents[1]
for module in ('app', 'platform'):
    for path in (repo / module / 'src').rglob('*.xml'):
        ET.parse(path)
with (repo / 'gradle/libs.versions.toml').open('rb') as stream:
    tomllib.load(stream)
for source_set in ('main', 'debug', 'release'):
    names = set()
    for source in {'main', source_set}:
        for path in (repo / 'app/src' / source / 'res/values').glob('*.xml'):
            names.update(e.attrib['name'] for e in ET.parse(path).getroot() if 'name' in e.attrib)
    for path in (repo / 'app/src' / source_set).rglob('*.kt'):
        missing = set(re.findall(r'R\.string\.(\w+)', path.read_text())) - names
        assert not missing, (path, missing)
localized = {}
for resource in (
    'camera_strings.xml', 'challenge_strings.xml', 'monitoring_strings.xml',
    'outcome_strings.xml', 'product_strings.xml', 'strings.xml', 'ux_strings.xml',
):
    for folder in ('values', 'values-en', 'values-fr'):
        path = repo / 'app/src/main/res' / folder / resource
        entries = list(ET.parse(path).getroot())
        assert len({e.attrib['name'] for e in entries}) == len(entries), f'Duplicate resource in {folder}/{resource}'
        signature = {}
        for entry in entries:
            if entry.tag == 'plurals':
                value = tuple(
                    (item.attrib['quantity'], re.findall(r'%\d+\$[sd]', ''.join(item.itertext())))
                    for item in entry
                )
            else:
                value = re.findall(r'%\d+\$[sd]', ''.join(entry.itertext()))
            signature[entry.attrib['name']] = (entry.tag, value)
        localized[(resource, folder)] = signature
    baseline = localized[(resource, 'values')]
    assert baseline == localized[(resource, 'values-en')] == localized[(resource, 'values-fr')], f'{resource} locale keys/format placeholders differ'
ns = '{http://schemas.android.com/apk/res/android}'
manifest = ET.parse(repo / 'app/src/main/AndroidManifest.xml').getroot()
activity = next(x for x in manifest.find('application').findall('activity') if x.get(ns + 'name') == '.challenge.ChallengeActivity')
assert activity.get(ns + 'exported') == 'false'
for path in (repo / 'app/src/main/res/xml/monitoring_accessibility_service.xml', repo / 'app/src/debug/res/xml/spike_accessibility_service.xml'):
    service = ET.parse(path).getroot()
    assert service.get(ns + 'canRetrieveWindowContent') == 'false'
    assert service.get(ns + 'canPerformGestures') in ('false', None)
subprocess.run(['git', '-c', 'core.whitespace=cr-at-eol', 'diff', '--check'], cwd=repo, check=True)
print('PASS: XML/TOML, main/debug/release string references, private challenge Activity, narrow accessibility capabilities and whitespace.')
print('PASS: camera, challenge, monitoring, outcome, product, app and UX resources have matching keys, plural forms and format placeholders in Persian, English and French.')
print('Source checks do not establish Android compilation, Room persistence, UI accessibility or device behavior.')
