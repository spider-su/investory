package com.smartbox.investory.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartbox.investory.ryczalt.domain.PeriodStatus;
import com.smartbox.investory.ryczalt.persistence.RyczaltCalculationJpaRepository;
import com.smartbox.investory.ryczalt.persistence.RyczaltObligationJpaRepository;
import com.smartbox.investory.ryczalt.persistence.RyczaltPeriodEntity;
import com.smartbox.investory.ryczalt.persistence.RyczaltPeriodJpaRepository;
import com.smartbox.investory.ryczalt.persistence.RyczaltPeriodLifecycleService;
import com.smartbox.investory.testsupport.WorkerDatabase;
import java.time.Clock;
import java.time.YearMonth;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(classes = RyczaltPeriodLifecycleConcurrencyIT.TestConfiguration.class)
class RyczaltPeriodLifecycleConcurrencyIT {
  private static final WorkerDatabase DATABASE = MigrationTestDatabase.open("ryczalt_lifecycle");

  @Autowired private RyczaltPeriodJpaRepository periods;
  @Autowired private RyczaltPeriodLifecycleService lifecycle;

  @BeforeAll
  static void migrateSchema() {
    MigrationTestDatabase.migrate(DATABASE);
  }

  @AfterAll
  static void closeDatabase() {
    DATABASE.close();
  }

  @BeforeEach
  void clean() {
    periods.deleteAll();
    periods.saveAndFlush(new RyczaltPeriodEntity(1L, 2026, 8, PeriodStatus.FROZEN));
  }

  @Test
  void concurrentReopenRequestsSerializeAtTheDatabaseRow() throws Exception {
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      Future<Boolean> first = executor.submit(() -> reopen(start));
      Future<Boolean> second = executor.submit(() -> reopen(start));
      start.countDown();

      assertThat(success(first) ^ success(second)).isTrue();
      assertThat(periods.findByProfileIdAndYearAndMonth(1L, 2026, 8))
          .get()
          .extracting(RyczaltPeriodEntity::getStatus)
          .isEqualTo(PeriodStatus.DIRTY);
    } finally {
      executor.shutdownNow();
    }
  }

  private boolean reopen(CountDownLatch start) throws InterruptedException {
    start.await();
    try {
      lifecycle.reopen(1L, YearMonth.of(2026, 8), "test", "concurrent test");
      return true;
    } catch (IllegalStateException expected) {
      return false;
    }
  }

  private static boolean success(Future<Boolean> result) throws Exception {
    try {
      return result.get();
    } catch (ExecutionException exception) {
      throw new AssertionError("Lifecycle request failed unexpectedly", exception.getCause());
    }
  }

  @Configuration(proxyBeanMethods = false)
  @EnableAutoConfiguration
  @EntityScan(basePackageClasses = RyczaltPeriodEntity.class)
  @EnableJpaRepositories(
      basePackageClasses = {
        RyczaltPeriodJpaRepository.class,
        RyczaltCalculationJpaRepository.class,
        RyczaltObligationJpaRepository.class
      })
  @Import({
    RyczaltPeriodLifecycleService.class,
    com.smartbox.investory.ryczalt.persistence.JdbcRyczaltAuditEventWriter.class
  })
  static class TestConfiguration {
    @Bean
    Clock applicationClock() {
      return Clock.systemUTC();
    }
  }

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", DATABASE::jdbcUrl);
    registry.add("spring.datasource.username", DATABASE::username);
    registry.add("spring.datasource.password", DATABASE::password);
    registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
    registry.add("spring.flyway.enabled", () -> "false");
  }
}
