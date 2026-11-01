"""Exercise actual secret detection without retaining complete sample secrets."""
import importlib.util
from pathlib import Path
import tempfile
import subprocess
import unittest

spec=importlib.util.spec_from_file_location('scanner',Path(__file__).resolve().parents[1]/'tools/scan_secrets.py')
scanner=importlib.util.module_from_spec(spec);spec.loader.exec_module(scanner)

class ScannerTest(unittest.TestCase):
    def test_rules(self):
        samples={
            'private-key': b'-----BEGIN '+b'OPENSSH PRIVATE KEY-----',
            'github-token': b'ghp_'+b'A'*36,
            'aws-access-key': b'AKIA'+b'A'*16,
            'icloud-album': b'https://www.icloud.com/sharedalbum/#'+b'A' * 20,
            'embedded-credential': b'pass'+b'word = '+bytes([34])+b'example-value'+bytes([34]),
            'ssh-public-key': b'ssh-ed25519 '+b'A'*50,
            'local-address': b'192.'+b'168.1.2',
        }
        for rule,data in samples.items():
            self.assertIn(rule,{name for name,_ in scanner.findings(data)},rule)
    def test_placeholders(self):
        self.assertEqual([],list(scanner.findings(b'https://www.icloud.com/sharedalbum/#TOKEN\nPHOTOFRAME_KEYSTORE_PASSWORD\n')))
    def test_deleted_secret_is_detected(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d)
            def git(*args):return subprocess.run(['git','-C',d,*args],check=True,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
            git('init');git('config','user.name','Test');git('config','user.email','test@users.noreply.github.com')
            sample=root/'sample.txt';sample.write_bytes(b'ghp_'+b'A'*36)
            git('add','sample.txt');git('commit','-m','sample')
            sample.unlink();git('add','-u');git('commit','-m','remove')
            import contextlib,io
            out=io.StringIO()
            with contextlib.redirect_stdout(out):result=scanner.scan(root)
            self.assertEqual(1,result)
            self.assertIn('github-token:',out.getvalue())
            self.assertNotIn('A'*36,out.getvalue())

if __name__=='__main__':unittest.main()
