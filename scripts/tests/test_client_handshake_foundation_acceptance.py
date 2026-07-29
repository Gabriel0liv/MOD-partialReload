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
        "server_absent_client_mod_allowed", "server_absent_client_mod_reconnect",
        "connected_commit_still_blocked")}
    scenarios["compatible"].update({"challenge": "one", "connection": "1"})
    scenarios["reconnect"].update({"challenge": "two", "previous_challenge": "one",
                                    "connection": "2", "previous_connection": "1",
                                    "same_client_process": True, "reset_line": 4})
    scenarios["silent_timeout"].update({"response_sent": False})
    scenarios["absent_client_allowed"].update({"pending_seen": False})
    return {"status": status, "complete_run": complete, "scenarios": scenarios,
            "cleanup": {"status": cleanup},
            "subruns": {"with_mod": {"cleanup": {"status": cleanup}},
                        "without_mod": {"cleanup": {"status": cleanup}}}}


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

    def test_marker_without_fields_is_parsed(self):
        process = self.process(["HANDSHAKE_ACCEPTANCE_CLIENT_CONNECT_REQUESTED"])
        value = process.wait_marker("HANDSHAKE_ACCEPTANCE_CLIENT_CONNECT_REQUESTED", timeout=.01)
        self.assertEqual(value["line"], 0)

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

    def test_login_failure_classification(self):
        self.assertEqual(MODULE.classify_failure(MODULE.LoginEvidence(False, False, False, False, False)),
                         "CLIENT_BOOT_NOT_READY")
        self.assertEqual(MODULE.classify_failure(MODULE.LoginEvidence(True, False, False, False, False)),
                         "CLIENT_CONNECT_NOT_TRIGGERED")
        self.assertEqual(MODULE.classify_failure(MODULE.LoginEvidence(True, True, False, False, False)),
                         "FORGE_LOGIN_NOT_COMPLETED")
        self.assertEqual(MODULE.classify_failure(MODULE.LoginEvidence(True, True, True, False, False)),
                         "PARTIALRELOAD_HANDSHAKE_NOT_STARTED")
        self.assertEqual(MODULE.classify_failure(MODULE.LoginEvidence(True, True, True, True, False)),
                         "PARTIALRELOAD_HANDSHAKE_FAILED")
        self.assertEqual(MODULE.classify_failure(
            MODULE.LoginEvidence(False, True, True, True, True), "LAUNCH_ARGS"),
            "UNKNOWN_ACCEPTANCE_FAILURE")

    def test_launch_args_does_not_require_ready(self):
        self.assertEqual(MODULE.classify_failure(
            MODULE.LoginEvidence(False, True, False, False, False), "LAUNCH_ARGS"),
            "FORGE_LOGIN_NOT_COMPLETED")

    def test_cursor_is_snapshot_before_trigger(self):
        process = self.process(["CLIENT_HANDSHAKE_SERVER_PENDING player=old"])
        cursor = process.cursor()
        process.lines.append("CLIENT_HANDSHAKE_SERVER_PENDING player=new")
        found = process.wait_marker("CLIENT_HANDSHAKE_SERVER_PENDING", timeout=.01, after_line=cursor)
        self.assertEqual(found["fields"]["player"], "new")

    def test_acceptance_runs_have_distinct_run_ids(self):
        first = MODULE.Acceptance("CONTROL", 0)
        second = MODULE.Acceptance("LAUNCH_ARGS", 0)
        self.assertNotEqual(first.run_id, second.run_id)
        self.assertEqual(first.initial_connect_mode, "CONTROL")
        self.assertEqual(second.initial_connect_mode, "LAUNCH_ARGS")

    def test_diagnostic_report_cannot_be_complete(self):
        value = report(status="diagnostic_passed", complete=False)
        self.assertFalse(MODULE.validate_report(value)[0])

    def test_descendant_processes_are_transitive_and_cycle_safe(self):
        processes = [
            {"pid": 2, "parent_pid": 1, "command_line": "gradle", "creation_time": "a"},
            {"pid": 3, "parent_pid": 2, "command_line": "forgeclientuserdev", "creation_time": "b"},
            {"pid": 4, "parent_pid": 1, "command_line": "other", "creation_time": "c"},
            {"pid": 5, "parent_pid": 99, "command_line": "forgeclientuserdev", "creation_time": "d"},
            {"pid": 6, "parent_pid": 7, "command_line": "cycle", "creation_time": "e"},
            {"pid": 7, "parent_pid": 6, "command_line": "cycle", "creation_time": "f"},
        ]
        self.assertEqual([item["pid"] for item in MODULE.descendant_processes(1, processes)], [2, 4, 3])

    def test_find_game_process_is_role_strict(self):
        tree = [{"pid": 2, "parent_pid": 1, "command_line": "BootstrapLauncher --launchTarget forgeclientuserdev", "creation_time": "a"},
                {"pid": 3, "parent_pid": 1, "command_line": "BootstrapLauncher --launchTarget forgeserveruserdev", "creation_time": "b"}]
        self.assertEqual(MODULE.find_game_process(tree, "client")["pid"], 2)
        self.assertEqual(MODULE.find_game_process(tree, "server")["pid"], 3)
        self.assertIsNone(MODULE.find_game_process(tree, "other"))

    def test_process_tree_none_and_launch_args_evidence(self):
        self.assertEqual(MODULE.process_tree(None), [])
        process = self.process([])
        process.process = type("P", (), {"pid": 1})()
        self.assertEqual(MODULE.launch_args_evidence(process, 25565)["server_arg_present"], False)

    def test_attempt_window_excludes_previous_markers(self):
        process = self.process(["CLIENT_HANDSHAKE_SERVER_ABSENT run=r attempt=old",
                                "CLIENT_HANDSHAKE_SERVER_ABSENT run=r attempt=new"])
        window = MODULE.AttemptWindow(0, 0, 1, 1)
        evidence = MODULE.attempt_marker_evidence(process, None, window, "r", "new")
        self.assertTrue(evidence["server_absent"])
        self.assertEqual(len(evidence["server_entries"]), 1)

    def test_attempt_cleanup_flags_are_fail_closed(self):
        self.assertFalse(MODULE.validate_report({"status": "diagnostic_passed", "complete_run": False,
                                                  "scenarios": {}, "cleanup": {"status": "failed"}})[0])

    def test_first_network_divergence_normalizes_attempt_fields(self):
        value = MODULE.first_network_divergence(
            ["NETWORK phase=login connection=11", "COMPATIBLE run=a"],
            ["NETWORK phase=login connection=22", "Disconnected"])
        self.assertEqual(value["last_common_event"], "NETWORK phase=login connection=11")
        self.assertEqual(value["failure_terminal_event"], "Disconnected")

    def test_process_alive_rejects_invalid_pid(self): self.assertFalse(MODULE.process_alive(-1))
    def test_process_alive_empty_output(self):
        old = MODULE.subprocess.check_output
        MODULE.subprocess.check_output = lambda *a, **k: ""
        try: self.assertFalse(MODULE.process_alive(123))
        finally: MODULE.subprocess.check_output = old
    def test_identity_missing_creation_time_rejected(self):
        expected = MODULE.OwnedProcessIdentity(1, None, None, "x", "UNKNOWN_OWNED_DESCENDANT")
        self.assertFalse(MODULE.identity_matches(expected, {"pid": 1, "creation_time": None, "command_line": "x"}))
    def test_fingerprint_is_canonical(self):
        evidence = MODULE.AttemptEvidence(True, True, False, False, False, False, False, False, None, None)
        self.assertEqual(MODULE.infrastructure_fingerprint(evidence, {}), MODULE.infrastructure_fingerprint(evidence, {}))
    def test_fingerprint_has_no_pid(self):
        evidence = MODULE.AttemptEvidence(True, True, False, False, False, False, False, False, None, None)
        self.assertNotIn("12345", MODULE.infrastructure_fingerprint(evidence, {"last_marker": "pid=12345"}))
    def test_quota_requires_target(self):
        self.assertFalse(MODULE.evaluate_quota([], 1, 1)["quota_reached"])
    def test_quota_rejects_product(self):
        self.assertFalse(MODULE.evaluate_quota([{"classification": "PRODUCT_FAILURE"}], 0, 1)["quota_reached"])
    def test_quota_accepts_authorized_infra(self):
        item = {"classification": "INFRASTRUCTURE_FAILURE", "fingerprint": "x"}
        self.assertTrue(MODULE.evaluate_quota([item, {"classification": "VALID_PASS"}], 1, 2, {"x"})["quota_reached"])
    def test_quota_rejects_unauthorized_infra(self):
        item = {"classification": "INFRASTRUCTURE_FAILURE", "fingerprint": "x"}
        self.assertFalse(MODULE.evaluate_quota([item, {"classification": "VALID_PASS"}], 1, 2)["quota_reached"])
    def test_classification_control_control(self):
        e = MODULE.AttemptEvidence(True, True, True, False, False, False, False, False, "p", None, player_present_in_rcon=True)
        self.assertEqual(MODULE.classify_attempt(e, MODULE.MatrixExpectation("without_mod", "without_mod"), {"status": "passed"}, {}), MODULE.AttemptClassification.VALID_PASS)
    def test_classification_mod_absent(self):
        e = MODULE.AttemptEvidence(True, True, True, True, False, False, False, False, "p", None, server_discovering_seen=True)
        self.assertEqual(MODULE.classify_attempt(e, MODULE.MatrixExpectation("with_mod", "without_mod"), {"status": "passed"}, {}), MODULE.AttemptClassification.VALID_PASS)
    def test_classification_cleanup_failure(self):
        e = MODULE.AttemptEvidence(True, True, True, False, False, False, False, False, None, None)
        self.assertEqual(MODULE.classify_attempt(e, MODULE.MatrixExpectation("without_mod", "without_mod"), {"status": "failed"}, {}), MODULE.AttemptClassification.HARNESS_FAILURE)
    def test_classification_channel_is_product(self):
        e = MODULE.AttemptEvidence(True, True, True, False, False, False, False, False, None, None)
        self.assertEqual(MODULE.classify_attempt(e, MODULE.MatrixExpectation("without_mod", "without_mod"), {"status": "passed"}, {"channel_rejection_seen": True}), MODULE.AttemptClassification.PRODUCT_FAILURE)
    def test_preflight_empty_directory(self):
        import tempfile
        with tempfile.TemporaryDirectory() as path: self.assertEqual(MODULE.preflight_owned_processes(pathlib.Path(path))["status"], "passed")
    def test_preflight_invalid_manifest(self):
        import tempfile
        with tempfile.TemporaryDirectory() as path:
            file = pathlib.Path(path) / "x.json"; file.write_text("bad")
            self.assertEqual(MODULE.preflight_owned_processes(pathlib.Path(path))["error_code"], "OWNERSHIP_MANIFEST_INVALID")
    def test_owned_process_result_shape(self):
        result = MODULE.ProcessStopResult("failed", False, False, False, False, (1,), (2,))
        self.assertEqual(result.residual_owned_pids, (1,)); self.assertEqual(result.identity_mismatches, (2,))
    def test_matrix_expectation_is_immutable(self):
        with self.assertRaises(Exception): MODULE.MatrixExpectation("x", "y").server_mod_mode = "z"
    def test_find_game_process_rejects_neighbor(self):
        self.assertIsNone(MODULE.find_game_process([{"pid": 1, "command_line": "forgeclientuserdev"}], "server"))
    def test_process_tree_invalid_pid(self): self.assertEqual(MODULE.process_tree(-1), [])
    def test_current_snapshot_failure_is_empty(self):
        old = MODULE.subprocess.check_output
        MODULE.subprocess.check_output = lambda *a, **k: (_ for _ in ()).throw(OSError())
        try: self.assertEqual(MODULE.current_process_snapshot(), [])
        finally: MODULE.subprocess.check_output = old
    def test_validate_report_requires_new_scenario(self):
        value = report(); del value["scenarios"]["server_absent_client_mod_reconnect"]
        self.assertFalse(MODULE.validate_report(value)[0])


if __name__ == "__main__":
    unittest.main()
