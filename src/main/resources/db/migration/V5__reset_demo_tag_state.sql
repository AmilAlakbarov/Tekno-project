DELETE FROM scan_logs;

UPDATE nfc_tags
SET last_scan_counter = 0,
    status = CASE
        WHEN tag_uid IN ('049D45521F1E90', '041888521F1E90') THEN 'ACTIVE'
        WHEN tag_uid IN ('045E0A521F1E90', '045173D2151990') THEN 'REVOKED'
        WHEN tag_uid = '042166521F1E90' THEN 'ACTIVE'
        ELSE status
    END
WHERE tag_uid IN (
    '042166521F1E90',
    '049D45521F1E90',
    '041888521F1E90',
    '045E0A521F1E90',
    '045173D2151990'
);

UPDATE nfc_tags
SET last_scan_counter = 2
WHERE tag_uid = '042166521F1E90';
