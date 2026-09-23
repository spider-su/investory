INSERT INTO investory.ryczalt_native_month_input (
    profile_id,
    tax_year,
    tax_month,
    jdg_active,
    qualifying_uop,
    zus_regime,
    voluntary_sickness,
    ytd_ryczalt_revenue,
    full_jdg_social,
    deductions_already_consumed,
    sales_corrections,
    explicit_vat_adjustments
)
SELECT
    period.profile_id,
    period.period_year,
    period.period_month,
    tax_profile.jdg_active,
    EXISTS (
        SELECT 1
          FROM investory.employment_period employment
         WHERE employment.profile_id = period.profile_id
           AND employment.employment_type = 'UOP'
           AND COALESCE(employment.qualifies_as_primary_social_insurance, TRUE)
           AND employment.date_from <= make_date(period.period_year, period.period_month, 1)
           AND (employment.date_to IS NULL
                OR employment.date_to >= make_date(period.period_year, period.period_month, 1))
    ),
    tax_profile.zus_regime,
    tax_profile.voluntary_sickness,
    COALESCE(SUM(invoice.booked_net_pln) FILTER (
        WHERE invoice.direction = 'INCOME'
          AND invoice.approval_status = 'APPROVED'
          AND invoice.currency = 'PLN'
          AND invoice.booked_net_pln IS NOT NULL
          AND invoice.accounting_date >= make_date(period.period_year, 1, 1)
          AND invoice.accounting_date <
              (make_date(period.period_year, period.period_month, 1) + INTERVAL '1 month')
    ), 0),
    NULL,
    0,
    0,
    0
FROM investory.ryczalt_period period
JOIN investory.accounting_tax_profile_period tax_profile
  ON tax_profile.profile_id = period.profile_id
 AND tax_profile.valid_from <= make_date(period.period_year, period.period_month, 1)
 AND (tax_profile.valid_to IS NULL
      OR tax_profile.valid_to >= make_date(period.period_year, period.period_month, 1))
LEFT JOIN investory.ryczalt_invoice invoice
  ON invoice.profile_id = period.profile_id
GROUP BY
    period.profile_id,
    period.period_year,
    period.period_month,
    tax_profile.jdg_active,
    tax_profile.zus_regime,
    tax_profile.voluntary_sickness
ON CONFLICT (profile_id, tax_year, tax_month) DO NOTHING;
