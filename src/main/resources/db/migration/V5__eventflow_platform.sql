CREATE TABLE user_role (
    user_id UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    role VARCHAR(40) NOT NULL,
    PRIMARY KEY (user_id, role)
);

ALTER TABLE users
    ADD COLUMN user_status VARCHAR(24) NOT NULL DEFAULT 'PENDING_VERIFICATION',
    ADD COLUMN user_email_verified_at TIMESTAMPTZ,
    ADD COLUMN user_google_subject VARCHAR(255) UNIQUE,
    ADD COLUMN user_deletion_requested_at TIMESTAMPTZ,
    ADD COLUMN user_created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN user_updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

CREATE TABLE auth_token (
    auth_token_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    token_type VARCHAR(32) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_auth_token_user_type ON auth_token(user_id, token_type);

CREATE TABLE user_session (
    session_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    refresh_token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_used_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    user_agent VARCHAR(500),
    ip_address VARCHAR(64)
);

CREATE TABLE notification_preference (
    user_id UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    channel VARCHAR(20) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (user_id, channel)
);

CREATE TABLE event (
    event_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_user_id UUID NOT NULL REFERENCES users(user_id),
    event_name VARCHAR(160) NOT NULL,
    event_type VARCHAR(60) NOT NULL,
    description TEXT,
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ,
    timezone VARCHAR(60) NOT NULL DEFAULT 'America/Guatemala',
    location VARCHAR(300),
    estimated_capacity INT CHECK (estimated_capacity IS NULL OR estimated_capacity > 0),
    budget NUMERIC(14,2) CHECK (budget IS NULL OR budget >= 0),
    status VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
    reentry_allowed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_event_owner ON event(owner_user_id);

CREATE TABLE event_collaborator (
    event_id UUID NOT NULL REFERENCES event(event_id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    permissions TEXT NOT NULL DEFAULT '',
    PRIMARY KEY (event_id, user_id)
);

CREATE TABLE module_catalog (
    module_code VARCHAR(8) PRIMARY KEY,
    module_name VARCHAR(120) NOT NULL,
    category VARCHAR(60) NOT NULL,
    description TEXT NOT NULL,
    globally_enabled BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE module_dependency (
    module_code VARCHAR(8) NOT NULL REFERENCES module_catalog(module_code),
    required_module_code VARCHAR(8) NOT NULL REFERENCES module_catalog(module_code),
    PRIMARY KEY(module_code, required_module_code)
);

CREATE TABLE event_module (
    event_id UUID NOT NULL REFERENCES event(event_id) ON DELETE CASCADE,
    module_code VARCHAR(8) NOT NULL REFERENCES module_catalog(module_code),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    display_order INT NOT NULL DEFAULT 0,
    featured BOOLEAN NOT NULL DEFAULT FALSE,
    configuration TEXT NOT NULL DEFAULT '{}',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY(event_id, module_code)
);

CREATE TABLE marketplace_offering (
    offering_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_user_id UUID NOT NULL REFERENCES users(user_id),
    offering_type VARCHAR(16) NOT NULL,
    name VARCHAR(160) NOT NULL,
    category VARCHAR(80),
    description TEXT,
    location VARCHAR(300),
    capacity INT,
    price NUMERIC(14,2) NOT NULL DEFAULT 0,
    attributes TEXT NOT NULL DEFAULT '{}',
    image_urls TEXT NOT NULL DEFAULT '[]',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    rating NUMERIC(3,2) NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE offering_availability (
    availability_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    offering_id UUID NOT NULL REFERENCES marketplace_offering(offering_id) ON DELETE CASCADE,
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    available BOOLEAN NOT NULL DEFAULT TRUE,
    CHECK (ends_at > starts_at)
);

CREATE TABLE reservation (
    reservation_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id UUID NOT NULL REFERENCES event(event_id),
    offering_id UUID NOT NULL REFERENCES marketplace_offering(offering_id),
    requester_user_id UUID NOT NULL REFERENCES users(user_id),
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    note TEXT,
    decided_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (ends_at > starts_at)
);
CREATE INDEX idx_reservation_offering_dates ON reservation(offering_id, starts_at, ends_at);

CREATE TABLE simulated_payment (
    payment_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reservation_id UUID NOT NULL REFERENCES reservation(reservation_id),
    amount NUMERIC(14,2) NOT NULL CHECK (amount > 0),
    status VARCHAR(20) NOT NULL,
    paid_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    reference VARCHAR(80) NOT NULL UNIQUE
);

CREATE TABLE invitation (
    invitation_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id UUID NOT NULL REFERENCES event(event_id) ON DELETE CASCADE,
    linked_user_id UUID REFERENCES users(user_id),
    guest_name VARCHAR(160) NOT NULL,
    guest_email VARCHAR(320),
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    token_expires_at TIMESTAMPTZ,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    allowed_capacity INT NOT NULL DEFAULT 1 CHECK (allowed_capacity > 0),
    companions TEXT NOT NULL DEFAULT '[]',
    table_label VARCHAR(80),
    seat_label VARCHAR(80),
    sector_label VARCHAR(80),
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE guest_access_log (
    access_log_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invitation_id UUID NOT NULL REFERENCES invitation(invitation_id) ON DELETE CASCADE,
    action VARCHAR(20) NOT NULL,
    quantity INT NOT NULL DEFAULT 1 CHECK (quantity > 0),
    performed_by UUID REFERENCES users(user_id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE agenda_item (
    agenda_item_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id UUID NOT NULL REFERENCES event(event_id) ON DELETE CASCADE,
    title VARCHAR(180) NOT NULL,
    description TEXT,
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    zone VARCHAR(120),
    responsible VARCHAR(160),
    status VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    capacity INT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (ends_at > starts_at)
);

CREATE TABLE agenda_favorite (
    agenda_item_id UUID NOT NULL REFERENCES agenda_item(agenda_item_id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    PRIMARY KEY(agenda_item_id, user_id)
);

CREATE TABLE assistance_request (
    assistance_request_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id UUID NOT NULL REFERENCES event(event_id) ON DELETE CASCADE,
    invitation_id UUID REFERENCES invitation(invitation_id),
    requester_user_id UUID REFERENCES users(user_id),
    assigned_user_id UUID REFERENCES users(user_id),
    category VARCHAR(80) NOT NULL,
    details TEXT,
    location VARCHAR(160),
    priority INT NOT NULL DEFAULT 0,
    status VARCHAR(24) NOT NULL DEFAULT 'RECEIVED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE notification (
    notification_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id UUID REFERENCES event(event_id) ON DELETE CASCADE,
    author_user_id UUID REFERENCES users(user_id),
    recipient_user_id UUID REFERENCES users(user_id),
    title VARCHAR(180) NOT NULL,
    body TEXT NOT NULL,
    audience_type VARCHAR(40) NOT NULL DEFAULT 'ALL',
    audience_value VARCHAR(160),
    channel VARCHAR(20) NOT NULL DEFAULT 'IN_APP',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE conversation_message (
    message_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id UUID REFERENCES event(event_id) ON DELETE CASCADE,
    reservation_id UUID REFERENCES reservation(reservation_id),
    sender_user_id UUID NOT NULL REFERENCES users(user_id),
    recipient_user_id UUID REFERENCES users(user_id),
    channel VARCHAR(60) NOT NULL,
    body TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE module_record (
    module_record_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id UUID NOT NULL REFERENCES event(event_id) ON DELETE CASCADE,
    module_code VARCHAR(8) NOT NULL REFERENCES module_catalog(module_code),
    record_type VARCHAR(60) NOT NULL,
    owner_user_id UUID REFERENCES users(user_id),
    invitation_id UUID REFERENCES invitation(invitation_id),
    parent_record_id UUID REFERENCES module_record(module_record_id),
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    title VARCHAR(180),
    payload TEXT NOT NULL DEFAULT '{}',
    capacity INT,
    current_count INT NOT NULL DEFAULT 0,
    starts_at TIMESTAMPTZ,
    ends_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CHECK (capacity IS NULL OR capacity >= 0),
    CHECK (current_count >= 0)
);
CREATE INDEX idx_module_record_scope ON module_record(event_id, module_code, record_type, status);

CREATE TABLE module_action (
    module_action_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    module_record_id UUID NOT NULL REFERENCES module_record(module_record_id) ON DELETE CASCADE,
    actor_user_id UUID REFERENCES users(user_id),
    invitation_id UUID REFERENCES invitation(invitation_id),
    action_type VARCHAR(40) NOT NULL,
    payload TEXT NOT NULL DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(module_record_id, actor_user_id, invitation_id, action_type)
);

CREATE TABLE review (
    review_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reservation_id UUID NOT NULL REFERENCES reservation(reservation_id),
    author_user_id UUID NOT NULL REFERENCES users(user_id),
    rating INT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(reservation_id, author_user_id)
);

CREATE TABLE moderation_report (
    report_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reporter_user_id UUID REFERENCES users(user_id),
    event_id UUID REFERENCES event(event_id),
    target_type VARCHAR(40) NOT NULL,
    target_id UUID NOT NULL,
    reason TEXT NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    resolution TEXT,
    resolved_by UUID REFERENCES users(user_id),
    resolved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE role_request (
    role_request_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(user_id),
    requested_role VARCHAR(40) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    decided_by UUID REFERENCES users(user_id),
    decided_at TIMESTAMPTZ,
    reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE audit_log (
    audit_log_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_user_id UUID REFERENCES users(user_id),
    event_id UUID REFERENCES event(event_id),
    action VARCHAR(80) NOT NULL,
    target_type VARCHAR(60),
    target_id UUID,
    details TEXT NOT NULL DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO module_catalog(module_code, module_name, category, description) VALUES
('AUT','Autenticación','Acceso','Identidad, credenciales y sesiones'),
('USR','Usuarios y perfiles','Acceso','Perfiles, roles y preferencias'),
('EVT','Proyectos y eventos','Organización','Ciclo de vida del evento'),
('ESP','Espacios','Marketplace','Publicación y búsqueda de espacios'),
('SRV','Servicios','Marketplace','Publicación y búsqueda de servicios'),
('RES','Reservaciones','Marketplace','Solicitudes y contratación'),
('PAY','Pagos simulados','Marketplace','Flujo académico de pago'),
('MSG','Mensajería','Social','Conversaciones contextuales'),
('CAL','Agenda','Organización','Agenda, ahora y siguiente'),
('MOD','Configuración modular','Organización','Catálogo y plantillas'),
('INV','Invitaciones y RSVP','Acceso','Invitaciones únicas y confirmación'),
('GST','Invitados y Event Pass','Acceso','Pase, QR y check-in'),
('MAP','Mapa y zonas','Organización','Zonas y navegación'),
('AST','Solicitudes','Atención','Atención durante el evento'),
('ORD','Menú y pedidos','Atención','Menú y seguimiento de pedidos'),
('QUE','Cola virtual','Atención','Turnos virtuales'),
('BKG','Reserva de actividades','Organización','Cupos de actividades'),
('INT','Participación','Participación','Encuestas, preguntas y juegos'),
('GAM','Gamificación','Participación','Pasaportes, misiones e insignias'),
('GAL','Galería','Social','Contenido colaborativo y moderación'),
('NET','Networking','Social','Perfiles e intercambio voluntario'),
('EXH','Expositores','Contenido','Stands y recursos comerciales'),
('SES','Sesiones y ponentes','Contenido','Ponentes, materiales y asistencia'),
('SPT','Torneos','Competencia','Participantes, brackets y resultados'),
('TRN','Transporte','Logística','Rutas, reservas y avisos'),
('LNF','Objetos perdidos','Logística','Reportes de objetos'),
('AFO','Aforo y servicios','Logística','Ocupación y estado operativo'),
('RSC','Recursos y certificados','Contenido','Materiales y certificados'),
('NOT','Notificaciones','Comunicación','Avisos y segmentación'),
('REV','Retroalimentación','Social','Encuestas y reseñas'),
('ADM','Administración','Administración','Supervisión global');

INSERT INTO module_dependency(module_code, required_module_code) VALUES
('GST','INV'),('MAP','EVT'),('AST','GST'),('ORD','GST'),('QUE','GST'),('BKG','CAL'),
('GAM','INT'),('NET','GST'),('EXH','MAP'),('SES','CAL'),('SPT','CAL'),('TRN','GST'),
('AFO','MAP'),('RSC','EVT'),('NOT','EVT'),('REV','RES');
