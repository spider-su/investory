package com.smartbox.investory.rag;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

/** Load the same YAML rules as production; unit tests have no shadow regex defaults. */
final class RoutingPatternsTestSupport {
  private RoutingPatternsTestSupport() {}

  static AssistantRoutingPatterns load() {
    var yaml = new YamlPropertiesFactoryBean();
    yaml.setResources(new ClassPathResource("application.yml"));
    var properties = yaml.getObject();
    if (properties == null) throw new IllegalStateException("application.yml unavailable");
    Map<String, String> rules = new LinkedHashMap<>();
    String prefix = "investory.assistant.routing.patterns.";
    for (String name : properties.stringPropertyNames()) {
      if (name.startsWith(prefix)) {
        rules.put(name.substring(prefix.length()), properties.getProperty(name));
      }
    }
    var result = new AssistantRoutingPatterns();
    result.setPatterns(rules);
    result.validate();
    return result;
  }
}
