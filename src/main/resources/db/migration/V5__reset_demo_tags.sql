DELETE FROM scan_logs;
DELETE FROM nfc_tags;
DELETE FROM products;

INSERT INTO products (id, name, manufacturer, dpp_data)
VALUES
    ('77777777-7777-7777-7777-777777777771', 'Authentic Product One', 'Authentichain Team',
        '{"classification":"REAL","tagUid":"049D45521F1E90"}'::jsonb),
    ('77777777-7777-7777-7777-777777777772', 'Authentic Product Two', 'Authentichain Team',
        '{"classification":"REAL","tagUid":"041888521F1E90"}'::jsonb),
    ('88888888-8888-8888-8888-888888888881', 'Counterfeit Product One', 'Authentichain Team',
        '{"classification":"FAKE","tagUid":"045E0A521F1E90"}'::jsonb),
    ('88888888-8888-8888-8888-888888888882', 'Counterfeit Product Two', 'Authentichain Team',
        '{"classification":"FAKE","tagUid":"045173D2151990"}'::jsonb),
    ('99999999-9999-9999-9999-999999999991', 'Replay Test Product', 'Authentichain Team',
        '{"classification":"REPLAY_TEST","tagUid":"042166521F1E90"}'::jsonb);

INSERT INTO nfc_tags (id, product_id, tag_uid, aes_key, last_scan_counter, status)
VALUES
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1', '77777777-7777-7777-7777-777777777771',
        '049D45521F1E90', '00000000000000000000000000000000', 0, 'ACTIVE'),
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2', '77777777-7777-7777-7777-777777777772',
        '041888521F1E90', '00000000000000000000000000000000', 0, 'ACTIVE'),
    ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb1', '88888888-8888-8888-8888-888888888881',
        '045E0A521F1E90', '00000000000000000000000000000000', 0, 'REVOKED'),
    ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb2', '88888888-8888-8888-8888-888888888882',
        '045173D2151990', '00000000000000000000000000000000', 0, 'REVOKED'),
    ('cccccccc-cccc-cccc-cccc-ccccccccccc1', '99999999-9999-9999-9999-999999999991',
        '042166521F1E90', '00000000000000000000000000000000', 2147483647, 'ACTIVE');
