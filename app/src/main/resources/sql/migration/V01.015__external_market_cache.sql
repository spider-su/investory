-- Public market research only. No portfolio facts or credentials are persisted here.
CREATE TABLE investory.external_market_cache (
    provider_profile varchar(100) NOT NULL,
    tool varchar(40) NOT NULL,
    symbol varchar(20) NOT NULL,
    data text,
    fetched_at timestamptz,
    expires_at timestamptz,
    blocked_until timestamptz,
    blocked_reason varchar(30),
    PRIMARY KEY (provider_profile, tool, symbol)
);
CREATE TABLE investory.external_market_attempt (
    provider_profile varchar(100) NOT NULL,
    reserved_at timestamptz NOT NULL
);
CREATE INDEX external_market_attempt_window ON investory.external_market_attempt(provider_profile, reserved_at);
CREATE TABLE investory.external_market_provider_state (
    provider_profile varchar(100) PRIMARY KEY,
    blocked_until timestamptz NOT NULL
);
