CREATE TABLE investory.radar_notification_state (
    portfolio_id bigint NOT NULL REFERENCES investory.portfolios(id) ON DELETE CASCADE,
    rule_key varchar(160) NOT NULL,
    active boolean NOT NULL,
    sequence bigint NOT NULL CHECK (sequence >= 0),
    last_session date,
    streak integer NOT NULL CHECK (streak >= 0),
    primary_amount numeric,
    secondary_amount numeric,
    observed_at timestamp with time zone NOT NULL,
    PRIMARY KEY (portfolio_id, rule_key)
);
