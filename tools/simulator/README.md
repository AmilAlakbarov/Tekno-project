# Development HSM/tag simulator

This program is a **software simulator for development and demonstrations**.
It is not a certified HSM, does not provide production key protection, and
must never replace a real HSM or factory provisioning station.

It lets the team demonstrate the future provisioning flow without new NFC
tags or a reader/writer:

1. Generate a custom UID and AES-128 key.
2. Keep the key in a local simulator vault.
3. Export a safe manifest for the dashboard.
4. Generate an NTAG 424 SDM-style URL.
5. Later submit a key-bearing manifest through a protected provisioning API.

It also provides a localhost-only development HSM HTTP service. The service
can create keys for requested UIDs and return a key to a local provisioning
client. It is intentionally bound to `127.0.0.1` and must never be deployed
to Render or exposed to the internet.

## Setup on Windows

```powershell
cd tools\simulator
py -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
```

## Create a simulated tag

```powershell
.\.venv\Scripts\python.exe tag_hsm_simulator.py generate `
  --uid 04ABCDEF123456 `
  --product-id demo-product-001 `
  --name "Demo blue bottle" `
  --description "Software-only provisioning demonstration"
```

If `--uid` is omitted, a random 7-byte UID beginning with `04` is created.
The generated `simulator_vault.json` contains test key material and must not
be committed or uploaded. It is ignored by the repository's simulator rules.

## Generate a verification URL

```powershell
.\.venv\Scripts\python.exe tag_hsm_simulator.py sign `
  --uid 04ABCDEF123456
```

Open the printed URL against the deployed backend only after that simulated
tag has been provisioned into the backend database. Repeating `sign` advances
the simulated counter; passing `--counter 1` intentionally creates a replay
test once the backend has already accepted a higher counter.

## Export

The default manifest excludes AES keys:

```powershell
.\.venv\Scripts\python.exe tag_hsm_simulator.py export
```

For the future protected provisioning API only, a development operator may
export keys explicitly:

```powershell
.\.venv\Scripts\python.exe tag_hsm_simulator.py export --include-keys
```

Do not send that file through email, commit it, or expose it to the browser.
The eventual production design should replace this simulator with an HSM
adapter where the backend receives a key reference or performs a secure
factory-side import.

## Local HSM service

Start the service:

```powershell
.\.venv\Scripts\python.exe tag_hsm_simulator.py serve
```

Create virtual tags and keys:

```powershell
Invoke-RestMethod http://127.0.0.1:8787/v1/hsm/keys `
  -Method Post -ContentType "application/json" `
  -Body '{"count":3,"productId":"demo-product-001","displayName":"Demo bottle"}'
```

Retrieve a development key for a specific UID:

```text
GET http://127.0.0.1:8787/v1/hsm/key?uid=04ABCDEF123456
```

Export the key-bearing CSV for the future local provisioning import:

```powershell
.\.venv\Scripts\python.exe tag_hsm_simulator.py export-csv
```
