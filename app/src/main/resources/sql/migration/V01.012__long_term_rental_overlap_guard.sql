-- Effective rental periods are closed on both dates. The upper bound is made exclusive
-- so contracts ending on one day may be followed by a contract starting the next day.
CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE investory.rental_contract
    ADD CONSTRAINT ex_rental_contract_non_overlapping_effective_periods
    EXCLUDE USING gist (
        real_estate_id WITH =,
        daterange(
            start_date,
            least(
                coalesce(end_date, 'infinity'::date),
                coalesce(terminated_date, 'infinity'::date)
            ) + 1,
            '[)'
        ) WITH &&
    );
