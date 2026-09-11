package com.smartbox.investory.testsupport;

import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Base class for integration tests that need the isolated accounting POC fixture. */
@ActiveProfiles("test-fast")
public abstract class PocDatabaseTest {

  @DynamicPropertySource
  protected static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", FastDatabase::pocJdbcUrl);
    registry.add("spring.datasource.username", FastDatabase::pocUsername);
    registry.add("spring.datasource.password", FastDatabase::pocPassword);
  }
}
