SET search_path TO investory, public;

CREATE TABLE investory.drawdown_alert_state (
    id bigint PRIMARY KEY CHECK (id = 1),
    peak_equity numeric(30,12) NOT NULL DEFAULT 0 CHECK (peak_equity >= 0),
    last_alert_at timestamptz
);

COMMENT ON TABLE investory.drawdown_alert_state IS
    'Singleton durable state for peak-to-trough drawdown notifications and their cooldown.';
