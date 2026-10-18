import unittest

from nightly_release_preflight import assess


class NightlyReleasePreflightTest(unittest.TestCase):
    def test_unchanged_branches_skip(self):
        self.assertEqual("skip", assess("a", "a", [])[0])

    def test_absent_branch_sha_never_skips(self):
        self.assertEqual("fail", assess("", "", [])[0])

    def test_missing_required_job_fails(self):
        self.assertEqual("fail", assess("a", "b", [{"head_sha": "a", "name": "Unit", "conclusion": "success"}], required=["Unit", "DB_IT"])[0])

    def test_exact_sha_success_stops_at_preflight(self):
        self.assertEqual("preflight", assess("a", "b", [{"head_sha": "a", "conclusion": "success"}])[0])

    def test_missing_or_mismatched_checks_fail_closed(self):
        self.assertEqual("fail", assess("a", "b", [])[0])
        self.assertEqual("fail", assess("a", "b", [{"head_sha": "b", "conclusion": "success"}])[0])

    def test_failed_or_pending_check_fails(self):
        for conclusion in ("failure", "cancelled", None):
            with self.subTest(conclusion=conclusion):
                self.assertEqual("fail", assess("a", "b", [{"head_sha": "a", "conclusion": conclusion}])[0])


if __name__ == "__main__":
    unittest.main()
