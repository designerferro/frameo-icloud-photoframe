"""Download official Android tools for Windows, Linux and macOS.

Requires Python 3 and an installed JDK 17. No global changes or licence auto-acceptance.
"""
import argparse
import hashlib
from pathlib import Path
import platform
import shutil
import stat
import urllib.request
import xml.etree.ElementTree as ET
import zipfile

REPOSITORY='https://dl.google.com/android/repository/'
PACKAGES=('platforms;android-23','build-tools;34.0.0','platform-tools')
def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--sdk',type=Path,default=Path(__file__).resolve().parents[2]/'photoframe-sdk')
    args=parser.parse_args();sdk=args.sdk.expanduser().resolve()
    system=platform.system();host={'Darwin':'macosx','Linux':'linux','Windows':'windows'}.get(system)
    if not host:parser.error('Sistema não suportado: '+system)
    if system=='Linux' and platform.machine().lower() not in ('x86_64','amd64'):parser.error('As ferramentas oficiais usadas neste guia requerem Linux x86_64.')
    sdk.mkdir(parents=True,exist_ok=True)
    with urllib.request.urlopen(REPOSITORY+'repository2-1.xml',timeout=60) as response:xml=ET.fromstring(response.read())
    for name in PACKAGES:
        target=sdk/Path(name.replace(';','/'))
        marker=target/('android.jar' if name.startswith('platforms;') else 'source.properties')
        if marker.exists():print('Já existe:',target);continue
        package=next((p for p in xml.findall('remotePackage') if p.attrib.get('path')==name),None)
        if package is None:raise RuntimeError('Pacote não encontrado: '+name)
        archives=package.findall('./archives/archive')
        archive=next((a for a in archives if a.findtext('host-os') in (None,host)),None)
        if archive is None:raise RuntimeError('Sem arquivo para '+host+': '+name)
        complete=archive.find('complete');url=complete.findtext('url')
        checksum=complete.find('checksum');algorithm=checksum.attrib.get('type','sha1')
        destination=sdk/(name.replace(';','-')+'.zip')
        print('A descarregar:',url)
        urllib.request.urlretrieve(REPOSITORY+url,destination)
        if hashlib.new(algorithm,destination.read_bytes()).hexdigest().lower()!=checksum.text.lower():raise RuntimeError('Checksum incorreto: '+url)
        stage=sdk/('extract-'+name.replace(';','-'));stage.mkdir(exist_ok=True)
        with zipfile.ZipFile(destination) as z:
            for member in z.infolist():
                resolved=(stage/member.filename).resolve()
                if stage.resolve() not in resolved.parents and resolved!=stage.resolve():raise RuntimeError('Caminho inválido no ZIP')
            z.extractall(stage)
            if system!='Windows':
                for member in z.infolist():
                    path=stage/member.filename;mode=member.external_attr>>16
                    if path.is_file() and mode: path.chmod(mode&0o777)
        roots=list(stage.iterdir())
        if len(roots)!=1 or not roots[0].is_dir():raise RuntimeError('Estrutura de arquivo inesperada')
        target.parent.mkdir(parents=True,exist_ok=True)
        shutil.move(str(roots[0]),str(target));stage.rmdir();destination.unlink()
    print('SDK preparado em:',sdk)
    print('Consulta as licenças dos fornecedores antes de usar as ferramentas. Licenças não são aceites automaticamente.')
    print('Compila com: python tools/build.py --sdk "'+str(sdk)+'" --jdk "CAMINHO_DO_JDK_17"')
if __name__=='__main__':main()
