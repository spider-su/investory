package com.smartbox.investory.investment.imports.ibkr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartbox.investory.investment.imports.ImportExecutionResult;
import com.smartbox.investory.investment.imports.ImportSourceEvidenceService;
import com.smartbox.investory.investment.infrastructure.persistence.account.AccountEntity;
import com.smartbox.investory.investment.infrastructure.persistence.account.AccountRepository;
import com.smartbox.investory.investment.ledger.asset.AssetCatalogService;
import com.smartbox.investory.investment.ledger.asset.persistence.AssetEntity;
import com.smartbox.investory.investment.ledger.asset.persistence.AssetRepository;
import com.smartbox.investory.investment.ledger.cash.CashOperationType;
import com.smartbox.investory.investment.ledger.cash.persistence.CashOperationEntity;
import com.smartbox.investory.investment.ledger.cash.persistence.CashOperationRepository;
import com.smartbox.investory.investment.ledger.position.persistence.PositionEntity;
import com.smartbox.investory.investment.ledger.position.persistence.PositionRepository;
import com.smartbox.investory.investment.valuation.fx.CurrencyRateService;
import com.smartbox.investory.investment.valuation.price.persistence.AssetPriceHistoryRepository;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("Ibkr Import History Service")
class IbkrImportHistoryServiceTest {

  @Mock private PositionRepository closedPositionRepository;
  @Mock private CashOperationRepository cashOperationRepository;
  @Mock private PositionRepository openedPositionRepository;
  @Mock private AssetPriceHistoryRepository assetPriceHistoryRepository;
  @Mock private AssetRepository assetRepository;
  @Mock private AccountRepository accountRepository;
  @Mock private ImportSourceEvidenceService sourceEvidenceService;
  @Mock private CurrencyRateService currencyRateService;

  private IbkrImportService service;
  private final List<CashOperationEntity> persistedCashOperations = new ArrayList<>();

  @BeforeEach
  void setUp() {
    AssetCatalogService assetCatalogService = new AssetCatalogService(assetRepository);
    IbkrPositionReconstructionService reconstructionService =
        new IbkrPositionReconstructionService(
            cashOperationRepository, openedPositionRepository, closedPositionRepository);
    service =
        new IbkrImportService(
            cashOperationRepository,
            assetPriceHistoryRepository,
            assetRepository,
            accountRepository,
            assetCatalogService,
            reconstructionService,
            sourceEvidenceService,
            currencyRateService);
    persistedCashOperations.clear();
    org.mockito.Mockito.lenient()
        .doAnswer(
            invocation -> {
              Iterable<CashOperationEntity> rows = invocation.getArgument(0);
              for (CashOperationEntity row : rows) {
                persistedCashOperations.removeIf(existing -> existing.getId().equals(row.getId()));
                persistedCashOperations.add(row);
              }
              return null;
            })
        .when(cashOperationRepository)
        .saveAll(org.mockito.ArgumentMatchers.anyIterable());
    org.mockito.Mockito.lenient()
        .when(cashOperationRepository.findAllByAccount(17959259L))
        .thenAnswer(_ -> new ArrayList<>(persistedCashOperations));
    org.mockito.Mockito.lenient()
        .when(assetRepository.findAllBySymbolIn(org.mockito.ArgumentMatchers.anyCollection()))
        .thenAnswer(
            invocation -> {
              java.util.Collection<String> symbols = invocation.getArgument(0);
              return symbols.stream().map(IbkrImportHistoryServiceTest::asset).toList();
            });
    org.mockito.Mockito.lenient()
        .when(assetRepository.findAllByTickerIn(org.mockito.ArgumentMatchers.anyCollection()))
        .thenReturn(List.of());
    org.mockito.Mockito.lenient()
        .when(assetRepository.findAllByIbkrIgnoreCase(org.mockito.ArgumentMatchers.anyString()))
        .thenAnswer(
            invocation -> {
              String brokerSymbol = invocation.getArgument(0);
              String canonicalSymbol =
                  brokerSymbol.contains(".") ? brokerSymbol : brokerSymbol + ".US";
              return List.of(asset(canonicalSymbol));
            });
    org.mockito.Mockito.lenient()
        .when(
            accountRepository.findByProviderIgnoreCaseAndExternalAccountId(
                org.mockito.ArgumentMatchers.eq("IBKR"), org.mockito.ArgumentMatchers.anyString()))
        .thenAnswer(
            invocation -> {
              AccountEntity account = new AccountEntity();
              String externalAccountId = invocation.getArgument(1);
              account.setId(Long.valueOf(externalAccountId));
              account.setExternalAccountId(externalAccountId);
              account.setProvider("IBKR");
              account.setCurrency(CurrencyType.USD);
              return java.util.Optional.of(account);
            });
  }

  private static AssetEntity asset(String symbol) {
    boolean treasury = "T458022826".equals(symbol) || "T458022826.US".equals(symbol);
    String canonical = treasury ? "US91282CKB62" : symbol;
    String ticker =
        treasury
            ? "T458022826"
            : (symbol.contains(".") ? symbol.substring(0, symbol.indexOf('.')) : symbol);
    return AssetEntity.builder()
        .id((long) Math.abs(canonical.hashCode()) + 1L)
        .name(ticker)
        .symbol(canonical)
        .ticker(ticker)
        .ibrk(ticker)
        .yahoo(canonical)
        .country("US")
        .currency(CurrencyType.USD)
        .assetType(treasury ? "BOND" : "EQUITY")
        .active(true)
        .build();
  }

