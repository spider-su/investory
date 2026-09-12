package com.smartbox.investory.testsupport.accounting;

import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Base class for integration tests that need the isolated accounting POC fixture. */
@ActiveProfiles("test-fast")
public abstract class AccountingDatabaseTest {

  @DynamicPropertySource
  protected static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", AccountingDatabase::pocJdbcUrl);
    registry.add("spring.datasource.username", AccountingDatabase::pocUsername);
    registry.add("spring.datasource.password", AccountingDatabase::pocPassword);
  }
}
