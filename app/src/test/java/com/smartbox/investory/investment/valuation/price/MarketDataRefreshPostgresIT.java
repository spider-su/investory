package com.smartbox.investory.investment.valuation.price;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartbox.investory.investment.port.market.MarketDataProvider;
import com.smartbox.investory.investment.port.market.MarketQuote;
import com.smartbox.investory.testsupport.FastDatabaseTest;
import com.smartbox.investory.testsupport.happyinvestor.HappyInvestorMarketDataFacts;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Provider-to-persistence market refresh coverage on canonical HappyInvestor positions. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class MarketDataRefreshPostgresIT extends FastDatabaseTest {
  @Autowired private MarketDataService marketData;
  @Autowired private JdbcTemplate jdbc;
  @MockitoBean private MarketDataProvider provider;

  @AfterEach
  void restoreCanonicalMarketData() {
    jdbc.update(
        "delete from investory.asset_price_history where source = 'YAHOO_FINANCE' and price_date >= ?",
        HappyInvestorMarketDataFacts.REFRESH_DATE);
    jdbc.update(
        """
        update investory.assets
        set market_price = case id when 251 then 249.059 when 1001 then 403.840 end,
            market_price_usd = case id when 251 then 249.059 when 1001 then 403.840 end,
            price_source = 'STOOQ',
            price_updated_at = timestamptz '2025-01-01 12:00:00 Europe/Warsaw'
        where id in (251, 1001)
        """);
  }

  @Test
  void persistsCanonicalUsdQuotesAndReprocessingIsIdempotent() {
    when(provider.externalSymbol(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
    when(provider.fetchQuotes(anyList()))
        .thenAnswer(
            invocation -> {
              List<String> symbols = invocation.getArgument(0);
              Map<String, MarketQuote> quotes = new LinkedHashMap<>();
              if (symbols.contains("GOOGL")) quotes.put("GOOGL", quote("GOOGL", "249.059"));
              if (symbols.contains("TSLA")) quotes.put("TSLA", quote("TSLA", "403.840"));
              return quotes;
            });

    marketData.updateStocks(1L);

    assertQuote(251L, "GOOGL.US", HappyInvestorMarketDataFacts.GOOGL_CLOSE);
    assertQuote(1001L, "TSLA.US", HappyInvestorMarketDataFacts.TESLA_CLOSE);
    verify(provider).fetchQuotes(anyList());

    int before = countRefreshRows();
    marketData.updateStocks(1L);
    assertThat(countRefreshRows()).isEqualTo(before);
  }

  @Test
  void missingAndMalformedProviderQuotesDoNotEraseSuccessfulCanonicalQuote() {
    when(provider.externalSymbol(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
    when(provider.fetchQuotes(anyList()))
        .thenReturn(
            Map.of(
                "GOOGL", quote("GOOGL", "251.25"),
                "TSLA", quote("TSLA", "NaN")));

    marketData.updateStocks(1L);

    assertThat(
            jdbc.queryForObject(
                "select close_price from investory.asset_price_history where asset_id = 251 and price_date = date '2026-08-20' and source = 'YAHOO_FINANCE'",
                BigDecimal.class))
        .isEqualByComparingTo("251.25");
    assertThat(
            jdbc.queryForObject(
                "select count(*) from investory.asset_price_history where asset_id = 1001 and price_date = date '2026-08-20' and source = 'YAHOO_FINANCE'",
                Integer.class))
        .isZero();
  }

  private void assertQuote(long assetId, String symbol, BigDecimal expected) {
    assertThat(
            jdbc.queryForObject(
                    "select price_date from investory.asset_price_history where asset_id = ? and price_date = date '2026-08-20' and source = 'YAHOO_FINANCE'",
                    java.sql.Date.class,
                    assetId)
                .toLocalDate())
        .isEqualTo(HappyInvestorMarketDataFacts.REFRESH_DATE);
    assertThat(
            jdbc.queryForObject(
                "select close_price from investory.asset_price_history where asset_id = ? and price_date = date '2026-08-20' and source = 'YAHOO_FINANCE'",
                BigDecimal.class,
                assetId))
        .isEqualByComparingTo(expected);
    assertThat(
            jdbc.queryForObject(
                "select price_currency from investory.asset_price_history where asset_id = ? and price_date = date '2026-08-20' and source = 'YAHOO_FINANCE'",
                String.class,
                assetId))
        .isEqualTo("USD");
    assertThat(
            jdbc.queryForObject(
                "select price_origin from investory.asset_price_history where asset_id = ? and price_date = date '2026-08-20' and source = 'YAHOO_FINANCE'",
                String.class,
                assetId))
        .isEqualTo(HappyInvestorMarketDataFacts.PRICE_ORIGIN);
    assertThat(
            jdbc.queryForObject(
                "select source from investory.asset_price_history where asset_id = ? and price_date = date '2026-08-20' and source = 'YAHOO_FINANCE'",
                String.class,
                assetId))
        .isEqualTo(HappyInvestorMarketDataFacts.PROVIDER);
    assertThat(
            jdbc.queryForObject(
                "select symbol from investory.assets where id = ?", String.class, assetId))
        .isEqualTo(symbol);
    assertThat(
            jdbc.queryForObject(
                "select price_source from investory.assets where id = ?", String.class, assetId))
        .isEqualTo("YahooFinance");
  }

  private int countRefreshRows() {
    return jdbc.queryForObject(
        "select count(*) from investory.asset_price_history where price_date = date '2026-08-20' and source = 'YAHOO_FINANCE'",
        Integer.class);
  }

  private static MarketQuote quote(String symbol, String close) {
    MarketQuote quote = new MarketQuote();
    quote.setSymbol(symbol);
    quote.setCurrency("USD");
    quote.setDatetime("2026-08-20T21:00:00Z");
    quote.setClose(Double.parseDouble(close));
    return quote;
  }
}
