package com.smartbox.investory.profile;

import com.smartbox.investory.profile.api.model.EmploymentPeriod;
import com.smartbox.investory.profile.api.model.EmploymentType;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class EmploymentPeriodRepository {
  private final JdbcTemplate jdbc;

  public List<EmploymentPeriod> findAll(long profileId) {
    return jdbc.query(
        "SELECT id, employment_type, date_from, date_to FROM investory.employment_period WHERE profile_id = ? ORDER BY date_from, id",
        (rs, row) ->
            new EmploymentPeriod(
                rs.getLong(1),
                EmploymentType.valueOf(rs.getString(2)),
                rs.getObject(3, LocalDate.class),
                rs.getObject(4, LocalDate.class)),
        profileId);
  }

  public EmploymentPeriod save(long profileId, EmploymentPeriod period) {
    Long id =
        period.id() == null
            ? jdbc.queryForObject(
                "INSERT INTO investory.employment_period(profile_id, employment_type, date_from, date_to) VALUES (?, ?, ?, ?) RETURNING id",
                Long.class,
                profileId,
                period.type().name(),
                period.from(),
                period.to())
            : period.id();
    if (period.id() != null)
      jdbc.update(
          "UPDATE investory.employment_period SET employment_type = ?, date_from = ?, date_to = ? WHERE id = ? AND profile_id = ?",
          period.type().name(),
          period.from(),
          period.to(),
          period.id(),
          profileId);
    return new EmploymentPeriod(id, period.type(), period.from(), period.to());
  }

  public void delete(long profileId, long id) {
    jdbc.update(
        "DELETE FROM investory.employment_period WHERE id = ? AND profile_id = ?", id, profileId);
  }
}
