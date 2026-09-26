-- Segundo evento para probar la cartera multi-evento del invitado.
-- Token público: demo-congreso-2026

INSERT INTO event (event_id, owner_user_id, event_name, event_type, description, starts_at, ends_at,
                   timezone, location, estimated_capacity, budget, status, reentry_allowed)
VALUES ('bbbbbbbb-aaaa-4aaa-8aaa-aaaaaaaaaaaa', '11111111-1111-4111-8111-111111111111',
        'Innovation Summit Guatemala', 'CONFERENCE', 'Charlas, networking y experiencias sobre tecnología y creatividad.',
        now() + interval '12 days', now() + interval '12 days 10 hours', 'America/Guatemala',
        'Centro de Convenciones, Ciudad de Guatemala', 450, 210000.00, 'PUBLISHED', true);

INSERT INTO event_module (event_id, module_code, enabled, display_order, featured, configuration) VALUES
('bbbbbbbb-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'INV', true, 1, false, '{}'),
('bbbbbbbb-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'GST', true, 2, true, '{"showQr":true}'),
('bbbbbbbb-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'CAL', true, 3, true, '{}'),
('bbbbbbbb-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'MAP', true, 4, false, '{}'),
('bbbbbbbb-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'AST', true, 5, true, '{}'),
('bbbbbbbb-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'NOT', true, 6, false, '{}'),
('bbbbbbbb-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'SES', true, 7, false, '{}'),
('bbbbbbbb-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'NET', true, 8, false, '{}');

INSERT INTO invitation (invitation_id, event_id, linked_user_id, guest_name, guest_email, token_hash,
                        token_expires_at, status, allowed_capacity, companions, table_label, seat_label, sector_label)
VALUES ('f3333333-3333-4333-8333-333333333333', 'bbbbbbbb-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
        '22222222-2222-4222-8222-222222222222', 'Sofía Morales', 'demo.invitada@eventflow.gt',
        'f9b2301cbd2192b3ca5167c184adf05d96b91912d795f23d804c46ab761583e8',
        now() + interval '45 days', 'ACCEPTED', 1, '[]', null, 'B-24', 'Auditorio Principal');

INSERT INTO agenda_item (agenda_item_id, event_id, title, description, starts_at, ends_at, zone, responsible, status, capacity) VALUES
('b1000000-0000-4000-8000-000000000001', 'bbbbbbbb-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'Registro y café', 'Entrega de acreditaciones.', now() + interval '12 days', now() + interval '12 days 1 hour', 'Lobby', 'Equipo EventFlow', 'SCHEDULED', 450),
('b1000000-0000-4000-8000-000000000002', 'bbbbbbbb-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'Tecnología con propósito', 'Conferencia de apertura.', now() + interval '12 days 1 hour', now() + interval '12 days 2 hours', 'Auditorio Principal', 'Mariana López', 'SCHEDULED', 450),
('b1000000-0000-4000-8000-000000000003', 'bbbbbbbb-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'Networking regional', 'Conecta con participantes y empresas.', now() + interval '12 days 5 hours', now() + interval '12 days 6 hours 30 minutes', 'Terraza', 'Equipo de comunidad', 'SCHEDULED', 300);

INSERT INTO notification (notification_id, event_id, author_user_id, title, body, audience_type, channel, active)
VALUES ('b2000000-0000-4000-8000-000000000001', 'bbbbbbbb-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
        '11111111-1111-4111-8111-111111111111', 'Tu acreditación está lista',
        'Presenta tu Event Pass en el acceso rápido del lobby.', 'ALL', 'IN_APP', true);

INSERT INTO module_record (module_record_id, event_id, module_code, record_type, owner_user_id, status, title, payload, capacity, current_count) VALUES
('b3000000-0000-4000-8000-000000000001', 'bbbbbbbb-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'MAP', 'ZONE', '11111111-1111-4111-8111-111111111111', 'ACTIVE', 'Auditorio Principal', '{"description":"Charlas principales y asiento B-24"}', 450, 0),
('b3000000-0000-4000-8000-000000000002', 'bbbbbbbb-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'MAP', 'POINT', '11111111-1111-4111-8111-111111111111', 'ACTIVE', 'Mesa de acreditaciones', '{"description":"Acceso rápido con Event Pass"}', null, 0);

INSERT INTO audit_log (audit_log_id, actor_user_id, event_id, action, target_type, target_id, details)
VALUES ('bb000000-0000-4000-8000-000000000001', '11111111-1111-4111-8111-111111111111',
        'bbbbbbbb-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'DEMO_EVENT_CREATED', 'EVENT',
        'bbbbbbbb-aaaa-4aaa-8aaa-aaaaaaaaaaaa', '{"source":"flyway","version":9,"purpose":"multi-event"}');
