"""Protect exact-commit post-merge CI while cancelling obsolete PR candidates."""
from pathlib import Path
import unittest


class CiConcurrencyTests(unittest.TestCase):
    def test_pushes_use_commit_identity_and_prs_use_ref_identity(self):
        workflow = Path('.github/workflows/tests.yml').read_text()
        self.assertIn("group: tests-${{ github.workflow }}-${{ github.event_name == 'pull_request' && github.ref || github.sha }}", workflow)
        self.assertIn("cancel-in-progress: ${{ github.event_name == 'pull_request' }}", workflow)


if __name__ == '__main__':
    unittest.main()
