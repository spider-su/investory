package com.smartbox.investory.testsupport;

import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Base class for Spring integration tests that use the snapshot-backed PostgreSQL database. */
@ActiveProfiles("test-fast")
public abstract class FastDatabaseTest {

  @DynamicPropertySource
  protected static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", FastDatabase::jdbcUrl);
    registry.add("spring.datasource.username", FastDatabase::username);
    registry.add("spring.datasource.password", FastDatabase::password);
  }
}
