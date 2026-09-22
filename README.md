# Product Authentication API

Spring Boot REST API for prototype authentication of products using NTAG 424 DNA-style UID, counter, and CMAC data.

## Requirements

- Java 21
- Maven 3.9+
- PostgreSQL 14+
- Firebase project (optional for local development)

## Run

Start the project-specific PostgreSQL container on host port `5433` (host port `5432` is commonly occupied by a native PostgreSQL installation):

```powershell
docker run --name product-auth-postgres-tekno `
  -e POSTGRES_DB=product_auth `
  -e POSTGRES_USER=postgres `
  -e POSTGRES_PASSWORD=postgres `
  -p 5433:5432 `
  -d postgres:16
```

The application connects to `127.0.0.1:5433` by default. You can override it with:

```powershell
$env:DB_URL = "jdbc:postgresql://127.0.0.1:5433/product_auth"
$env:DB_USERNAME = "postgres"
$env:DB_PASSWORD = "postgres"
```

The Docker database created for local development uses `postgres` as both the username and password. If you previously set a different `DB_PASSWORD` or `SPRING_DATASOURCE_PASSWORD` in the terminal, VS Code launch configuration, or system environment, remove it or set it to `postgres` before starting the application. `SPRING_DATASOURCE_*` variables take precedence over this file.

Firebase is disabled by default. To enable Firestore mirroring and FCM alerts, set `FIREBASE_ENABLED=true` and provide either `FIREBASE_SERVICE_ACCOUNT_JSON` or application default credentials through `GOOGLE_APPLICATION_CREDENTIALS`.

```powershell
mvn spring-boot:run
```

## Verify endpoint

`POST /api/v1/verify`

```json
{
  "uid": "04A1B2C3D4E5F6",
  "ctr": "000042",
  "cmac": "8A9B7C6D5E4F3A2B",
  "latitude": 40.5858,
  "longitude": 49.6317
}
```

The prototype signature is AES-CMAC over the UID bytes followed by the three-byte big-endian counter. The first 8 CMAC bytes are compared with the incoming 16 hexadecimal characters. This is intentionally isolated and documented as a prototype; production NTAG 424 DNA SDM requires the product's exact SDM key/session derivation and URL/file-read configuration.

## Test data

Flyway migration `V2__insert_sample_data.sql` inserts one sample product and one active NFC tag:

```text
UID: 04A1B2C3D4E5F6
AES key: 000102030405060708090A0B0C0D0E0F
Initial counter: 0
```

Start the database and API:

```powershell
docker start product-auth-postgres-tekno
mvn spring-boot:run
```

In another PowerShell terminal, send a valid scan:

```powershell
$body = @{
  uid = "04A1B2C3D4E5F6"
  ctr = "000042"
  cmac = "B4CF78EAF9793BF9"
  latitude = 40.5858
  longitude = 49.6317
} | ConvertTo-Json

Invoke-RestMethod -Uri http://localhost:8080/api/v1/verify -Method Post -ContentType "application/json" -Body $body
```

Expected result:

```json
{
  "status": "REAL",
  "message": "Product is authentic.",
  "product": {
    "name": "Sample Product",
    "manufacturer": "Sample Manufacturer"
  }
}
```

Run the same request again to verify replay protection. It should return `FAKE` with `Replay attack detected.`. To test tampering, keep the UID and use a new counter such as `000043` with an incorrect CMAC. To test a missing tag, use a valid 14-character hexadecimal UID that is not `04A1B2C3D4E5F6`.

Inspect persisted results:

```powershell
docker exec product-auth-postgres-tekno psql -U postgres -d product_auth -c "select tag_uid, scan_result, scanned_at from scan_logs order by scanned_at desc;"
```

Migration `V3__insert_six_demo_products.sql` adds six more test products. The first three have active tags and are treated as real; the last three have revoked tags and are treated as fake:

```text
REAL  04A1B2C3D4E5F7  Real Product One
REAL  04A1B2C3D4E5F8  Real Product Two
REAL  04A1B2C3D4E5F9  Real Product Three
FAKE  04A1B2C3D4E5FA  Fake Product One
FAKE  04A1B2C3D4E5FB  Fake Product Two
FAKE  04A1B2C3D4E5FC  Fake Product Three
```

Query them with:

```powershell
docker exec product-auth-postgres-tekno psql -U postgres -d product_auth -c "select p.name, p.manufacturer, t.tag_uid, t.status from products p join nfc_tags t on t.product_id = p.id where t.tag_uid like '04A1B2C3D4E5%' order by t.tag_uid;"
```

The active tags require a CMAC generated with their stored AES key. The revoked tags return `FAKE` before CMAC verification, so they can be tested with any valid request shape and counter.

## Postman

Import `postman/product-auth-api.postman_collection.json` into Postman. Set the collection variable `baseUrl` to `http://localhost:8080`, start the API, and run the requests. The collection contains three active-tag requests that should return `REAL` and three revoked-tag requests that should return `FAKE`.

## Deploy to Render

The repository includes `Dockerfile` and `render.yaml` for deploying the API and a
PostgreSQL database as a Render Blueprint. Firebase is optional and is disabled by
default.

1. Push this project to a Git repository that Render can access.
2. In Render, choose **New > Blueprint** and select the repository.
3. Review the `product-auth-api` web service and `product-auth-db` database, then
   apply the Blueprint.
4. Wait for the deployment to finish. Flyway creates the schema and loads the
   sample migrations on the first startup.
5. Use the generated HTTPS service URL as the gateway's backend base URL:

   ```text
   https://authentichain-c3ky.onrender.com/api/v1/verify
   ```

The Render service receives database host, port, name, user, and password from the
managed database. Do not commit database passwords, Firebase service-account JSON,
or NTAG AES keys.

### Testing an NTAG 424 URL

The backend verifies NTAG 424 DNA SDM/SUN URLs at:

```text
GET https://authentichain-c3ky.onrender.com/nfc/v1/verify
```

Configure the tag's SDM/SUN NDEF URL to use this path. A generated URL looks like:

```text
https://authentichain-c3ky.onrender.com/nfc/v1/verify?uid=041888521F1E90&ctr=000013&cmac=9FDA395E5774C71C
```

The endpoint derives the NTAG 424 SDM session key, validates the URL CMAC, checks
the tag status, and rejects replayed counters. It returns a clean branded HTML
verification page rather than a JSON response. The page shows `REAL` only for an
active tag with a valid, newer counter. Revoked, unknown, invalid, and replayed
tags show `FAKE`. Migration `V5__reset_demo_tags.sql` resets the demonstration
database to two REAL tags, two FAKE tags, and one replay-test tag
(`042166521F1E90`). The all-zero AES key is for development only.
Replace it with unique production keys before shipping.

The current demo state is reset by `V5__reset_demo_tag_state.sql`:
`049D45521F1E90` and `041888521F1E90` are real, `045E0A521F1E90` and
`045173D2151990` are fake, and `042166521F1E90` is reserved for replay testing
with counter `000002`.

For the hosted demonstration service, `DATABASE_RESET_ON_STARTUP=true` clears
scan logs and resets counters whenever the application starts (including after
a Render deployment). The UID `042166521F1E90` is initialized at counter `2`,
so its captured counter `000002` is intentionally shown as a replay attack.
Disable this setting before production use because persistent replay protection
must not be reset on deployment.

Render database changes should be made with a new Flyway migration in
`src/main/resources/db/migration`, for example `V4__insert_real_tags.sql`. Do not
edit migrations that have already run. For one-off inspection or emergency
maintenance, use Render's database shell/connection details with `psql`.
