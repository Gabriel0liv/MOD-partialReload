import importlib.util
import pathlib
import unittest

path = pathlib.Path(__file__).parents[1] / "report-phase4e-gametests.py"
spec = importlib.util.spec_from_file_location("phase4e_report", path)
report = importlib.util.module_from_spec(spec)
spec.loader.exec_module(report)


class GameTestReportTest(unittest.TestCase):
    def base(self):
        names = sorted(report.REQUIRED_TRANSACTIONAL_TESTS | report.REQUIRED_SMOKE_TESTS)
        return "\n".join("PHASE4E_GAMETEST_PASSED:" + n for n in names) + "\n" \
            "Running test batch 'phase4e-tag-recipe-transaction:1' (13 tests)...\n" \
            "========= 14 GAME TESTS COMPLETE ======================\nAll 14 required tests passed :)"

    def test_complete(self):
        parsed = report.parse(self.base())
        self.assertEqual(parsed["status"], "passed")
        self.assertTrue(parsed["coverage_complete"])

    def test_missing_batch(self):
        parsed = report.parse("========= 14 GAME TESTS COMPLETE =========")
        self.assertEqual(parsed["status"], "failed")

    def test_missing_required(self):
        parsed = report.parse(self.base().replace("successfulCommitPublishesGenerationB", "missing"))
        self.assertIn("successfulCommitPublishesGenerationB", parsed["missing_tests"])

    def test_duplicate_marker(self):
        parsed = report.parse(self.base() + "\nPHASE4E_GAMETEST_PASSED:forgeWrapperIsRecognized")
        self.assertIn("forgeWrapperIsRecognized", parsed["duplicate_markers"])

    def test_global_failure(self):
        parsed = report.parse(self.base() + "\nFailures: 1")
        self.assertEqual(parsed["status"], "failed")
        self.assertEqual(parsed["global_failed"], 1)

    def test_smoke_only_is_incomplete(self):
        log = "\n".join("PHASE4E_GAMETEST_PASSED:" + n for n in report.REQUIRED_SMOKE_TESTS)
        parsed = report.parse(log + "\nRunning test batch 'phase4e-tag-recipe-transaction:1' (3 tests)...\n========= 3 GAME TESTS COMPLETE =========")
        self.assertEqual(parsed["status"], "failed")


if __name__ == "__main__":
    unittest.main()
