UPDATE products
SET name = 'Replay Test Product',
    manufacturer = 'Authentichain Demo',
    dpp_data = '{"classification":"REPLAY_ATTACK","tagUid":"042166521F1E90"}'::jsonb
WHERE id = '77777777-7777-7777-7777-777777777771';

UPDATE products
SET dpp_data = '{"classification":"REAL","tagUid":"049D45521F1E90"}'::jsonb
WHERE id = '77777777-7777-7777-7777-777777777772';

UPDATE products
SET dpp_data = '{"classification":"REAL","tagUid":"041888521F1E90"}'::jsonb
WHERE id = '77777777-7777-7777-7777-777777777773';

UPDATE nfc_tags
SET last_scan_counter = 2
WHERE tag_uid = '042166521F1E90';
