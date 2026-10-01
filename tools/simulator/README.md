# Tag and HSM simulator

This Python tool creates software-only NTAG-style tags, holds their test keys
in a local vault, exports provisioning data, and generates signed SDM-style
URLs. It is for development and demonstrations only. It is not a certified
HSM and must never be exposed publicly or used for production keys.

## Setup on Windows

Open PowerShell in the simulator directory:

```powershell
Set-Location "C:\Users\Amil\Downloads\Tekno project\Tekno project\tools\simulator"
py -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
$Python = ".\.venv\Scripts\python.exe"
```

If the virtual environment already exists, set `$Python` and continue.

## Create a product and tags

First create a product in the dashboard under **Provisioning**. Copy its
complete product ID (a UUID). Then generate one or more simulator tags using
that ID:

```powershell
python tag_hsm_simulator.py generate `
  --count 5 `
  --product-id "PASTE-COMPLETE-PRODUCT-UUID-HERE" `
  --name "Demo product" `
  --description "Five virtual tags"
```

Each tag receives a unique 7-byte UID and random AES-128 key. To use a custom
UID instead, omit `--count` and add `--uid 04ABCDEF123456`.
The command also writes a key-bearing CSV for exactly that generated batch.
Pass `--csv-output ".\my-batch.csv"` to choose the file location.

The default files are:

- `simulator_vault.json` — local UIDs, AES keys, counters, and product data
- `provisioning_manifest.json` — safe manifest without keys
- `provisioning_keys.csv` — provisioning CSV containing keys

## Provision the tags to AuthentiChain

Use the batch CSV path printed by `generate`. To export the entire local
simulator vault instead:

```powershell
python tag_hsm_simulator.py export-csv `
  --output ".\provisioning_keys.csv"
```

In the dashboard, select **Provisioning → Import provisioning CSV**, choose
that file, and import it. The CSV requires `uid`, `aesKey`, and `productId`.
Every product ID must exactly match an existing dashboard product UUID.
To load a CSV into the local simulator vault, run:

```powershell
python tag_hsm_simulator.py import-csv --input ".\provisioning_keys.csv"
```

The simulator rejects duplicate UIDs in a file and refuses to overwrite an
existing UID with a different key. All key-bearing CSV files are highly
sensitive. The dashboard's batch-key export is available to administrators
only, requires confirmation, and retrieves keys from the configured HSM.

When the backend is configured with `HSM_BASE_URL` and `HSM_SERVICE_TOKEN`,
the backend sends the AES key to the HSM's authenticated
`POST /v1/keys/import` endpoint. The HSM stores it in PostgreSQL and returns
metadata only. Without a configured HSM, development keys may be stored by
the backend instead.

## Generate and test a signed URL

After provisioning, sign a tag using its UID:

```powershell
$url = & $Python tag_hsm_simulator.py sign `
  --uid 04ABCDEF123456 `
  --endpoint "https://authentichain.website/nfc/v1/verify"
Start-Process $url
```

Each normal `sign` call advances the simulator's local counter. Opening the
same URL twice tests replay detection. Changing one hexadecimal character in
the `cmac` query value tests tamper detection. The tag must already exist in
the backend and HSM for a successful scan.

## Test impossible travel with JSON scans

The travel-test helper submits two valid JSON scans with sequential counters.
It defaults to Baku for the first scan and Sanliurfa for the second, using
coordinates from the local simulator vault to generate valid CMACs:
Baku `40.4093, 49.8671` and Sanliurfa `37.1674, 38.7955`.

```powershell
& $Python .\test-impossible-travel.py --uid 04ABCDEF123456
```

The two scans must happen within roughly one hour for the Baku-to-Sanliurfa
distance to exceed the server's 1,000 km/h threshold. The JSON coordinates are
unverified advisory evidence. For physical NFC scans, the result page's
**Share location and check travel** button submits browser GPS after tag
authentication. When exact coordinates are unavailable, the backend can use
local GeoIP coordinates as a fallback and marks scans above the same threshold
as `SPEED_ANOMALY`. GeoIP is approximate and VPNs, proxies, and mobile-carrier
routing can cause false positives; treat this as a demonstration signal, not
proof of a tag clone or a person's location.

## Other useful commands

Export a manifest without keys:

```powershell
& $Python tag_hsm_simulator.py export `
  --output ".\provisioning_manifest.json"
```

Use an isolated vault:

```powershell
& $Python tag_hsm_simulator.py --vault ".\demo-vault.json" generate `
  --count 2 `
  --product-id "PASTE-COMPLETE-PRODUCT-UUID-HERE"
```

Start the localhost-only development HSM service:

```powershell
& $Python tag_hsm_simulator.py serve
```

It listens on `127.0.0.1:8787`. Check it from another PowerShell window:

```powershell
Invoke-RestMethod http://127.0.0.1:8787/health
```

Stop it with `Ctrl+C`. This separate simulator service is not the deployed
PostgreSQL-backed HSM service.

## Protect the test keys

`simulator_vault.json` and `provisioning_keys.csv` contain AES key material.
Keep them local, do not commit or share them, and do not display the vault
contents. The safe manifest does not contain AES keys. For production, use
real NTAG provisioning equipment and a properly secured, certified key
management system.
