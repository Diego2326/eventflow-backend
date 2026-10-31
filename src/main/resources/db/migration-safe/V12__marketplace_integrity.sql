CREATE INDEX idx_offering_search
    ON marketplace_offering(offering_type, status, category, created_at DESC);

CREATE UNIQUE INDEX ux_simulated_payment_paid_reservation
    ON simulated_payment(reservation_id) WHERE status = 'PAID';
