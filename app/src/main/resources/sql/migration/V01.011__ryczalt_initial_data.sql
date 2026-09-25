SET search_path TO investory, public;

-- No profile, invoice, payment, bank, or tax-obligation rows belong in Flyway.
-- The schema has no global Ryczalt lookup rows today; this file is kept as the
-- explicit home for future stable, non-personal seeds.
