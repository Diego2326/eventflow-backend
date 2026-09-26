-- Dataset de demostración coherente para recorrer el sistema completo.
-- Token público de la invitación: demo-sofia-2026
-- Acceso del organizador: demo.organizador@eventflow.gt / EventFlow#2026

INSERT INTO users (user_id, user_name, user_email, user_phone_prefix_id, user_phone_number,
                   user_password_hash, user_birth_date, user_nationality_country_code,
                   user_status, user_email_verified_at)
VALUES
('11111111-1111-4111-8111-111111111111', 'Andrea Castillo', 'demo.organizador@eventflow.gt', (SELECT phone_prefix_id FROM phone_prefix WHERE phone_prefix_country_code='GT'), '55551001', '$2a$10$/duvU4seZcVKTlz7RjJ/m.ndB135kqijkVWVP1c9CR9do58JocT8i', '1992-04-12', 'GT', 'ACTIVE', now()),
('22222222-2222-4222-8222-222222222222', 'Sofía Morales', 'demo.invitada@eventflow.gt', (SELECT phone_prefix_id FROM phone_prefix WHERE phone_prefix_country_code='GT'), '55551002', '$2a$10$/duvU4seZcVKTlz7RjJ/m.ndB135kqijkVWVP1c9CR9do58JocT8i', '1997-08-23', 'GT', 'ACTIVE', now()),
('33333333-3333-4333-8333-333333333333', 'Casa Santo Domingo', 'demo.proveedor@eventflow.gt', (SELECT phone_prefix_id FROM phone_prefix WHERE phone_prefix_country_code='GT'), '55551003', '$2a$10$/duvU4seZcVKTlz7RjJ/m.ndB135kqijkVWVP1c9CR9do58JocT8i', '1988-02-10', 'GT', 'ACTIVE', now());

INSERT INTO user_role (user_id, role) VALUES
('11111111-1111-4111-8111-111111111111', 'USER'),
('11111111-1111-4111-8111-111111111111', 'ORGANIZER'),
('11111111-1111-4111-8111-111111111111', 'ADMIN'),
('22222222-2222-4222-8222-222222222222', 'USER'),
('33333333-3333-4333-8333-333333333333', 'USER'),
('33333333-3333-4333-8333-333333333333', 'SERVICE_PROVIDER');

INSERT INTO auth_token (auth_token_id, user_id, token_hash, token_type, expires_at, used_at)
VALUES ('10000000-0000-4000-8000-000000000001', '22222222-2222-4222-8222-222222222222', '1d2e46e8fcd417f19ffedf31fdd10de421335c0ef91b4e7057cc0dffd66f3089', 'EMAIL_VERIFICATION', now() + interval '7 days', now());

INSERT INTO user_session (session_id, user_id, refresh_token_hash, expires_at, user_agent, ip_address)
VALUES ('10000000-0000-4000-8000-000000000002', '11111111-1111-4111-8111-111111111111', 'fd38351c89b74b5401e341c67263f9eef5a702e77cf1ce7c3d24d17a1f3bda75', now() + interval '30 days', 'EventFlow Demo', '127.0.0.1');

INSERT INTO notification_preference (user_id, channel, enabled) VALUES
('11111111-1111-4111-8111-111111111111', 'EMAIL', true),
('11111111-1111-4111-8111-111111111111', 'PUSH', true),
('22222222-2222-4222-8222-222222222222', 'IN_APP', true);

INSERT INTO event (event_id, owner_user_id, event_name, event_type, description, starts_at, ends_at,
                   timezone, location, estimated_capacity, budget, status, reentry_allowed)
VALUES ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', '11111111-1111-4111-8111-111111111111',
        'Boda de Andrea y Mateo', 'WEDDING', 'Una celebración entre jardines, música y personas queridas.',
        now() + interval '2 days', now() + interval '2 days 9 hours', 'America/Guatemala',
        'Casa Santo Domingo, Antigua Guatemala', 180, 145000.00, 'PUBLISHED', true);

INSERT INTO event_collaborator (event_id, user_id, permissions)
VALUES ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', '33333333-3333-4333-8333-333333333333', 'CHECK_IN,ASSISTANCE,MODULE_DATA');

INSERT INTO event_module (event_id, module_code, enabled, display_order, featured, configuration)
SELECT 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', module_code, true,
       row_number() OVER (ORDER BY module_code)::int,
       module_code IN ('GST','CAL','AST'),
       CASE module_code WHEN 'GST' THEN '{"showQr":true}' WHEN 'MAP' THEN '{"showAssignment":true}' ELSE '{}' END
FROM module_catalog;

INSERT INTO marketplace_offering (offering_id, owner_user_id, offering_type, name, category, description,
                                  location, capacity, price, attributes, image_urls, status, rating)
