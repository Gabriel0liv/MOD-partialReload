"""Small Minecraft RCON client used only by the local acceptance harness."""
from __future__ import annotations

import socket
import struct
from dataclasses import dataclass

SERVERDATA_RESPONSE_VALUE = 0
SERVERDATA_EXECCOMMAND = 2
SERVERDATA_AUTH = 3
SERVERDATA_AUTH_RESPONSE = 2


class RconError(RuntimeError):
    pass


@dataclass
class RconPacket:
    request_id: int
    packet_type: int
    payload: bytes


class RconClient:
    def __init__(self, host: str, port: int, password: str, timeout: float = 15.0,
                 max_response_bytes: int = 1_048_576):
        self.host = host
        self.port = port
        self.password = password
        self.timeout = timeout
        self.max_response_bytes = max_response_bytes
        self._socket: socket.socket | None = None
        self._next_id = 1

    def connect(self) -> None:
        self._socket = socket.create_connection((self.host, self.port), self.timeout)
        self._socket.settimeout(self.timeout)
        response = self._request(SERVERDATA_AUTH, self.password)
        if response.request_id == -1:
            raise RconError("RCON authentication rejected")
        if response.request_id <= 0:
            raise RconError("RCON authentication returned an invalid request id")

    def close(self) -> None:
        if self._socket is not None:
            try:
                self._socket.close()
            finally:
                self._socket = None

    def command(self, command: str) -> str:
        if self._socket is None:
            raise RconError("RCON client is not connected")
        response = self._request(SERVERDATA_EXECCOMMAND, command.lstrip("/"))
        return response.payload.decode("utf-8", errors="replace")

    def _request(self, packet_type: int, payload: str) -> RconPacket:
        assert self._socket is not None
        request_id = self._next_id
        self._next_id += 1
        body = struct.pack("<ii", request_id, packet_type) + payload.encode("utf-8") + b"\x00\x00"
        self._socket.sendall(struct.pack("<i", len(body)) + body)
        packets: list[RconPacket] = []
        total = 0
        while True:
            packet = self._read_packet()
            packets.append(packet)
            total += len(packet.payload)
            if total > self.max_response_bytes:
                raise RconError("RCON response exceeded configured size limit")
            if packet.request_id == -1:
                return packet
            # Vanilla sends a single packet for auth and usually one or more for commands.
            # A short read timeout after the first matching packet marks the end of a command.
            if packet.request_id == request_id:
                if packet_type == SERVERDATA_AUTH:
                    return packet
                self._socket.settimeout(0.20)
                try:
                    while True:
                        extra = self._read_packet()
                        if extra.request_id not in (request_id,):
                            raise RconError("RCON response request id mismatch")
                        packets.append(extra)
                        total += len(extra.payload)
                        if total > self.max_response_bytes:
                            raise RconError("RCON response exceeded configured size limit")
                except socket.timeout:
                    pass
                finally:
                    self._socket.settimeout(self.timeout)
                return RconPacket(request_id, packet.packet_type,
                                  b"".join(item.payload for item in packets
                                            if item.request_id == request_id))

    def _read_packet(self) -> RconPacket:
        assert self._socket is not None
        length_bytes = self._recv_exact(4)
        length = struct.unpack("<i", length_bytes)[0]
        if length < 10 or length > self.max_response_bytes + 16:
            raise RconError(f"invalid RCON packet length: {length}")
        data = self._recv_exact(length)
        request_id, packet_type = struct.unpack("<ii", data[:8])
        if data[-2:] != b"\x00\x00":
            raise RconError("malformed RCON packet terminator")
        return RconPacket(request_id, packet_type, data[8:-2])

    def _recv_exact(self, size: int) -> bytes:
        assert self._socket is not None
        chunks = bytearray()
        while len(chunks) < size:
            chunk = self._socket.recv(size - len(chunks))
            if not chunk:
                raise RconError("RCON socket closed unexpectedly")
            chunks.extend(chunk)
        return bytes(chunks)

