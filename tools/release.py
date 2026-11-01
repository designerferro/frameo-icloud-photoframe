"""Publish an already signed build with GitHub CLI; never uploads signing keys."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess

root=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,default=root.parent/'photoframe-build');p.add_argument('--draft',action='store_true');a=p.parse_args()
out=a.out.resolve();version=json.loads((root/'VERSION.json').read_text(encoding='utf-8'));metadata=json.loads((out/'update.json').read_text(encoding='utf-8'))
for key in ('versionName','versionCode','repository','author'):
    if metadata[key]!=version[key]:raise SystemExit('Build metadata differs from source: '+key)
apk=out/metadata['apk']
if hashlib.sha256(apk.read_bytes()).hexdigest()!=metadata['sha256']:raise SystemExit('APK checksum mismatch')
tag='v'+version['versionName'];repo=version['repository']
commit=subprocess.check_output(['git','rev-parse','HEAD'],cwd=root,text=True).strip()
if subprocess.check_output(['git','status','--porcelain'],cwd=root,text=True).strip():raise SystemExit('Commit changes before publishing.')
command=['gh','release','create',tag,str(apk),str(out/'update.json'),str(out/'SHA256SUMS'),'--repo',repo,'--target',commit,'--title','Photoframe '+version['versionName'],'--notes-file',str(root/'docs/RELEASE-NOTES.md')]
if a.draft:command.append('--draft')
subprocess.run(command,check=True,cwd=root)
