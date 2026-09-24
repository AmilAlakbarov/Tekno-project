# Authentichain

Authentichain is a Spring Boot service that verifies NTAG 424 DNA product
identities. It validates the tag UID, counter, and SDM CMAC, protects against
replayed scans, records scan results in PostgreSQL, and shows a clean branded
verification page when a tag URL is opened.

## Stack

- Java 21 and Spring Boot 3.5
- PostgreSQL with Flyway migrations
- JPA/Hibernate
- Bouncy Castle AES-CMAC
- Docker and Render deployment
- Render-hosted PostgreSQL admin dashboard integration

## Run locally

Requirements: Java 21, Maven 3.9+, Docker, and PostgreSQL.

Start the development database:

```powershell
docker run --name product-auth-postgres-tekno `
  -e POSTGRES_DB=product_auth `
  -e POSTGRES_USER=postgres `
  -e POSTGRES_PASSWORD=postgres `
  -p 5433:5432 `
  -d postgres:16
```

Start the API:

```powershell
mvn spring-boot:run
```

The default local database is `jdbc:postgresql://127.0.0.1:5433/product_auth`.
Override `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` when required.

## NFC verification

The tag URL must point to:

```text
https://authentichain.website/nfc/v1/verify
```

A generated request has this shape:

```text
https://authentichain.website/nfc/v1/verify?uid=041888521F1E90&ctr=000013&cmac=9FDA395E5774C71C
```

The service:

1. Derives the NTAG 424 SDM session key.
2. Validates the URL CMAC.
3. Checks the tag status.
4. Rejects counters that are not newer than the stored counter.
5. Stores the scan result.
6. Returns an HTML result page branded **Authentichain Team**.

An active tag with a valid, newer counter shows **REAL**. Unknown, revoked,
tampered, invalid, or replayed scans show **FAKE**.

## Fraud and anomaly detection

The verifier records several fraud signals in Security events:

- **Replay attack**: the received counter is not greater than the last
  accepted counter for that UID.
- **Speed anomaly**: two scans with coordinates imply travel faster than the
  configured limit, currently `1000 km/h`.
- **Tampered signature**: the UID, counter, URL data, or CMAC does not
  validate.
- **Unknown or revoked tag**: the UID is not registered or has been disabled.
- **Counter jump**: a valid signature advances the counter by more than 1,000,
  which can indicate a copied tag or an unusual reader workflow.

An anomaly is logged and does not advance the trusted counter. The public NFC
browser URL cannot provide trusted GPS coordinates by itself. Reliable
location rules require a controlled scanner/mobile app that sends authenticated
location data or a server-side scanner location. Client-supplied coordinates
alone must not be treated as proof of physical location.

Each verification also stores the source IP address. The dashboard shows it
for security events. An IP address gives an approximate network location, not
the person's exact physical location. To turn IPs into countries/cities, use
a privacy-reviewed GeoIP database such as MaxMind GeoLite2 on the backend;
do not call an external geolocation service for every scan. Behind Render,
the backend reads the trusted `X-Forwarded-For` value.

## Demonstration tags

Migration `V5__reset_demo_tags.sql` creates the five-tag demonstration set:

| UID | Expected result |
|---|---|
| `049D45521F1E90` | REAL |
| `041888521F1E90` | REAL |
| `045E0A521F1E90` | FAKE |
| `045173D2151990` | FAKE |
| `042166521F1E90` | REPLAY ATTACK |

The replay demonstration starts with a high stored counter, so its normal
captured counter is rejected. The current all-zero AES key is for development
only. Use unique private keys before production.

## Admin dashboard API

The Render-native admin dashboard reads PostgreSQL through these Spring Boot
endpoints; it does not use Firebase:

```text
GET  /api/v1/admin/overview
GET  /api/v1/admin/tags?page=0&size=25&uid=
GET  /api/v1/admin/scans
GET  /api/v1/admin/security-events
GET  /api/v1/admin/scans/locations
GET  /api/v1/admin/products
POST /api/v1/admin/products
POST /api/v1/admin/provisioning/import
POST /api/v1/admin/tags/{uid}/revoke
DELETE /api/v1/admin/tags/{uid}
DELETE /api/v1/admin/products/{productId}
POST /api/v1/admin/tags/{uid}/activate
GET  /api/v1/admin/accounts
POST /api/v1/admin/accounts
DELETE /api/v1/admin/accounts/{id}
```

