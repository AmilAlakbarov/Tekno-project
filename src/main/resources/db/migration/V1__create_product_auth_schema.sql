CREATE TABLE products (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    manufacturer VARCHAR(255) NOT NULL,
    dpp_data JSONB
);

CREATE TABLE nfc_tags (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL,
    tag_uid VARCHAR(14) NOT NULL UNIQUE,
    aes_key VARCHAR(32) NOT NULL,
    last_scan_counter INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT fk_nfc_tags_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT chk_nfc_tags_status CHECK (status IN ('ACTIVE', 'REVOKED'))
);

CREATE INDEX idx_nfc_tags_tag_uid ON nfc_tags (tag_uid);

CREATE TABLE scan_logs (
    id UUID PRIMARY KEY,
    tag_uid VARCHAR(14) NOT NULL,
    scanned_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    scan_result VARCHAR(32) NOT NULL,
    CONSTRAINT chk_scan_logs_result CHECK (scan_result IN ('REAL', 'TAMPERED', 'REPLAY_ATTACK', 'SPEED_ANOMALY', 'NOT_FOUND'))
);

CREATE INDEX idx_scan_logs_tag_uid ON scan_logs (tag_uid);
CREATE INDEX idx_scan_logs_tag_uid_scanned_at ON scan_logs (tag_uid, scanned_at DESC);
