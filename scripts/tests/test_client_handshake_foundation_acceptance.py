import importlib.util
import json
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
import handshake_infrastructure_policy as POLICY


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

    def test_git_fsmonitor_is_not_adopted_as_owned_descendant(self):
        self.assertFalse(MODULE.should_track_descendant("git fsmonitor--daemon run --detach"))
        self.assertTrue(MODULE.should_track_descendant("BootstrapLauncher --launchTarget forgeclientuserdev"))

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
    def test_quota_rejects_harness_cleanup_failure(self):
        attempt = {"classification": MODULE.AttemptClassification.HARNESS_FAILURE.value,
                   "error_code": "ATTEMPT_CLEANUP_FAILED"}
        result = MODULE.evaluate_quota([attempt], 0, 1)
        self.assertFalse(result["quota_reached"])
        self.assertEqual(result["harness_failures"], 1)
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
    def test_heartbeat_only_fingerprint_is_insufficient(self):
        evidence = MODULE.AttemptEvidence(True, True, False, False, False, False, False, False, None, None)
        diagnostics = {"last_client_marker": "HANDSHAKE_ACCEPTANCE_CLIENT_CONNECT_HEARTBEAT"}
        quality, missing = MODULE.infrastructure_fingerprint_quality(evidence, diagnostics)
        self.assertEqual(quality, MODULE.FingerprintQuality.INSUFFICIENT)
        self.assertIn("last_meaningful_client_marker", missing)
    def test_disconnected_screen_fingerprint_is_high(self):
        evidence = MODULE.AttemptEvidence(True, True, False, False, False, False, False, False, None, None)
        diagnostics = {"last_client_marker": "HANDSHAKE_ACCEPTANCE_CLIENT_SCREEN_CHANGED",
                       "last_meaningful_client_marker": "HANDSHAKE_ACCEPTANCE_CLIENT_SCREEN_CHANGED",
                       "last_client_screen": "DisconnectedScreen", "client_connection_phase": "DISCONNECTED",
                       "elapsed_connect_seconds": 20, "client_game_process_alive": True,
                       "server_game_process_alive": True, "tcp_state_summary": "NONE"}
        self.assertEqual(MODULE.infrastructure_fingerprint_quality(evidence, diagnostics)[0],
                         MODULE.FingerprintQuality.HIGH)
    def test_server_login_timeout_fingerprint_is_high(self):
        evidence = MODULE.AttemptEvidence(True, True, False, False, False, False, False, False, None, None)
        diagnostics = {"last_client_marker": "HANDSHAKE_ACCEPTANCE_CLIENT_CONNECT_REQUESTED",
                       "last_meaningful_client_marker": "HANDSHAKE_ACCEPTANCE_CLIENT_CONNECT_REQUESTED",
                       "client_connection_phase": "CONNECTING", "elapsed_connect_seconds": 61,
                       "client_game_process_alive": True, "server_game_process_alive": True,
                       "tcp_state_summary": "NONE", "server_login_timeout_seen": True}
        self.assertEqual(MODULE.infrastructure_fingerprint_quality(evidence, diagnostics)[0],
                         MODULE.FingerprintQuality.HIGH)
    def test_dead_client_process_fingerprint_is_high(self):
        evidence = MODULE.AttemptEvidence(True, True, False, False, False, False, False, False, None, None)
        diagnostics = {"last_client_marker": "HANDSHAKE_ACCEPTANCE_CLIENT_CONNECT_CALL_RETURN",
                       "last_meaningful_client_marker": "HANDSHAKE_ACCEPTANCE_CLIENT_CONNECT_CALL_RETURN",
                       "client_connection_phase": "CONNECTING", "elapsed_connect_seconds": 10,
                       "client_game_process_alive": False, "server_game_process_alive": True,
                       "tcp_state_summary": "NONE"}
        self.assertEqual(MODULE.infrastructure_fingerprint_quality(evidence, diagnostics)[0],
                         MODULE.FingerprintQuality.HIGH)
    def test_phase_without_terminal_is_medium(self):
        evidence = MODULE.AttemptEvidence(True, True, False, False, False, False, False, False, None, None)
        diagnostics = {"last_client_marker": "HANDSHAKE_ACCEPTANCE_CLIENT_CONNECT_REQUESTED",
                       "last_meaningful_client_marker": "HANDSHAKE_ACCEPTANCE_CLIENT_CONNECT_REQUESTED",
                       "client_connection_phase": "CONNECTING", "elapsed_connect_seconds": 10,
                       "client_game_process_alive": True, "server_game_process_alive": True,
                       "tcp_state_summary": "NONE"}
        self.assertEqual(MODULE.infrastructure_fingerprint_quality(evidence, diagnostics)[0],
                         MODULE.FingerprintQuality.MEDIUM)
    def test_authorization_rejects_insufficient_fingerprint(self):
        import tempfile, json
        with tempfile.TemporaryDirectory() as path:
            fp = pathlib.Path(path) / "report.json"
            fp.write_text(json.dumps({"server_mod_mode": "without_mod", "classpath_isolated": True,
                                      "partialreload_loaded": False, "cleanup": {"status": "passed"},
                                      "attempts": [{"classification": "INFRASTRUCTURE_FAILURE",
                                                    "fingerprint": "x",
                                                    "fingerprint_quality": "INSUFFICIENT"}]}))
            with self.assertRaises(ValueError): MODULE.load_authorized_infrastructure_fingerprints(fp)
    def test_authorization_accepts_high_fingerprint(self):
        import tempfile, json
        with tempfile.TemporaryDirectory() as path:
            fp = pathlib.Path(path) / "report.json"
            report = full_matrix_report("without_mod", [authorizable_attempt()] + [{"classification": "VALID_PASS"} for _ in range(9)])
            fp.write_text(json.dumps(report))
            self.assertEqual(MODULE.load_authorized_infrastructure_fingerprints(fp), POLICY.validate_control_baseline_report(report)[2])
    def test_fingerprint_normalization_removes_ids(self):
        self.assertEqual(MODULE.normalize_fingerprint_value("run=abc12345 pid=12345 C:\\Users\\Sato\\x"),
                         "<path>")
    def test_fingerprint_normalization_removes_gameprofile_identity_and_ip(self):
        value = POLICY.normalize_fingerprint_value(
            "com.mojang.authlib.GameProfile@360ca5e(/127.0.0.1:52135) lost connection")
        self.assertNotIn("360ca5e", str(value))
        self.assertNotIn("127.0.0.1", str(value))
        self.assertIn("GameProfile@<addr>", str(value))
    def test_causally_different_fingerprints_differ(self):
        evidence = MODULE.AttemptEvidence(True, True, False, False, False, False, False, False, None, None)
        one = MODULE.infrastructure_fingerprint(evidence, {"last_meaningful_client_marker": "A", "client_connection_phase": "CONNECTING"})
        two = MODULE.infrastructure_fingerprint(evidence, {"last_meaningful_client_marker": "B", "client_connection_phase": "CONNECTING"})
        self.assertNotEqual(one, two)
    def test_adjudicator_reconfirms_case_d(self):
        import importlib.util
        script = pathlib.Path(__file__).resolve().parents[1] / "adjudicate-handshake-infrastructure.py"
        spec = importlib.util.spec_from_file_location("adjudicator", script)
        module = importlib.util.module_from_spec(spec); spec.loader.exec_module(module)
        report = full_matrix_report("without_mod", [{"classification": "VALID_PASS"} for _ in range(10)])
        target = full_matrix_report("with_mod", [{"classification": "VALID_PASS"} for _ in range(10)])
        result = module.adjudicate({"attempts": [{"classification": "INFRASTRUCTURE_FAILURE",
                                                  "fingerprint_quality": "INSUFFICIENT"}]}, report, target)
        self.assertEqual(result["decision"], "CASE_D_RECONFIRMED")
    def test_tcp_progress_states_are_medium_not_high(self):
        for state in ("Established", "SynSent", "SynReceived"):
            payload = POLICY.fingerprint_payload(dummy_evidence(), complete_diag({"tcp_state_summary": state}))
            self.assertEqual(POLICY.assess_fingerprint_quality(payload)[0], POLICY.FingerprintQuality.MEDIUM)
    def test_tcp_terminal_states_can_be_high(self):
        for state in ("CloseWait", "FinWait1", "Closed"):
            payload = POLICY.fingerprint_payload(dummy_evidence(), complete_diag({"tcp_state_summary": state}))
            self.assertEqual(POLICY.assess_fingerprint_quality(payload)[0], POLICY.FingerprintQuality.HIGH)
    def test_normal_login_handshake_lines_are_not_terminal_errors(self):
        lines = ["Starting client connection handshake", "Network login completed", "Channel registered"]
        self.assertIsNone(POLICY.last_error_log_signature(lines))
    def test_terminal_error_log_patterns_are_strict(self):
        self.assertIsNotNone(POLICY.last_error_log_signature(["Failed to connect to the server"]))
        self.assertIsNotNone(POLICY.last_error_log_signature(["java.net.ConnectException: Connection refused"]))
        self.assertIsNotNone(POLICY.last_error_log_signature(["Internal Exception: io.netty.handler.codec.DecoderException"]))
    def test_attempt_window_raw_lines_are_isolated(self):
        process = self.process(["old Unknown custom packet", "current normal", "later rejected partialreload:client_sync"])
        self.assertEqual(MODULE.raw_lines_in_window(process, 0, 1), ["current normal"])
        self.assertIsNone(POLICY.last_error_log_signature(MODULE.raw_lines_in_window(process, 0, 1)))
    def test_loader_rejects_missing_attempt_cleanup(self):
        attempt = authorizable_attempt(); del attempt["cleanup"]
        ok, error = POLICY.validate_authorizable_attempt(attempt)[:2]
        self.assertFalse(ok); self.assertEqual(error, "ATTEMPT_CLEANUP_NOT_PASSED")
    def test_loader_rejects_failed_attempt_cleanup(self):
        ok, error = POLICY.validate_authorizable_attempt(authorizable_attempt(cleanup={"status": "failed"}))[:2]
        self.assertFalse(ok); self.assertEqual(error, "ATTEMPT_CLEANUP_NOT_PASSED")
    def test_loader_rejects_missing_attempt_evidence(self):
        attempt = authorizable_attempt(); del attempt["attempt_evidence"]
        ok, error = POLICY.validate_authorizable_attempt(attempt)
        self.assertFalse(ok); self.assertEqual(error, "ATTEMPT_EVIDENCE_MISSING")
    def test_loader_rejects_network_login_true(self):
        attempt = authorizable_attempt(); attempt["attempt_evidence"]["network_login_seen"] = True
        ok, error = POLICY.validate_authorizable_attempt(attempt)
        self.assertFalse(ok); self.assertEqual(error, "NETWORK_LOGIN_TRUE")
    def test_authorization_attempt_evidence_is_canonical_schema2(self):
        evidence = MODULE.authorization_attempt_evidence(
            {"ready": True, "connect_requested": True, "network_login": False,
             "server_absent": True, "server_pending": False, "server_compatible": False,
             "network_logout": False, "server_disconnected": False})
        self.assertEqual(set(evidence), {
            "client_ready_seen", "connect_requested_seen", "network_login_seen",
            "server_absent_seen", "server_pending_seen", "server_compatible_seen",
            "network_logout_seen", "server_disconnected_seen"})
        self.assertTrue(evidence["client_ready_seen"])
        self.assertTrue(evidence["connect_requested_seen"])
        self.assertFalse(evidence["network_login_seen"])
    def test_old_attempt_evidence_schema_is_rejected(self):
        attempt = authorizable_attempt()
        attempt["attempt_evidence"] = {"ready": True, "connect_requested": True, "network_login": False}
        ok, error = POLICY.validate_authorizable_attempt(attempt)
        self.assertFalse(ok); self.assertEqual(error, "ATTEMPT_EVIDENCE_SCHEMA_INVALID")
    def test_missing_attempt_evidence_schema_version_is_rejected(self):
        attempt = authorizable_attempt(); del attempt["attempt_evidence_schema_version"]
        ok, error = POLICY.validate_authorizable_attempt(attempt)
        self.assertFalse(ok); self.assertEqual(error, "ATTEMPT_EVIDENCE_SCHEMA_INVALID")
    def test_ready_missing_does_not_occur_for_valid_schema2_dict(self):
        ok, error = POLICY.validate_authorizable_attempt(authorizable_attempt())
        self.assertTrue(ok, error)
    def test_wait_evidence_inconsistency_ready(self):
        evidence = MODULE.AttemptEvidence(False, True, False, False, False, False, False, False, None, None)
        self.assertEqual(MODULE.wait_evidence_inconsistency(True, False, False, evidence),
                         "ATTEMPT_WINDOW_READY_INCONSISTENT")
    def test_wait_evidence_inconsistency_connect_requested(self):
        evidence = MODULE.AttemptEvidence(True, False, False, False, False, False, False, False, None, None)
        self.assertEqual(MODULE.wait_evidence_inconsistency(True, True, False, evidence),
                         "ATTEMPT_WINDOW_CONNECT_REQUESTED_INCONSISTENT")
    def test_wait_evidence_inconsistency_network_login(self):
        evidence = MODULE.AttemptEvidence(True, True, False, False, False, False, False, False, None, None)
        self.assertEqual(MODULE.wait_evidence_inconsistency(True, True, True, evidence),
                         "ATTEMPT_WINDOW_NETWORK_LOGIN_INCONSISTENT")
    def test_prelogin_disconnected_screen_does_not_require_exit_request_marker(self):
        evidence = MODULE.AttemptEvidence(True, True, False, False, False, False, False, False, None, None)
        self.assertFalse(MODULE.should_expect_exit_request(
            evidence, {"last_client_screen": "DisconnectedScreen"}))
    def test_connected_or_nonterminal_attempt_requires_exit_request_marker(self):
        connected = MODULE.AttemptEvidence(True, True, True, False, False, False, False, False, None, None)
        connecting = MODULE.AttemptEvidence(True, True, False, False, False, False, False, False, None, None)
        self.assertTrue(MODULE.should_expect_exit_request(connected, {"last_client_screen": "DisconnectedScreen"}))
        self.assertTrue(MODULE.should_expect_exit_request(connecting, {"last_client_screen": "ConnectScreen"}))
    def test_fresh_client_attempt_window_starts_before_first_line(self):
        process = self.process(["HANDSHAKE_ACCEPTANCE_CLIENT_READY", "HANDSHAKE_ACCEPTANCE_CLIENT_CONNECT_REQUESTED"])
        window = MODULE.AttemptWindow(-1, -1, None, None)
        evidence = MODULE.attempt_marker_evidence(None, process, window, None, None)
        self.assertTrue(evidence["ready"])
        self.assertTrue(evidence["connect_requested"])
    def test_tcp_summary_splits_comma_separated_states(self):
        self.assertEqual(POLICY.tcp_state_summary("ESTABLISHED,SYN_SENT"), "ESTABLISHED,SYN_SENT")
    def test_tcp_terminal_evidence_with_mixed_states(self):
        self.assertEqual(POLICY.tcp_state_summary("CLOSE_WAIT,ESTABLISHED"), "CLOSE_WAIT,ESTABLISHED")
        self.assertTrue(POLICY.tcp_terminal_evidence("CLOSE_WAIT,ESTABLISHED"))
    def test_control_bootstrap_failure_is_explicit_execution_failure(self):
        report = {"status": "failed", "bootstrap_completed": False, "server_mod_mode": "without_mod"}
        ok, error, _ = POLICY.validate_control_baseline_report(report)
        self.assertFalse(ok); self.assertEqual(error, "CONTROL_EXECUTION_FAILED")
    def test_target_bootstrap_failure_is_not_mode_invalid(self):
        adj = load_adjudicator()
        report = {"status": "failed", "bootstrap_completed": False,
                  "server_mod_mode": "with_mod", "server_main_mod_present": True}
        ok, error = adj.validate_target_report(report)
        self.assertFalse(ok); self.assertEqual(error, "TARGET_EXECUTION_FAILED")
    def test_loader_rejects_product_signals(self):
        for key in ("partialreload_marker_seen", "channel_rejection_seen", "unknown_custom_packet_seen"):
            attempt = authorizable_attempt(); attempt["fingerprint_diagnostics"][key] = True
            ok, error = POLICY.validate_authorizable_attempt(attempt)
            self.assertFalse(ok); self.assertEqual(error, "PRODUCT_SIGNAL_PRESENT")
    def test_loader_rejects_fingerprint_integrity_mismatch(self):
        attempt = authorizable_attempt()
        payload = json.loads(attempt["fingerprint"]); payload["last_client_screen"] = "OtherScreen"
        attempt["fingerprint"] = json.dumps(payload, sort_keys=True, separators=(",", ":"))
        ok, error = POLICY.validate_authorizable_attempt(attempt)
        self.assertFalse(ok); self.assertEqual(error, "FINGERPRINT_INTEGRITY_MISMATCH")
    def test_loader_rejects_quality_mismatch(self):
        attempt = authorizable_attempt(); attempt["fingerprint_quality"] = "MEDIUM"
        ok, error = POLICY.validate_authorizable_attempt(attempt)
        self.assertFalse(ok); self.assertEqual(error, "FINGERPRINT_INTEGRITY_MISMATCH")
    def test_loader_rejects_schema_old(self):
        attempt = authorizable_attempt()
        payload = json.loads(attempt["fingerprint"]); payload["schema_version"] = 1
        attempt["fingerprint"] = json.dumps(payload, sort_keys=True, separators=(",", ":"))
        ok, error = POLICY.validate_authorizable_attempt(attempt)
        self.assertFalse(ok); self.assertEqual(error, "FINGERPRINT_SCHEMA_INVALID")
    def test_control_report_validation_rejects_partial_matrix(self):
        report = full_matrix_report("without_mod", [authorizable_attempt()])
        ok, error, _ = POLICY.validate_control_baseline_report(report)
        self.assertFalse(ok); self.assertEqual(error, "CONTROL_MATRIX_INCOMPLETE")
    def test_control_report_validation_rejects_modded_control(self):
        report = full_matrix_report("with_mod", [authorizable_attempt()] * 10)
        ok, error, _ = POLICY.validate_control_baseline_report(report)
        self.assertFalse(ok); self.assertEqual(error, "CONTROL_SERVER_MODE_INVALID")
    def test_control_report_validation_accepts_complete_high(self):
        report = full_matrix_report("without_mod", [authorizable_attempt()] + [{"classification": "VALID_PASS", "cleanup": {"status": "passed"}} for _ in range(9)])
        ok, error, fps = POLICY.validate_control_baseline_report(report)
        self.assertTrue(ok, error); self.assertEqual(len(fps), 1)
    def test_adjudicator_current_baseline_case(self):
        adj = load_adjudicator()
        control = full_matrix_report("without_mod", [authorizable_attempt()] + [{"classification": "VALID_PASS", "cleanup": {"status": "passed"}} for _ in range(9)])
        target = full_matrix_report("with_mod", [{"classification": "VALID_PASS", "cleanup": {"status": "passed"}} for _ in range(10)])
        result = adj.adjudicate({"attempts": [{"classification": "INFRASTRUCTURE_FAILURE", "fingerprint_quality": "INSUFFICIENT"}]}, control, target)
        self.assertEqual(result["decision"], "CASE_CONTROL_BASELINE_ESTABLISHED")
        self.assertTrue(result["prospective_quota_allowed"])
        self.assertFalse(result["historical_failure"]["authorized"])
    def test_adjudicator_target_product_blocks_baseline(self):
        adj = load_adjudicator()
        control = full_matrix_report("without_mod", [authorizable_attempt()] + [{"classification": "VALID_PASS", "cleanup": {"status": "passed"}} for _ in range(9)])
        target = full_matrix_report("with_mod", [{"classification": "PRODUCT_FAILURE", "cleanup": {"status": "passed"}}] + [{"classification": "VALID_PASS", "cleanup": {"status": "passed"}} for _ in range(9)])
        self.assertEqual(adj.adjudicate({}, control, target)["decision"], "CASE_INVALID_EVIDENCE")
    def test_adjudicator_does_not_authorize_target_only_fingerprint(self):
        adj = load_adjudicator()
        control = full_matrix_report("without_mod", [{"classification": "VALID_PASS", "cleanup": {"status": "passed"}} for _ in range(10)])
        target = full_matrix_report("with_mod", [authorizable_attempt()] + [{"classification": "VALID_PASS", "cleanup": {"status": "passed"}} for _ in range(9)])
        result = adj.adjudicate({}, control, target)
        self.assertEqual(result["decision"], "CASE_B_PRODUCT_CORRELATED_PRELOGIN_FAILURE")
        self.assertEqual(result["authorized_fingerprints"], [])
    def test_loader_rejects_unknown_custom_packet_authorization(self):
        attempt = authorizable_attempt(); attempt["fingerprint_diagnostics"]["unknown_custom_packet_seen"] = True
        ok, error = POLICY.validate_authorizable_attempt(attempt)
        self.assertFalse(ok); self.assertEqual(error, "PRODUCT_SIGNAL_PRESENT")
    def test_control_report_rejects_matrix_complete_false(self):
        report = full_matrix_report("without_mod", [authorizable_attempt()] + [{"classification": "VALID_PASS"} for _ in range(9)])
        report["scenarios"]["cold_login"]["matrix_complete"] = False
        ok, error, _ = POLICY.validate_control_baseline_report(report)
        self.assertFalse(ok); self.assertEqual(error, "CONTROL_MATRIX_INCOMPLETE")
    def test_control_report_rejects_global_cleanup_failure(self):
        report = full_matrix_report("without_mod", [authorizable_attempt()] + [{"classification": "VALID_PASS"} for _ in range(9)])
        report["cleanup"]["status"] = "failed"
        ok, error, _ = POLICY.validate_control_baseline_report(report)
        self.assertFalse(ok); self.assertEqual(error, "CONTROL_CLEANUP_INVALID")
    def test_adjudicator_baseline_does_not_mask_c(self):
        adj = load_adjudicator()
        control = full_matrix_report("without_mod", [authorizable_attempt()] + [{"classification": "VALID_PASS"} for _ in range(9)])
        target_attempt = authorizable_attempt()
        target_attempt["fingerprint_diagnostics"]["disconnect_reason"] = "Connection refused"
        target_attempt["fingerprint"] = POLICY.canonical_fingerprint(dummy_evidence(), target_attempt["fingerprint_diagnostics"])
        target_payload = json.loads(target_attempt["fingerprint"])
        quality, missing = POLICY.assess_fingerprint_quality(target_payload)
        target_attempt["fingerprint_quality"] = quality.value
        target_attempt["fingerprint_missing_fields"] = missing
        target = full_matrix_report("with_mod", [target_attempt] + [{"classification": "VALID_PASS"} for _ in range(9)])
        self.assertEqual(adj.adjudicate({}, control, target)["decision"], "CASE_C_NON_EQUIVALENT_FAILURES")
    def test_scope_schema_version(self):
        scope = POLICY.authorization_scope("with_mod", "CONTROL", test_versions())
        self.assertEqual(scope.schema_version, POLICY.AUTHORIZATION_SCOPE_SCHEMA_VERSION)
        self.assertEqual(scope.fingerprint_schema_version, POLICY.FINGERPRINT_SCHEMA_VERSION)
    def test_helper_only_scope_derivation(self):
        scope = POLICY.authorization_scope("without_mod", "CONTROL", test_versions())
        self.assertEqual(scope.client_classpath_profile, "HELPER_ONLY")
        self.assertFalse(scope.client_main_mod_present)
        self.assertTrue(scope.helper_mod_present)
    def test_main_mod_and_helper_scope_derivation(self):
        scope = POLICY.authorization_scope("with_mod", "CONTROL", test_versions())
        self.assertEqual(scope.client_classpath_profile, "MAIN_MOD_AND_HELPER")
        self.assertTrue(scope.client_main_mod_present)
        self.assertTrue(scope.helper_mod_present)
    def test_ready_profile_without_mod(self):
        entry = {"fields": {"partialReloadLoaded": "false", "helperLoaded": "true"}}
        profile, error = MODULE.ready_client_profile(entry, "without_mod", "CONTROL")
        self.assertIsNone(error)
        self.assertEqual(profile["client_classpath_profile"], "HELPER_ONLY")
    def test_ready_profile_with_mod(self):
        entry = {"fields": {"partialReloadLoaded": "true", "helperLoaded": "true"}}
        profile, error = MODULE.ready_client_profile(entry, "with_mod", "CONTROL")
        self.assertIsNone(error)
        self.assertEqual(profile["client_classpath_profile"], "MAIN_MOD_AND_HELPER")
    def test_ready_profile_mismatch(self):
        entry = {"fields": {"partialReloadLoaded": "false", "helperLoaded": "true"}}
        _, error = MODULE.ready_client_profile(entry, "with_mod", "CONTROL")
        self.assertEqual(error, "CLIENT_PROFILE_MISMATCH")
    def test_scope_match_accepts_identical(self):
        left = POLICY.authorization_scope("with_mod", "CONTROL", test_versions())
        right = POLICY.authorization_scope("with_mod", "CONTROL", test_versions())
        self.assertEqual(POLICY.authorization_scope_matches(left, right), (True, []))
    def test_scope_match_rejects_client_mode(self):
        left = POLICY.authorization_scope("without_mod", "CONTROL", test_versions())
        right = POLICY.authorization_scope("with_mod", "CONTROL", test_versions())
        ok, changed = POLICY.authorization_scope_matches(left, right)
        self.assertFalse(ok); self.assertIn("client_mod_mode", changed)
    def test_scope_match_rejects_forge_version(self):
        left = POLICY.authorization_scope("with_mod", "CONTROL", test_versions())
        right = POLICY.authorization_scope("with_mod", "CONTROL", test_versions({"forge_version": "47.4.11"}))
        self.assertIn("forge_version", POLICY.authorization_scope_matches(left, right)[1])
    def test_scope_match_rejects_java_major(self):
        left = POLICY.authorization_scope("with_mod", "CONTROL", test_versions())
        right = POLICY.authorization_scope("with_mod", "CONTROL", test_versions({"java_version": 21}))
        self.assertIn("java_major", POLICY.authorization_scope_matches(left, right)[1])
    def test_scope_match_rejects_initial_connect_mode(self):
        left = POLICY.authorization_scope("with_mod", "CONTROL", test_versions())
        right = POLICY.authorization_scope("with_mod", "LAUNCH_ARGS", test_versions())
        self.assertIn("initial_connect_mode", POLICY.authorization_scope_matches(left, right)[1])
    def test_absent_baseline_cannot_authorize_compatible_scope(self):
        report = full_matrix_report("without_mod", [authorizable_attempt()] + [{"classification": "VALID_PASS"} for _ in range(9)], "without_mod")
        ok, error, _ = POLICY.validate_control_baseline_report(report, "with_mod")
        self.assertFalse(ok); self.assertEqual(error, "AUTHORIZATION_SCOPE_CLIENT_MODE_INVALID")
    def test_compatible_baseline_cannot_authorize_absent_scope(self):
        report = full_matrix_report("without_mod", [authorizable_attempt()] + [{"classification": "VALID_PASS"} for _ in range(9)], "with_mod")
        ok, error, _ = POLICY.validate_control_baseline_report(report, "without_mod")
        self.assertFalse(ok); self.assertEqual(error, "AUTHORIZATION_SCOPE_CLIENT_MODE_INVALID")
    def test_loader_returns_baseline_with_scope(self):
        report = full_matrix_report("without_mod", [authorizable_attempt()] + [{"classification": "VALID_PASS"} for _ in range(9)], "with_mod")
        ok, error, baseline = POLICY.load_authorized_infrastructure_baseline(report, "source", "with_mod")
        self.assertTrue(ok, error)
        self.assertEqual(baseline.scope.client_mod_mode, "with_mod")
        self.assertEqual(len(baseline.fingerprints), 1)
    def test_compare_fingerprint_payloads(self):
        left = json.dumps({"schema_version": 2, "a": 1}, sort_keys=True)
        right = json.dumps({"schema_version": 2, "a": 2}, sort_keys=True)
        diff = POLICY.compare_fingerprint_payloads(left, right)
        self.assertFalse(diff["equal"])
        self.assertIn("a", diff["changed_fields"])
    def test_compatible_case_a_requires_historical_exact_match_in_control(self):
        adj = load_adjudicator()
        hist = authorizable_attempt()
        control = full_matrix_report("without_mod", [hist] + [{"classification": "VALID_PASS"} for _ in range(9)], "with_mod")
        target = full_matrix_report("with_mod", [{"classification": "VALID_PASS"} for _ in range(10)], "with_mod")
        quota = {"scenarios": {"cold_login": {"attempts": [hist]}}}
        self.assertEqual(adj.adjudicate(quota, control, target, "compatible_client")["decision"],
                         "COMPATIBLE_CASE_A_MATCHED_INFRASTRUCTURE")
    def test_compatible_case_d_reconfirmed(self):
        adj = load_adjudicator()
        control = full_matrix_report("without_mod", [{"classification": "VALID_PASS"} for _ in range(10)], "with_mod")
        target = full_matrix_report("with_mod", [{"classification": "VALID_PASS"} for _ in range(10)], "with_mod")
        self.assertEqual(adj.adjudicate({}, control, target, "compatible_client")["decision"],
                         "COMPATIBLE_CASE_D_RECONFIRMED")
    def test_compatible_case_b_target_correlated(self):
        adj = load_adjudicator()
        hist = authorizable_attempt()
        control = full_matrix_report("without_mod", [{"classification": "VALID_PASS"} for _ in range(10)], "with_mod")
        target = full_matrix_report("with_mod", [hist] + [{"classification": "VALID_PASS"} for _ in range(9)], "with_mod")
        quota = {"scenarios": {"cold_login": {"attempts": [hist]}}}
        self.assertEqual(adj.adjudicate(quota, control, target, "compatible_client")["decision"],
                         "COMPATIBLE_CASE_B_TARGET_CORRELATED")
    def test_compatible_case_c_non_equivalent(self):
        adj = load_adjudicator()
        hist = authorizable_attempt()
        different = authorizable_attempt()
        different["fingerprint_diagnostics"]["disconnect_reason"] = "Connection refused"
        different["fingerprint"] = POLICY.canonical_fingerprint(dummy_evidence(), different["fingerprint_diagnostics"])
        payload = json.loads(different["fingerprint"])
        quality, missing = POLICY.assess_fingerprint_quality(payload)
        different["fingerprint_quality"] = quality.value
        different["fingerprint_missing_fields"] = missing
        control = full_matrix_report("without_mod", [different] + [{"classification": "VALID_PASS"} for _ in range(9)], "with_mod")
        target = full_matrix_report("with_mod", [hist] + [{"classification": "VALID_PASS"} for _ in range(9)], "with_mod")
        quota = {"scenarios": {"cold_login": {"attempts": [hist]}}}
        self.assertEqual(adj.adjudicate(quota, control, target, "compatible_client")["decision"],
                         "COMPATIBLE_CASE_C_NON_EQUIVALENT")
    def test_compatible_case_unresolved_when_control_has_unrelated_infra_and_target_passes(self):
        adj = load_adjudicator()
        hist = authorizable_attempt()
        different = authorizable_attempt()
        different["fingerprint_diagnostics"]["disconnect_reason"] = "Connection refused"
        different["fingerprint"] = POLICY.canonical_fingerprint(dummy_evidence(), different["fingerprint_diagnostics"])
        payload = json.loads(different["fingerprint"])
        quality, missing = POLICY.assess_fingerprint_quality(payload)
        different["fingerprint_quality"] = quality.value
        different["fingerprint_missing_fields"] = missing
        control = full_matrix_report("without_mod", [different] + [{"classification": "VALID_PASS"} for _ in range(9)], "with_mod")
        target = full_matrix_report("with_mod", [{"classification": "VALID_PASS"} for _ in range(10)], "with_mod")
        quota = {"scenarios": {"cold_login": {"attempts": [hist]}}}
        self.assertEqual(adj.adjudicate(quota, control, target, "compatible_client")["decision"],
                         "COMPATIBLE_CASE_UNRESOLVED")


