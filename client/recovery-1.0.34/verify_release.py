#!/usr/bin/env python3
"""Fail closed on unexpected changes in the prepared 1.0.35 archive and manifest."""
import hashlib
import json
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parents[2]
OLD = ROOT / 'updates/nexus-client-1.0.34-mc1.21.11.zip'
NEW = ROOT / 'updates/nexus-client-1.0.35-mc1.21.11.zip'
EXPECTED_OLD = 'f6259ff4ac726111b648e9e1c0be84a85111fc5748fdce09d44071c227bb7ab5'
EXPECTED_NEW = '9a2b40ba557222525693e9366c3ae6af2005e6dd822602029445009a03ea1d71'
PATCHED = {'dev/nexus/client/hud/RShiftHud.class', 'dev/nexus/client/ui/panel/RShiftPanel.class'}
ADDED = {'dev/nexus/recovery/HudMotion.class', 'dev/nexus/recovery/HudAdapter.class',
         'dev/nexus/recovery/PanelMotion.class', 'dev/nexus/recovery/PanelMotion$State.class'}

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

assert sha(OLD) == EXPECTED_OLD, 'baseline hash changed'
assert sha(NEW) == EXPECTED_NEW, 'release hash changed'
with zipfile.ZipFile(OLD) as old, zipfile.ZipFile(NEW) as new:
    assert old.testzip() is None and new.testzip() is None
    old_names, new_names = old.namelist(), new.namelist()
    assert len(old_names) == len(set(old_names)) and len(new_names) == len(set(new_names))
    assert set(new_names) - set(old_names) == ADDED
    assert not (set(old_names) - set(new_names))
    for name in old_names:
        if name not in PATCHED | {'fabric.mod.json'}:
            assert old.read(name) == new.read(name), f'unexpected changed entry: {name}'
    for name in PATCHED:
        assert old.read(name) != new.read(name), f'patch missing: {name}'
    old_meta, new_meta = json.loads(old.read('fabric.mod.json')), json.loads(new.read('fabric.mod.json'))
    assert old_meta.pop('version') == '1.0.34'
    assert new_meta.pop('version') == '1.0.35'
    assert old_meta == new_meta
manifest = json.loads((ROOT / 'updates/nexus-update.json').read_text(encoding='utf8'))
assert manifest['client']['version'] == '1.0.35'
entry = manifest['client']['files']['1.21.11']
assert entry['sha256'] == EXPECTED_NEW and entry['size'] == NEW.stat().st_size
assert entry['url'] == 'https://github.com/qwercheka-byte/nexusCLIENT/releases/download/v1.3.21/nexus-client-1.0.35-mc1.21.11.zip'
print('Release archive, preserved entries, metadata and manifest verified')
