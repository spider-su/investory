package com.smartbox.investory.testsupport.portfolio;

import com.smartbox.investory.investment.imports.BrokerType;
import com.smartbox.investory.investment.imports.ImportBatchStatus;
import com.smartbox.investory.investment.imports.ImportSourceType;
import com.smartbox.investory.investment.infrastructure.persistence.account.AccountDailyEntity;
import com.smartbox.investory.investment.infrastructure.persistence.account.AccountEntity;
import com.smartbox.investory.investment.infrastructure.persistence.account.AccountStatisticsEntity;
import com.smartbox.investory.investment.infrastructure.persistence.imports.ImportHistoryEntity;
import com.smartbox.investory.investment.ledger.asset.persistence.AssetEntity;
import com.smartbox.investory.investment.ledger.cash.CashOperationType;
import com.smartbox.investory.investment.ledger.cash.persistence.CashOperationEntity;
import com.smartbox.investory.investment.ledger.position.PositionType;
import com.smartbox.investory.investment.ledger.position.persistence.PositionEntity;
import com.smartbox.investory.investment.valuation.fx.persistence.CurrencyRateEntity;
import com.smartbox.investory.shared.currency.CurrencyType;
import com.smartbox.investory.testsupport.portfolio.PortfolioTestData.AccountDefinition;
import com.smartbox.investory.testsupport.portfolio.PortfolioTestData.AssetDefinition;
import java.time.LocalDate;
import java.time.ZonedDateTime;

public final class PortfolioBuilders {

  private PortfolioBuilders() {}

  public static AccountBuilder account(AccountDefinition definition) {
    return new AccountBuilder(definition);
  }

  public static AssetBuilder asset(AssetDefinition definition) {
    return new AssetBuilder(definition);
  }

  public static CashOperationBuilder cashOperation() {
    return new CashOperationBuilder();
  }

  public static OpenPositionBuilder openPosition(AssetDefinition asset) {
    return new OpenPositionBuilder(asset);
  }

  public static PositionEntityBuilder closedPosition(AssetDefinition asset) {
    return new PositionEntityBuilder(asset);
  }

  public static FxRateBuilder fxRate() {
    return new FxRateBuilder();
  }

  public static AccountDailyBuilder accountDaily() {
    return new AccountDailyBuilder();
  }

  public static AccountStatisticsBuilder accountStatistics() {
    return new AccountStatisticsBuilder();
  }

  public static ImportHistoryBuilder importHistory() {
    return new ImportHistoryBuilder();
  }

  private static void setCurrencies(PositionEntity position, CurrencyType currency) {
    position.setPriceCurrency(currency);
    position.setCostCurrency(currency);
    position.setProfitCurrency(currency);
    position.setCommissionCurrency(currency);
  }

  public static final class AccountBuilder {
    private final AccountEntity account = new AccountEntity();

    private AccountBuilder(AccountDefinition definition) {
      account.setId(definition.id());
      account.setName(definition.name());
      account.setCurrency(definition.currency());
    }

    public AccountBuilder withId(Long id) {
      account.setId(id);
      return this;
    }

    public AccountEntity build() {
      require(account.getId() != null, "account id is required");
      require(account.getCurrency() != null, "account currency is required");
      return account;
    }
  }

  public static final class AssetBuilder {
    private final AssetEntity asset;

    private AssetBuilder(AssetDefinition definition) {
      asset =
          AssetEntity.builder()
              .name(definition.symbol())
              .symbol(definition.symbol())
              .ticker(definition.ticker())
              .ibrk(definition.ibkr())
              .yahoo(definition.yahoo())
              .country(definition.country())
              .currency(definition.currency())
              .assetType(definition.assetType())
              .active(true)
              .build();
    }

    public AssetBuilder withName(String name) {
      asset.setName(name);
      return this;
    }

    public AssetBuilder withLatestPrice(double marketPrice, double marketPriceUsd, LocalDate date) {
      asset.setMarketPrice(java.math.BigDecimal.valueOf(marketPrice));
      asset.setMarketPriceUsd(java.math.BigDecimal.valueOf(marketPriceUsd));
      asset.setPriceSource("TestData");
      asset.setPriceUpdatedAt(PortfolioTestData.atNoon(date));
      return this;
    }

    public AssetBuilder inactive() {
      asset.setActive(false);
      return this;
    }

    public AssetEntity build() {
      requireText(asset.getSymbol(), "asset symbol is required");
      requireText(asset.getTicker(), "asset ticker is required");
      require(asset.getCurrency() != null, "asset currency is required");
      return asset;
    }
  }

