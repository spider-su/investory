CREATE EXTENSION IF NOT EXISTS pgcrypto WITH SCHEMA public;

-- Only the synthetic snapshot's scoped account can sign in to this test stack.
UPDATE investory.app_users
SET password_hash = public.crypt('fixture-password', public.gen_salt('bf')),
    role = 'PROFILE_USER'
WHERE username = 'happy.investor';

-- Leave two attempts for a successful quote and a simulated provider failure.
INSERT INTO investory.external_market_attempt (provider_profile, reserved_at)
SELECT 'assistant-release-smoke', now() - attempt * interval '1 second'
FROM generate_series(1, 18) AS attempt;
