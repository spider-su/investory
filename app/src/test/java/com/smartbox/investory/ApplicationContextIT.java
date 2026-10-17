package com.smartbox.investory;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.smartbox.investory.testsupport.FastDatabaseTest;
import com.smartbox.investory.user.infrastructure.AppUserRepository;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test-fast")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ApplicationContextIT extends FastDatabaseTest {

  @Autowired private JdbcTemplate jdbc;

  @Autowired private AppUserRepository users;

  @Test
  void concurrentGoogleLinkingAllowsExactlyOneWinner() throws Exception {
    String username = "link-" + UUID.randomUUID();
    jdbc.update(
        "insert into investory.app_users (username, display_name, active, role) values (?, ?, true, 'USER')",
        username,
        "Test linking");
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var first =
          executor.submit(
              () -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS))
                  throw new IllegalStateException("Start timed out");
                return users.linkGoogleSubjectIfUnlinked(username, "subject-a", Instant.now());
              });
      var second =
          executor.submit(
              () -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS))
                  throw new IllegalStateException("Start timed out");
                return users.linkGoogleSubjectIfUnlinked(username, "subject-b", Instant.now());
              });
      org.junit.jupiter.api.Assertions.assertTrue(ready.await(10, TimeUnit.SECONDS));
      start.countDown();
      int firstCount = first.get(15, TimeUnit.SECONDS);
      int secondCount = second.get(15, TimeUnit.SECONDS);
      assertEquals(1, firstCount + secondCount);
      assertEquals(
          firstCount == 1 ? "subject-a" : "subject-b",
          jdbc.queryForObject(
              "select google_subject from investory.app_users where username = ?",
              String.class,
              username));
      assertEquals(0, users.linkGoogleSubjectIfUnlinked(username, "replacement", Instant.now()));
      assertEquals(
          firstCount == 1 ? "subject-a" : "subject-b",
          jdbc.queryForObject(
              "select google_subject from investory.app_users where username = ?",
              String.class,
              username));
    } finally {
      start.countDown();
      jdbc.update("delete from investory.app_users where username = ?", username);
    }
  }

  @Test
  void inactiveUserCannotBeLinked() {
    String username = "inactive-" + UUID.randomUUID();
    jdbc.update(
        "insert into investory.app_users (username, display_name, active, role) values (?, ?, false, 'USER')",
        username,
        "Inactive linking");
    try {
      assertEquals(
          0, users.linkGoogleSubjectIfUnlinked(username, "subject-inactive", Instant.now()));
    } finally {
      jdbc.update("delete from investory.app_users where username = ?", username);
    }
  }

  @Test
  void applicationContextLoads() {}

  @Test
  void jdbcUsesInvestoryAsItsDefaultSchema() {
    assertEquals("investory", jdbc.queryForObject("select current_schema()", String.class));
  }
}
