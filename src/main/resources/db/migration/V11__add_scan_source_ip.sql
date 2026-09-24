ALTER TABLE scan_logs
    ADD COLUMN ip_address VARCHAR(45);

CREATE INDEX idx_scan_logs_ip_address ON scan_logs (ip_address);
