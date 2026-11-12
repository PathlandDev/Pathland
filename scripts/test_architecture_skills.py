"""Structural checks for repository-local skills; not agent-behavior tests."""

import importlib.util
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parent.parent
SPEC = importlib.util.spec_from_file_location('references', ROOT / 'scripts/check-references.py')
REFERENCES = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(REFERENCES)
SKILLS = ROOT / '.agents/skills'
EXPECTED = {'pathland-orient', 'pathland-decision', 'pathland-review', 'pathland-maintain'}


class ArchitectureSkillTests(unittest.TestCase):
    def test_expected_skills_exist(self):
        for name in EXPECTED:
            self.assertTrue((SKILLS / name / 'SKILL.md').is_file(), name)

    def test_metadata_and_sections(self):
        for name in EXPECTED:
            with self.subTest(skill=name):
                text = (SKILLS / name / 'SKILL.md').read_text()
                match = re.match(r'\A---\n(.*?)\n---\n', text, re.DOTALL)
                self.assertIsNotNone(match)
                header = match.group(1)
                self.assertRegex(header, rf'(?m)^name: {name}$')
                self.assertRegex(header, r'(?m)^description: \S.+$')
                for section in ['Trigger', 'Invariants', 'Workflow', 'Artifact', 'Hand-offs']:
                    self.assertIn(f'\n## {section}\n', text)

    def test_skill_and_usage_links_resolve(self):
        files = [SKILLS / name / 'SKILL.md' for name in EXPECTED]
        files.append(ROOT / 'docs/decisions/skills.md')
        for source in files:
            for target in re.findall(r'\[[^\]]*\]\(([^)]+)\)', source.read_text()):
                if '://' in target:
                    continue
                with self.subTest(source=source, target=target):
                    path, _, anchor = target.partition('#')
                    destination = (source.parent / path).resolve() if path else source
                    self.assertTrue(destination.is_relative_to(ROOT), target)
                    self.assertTrue(destination.is_file(), target)
                    if anchor:
                        self.assertIn(anchor, REFERENCES.markdown_anchors(destination.read_text()))


if __name__ == '__main__':
    unittest.main()