  public static final class CashOperationBuilder {
    private Long id;
    private Long account = PortfolioTestData.IBKR_USD_ACCOUNT_ID;
    private CashOperationType type = CashOperationType.DEPOSIT;
    private String symbol;
    private double amount = PortfolioTestData.DEFAULT_USD_DEPOSIT;
    private CurrencyType currency = CurrencyType.USD;
    private String comment = "TestData";
    private ZonedDateTime date = PortfolioTestData.atNoon(PortfolioTestData.JANUARY_DEPOSIT_DATE);

    public CashOperationBuilder withId(Long id) {
      this.id = id;
      return this;
    }

    public CashOperationBuilder forAccount(AccountDefinition account) {
      this.account = account.id();
      this.currency = account.currency();
      return this;
    }

    public CashOperationBuilder forAccount(Long accountId) {
      this.account = accountId;
      return this;
    }

    public CashOperationBuilder deposit(double amount, CurrencyType currency) {
      this.type = CashOperationType.DEPOSIT;
      this.amount = amount;
      this.currency = currency;
      return this;
    }

    public CashOperationBuilder withdrawal(double amount, CurrencyType currency) {
      this.type = CashOperationType.WITHDRAWAL;
      this.amount = -Math.abs(amount);
      this.currency = currency;
      return this;
    }

    public CashOperationBuilder dividend(AssetDefinition asset, double amount) {
      this.type = CashOperationType.DIVIDEND;
      this.symbol = asset.symbol();
      this.amount = amount;
      this.currency = asset.currency();
      return this;
    }

    public CashOperationBuilder withholdingTax(AssetDefinition asset, double amount) {
      this.type = CashOperationType.WITHHOLDING_TAX;
      this.symbol = asset.symbol();
      this.amount = -Math.abs(amount);
      this.currency = asset.currency();
      return this;
    }

    public CashOperationBuilder fee(double amount, CurrencyType currency, String comment) {
      this.type = CashOperationType.COMMISSION;
      this.amount = -Math.abs(amount);
      this.currency = currency;
      this.comment = comment;
      return this;
    }

    public CashOperationBuilder transfer(double amount, CurrencyType currency, String comment) {
      this.type = CashOperationType.TRANSFER;
      this.amount = amount;
      this.currency = currency;
      this.comment = comment;
      return this;
    }

    public CashOperationBuilder type(CashOperationType type) {
      this.type = type;
      return this;
    }

    public CashOperationBuilder on(LocalDate date) {
      this.date = PortfolioTestData.atNoon(date);
      return this;
    }

    public CashOperationBuilder comment(String comment) {
      this.comment = comment;
      return this;
    }

    public CashOperationEntity build() {
      require(account != null, "cash operation account is required");
      require(type != null, "cash operation type is required");
      require(currency != null, "cash operation currency is required");
      CashOperationEntity operation = new CashOperationEntity();
      operation.setId(id);
      operation.setAccount(account);
      operation.setType(type);
      operation.setSymbol(symbol);
      operation.setAmount(java.math.BigDecimal.valueOf(amount));
      operation.setCurrency(currency);
      operation.setComment(comment);
      operation.setDate(date);
      return operation;
    }
  }

  public static final class OpenPositionBuilder {
    private final PositionEntity position = new PositionEntity();

    private OpenPositionBuilder(AssetDefinition asset) {
      position.setAccount(PortfolioTestData.IBKR_USD_ACCOUNT_ID);
      position.setSymbol(asset.symbol());
      setCurrencies(position, asset.currency());
      position.setType(PositionType.BUY);
      position.setVolume(java.math.BigDecimal.valueOf(PortfolioTestData.AAPL_FIRST_BUY_QUANTITY));
      position.setOpenPrice(java.math.BigDecimal.valueOf(PortfolioTestData.AAPL_FIRST_BUY_PRICE));
      position.setPurchaseValue(
          java.math.BigDecimal.valueOf(
              PortfolioTestData.AAPL_FIRST_BUY_QUANTITY * PortfolioTestData.AAPL_FIRST_BUY_PRICE));
      position.setCommission(
          java.math.BigDecimal.valueOf(PortfolioTestData.AAPL_FIRST_BUY_COMMISSION));
      position.setOpenTime(PortfolioTestData.atNoon(PortfolioTestData.AAPL_FIRST_BUY_DATE));
    }

    public OpenPositionBuilder withId(Long id) {
      position.setId(id);
      return this;
    }

    public OpenPositionBuilder forAccount(AccountDefinition account) {
      position.setAccount(account.id());
      return this;
    }

    public OpenPositionBuilder forAccount(Long accountId) {
      position.setAccount(accountId);
      return this;
    }

