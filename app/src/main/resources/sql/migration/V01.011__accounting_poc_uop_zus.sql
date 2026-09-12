CREATE TABLE investory.accounting_poc_profile (
    id SMALLINT PRIMARY KEY,
    has_uop BOOLEAN NOT NULL,
    CONSTRAINT chk_accounting_poc_profile_singleton CHECK (id = 1)
);

COMMENT ON TABLE investory.accounting_poc_profile IS
    'POC-only JDG accounting assumptions shared by all represented months.';
COMMENT ON COLUMN investory.accounting_poc_profile.has_uop IS
    'True means an active UoP meeting the minimum-remuneration condition for exemption from compulsory JDG social contributions.';

INSERT INTO investory.accounting_poc_profile (id, has_uop)
VALUES (1, TRUE);

-- Explicit POC calculation input for the normal-JDG branch. This is the 2026 minimum
-- compulsory social-side amount without voluntary sickness insurance. Keeping it as an
-- input avoids introducing statutory rate/base tables into this POC.
INSERT INTO investory.accounting_poc_tax_input (tax_period, input_type, amount, note)
VALUES
    ('2026-01-01', 'JDG_COMPULSORY_SOCIAL_ZUS', 1788.2900, '2026 normal-JDG compulsory social-side ZUS input; excludes voluntary sickness insurance.'),
    ('2026-02-01', 'JDG_COMPULSORY_SOCIAL_ZUS', 1788.2900, '2026 normal-JDG compulsory social-side ZUS input; excludes voluntary sickness insurance.'),
    ('2026-03-01', 'JDG_COMPULSORY_SOCIAL_ZUS', 1788.2900, '2026 normal-JDG compulsory social-side ZUS input; excludes voluntary sickness insurance.'),
    ('2026-04-01', 'JDG_COMPULSORY_SOCIAL_ZUS', 1788.2900, '2026 normal-JDG compulsory social-side ZUS input; excludes voluntary sickness insurance.'),
    ('2026-05-01', 'JDG_COMPULSORY_SOCIAL_ZUS', 1788.2900, '2026 normal-JDG compulsory social-side ZUS input; excludes voluntary sickness insurance.'),
    ('2026-06-01', 'JDG_COMPULSORY_SOCIAL_ZUS', 1788.2900, '2026 normal-JDG compulsory social-side ZUS input; excludes voluntary sickness insurance.'),
    ('2026-07-01', 'JDG_COMPULSORY_SOCIAL_ZUS', 1788.2900, '2026 normal-JDG compulsory social-side ZUS input; excludes voluntary sickness insurance.'),
    ('2026-08-01', 'JDG_COMPULSORY_SOCIAL_ZUS', 1788.2900, '2026 normal-JDG compulsory social-side ZUS input; excludes voluntary sickness insurance.');
