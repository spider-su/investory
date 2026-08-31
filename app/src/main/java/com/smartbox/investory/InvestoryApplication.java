package com.smartbox.investory;

import com.smartbox.investory.config.TimeConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@EntityScan("com.smartbox.investory")
@Import(TimeConfig.class)
public class InvestoryApplication {

  public static void main(String[] args) {
    SpringApplication.run(InvestoryApplication.class, args);
  }
}
