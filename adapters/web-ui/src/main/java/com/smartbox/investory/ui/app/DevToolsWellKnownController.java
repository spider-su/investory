package com.smartbox.investory.ui.app;

import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Handles the optional metadata probe issued by Chrome DevTools. */
@RestController
public class DevToolsWellKnownController {

  @GetMapping(
      value = "/.well-known/appspecific/com.chrome.devtools.json",
      produces = MediaType.APPLICATION_JSON_VALUE)
  public Map<String, Object> chromeDevToolsConfiguration() {
    return Map.of();
  }
}
