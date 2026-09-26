package com.smartbox.investory.ryczalt.persistence;

import com.smartbox.investory.ryczalt.application.RyczaltProfile;
import com.smartbox.investory.ryczalt.application.port.RyczaltProfileReader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcRyczaltProfileReader implements RyczaltProfileReader {
  private final JdbcTemplate jdbc;

  public JdbcRyczaltProfileReader(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public RyczaltProfile read(long profileId) {
    return jdbc.queryForObject(
        """
        SELECT profile_id, has_uop, nip, full_name, tax_office_code, email,
               vat_payment_account, ryczalt_payment_account, zus_payment_account,
               first_name, surname, date_of_birth
          FROM investory.ryczalt_profile
         WHERE profile_id = ?
        """,
        (rs, rowNum) ->
            new RyczaltProfile(
                rs.getLong("profile_id"),
                rs.getBoolean("has_uop"),
                rs.getString("nip"),
                rs.getString("full_name"),
                rs.getString("tax_office_code"),
                rs.getString("email"),
                rs.getString("vat_payment_account"),
                rs.getString("ryczalt_payment_account"),
                rs.getString("zus_payment_account"),
                rs.getString("first_name"),
                rs.getString("surname"),
                rs.getObject("date_of_birth", java.time.LocalDate.class)),
        profileId);
  }
}