    public OpenPositionBuilder symbol(String symbol) {
      position.setSymbol(symbol);
      return this;
    }

    public OpenPositionBuilder currency(CurrencyType currency) {
      setCurrencies(position, currency);
      return this;
    }

    public OpenPositionBuilder quantity(double quantity) {
      position.setVolume(java.math.BigDecimal.valueOf(quantity));
      recalculatePurchaseValue();
      return this;
    }

    public OpenPositionBuilder price(double price) {
      position.setOpenPrice(java.math.BigDecimal.valueOf(price));
      recalculatePurchaseValue();
      return this;
    }

    public OpenPositionBuilder marketPrice(double marketPrice) {
      position.setMarketPrice(java.math.BigDecimal.valueOf(marketPrice));
      position.setProfit(
          java.math.BigDecimal.valueOf(
              (marketPrice - position.getOpenPrice().doubleValue())
                  * position.getVolume().doubleValue()));
      return this;
    }

    public OpenPositionBuilder commission(double commission) {
      position.setCommission(java.math.BigDecimal.valueOf(commission));
      return this;
    }

    public OpenPositionBuilder swap(double swap) {
      position.setSwap(java.math.BigDecimal.valueOf(swap));
      return this;
    }

    public OpenPositionBuilder profit(double profit) {
      position.setProfit(java.math.BigDecimal.valueOf(profit));
      return this;
    }

    public OpenPositionBuilder on(LocalDate date) {
      position.setOpenTime(PortfolioTestData.atNoon(date));
      return this;
    }

    public OpenPositionBuilder type(PositionType type) {
      position.setType(type);
      return this;
    }

    public PositionEntity build() {
      requireText(position.getSymbol(), "open position symbol is required");
      require(position.getAccount() != null, "open position account is required");
      require(position.getPriceCurrency() != null, "open position price currency is required");
      require(position.getCostCurrency() != null, "open position cost currency is required");
      require(position.getProfitCurrency() != null, "open position profit currency is required");
      require(
          position.getCommissionCurrency() != null,
          "open position commission currency is required");
      return position;
    }

    private void recalculatePurchaseValue() {
      if (position.getVolume() != null && position.getOpenPrice() != null) {
        position.setPurchaseValue(position.getVolume().multiply(position.getOpenPrice()));
      }
    }
  }

  public static final class PositionEntityBuilder {
    private final PositionEntity position = new PositionEntity();

    private PositionEntityBuilder(AssetDefinition asset) {
      position.setAccount(PortfolioTestData.IBKR_USD_ACCOUNT_ID);
      position.setSymbol(asset.symbol());
      setCurrencies(position, asset.currency());
      position.setType(PositionType.BUY);
      position.setVolume(java.math.BigDecimal.valueOf(10.0));
      position.setOpenPrice(java.math.BigDecimal.valueOf(100.0));
      position.setClosePrice(java.math.BigDecimal.valueOf(110.0));
      position.setPurchaseValue(java.math.BigDecimal.valueOf(1000.0));
      position.setSaleValue(java.math.BigDecimal.valueOf(1100.0));
      position.setCommission(java.math.BigDecimal.valueOf(0.0));
      position.setSwap(java.math.BigDecimal.valueOf(0.0));
      position.setProfit(java.math.BigDecimal.valueOf(100.0));
      position.setOpenTime(PortfolioTestData.atNoon(PortfolioTestData.AAPL_FIRST_BUY_DATE));
      position.setCloseTime(PortfolioTestData.atNoon(PortfolioTestData.AAPL_PARTIAL_SALE_DATE));
    }

    public PositionEntityBuilder profit(double profit) {
      position.setProfit(java.math.BigDecimal.valueOf(profit));
      return this;
    }

    public PositionEntityBuilder symbol(String symbol) {
      position.setSymbol(symbol);
      return this;
    }

    public PositionEntityBuilder currency(CurrencyType currency) {
      setCurrencies(position, currency);
      return this;
    }

    public PositionEntityBuilder commission(double commission) {
      position.setCommission(java.math.BigDecimal.valueOf(commission));
      return this;
    }

    public PositionEntityBuilder swap(double swap) {
      position.setSwap(java.math.BigDecimal.valueOf(swap));
      return this;
    }

    public PositionEntityBuilder closeOn(LocalDate closeDate) {
      position.setCloseTime(PortfolioTestData.atNoon(closeDate));
      return this;
    }

    public PositionEntity build() {
      requireText(position.getSymbol(), "closed position symbol is required");
      require(position.getCloseTime() != null, "closed position close time is required");
      return position;
    }
  }

