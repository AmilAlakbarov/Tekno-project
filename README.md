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

Impossible-travel detection uses locally resolved GeoIP coordinates and compares
only with the latest successful scan. If the latest/current scan has no GeoIP
coordinates, that location anomaly cannot be checked. JSON scanner coordinates
remain recorded as explicitly untrusted client-provided evidence and are never
used as an authoritative travel baseline. Public NFC URLs obtain location from
the source IP when GeoIP is configured.

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