def dummy_evidence():
    return type("Evidence", (), {"channel_rejection_seen": False, "unknown_custom_packet_seen": False})()


def complete_diag(overrides=None):
    data = {"last_client_marker": "HANDSHAKE_ACCEPTANCE_CLIENT_DISCONNECTED_SCREEN",
            "last_meaningful_client_marker": "HANDSHAKE_ACCEPTANCE_CLIENT_DISCONNECTED_SCREEN",
            "last_client_screen": None,
            "client_connection_phase": "CONNECTING",
            "elapsed_connect_seconds": 20,
            "client_game_process_alive": True,
            "server_game_process_alive": True,
            "tcp_state_summary": "NONE",
            "partialreload_marker_seen": False,
            "channel_rejection_seen": False,
            "unknown_custom_packet_seen": False,
            "player_present_in_rcon": False}
    if overrides:
        data.update(overrides)
    return data


def authorizable_attempt(cleanup="DEFAULT"):
    diagnostics = complete_diag({"last_client_screen": "DisconnectedScreen"})
    fingerprint = POLICY.canonical_fingerprint(dummy_evidence(), diagnostics)
    payload = json.loads(fingerprint)
    quality, missing = POLICY.assess_fingerprint_quality(payload)
    return {"classification": "INFRASTRUCTURE_FAILURE", "status": "failed",
            "functional_trial": None,
            "cleanup": {"status": "passed"} if cleanup == "DEFAULT" else cleanup,
            "attempt_evidence_schema_version": POLICY.ATTEMPT_EVIDENCE_SCHEMA_VERSION,
            "attempt_evidence": {"client_ready_seen": True, "connect_requested_seen": True,
                                 "network_login_seen": False, "server_absent_seen": False,
                                 "server_pending_seen": False, "server_compatible_seen": False,
                                 "network_logout_seen": False, "server_disconnected_seen": False},
            "fingerprint_diagnostics": diagnostics,
            "fingerprint": fingerprint,
            "fingerprint_quality": quality.value,
            "fingerprint_missing_fields": missing}