  public static final class AccountDailyBuilder {
    private final AccountDailyEntity daily = new AccountDailyEntity();

    private AccountDailyBuilder() {
      daily.setId(1L);
      daily.setAccountId(PortfolioTestData.IBKR_USD_ACCOUNT_ID);
      daily.setDate(PortfolioTestData.JANUARY_MONTH_END);
      daily.setCashBalance(java.math.BigDecimal.valueOf(0.0));
      daily.setMarketValue(java.math.BigDecimal.valueOf(0.0));
      daily.setEquity(java.math.BigDecimal.valueOf(0.0));
      daily.setUnrealizedProfit(java.math.BigDecimal.valueOf(0.0));
      daily.setCostBase(java.math.BigDecimal.valueOf(0.0));
      daily.setRealizedProfit(java.math.BigDecimal.valueOf(0.0));
      daily.setDividends(java.math.BigDecimal.valueOf(0.0));
      daily.setInterest(java.math.BigDecimal.valueOf(0.0));
      daily.setFees(java.math.BigDecimal.valueOf(0.0));
      daily.setTaxes(java.math.BigDecimal.valueOf(0.0));
      daily.setDeposits(java.math.BigDecimal.valueOf(0.0));
      daily.setWithdrawals(java.math.BigDecimal.valueOf(0.0));
      daily.setUpdatedAt(PortfolioTestData.atNoon(PortfolioTestData.JANUARY_MONTH_END));
    }

    public AccountDailyBuilder id(Long id) {
      daily.setId(id);
      return this;
    }

    public AccountDailyBuilder account(AccountDefinition account) {
      daily.setAccountId(account.id());
      return this;
    }

    public AccountDailyBuilder account(Long accountId) {
      daily.setAccountId(accountId);
      return this;
    }

    public AccountDailyBuilder on(LocalDate date) {
      daily.setDate(date);
      daily.setUpdatedAt(PortfolioTestData.atNoon(date));
      return this;
    }

    public AccountDailyBuilder valuation(double cashBalance, double marketValue, double costBase) {
      daily.setCashBalance(java.math.BigDecimal.valueOf(cashBalance));
      daily.setMarketValue(java.math.BigDecimal.valueOf(marketValue));
      daily.setEquity(java.math.BigDecimal.valueOf(cashBalance + marketValue));
      daily.setCostBase(java.math.BigDecimal.valueOf(costBase));
      daily.setUnrealizedProfit(java.math.BigDecimal.valueOf(marketValue - costBase));
      return this;
    }

    public AccountDailyBuilder equity(double equity) {
      daily.setEquity(java.math.BigDecimal.valueOf(equity));
      return this;
    }

    public AccountDailyBuilder performance(
        double realizedProfit,
        double unrealizedProfit,
        double dividends,
        double interest,
        double fees,
        double taxes) {
      daily.setRealizedProfit(java.math.BigDecimal.valueOf(realizedProfit));
      daily.setUnrealizedProfit(java.math.BigDecimal.valueOf(unrealizedProfit));
      daily.setDividends(java.math.BigDecimal.valueOf(dividends));
      daily.setInterest(java.math.BigDecimal.valueOf(interest));
      daily.setFees(java.math.BigDecimal.valueOf(fees));
      daily.setTaxes(java.math.BigDecimal.valueOf(taxes));
      return this;
    }

    public AccountDailyEntity build() {
      require(daily.getAccountId() != null, "account daily account id is required");
      require(daily.getDate() != null, "account daily date is required");
      return daily;
    }
  }

  public static final class FxRateBuilder {
    private LocalDate monthStart = PortfolioTestData.PERIOD_START;
    private CurrencyType base = CurrencyType.USD;
    private CurrencyType toCurrency = CurrencyType.EUR;
    private double rate = 1.10;

    public FxRateBuilder on(LocalDate monthStart) {
      this.monthStart = monthStart;
      return this;
    }

    public FxRateBuilder pair(CurrencyType base, CurrencyType toCurrency) {
      this.base = base;
      this.toCurrency = toCurrency;
      return this;
    }

    public FxRateBuilder rate(double rate) {
      this.rate = rate;
      return this;
    }

    public CurrencyRateEntity build() {
      CurrencyRateEntity currencyRate = new CurrencyRateEntity();
      currencyRate.setRateDate(monthStart);
      currencyRate.setBase(base);
      currencyRate.setToCurrency(toCurrency);
      currencyRate.setRate(java.math.BigDecimal.valueOf(rate));
      return currencyRate;
    }
  }

  public static final class AccountStatisticsBuilder {
    private final AccountStatisticsEntity statistics = new AccountStatisticsEntity();

