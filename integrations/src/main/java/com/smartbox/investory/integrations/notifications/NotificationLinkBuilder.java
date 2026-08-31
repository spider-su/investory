package com.smartbox.investory.integrations.notifications;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class NotificationLinkBuilder {
  private final String baseUrl;

  public NotificationLinkBuilder(
      @Value("${app.notifications.base-url:http://localhost:8080}") String baseUrl) {
    this.baseUrl = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
  }

  public String link(String path) {
    return baseUrl + path;
  }
}
