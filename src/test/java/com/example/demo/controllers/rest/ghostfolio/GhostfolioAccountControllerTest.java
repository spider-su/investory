package com.example.demo.controllers.rest.ghostfolio;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(GhostfolioAccountController.class)
class GhostfolioAccountControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private GhostfolioCompatibilityService compatibilityService;

    @Test
    void returnsAccountEnvelope() throws Exception {
        when(compatibilityService.accounts())
                .thenReturn(
                        Map.of(
                                "accounts", List.of(),
                                "activitiesCount", 0,
                                "totalBalanceInBaseCurrency", 0.0d,
                                "totalDividendInBaseCurrency", 0.0d,
                                "totalInterestInBaseCurrency", 0.0d,
                                "totalValueInBaseCurrency", 0.0d));

        mockMvc
                .perform(get("/api/v1/account"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accounts", hasSize(0)))
                .andExpect(jsonPath("$.activitiesCount").value(0))
                .andExpect(jsonPath("$.totalBalanceInBaseCurrency").value(0.0))
                .andExpect(jsonPath("$.totalDividendInBaseCurrency").value(0.0))
                .andExpect(jsonPath("$.totalInterestInBaseCurrency").value(0.0))
                .andExpect(jsonPath("$.totalValueInBaseCurrency").value(0.0));
    }

    @Test
    void returnsCompleteGhostfolioAccountWithValueEnvelope() throws Exception {
        Map<String, Object> account =
                Map.of(
                        "id", "17959259",
                        "name", "IBKR",
                        "balance", 16_239.61335696,
                        "balanceInBaseCurrency", 16_239.61335696,
                        "value", 51_927.00335696,
                        "valueInBaseCurrency", 51_927.00335696,
                        "activitiesCount", 0,
                        "dividendInBaseCurrency", 0.0d,
                        "interestInBaseCurrency", 0.0d,
                        "allocationInPercentage", 0.3287089087898905d);
        when(compatibilityService.accounts())
                .thenReturn(
                        Map.of(
                                "accounts", List.of(account),
                                "activitiesCount", 0,
                                "totalBalanceInBaseCurrency", 16_239.61335696,
                                "totalDividendInBaseCurrency", 0.0d,
                                "totalInterestInBaseCurrency", 0.0d,
                                "totalValueInBaseCurrency", 51_927.00335696));

        mockMvc
                .perform(get("/api/v1/account"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accounts", hasSize(1)))
                .andExpect(jsonPath("$.accounts[0].id").value("17959259"))
                .andExpect(jsonPath("$.accounts[0].name").value("IBKR"))
                .andExpect(jsonPath("$.accounts[0].balance").value(16_239.61335696))
                .andExpect(
                        jsonPath("$.accounts[0].balanceInBaseCurrency")
                                .value(16_239.61335696))
                .andExpect(jsonPath("$.accounts[0].value").value(51_927.00335696))
                .andExpect(
                        jsonPath("$.accounts[0].valueInBaseCurrency")
                                .value(51_927.00335696))
                .andExpect(jsonPath("$.accounts[0].activitiesCount").value(0))
                .andExpect(jsonPath("$.accounts[0].dividendInBaseCurrency").value(0.0))
                .andExpect(jsonPath("$.accounts[0].interestInBaseCurrency").value(0.0))
                .andExpect(
                        jsonPath("$.accounts[0].allocationInPercentage")
                                .value(0.3287089087898905d))
                .andExpect(jsonPath("$.activitiesCount").value(0))
                .andExpect(jsonPath("$.totalBalanceInBaseCurrency").value(16_239.61335696))
                .andExpect(jsonPath("$.totalDividendInBaseCurrency").value(0.0))
                .andExpect(jsonPath("$.totalInterestInBaseCurrency").value(0.0))
                .andExpect(jsonPath("$.totalValueInBaseCurrency").value(51_927.00335696));
    }

    @Test
    void returnsSingleAccountAndBalances() throws Exception {
        when(compatibilityService.account("17959259"))
                .thenReturn(Optional.of(Map.of("id", "17959259", "name", "IBKR")));
        when(compatibilityService.accountBalances("17959259"))
                .thenReturn(
                        Map.of(
                                "balances",
                                List.of(
                                        Map.of(
                                                "accountId", "17959259",
                                                "date", "2026-07-01T00:00:00Z",
                                                "id", "17959259:2026-07-01",
                                                "value", 100.0d,
                                                "valueInBaseCurrency", 120.0d))));

        mockMvc
                .perform(get("/api/v1/account/17959259"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("17959259"));
        mockMvc
                .perform(get("/api/v1/account/17959259/balances"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balances", hasSize(1)))
                .andExpect(jsonPath("$.balances[0].valueInBaseCurrency").value(120.0d));
    }
}