    private AccountStatisticsBuilder() {
      statistics.setAccountId(PortfolioTestData.IBKR_USD_ACCOUNT_ID);
      statistics.setValuationCurrency(CurrencyType.USD.name());
      statistics.setTotalDeposit(java.math.BigDecimal.valueOf(0.0));
      statistics.setTotalWithdrawal(java.math.BigDecimal.valueOf(0.0));
      statistics.setNetDeposit(java.math.BigDecimal.valueOf(0.0));
      statistics.setCashBalance(java.math.BigDecimal.valueOf(0.0));
      statistics.setMarketValue(java.math.BigDecimal.valueOf(0.0));
      statistics.setCostBase(java.math.BigDecimal.valueOf(0.0));
      statistics.setRealizedProfit(java.math.BigDecimal.valueOf(0.0));
      statistics.setUnrealizedProfit(java.math.BigDecimal.valueOf(0.0));
      statistics.setDividends(java.math.BigDecimal.valueOf(0.0));
      statistics.setInterest(java.math.BigDecimal.valueOf(0.0));
      statistics.setFees(java.math.BigDecimal.valueOf(0.0));
      statistics.setTaxes(java.math.BigDecimal.valueOf(0.0));
      statistics.setUpdatedAt(PortfolioTestData.atNoon(PortfolioTestData.YEAR_END));
    }

    public AccountStatisticsBuilder account(AccountDefinition account) {
      statistics.setAccountId(account.id());
      return this;
    }

    public AccountStatisticsBuilder balances(double cash, double marketValue, double costBase) {
      statistics.setCashBalance(java.math.BigDecimal.valueOf(cash));
      statistics.setMarketValue(java.math.BigDecimal.valueOf(marketValue));
      statistics.setCostBase(java.math.BigDecimal.valueOf(costBase));
      statistics.setUnrealizedProfit(java.math.BigDecimal.valueOf(marketValue - costBase));
      return this;
    }

    public AccountStatisticsBuilder deposits(double deposits, double withdrawals) {
      statistics.setTotalDeposit(java.math.BigDecimal.valueOf(deposits));
      statistics.setTotalWithdrawal(java.math.BigDecimal.valueOf(withdrawals));
      statistics.setNetDeposit(java.math.BigDecimal.valueOf(deposits + withdrawals));
      return this;
    }

    public AccountStatisticsBuilder netDeposits(
        double netDepositBase, double ignoredLocalNetDeposit) {
      statistics.setNetDeposit(java.math.BigDecimal.valueOf(netDepositBase));
      return this;
    }

    public AccountStatisticsBuilder performance(
        double realizedProfit, double unrealizedProfit, double dividends) {
      statistics.setRealizedProfit(java.math.BigDecimal.valueOf(realizedProfit));
      statistics.setUnrealizedProfit(java.math.BigDecimal.valueOf(unrealizedProfit));
      statistics.setDividends(java.math.BigDecimal.valueOf(dividends));
      return this;
    }

    public AccountStatisticsEntity build() {
      return statistics;
    }
  }

  public static final class ImportHistoryBuilder {
    private final ImportHistoryEntity history = new ImportHistoryEntity();

    private ImportHistoryBuilder() {
      history.setId(1L);
      history.setBroker(BrokerType.XTB);
      history.setSourceType(ImportSourceType.MANUAL);
      history.setSourceRef("test-source");
      history.setFileName("statement.csv");
      history.setFileSha256("duplicate-import-checksum");
      history.setStartedAt(PortfolioTestData.atNoon(PortfolioTestData.JANUARY_DEPOSIT_DATE));
      history.setFinishedAt(PortfolioTestData.atNoon(PortfolioTestData.JANUARY_DEPOSIT_DATE));
      history.setStatus(ImportBatchStatus.COMPLETED);
      history.setRowsTotal(1);
      history.setRowsApplied(1);
      history.setRowsFailed(0);
      history.setErrorMessage("ok");
    }

    public ImportHistoryBuilder id(Long id) {
      history.setId(id);
      return this;
    }

    public ImportHistoryBuilder broker(BrokerType broker) {
      history.setBroker(broker);
      return this;
    }

    public ImportHistoryBuilder checksum(String checksum) {
      history.setFileSha256(checksum);
      return this;
    }

    public ImportHistoryEntity build() {
      return history;
    }
  }

  private static void require(boolean condition, String message) {
    if (!condition) {
      throw new IllegalStateException(message);
    }
  }

  private static void requireText(String value, String message) {
    require(value != null && !value.isBlank(), message);
  }
}
