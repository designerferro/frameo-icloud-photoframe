"""Build and run synthetic video tests on an authorized Android 6 test device.
Requires ffmpeg, adb, the normal build toolchain and the release signing key.
Installs the supplied APK, preserving app data. Briefly plays test tones at full
volume, restores volume/preferences, and returns the app to its normal album.
"""
import argparse
import json
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile

root=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser(description=__doc__)
for name in ('sdk','jdk','keystore','apk'):
    p.add_argument('--'+name,required=True,type=Path)
p.add_argument('--alias',default='photoframe')
p.add_argument('--privacy-only',action='store_true',help='Test the album privacy dialog instead of video; requires a configured test album')
a=p.parse_args()
runner='PrivacyRunner' if a.privacy_only else 'Runner'
def run(*command):
    subprocess.run([str(x) for x in command],check=True)
with tempfile.TemporaryDirectory(prefix='photoframe-video-') as temporary:
    work=Path(temporary);source=work/'source';source.mkdir()
    shutil.copytree(root/'src',source/'src')
    shutil.copy(root/('tests/android/'+runner+'.java'),source/('src/pt/photoframe/'+runner+'.java'))
    (source/'tools').mkdir();shutil.copy(root/'tools/build.py',source/'tools/build.py')
    (source/'VERSION.json').write_text(json.dumps(dict(versionName='test',versionCode=1,author='Codex',repository='designerferro/frameo-icloud-photoframe')))
    (source/'AndroidManifest.xml').write_text(('''<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="pt.photoframe.videotest" android:versionCode="1" android:versionName="test"><uses-sdk android:minSdkVersion="23" android:targetSdkVersion="23"/><application android:label="Video test"/><instrumentation android:name="pt.photoframe.TEST_RUNNER" android:targetPackage="pt.photoframe"/></manifest>''').replace('TEST_RUNNER',runner))
    run(sys.executable,source/'tools/build.py','--sdk',a.sdk.resolve(),'--jdk',a.jdk.resolve(),'--keystore',a.keystore.resolve(),'--alias',a.alias,'--out',work/'build')
    if not a.privacy_only:
        for name,size in (('small','640x360'),('large','1920x1080')):
            run('ffmpeg','-hide_banner','-loglevel','error','-f','lavfi','-i','testsrc2=size='+size+':rate=24','-f','lavfi','-i','sine=frequency=440:sample_rate=44100','-t','4','-c:v','libx264','-preset','ultrafast','-pix_fmt','yuv420p','-c:a','aac','-y',work/(name+'.mp4'))
        run('ffmpeg','-hide_banner','-loglevel','error','-display_rotation','90','-i',work/'large.mp4','-c','copy','-y',work/'rotated.mp4')
    run('adb','install','-r',a.apk.resolve())
    run('adb','install','-r',work/'build/Photoframe-vtest.apk')
    try:
        if not a.privacy_only:
            for name in ('small','large','rotated'):
                run('adb','push',work/(name+'.mp4'),'/data/local/tmp/'+name+'.mp4')
        result=subprocess.check_output(['adb','shell','am','instrument','-w','pt.photoframe.videotest/pt.photoframe.'+runner],text=True,timeout=90)
        print(result)
        if 'INSTRUMENTATION_RESULT: result=PASS' not in result:raise SystemExit('Device tests failed')
    finally:
        run('adb','uninstall','pt.photoframe.videotest')
        run('adb','shell','rm','-f','/data/local/tmp/small.mp4','/data/local/tmp/large.mp4','/data/local/tmp/rotated.mp4')
        run('adb','shell','am','start','-n','pt.photoframe/.MainActivity')
