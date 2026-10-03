package com.smartbox.investory.integrations.notifications.application;

import com.smartbox.investory.integrations.notifications.persistence.NotificationEventEntity;
import com.smartbox.investory.shared.notifications.NotificationEventType;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

@Component
public class TextBeltNotificationDeliveryChannel implements NotificationDeliveryChannel {
  private final RestClient restClient;
  private final boolean enabled;
  private final String phone;
  private final String key;
  private final Set<String> alertRules;

  public TextBeltNotificationDeliveryChannel(
      RestClient.Builder builder,
      @Value("${app.notifications.sms.textbelt.enabled:false}") boolean enabled,
      @Value("${app.notifications.sms.textbelt.phone:}") String phone,
      @Value("${app.notifications.sms.textbelt.key:textbelt}") String key,
      @Value("${app.notifications.sms.textbelt.alert-rules:RENTAL_CONTRACT_EXPIRING,BOND_MATURITY_APPROACHING}")
          String alertRules) {
    this.restClient = builder.baseUrl("https://textbelt.com").build();
    this.enabled = enabled;
    this.phone = phone;
    this.key = key;
    this.alertRules =
        Arrays.stream(alertRules.split(","))
            .map(String::trim)
            .filter(value -> !value.isBlank())
            .collect(Collectors.toUnmodifiableSet());
  }

  @Override
  public boolean supports(NotificationEventEntity event) {
    if (!enabled || phone.isBlank() || event.getEventType() != NotificationEventType.THRESHOLD_ALERT) {
      return false;
    }
    return alertRules.contains(event.getPayload().getOrDefault("rule", ""));
  }

  /**
   * The free TextBelt tier is intentionally best-effort: retrying a quota failure cannot succeed
   * until the quota resets and must not cause Telegram to be delivered twice.
   */
  @Override
  public boolean bestEffort() {
    return true;
  }

  @Override
  public void send(String message) {
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("phone", phone);
    form.add("message", plainText(message));
    form.add("key", key);
    TextBeltResponse response =
        restClient
            .post()
            .uri("/text")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(form)
            .retrieve()
            .body(TextBeltResponse.class);
    if (response == null || !response.success()) {
      throw new IllegalStateException(
          "TextBelt rejected SMS" + (response == null || response.error() == null ? "" : ": " + response.error()));
    }
  }

  private static String plainText(String html) {
    return html
        .replaceAll("<[^>]+>", "")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replaceAll("\\s*\\n\\s*", " · ")
        .replaceAll("\\s{2,}", " ")
        .trim();
  }

  record TextBeltResponse(boolean success, String error, Integer quotaRemaining, String textId) {}
}
