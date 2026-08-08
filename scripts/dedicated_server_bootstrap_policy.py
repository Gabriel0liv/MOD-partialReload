"""Pure fail-closed policy for dedicated acceptance server bootstrap."""
from __future__ import annotations

from dataclasses import dataclass
from enum import Enum
import re
from typing import Iterable


class StartupState(str, Enum):
    PROCESS_STARTED = "PROCESS_STARTED"
    FORGE_BOOTSTRAP_OBSERVED = "FORGE_BOOTSTRAP_OBSERVED"
    SERVER_DONE_OBSERVED = "SERVER_DONE_OBSERVED"
    RCON_LISTENING = "RCON_LISTENING"
    RCON_AUTHENTICATED = "RCON_AUTHENTICATED"
    PRODUCT_MARKER_OBSERVED = "PRODUCT_MARKER_OBSERVED"
    PROCESS_EXITED = "PROCESS_EXITED"
    TIMEOUT = "TIMEOUT"


class StartupClassification(str, Enum):
    PASSED = "PASSED"
    PRODUCT_FAILURE = "PRODUCT_FAILURE"
    HARNESS_FAILURE = "HARNESS_FAILURE"
    INFRA_TRANSIENT = "INFRA_TRANSIENT"
    ENVIRONMENT_FAILURE = "ENVIRONMENT_FAILURE"


@dataclass(frozen=True)
class StartupAssessment:
    classification: StartupClassification
    reason: str
    states: tuple[StartupState, ...]


PRODUCT_ERROR = re.compile(
    r"(?:com\.gabriel0liv\.partialreload.*(?:Exception|Error)|"
    r"TAG_RECIPE_|TAG_REGISTRY_|MAPPED_REGISTRY_|ADVANCEMENT_COMMIT_)", re.I
)
ENVIRONMENT_ERROR = re.compile(
    r"(?:OutOfMemoryError|No space left|Access is denied|Permission denied|"
    r"UnsupportedClassVersionError|Could not reserve enough space)", re.I
)


def observe_states(lines: Iterable[str], *, authenticated: bool = False,
                   exited: bool = False, timed_out: bool = False) -> tuple[StartupState, ...]:
    text = "\n".join(lines)
    states: list[StartupState] = [StartupState.PROCESS_STARTED]
    if re.search(r"ModLauncher running|Launching target 'forgeserveruserdev'", text):
        states.append(StartupState.FORGE_BOOTSTRAP_OBSERVED)
    if re.search(r"Done \([0-9.]+s\)! For help", text):
        states.append(StartupState.SERVER_DONE_OBSERVED)
    if re.search(r"RCON running on ", text):
        states.append(StartupState.RCON_LISTENING)
    if authenticated:
        states.append(StartupState.RCON_AUTHENTICATED)
    if re.search(r"PartialReloadMod|CLIENT_HANDSHAKE_FOUNDATION_CHANNEL_REGISTERED", text):
        states.append(StartupState.PRODUCT_MARKER_OBSERVED)
    if exited:
        states.append(StartupState.PROCESS_EXITED)
    if timed_out:
        states.append(StartupState.TIMEOUT)
    return tuple(states)


def classify_startup(lines: Iterable[str], *, authenticated: bool = False,
                     exited: bool = False, exit_code: int | None = None,
                     timed_out: bool = False) -> StartupAssessment:
    material = tuple(lines)
    text = "\n".join(material)
    states = observe_states(material, authenticated=authenticated, exited=exited,
                            timed_out=timed_out)
    if authenticated:
        return StartupAssessment(StartupClassification.PASSED, "RCON_AUTHENTICATED", states)
    if PRODUCT_ERROR.search(text):
        return StartupAssessment(StartupClassification.PRODUCT_FAILURE,
                                 "PRODUCT_ERROR_DURING_BOOTSTRAP", states)
    if ENVIRONMENT_ERROR.search(text):
        return StartupAssessment(StartupClassification.ENVIRONMENT_FAILURE,
                                 "PERSISTENT_ENVIRONMENT_ERROR", states)
    if re.search(r"Unable to initialise RCON.*\n(?:.*\n)?java\.net\.BindException: Address already in use", text):
        return StartupAssessment(StartupClassification.INFRA_TRANSIENT,
                                 "RCON_PORT_BIND_RACE_AFTER_DONE", states)
    if exited:
        return StartupAssessment(StartupClassification.HARNESS_FAILURE,
                                 f"PROCESS_EXITED_BEFORE_RCON:exit_code={exit_code}", states)
    if timed_out:
        return StartupAssessment(StartupClassification.HARNESS_FAILURE,
                                 "BOOTSTRAP_TIMEOUT_WITHOUT_CAUSE", states)
    return StartupAssessment(StartupClassification.HARNESS_FAILURE,
                             "BOOTSTRAP_EVIDENCE_INCOMPLETE", states)


def retry_allowed(classification: StartupClassification, cleanup_passed: bool,
                  attempt_number: int, maximum_attempts: int = 3) -> bool:
    return (classification == StartupClassification.INFRA_TRANSIENT
            and cleanup_passed and 1 <= attempt_number < maximum_attempts)
