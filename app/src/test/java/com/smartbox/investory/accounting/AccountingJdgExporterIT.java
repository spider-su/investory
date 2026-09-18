package com.smartbox.investory.accounting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.smartbox.investory.investment.valuation.fx.CurrencyRateService;
import com.smartbox.investory.shared.currency.CurrencyType;
import com.smartbox.investory.testsupport.accounting.AccountingDatabaseTest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class AccountingJdgExporterIT extends AccountingDatabaseTest {

  @Autowired private AccountingJdgExporter exporter;

  @MockitoBean(name = "currencyRateService")
  private CurrencyRateService currencyConversion;

  @BeforeEach
  void configureCapturedFxGoldens() {
    stubFx(LocalDate.of(2026, 1, 30), "32171.23");
    stubFx(LocalDate.of(2026, 2, 27), "32249.12");
    stubFx(LocalDate.of(2026, 3, 30), "32706.52");
    stubFx(LocalDate.of(2026, 4, 29), "32481.25");
    stubFx(LocalDate.of(2026, 5, 29), "32317.08");
    stubFx(LocalDate.of(2026, 6, 29), "32750.80");
    stubFx(LocalDate.of(2026, 7, 30), "32908.87");
  }

  @Test
  void exportsExistingJdgDatasetAsMonthlyCsv() {
    String csv = new String(exporter.exportCsv(1L));
    List<String> rows = Arrays.asList(csv.strip().split("\\R"));

    assertThat(rows).hasSize(19);
    assertThat(rows.get(0))
        .isEqualTo(
            "period,has_uop,domestic_revenue_net_pln,foreign_booked_revenue_pln,total_revenue_pln,"
                + "ryczalt_tax_pln,output_vat_pln,deductible_input_vat_pln,vat_payable_pln,"
                + "social_zus_pln,health_zus_pln,total_zus_pln");
    assertThat(rows)
        .contains(
            "2025-03,true,0.0000,0.0000,0.0000,0,0.0000,0.00,0,0.00,1384.97,1384.97",
            "2025-12,true,0.0000,0.0000,0.0000,0,0.0000,0.00,0,0.00,1384.97,1384.97",
            "2026-01,true,29600.0000,32171.2300,61771.2300,7323,6808.0000,93.54,6714,0.00,1495.04,1495.04",
            "2026-03,true,32560.0000,32706.5200,65266.5200,7742,7488.8000,238.38,7251,0.00,1495.04,1495.04",
            "2026-07,true,16250.0000,32908.8700,49008.8700,5791,3703.0000,145.99,3557,0.00,1495.04,1495.04");
  }

  private void stubFx(LocalDate rateDate, String pln) {
    when(currencyConversion.convertToBaseCurrency(
            new BigDecimal("7636.0000"), CurrencyType.PLN, CurrencyType.EUR, rateDate))
        .thenReturn(new BigDecimal(pln));
  }
}
