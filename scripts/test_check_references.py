"""Dependency-free tests for the architecture reference checker."""

import importlib.util
from pathlib import Path
import tempfile
import subprocess
import sys
import unittest

spec = importlib.util.spec_from_file_location('check_references', Path(__file__).with_name('check-references.py'))
checker = importlib.util.module_from_spec(spec)
spec.loader.exec_module(checker)


class ReferenceTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name).resolve()
        (self.root / 'spec').mkdir()
        (self.root / 'spec/MEDIA.md').write_text('# Media\n\n## Stable identity\n')
        (self.root / 'docs/decisions').mkdir(parents=True)
        self.adr = self.root / 'docs/decisions/0001-media.md'
        self.adr.write_text('# Media\n\n**Status:** Accepted\n')
        self.source = self.root / 'example.rs'

    def check(self, text):
        self.source.write_text(text)
        return checker.check_file(self.root, self.source)

    def test_valid_relations_and_comment_forms(self):
        for prefix in ['//', '///', '//!', '*', '#']:
            for relation in ['implements', 'tests', 'constrained-by', 'explains', 'historical']:
                with self.subTest(prefix=prefix, relation=relation):
                    self.assertEqual((1, []), self.check(f'{prefix} @ref spec/MEDIA.md#stable-identity [{relation}] — Reason\n'))
        self.assertEqual((1, []), self.check('/* @ref spec/MEDIA.md [implements] */'))

    def test_missing_path_and_anchor(self):
        self.assertIn('missing target', self.check('// @ref spec/GONE.md [tests]')[1][0])
        self.assertIn('missing anchor', self.check('// @ref spec/MEDIA.md#gone [tests]')[1][0])

    def test_malformed_directives(self):
        for body in ['', 'spec/MEDIA.md', 'spec/MEDIA.md [guesses]', 'spec/MEDIA.md [tests] trailing', 'spec/MEDIA.md# [tests]']:
            with self.subTest(body=body):
                self.assertTrue(self.check('// @ref ' + body)[1])

    def test_path_escape(self):
        for target in ['../spec/MEDIA.md', '/spec/MEDIA.md']:
            self.assertIn('repository-root-relative', self.check(f'// @ref {target} [tests]')[1][0])
        (self.root / 'outside.md').symlink_to(self.root.parent / 'outside.md')
        self.assertIn('repository-root-relative', self.check('// @ref outside.md [tests]')[1][0])

    def test_retired_adrs_require_historical(self):
        for status in ['Rejected', 'Superseded']:
            self.adr.write_text(f'# Media\n\n**Status:** {status}\n')
            self.assertTrue(self.check('// @ref docs/decisions/0001-media.md [implements]')[1])
            self.assertEqual((1, []), self.check('// @ref docs/decisions/0001-media.md [historical]'))

    def test_adr_metadata_and_proposals(self):
        self.adr.write_text('# Media\n')
        self.assertIn('invalid ADR status', self.check('// @ref docs/decisions/0001-media.md [tests]')[1][0])
        self.adr.write_text('# Media\n**Status:** Proposed\n')
        self.assertTrue(self.check('// @ref docs/decisions/0001-media.md [implements]')[1])
        self.assertEqual((1, []), self.check('// @ref docs/decisions/0001-media.md [explains]'))

    def test_ordinary_mentions_are_not_directives(self):
        self.assertEqual((0, []), self.check('let example = "// @ref not-a-reference";\n// See @ref syntax in the policy.'))

    def test_cli_inventory_and_exit_status(self):
        subprocess.run(['git', 'init', '-q', str(self.root)], check=True)
        (self.root / '.gitignore').write_text('ignored.rs\n')
        (self.root / 'ignored.rs').write_text('// @ref missing.md [tests]')
        self.source.write_text('// @ref spec/MEDIA.md#stable-identity [tests]')
        command = [sys.executable, str(Path(checker.__file__).resolve()), '--root', str(self.root)]
        success = subprocess.run(command, capture_output=True, text=True)
        self.assertEqual(0, success.returncode, success.stdout + success.stderr)
        self.assertIn('1 references; 1/1', success.stdout)
        subprocess.run(['git', 'add', 'example.rs'], cwd=self.root, check=True)
        self.source.write_text('// @ref spec/MEDIA.md#gone [tests]')
        failure = subprocess.run(command, capture_output=True, text=True)
        self.assertEqual(1, failure.returncode)
        self.assertIn('example.rs:1: missing anchor', failure.stdout)

    def test_heading_slugs(self):
        anchors = checker.markdown_anchors('''# A `Media` (test)
## A Media test
## A Media test-1
## Ångström: identity
## Labels & **links**
Heading two
-----------
~~~md
## Not a heading
~~~
<a id="stable"></a>
''')
        self.assertEqual({'a-media-test', 'a-media-test-1', 'a-media-test-1-1', 'ångström-identity', 'labels--links', 'heading-two', 'stable'}, anchors)


if __name__ == '__main__':
    unittest.main()
