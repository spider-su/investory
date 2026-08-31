package com.smartbox.investory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;

@SpringBootApplication
@EntityScan("com.smartbox.investory")
public class InvestoryApplication {

  static void main(String[] args) {
    SpringApplication.run(InvestoryApplication.class, args);
  }
}
