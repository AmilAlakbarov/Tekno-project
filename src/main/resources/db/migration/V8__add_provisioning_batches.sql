CREATE TABLE provisioning_batches (
    id UUID PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NOT NULL,
    total_rows INTEGER NOT NULL,
    imported_rows INTEGER NOT NULL,
    duplicate_rows INTEGER NOT NULL,
    invalid_rows INTEGER NOT NULL,
    status VARCHAR(32) NOT NULL
);

ALTER TABLE nfc_tags
    ADD COLUMN provisioning_batch_id UUID,
    ADD CONSTRAINT fk_nfc_tags_provisioning_batch
        FOREIGN KEY (provisioning_batch_id) REFERENCES provisioning_batches (id);
