# Development HSM storage service

This is a separate, development-only HTTP service for storing UID/AES records
and performing AES-CMAC checks. It is intentionally independent of the Java
application and dashboard. Keys are stored in PostgreSQL and are never returned
by the API. This remains a development-oriented service; use a separately
secured database (and preferably a separate database or schema) for production
HSM data.

## Run locally

```powershell
cd tools/hsm-service
python -m pip install -r requirements.txt
$env:HSM_API_TOKEN="replace-with-a-long-random-token"
$env:HSM_DB_HOST="localhost"
$env:HSM_DB_PORT="5432"
$env:HSM_DB_NAME="hsm"
$env:HSM_DB_USER="hsm"
$env:HSM_DB_PASSWORD="replace-with-a-database-password"
# Alternatively, use one PostgreSQL connection string:
# $env:HSM_DATABASE_URL="postgresql://hsm:password@localhost:5432/hsm"
python app.py
```

The service creates its `keys` table automatically on startup. `PORT` is read
from the environment (default `8080`), and `HSM_SERVICE_TOKEN` (or the legacy
`HSM_API_TOKEN`) protects all non-health endpoints.

## Render and database persistence

The included `render.yaml` wires the HSM service to the managed
`product-auth-db` PostgreSQL instance using `HSM_DB_HOST`, `HSM_DB_PORT`,
`HSM_DB_NAME`, `HSM_DB_USER`, and `HSM_DB_PASSWORD`. Render supplies `PORT`.
This shared PostgreSQL database is acceptable for development, but production
should use a separate database or schema with appropriately restricted
credentials and backups. Do not use a filesystem/SQLite path or rely on a
Render web-service disk for key persistence.

To use another PostgreSQL instance, replace those Render `fromDatabase`
settings with environment values, or set `HSM_DATABASE_URL` as a secret. When
present, `HSM_DATABASE_URL` overrides the individual `HSM_DB_*` settings.

## API contract

All JSON requests use `Content-Type: application/json`. `POST` endpoints
require `Authorization: Bearer <HSM_API_TOKEN>`. Error responses are
`{"error":"..."}` and never contain key material.

### `GET /healthz`

Unauthenticated liveness check. Returns `200`:

```json
{"status":"ok","database":"ok"}

If PostgreSQL is unavailable, this endpoint returns HTTP `503` with
`{"status":"degraded","database":"unavailable"}`. A `200` health response
therefore confirms both the HTTP process and database connection.

### `GET /v1/keys`

Authenticated inventory check. It returns UIDs and storage timestamps only;
AES keys are never returned:

```json
{
  "count": 1,
  "keys": [
    {"uid": "04A1B2C3D4E5F6", "status": "stored", "createdAt": "..."}
  ]
}
```
```

### `POST /v1/keys/import`

Imports or replaces the key for a UID. `uid` is 8–32 hexadecimal characters
(4–16 bytes); `aes_key` is 32, 48, or 64 hexadecimal characters (AES-128,
AES-192, or AES-256).

Request:

```json
{"uid":"04A1B2C3D4E5F6","aes_key":"00000000000000000000000000000000"}
```

Response `200` (no key):

```json
{"uid":"04A1B2C3D4E5F6","status":"imported"}
```

### `POST /v1/cmac/verify`

Computes AES-CMAC using the stored key and compares it with `cmac`. Supply
exactly one message encoding: `message_hex` (up to 2048 bytes) or
`message_base64` (up to 2048 decoded bytes). `cmac` must be a 32-character
hexadecimal full AES-CMAC.

Request:

```json
{
  "uid":"04A1B2C3D4E5F6",
  "message_hex":"010203",
  "cmac":"..."
}
```

Response `200`:

```json
{"uid":"04A1B2C3D4E5F6","valid":true}
```

An unknown UID returns `404 {"error":"unknown_uid"}`. Invalid JSON, fields, or
hex values return `400`; missing or incorrect authentication returns `401`.

### `POST /v1/ntag424/verify`

Requires the same bearer token and uses the stored 16-byte AES key. It matches
the Java NTAG 424 SDM implementation: derive `SV2 || UID || reverse(counter)`
with `SV2 = 3CC300010080`, AES-CMAC the UTF-8 `mac_input`, then take CMAC
bytes at indexes 1, 3, 5, 7, 9, 11, 13, and 15. `uid` is exactly 14 hex
characters, `counter_hex` exactly 6, and `incoming_cmac` exactly 16.

Request:

```json
{
  "uid":"04A1B2C3D4E5F6",
  "counter_hex":"000013",
  "mac_input":"https://example.test/nfc?uid=04A1B2C3D4E5F6",
  "incoming_cmac":"..."
}
```

Response `200`: `{"uid":"04A1B2C3D4E5F6","valid":true}`. Keys are never
included in responses.
