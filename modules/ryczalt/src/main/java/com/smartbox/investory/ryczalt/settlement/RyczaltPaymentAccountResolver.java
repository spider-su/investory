package com.smartbox.investory.ryczalt.settlement;

import com.smartbox.investory.ryczalt.checker.PaymentAccountRules;
import com.smartbox.investory.ryczalt.domain.ObligationType;
import java.util.EnumMap;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class RyczaltPaymentAccountResolver {
  private final JdbcTemplate jdbc;

  public RyczaltPaymentAccountResolver(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public PaymentAccountRules forProfile(long profileId) {
    return jdbc.queryForObject(
        """
        SELECT COALESCE(
                   (SELECT r.account_number
                      FROM investory.ryczalt_payment_account_rule r
                     WHERE r.profile_id = p.id
                       AND r.obligation_type = 'RYCZALT'
                     ORDER BY r.id
                     LIMIT 1),
                   p.tax_micro_account) AS ryczalt_payment_account,
               COALESCE(
                   (SELECT r.account_number
                      FROM investory.ryczalt_payment_account_rule r
                     WHERE r.profile_id = p.id
                       AND r.obligation_type = 'VAT'
                     ORDER BY r.id
                     LIMIT 1),
                   p.tax_micro_account) AS vat_payment_account,
               COALESCE(
                   (SELECT r.account_number
                      FROM investory.ryczalt_payment_account_rule r
                     WHERE r.profile_id = p.id
                       AND r.obligation_type = 'ZUS'
                     ORDER BY r.id
                     LIMIT 1),
                   p.zus_payment_account) AS zus_payment_account
          FROM investory.portfolios p
         WHERE p.id = ?
        """,
        (rs, rowNum) -> {
          EnumMap<ObligationType, String> accounts = new EnumMap<>(ObligationType.class);
          accounts.put(ObligationType.RYCZALT, rs.getString("ryczalt_payment_account"));
          accounts.put(ObligationType.VAT, rs.getString("vat_payment_account"));
          accounts.put(ObligationType.ZUS, rs.getString("zus_payment_account"));
          return new PaymentAccountRules(accounts);
        },
        profileId);
  }
}
