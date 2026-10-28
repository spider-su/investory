package com.smartbox.investory.config;

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {

  @Bean(name = "importRefreshScheduler", defaultCandidate = false)
  org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler importRefreshScheduler() {
    var scheduler = new org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler();
    scheduler.setPoolSize(1);
    scheduler.setThreadNamePrefix("import-refresh-");
    scheduler.setWaitForTasksToCompleteOnShutdown(true);
    scheduler.setAwaitTerminationSeconds(30);
    return scheduler;
  }

  /** Manual import jobs progress even when automatic integration scheduling is disabled. */
  @Bean
  org.springframework.beans.factory.InitializingBean importRefreshPolling(
      @org.springframework.beans.factory.annotation.Qualifier("importRefreshScheduler")
          org.springframework.scheduling.TaskScheduler scheduler,
      com.smartbox.investory.investment.imports.ImportRefreshService worker,
      @org.springframework.beans.factory.annotation.Value("${app.import.refresh-poll-ms:2000}")
          long delayMillis) {
    return () ->
        scheduler.scheduleWithFixedDelay(
            worker::processNext, java.time.Duration.ofMillis(Math.max(1000, delayMillis)));
  }

  @Bean(name = "reconciliationRefreshExecutor")
  ThreadPoolTaskExecutor reconciliationRefreshExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(1);
    executor.setMaxPoolSize(1);
    executor.setQueueCapacity(100);
    executor.setThreadNamePrefix("reconciliation-refresh-");
    executor.setWaitForTasksToCompleteOnShutdown(true);
    return executor;
  }

  @Bean(name = "investmentProfileReadExecutor")
  ThreadPoolTaskExecutor investmentProfileReadExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(4);
    executor.setMaxPoolSize(4);
    executor.setQueueCapacity(32);
    executor.setThreadNamePrefix("investment-profile-read-");
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
    executor.setWaitForTasksToCompleteOnShutdown(true);
    return executor;
  }
}
