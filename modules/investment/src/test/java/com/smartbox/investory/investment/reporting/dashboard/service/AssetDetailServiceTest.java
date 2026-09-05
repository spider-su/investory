package com.smartbox.investory.investment.reporting.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smartbox.investory.investment.api.asset.model.*;
import com.smartbox.investory.investment.api.asset.model.AssetDetailView;
import com.smartbox.investory.investment.api.reporting.DashboardPeriod;
import com.smartbox.investory.investment.infrastructure.persistence.account.AccountEntity;
import com.smartbox.investory.investment.infrastructure.persistence.account.AccountRepository;
import com.smartbox.investory.investment.infrastructure.persistence.portfolio.SymbolPerformanceRepository;
import com.smartbox.investory.investment.ledger.asset.persistence.AssetEntity;
import com.smartbox.investory.investment.ledger.asset.persistence.AssetRepository;
import com.smartbox.investory.investment.ledger.cash.CashOperationType;
import com.smartbox.investory.investment.ledger.cash.persistence.CashOperationEntity;
import com.smartbox.investory.investment.ledger.cash.persistence.CashOperationRepository;
import com.smartbox.investory.investment.ledger.position.PositionSettlementModel;
import com.smartbox.investory.investment.ledger.position.PositionType;
import com.smartbox.investory.investment.ledger.position.persistence.PositionEntity;
import com.smartbox.investory.investment.ledger.position.persistence.PositionRepository;
import com.smartbox.investory.investment.valuation.fx.CurrencyRateService;
import com.smartbox.investory.shared.currency.CurrencyType;
import com.smartbox.investory.shared.time.ClockApplicationTime;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Asset Detail Service")
class AssetDetailServiceTest {

  private static final ClockApplicationTime TIME =
      new ClockApplicationTime(
          Clock.fixed(Instant.parse("2026-09-05T08:00:00Z"), ZoneOffset.UTC),
          ZoneId.of("Europe/Warsaw"));

  private final AssetRepository assets = mock();
  private final PositionRepository open = mock();
  private final PositionRepository closed = mock();
  private final CashOperationRepository cash = mock();
  private final SymbolPerformanceRepository performance = mock();
  private final CurrencyRateService currencyRates = mock();
  private final AccountRepository accounts = mock();
  private final AssetDetailService service =
      new AssetDetailService(
          assets, open, closed, cash, performance, currencyRates, accounts, TIME);

  @DisplayName("aggregates Signed Quantities And Weighted Cost By Account")
  @Test
  void aggregatesSignedQuantitiesAndWeightedCostByAccount() {
    AssetEntity asset =
        AssetEntity.builder()
            .id(9L)
            .symbol("VWCE")
            .name("Vanguard")
            .ticker("VWCE")
            .country("IE")
            .currency(CurrencyType.EUR)
            .assetType("ETF")
            .marketPrice(java.math.BigDecimal.valueOf(120d))
            .build();
    PositionEntity first = position(1L, PositionType.BUY, 2d, 100d);
    PositionEntity second = position(1L, PositionType.BUY, 1d, 130d);
    PositionEntity otherAccount = position(2L, PositionType.SELL, 1d, 90d);
    when(accounts.findAllByPortfolioId(1L)).thenReturn(List.of(account(1L), account(2L)));
    when(assets.findBySymbol("VWCE")).thenReturn(Optional.of(asset));
    when(open.findOpenByAssetIdAndAccountIn(eq(9L), any()))
        .thenReturn(List.of(first, second, otherAccount));
    when(closed.findClosedByAssetIdAndAccountIn(eq(9L), any())).thenReturn(List.of());
    when(cash.findAllByAssetIdAndAccountInAndTypeInOrderByDateDescIdDesc(eq(9L), any(), any()))
        .thenReturn(List.of());
    when(performance.findAllByPortfolioIdAndSymbol(1L, "VWCE")).thenReturn(List.of());

    AssetDetailView view = service.findBySymbol(1L, " vwce ", DashboardPeriod.MAX);

    assertThat(view.holdings()).hasSize(2);
    assertThat(view.holdings().get(0).accountId()).isEqualTo(1L);
    assertThat(view.holdings().get(0).quantity()).isEqualTo(3d);
    assertThat(view.holdings().get(0).averageCost()).isEqualTo(110d);
    assertThat(view.holdings().get(0).marketValue()).isEqualTo(360d);
    assertThat(view.totalQuantity()).isEqualTo(2d);
  }

