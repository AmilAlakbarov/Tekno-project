"""Small development HSM-like HTTP service.

The service deliberately returns metadata only. AES keys remain inside the
PostgreSQL-backed service and are never included in an HTTP response.
"""

from __future__ import annotations

import base64
import binascii
import hmac
import json
import os
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import Any

from cryptography.hazmat.primitives.cmac import CMAC
from cryptography.hazmat.primitives.ciphers import algorithms
import psycopg


DATABASE_URL = os.environ.get("HSM_DATABASE_URL")
DB_CONFIG = {
    "host": os.environ.get("HSM_DB_HOST", "localhost"),
    "port": os.environ.get("HSM_DB_PORT", "5432"),
    "dbname": os.environ.get("HSM_DB_NAME", "hsm"),
    "user": os.environ.get("HSM_DB_USER", "hsm"),
    "password": os.environ.get("HSM_DB_PASSWORD", ""),
}
API_TOKEN = os.environ.get("HSM_SERVICE_TOKEN", os.environ.get("HSM_API_TOKEN", ""))
MAX_BODY_BYTES = 64 * 1024


def _database() -> psycopg.Connection:
    connection = (
        psycopg.connect(DATABASE_URL, connect_timeout=10)
        if DATABASE_URL
        else psycopg.connect(connect_timeout=10, **DB_CONFIG)
    )
    connection.execute(
        """
        CREATE TABLE IF NOT EXISTS keys (
            uid TEXT PRIMARY KEY,
            aes_key BYTEA NOT NULL,
            created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
        )
        """
    )
    connection.commit()
    return connection


def _hex_value(value: Any, field: str, *, lengths: set[int]) -> bytes:
    if not isinstance(value, str):
        raise ValueError(f"{field} must be a hexadecimal string")
    try:
        decoded = bytes.fromhex(value)
    except ValueError as error:
        raise ValueError(f"{field} must be a hexadecimal string") from error
    if len(decoded) not in lengths:
        expected = "Bearer " + API_TOKEN
        raise ValueError(f"{field} must contain {expected} hex characters")
    return decoded


def _message(body: dict[str, Any]) -> bytes:
    if "message_hex" in body and "message_base64" in body:
        raise ValueError("provide only one of message_hex or message_base64")
    if "message_hex" in body:
        return _hex_value(body["message_hex"], "message_hex", lengths=set(range(0, 2049)))
    encoded = body.get("message_base64")
    if not isinstance(encoded, str):
        raise ValueError("message_hex or message_base64 is required")
    try:
        result = base64.b64decode(encoded, validate=True)
    except (ValueError, binascii.Error) as error:
        raise ValueError("message_base64 must be valid base64") from error
    if len(result) > 2048:
        raise ValueError("message must not exceed 2048 bytes")
    return result


def _aes_cmac(key: bytes, message: bytes) -> bytes:
    signer = CMAC(algorithms.AES(key))
    signer.update(message)
    return signer.finalize()


def _json_response(handler: BaseHTTPRequestHandler, status: HTTPStatus, payload: dict[str, Any]) -> None:
    encoded = json.dumps(payload, separators=(",", ":")).encode("utf-8")
    handler.send_response(status)
    handler.send_header("Content-Type", "application/json")
    handler.send_header("Content-Length", str(len(encoded)))
    handler.end_headers()
    handler.wfile.write(encoded)


