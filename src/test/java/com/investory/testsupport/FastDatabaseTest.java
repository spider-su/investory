package com.investory.testsupport;

import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/** Base class for Spring integration tests that use the snapshot-backed PostgreSQL database. */
@ActiveProfiles("test-fast")
public abstract class FastDatabaseTest {

  private static final PostgreSQLContainer<?> DATABASE = FastDatabase.container();

  @DynamicPropertySource
  protected static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", DATABASE::getJdbcUrl);
    registry.add("spring.datasource.username", DATABASE::getUsername);
    registry.add("spring.datasource.password", DATABASE::getPassword);
  }
}
