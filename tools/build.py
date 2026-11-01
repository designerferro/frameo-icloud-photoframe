"""Portable build: Python 3 + JDK 17 + Android SDK platform 23/build-tools 34.0.0.

Build outputs and signing keys live outside the source repository. No Gradle required.
"""
import argparse
import getpass
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import xml.etree.ElementTree as ET
import zipfile

ROOT=Path(__file__).resolve().parents[1]
def run(*args):
    subprocess.run([str(a) for a in args],check=True)

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--sdk',default=os.environ.get('ANDROID_SDK_ROOT') or os.environ.get('ANDROID_HOME'))
    parser.add_argument('--jdk',default=os.environ.get('JAVA_HOME'))
    parser.add_argument('--out',type=Path,default=ROOT.parent/'photoframe-build')
    parser.add_argument('--keystore',type=Path)
    parser.add_argument('--alias',default='photoframe')
    args=parser.parse_args()
    if not args.sdk:parser.error('Define ANDROID_SDK_ROOT ou usa --sdk CAMINHO.')
    sdk=Path(args.sdk).expanduser().resolve()
    javac=shutil.which('javac')
    jdk=Path(args.jdk).expanduser().resolve() if args.jdk else Path(javac).resolve().parent.parent if javac else None
    if not jdk:parser.error('Define JAVA_HOME ou usa --jdk CAMINHO (JDK 17).')
    exe='.exe' if os.name=='nt' else ''
    java=jdk/'bin'/('java'+exe)
    tools=sdk/'build-tools/34.0.0'
    android=sdk/'platforms/android-23/android.jar'
    required=[java,jdk/'bin'/('javac'+exe),android,tools/('aapt'+exe),tools/'lib/d8.jar',tools/'lib/apksigner.jar',tools/('zipalign'+exe)]
    for f in required:
        if not f.exists():parser.error('Falta ferramenta: '+str(f))
    out=args.out.expanduser().resolve();out.mkdir(parents=True,exist_ok=True)
    if out==ROOT or ROOT in out.parents:parser.error('Usa uma pasta --out fora do repositório, para não publicar chaves ou ficheiros de compilação.')
    version=json.loads((ROOT/'VERSION.json').read_text(encoding='utf-8'))
    manifest=ET.parse(ROOT/'AndroidManifest.xml').getroot();namespace='{http://schemas.android.com/apk/res/android}'
    if manifest.get(namespace+'versionName')!=version['versionName'] or int(manifest.get(namespace+'versionCode'))!=version['versionCode']:
        parser.error('As versões de AndroidManifest.xml e VERSION.json não coincidem.')
    classes=out/'classes';classes.mkdir(exist_ok=True)
    bootcp=str(android)+os.pathsep+str(tools/'core-lambda-stubs.jar')
    run(jdk/'bin'/('javac'+exe),'-encoding','UTF-8','-source','8','-target','8','-bootclasspath',bootcp,'-d',classes,*sorted(ROOT.glob('src/pt/photoframe/*.java')))
    jar=out/'classes.jar'
    with zipfile.ZipFile(jar,'w',zipfile.ZIP_DEFLATED) as archive:
        for file in sorted(classes.rglob('*.class')):archive.write(file,file.relative_to(classes).as_posix())
    run(java,'-cp',tools/'lib/d8.jar','com.android.tools.r8.D8','--lib',android,'--min-api','23','--output',out,jar)
    unsigned=out/'unsigned.apk'
    run(tools/('aapt'+exe),'package','-f','-M',ROOT/'AndroidManifest.xml','-I',android,'-F',unsigned)
    with zipfile.ZipFile(unsigned,'a') as archive:archive.write(out/'classes.dex','classes.dex')
    aligned=out/'aligned.apk';run(tools/('zipalign'+exe),'-f','4',unsigned,aligned)
    key=(args.keystore or out/'photoframe-signing.p12').expanduser().resolve()
    password=os.environ.get('PHOTOFRAME_KEYSTORE_PASSWORD')
    if not password:password=getpass.getpass('Palavra-passe da chave de assinatura (será criada se não existir): ')
    if len(password)<6:parser.error('A palavra-passe tem de ter pelo menos 6 caracteres.')
    os.environ['PHOTOFRAME_KEYSTORE_PASSWORD']=password
    if not key.exists():
        key.parent.mkdir(parents=True,exist_ok=True)
        run(jdk/'bin'/('keytool'+exe),'-genkeypair','-keystore',key,'-storepass:env','PHOTOFRAME_KEYSTORE_PASSWORD','-alias',args.alias,'-keyalg','RSA','-keysize','2048','-validity','10000','-dname','CN=Codex Photoframe local build')
        if os.name!='nt':key.chmod(0o600)
    name='Photoframe-v'+version['versionName']+'.apk';apk=out/name
    signer=[java,'-jar',tools/'lib/apksigner.jar']
    run(*signer,'sign','--ks',key,'--ks-key-alias',args.alias,'--ks-pass','env:PHOTOFRAME_KEYSTORE_PASSWORD','--out',apk,aligned)
    run(*signer,'verify','--verbose',apk)
    sha=hashlib.sha256(apk.read_bytes()).hexdigest()
    update=dict(version,apk=name,sha256=sha,minSdk=23)
    (out/'update.json').write_text(json.dumps(update,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    (out/'SHA256SUMS').write_text(sha+'  '+name+'\n',encoding='utf-8')
    print('APK:',apk)
    print('Release assets:',name,'update.json SHA256SUMS')
    return apk

if __name__=='__main__':main()
