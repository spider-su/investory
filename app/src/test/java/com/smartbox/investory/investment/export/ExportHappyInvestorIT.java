package com.smartbox.investory.investment.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.opencsv.CSVReader;
import com.smartbox.investory.testsupport.FastDatabaseTest;
import com.smartbox.investory.testsupport.happyinvestor.HappyInvestorTestData;
import java.io.StringReader;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

/** Full HappyInvestor database-to-HTTP verification for the Yahoo export file. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class ExportHappyInvestorIT extends FastDatabaseTest {
  private static final ZoneId ZONE = ZoneId.of("Europe/Warsaw");
  private static final LocalDate MONTH_START = LocalDate.now(ZONE).withDayOfMonth(1);
  private static final String TRADE_DATE_DISPLAY =
      MONTH_START.format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
  private static final String TRADE_DATE = MONTH_START.format(DateTimeFormatter.BASIC_ISO_DATE);
  private static final String TRADE_TIME = "00:00 Europe/Warsaw";

  @Autowired private MockMvc mockMvc;

  @Test
  void downloadsExactHappyInvestorYahooCsv() throws Exception {
    String csv =
        mockMvc
            .perform(
                get("/api/v1/investment/export/generate")
                    .param("portfolioId", String.valueOf(HappyInvestorTestData.PORTFOLIO_ID))
                    .with(SecurityMockMvcRequestPostProcessors.user("admin").roles("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(content().contentType("text/csv"))
            .andExpect(
                header()
                    .string(
                        "Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
            .andReturn()
            .getResponse()
            .getContentAsString();

    try (CSVReader reader = new CSVReader(new StringReader(csv))) {
      List<String[]> rows = reader.readAll();
      assertThat(rows)
          .containsExactly(
              row(
                  "Symbol",
                  "Current Price",
                  "Date",
                  "Time",
                  "Change",
                  "Open",
                  "High",
                  "Low",
                  "Volume",
                  "Trade Date",
                  "Purchase Price",
                  "Quantity",
                  "Commission",
                  "High Limit",
                  "Low Limit",
                  "Comment",
                  "Transaction Type"),
              row(
                  "AAPL",
                  "249.059",
                  TRADE_DATE_DISPLAY,
                  TRADE_TIME,
                  "",
                  "",
                  "",
                  "",
                  "",
                  TRADE_DATE,
                  "186.66666666666666",
                  "150",
                  "",
                  "",
                  "",
                  "",
                  "BUY"),
              row(
                  "GOOGL",
                  "",
                  TRADE_DATE_DISPLAY,
                  TRADE_TIME,
                  "",
                  "",
                  "",
                  "",
                  "",
                  TRADE_DATE,
                  "150.0",
                  "5",
                  "",
                  "",
                  "",
                  "",
                  "BUY"),
              row(
                  "MSFT",
                  "",
                  TRADE_DATE_DISPLAY,
                  TRADE_TIME,
                  "",
                  "",
                  "",
                  "",
                  "",
                  TRADE_DATE,
                  "100.0",
                  "10",
                  "",
                  "",
                  "",
                  "",
                  "BUY"),
              row(
                  "NVDA",
                  "",
                  TRADE_DATE_DISPLAY,
                  TRADE_TIME,
                  "",
                  "",
                  "",
                  "",
                  "",
                  TRADE_DATE,
                  "100.0",
                  "10",
                  "",
                  "",
                  "",
                  "",
                  "BUY"),
              row(
                  "TSLA",
                  "403.84",
                  TRADE_DATE_DISPLAY,
                  TRADE_TIME,
                  "",
                  "",
                  "",
                  "",
                  "",
                  TRADE_DATE,
                  "200.0",
                  "1",
                  "",
                  "",
                  "",
                  "",
                  "BUY"),
              row(
                  "VWRA.L",
                  "",
                  TRADE_DATE_DISPLAY,
                  TRADE_TIME,
                  "",
                  "",
                  "",
                  "",
                  "",
                  TRADE_DATE,
                  "123.33333333333333",
                  "30",
                  "",
                  "",
                  "",
                  "",
                  "BUY"));
    }
  }

  private static String[] row(String... values) {
    return Arrays.copyOf(values, values.length);
  }
}
