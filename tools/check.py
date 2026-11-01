"""Run pure Java version-policy tests and validate release/source privacy invariants."""
import argparse
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET

root=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser();p.add_argument('--jdk',default=os.environ.get('JAVA_HOME'));args=p.parse_args()
java=Path(args.jdk)/'bin' if args.jdk else Path('')
suffix='.exe' if os.name=='nt' else ''
def tool(name):return str(java/(name+suffix)) if args.jdk else name
with tempfile.TemporaryDirectory(prefix='photoframe-policy-') as directory:
    subprocess.run([tool('javac'),'-encoding','UTF-8','-d',directory,str(root/'src/pt/photoframe/VersionPolicy.java'),str(root/'tests/VersionPolicyTest.java')],check=True)
    subprocess.run([tool('java'),'-cp',directory,'VersionPolicyTest'],check=True)
    subprocess.run([tool('javac'),'-encoding','UTF-8','-d',directory,str(root/'src/pt/photoframe/RemoteSecurity.java'),str(root/'src/pt/photoframe/RemoteHttpServer.java'),str(root/'tests/RemoteCoreTest.java')],check=True)
    subprocess.run([tool('java'),'-cp',directory,'RemoteCoreTest'],check=True)
    if os.name!='nt':
        subprocess.run([tool('javac'),'-encoding','UTF-8','-d',directory,str(root/'src/pt/photoframe/RootInstaller.java'),str(root/'tests/RootInstallerTest.java')],check=True)
        subprocess.run([tool('java'),'-cp',directory,'pt.photoframe.RootInstallerTest'],check=True)
v=json.loads((root/'VERSION.json').read_text(encoding='utf-8'))
m=ET.parse(root/'AndroidManifest.xml').getroot();a='{http://schemas.android.com/apk/res/android}'
assert m.get(a+'versionName')==v['versionName'] and int(m.get(a+'versionCode'))==v['versionCode']
assert v['author']=='Codex'
tracked=subprocess.check_output(['git','-C',str(root),'ls-files','-z']).decode().split('\0')
assert not any(Path(name).name=='.gitignore' for name in tracked if name),'Do not track or publish .gitignore'
local_ignore=root/'.gitignore'
if local_ignore.exists():
    assert '.gitignore' in local_ignore.read_text(encoding='utf-8').splitlines(),'Local .gitignore must ignore itself'
    assert subprocess.run(['git','-C',str(root),'check-ignore','-q','.gitignore']).returncode==0,'Local .gitignore must be ignored'
assert not list(root.rglob('*.p12')) and not list(root.rglob('*.apk')),'Keep signing keys and APK outside source'
assert not list(root.rglob('*.jpg')) and not (root/'assets/album.json').exists(),'No private album or photos in public source'
subprocess.run([sys.executable,str(root/'tools/scan_secrets.py')],check=True)
print('Source and version checks passed.')