class HsmHandler(BaseHTTPRequestHandler):
    server_version = "DevHSM/1.0"

    def log_message(self, format: str, *args: Any) -> None:
        return

    def do_GET(self) -> None:
        if self.path != "/healthz":
            _json_response(self, HTTPStatus.NOT_FOUND, {"error": "not_found"})
            return
        _json_response(self, HTTPStatus.OK, {"status": "ok"})

    def do_POST(self) -> None:
        if self.path not in {"/v1/keys/import", "/v1/cmac/verify", "/v1/ntag424/verify"}:
            _json_response(self, HTTPStatus.NOT_FOUND, {"error": "not_found"})
            return
        if not self._authenticated():
            _json_response(self, HTTPStatus.UNAUTHORIZED, {"error": "unauthorized"})
            return
        try:
            body = self._read_json()
            if self.path == "/v1/keys/import":
                self._import_key(body)
            elif self.path == "/v1/cmac/verify":
                self._verify_cmac(body)
            else:
                self._verify_ntag424(body)
        except ValueError as error:
            _json_response(self, HTTPStatus.BAD_REQUEST, {"error": str(error)})
        except psycopg.Error:
            _json_response(self, HTTPStatus.INTERNAL_SERVER_ERROR, {"error": "storage_error"})

    def _authenticated(self) -> bool:
        supplied = self.headers.get("Authorization", "")
        expected = "Bearer " + API_TOKEN
        return bool(API_TOKEN) and hmac.compare_digest(supplied, expected)

    def _read_json(self) -> dict[str, Any]:
        length_header = self.headers.get("Content-Length")
        try:
            length = int(length_header or "0")
        except ValueError as error:
            raise ValueError("Content-Length must be an integer") from error
        if length <= 0 or length > MAX_BODY_BYTES:
            raise ValueError("request body is missing or too large")
        try:
            payload = json.loads(self.rfile.read(length))
        except json.JSONDecodeError as error:
            raise ValueError("request body must be valid JSON") from error
        if not isinstance(payload, dict):
            raise ValueError("request body must be a JSON object")
        return payload

    def _import_key(self, body: dict[str, Any]) -> None:
        uid = _hex_value(body.get("uid"), "uid", lengths=set(range(4, 17)))
        aes_key = _hex_value(body.get("aes_key"), "aes_key", lengths={16, 24, 32})
        uid_text = uid.hex().upper()
        with _database() as connection:
            connection.execute(
                "INSERT INTO keys(uid, aes_key) VALUES(%s, %s) "
                "ON CONFLICT(uid) DO UPDATE SET aes_key=excluded.aes_key",
                (uid_text, aes_key),
            )
        _json_response(self, HTTPStatus.OK, {"uid": uid_text, "status": "imported"})

    def _verify_cmac(self, body: dict[str, Any]) -> None:
        uid = _hex_value(body.get("uid"), "uid", lengths=set(range(4, 17)))
        provided = _hex_value(body.get("cmac"), "cmac", lengths={16})
        message = _message(body)
        uid_text = uid.hex().upper()
        with _database() as connection:
            row = connection.execute("SELECT aes_key FROM keys WHERE uid=%s", (uid_text,)).fetchone()
        if row is None:
            _json_response(self, HTTPStatus.NOT_FOUND, {"error": "unknown_uid"})
            return
        valid = hmac.compare_digest(_aes_cmac(bytes(row[0]), message), provided)
        _json_response(self, HTTPStatus.OK, {"uid": uid_text, "valid": valid})

    def _verify_ntag424(self, body: dict[str, Any]) -> None:
        """Match SignatureVerificationService.matchesNtag424Sdm exactly."""
        uid = _hex_value(body.get("uid"), "uid", lengths={7})
        counter = _hex_value(body.get("counter_hex"), "counter_hex", lengths={3})
        incoming = _hex_value(body.get("incoming_cmac"), "incoming_cmac", lengths={8})
        mac_input = body.get("mac_input")
        if not isinstance(mac_input, str):
            raise ValueError("mac_input must be a string")
        if len(mac_input.encode("utf-8")) > 2048:
            raise ValueError("mac_input must not exceed 2048 UTF-8 bytes")
        uid_text = uid.hex().upper()
        with _database() as connection:
            row = connection.execute("SELECT aes_key FROM keys WHERE uid=%s", (uid_text,)).fetchone()
        if row is None:
            _json_response(self, HTTPStatus.NOT_FOUND, {"error": "unknown_uid"})
            return
        if len(row[0]) != 16:
            _json_response(self, HTTPStatus.OK, {"uid": uid_text, "valid": False})
            return

        session_vector = bytes.fromhex("3CC300010080") + uid + counter[::-1]
        session_key = _aes_cmac(bytes(row[0]), session_vector)
        full_cmac = _aes_cmac(session_key, mac_input.encode("utf-8"))
        truncated = bytes(full_cmac[index] for index in range(1, 16, 2))
        valid = hmac.compare_digest(truncated, incoming)
        _json_response(self, HTTPStatus.OK, {"uid": uid_text, "valid": valid})


def main() -> None:
    host = os.environ.get("HOST", "0.0.0.0")
    port = int(os.environ.get("PORT", "8080"))
    _database().close()
    server = ThreadingHTTPServer((host, port), HsmHandler)
    print(f"development HSM listening on {host}:{port}", flush=True)
    server.serve_forever()


if __name__ == "__main__":
    main()
