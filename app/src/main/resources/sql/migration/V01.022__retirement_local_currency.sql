-- Retirement values are portfolio-local amounts. Convert legacy USD persistence once.
CREATE TABLE IF NOT EXISTS investory.retirement_currency_conversions (
    migration_key varchar(100) NOT NULL,
    portfolio_id bigint NOT NULL REFERENCES investory.portfolios(id) ON DELETE CASCADE,
    source_currency varchar(3) NOT NULL,
    target_currency varchar(3) NOT NULL,
    rate numeric(30,12) NOT NULL,
    rate_date date NOT NULL,
    planning_years_converted boolean NOT NULL DEFAULT false,
    converted_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (migration_key, portfolio_id)
);
ALTER TABLE investory.retirement_currency_conversions
    ADD COLUMN IF NOT EXISTS planning_years_converted boolean NOT NULL DEFAULT false;

CREATE OR REPLACE FUNCTION investory.convert_retirement_currency_json(doc jsonb, factor numeric)
RETURNS jsonb LANGUAGE plpgsql STABLE AS $$
DECLARE
    result jsonb;
BEGIN
    IF jsonb_typeof(doc) = 'object' THEN
        SELECT COALESCE(jsonb_object_agg(key,
            CASE
              WHEN key IN ('currency','baseCurrency') THEN to_jsonb(COALESCE(current_setting('investory.retirement_target_currency', true), doc->>key))
              WHEN key IN ('currentValue','annualIncome','annualExpense','amount','reserve','investmentCapital',
                           'longTermCapital','cashReserve','marketAssets','netWorth','bondValue','realEstateValue',
                           'cashReserveValue','rentalIncome','bondIncome','annualLivingExpenses',
                           'annualDiscretionaryExpenses','annualPension','employmentIncome')
                   AND jsonb_typeof(doc->key) = 'number' THEN to_jsonb((doc->>key)::numeric * factor)
              ELSE investory.convert_retirement_currency_json(doc->key, factor)
            END), '{}'::jsonb)
          INTO result FROM jsonb_each(doc);
        RETURN result;
    ELSIF jsonb_typeof(doc) = 'array' THEN
        SELECT COALESCE(jsonb_agg(investory.convert_retirement_currency_json(entry, factor)), '[]'::jsonb)
          INTO result FROM jsonb_array_elements(doc) AS elements(entry);
        RETURN result;
    END IF;
    RETURN doc;
END $$;

DO $$
DECLARE
    p record;
    fx numeric(30,12);
BEGIN
    FOR p IN
        SELECT id, base_currency, local_currency
        FROM investory.portfolios
    LOOP
        IF EXISTS (
            SELECT 1 FROM investory.retirement_currency_conversions
            WHERE migration_key = 'V01.022' AND portfolio_id = p.id
        ) THEN
            CONTINUE;
        END IF;

        IF p.base_currency = p.local_currency THEN
            fx := 1;
        ELSE
            SELECT fx_rate_to_target INTO STRICT fx
            FROM investory.resolve_fx_rate(current_date, p.base_currency, p.local_currency);
        END IF;

        UPDATE investory.retirement_plans
        SET annual_employment_income = annual_employment_income * fx,
            annual_pre_retirement_contribution = annual_pre_retirement_contribution * fx,
            annual_living_expenses = annual_living_expenses * fx,
            annual_discretionary_expenses = annual_discretionary_expenses * fx,
            annual_pension = annual_pension * fx,
            baseline_reserve = baseline_reserve * fx,
            baseline_investment_capital = baseline_investment_capital * fx,
            baseline_long_term_capital = baseline_long_term_capital * fx,
            baseline_rental_income = baseline_rental_income * fx,
            baseline_long_term_income = baseline_long_term_income * fx,
            updated_at = now()
        WHERE portfolio_id = p.id;

        UPDATE investory.retirement_plan_events e
        SET amount = amount * fx
        FROM investory.retirement_plans plan
        WHERE e.plan_id = plan.id AND plan.portfolio_id = p.id;

        -- Historical planning metrics have stable metric identifiers. Ratios remain unchanged.
        UPDATE investory.retirement_planning_years y
        SET state = (
            SELECT jsonb_set(y.state, '{values}', converted.values, true)
            FROM (
                SELECT jsonb_object_agg(section.key, section.value) AS values
                FROM jsonb_each(COALESCE(y.state->'values', '{}'::jsonb)) section
                CROSS JOIN LATERAL (
                    SELECT jsonb_object_agg(metric.key,
                        CASE WHEN metric.key NOT IN ('EQUITY_RETURN','MARKET_RETURN')
                             THEN jsonb_set(jsonb_set(metric.value, '{derivedValue}',
                                  CASE WHEN metric.value->'derivedValue' IS NULL OR metric.value->'derivedValue' = 'null'::jsonb THEN 'null'::jsonb
                                       ELSE to_jsonb((metric.value->>'derivedValue')::numeric * fx) END, true),
                                  '{approvedValue}',
                                  CASE WHEN metric.value->'approvedValue' IS NULL OR metric.value->'approvedValue' = 'null'::jsonb THEN 'null'::jsonb
                                       ELSE to_jsonb((metric.value->>'approvedValue')::numeric * fx) END, true)
                             ELSE metric.value END) AS value
                    FROM jsonb_each(section.value) metric
                ) metrics
            ) converted
        ), updated_at = now()
        WHERE y.portfolio_id = p.id AND y.state ? 'values';

        -- Baseline long-term JSON stores monetary amounts alongside currency metadata.
        -- Currency identifiers are rewritten after numeric conversion so snapshots stay coherent.
        IF p.base_currency <> p.local_currency THEN
            PERFORM set_config('investory.retirement_target_currency', p.local_currency, true);
            UPDATE investory.retirement_plans
            SET baseline_long_term_state = investory.convert_retirement_currency_json(
                baseline_long_term_state::jsonb, fx)::text
            WHERE portfolio_id = p.id;
        END IF;

        INSERT INTO investory.retirement_currency_conversions
            (migration_key, portfolio_id, source_currency, target_currency, rate, rate_date,
             planning_years_converted)
        VALUES ('V01.022', p.id, p.base_currency, p.local_currency, fx, current_date, true);
    END LOOP;
END $$;
DROP FUNCTION investory.convert_retirement_currency_json(jsonb, numeric);
