DO $$
DECLARE
    max_id bigint;
    sequence_value bigint;
    sequence_called boolean;
    sequence_name regclass := pg_get_serial_sequence(
        'investory.benchmark_monthly_closes', 'id'
    )::regclass;
BEGIN
    SELECT COALESCE(MAX(id), 0)
      INTO max_id
      FROM investory.benchmark_monthly_closes;

    EXECUTE format('SELECT last_value, is_called FROM %s', sequence_name)
      INTO sequence_value, sequence_called;

    IF max_id >= sequence_value THEN
        PERFORM setval(sequence_name, GREATEST(max_id, 1), max_id > 0 OR sequence_called);
    END IF;
END $$;
