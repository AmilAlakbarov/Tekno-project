INSERT INTO products (id, name, manufacturer, dpp_data)
VALUES (
    '11111111-1111-1111-1111-111111111111',
    'Sample Product',
    'Sample Manufacturer',
    '{"batch":"DEMO-2026-001","material":"Recycled aluminum","countryOfOrigin":"Azerbaijan"}'::jsonb
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO nfc_tags (id, product_id, tag_uid, aes_key, last_scan_counter, status)
VALUES (
    '22222222-2222-2222-2222-222222222222',
    '11111111-1111-1111-1111-111111111111',
    '04A1B2C3D4E5F6',
    '000102030405060708090A0B0C0D0E0F',
    0,
    'ACTIVE'
)
ON CONFLICT (id) DO NOTHING;