  @DisplayName("import Statement maps Ibkr Symbol To Canonical Asset Symbol By Ibrk Column")
  @Test
  void importStatement_mapsIbkrSymbolToCanonicalAssetSymbolByIbrkColumn() throws Exception {
    AssetEntity canonical =
        AssetEntity.builder()
            .id(30L)
            .name("Realty Income")
            .symbol("O.US")
            .ticker("O")
            .ibrk("O")
            .yahoo("O.US")
            .country("US")
            .currency(com.smartbox.investory.shared.currency.CurrencyType.USD)
            .assetType("EQUITY")
            .active(true)
            .build();
    when(assetRepository.findAllByIbkrIgnoreCase("O")).thenReturn(List.of(canonical));

    String csv =
        String.join(
            "\n",
            "Transaction History,Header,Transaction"
                + " Type,AccountEntity,Symbol,Description,Date,Quantity,Price,Net Amount,Currency",
            "Transaction History,Data,Buy,U17959259,O,Realty Income buy,2026-07-01,1,50,-50.00,USD");

    service.importStatement(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)), null);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Iterable<CashOperationEntity>> cashCaptor =
        (ArgumentCaptor<Iterable<CashOperationEntity>>)
            (ArgumentCaptor<?>) ArgumentCaptor.forClass(Iterable.class);
    verify(cashOperationRepository).saveAll(cashCaptor.capture());
    List<CashOperationEntity> operations = toList(cashCaptor.getValue());
    assertEquals(1, operations.size());
    assertEquals(CashOperationType.STOCK_PURCHASE, operations.getFirst().getType());
    assertEquals("O.US", operations.getFirst().getSymbol());
    assertTrue(operations.getFirst().getAssetId() > 0);
  }

  @DisplayName("import Statement keeps Asset Marked Excluded From Import")
  @Test
  void importStatement_keepsAssetMarkedExcludedFromImport() throws Exception {
    AssetEntity excluded = asset("AIGI.UK");
    excluded.setExcludeFromImport(true);
    when(assetRepository.findAllByIbkrIgnoreCase("AIGI")).thenReturn(List.of(excluded));
    when(assetRepository.findAllBySymbolIn(org.mockito.ArgumentMatchers.anyCollection()))
        .thenReturn(List.of(excluded));

    String csv =
        String.join(
            "\n",
            "Transaction History,Header,Transaction Type,AccountEntity,Symbol,Description,Date,Quantity,Price,Net Amount,Currency",
            "Transaction History,Data,Buy,U17959259,AIGI,AIGI buy,2026-07-01,1,100,-100.00,USD");

    service.importStatement(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)), null);

    assertEquals(1, persistedCashOperations.size());
    assertEquals(excluded.getId(), persistedCashOperations.getFirst().getAssetId());
  }

  @DisplayName("import Statement preserves Asset Identity For Investment Interest")
  @Test
  void importStatement_preservesAssetIdentityForInvestmentInterest() throws Exception {
    String csv =
        String.join(
            "\n",
            "Transaction History,Header,Transaction Type,AccountEntity,Symbol,Description,Date,Net Amount,Currency",
            "Transaction History,Data,Investment Interest Received,U17959259,AAPL,Security interest,2026-07-01,2.50,USD");

    service.importStatement(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)), null);

    assertEquals(1, persistedCashOperations.size());
    CashOperationEntity operation = persistedCashOperations.getFirst();
    assertEquals(CashOperationType.FREE_FUNDS_INTEREST, operation.getType());
    assertEquals("AAPL.US", operation.getSymbol());
    assertEquals("AAPL", operation.getSourceAssetSymbol());
    assertEquals("AAPL", operation.getBrokerSymbol());
    assertTrue(operation.getAssetId() > 0);
  }

  @DisplayName("import Statement does Not Normalize Bond Etf From Treasury Description")
  @Test
  void importStatement_doesNotNormalizeBondEtfFromTreasuryDescription() throws Exception {
    AssetEntity etf = asset("DTLA.UK");
    etf.setAssetType("ETF");
    when(assetRepository.findAllByIbkrIgnoreCase("DTLA")).thenReturn(List.of(etf));

    String csv =
        String.join(
            "\n",
            "Transaction History,Header,Transaction Type,AccountEntity,Symbol,Description,Date,Quantity,Price,Net Amount,Currency",
            "Transaction History,Data,Buy,U17959259,DTLA,US Treasury Bond ETF,2026-07-01,1000,100,-100000,USD");

    service.importStatement(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)), null);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Iterable<PositionEntity>> positionCaptor =
        (ArgumentCaptor<Iterable<PositionEntity>>)
            (ArgumentCaptor<?>) ArgumentCaptor.forClass(Iterable.class);
    verify(openedPositionRepository).saveAll(positionCaptor.capture());
    List<PositionEntity> positions = toList(positionCaptor.getValue());
    assertEquals(1, positions.size());
    assertEquals(1000.0, positions.getFirst().getVolume().doubleValue(), 0.0001);
    assertEquals(100.0, positions.getFirst().getOpenPrice().doubleValue(), 0.0001);
  }

  @DisplayName("import Statement rejects Stock Sell Without Inventory")
  @Test
  void importStatement_rejectsStockSellWithoutInventory() throws Exception {
    String csv =
        String.join(
            "\n",
            "Transaction History,Header,Transaction"
                + " Type,AccountEntity,Symbol,Description,Date,Quantity,Price,Net Amount,Currency",
            "Transaction History,Data,Sell,U17959259,AAPL,AAPL sell,2026-07-01,1,200,200.00,USD");

    assertThrows(
        IllegalStateException.class,
        () ->
            service.importStatement(
                new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)), null));
  }

  @DisplayName("import Statement reconstructs Open Positions From Transaction Only File")
  @Test
  void importStatement_reconstructsOpenPositionsFromTransactionOnlyFile() throws Exception {
    String csv =
        String.join(
            "\n",
            "Transaction History,Header,Transaction"
                + " Type,AccountEntity,Symbol,Description,Date,Quantity,Price,Net Amount,Currency",
            "Transaction History,Data,Buy,U17959259,AAPL,AAPL buy,2026-07-01,10,100,-1000.00,USD",
            "Transaction History,Data,Sell,U17959259,AAPL,AAPL sell,2026-07-02,4,120,480.00,USD",
            "Transaction History,Data,Buy,U17959259,MSFT,MSFT buy,2026-07-03,2,250,-500.00,USD");

    service.importStatement(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)), null);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Iterable<PositionEntity>> positionCaptor =
        (ArgumentCaptor<Iterable<PositionEntity>>)
            (ArgumentCaptor<?>) ArgumentCaptor.forClass(Iterable.class);
    verify(openedPositionRepository).saveAll(positionCaptor.capture());
    List<PositionEntity> positions = toList(positionCaptor.getValue());

    PositionEntity aapl =
        positions.stream()
            .filter(position -> "AAPL.US".equals(position.getSymbol()))
            .findFirst()
            .orElseThrow();
    assertEquals(6.0, aapl.getVolume().doubleValue(), 0.01);
    assertEquals(600.0, aapl.getPurchaseValue().doubleValue(), 0.01);
    assertEquals(100.0, aapl.getOpenPrice().doubleValue(), 0.01);
    assertTrue(aapl.getComment().contains("canonical cash history"));

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Iterable<PositionEntity>> closedCaptor =
        (ArgumentCaptor<Iterable<PositionEntity>>)
            (ArgumentCaptor<?>) ArgumentCaptor.forClass(Iterable.class);
    verify(closedPositionRepository).saveAll(closedCaptor.capture());
    List<PositionEntity> closed = toList(closedCaptor.getValue());
    assertEquals(1, closed.size());
    assertEquals("AAPL.US", closed.getFirst().getSymbol());
    assertEquals(4.0, closed.getFirst().getVolume().doubleValue(), 0.01);
    assertEquals(400.0, closed.getFirst().getPurchaseValue().doubleValue(), 0.01);
    assertEquals(480.0, closed.getFirst().getSaleValue().doubleValue(), 0.01);
    assertEquals(80.0, closed.getFirst().getProfit().doubleValue(), 0.01);
  }

  @DisplayName("import Statement sets Explicit Currencies For Open Position Snapshot")
  @Test
  void importStatement_setsExplicitCurrenciesForOpenPositionSnapshot() throws Exception {
    String csv =
        String.join(
            "\n",
            "Transaction History,Header,Transaction Type,AccountEntity,Symbol,Description,Date,Quantity,Price,Net Amount,Currency",
            "Transaction History,Data,Buy,U17959259,AAPL,AAPL buy,2026-07-01,2,100,-200.00,EUR",
            "Open Positions,Header,Symbol,Quantity,Currency,Cost Price,Cost Basis,Close Price,Unrealized P/L,DataDiscriminator",
            "Open Positions,Data,AAPL,2,EUR,100,200,110,20,Summary");

    service.importStatement(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)), null);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Iterable<PositionEntity>> positionCaptor =
        (ArgumentCaptor<Iterable<PositionEntity>>)
            (ArgumentCaptor<?>) ArgumentCaptor.forClass(Iterable.class);
    verify(openedPositionRepository).saveAll(positionCaptor.capture());
    PositionEntity position = toList(positionCaptor.getValue()).getFirst();

    assertEquals(CurrencyType.EUR, position.getPriceCurrency());
    assertEquals(CurrencyType.EUR, position.getCostCurrency());
    assertEquals(CurrencyType.EUR, position.getProfitCurrency());
    assertEquals(CurrencyType.EUR, position.getCommissionCurrency());
  }

  @DisplayName("import Statement does Not Inflate Applied Rows With Open Position Snapshot")
  @Test
  void importStatement_doesNotInflateAppliedRowsWithOpenPositionSnapshot() throws Exception {
    String csv =
        String.join(
            "\n",
            "Transaction History,Header,Transaction Type,AccountEntity,Symbol,Description,Date,Quantity,Price,Price Currency,Net Amount,Currency",
            "Transaction History,Data,Buy,U17959259,AAPL,AAPL buy,2026-07-01,2,100,USD,-200.00,USD",
            "Open Positions,Header,Symbol,Quantity,Currency,Cost Price,Cost Basis,Close Price,Unrealized P/L,DataDiscriminator",
            "Open Positions,Data,AAPL,2,USD,100,200,110,20,Summary");

    ImportExecutionResult result =
        service.importStatement(
            new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)), null);

    assertEquals(1, result.rowsTotal());
    assertEquals(1, result.rowsApplied());
    assertEquals(0, result.rowsFailed());
    assertTrue(result.details().contains("1 open positions"));
  }

  @DisplayName("import Statement reconstructs Open Positions From Ibkr Alias Columns")
  @Test
  void importStatement_reconstructsOpenPositionsFromIbkrAliasColumns() throws Exception {
    String csv =
        String.join(
            "\n",
            "Transaction History,Header,Type,AccountEntity ID,Symbol,Description,Date/Time,Qty,T."
                + " Price,Net Cash,Currency",
            "Transaction History,Data,Buy,U17959259,VWRA,VANG FTSE AW USDA,2026-01-07"
                + " 23:00:00,10,103.654,-1036.54,USD");

    service.importStatement(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)), null);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Iterable<PositionEntity>> positionCaptor =
        (ArgumentCaptor<Iterable<PositionEntity>>)
            (ArgumentCaptor<?>) ArgumentCaptor.forClass(Iterable.class);
    verify(openedPositionRepository).saveAll(positionCaptor.capture());
    List<PositionEntity> positions = toList(positionCaptor.getValue());

    assertEquals(1, positions.size());
    PositionEntity vwra = positions.getFirst();
    assertEquals(17959259L, vwra.getAccount());
    assertEquals("VWRA.US", vwra.getSymbol());
    assertEquals(10.0, vwra.getVolume().doubleValue(), 0.01);
    assertEquals(1036.54, vwra.getPurchaseValue().doubleValue(), 0.01);
    assertEquals(103.654, vwra.getOpenPrice().doubleValue(), 0.01);
  }

  @DisplayName("import Statement uses Cash Currency Column When Present")
  @Test
  void importStatement_usesCashCurrencyColumnWhenPresent() throws Exception {
    String csv =
        String.join(
            "\n",
            "Transaction History,Header,Date,AccountEntity,Description,Transaction"
                + " Type,Currency,Symbol,Quantity,Price,Net Amount",
            "Transaction History,Data,2026-06-03,U17959259,EUR cash deposit,Deposit,EUR,,,"
                + ",100.00");

    service.importStatement(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)), null);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Iterable<CashOperationEntity>> cashCaptor =
        (ArgumentCaptor<Iterable<CashOperationEntity>>)
            (ArgumentCaptor<?>) ArgumentCaptor.forClass(Iterable.class);
    verify(cashOperationRepository).saveAll(cashCaptor.capture());
    List<CashOperationEntity> operations = toList(cashCaptor.getValue());

    assertEquals(1, operations.size());
    assertEquals(CurrencyType.EUR, operations.getFirst().getCurrency());
    assertEquals(100.0, operations.getFirst().getAmount().doubleValue(), 0.01);
  }

  @DisplayName("import Statement rejects Unknown Asset Instead Of Bootstrapping")
  @Test
  void importStatement_rejectsUnknownAssetInsteadOfBootstrapping() throws Exception {
    when(assetRepository.findAllByIbkrIgnoreCase("NVDA")).thenReturn(List.of());

    String csv =
        String.join(
            "\n",
            "Transaction History,Header,Transaction Type,AccountEntity,Symbol,Description,Date,Quantity,Price,Net Amount,Currency",
            "Transaction History,Data,Buy,U17959259,NVDA,Nvidia buy,2026-07-01,1,100,-100.00,USD");

    ImportExecutionResult result =
        service.importStatement(
            new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)), null);

    assertEquals(1, result.rowsTotal());
    assertEquals(0, result.rowsApplied());
    assertEquals(1, result.rowsFailed());
    assertTrue(persistedCashOperations.isEmpty());
  }

  @DisplayName(
      "import Statement uses Statement Base Currency For Cash When Only Price Currency Is Present")
  @Test
  void importStatement_usesStatementBaseCurrencyForCashWhenOnlyPriceCurrencyIsPresent()
      throws Exception {
    String csv =
        String.join(
            "\n",
            "Transaction History,Header,Date,AccountEntity,Description,Transaction"
                + " Type,Symbol,Quantity,Price,Price Currency,Net Amount",
            "Summary,Data,Base Currency,USD",
            "Transaction History,Data,2026-06-03,U17959259,EUR-priced ETF buy,Buy,JGPI,1,22.54,"
                + "EUR,-26.45");

    ImportExecutionResult result =
        service.importStatement(
            new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)), null);

    assertEquals(1, result.rowsTotal());
    assertEquals(0, result.rowsFailed());

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Iterable<CashOperationEntity>> cashCaptor =
        (ArgumentCaptor<Iterable<CashOperationEntity>>)
            (ArgumentCaptor<?>) ArgumentCaptor.forClass(Iterable.class);
    verify(cashOperationRepository).saveAll(cashCaptor.capture());
    List<CashOperationEntity> operations = toList(cashCaptor.getValue());

    assertEquals(1, operations.size());
    assertEquals(CashOperationType.STOCK_PURCHASE, operations.getFirst().getType());
    assertEquals(CurrencyType.USD, operations.getFirst().getCurrency());
    assertEquals(-26.45, operations.getFirst().getAmount().doubleValue(), 0.0001);
  }

  @DisplayName("import Statement maps Dividend Without Row Currency To Statement Base Currency")
  @Test
  void importStatement_mapsDividendWithoutRowCurrencyToStatementBaseCurrency() throws Exception {
    AssetEntity eurListed =
        AssetEntity.builder()
            .id(77L)
            .name("Xtrackers MSCI World")
            .symbol("XDWL.DE")
            .ticker("XDWL")
            .ibrk("XDWL")
            .yahoo("XDWL.DE")
            .country("DE")
            .currency(CurrencyType.EUR)
            .assetType("ETF")
            .active(true)
            .build();
    when(assetRepository.findAllByIbkrIgnoreCase("XDWL")).thenReturn(List.of(eurListed));
    when(assetRepository.findAllBySymbolIn(Set.of("XDWL.DE"))).thenReturn(List.of(eurListed));

    String csv =
        String.join(
            "\n",
            "Transaction History,Header,Date,AccountEntity,Description,Transaction"
                + " Type,Symbol,Quantity,Price,Price Currency,Net Amount",
            "Summary,Data,Base Currency,USD",
            "Transaction History,Data,2026-06-03,U17959259,XDWL cash dividend,Dividend,XDWL,-,-,-,3.99");

    ImportExecutionResult result =
        service.importStatement(
            new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)), null);

    assertEquals(1, result.rowsTotal());
    assertEquals(0, result.rowsFailed());

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Iterable<CashOperationEntity>> cashCaptor =
        (ArgumentCaptor<Iterable<CashOperationEntity>>)
            (ArgumentCaptor<?>) ArgumentCaptor.forClass(Iterable.class);
    verify(cashOperationRepository).saveAll(cashCaptor.capture());
    List<CashOperationEntity> operations = toList(cashCaptor.getValue());

    assertEquals(1, operations.size());
    assertEquals(CashOperationType.DIVIDEND, operations.getFirst().getType());
    assertEquals(CurrencyType.USD, operations.getFirst().getCurrency());
    assertEquals(3.99, operations.getFirst().getAmount().doubleValue(), 0.0001);
  }

  @DisplayName("import Statement maps Us Dividend Without Row Currency To Usd")
  @Test
  void importStatement_mapsUsDividendWithoutRowCurrencyToUsd() throws Exception {
    String csv =
        String.join(
            "\n",
            "Transaction History,Header,Date,AccountEntity,Description,Transaction"
                + " Type,Symbol,Quantity,Price,Price Currency,Net Amount",
            "Summary,Data,Base Currency,USD",
            "Transaction History,Data,2026-06-03,U17959259,O cash dividend,Dividend,O,-,-,-,14.28");

    ImportExecutionResult result =
        service.importStatement(
            new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)), null);

    assertEquals(1, result.rowsTotal());
    assertEquals(0, result.rowsFailed());

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Iterable<CashOperationEntity>> cashCaptor =
        (ArgumentCaptor<Iterable<CashOperationEntity>>)
            (ArgumentCaptor<?>) ArgumentCaptor.forClass(Iterable.class);
    verify(cashOperationRepository).saveAll(cashCaptor.capture());
    List<CashOperationEntity> operations = toList(cashCaptor.getValue());

    assertEquals(1, operations.size());
    assertEquals(CashOperationType.DIVIDEND, operations.getFirst().getType());
    assertEquals(CurrencyType.USD, operations.getFirst().getCurrency());
    assertEquals(14.28, operations.getFirst().getAmount().doubleValue(), 0.0001);
  }

  @DisplayName("import Statement falls Back To Statement Base Currency For Symbol Less Row")
  @Test
  void importStatement_fallsBackToStatementBaseCurrencyForSymbolLessRow() throws Exception {
    String csv =
        String.join(
            "\n",
            "Summary,Header,Field Name,Field Value",
            "Summary,Data,Base Currency,USD",
            "Transaction History,Header,Date,AccountEntity,Description,Transaction"
                + " Type,Symbol,Quantity,Price,Price Currency,Net Amount",
            "Transaction History,Data,2026-06-04,U17959259,USD Credit Interest,Credit"
                + " Interest,-,-,-,-,0.10");

    ImportExecutionResult result =
        service.importStatement(
            new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)), null);

    assertEquals(1, result.rowsTotal());
    assertEquals(0, result.rowsFailed());

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Iterable<CashOperationEntity>> cashCaptor =
        (ArgumentCaptor<Iterable<CashOperationEntity>>)
            (ArgumentCaptor<?>) ArgumentCaptor.forClass(Iterable.class);
    verify(cashOperationRepository).saveAll(cashCaptor.capture());
    List<CashOperationEntity> operations = toList(cashCaptor.getValue());

    assertEquals(1, operations.size());
    assertEquals(CashOperationType.FREE_FUNDS_INTEREST, operations.getFirst().getType());
    assertEquals(CurrencyType.USD, operations.getFirst().getCurrency());
    assertEquals(0.10, operations.getFirst().getAmount().doubleValue(), 0.0001);
  }

  @DisplayName(
      "import Statement does Not Use Configured Account Currency Without Statement Currency")
  @Test
  void importStatement_doesNotUseConfiguredAccountCurrencyWithoutStatementCurrency()
      throws Exception {
    String csv =
        String.join(
            "\n",
            "Transaction History,Header,Date,AccountEntity,Description,Transaction Type,Symbol,Quantity,Price,Price Currency,Net Amount",
            "Transaction History,Data,2026-06-04,U17959259,Interest,Credit Interest,-,-,-,0.10");

    ImportExecutionResult result =
        service.importStatement(
            new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)), null);

    assertEquals(1, result.rowsTotal());
    assertEquals(0, result.rowsApplied());
    assertEquals(1, result.rowsFailed());
    assertTrue(persistedCashOperations.isEmpty());
  }

  @DisplayName("import Statement rejects Unknown Broker Symbol")
  @Test
  void importStatement_rejectsUnknownBrokerSymbol() {
    when(assetRepository.findAllBySymbolIn(Set.of("UNKNOWN.US"))).thenReturn(List.of());
    String csv =
        String.join(
            "\n",
            "Transaction History,Header,Transaction Type,AccountEntity,Symbol,Description,Date,Quantity,Price,Net Amount,Currency",
            "Transaction History,Data,Buy,U17959259,UNKNOWN,Unknown asset,2026-07-01,1,10,-10.00,USD");

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                service.importStatement(
                    new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)), null));

    assertTrue(exception.getMessage().contains("Unknown asset mapping"));
  }

  @DisplayName("import Statement writes Exact Ibkr Trade Price To Asset Price History")
  @Test
  void importStatement_writesExactIbkrTradePriceToAssetPriceHistory() throws Exception {
    AssetEntity asset =
        AssetEntity.builder()
            .id(41L)
            .name("iShares Edge MSCI USA Value")
            .symbol("IUVL.UK")
            .ticker("IUVL")
            .ibrk("IUVL")
            .yahoo("IUVL.L")
            .country("UK")
            .currency(CurrencyType.USD)
            .assetType("ETF")
            .active(true)
            .build();
    when(assetRepository.findAllByIbkrIgnoreCase("IUVL")).thenReturn(List.of(asset));
    when(assetRepository.findAllBySymbolIn(Set.of("IUVL.UK"))).thenReturn(List.of(asset));

    String csv =
        String.join(
            "\n",
            "Transaction History,Header,Date,AccountEntity,Description,Transaction Type,Symbol,"
                + "Quantity,Price,Price Currency,Net Amount,Currency",
            "Transaction History,Data,2026-06-10,U17959259,ISHARES EDGE MSCI USA VALUE,Buy,"
                + "IUVL,100,18.3577,USD,-1835.77,USD");

    service.importStatement(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)), null);

    verify(assetPriceHistoryRepository)
        .upsertIbkrTradeObservation(
            41L, LocalDate.of(2026, 6, 10), "IUVL.UK", "IUVL", "USD", BigDecimal.valueOf(18.3577));
  }

  @DisplayName("import Statement maps Corporate Action And Adjustment To Concrete Cash Types")
  @Test
  void importStatement_mapsCorporateActionAndAdjustmentToConcreteCashTypes() throws Exception {
    String csv =
        String.join(
            "\n",
            "Transaction History,Header,Transaction"
                + " Type,AccountEntity,Symbol,Description,Date,Quantity,Price,Net Amount,Currency",
            "Transaction History,Data,Corporate Action,U17959259,TLT,Bond redemption,2026-07-01,,,100.00,USD",
            "Transaction History,Data,Adjustment,U17959259,,Manual correction,2026-07-02,,,-5.00,USD");

    ImportExecutionResult result =
        service.importStatement(
            new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)), null);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Iterable<CashOperationEntity>> captor =
        (ArgumentCaptor<Iterable<CashOperationEntity>>)
            (ArgumentCaptor<?>) ArgumentCaptor.forClass(Iterable.class);
    verify(cashOperationRepository).saveAll(captor.capture());
    List<CashOperationEntity> operations = toList(captor.getValue());

    assertEquals(2, operations.size());
    assertEquals(CashOperationType.TRANSFER, operations.get(0).getType());
    assertEquals(CashOperationType.CORRECTION, operations.get(1).getType());
    assertEquals(2, result.rowsTotal());
    assertEquals(2, result.rowsApplied());
    assertEquals(0, result.rowsFailed());
  }

  @DisplayName(
      "import Statement maps Ibkr Cash Transfer Deposit To Transfer Instead Of External Deposit")
  @Test
  void importStatement_mapsIbkrCashTransferDepositToTransferInsteadOfExternalDeposit()
      throws Exception {
    String csv =
        String.join(
            "\n",
            "Transaction History,Header,Date,AccountEntity,Description,Transaction"
                + " Type,Currency,Symbol,Quantity,Price,Net Amount",
            "Transaction History,Data,2025-02-13,U17959259,Cash Transfer,Deposit,USD,,,,7838.285");

    service.importStatement(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)), null);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Iterable<CashOperationEntity>> captor =
        (ArgumentCaptor<Iterable<CashOperationEntity>>)
            (ArgumentCaptor<?>) ArgumentCaptor.forClass(Iterable.class);
    verify(cashOperationRepository).saveAll(captor.capture());
    List<CashOperationEntity> operations = toList(captor.getValue());

    assertEquals(1, operations.size());
    assertEquals(CashOperationType.TRANSFER, operations.getFirst().getType());
    assertNull(operations.getFirst().getSymbol());
  }

  @DisplayName("import Statement preserves Forex Metadata Without Using Pseudo Symbol As Asset Id")
  @Test
  void importStatement_preservesForexMetadataWithoutUsingPseudoSymbolAsAssetId() throws Exception {
    String csv =
        String.join(
            "\n",
            "Transaction History,Header,Date,AccountEntity,Description,Transaction"
                + " Type,Symbol,Quantity,Price,Net Amount,Currency",
            "Transaction History,Data,2026-05-08,U17959259,Net Amount in Base from Forex Trade:"
                + " -3.32 EUR.USD,Forex Trade Component,EUR.USD,-3.32,1.17382,-0.0158696,USD");

    service.importStatement(
        new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)),
        "U17959259.TRANSACTIONS.20250211.20260612.csv");

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Iterable<CashOperationEntity>> cashCaptor =
        (ArgumentCaptor<Iterable<CashOperationEntity>>)
            (ArgumentCaptor<?>) ArgumentCaptor.forClass(Iterable.class);
    verify(cashOperationRepository).saveAll(cashCaptor.capture());
    List<CashOperationEntity> operations = toList(cashCaptor.getValue());
    assertEquals(1, operations.size());
    assertEquals(CashOperationType.TRANSFER, operations.getFirst().getType());
    assertNull(operations.getFirst().getSymbol());
    assertTrue(operations.getFirst().getComment().contains("ibkrRawType=Forex Trade Component"));
    assertTrue(operations.getFirst().getComment().contains("ibkrRawSymbol=EUR.USD"));
    assertTrue(operations.getFirst().getComment().contains("ibkrQuantity=-3.32"));
    assertTrue(operations.getFirst().getComment().contains("ibkrPrice=1.17382"));
    assertEquals(17959259L, operations.getFirst().getAccount());
  }

  @DisplayName("import Statement compacts Long Bond Symbol Before Asset Insert")
  @Test
  void importStatement_compactsLongBondSymbolBeforeAssetInsert() throws Exception {
    String csv =
        String.join(
            "\n",
            "Transaction History,Header,Date,AccountEntity,Description,Transaction"
                + " Type,Symbol,Quantity,Price,Net Amount,Currency",
            "Transaction History,Data,2025-05-06,U17959259,T 4 5/8 02/28/26,Buy,T 4 5/8"
                + " 02/28/26,8000.0,100.42611625,-8039.09,USD");

    service.importStatement(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)), null);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Iterable<PositionEntity>> positionCaptor =
        (ArgumentCaptor<Iterable<PositionEntity>>)
            (ArgumentCaptor<?>) ArgumentCaptor.forClass(Iterable.class);
    verify(openedPositionRepository).saveAll(positionCaptor.capture());
    List<PositionEntity> positions = toList(positionCaptor.getValue());
    assertEquals(1, positions.size());
    assertEquals("US91282CKB62", positions.getFirst().getSymbol());
    assertEquals(8000.0, positions.getFirst().getVolume().doubleValue(), 0.01);
    assertEquals(8039.09, positions.getFirst().getPurchaseValue().doubleValue(), 0.01);
    assertEquals(1.00488625, positions.getFirst().getOpenPrice().doubleValue(), 0.00000001);
    assertEquals("T 4 5/8 02/28/26", positions.getFirst().getSourceAssetSymbol());
    assertTrue(positions.getFirst().getAssetId() > 0);
  }

  @DisplayName("import Statement removes Bond Closed By Final Call From Reconstructed Positions")
  @Test
  void importStatement_removesBondClosedByFinalCallFromReconstructedPositions() throws Exception {
    String csv =
        String.join(
            "\n",
            "Transaction History,Header,Date,AccountEntity,Description,Transaction"
                + " Type,Symbol,Quantity,Price,Price Currency,Gross Amount ,Commission,Net Amount,Currency",
            "Transaction History,Data,2026-02-27,U17959259,\"(US91282CKB62) Full Call / Early"
                + " Redemption for USD 1.00 per Bond (T 4 5/8 02/28/26, T 4 5/8 02/28/26,"
                + " US91282CKB62)\",Corporate Action,T 4 5/8 02/28/26,-,-,-,10000.0,-,10000.0,USD",
            "Transaction History,Data,2026-02-11,U17959259,VANG FTSE AW USDA,Buy,VWRA,"
                + "2.0,177.0,USD,-354.0,-4.0,-358.0,USD",
            "Transaction History,Data,2025-05-06,U17959259,T 4 5/8 02/28/26,Buy,T 4 5/8"
                + " 02/28/26,8000.0,100.42611625,USD,-8034.09,-5.0,-8039.09,USD",
            "Transaction History,Data,2025-04-17,U17959259,T 4 5/8 02/28/26,Buy,T 4 5/8"
                + " 02/28/26,1000.0,100.484375,USD,-1004.84,-5.0,-1009.84,USD",
            "Transaction History,Data,2025-03-26,U17959259,T 4 5/8 02/28/26,Buy,T 4 5/8"
                + " 02/28/26,1000.0,100.41049125,USD,-1004.1,-5.0,-1009.1,USD");

    service.importStatement(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)), null);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Iterable<PositionEntity>> positionCaptor =
        (ArgumentCaptor<Iterable<PositionEntity>>)
            (ArgumentCaptor<?>) ArgumentCaptor.forClass(Iterable.class);
    verify(openedPositionRepository).saveAll(positionCaptor.capture());
    List<PositionEntity> positions = toList(positionCaptor.getValue());

    assertEquals(2.0, positions.stream().mapToDouble(p -> p.getVolume().doubleValue()).sum(), 0.01);
    assertTrue(positions.stream().allMatch(position -> "VWRA.US".equals(position.getSymbol())));
    assertFalse(
        positions.stream().anyMatch(position -> "US91282CKB62".equals(position.getSymbol())));

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Iterable<PositionEntity>> closedCaptor =
        (ArgumentCaptor<Iterable<PositionEntity>>)
            (ArgumentCaptor<?>) ArgumentCaptor.forClass(Iterable.class);
    verify(closedPositionRepository).saveAll(closedCaptor.capture());
    List<PositionEntity> closed = toList(closedCaptor.getValue());
    assertEquals(3, closed.size());
    assertTrue(closed.stream().allMatch(position -> "US91282CKB62".equals(position.getSymbol())));
    assertEquals(
        10000.0, closed.stream().mapToDouble(p -> p.getVolume().doubleValue()).sum(), 0.01);
    assertEquals(
        10000.0, closed.stream().mapToDouble(p -> p.getSaleValue().doubleValue()).sum(), 0.01);
    assertEquals(
        Set.of(LocalDate.of(2025, 3, 26), LocalDate.of(2025, 4, 17), LocalDate.of(2025, 5, 6)),
        closed.stream()
            .map(position -> position.getOpenTime().toLocalDate())
            .collect(Collectors.toSet()));
  }

  @DisplayName("import Statement deletes Ibkr Positions When Transaction Reconstruction Ends Empty")
  @Test
  void importStatement_deletesIbkrPositionsWhenTransactionReconstructionEndsEmpty()
      throws Exception {
    String csv =
        String.join(
            "\n",
            "Transaction History,Header,Date,AccountEntity,Description,Transaction"
                + " Type,Symbol,Quantity,Price,Price Currency,Gross Amount ,Commission,Net Amount,Currency",
            "Transaction History,Data,2026-02-27,U17959259,\"(US91282CKB62) Full Call / Early"
                + " Redemption for USD 1.00 per Bond (T 4 5/8 02/28/26, T 4 5/8 02/28/26,"
                + " US91282CKB62)\",Corporate Action,T 4 5/8 02/28/26,-,-,-,10000.0,-,10000.0,USD",
            "Transaction History,Data,2025-05-06,U17959259,T 4 5/8 02/28/26,Buy,T 4 5/8"
                + " 02/28/26,10000.0,100.42611625,USD,-10042.61,-5.0,-10047.61,USD");

    service.importStatement(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)), null);

    verify(openedPositionRepository).deleteOpenByAccount(17959259L);
  }

  @DisplayName("import Statement uses Account Id From Filename Before Alias Column")
  @Test
  void importStatement_usesAccountIdFromFilenameBeforeAliasColumn() throws Exception {
    String csv =
        String.join(
            "\n",
            "Transaction History,Header,Date,AccountEntity,Description,Transaction Type,Currency,Net Amount",
            "Transaction History,Data,2026-06-03,U17959259,USD deposit,Deposit,USD,100.00");

    service.importStatement(
        new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)),
        "U17959259.TRANSACTIONS.20250211.20260612.csv");

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Iterable<CashOperationEntity>> cashCaptor =
        (ArgumentCaptor<Iterable<CashOperationEntity>>)
            (ArgumentCaptor<?>) ArgumentCaptor.forClass(Iterable.class);
    verify(cashOperationRepository).saveAll(cashCaptor.capture());
    List<CashOperationEntity> operations = toList(cashCaptor.getValue());
    assertEquals(17959259L, operations.getFirst().getAccount());
  }

  @DisplayName("import Statement keeps Alias Fallback When Filename Has No Account")
  @Test
  void importStatement_keepsAliasFallbackWhenFilenameHasNoAccount() throws Exception {
    String csv =
        String.join(
            "\n",
            "Transaction History,Header,Date,AccountEntity ID,Description,Transaction Type,Currency,Net Amount",
            "Transaction History,Data,2026-06-03,U18000001,USD deposit,Deposit,USD,100.00");

    service.importStatement(
        new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)),
        "statement-without-account.csv");

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Iterable<CashOperationEntity>> cashCaptor =
        (ArgumentCaptor<Iterable<CashOperationEntity>>)
            (ArgumentCaptor<?>) ArgumentCaptor.forClass(Iterable.class);
    verify(cashOperationRepository).saveAll(cashCaptor.capture());
    List<CashOperationEntity> operations = toList(cashCaptor.getValue());
    assertEquals(18000001L, operations.getFirst().getAccount());
  }

  @DisplayName(
      "import Statement preserves Forex Trade Component Metadata Without Using Pseudo Asset Id")
  @Test
  void importStatement_preservesForexTradeComponentMetadataWithoutUsingPseudoAssetId()
      throws Exception {
    String csv =
        String.join(
            "\n",
            "Transaction History,Header,Date,AccountEntity,Description,Transaction Type,Symbol,Quantity,Price,Currency,Gross Amount,Commission,Net Amount",
            "Transaction History,Data,2026-06-03,U17959259,Forex Trade Component for EUR.USD,Forex Trade Component,EUR.USD,1000,1.12,USD,1120.00,0.00,1120.00");

    service.importStatement(
        new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)),
        "U17959259.TRANSACTIONS.20260211.20260612.csv");

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Iterable<CashOperationEntity>> cashCaptor =
        (ArgumentCaptor<Iterable<CashOperationEntity>>)
            (ArgumentCaptor<?>) ArgumentCaptor.forClass(Iterable.class);
    verify(cashOperationRepository).saveAll(cashCaptor.capture());
    List<CashOperationEntity> operations = toList(cashCaptor.getValue());
    assertEquals(1, operations.size());
    CashOperationEntity operation = operations.getFirst();
    assertEquals(CashOperationType.TRANSFER, operation.getType());
    assertNull(operation.getSymbol());
    assertTrue(operation.getComment().contains("ibkrRawType=Forex Trade Component"));
    assertTrue(operation.getComment().contains("ibkrRawSymbol=EUR.USD"));
    assertTrue(operation.getComment().contains("ibkrQuantity=1000.0"));
    assertTrue(operation.getComment().contains("ibkrPrice=1.12"));
  }

  @DisplayName(
      "import Statement stores Commission Explicitly On Closed Position Without Changing Net Cash")
  @Test
  void importStatement_storesCommissionExplicitlyOnPositionEntityWithoutChangingNetCash()
      throws Exception {
    String csv =
        String.join(
            "\n",
            "Transaction History,Header,Date,AccountEntity,Description,Transaction"
                + " Type,Symbol,Quantity,Price,Price Currency,Gross Amount ,Commission,Net Amount,Currency",
            "Transaction History,Data,2026-06-01,U17959259,AAPL buy,Buy,AAPL,10,100,USD,-1000.0,-5.0,-1005.0,USD",
            "Transaction History,Data,2026-06-02,U17959259,AAPL sell,Sell,AAPL,10,110,USD,1100.0,-7.0,1093.0,USD");

    service.importStatement(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)), null);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Iterable<PositionEntity>> closedCaptor =
        (ArgumentCaptor<Iterable<PositionEntity>>)
            (ArgumentCaptor<?>) ArgumentCaptor.forClass(Iterable.class);
    verify(closedPositionRepository).saveAll(closedCaptor.capture());
    List<PositionEntity> closed = toList(closedCaptor.getValue());
    assertEquals(1, closed.size());
    assertEquals(-12.0, closed.getFirst().getCommission().doubleValue(), 0.0001);
    assertEquals(1100.0, closed.getFirst().getSaleValue().doubleValue(), 0.0001);
    assertEquals(100.0, closed.getFirst().getProfit().doubleValue(), 0.0001);
  }

  @DisplayName("import Statement rebuilds Positions Across Overlapping Incremental Imports")
  @Test
  void importStatement_rebuildsPositionsAcrossOverlappingIncrementalImports() throws Exception {
    String fileA =
        String.join(
            "\n",
            "Transaction History,Header,Date,AccountEntity,Description,Transaction Type,Symbol,Quantity,Price,Net Amount,Currency",
            "Transaction History,Data,2026-02-11,U17959259,JGPI buy,Buy,JGPI,404,10,-4040.00,USD",
            "Transaction History,Data,2026-02-12,U17959259,VWRA buy,Buy,VWRA,82,100,-8200.00,USD",
            "Transaction History,Data,2026-02-13,U17959259,IUVL buy,Buy,IUVL,610,20,-12200.00,USD");
    String fileB =
        String.join(
            "\n",
            "Transaction History,Header,Date,AccountEntity,Description,Transaction Type,Symbol,Quantity,Price,Net Amount,Currency",
            "Transaction History,Data,2026-06-08,U17959259,VWRA buy,Buy,VWRA,16,101,-1616.00,USD",
            "Transaction History,Data,2026-06-09,U17959259,VWRA buy,Buy,VWRA,16,102,-1632.00,USD",
            "Transaction History,Data,2026-06-10,U17959259,VWRA buy,Buy,VWRA,32,103,-3296.00,USD",
            "Transaction History,Data,2026-06-11,U17959259,IUVL sell,Sell,IUVL,610,21,12810.00,USD");

    service.importStatement(
        new ByteArrayInputStream(fileA.getBytes(StandardCharsets.UTF_8)), "A.csv");
    service.importStatement(
        new ByteArrayInputStream(fileB.getBytes(StandardCharsets.UTF_8)), "B.csv");

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Iterable<PositionEntity>> openCaptor =
        (ArgumentCaptor<Iterable<PositionEntity>>)
            (ArgumentCaptor<?>) ArgumentCaptor.forClass(Iterable.class);
    verify(openedPositionRepository, atLeastOnce()).saveAll(openCaptor.capture());
    List<PositionEntity> latest = toList(openCaptor.getAllValues().getLast());

    assertEquals(404.0, volumeFor(latest, "JGPI.US"), 0.0001);
    assertEquals(146.0, volumeFor(latest, "VWRA.US"), 0.0001);
    assertEquals(0.0, volumeFor(latest, "IUVL.UK"), 0.0001);
  }

  @DisplayName("import Statement rejects Sell Without Historical Inventory")
  @Test
  void importStatement_rejectsSellWithoutHistoricalInventory() throws Exception {
    String fileB =
        String.join(
            "\n",
            "Transaction History,Header,Date,AccountEntity,Description,Transaction Type,Symbol,Quantity,Price,Net Amount,Currency",
            "Transaction History,Data,2026-06-08,U17959259,VWRA buy,Buy,VWRA,16,101,-1616.00,USD",
            "Transaction History,Data,2026-06-09,U17959259,VWRA buy,Buy,VWRA,16,102,-1632.00,USD",
            "Transaction History,Data,2026-06-10,U17959259,VWRA buy,Buy,VWRA,32,103,-3296.00,USD",
            "Transaction History,Data,2026-06-11,U17959259,IUVL sell,Sell,IUVL,610,21,12810.00,USD");

    assertThrows(
        IllegalStateException.class,
        () ->
            service.importStatement(
                new ByteArrayInputStream(fileB.getBytes(StandardCharsets.UTF_8)), "B.csv"));
  }

  @DisplayName(
      "import Statement ignores Stale Snapshot Rows When Canonical History Shows Symbol Closed")
  @Test
  void importStatement_ignoresStaleSnapshotRowsWhenCanonicalHistoryShowsSymbolClosed()
      throws Exception {
    String fileA =
        String.join(
            "\n",
            "Transaction History,Header,Date,AccountEntity,Description,Transaction Type,Symbol,Quantity,Price,Net Amount,Currency",
            "Transaction History,Data,2026-01-02,U17959259,AAPL buy,Buy,AAPL,4,100,-400.00,USD",
            "Transaction History,Data,2026-01-03,U17959259,JGPI buy,Buy,JGPI,10,20,-200.00,USD");
    String fileB =
        String.join(
            "\n",
            "Transaction History,Header,Date,AccountEntity,Description,Transaction Type,Symbol,Quantity,Price,Net Amount,Currency",
            "Transaction History,Data,2026-01-05,U17959259,AAPL sell,Sell,AAPL,-4,110,440.00,USD",
            "Open Positions,Header,Symbol,Quantity,Currency,Cost Price,Cost Basis,Close Price,Unrealized P/L,DataDiscriminator",
            "Open Positions,Data,AAPL,4,USD,100,400,110,40,Summary",
            "Open Positions,Data,JGPI,10,USD,20,200,21,10,Summary");

    service.importStatement(
        new ByteArrayInputStream(fileA.getBytes(StandardCharsets.UTF_8)), "A.csv");
    service.importStatement(
        new ByteArrayInputStream(fileB.getBytes(StandardCharsets.UTF_8)), "B.csv");

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Iterable<PositionEntity>> openCaptor =
        (ArgumentCaptor<Iterable<PositionEntity>>)
            (ArgumentCaptor<?>) ArgumentCaptor.forClass(Iterable.class);
    verify(openedPositionRepository, atLeastOnce()).saveAll(openCaptor.capture());
    List<PositionEntity> latest = toList(openCaptor.getAllValues().getLast());

    assertEquals(0.0, volumeFor(latest, "AAPL.US"), 0.0001);
    assertEquals(10.0, volumeFor(latest, "JGPI.US"), 0.0001);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Iterable<PositionEntity>> closedCaptor =
        (ArgumentCaptor<Iterable<PositionEntity>>)
            (ArgumentCaptor<?>) ArgumentCaptor.forClass(Iterable.class);
    verify(closedPositionRepository, atLeastOnce()).saveAll(closedCaptor.capture());
    List<PositionEntity> closed = toList(closedCaptor.getAllValues().getLast());

    assertEquals(
        1, closed.stream().filter(position -> "AAPL.US".equals(position.getSymbol())).count());
    assertEquals(
        4.0,
        closed.stream()
            .filter(position -> "AAPL.US".equals(position.getSymbol()))
            .mapToDouble(p -> p.getVolume().doubleValue())
            .sum(),
        0.0001);
  }

  private static double volumeFor(List<PositionEntity> positions, String symbol) {
    return positions.stream()
        .filter(position -> symbol.equals(position.getSymbol()))
        .mapToDouble(p -> p.getVolume().doubleValue())
        .sum();
  }

  private static <T> List<T> toList(Iterable<T> iterable) {
    java.util.ArrayList<T> list = new java.util.ArrayList<>();
    iterable.forEach(list::add);
    return list;
  }
}