Dashboard access uses session-based accounts. Configure `ADMIN_USERNAME` and
`ADMIN_PASSWORD` on the backend before the first deployment; the account is
created by Flyway/application startup only when it does not already exist.
Passwords are stored as BCrypt hashes. `ADMIN` can manage accounts,
`OPERATOR` can manage tags and provisioning, and `VIEWER` has read-only
dashboard access. The initial password is not changed automatically after the
account is created, so rotate it by creating a replacement account and
removing the bootstrap account after signing in as the replacement.

## Development provisioning simulator

Until a real HSM and NFC reader/writer are available, the repository includes
a separate software-only simulator in `tools/simulator`. It generates custom
UIDs, AES-128 test keys, provisioning manifests, and NTAG 424 SDM-style URLs.
It is suitable for demonstrating provisioning and dashboard flows only; it is
not a production HSM and must not protect real product keys. See
`tools/simulator/README.md`.

Set `FRONTEND_ORIGINS` in Render to the deployed dashboard origin. The admin
routes require an authenticated account session.

## React admin dashboard

The Vite dashboard lives in `dashboard/` and consumes the admin API above with
Axios polling (there is no Firebase dependency). Run it locally with:

```powershell
cd dashboard
npm install
$env:VITE_API_URL="http://localhost:8080"
npm run dev
```

`VITE_API_URL` is optional and defaults to `http://localhost:8080`. Set it to the
deployed Spring Boot origin when hosting the dashboard separately. The dashboard
includes Overview, Tag registry, and Security events views and refreshes live data
every 15 seconds.

The Provisioning view can create products and import the CSV exported by the
desktop virtual-tag simulator. The backend validates each UID, AES-128 key,
and product ID before registering the tag. When `HSM_BASE_URL` and
`HSM_SERVICE_TOKEN` are configured, each imported key is also stored in the
separate HSM service and new imported tags omit the plaintext key from
PostgreSQL. Use the same secret value for `HSM_SERVICE_TOKEN` on both Render
services.

The Render Blueprint also defines a static site named `authentichain-dashboard`.
After deployment, optionally attach `admin.authentichain.website` to that
static site. Set the backend `FRONTEND_ORIGIN` value to the dashboard's actual
origin before using the admin routes from a browser.

## API

The original JSON API remains available for computer gateways:

```text
POST /api/v1/verify
```

Example body:

```json
{
  "uid": "04A1B2C3D4E5F6",
  "ctr": "000042",
  "cmac": "8A9B7C6D5E4F3A2B",
  "latitude": 40.5858,
  "longitude": 49.6317
}
```

The NFC browser endpoint is separate:

```text
GET /nfc/v1/verify
```

## Flyway migrations

Keep all migrations in `src/main/resources/db/migration`:

| Migration | Purpose |
|---|---|
| `V1__create_product_auth_schema.sql` | Creates the products, tags, and scan-log tables. |
| `V2__insert_sample_data.sql` | Historical sample data migration. |
| `V3__insert_six_demo_products.sql` | Historical prototype demo data migration. |
| `V4__insert_project_tags.sql` | Historical NTAG project data migration. |
| `V5__reset_demo_tags.sql` | Current five-tag demonstration state and replay setup. |
| `V6__add_scan_counter_details.sql` | Stores received and expected scan counters. |
| `V7__add_tag_metadata.sql` | Adds optional tag display metadata. |
| `V8__add_provisioning_batches.sql` | Adds provisioning batch tracking. |
| `V9__move_new_tag_keys_to_hsm.sql` | Allows HSM-backed tags to omit plaintext keys from PostgreSQL. |

Do not delete or rename an applied migration. Flyway stores each applied
version in the database, and removing an old file can make deployment fail
validation. Add a new migration for future data changes instead. `V5` is the
current reset migration; it is not safe to run repeatedly against a database
manually because it replaces the demo rows.

## Render deployment

The repository contains [Dockerfile](./Dockerfile) and
[render.yaml](./render.yaml) for a Render Blueprint.

1. Push the repository to GitHub.
2. In Render, choose **New > Blueprint** and select the repository.
3. Use the web service name `authentichain`.
4. Confirm the custom public URL is `https://authentichain.website`.
5. Redeploy after changing the tag URL hostname.

The hostname is part of the signed SDM URL. After changing from another
hostname, reconfigure the tag's SDM/SUN URL so the tag generates a new CMAC.
Do not reuse a CMAC generated for the old hostname.

Render supplies database connection variables through the managed PostgreSQL
instance. Never commit database passwords or production AES keys.

## Project structure

- `src/main/java` - API, verification, and persistence
- `src/main/resources/db/migration` - immutable Flyway schema and data migrations
- `src/test/java` - service tests
Run tests with:

```powershell
mvn test
```
