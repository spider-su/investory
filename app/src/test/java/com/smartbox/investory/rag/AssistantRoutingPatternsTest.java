package com.smartbox.investory.rag;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AssistantRoutingPatternsTest {
  @Test
  void yamlProvidesEveryRequiredRoutingCategory() {
    var settings = RoutingPatternsTestSupport.load();
    assertTrue(settings.pattern("holdings").matcher("What stocks do I hold?").find());
    assertTrue(settings.pattern("profit-history").matcher("since inception").find());
    assertTrue(settings.pattern("classify-benchmark-subject").matcher("SPY").find());
  }

  @Test
  void overriddenPhraseChangesMatchingWithoutJavaFallback() {
    var base = RoutingPatternsTestSupport.load();
    Map<String, String> changed = new HashMap<>(base.getPatterns());
    changed.put("holdings", "(?i)\\\\bmy custom positions\\\\b");
    var override = new AssistantRoutingPatterns();
    override.setPatterns(changed);
    override.validate();
    assertTrue(override.pattern("holdings").matcher("My custom positions").find());
    assertFalse(override.pattern("holdings").matcher("Show my holdings").find());
  }

  @Test
  void invalidAndMissingPatternsCannotSilentlyFallBack() {
    var invalid = new AssistantRoutingPatterns();
    invalid.setPatterns(Map.of("holdings", "["));
    assertThrows(IllegalStateException.class, invalid::validate);
    var empty = new AssistantRoutingPatterns();
    empty.setPatterns(Map.of());
    empty.validate();
    assertThrows(IllegalStateException.class, () -> empty.pattern("holdings"));
  }
}
