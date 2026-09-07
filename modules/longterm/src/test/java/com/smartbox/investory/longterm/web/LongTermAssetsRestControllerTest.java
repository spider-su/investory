package com.smartbox.investory.longterm.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartbox.investory.longterm.api.LongTermAssetsApi;
import com.smartbox.investory.longterm.api.model.BondCommand;
import com.smartbox.investory.longterm.api.model.BondView;
import com.smartbox.investory.longterm.api.model.CashReserveCommand;
import com.smartbox.investory.longterm.api.model.CashReserveView;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class LongTermAssetsRestControllerTest {
  @Mock private LongTermAssetsApi assets;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    mvc = MockMvcBuilders.standaloneSetup(new LongTermAssetsRestController(assets)).build();
  }

  @Test
  void bondDetailAndUnrelatedUpdatePreserveAcquisitionDate() throws Exception {
    BondView original = bond("Original");
    BondView updated = bond("Renamed");
    when(assets.bond(7L, 41L)).thenReturn(original, updated);
    when(assets.updateBond(any())).thenReturn(updated);

    mvc.perform(get("/api/v1/portfolios/7/long-term-assets/bond/41"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.acquisitionDate").value("2024-07-31"));
    mvc.perform(
            put("/api/v1/portfolios/7/long-term-assets/bond/41")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name":"Renamed","currency":"PLN","value":10000,
                     "acquisitionDate":"2024-07-31","interestRate":0.059,
                     "maturityDate":"2026-02-28","notes":"notes"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Renamed"))
        .andExpect(jsonPath("$.acquisitionDate").value("2024-07-31"));
    mvc.perform(get("/api/v1/portfolios/7/long-term-assets/bond/41"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.acquisitionDate").value("2024-07-31"));
    verify(assets).updateBond(any());
  }

  @Test
  void cashDetailAndUnrelatedUpdatePreserveAcquisitionRateAndMaturity() throws Exception {
    CashReserveView original = cash("Original");
    CashReserveView updated = cash("Renamed");
    when(assets.cashReserve(7L, 42L)).thenReturn(original, updated);
    when(assets.updateCashReserve(any())).thenReturn(updated);

    mvc.perform(get("/api/v1/portfolios/7/long-term-assets/cash-reserve/42"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.acquisitionDate").value("2024-08-01"))
        .andExpect(jsonPath("$.interestRate").value(0.04))
        .andExpect(jsonPath("$.maturityDate").value("2027-08-01"));
    mvc.perform(
            put("/api/v1/portfolios/7/long-term-assets/cash-reserve/42")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name":"Renamed","currency":"PLN","value":50000,
                     "acquisitionDate":"2024-08-01","interestRate":0.04,
                     "maturityDate":"2027-08-01","notes":"notes"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.acquisitionDate").value("2024-08-01"))
        .andExpect(jsonPath("$.interestRate").value(0.04))
        .andExpect(jsonPath("$.maturityDate").value("2027-08-01"));
    mvc.perform(get("/api/v1/portfolios/7/long-term-assets/cash-reserve/42"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.acquisitionDate").value("2024-08-01"));
    verify(assets).updateCashReserve(any());
  }

  private static BondCommand command(String name) {
    return new BondCommand(
        7L,
        41L,
        name,
        CurrencyType.PLN,
        new BigDecimal("10000"),
        LocalDate.of(2024, 7, 31),
        new BigDecimal("0.059"),
        LocalDate.of(2026, 2, 28),
        "notes");
  }

  private static CashReserveCommand cashCommand(String name) {
    return new CashReserveCommand(
        7L,
        42L,
        name,
        CurrencyType.PLN,
        new BigDecimal("50000"),
        LocalDate.of(2024, 8, 1),
        new BigDecimal("0.04"),
        LocalDate.of(2027, 8, 1),
        "notes");
  }

  private static BondView bond(String name) {
    return new BondView(
        41L,
        7L,
        name,
        CurrencyType.PLN,
        LocalDate.of(2024, 7, 31),
        new BigDecimal("10000"),
        new BigDecimal("0.059"),
        LocalDate.of(2026, 2, 28),
        true,
        "notes");
  }

  private static CashReserveView cash(String name) {
    return new CashReserveView(
        42L,
        7L,
        name,
        CurrencyType.PLN,
        LocalDate.of(2024, 8, 1),
        new BigDecimal("50000"),
        new BigDecimal("0.04"),
        LocalDate.of(2027, 8, 1),
        true,
        "notes");
  }
}
