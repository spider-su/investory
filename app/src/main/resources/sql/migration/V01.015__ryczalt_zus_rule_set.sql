CREATE TABLE IF NOT EXISTS investory.ryczalt_zus_rule_set (
    id BIGSERIAL PRIMARY KEY,
    rule_year INTEGER NOT NULL,
    version VARCHAR(64) NOT NULL,
    social_insurance NUMERIC(19,4) NOT NULL,
    labour_fund NUMERIC(19,4) NOT NULL,
    voluntary_sickness NUMERIC(19,4) NOT NULL,
    health_low NUMERIC(19,4) NOT NULL,
    health_medium NUMERIC(19,4) NOT NULL,
    health_high NUMERIC(19,4) NOT NULL,
    threshold_low NUMERIC(19,4) NOT NULL DEFAULT 60000,
    threshold_medium NUMERIC(19,4) NOT NULL DEFAULT 300000,
    CONSTRAINT uq_ryczalt_zus_rule_set_year UNIQUE (rule_year),
    CONSTRAINT chk_ryczalt_zus_rule_set_year CHECK (rule_year >= 2025),
    CONSTRAINT chk_ryczalt_zus_rule_set_amounts CHECK (
        social_insurance >= 0 AND labour_fund >= 0 AND voluntary_sickness >= 0
        AND health_low >= 0 AND health_medium >= health_low AND health_high >= health_medium)
);

INSERT INTO investory.ryczalt_zus_rule_set
    (rule_year, version, social_insurance, labour_fund, voluntary_sickness,
     health_low, health_medium, health_high)
VALUES
    (2025, 'ZUS_2025_POC_V1', 1391.49, 127.49, 127.49, 461.66, 769.43, 1384.97),
    (2026, 'ZUS_2026_POC_V1', 1649.82, 138.47, 138.47, 498.35, 830.58, 1495.04)
ON CONFLICT (rule_year) DO NOTHING;
