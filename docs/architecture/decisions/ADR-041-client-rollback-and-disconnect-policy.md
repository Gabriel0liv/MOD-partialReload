# ADR-041 — rollback e disconnect

Client ACK failure triggers compensating server rollback. A client that cannot
confirm the restored generation is disconnected rather than left inconsistent.
