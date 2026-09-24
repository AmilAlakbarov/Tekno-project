ALTER TABLE nfc_tags
    ADD COLUMN display_name VARCHAR(255),
    ADD COLUMN description TEXT,
    ADD COLUMN image_url VARCHAR(1000);
