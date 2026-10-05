package com.smartbox.investory;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.smartbox.investory.testsupport.FastDatabaseTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test-fast")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ApplicationContextIT extends FastDatabaseTest {

  @Autowired private JdbcTemplate jdbc;

  @Test
  void applicationContextLoads() {}

  @Test
  void jdbcUsesInvestoryAsItsDefaultSchema() {
    assertEquals("investory", jdbc.queryForObject("select current_schema()", String.class));
  }
}
