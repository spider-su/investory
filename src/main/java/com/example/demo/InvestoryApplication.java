package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@EnableFeignClients(basePackages = "com.example.demo.clients")
@SpringBootApplication
public class InvestoryApplication {

    public static void main(String[] args) {
        SpringApplication.run(InvestoryApplication.class, args);
    }
}