  @DisplayName("rejects Blank And Unknown Symbols")
  @Test
  void rejectsBlankAndUnknownSymbols() {
    when(accounts.findAllByPortfolioId(1L)).thenReturn(List.of());
    assertThatThrownBy(() -> service.findBySymbol(1L, " ", DashboardPeriod.MAX))
        .isInstanceOf(AssetDetailNotFoundException.class);
    when(assets.findBySymbol("NOPE")).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.findBySymbol(1L, "nope", DashboardPeriod.MAX))
        .isInstanceOf(AssetDetailNotFoundException.class);
  }

  @DisplayName("result Only Position Does Not Expose Full Notional Value")
  @Test
  void resultOnlyPositionDoesNotExposeFullNotionalValue() {
    AssetEntity asset =
        AssetEntity.builder()
            .id(9L)
            .symbol("CFD")
            .currency(CurrencyType.USD)
            .marketPrice(BigDecimal.valueOf(120d))
            .build();
    PositionEntity position = position(1L, PositionType.BUY, 10d, 100d);
    position.setSettlementModel(PositionSettlementModel.RESULT_ONLY);
    when(accounts.findAllByPortfolioId(1L)).thenReturn(List.of(account(1L)));
    when(assets.findBySymbol("CFD")).thenReturn(Optional.of(asset));
    when(open.findOpenByAssetIdAndAccountIn(eq(9L), any())).thenReturn(List.of(position));
    when(closed.findClosedByAssetIdAndAccountIn(eq(9L), any())).thenReturn(List.of());
    when(cash.findAllByAssetIdAndAccountInAndTypeInOrderByDateDescIdDesc(eq(9L), any(), any()))
        .thenReturn(List.of());
    when(performance.findAllByPortfolioIdAndSymbol(1L, "CFD")).thenReturn(List.of());

    AssetDetailView view = service.findBySymbol(1L, "CFD", DashboardPeriod.MAX);

    assertThat(view.holdings().getFirst().marketValue()).isNull();
    assertThat(view.holdings().getFirst().unrealizedProfitLoss()).isNull();
    assertThat(view.totalMarketValue()).isNull();
  }

  @DisplayName("converts Dividend Totals To Asset Display Currency")
  @Test
  void convertsDividendTotalsToAssetDisplayCurrency() {
    AssetEntity asset =
        AssetEntity.builder().id(9L).symbol("VWCE").currency(CurrencyType.EUR).build();
    CashOperationEntity usdDividend = cash(CashOperationType.DIVIDEND, "10", CurrencyType.USD);
    CashOperationEntity eurDividend = cash(CashOperationType.DIVIDEND, "5", CurrencyType.EUR);
    CashOperationEntity tax = cash(CashOperationType.WITHHOLDING_TAX, "-2", CurrencyType.USD);
    when(accounts.findAllByPortfolioId(1L)).thenReturn(List.of(account(1L)));
    when(assets.findBySymbol("VWCE")).thenReturn(Optional.of(asset));
    when(open.findOpenByAssetIdAndAccountIn(eq(9L), any())).thenReturn(List.of());
    when(closed.findClosedByAssetIdAndAccountIn(eq(9L), any())).thenReturn(List.of());
    when(cash.findAllByAssetIdAndAccountInAndTypeInOrderByDateDescIdDesc(eq(9L), any(), any()))
        .thenReturn(List.of(usdDividend, eurDividend, tax));
    when(performance.findAllByPortfolioIdAndSymbol(1L, "VWCE")).thenReturn(List.of());
    when(currencyRates.convertToBaseCurrency(
            new BigDecimal("10.00000000"),
            CurrencyType.EUR,
            CurrencyType.USD,
            LocalDate.of(2026, 8, 1)))
        .thenReturn(new BigDecimal("9.00000000"));
    when(currencyRates.convertToBaseCurrency(
            new BigDecimal("-2.00000000"),
            CurrencyType.EUR,
            CurrencyType.USD,
            LocalDate.of(2026, 8, 1)))
        .thenReturn(new BigDecimal("-1.80000000"));

    AssetDetailView view = service.findBySymbol(1L, "VWCE", DashboardPeriod.MAX);

    assertThat(view.totalGrossDividends()).isEqualTo(14d);
    assertThat(view.totalWithholdingTax()).isEqualTo(1.8d);
    assertThat(view.totalNetDividends()).isEqualTo(12.2d);
  }

  private PositionEntity position(Long account, PositionType type, double quantity, double price) {
    PositionEntity position = new PositionEntity();
    position.setAccount(account);
    position.setType(type);
    position.setVolume(java.math.BigDecimal.valueOf(quantity));
    position.setOpenPrice(java.math.BigDecimal.valueOf(price));
    position.setCostCurrency(CurrencyType.EUR);
    position.setPriceCurrency(CurrencyType.EUR);
    position.setSettlementModel(PositionSettlementModel.CASH_SETTLED);
    return position;
  }

  private AccountEntity account(Long id) {
    var account = new AccountEntity();
    account.setId(id);
    return account;
  }

  private CashOperationEntity cash(CashOperationType type, String amount, CurrencyType currency) {
    CashOperationEntity operation = new CashOperationEntity();
    operation.setType(type);
    operation.setAmount(new BigDecimal(amount));
    operation.setCurrency(currency);
    operation.setDate(ZonedDateTime.of(2026, 8, 1, 12, 0, 0, 0, ZoneId.of("Europe/Warsaw")));
    return operation;
  }
}
