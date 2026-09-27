# AuthentiChain

AuthentiChain is a product-authentication system for NTAG 424 DNA tags. A scan
is checked against its UID, AES-CMAC signature, tag status, and one-time
counter. The dashboard manages products, tags, accounts, provisioning, and
security events.

## What it uses

- Java 21, Spring Boot, Spring Security, and Maven for the API
- PostgreSQL and Flyway for application data and schema migrations
- A Python HSM-like service for private AES-key storage and CMAC verification
- React and Vite for the admin dashboard
- A separate static product website in `product-site/`
- Docker and Render configuration for deployment
- A Python virtual-tag simulator for development and demonstrations

The simulator and HSM service are development components, not certified
hardware security modules. Do not use demo keys for real products.

## Main features

- NTAG 424 SDM URL and CMAC verification
- Replay, tampered signature, unknown/revoked tag, counter-jump, and
  coordinate-based impossible-travel checks
- Session-cookie dashboard login with `ADMIN`, `OPERATOR`, and `VIEWER` roles
- Product/tag management, CSV provisioning, security-event and scan history
- HSM inventory containing key metadata only; AES keys are not returned
- Source-IP recording and optional local MaxMind GeoLite2 City enrichment for
  successfully verified scans; client IPs are never sent to a lookup service

Impossible-travel detection compares the latitude and longitude submitted with
each successful JSON verification against the latest successful scan. When
available, a previously shared device location is preferred for the prior
scan; otherwise its submitted coordinates are used. This is user-provided
location evidence: it may be inaccurate or spoofed and is an advisory fraud
signal, not proof of physical location. GeoIP remains separate contextual
evidence and does not drive the travel-speed decision. Public NFC URL scans do
not include coordinates, so they cannot contribute a location to this check.

The dashboard overview map uses OpenStreetMap tiles and plots up to 100 recent
verified scan locations, preferring user-shared device GPS when available and
otherwise using approximate GeoIP points. After a successful NFC verification,
the result page offers an optional browser location-permission prompt. Declining
does not affect NFC authentication. Shared device coordinates are user-provided,
may be inaccurate or spoofed, and are stored as separate, untrusted evidence;
they are not used for impossible-travel decisions. Map tile requests go from the
dashboard browser to OpenStreetMap and disclose the requested map area and
browser IP to the tile provider; scan IPs are still resolved locally. The
overview activity chart reports daily verified and flagged scan counts for the
last seven UTC days.

### Impossible-travel test helper

Run `python scripts\test-impossible-travel.py --uid <14-hex-characters>` from
PowerShell. The helper reads the matching AES key from the local
`tools\simulator\simulator_vault.json` file and defaults to the deployed API at
`https://authentichain.website`. It starts with the next counter after the
counter stored for that tag and saves the second scan's counter back to the
vault, so repeated runs do not reuse locally generated counters. Use `--vault`,
`--backend-url`, or `--first-counter` to override the defaults; an explicit
counter must be greater than the vault's saved counter. It never prints the
AES key.

The helper submits successive valid JSON scans at Baku and London coordinates.
The second scan should be flagged as `SPEED_ANOMALY` when the scans are close
together in time. Use a newly provisioned tag with no prior successful scans so
the Baku scan establishes the baseline. If the backend has already accepted a
higher counter than the local vault records, set `--first-counter` above the
backend's current accepted counter. The speed check uses submitted coordinates,
not request IP or GeoIP; those coordinates are not independently verified and
must be treated as an advisory signal. Requests still store normal IP/GeoIP
context when available.

## Public product website

`product-site/` contains the public-facing AuthentiChain overview, separate
from the React admin dashboard. The Render blueprint defines it as the static
site `authentichain-product-site`; syncing `render.yaml` creates the additional
service. It can use the Render-provided `onrender.com` address or a custom
domain configured in Render. The roadmap on the site is explicitly labeled as
future direction rather than shipped functionality.

## Local development

Requirements: Java 21, Maven, Docker, Node.js, and Python 3.

Start PostgreSQL:

```powershell
docker run --name product-auth-postgres `
  -e POSTGRES_DB=product_auth `
  -e POSTGRES_USER=postgres `
  -e POSTGRES_PASSWORD=postgres `
  -p 5433:5432 `
  -d postgres:16
