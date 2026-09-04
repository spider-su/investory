package com.smartbox.investory.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class PortfolioOwnershipWebConfig implements WebMvcConfigurer {
  private final PortfolioOwnershipInterceptor portfolioOwnershipInterceptor;

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(portfolioOwnershipInterceptor);
  }
}