def test_versions(overrides=None):
    data = {"minecraft_version": "1.20.1", "forge_version": "47.4.10",
            "mapping_channel": "official", "mapping_version": "1.20.1",
            "java_version": 17}
    if overrides:
        data.update(overrides)
    return data


def full_matrix_report(server_mode, attempts, client_mode="without_mod"):
    infrastructure = sum(item.get("classification") == "INFRASTRUCTURE_FAILURE" for item in attempts)
    product = sum(item.get("classification") == "PRODUCT_FAILURE" for item in attempts)
    harness = sum(item.get("classification") == "HARNESS_FAILURE" for item in attempts)
    valid = sum(item.get("classification") == "VALID_PASS" for item in attempts)
    for item in attempts:
        item.setdefault("cleanup", {"status": "passed"})
    scope = POLICY.authorization_scope(client_mode, "CONTROL", test_versions())
    return {"server_mod_mode": server_mode,
            "client_mod_mode": client_mode,
            "authorization_scope": POLICY.scope_to_dict(scope),
            "server_build_mode": "independent_gradle_build" if server_mode == "without_mod" else "root_gradle_build",
            "server_main_mod_present": server_mode == "with_mod",
            "classpath_isolated": True if server_mode == "without_mod" else None,
            "partialreload_loaded": False if server_mode == "without_mod" else True,
            "partialreload_markers_seen": False,
            "diagnostic_matrix": True,
            "status": "diagnostic_passed",
            "complete_run": False,
            "cleanup": {"status": "passed"},
            "scenarios": {"cold_login": {"matrix_complete": True,
                                          "launch_attempts": len(attempts),
                                          "attempt_count": len(attempts),
                                          "valid_trials": valid,
                                          "infrastructure_failures": infrastructure,
                                          "product_failures": product,
                                          "harness_failures": harness,
                                          "attempts": attempts}}}


def load_adjudicator():
    script = pathlib.Path(__file__).resolve().parents[1] / "adjudicate-handshake-infrastructure.py"
    spec = importlib.util.spec_from_file_location("adjudicator", script)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    spec.loader.exec_module(module)
    return module


if __name__ == "__main__":
    unittest.main()