```

Run the API from the repository root:

```powershell
mvn spring-boot:run
```

Run the dashboard in another PowerShell window:

```powershell
Set-Location .\dashboard
npm install
$env:VITE_API_URL = "http://localhost:8080"
npm run dev
```

The dashboard API URL defaults to `http://localhost:8080`. Configure database
variables if your local PostgreSQL credentials differ.

For local IP geolocation, obtain a MaxMind GeoLite2 City `.mmdb` database and
set `GEOIP_DATABASE_PATH` to its absolute path (or set
`app.geoip.database-path` in Spring configuration). If unset, GeoIP enrichment
is disabled; if set to an unreadable or invalid database, the API fails startup
rather than silently running without the configured database.

For Render, configure the backend's `GEOIP_LICENSE_KEY` secret using a license
key from your MaxMind account. On startup, the container downloads the GeoLite2
City database to `/tmp` and sets `GEOIP_DATABASE_PATH` automatically. The file
is not committed or persisted on Render's ephemeral filesystem; a new deploy
downloads it again. If the secret is unset, verification continues with GeoIP
disabled. Keep the license key private and maintain the database according to
MaxMind's terms. Successful scan locations are approximate network locations,
not proof of a user's physical location.

## Verification endpoints

- `GET /nfc/v1/verify?uid=...&ctr=...&cmac=...` — public tag URL
- `POST /api/v1/verify` — JSON scanner integration; requires UID, counter,
  CMAC, latitude, and longitude

An NFC URL's host and path are included in its signed data. If the public host
changes, reconfigure the tag's SDM URL and generate a new CMAC.

## Dashboard accounts

Configure `ADMIN_USERNAME` and `ADMIN_PASSWORD` before the first deployment.
The initial account is created if it does not already exist; changing those
environment variables later does not reset an existing password. Passwords
are stored as BCrypt hashes.

- `ADMIN`: all dashboard functions and account management
- `OPERATOR`: product, tag, and provisioning management
- `VIEWER`: read-only access

Dashboard authentication uses server-side sessions and cookies, not JWT.
Unsafe dashboard requests require a CSRF token obtained through the frontend's
CSRF bootstrap endpoint.

## Provisioning and HSM

In the dashboard, create a product and copy its product ID. Generate tags for
that product with the simulator, export `provisioning_keys.csv`, then upload
the CSV under **Provisioning**. The backend sends each key to the HSM when
`HSM_BASE_URL` and `HSM_SERVICE_TOKEN` are configured.
Both the JSON scanner CMAC and NTAG 424 SDM CMAC are then verified inside the
HSM; AES keys remain outside the application database.

Set the same secret `HSM_SERVICE_TOKEN` on the backend and HSM services. The
HSM database configuration is provided by `HSM_DB_HOST`, `HSM_DB_PORT`,
`HSM_DB_NAME`, `HSM_DB_USER`, and `HSM_DB_PASSWORD` (or `HSM_DATABASE_URL`).
Check HSM health at `/healthz`; authenticated inventory is available at
`/v1/keys` and returns UIDs/status, never AES keys.

Optional IP geolocation uses the free MaxMind GeoLite2 City database. Set
`GEOIP_LICENSE_KEY` as a secret on the `authentichain` backend Render service;
create the key in your MaxMind account and add it under **Render → authentichain
→ Environment**. The container downloads the database at startup to `/tmp`.
Without the key, verification continues but GeoIP enrichment is disabled.
Redeploy the backend periodically to fetch the latest database; the file is
ephemeral and is not committed or stored in PostgreSQL. IP locations are
approximate and are recorded only for cryptographically verified scans. The
dashboard includes MaxMind's required data attribution.

Simulator setup, common commands, and key-handling notes are in
[tools/simulator/README.md](./tools/simulator/README.md).
The full product-to-HSM provisioning and scan-testing workflow is in
[PRODUCT_AND_HSM_GUIDE.md](./PRODUCT_AND_HSM_GUIDE.md).

## Tests and deployment

Run backend tests:

```powershell
mvn test
```

Build the dashboard:

```powershell
Set-Location .\dashboard
npm run build
```

The repository includes `Dockerfile` and `render.yaml` for Render deployment.
Never commit passwords, service tokens, simulator vaults, or key-bearing CSVs.
Do not remove or rename Flyway migrations that have already been applied;
create a new migration for future schema changes.