VALUES
('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb1', '33333333-3333-4333-8333-333333333333', 'SPACE', 'Jardines del Claustro', 'Bodas', 'Espacio colonial con jardines y capilla.', 'Antigua Guatemala', 220, 65000, '{"parking":true,"indoor":true}', '["https://images.example.com/jardin.jpg"]', 'ACTIVE', 4.85),
('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb2', '33333333-3333-4333-8333-333333333333', 'SERVICE', 'Banquete Chapín', 'Catering', 'Menú de temporada y atención en mesa.', 'Antigua Guatemala', 250, 28500, '{"dietaryOptions":["vegetariano","sin gluten"]}', '[]', 'ACTIVE', 4.70);

INSERT INTO offering_availability (availability_id, offering_id, starts_at, ends_at, available)
VALUES ('cccccccc-cccc-4ccc-8ccc-cccccccccccc', 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb1', now() + interval '2 days', now() + interval '2 days 10 hours', true);

INSERT INTO reservation (reservation_id, event_id, offering_id, requester_user_id, starts_at, ends_at, status, note, decided_at)
VALUES ('dddddddd-dddd-4ddd-8ddd-dddddddddddd', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb1', '11111111-1111-4111-8111-111111111111', now() + interval '2 days', now() + interval '2 days 9 hours', 'COMPLETED', 'Montaje desde las 10:00.', now() - interval '12 days');

INSERT INTO simulated_payment (payment_id, reservation_id, amount, status, reference)
VALUES ('eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee', 'dddddddd-dddd-4ddd-8ddd-dddddddddddd', 65000, 'PAID', 'EF-DEMO-2026-001');

INSERT INTO invitation (invitation_id, event_id, linked_user_id, guest_name, guest_email, token_hash,
                        token_expires_at, status, allowed_capacity, companions, table_label, seat_label, sector_label)
VALUES
('f1111111-1111-4111-8111-111111111111', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', '22222222-2222-4222-8222-222222222222', 'Sofía Morales', 'demo.invitada@eventflow.gt', '25cda9187d4ab7b14a130183acf3d554400a6ee031bd9210e90cb2a8d1b4a431', now() + interval '30 days', 'ACCEPTED', 2, '["Carlos Morales"]', 'Mesa 8', '12', 'Jardín'),
('f2222222-2222-4222-8222-222222222222', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', null, 'Lucía Herrera', 'lucia@example.com', '424d719f94125d696655456c46bef7cb0baeed578a204f4cf5037fd309f3fc1b', now() + interval '30 days', 'PENDING', 1, '[]', 'Mesa 4', '7', 'Salón');

INSERT INTO guest_access_log (access_log_id, invitation_id, action, quantity, performed_by, created_at) VALUES
('f3000000-0000-4000-8000-000000000001', 'f1111111-1111-4111-8111-111111111111', 'CHECK_IN', 1, '11111111-1111-4111-8111-111111111111', now() - interval '10 minutes'),
('f3000000-0000-4000-8000-000000000002', 'f1111111-1111-4111-8111-111111111111', 'CHECK_OUT', 1, '11111111-1111-4111-8111-111111111111', now() - interval '5 minutes');

INSERT INTO agenda_item (agenda_item_id, event_id, title, description, starts_at, ends_at, zone, responsible, status, capacity) VALUES
('a1000000-0000-4000-8000-000000000001', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'Ceremonia', 'Acompáñanos en la capilla.', now() + interval '2 days', now() + interval '2 days 1 hour', 'Capilla', 'Coordinación EventFlow', 'SCHEDULED', 180),
('a1000000-0000-4000-8000-000000000002', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'Cóctel de bienvenida', 'Bebidas y bocadillos en el jardín.', now() + interval '2 days 1 hour 15 minutes', now() + interval '2 days 2 hours 15 minutes', 'Jardín', 'Banquete Chapín', 'SCHEDULED', 180),
('a1000000-0000-4000-8000-000000000003', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'Cena y celebración', 'Cena, brindis y pista de baile.', now() + interval '2 days 2 hours 30 minutes', now() + interval '2 days 8 hours', 'Salón Mayor', 'Andrea Castillo', 'SCHEDULED', 180);

INSERT INTO agenda_favorite (agenda_item_id, user_id)
VALUES ('a1000000-0000-4000-8000-000000000003', '22222222-2222-4222-8222-222222222222');

INSERT INTO assistance_request (assistance_request_id, event_id, invitation_id, requester_user_id, assigned_user_id, category, details, location, priority, status)
VALUES ('a2000000-0000-4000-8000-000000000001', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'f1111111-1111-4111-8111-111111111111', '22222222-2222-4222-8222-222222222222', '33333333-3333-4333-8333-333333333333', 'UBICACION', 'Necesito indicaciones para encontrar mi mesa.', 'Entrada del jardín', 10, 'ON_THE_WAY');

INSERT INTO notification (notification_id, event_id, author_user_id, recipient_user_id, title, body, audience_type, audience_value, channel, active, created_at) VALUES
('a3000000-0000-4000-8000-000000000001', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', '11111111-1111-4111-8111-111111111111', null, '¡Bienvenidos!', 'La ceremonia inicia puntualmente. Te recomendamos llegar 30 minutos antes.', 'ALL', null, 'IN_APP', true, now() - interval '1 hour'),
('a3000000-0000-4000-8000-000000000002', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', '11111111-1111-4111-8111-111111111111', null, 'Información para el Jardín', 'El acceso al jardín está junto al claustro principal.', 'SECTOR', 'Jardín', 'IN_APP', true, now() - interval '30 minutes');

INSERT INTO conversation_message (message_id, event_id, sender_user_id, recipient_user_id, channel, body)
VALUES ('a4000000-0000-4000-8000-000000000001', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', '11111111-1111-4111-8111-111111111111', '22222222-2222-4222-8222-222222222222', 'EVENT', '¡Qué alegría contar contigo, Sofía!');

INSERT INTO module_record (module_record_id, event_id, module_code, record_type, owner_user_id, status, title, payload, capacity, current_count, starts_at, ends_at) VALUES
('a5000000-0000-4000-8000-000000000001', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'MAP', 'ZONE', '11111111-1111-4111-8111-111111111111', 'ACTIVE', 'Capilla', '{"description":"Ceremonia y bienvenida","floor":"Nivel 1"}', 180, 0, null, null),
('a5000000-0000-4000-8000-000000000002', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'MAP', 'POINT', '11111111-1111-4111-8111-111111111111', 'ACTIVE', 'Jardín', '{"description":"Cóctel y Mesa 8","icon":"garden"}', 200, 0, null, null),
('a5000000-0000-4000-8000-000000000003', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'INT', 'POLL', '11111111-1111-4111-8111-111111111111', 'ACTIVE', '¿Qué canción quieres escuchar?', '{"options":["Salsa","Pop","Merengue"]}', null, 1, now(), now() + interval '3 days'),
('a5000000-0000-4000-8000-000000000004', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'ORD', 'MENU_ITEM', '33333333-3333-4333-8333-333333333333', 'ACTIVE', 'Tamalito de chipilín', '{"price":35,"dietary":"vegetariano"}', 80, 12, null, null),
('a5000000-0000-4000-8000-000000000005', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'GAL', 'PHOTO', '22222222-2222-4222-8222-222222222222', 'ACTIVE', 'Recuerdo de la invitada', '{"caption":"Listos para celebrar"}', null, 0, null, null);

INSERT INTO module_action (module_action_id, module_record_id, actor_user_id, invitation_id, action_type, payload)
VALUES ('a6000000-0000-4000-8000-000000000001', 'a5000000-0000-4000-8000-000000000003', '22222222-2222-4222-8222-222222222222', null, 'VOTE', '{"option":"Salsa"}');

INSERT INTO review (review_id, reservation_id, author_user_id, rating, comment)
VALUES ('a7000000-0000-4000-8000-000000000001', 'dddddddd-dddd-4ddd-8ddd-dddddddddddd', '11111111-1111-4111-8111-111111111111', 5, 'Excelente coordinación, espacio precioso y atención puntual.');

INSERT INTO moderation_report (report_id, reporter_user_id, event_id, target_type, target_id, reason, status, resolution, resolved_by, resolved_at)
VALUES ('a8000000-0000-4000-8000-000000000001', '22222222-2222-4222-8222-222222222222', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'MODULE_RECORD', 'a5000000-0000-4000-8000-000000000005', 'Contenido duplicado en la galería.', 'RESOLVED', 'Se conservó la publicación original.', '11111111-1111-4111-8111-111111111111', now());

INSERT INTO role_request (role_request_id, user_id, requested_role, status, decided_by, decided_at, reason)
VALUES ('a9000000-0000-4000-8000-000000000001', '33333333-3333-4333-8333-333333333333', 'SERVICE_PROVIDER', 'APPROVED', '11111111-1111-4111-8111-111111111111', now() - interval '20 days', 'Proveedor verificado para eventos en Antigua Guatemala.');

INSERT INTO audit_log (audit_log_id, actor_user_id, event_id, action, target_type, target_id, details)
VALUES ('aa000000-0000-4000-8000-000000000001', '11111111-1111-4111-8111-111111111111', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'DEMO_DATA_CREATED', 'EVENT', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', '{"source":"flyway","version":8}');

INSERT INTO file_asset (file_asset_id, event_id, uploader_user_id, module_code, object_path, original_name, content_type, size_bytes, active)
VALUES ('ab000000-0000-4000-8000-000000000001', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', '11111111-1111-4111-8111-111111111111', 'RSC', 'demo/boda-andrea-mateo/programa.pdf', 'Programa del evento.pdf', 'application/pdf', 248320, true);
