ALTER TABLE event ADD COLUMN visibility VARCHAR(16) NOT NULL DEFAULT 'PRIVATE';
ALTER TABLE event ADD COLUMN allow_sales_while_running BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE event ADD COLUMN max_tickets_per_account INT NOT NULL DEFAULT 4 CHECK (max_tickets_per_account > 0);
ALTER TABLE event ADD COLUMN admission_capacity INT CHECK (admission_capacity IS NULL OR admission_capacity > 0);
ALTER TABLE event ADD COLUMN reserved_invitation_capacity INT NOT NULL DEFAULT 0 CHECK (reserved_invitation_capacity >= 0);
ALTER TABLE event ADD COLUMN allow_revoke_after_check_in BOOLEAN NOT NULL DEFAULT FALSE;
CREATE INDEX idx_event_public_catalog ON event(visibility, status, starts_at);

CREATE TABLE ticket_type (
    ticket_type_id UUID PRIMARY KEY,
    event_id UUID NOT NULL REFERENCES event(event_id) ON DELETE CASCADE,
    name VARCHAR(120) NOT NULL,
    description TEXT,
    price NUMERIC(14,2) NOT NULL CHECK (price >= 0),
    currency VARCHAR(3) NOT NULL,
    capacity INT NOT NULL CHECK (capacity > 0),
    max_per_order INT NOT NULL CHECK (max_per_order > 0),
    sales_start TIMESTAMPTZ NOT NULL,
    sales_end TIMESTAMPTZ NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_ticket_type_window CHECK (sales_end > sales_start)
);
CREATE INDEX idx_ticket_type_event ON ticket_type(event_id);

CREATE TABLE ticket_order (
    ticket_order_id UUID PRIMARY KEY,
    event_id UUID NOT NULL REFERENCES event(event_id) ON DELETE CASCADE,
    buyer_user_id UUID NOT NULL REFERENCES users(user_id),
    idempotency_key VARCHAR(120) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    total NUMERIC(14,2) NOT NULL CHECK (total >= 0),
    reference VARCHAR(40) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    cancelled_at TIMESTAMPTZ,
    UNIQUE (buyer_user_id, idempotency_key)
);
CREATE INDEX idx_ticket_order_event ON ticket_order(event_id);
CREATE INDEX idx_ticket_order_buyer ON ticket_order(buyer_user_id, created_at);

CREATE TABLE event_ticket (
    ticket_id UUID PRIMARY KEY,
    event_id UUID NOT NULL REFERENCES event(event_id) ON DELETE CASCADE,
    order_id UUID NOT NULL REFERENCES ticket_order(ticket_order_id) ON DELETE CASCADE,
    ticket_type_id UUID NOT NULL REFERENCES ticket_type(ticket_type_id),
    ordinal INT NOT NULL,
    type_name VARCHAR(120) NOT NULL,
    unit_price NUMERIC(14,2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    qr_hash VARCHAR(64) NOT NULL UNIQUE,
    checked_in BOOLEAN NOT NULL DEFAULT FALSE,
    ever_checked_in BOOLEAN NOT NULL DEFAULT FALSE,
    cancelled_at TIMESTAMPTZ,
    cancellation_reason TEXT,
    cancelled_by UUID REFERENCES users(user_id),
    UNIQUE (order_id, ordinal)
);
CREATE INDEX idx_event_ticket_event_status ON event_ticket(event_id, status);
CREATE INDEX idx_event_ticket_type_status ON event_ticket(ticket_type_id, status);

CREATE TABLE ticket_access_log (
    ticket_access_log_id UUID PRIMARY KEY,
    ticket_id UUID NOT NULL REFERENCES event_ticket(ticket_id) ON DELETE CASCADE,
    action VARCHAR(16) NOT NULL,
    performed_by UUID NOT NULL REFERENCES users(user_id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_ticket_access_log_ticket ON ticket_access_log(ticket_id, created_at);

CREATE TABLE ticket_settings_audit (
    audit_id UUID PRIMARY KEY,
    event_id UUID NOT NULL REFERENCES event(event_id) ON DELETE CASCADE,
    actor_user_id UUID NOT NULL REFERENCES users(user_id),
    before_snapshot TEXT NOT NULL,
    after_snapshot TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_ticket_settings_audit_event ON ticket_settings_audit(event_id, created_at);
