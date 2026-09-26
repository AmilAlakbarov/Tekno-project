ALTER TABLE scan_logs
    ADD COLUMN geo_country VARCHAR(255),
    ADD COLUMN geo_country_iso_code VARCHAR(2),
    ADD COLUMN geo_region VARCHAR(255),
    ADD COLUMN geo_city VARCHAR(255),
    ADD COLUMN geo_latitude DOUBLE PRECISION,
    ADD COLUMN geo_longitude DOUBLE PRECISION;

COMMENT ON COLUMN scan_logs.latitude IS
    'Coordinates supplied by a JSON scanner client; untrusted evidence and not used as an authoritative travel baseline.';
COMMENT ON COLUMN scan_logs.longitude IS
    'Coordinates supplied by a JSON scanner client; untrusted evidence and not used as an authoritative travel baseline.';
COMMENT ON COLUMN scan_logs.geo_latitude IS
    'Coordinates derived locally from the GeoLite2 City database for successfully verified scans.';
COMMENT ON COLUMN scan_logs.geo_longitude IS
    'Coordinates derived locally from the GeoLite2 City database for successfully verified scans.';
