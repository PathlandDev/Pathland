#!/usr/bin/env python3
"""Validate repository-relative architecture references in source comments."""

import argparse
from collections import Counter
from pathlib import Path
import re
import subprocess
import unicodedata


SOURCE_SUFFIXES = {
    '.rs', '.kt', '.kts', '.java', '.ts', '.tsx', '.js', '.mjs',
    '.c', '.cpp', '.h', '.hpp', '.py', '.sh',
}
DIRECTIVE = re.compile(r'^\s*(?://[/!]?|/\*+|\*|#)\s*@ref\b(.*?)\s*$')
REFERENCE = re.compile(
    r'(?P<target>[^\s#\[\]]+\.md)(?:#(?P<anchor>[^\s\[\]]+))?'
    r'\s+\[(?P<relation>implements|tests|constrained-by|explains|historical)\]'
    r'(?:\s+—\s+\S.*)?$'
)
ADR = re.compile(r'docs/decisions/\d{4}-[^/]+\.md$')


def markdown_anchors(text):
    """GitHub-style ATX/setext heading slugs, duplicates, and explicit anchors."""
    anchors = set()
    used = set()
    fence = None
    previous = ''
    for line in text.splitlines():
        marker = re.match(r'^\s{0,3}(`{3,}|~{3,})', line)
        if marker:
            token = marker.group(1)
            if fence is None:
                fence = token
            elif token[0] == fence[0] and len(token) >= len(fence) and not line.strip()[len(token):].strip():
                fence = None
            previous = ''
            continue
        if fence:
            continue
        anchors.update(re.findall(r'<a\s+(?:id|name)=[\"\']([^\"\']+)[\"\']', line))
        heading = re.match(r'^\s{0,3}#{1,6}\s+(.+?)(?:\s+#+)?\s*$', line)
        title = heading.group(1) if heading else None
        if title is None and previous.strip() and re.fullmatch(r'\s{0,3}(?:=+|-+)\s*', line):
            title = previous.strip()
        if title is not None:
            title = re.sub(r'<[^>]+>', '', title).lower()
            base = ''.join(c for c in title if c in '-_ ' or unicodedata.category(c)[0] in 'LN').replace(' ', '-')
            slug = base
            suffix = 0
            while slug in used:
                suffix += 1
                slug = f'{base}-{suffix}'
            used.add(slug)
            anchors.add(slug)
        previous = line
    return anchors


def check_file(root, source):
    errors = []
    references = 0
    root = root.resolve()
    for number, line in enumerate(source.read_text(encoding='utf-8').splitlines(), 1):
        directive = DIRECTIVE.match(line)
        if not directive:
            continue
        location = f'{source.relative_to(root)}:{number}'
        body = re.sub(r'\s*\*/$', '', directive.group(1)).strip()
        match = REFERENCE.fullmatch(body)
        if not match:
            errors.append(f'{location}: malformed reference or unknown relation')
            continue
        references += 1
        target = match.group('target')
        relative = Path(target)
        destination = (root / relative).resolve()
        if relative.is_absolute() or '..' in relative.parts or not destination.is_relative_to(root):
            errors.append(f'{location}: target must be a repository-root-relative path')
            continue
        if not destination.is_file():
            errors.append(f'{location}: missing target {target}')
            continue
        text = destination.read_text(encoding='utf-8')
        anchor = match.group('anchor')
        if anchor and anchor not in markdown_anchors(text):
            errors.append(f'{location}: missing anchor {target}#{anchor}')
        if ADR.fullmatch(target):
            status = re.search(r'^\*\*Status:\*\*\s+(\w+)', text, re.MULTILINE)
            if not status or status.group(1) not in {'Proposed', 'Accepted', 'Rejected', 'Superseded'}:
                errors.append(f'{location}: missing or invalid ADR status in {target}')
            elif status.group(1) in {'Rejected', 'Superseded'} and match.group('relation') != 'historical':
                errors.append(f'{location}: {status.group(1)} ADR requires [historical] or a current target')
            elif status.group(1) == 'Proposed' and match.group('relation') in {'implements', 'constrained-by'}:
                errors.append(f'{location}: proposed ADR is not an accepted constraint')
    return references, errors


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, default=Path(__file__).resolve().parent.parent)
    parser.add_argument('--report-unreferenced', action='store_true', help='list source files without references (informational)')
    args = parser.parse_args()
    root = args.root.resolve()
    paths = subprocess.check_output(
        ['git', 'ls-files', '-z', '--cached', '--others', '--exclude-standard'], cwd=root,
    ).decode().split('\0')
    errors = []
    coverage = Counter()
    unreferenced = []
    for name in sorted(set(paths) - {''}):
        source = root / name
        if source.suffix not in SOURCE_SUFFIXES or not source.is_file() or source.is_symlink():
            continue
        count, findings = check_file(root, source)
        errors.extend(findings)
        coverage['files'] += 1
        coverage['references'] += count
        coverage['annotated'] += bool(count)
        if not count:
            unreferenced.append(name)
    print(f"{coverage['references']} references; {coverage['annotated']}/{coverage['files']} source files annotated (coverage is informational).")
    if args.report_unreferenced:
        for name in unreferenced:
            print(f'unreferenced: {name}')
    for error in errors:
        print(error)
    return bool(errors)


if __name__ == '__main__':
    raise SystemExit(main())
