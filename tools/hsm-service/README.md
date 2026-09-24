# Development HSM storage service

This is a separate, development-only HTTP service for storing UID/AES records
and performing AES-CMAC checks. It is intentionally independent of the Java
application and dashboard. Keys are stored in SQLite and are never returned by
the API. Render's free tier cannot use persistent disks, so a free deployment
has ephemeral storage and its key store can be reset on restart or redeploy.
Do not use it for real production keys.

## Run locally

```powershell
cd tools/hsm-service
python -m pip install -r requirements.txt
$env:HSM_API_TOKEN="replace-with-a-long-random-token"
$env:HSM_DB_PATH="hsm.sqlite3"
python app.py
```

For Render, create a Python web service with this directory as its root,
set `HSM_SERVICE_TOKEN` as a secret environment variable, and use
`HSM_DATABASE_PATH=/tmp/hsm.sqlite3`. Render supplies `PORT`; the container
listens on it. Use a paid persistent disk or a managed encrypted database
before production.

## API contract

All JSON requests use `Content-Type: application/json`. `POST` endpoints
require `Authorization: Bearer <HSM_API_TOKEN>`. Error responses are
`{"error":"..."}` and never contain key material.

### `GET /healthz`

Unauthenticated liveness check. Returns `200`:

```json
{"status":"ok"}
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
