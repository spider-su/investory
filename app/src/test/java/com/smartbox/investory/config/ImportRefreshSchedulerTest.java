package com.smartbox.investory.config;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.EnableScheduling;

class ImportRefreshSchedulerTest {
  @Configuration(proxyBeanMethods = false)
  @EnableScheduling
  static class Scheduling {}

  @Test
  void importWorkerDoesNotReplaceTheDefaultScheduler() {
    try (var context = new AnnotationConfigApplicationContext()) {
      context.registerBean(
          com.smartbox.investory.investment.imports.ImportRefreshService.class,
          () ->
              org.mockito.Mockito.mock(
                  com.smartbox.investory.investment.imports.ImportRefreshService.class));
      context.register(Scheduling.class, AsyncConfig.class, TaskSchedulingAutoConfiguration.class);
      context.refresh();
      assertNotSame(context.getBean("importRefreshScheduler"), context.getBean("taskScheduler"));
    }
  }
}
