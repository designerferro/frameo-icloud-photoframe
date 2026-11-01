"""Scan working files and every reachable Git commit without printing matched values.

This is a prevention check, not proof that no secret exists. Keep private material
outside this repository. Findings report only rule, path, line and commit.
"""
import argparse
from pathlib import Path
import re
import subprocess

RULES = {
    'private-key': re.compile(rb'-----BEGIN (?:[A-Z0-9]+ )*PRIVATE KEY-----'),
    'github-token': re.compile(rb'\b(?:gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,})\b'),
    'aws-access-key': re.compile(rb'\b(?:AKIA|ASIA)[A-Z0-9]{16}\b'),
    'api-token': re.compile(rb'\b(?:sk-(?:proj-)?[A-Za-z0-9_-]{20,}|AIza[A-Za-z0-9_-]{30,}|xox[baprs]-[A-Za-z0-9-]{10,})\b'),
    'icloud-album': re.compile(rb'(?:sharedalbum/#|sharedstreams\.icloud\.com/)[ABCD][A-Za-z0-9_-]{11,}'),
    'ssh-public-key': re.compile(rb'\b(?:ssh-rsa|ssh-ed25519|ecdsa-sha2-nistp\d+)\s+[A-Za-z0-9+/]{30,}'),
    'embedded-credential': re.compile(rb'''(?i)(?:password|passwd|secret|api[_-]?key|access[_-]?token)\s*[=:]\s*["']([^\s"']{8,})["']'''),
    'credential-url': re.compile(rb'https?://[^\s/:]+:[^\s/@]+@'),
    'local-address': re.compile(rb'\b(?:192\.168\.\d{1,3}\.\d{1,3}|10\.\d{1,3}\.\d{1,3}\.\d{1,3}|172\.(?:1[6-9]|2\d|3[01])\.\d{1,3}\.\d{1,3})\b'),
    'personal-home-path': re.compile(rb'/(?:Users|home)/[A-Za-z0-9_.-]+/'),
}
PRIVATE_SUFFIXES = {'.pem', '.key', '.p12', '.pfx', '.jks', '.keystore', '.jpg', '.jpeg', '.png', '.heic', '.webp', '.apk'}

def findings(data):
    for number, line in enumerate(data.splitlines(), 1):
        for rule, pattern in RULES.items():
            if pattern.search(line):
                yield rule, number

def git(root, *args):
    return subprocess.check_output(['git', '-C', str(root), *args])

def scan(root):
    failed = False
    def check(path, data, location):
        nonlocal failed
        if Path(path).suffix.lower() in PRIVATE_SUFFIXES or Path(path).name in {'.env', 'id_rsa', 'id_ed25519', 'album.json'}:
            print(f'private-file: {location}:{path}');failed = True
        for rule, line in findings(data):
            print(f'{rule}: {location}:{path}:{line}');failed = True
    for path in sorted(root.rglob('*')):
        relative=path.relative_to(root)
        if '.git' in relative.parts or '__pycache__' in relative.parts or not path.is_file():
            continue
        check(relative.as_posix(), path.read_bytes(), 'worktree')
    # Scan each historical blob once, including content deleted from the current tree.
    seen=set()
    commits=git(root,'rev-list','--all').decode().splitlines()
    for commit in commits:
        metadata=git(root,'show','-s','--format=%B%n%an <%ae>%n%cn <%ce>',commit)
        check('(commit metadata)', metadata, commit[:12])
        for entry in git(root,'ls-tree','-rz',commit).split(b'\0'):
            if not entry:continue
            details,path=entry.split(b'\t',1);mode,kind,sha=details.split()
            if kind != b'blob':continue
            name=path.decode('utf-8',errors='replace')
            # File names matter even when the blob is reused under another name.
            key=(sha,name)
            if key in seen:continue
            seen.add(key)
            check(name,git(root,'cat-file','blob',sha.decode()),commit[:12])
    if failed:return 1
    print(f'Secret scan passed: working files and {len(commits)} reachable commits ({len(seen)} historical blobs).')
    return 0

if __name__ == '__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root',type=Path,default=Path(__file__).resolve().parents[1])
    raise SystemExit(scan(parser.parse_args().root.resolve()))
