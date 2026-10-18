import unittest
from image_release_receipt import receipt


class ImageReceiptTests(unittest.TestCase):
    def environment(self):
        return dict(RELEASE_SHA='a' * 40, RELEASE_DIGEST='sha256:' + 'b' * 64,
                    RELEASE_IMAGE='aserobaba/investory', GITHUB_RUN_ID='123', GITHUB_RUN_ATTEMPT='2', RELEASE_TESTS_RUN_ID='456', RELEASE_TESTS_RUN_ATTEMPT='1')

    def test_full_publication_identity(self):
        result = receipt(self.environment())
        self.assertEqual(result['sha'], 'a' * 40)
        self.assertEqual(result['run_attempt'], '2')
        self.assertEqual(result['digest'], 'sha256:' + 'b' * 64)

    def test_invalid_identity_fails_closed(self):
        for name, value in [('RELEASE_SHA', 'abcdefg'), ('RELEASE_DIGEST', 'latest'),
                            ('RELEASE_IMAGE', 'other/image'), ('GITHUB_RUN_ATTEMPT', 'unknown')]:
            with self.subTest(name=name), self.assertRaises(ValueError):
                receipt(dict(self.environment(), **{name: value}))
