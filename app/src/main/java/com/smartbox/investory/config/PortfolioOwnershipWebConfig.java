package com.smartbox.investory.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class PortfolioOwnershipWebConfig implements WebMvcConfigurer {
  private final PortfolioOwnershipInterceptor portfolioOwnershipInterceptor;
  private final String mobileApiAllowedOrigins;

  public PortfolioOwnershipWebConfig(
      PortfolioOwnershipInterceptor portfolioOwnershipInterceptor,
      @Value("${app.security.mobile-api-allowed-origins:http://localhost:8081}")
          String mobileApiAllowedOrigins) {
    this.portfolioOwnershipInterceptor = portfolioOwnershipInterceptor;
    this.mobileApiAllowedOrigins = mobileApiAllowedOrigins;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(portfolioOwnershipInterceptor);
  }

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry
        .addMapping("/api/**")
        .allowedOrigins(mobileApiAllowedOrigins.split(","))
        .allowedMethods("GET", "POST", "PUT", "OPTIONS")
        .allowedHeaders("Authorization", "Content-Type")
        .allowCredentials(true);
  }
}
