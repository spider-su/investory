package com.smartbox.investory.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartbox.investory.investment.api.reporting.InvestmentDashboardApi;
import com.smartbox.investory.investment.api.reporting.model.ReconciliationStatus;
import com.smartbox.investory.longterm.api.model.*;
import com.smartbox.investory.testsupport.time.MutableApplicationTime;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@DisplayName("Rest Api Exception Handler")
class RestApiExceptionHandlerTest {

  private final MockMvc mvc =
      MockMvcBuilders.standaloneSetup(new FailingController())
          .setControllerAdvice(
              new RestApiExceptionHandler(
                  MutableApplicationTime.fixed(
                      Instant.parse("2026-09-05T08:00:00Z"), ZoneId.of("Europe/Warsaw"))))
          .build();

  @DisplayName("maps Client Errors To400")
  @Test
  void mapsClientErrorsTo400() throws Exception {
    mvc.perform(get("/api/v1/test/bad"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.message").value("Invalid input"))
        .andExpect(jsonPath("$.path").value("/api/v1/test/bad"));
  }

  @DisplayName("maps Long Term Missing Resources To404")
  @Test
  void mapsLongTermMissingResourcesTo404() throws Exception {
    mvc.perform(get("/api/v1/test/missing"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.message").value("Long-term asset 7 was not found in portfolio 1"))
        .andExpect(jsonPath("$.path").value("/api/v1/test/missing"));
  }

  @DisplayName("maps Unexpected Errors To500Without Leaking Details")
  @Test
  void mapsUnexpectedErrorsTo500WithoutLeakingDetails() throws Exception {
    mvc.perform(get("/api/v1/test/failure"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.status").value(500))
        .andExpect(jsonPath("$.message").value("Internal server error"))
        .andExpect(jsonPath("$.path").value("/api/v1/test/failure"));
  }

  @DisplayName("maps Missing Static Resources To404")
  @Test
  void mapsMissingStaticResourcesTo404() throws Exception {
    mvc.perform(get("/api/v1/test/missing-static"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.path").value("/api/v1/test/missing-static"));
  }

  @DisplayName("maps Invalid Enum Parameter To400Contract")
  @Test
  void mapsInvalidEnumParameterTo400Contract() throws Exception {
    mvc.perform(get("/api/v1/test/mode").param("mode", "ARCHIVE"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.path").value("/api/v1/test/mode"));
  }

  @DisplayName("maps Missing Query Parameter To400Contract")
  @Test
  void mapsMissingQueryParameterTo400Contract() throws Exception {
    mvc.perform(get("/api/v1/test/required"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.path").value("/api/v1/test/required"));
  }

  @DisplayName("maps Domain Input Validation To400Contract")
  @Test
  void mapsDomainInputValidationTo400Contract() throws Exception {
    mvc.perform(get("/api/v1/test/domain-invalid"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.message").value("Invalid rate"));
  }

  @RestController
  static class FailingController {
    @GetMapping("/api/v1/test/bad")
    String bad() {
      throw new InvestmentDashboardApi.InvalidPortfolioRequest("Invalid input");
    }

    @GetMapping("/api/v1/test/failure")
    String failure() {
      throw new IllegalStateException("secret implementation detail");
    }

    @GetMapping("/api/v1/test/missing-static")
    String missingStatic() throws NoResourceFoundException {
      throw new NoResourceFoundException(
          HttpMethod.GET, "/api/v1/test/missing-static", "missing-static");
    }

    @GetMapping("/api/v1/test/missing")
    String missing() {
      throw new AssetNotFoundException(1L, 7L);
    }

    @GetMapping("/api/v1/test/mode")
    String mode(@RequestParam ReconciliationStatus mode) {
      return mode.name();
    }

    @GetMapping("/api/v1/test/required")
    String required(@RequestParam String value) {
      return value;
    }

    @GetMapping("/api/v1/test/domain-invalid")
    String domainInvalid() {
      throw new IllegalArgumentException("Invalid rate");
    }
  }
}
