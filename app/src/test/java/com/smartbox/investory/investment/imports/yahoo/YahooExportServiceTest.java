package com.smartbox.investory.investment.imports.yahoo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.opencsv.CSVReader;
import com.smartbox.investory.investment.infrastructure.integration.export.yahoo.YahooExportService;
import com.smartbox.investory.investment.infrastructure.persistence.OpenedPositionRepository;
import com.smartbox.investory.investment.infrastructure.persistence.account.AccountStatisticsEntity;
import com.smartbox.investory.investment.infrastructure.persistence.account.AccountStatisticsRepository;
import com.smartbox.investory.shared.currency.CurrencyType;
import com.smartbox.investory.testsupport.portfolio.PortfolioBuilders;
import com.smartbox.investory.testsupport.portfolio.PortfolioTestData;
import java.io.FileReader;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class YahooExportServiceTest {

  @Mock private OpenedPositionRepository openedPositionRepository;
  @Mock private AccountStatisticsRepository accountStatisticsRepository;

  @TempDir Path tempDir;

  private void stubNoAccountSummaries() {
    lenient().when(accountStatisticsRepository.findAll()).thenReturn(List.of());
  }

  private YahooExportService service() {
    return new YahooExportService(openedPositionRepository, accountStatisticsRepository, "");
  }

  @Test
  void exportToYahooCsv_weightedAveragePriceForMultipleLots() throws Exception {
    Path output = tempDir.resolve("yahoo-export.csv");
    YahooExportService service = service();

    // Open lots: 10 AAPL @ $100 + 2 AAPL @ $120 → weighted avg = (10*100 + 2*120) / 12 = 103.33
    when(openedPositionRepository.findAll())
        .thenReturn(
            List.of(
                PortfolioBuilders.openPosition(PortfolioTestData.AAPL)
                    .withId(1L)
                    .forAccount(51499241L)
                    .quantity(10.0)
                    .price(100.0)
                    .marketPrice(130.0)
                    .on(PortfolioTestData.YEAR_END)
                    .build(),
                PortfolioBuilders.openPosition(PortfolioTestData.AAPL)
                    .withId(2L)
                    .forAccount(51499241L)
                    .quantity(2.0)
                    .price(120.0)
                    .marketPrice(130.0)
                    .on(PortfolioTestData.AAPL_SECOND_BUY_DATE)
                    .build()));
    stubNoAccountSummaries();

    service.exportToYahooCsv(output.toString());

    try (CSVReader reader = new CSVReader(new FileReader(output.toFile()))) {
      List<String[]> rows = reader.readAll();
      // Header + 1 merged AAPL row (weighted avg)
      assertEquals(2, rows.size());

      String[] aaplRow = findRowBySymbol(rows, "AAPL").orElseThrow();
      assertEquals("12", aaplRow[11]); // total qty
      assertEquals(currentMonthStartTradeDate(), aaplRow[9]);
      // weighted avg = (10*100 + 2*120) / 12 = 1240/12 ≈ 103.33333...
      double price = Double.parseDouble(aaplRow[10]);
      assertEquals(103.33, price, 0.01);
    }
  }

  @Test
  void exportToYahooCsv_mixedCurrencyPositionsMergedByRawPrice() throws Exception {
    Path output = tempDir.resolve("yahoo-export-mixed.csv");
    YahooExportService service = service();

    // NVDA.US: 1 share @ $146 (USD account) + 1 share @ $150 (PLN account)
    // openPrice is always in instrument currency (USD), NOT account currency.
    // Weighted avg = (146 + 150) / 2 = 148
    when(openedPositionRepository.findAll())
        .thenReturn(
            List.of(
                PortfolioBuilders.openPosition(PortfolioTestData.AAPL)
                    .withId(1L)
                    .forAccount(51499241L)
                    .symbol("NVDA.US")
                    .quantity(1.0)
                    .price(146.0)
                    .marketPrice(130.0)
                    .on(PortfolioTestData.YEAR_END)
                    .build(),
                PortfolioBuilders.openPosition(PortfolioTestData.AAPL)
                    .withId(2L)
                    .forAccount(50290466L)
                    .symbol("NVDA.US")
                    .currency(CurrencyType.PLN)
                    .quantity(1.0)
                    .price(150.0)
                    .marketPrice(130.0)
                    .on(PortfolioTestData.YEAR_END)
                    .build()));
    stubNoAccountSummaries();

    service.exportToYahooCsv(output.toString());

    try (CSVReader reader = new CSVReader(new FileReader(output.toFile()))) {
      List<String[]> rows = reader.readAll();
      assertTrue(rows.size() >= 2);

      String[] nvdaRow = findRowBySymbol(rows, "NVDA").orElseThrow();
      assertEquals("2", nvdaRow[11]); // 1 + 1 = 2 total shares
      double price = Double.parseDouble(nvdaRow[10]);
      assertEquals(148.0, price, 0.01); // raw average, no currency conversion
    }
  }

  @Test
  void exportToYahooCsv_ibkrPositionsMergedWithXtbCounterpart() throws Exception {
    Path output = tempDir.resolve("yahoo-export-ibkr-merge.csv");
    YahooExportService service = service();

    // XTB: VWRA.UK (5 @ 90) + IBKR: VWRA (10 @ 92) → merged into VWRA.L (15, avg = 91.33)
    when(openedPositionRepository.findAll())
        .thenReturn(
            List.of(
                PortfolioBuilders.openPosition(PortfolioTestData.IWDA_AS)
                    .withId(1L)
                    .forAccount(51499241L)
                    .symbol("VWRA.UK")
                    .currency(CurrencyType.USD)
                    .quantity(5.0)
                    .price(90.0)
                    .marketPrice(100.0)
                    .on(PortfolioTestData.YEAR_END)
                    .build(),
                PortfolioBuilders.openPosition(PortfolioTestData.IWDA_AS)
                    .withId(2L)
                    .forAccount(51499241L)
                    .symbol("VWRA")
                    .currency(CurrencyType.USD)
                    .quantity(10.0)
                    .price(92.0)
                    .marketPrice(100.0)
                    .on(PortfolioTestData.YEAR_END)
                    .build()));
    stubNoAccountSummaries();

    service.exportToYahooCsv(output.toString());

    try (CSVReader reader = new CSVReader(new FileReader(output.toFile()))) {
      List<String[]> rows = reader.readAll();
      assertTrue(rows.size() >= 2);

      String[] merged = findRowBySymbol(rows, "VWRA.L").orElseThrow();
      assertEquals("15", merged[11]); // 5 + 10 total shares
      assertEquals("BUY", merged[16]);
      assertTrue(
          findRowBySymbol(rows, "VWRA").isEmpty(), "IBKR bare ticker should be merged into VWRA.L");
    }
  }

  @Test
  void exportToYahooCsv_cashBalanceEmitsUsdtRow() throws Exception {
    Path output = tempDir.resolve("yahoo-export-cash.csv");
    YahooExportService service = service();

    when(openedPositionRepository.findAll()).thenReturn(List.of());
    when(accountStatisticsRepository.findAll())
        .thenReturn(
            List.of(accountStatistics(51499241L, 12699.0), accountStatistics(51548444L, 8000.0)));

    service.exportToYahooCsv(output.toString());

    try (CSVReader reader = new CSVReader(new FileReader(output.toFile()))) {
      List<String[]> rows = reader.readAll();
      assertEquals(2, rows.size()); // Header + USDT row
      String[] usdtRow = findRowBySymbol(rows, "USDT-USD").orElseThrow();
      assertEquals("20699", usdtRow[11]); // account_statistics cash, not account balance/equity
      assertEquals("BUY", usdtRow[16]);
      assertEquals("1.0", usdtRow[10]); // purchase price = 1
      assertEquals(currentMonthStartTradeDate(), usdtRow[9]);
    }
  }

  @Test
  void exportToYahooCsv_noUsdtRowWhenCashIsZero() throws Exception {
    Path output = tempDir.resolve("yahoo-export-nocash.csv");
    YahooExportService service = service();

    when(openedPositionRepository.findAll())
        .thenReturn(
            List.of(
                PortfolioBuilders.openPosition(PortfolioTestData.AAPL)
                    .withId(1L)
                    .forAccount(51499241L)
                    .quantity(10.0)
                    .price(100.0)
                    .marketPrice(130.0)
                    .on(PortfolioTestData.YEAR_END)
                    .build()));
    when(accountStatisticsRepository.findAll())
        .thenReturn(List.of(accountStatistics(51499241L, 0.0)));

    service.exportToYahooCsv(output.toString());

    try (CSVReader reader = new CSVReader(new FileReader(output.toFile()))) {
      List<String[]> rows = reader.readAll();
      assertEquals(2, rows.size()); // Header + AAPL
      assertTrue(findRowBySymbol(rows, "USDT-USD").isEmpty());
    }
  }

  private Optional<String[]> findRowBySymbol(List<String[]> rows, String exactSymbol) {
    return rows.stream().skip(1).filter(row -> exactSymbol.equals(row[0])).findFirst();
  }

  private static AccountStatisticsEntity accountStatistics(Long accountId, Double cashBalance) {
    return PortfolioBuilders.accountStatistics()
        .account(
            new PortfolioTestData.AccountDefinition(
                accountId, "Cash AccountEntity", CurrencyType.USD, "Broker"))
        .balances(cashBalance, 0.0, 0.0)
        .build();
  }

  private static String currentMonthStartTradeDate() {
    return LocalDate.now(ZoneId.of("Europe/Warsaw"))
        .withDayOfMonth(1)
        .format(DateTimeFormatter.BASIC_ISO_DATE);
  }
}
