package com.smartbox.investory.rag;

import jakarta.annotation.PostConstruct;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Validated, deployment-configurable patterns used by the assistant fast path. */
@Component
@ConfigurationProperties(prefix = "investory.assistant.routing")
public class AssistantRoutingPatterns {
  private Map<String, String> patterns = new LinkedHashMap<>();
  private Map<String, Pattern> compiled = Map.of();

  public Map<String, String> getPatterns() {
    return patterns;
  }

  public void setPatterns(Map<String, String> patterns) {
    this.patterns = patterns == null ? Map.of() : Map.copyOf(patterns);
  }

  @PostConstruct
  public void validate() {
    Map<String, Pattern> result = new LinkedHashMap<>();
    for (var entry : patterns.entrySet()) {
      if (entry.getKey().isBlank() || entry.getValue() == null
          || entry.getValue().isBlank() || entry.getValue().length() > 2048) {
        throw new IllegalStateException("Invalid assistant routing pattern configuration");
      }
      try {
        result.put(entry.getKey(), Pattern.compile(entry.getValue()));
      } catch (PatternSyntaxException e) {
        throw new IllegalStateException(
            "Invalid assistant routing pattern: " + entry.getKey(), e);
      }
    }
    this.compiled = Map.copyOf(result);
  }

  public Pattern pattern(String name) {
    Pattern result = compiled.get(name);
    if (result == null) {
      throw new IllegalStateException("Missing assistant routing pattern: " + name);
    }
    return result;
  }
}
