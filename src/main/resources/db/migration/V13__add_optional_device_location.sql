ALTER TABLE scan_logs
    ADD COLUMN device_latitude DOUBLE PRECISION,
    ADD COLUMN device_longitude DOUBLE PRECISION;

COMMENT ON COLUMN scan_logs.device_latitude IS
    'Optional user-shared browser device location for verified NFC scans; client-provided and untrusted.';
COMMENT ON COLUMN scan_logs.device_longitude IS
    'Optional user-shared browser device location for verified NFC scans; client-provided and untrusted.';
