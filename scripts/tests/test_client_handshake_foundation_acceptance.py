import importlib.util
import pathlib
import sys
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
SPEC = importlib.util.spec_from_file_location("handshake_acceptance",
    ROOT / "run-client-handshake-foundation-acceptance.py")
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


def report(status="passed", complete=True, scenarios=None, cleanup="passed"):
    scenarios = scenarios or {name: {"status": "passed"} for name in (
        "compatible", "reconnect", "silent_timeout", "absent_client_allowed",
        "connected_commit_still_blocked")}
    scenarios["compatible"].update({"challenge": "one", "connection": "1"})
    scenarios["reconnect"].update({"challenge": "two", "previous_challenge": "one",
                                    "connection": "2", "previous_connection": "1",
                                    "same_client_process": True, "reset_line": 4})
    scenarios["silent_timeout"].update({"response_sent": False})
    scenarios["absent_client_allowed"].update({"pending_seen": False})
    return {"status": status, "complete_run": complete, "scenarios": scenarios,
            "cleanup": {"status": cleanup}}


class HandshakeAcceptanceReportTest(unittest.TestCase):
    def process(self, lines):
        process = MODULE.OwnedProcess("test", [], {}, pathlib.Path("."), pathlib.Path("test.log"))
        process.lines.extend(lines)
        return process

    def test_wait_marker_returns_first_after_cursor(self):
        process = self.process(["CLIENT_HANDSHAKE_SERVER_PENDING player=old",
                                "CLIENT_HANDSHAKE_SERVER_PENDING player=first",
                                "CLIENT_HANDSHAKE_SERVER_PENDING player=second"])
        value = process.wait_marker("CLIENT_HANDSHAKE_SERVER_PENDING", timeout=.01, after_line=0)
        self.assertEqual(value["fields"]["player"], "first")

    def test_wait_marker_filters_connection(self):
        process = self.process(["CLIENT_HANDSHAKE_SERVER_PENDING player=a connection=1",
                                "CLIENT_HANDSHAKE_SERVER_PENDING player=b connection=2"])
        value = process.wait_marker("CLIENT_HANDSHAKE_SERVER_PENDING", timeout=.01,
                                    expected_fields={"connection": "2"})
        self.assertEqual(value["fields"]["player"], "b")

    def test_timeout_contains_observed_tail(self):
        process = self.process(["CLIENT_HANDSHAKE_SERVER_ABSENT player=a"])
        with self.assertRaises(TimeoutError) as failure:
            process.wait_marker("CLIENT_HANDSHAKE_SERVER_PENDING", timeout=.01)
        self.assertIn("observed=", str(failure.exception))
        self.assertIn("tail=", str(failure.exception))

    def test_complete_report(self):
        self.assertEqual(MODULE.validate_report(report()), (True, "ok"))

    def test_missing_scenario(self):
        value = report(); del value["scenarios"]["reconnect"]
        self.assertFalse(MODULE.validate_report(value)[0])

    def test_failed_and_incomplete_reports(self):
        self.assertFalse(MODULE.validate_report(report(status="failed"))[0])
        self.assertFalse(MODULE.validate_report(report(complete=False))[0])
        self.assertFalse(MODULE.validate_report(report(cleanup="failed"))[0])

    def test_scenario_failure(self):
        value = report(); value["scenarios"]["silent_timeout"]["status"] = "failed"
        self.assertFalse(MODULE.validate_report(value)[0])

    def test_challenge_and_connection_reuse_are_rejected_by_evidence(self):
        value = report(); value["scenarios"]["reconnect"].update(
            {"challenge": "same", "previous_challenge": "same", "connection": "1"})
        self.assertFalse(MODULE.validate_report(value)[0])


if __name__ == "__main__":
    unittest.main()
