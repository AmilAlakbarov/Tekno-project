ALTER TABLE scan_logs
    ADD COLUMN received_counter INTEGER,
    ADD COLUMN expected_counter INTEGER;
