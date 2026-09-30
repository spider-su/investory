ALTER TABLE investory.real_estate
    ADD COLUMN acquisition_value numeric(30,12)
        CHECK (acquisition_value IS NULL OR acquisition_value > 0);

COMMENT ON COLUMN investory.real_estate.acquisition_value IS
    'Original property value used as the total-return denominator.';
