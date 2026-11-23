-- Stateful assistant conversations; never persist raw prompts, tokens or provider payloads.
-- Every read/write must be scoped by both portfolio_id and authenticated principal.
CREATE TABLE investory.assistant_conversation (
    id uuid PRIMARY KEY,
    portfolio_id bigint NOT NULL,
    principal varchar(255) NOT NULL,
    topic varchar(48),
    asset varchar(40),
    reporting_period varchar(40),
    selected_plan varchar(255),
    state_version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    expires_at timestamptz NOT NULL DEFAULT (now() + interval '30 days')
);
CREATE INDEX assistant_conversation_scope_idx
    ON investory.assistant_conversation (portfolio_id, principal, updated_at DESC);
CREATE INDEX assistant_conversation_expiry_idx
    ON investory.assistant_conversation (expires_at);

-- Scenario versions contain assumptions only. A submitted what-if must be recalculated
-- by the canonical retirement engine before any comparison is displayed.
CREATE TABLE investory.assistant_scenario_revision (
    conversation_id uuid NOT NULL REFERENCES investory.assistant_conversation(id) ON DELETE CASCADE,
    revision integer NOT NULL CHECK (revision > 0),
    annual_living_expenses numeric(20, 2) CHECK (annual_living_expenses >= 0),
    annual_discretionary_expenses numeric(20, 2) CHECK (annual_discretionary_expenses >= 0),
    baseline_plan varchar(255) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (conversation_id, revision)
);
