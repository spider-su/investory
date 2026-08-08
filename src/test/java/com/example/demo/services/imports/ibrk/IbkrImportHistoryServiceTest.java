package com.example.demo.services.imports.ibrk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.demo.infrastructure.CashOperationType;
import com.example.demo.infrastructure.CurrencyType;
import com.example.demo.infrastructure.repository.Asset;
import com.example.demo.infrastructure.repository.AssetPriceHistoryRepository;
import com.example.demo.infrastructure.repository.AssetRepository;
import com.example.demo.infrastructure.repository.CashOperation;
import com.example.demo.infrastructure.repository.CashOperationRepository;
import com.example.demo.infrastructure.repository.ClosedPosition;
import com.example.demo.infrastructure.repository.ClosedPositionRepository;
import com.example.demo.infrastructure.repository.OpenedPosition;
import com.example.demo.infrastructure.repository.OpenedPositionRepository;
import com.example.demo.infrastructure.repository.account.AccountRepository;
import com.example.demo.services.AssetCatalogService;
import com.example.demo.services.imports.ImportExecutionResult;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IbkrImportHistoryServiceTest {

  @Mock private ClosedPositionRepository closedPositionRepository;
  @Mock private CashOperationRepository cashOperationRepository;
  @Mock private OpenedPositionRepository openedPositionRepository;
  @Mock private AssetPriceHistoryRepository assetPriceHistoryRepository;
  @Mock private AssetRepository assetRepository;
  @Mock private AccountRepository accountRepository;

  private IbkrImportService service;
  private final List<CashOperation> persistedCashOperations = new ArrayList<>();

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
            reconstructionService);
    persistedCashOperations.clear();
    org.mockito.Mockito.lenient()
        .doAnswer(
            invocation -> {
              Iterable<CashOperation> rows = invocation.getArgument(0);
              for (CashOperation row : rows) {
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
        .when(assetRepository.findAllByIbrkIgnoreCase(org.mockito.ArgumentMatchers.anyString()))
        .thenAnswer(
            invocation -> {
              String brokerSymbol = invocation.getArgument(0);
              String canonicalSymbol =
                  brokerSymbol.contains(".") ? brokerSymbol : brokerSymbol + ".US";
              return List.of(asset(canonicalSymbol));
            });
  }

  private static Asset asset(String symbol) {
    String ticker = symbol.contains(".") ? symbol.substring(0, symbol.indexOf('.')) : symbol;
    return Asset.builder()
        .id((long) Math.abs(symbol.hashCode()) + 1L)
        .name(ticker)
        .symbol(symbol)
        .ticker(ticker)
        .ibrk(ticker)
        .yahoo(symbol)
        .country("US")
        .currency(CurrencyType.USD)
        .assetType("EQUITY")
        .active(true)
        .build();
  }

  @Test
  void importStatement_mapsIbkrSymbolToCanonicalAssetSymbolByIbrkColumn() throws Exception {
    Asset canonical =
        Asset.builder()
            .id(30L)
            .name("Realty Income")
            .symbol("O.US")
            .ticker("O")
            .ibrk("O")
            .yahoo("O.US")
            .country("US")
            .currency(com.example.demo.infrastructure.CurrencyType.USD)
            .assetType("EQUITY")
            .active(true)
            .build();
    when(assetRepository.findAllByIbrkIgnoreCase("O")).thenReturn(List.of(canonical));

    String csv =
        String.join(
            "\n",
            "Transaction History,Header,Transaction"
                + " Type,Account,Symbol,Description,Date,Quantity,Price,Net Amount,Currency",
            "Transaction History,Data,Buy,U1,O,Realty Income buy,2026-07-01,1,50,-50.00,USD");

    service.importStatement(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)), null);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Iterable<CashOperation>> cashCaptor =
        (ArgumentCaptor<Iterable<CashOperation>>)
            (ArgumentCaptor<?>) ArgumentCaptor.forClass(Iterable.class);
    verify(cashOperationRepository).saveAll(cashCaptor.capture());
    List<CashOperation> operations = toList(cashCaptor.getValue());
    assertEquals(1, operations.size());
    assertEquals(CashOperationType.STOCK_PURCHASE, operations.getFirst().getType());
    assertEquals("O.US", operations.getFirst().getSymbol());
    assertTrue(operations.getFirst().getAssetId() > 0);
  }

  @Test
  void importStatement_skipsAssetMarkedExcludedFromImport() throws Exception {
    Asset excluded = asset("AIGI.UK");
    excluded.setExcludeFromImport(true);
    when(assetRepository.findAllByIbrkIgnoreCase("AIGI")).thenReturn(List.of(excluded));
    when(assetRepository.findAllBySymbolIn(org.mockito.ArgumentMatchers.anyCollection()))
        .thenReturn(List.of(excluded));

    String csv =
        String.join(
            "\n",
            "Transaction History,Header,Transaction Type,Account,Symbol,Description,Date,Quantity,Price,Net Amount,Currency",
            "Transaction History,Data,Buy,U17959259,AIGI,AIGI buy,2026-07-01,1,100,-100.00,USD");

    service.importStatement(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)), null);

    assertTrue(persistedCashOperations.isEmpty());
    verify(assetPriceHistoryRepository, org.mockito.Mockito.never())
        .upsertIbkrTradeObservation(
            org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.any(LocalDate.class),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.any(BigDecimal.class));
  }

  @Test
  void importStatement_keepsAssetIdForStockSell() throws Exception {
    String csv =
        String.join(
            "\n",
            "Transaction History,Header,Transaction"
                + " Type,Account,Symbol,Description,Date,Quantity,Price,Net Amount,Currency",
            "Transaction History,Data,Sell,U17959259,AAPL,AAPL sell,2026-07-01,1,200,200.00,USD");

    service.importStatement(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)), null);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Iterable<CashOperation>> cashCaptor =
        (ArgumentCaptor<Iterable<CashOperation>>)
            (ArgumentCaptor<?>) ArgumentCaptor.forClass(Iterable.class);
    verify(cashOperationRepository).saveAll(cashCaptor.capture());
    List<CashOperation> operations = toList(cashCaptor.getValue());

    assertEquals(1, operations.size());
    assertEquals(CashOperationType.STOCK_SELL, operations.getFirst().getType());
    assertEquals("AAPL.US", operations.getFirst().getSymbol());
  }

  @Test
  void importStatement_reconstructsOpenPositionsFromTransactionOnlyFile() throws Exception {
    String csv =
        String.join(
            "\n",
            "Transaction History,Header,Transaction"
                + " Type,Account,Symbol,Description,Date,Quantity,Price,Net Amount,Currency",
            "Transaction History,Data,Buy,U17959259,AAPL,AAPL buy,2026-07-01,10,100,-1000.00,USD",
            "Transaction History,Data,Sell,U17959259,AAPL,AAPL sell,2026-07-02,4,120,480.00,USD",
            "Transaction History,Data,Buy,U17959259,MSFT,MSFT buy,2026-07-03,2,250,-500.00,USD");

    service.importStatement(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)), null);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Iterable<OpenedPosition>> positionCaptor =
        (ArgumentCaptor<Iterable<OpenedPosition>>)
            (ArgumentCaptor<?>) ArgumentCaptor.forClass(Iterable.class);
    verify(openedPositionRepository).saveAll(positionCaptor.capture());
    List<OpenedPosition> positions = toList(positionCaptor.getValue());

    OpenedPosition aapl =
        positions.stream()
            .filter(position -> "AAPL.US".equals(position.getSymbol()))
            .findFirst()
            .orElseThrow();
    assertEquals(6.0, aapl.getVolume(), 0.01);
    assertEquals(600.0, aapl.getPurchaseValue(), 0.01);
    assertEquals(100.0, aapl.getOpenPrice(), 0.01);
    assertTrue(aapl.getComment().contains("canonical cash history"));

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Iterable<ClosedPosition>> closedCaptor =
        (ArgumentCaptor<Iterable<ClosedPosition>>)
            (ArgumentCaptor<?>) ArgumentCaptor.forClass(Iterable.class);
    verify(closedPositionRepository).saveAll(closedCaptor.capture());
    List<ClosedPosition> closed = toList(closedCaptor.getValue());
    assertEquals(1, closed.size());
    assertEquals("AAPL.US", closed.getFirst().getSymbol());
    assertEquals(4.0, closed.getFirst().getVolume(), 0.01);
    assertEquals(400.0, closed.getFirst().getPurchaseValue(), 0.01);
    assertEquals(480.0, closed.getFirst().getSaleValue(), 0.01);
    assertEquals(80.0, closed.getFirst().getProfit(), 0.01);
  }

  @Test
  void importStatement_setsExplicitCurrenciesForOpenPositionSnapshot() throws Exception {
    String csv =
        String.join(
            "\n",
            "Transaction History,Header,Transaction Type,Account,Symbol,Description,Date,Quantity,Price,Net Amount,Currency",
            "Transaction History,Data,Buy,U17959259,AAPL,AAPL buy,2026-07-01,2,100,-200.00,EUR",
            "Open Positions,Header,Symbol,Quantity,Currency,Cost Price,Cost Basis,Close Price,Unrealized P/L,DataDiscriminator",
            "Open Positions,Data,AAPL,2,EUR,100,200,110,20,Summary");

    service.importStatement(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)), null);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Iterable<OpenedPosition>> positionCaptor =
        (ArgumentCaptor<Iterable<OpenedPosition>>)
            (ArgumentCaptor<?>) ArgumentCaptor.forClass(Iterable.class);
    verify(openedPositionRepository).saveAll(positionCaptor.capture());
    OpenedPosition position = toList(positionCaptor.getValue()).getFirst();

    assertEquals(CurrencyType.EUR, position.getPriceCurrency());
    assertEquals(CurrencyType.EUR, position.getCostCurrency());
    assertEquals(CurrencyType.EUR, position.getProfitCurrency());
    assertEquals(CurrencyType.EUR, position.getCommissionCurrency());
  }

  @Test
  void importStatement_doesNotInflateAppliedRowsWithOpenPositionSnapshot() throws Exception {
    String csv =
        String.join(
            "\n",
            "Transaction History,Header,Transaction Type,Account,Symbol,Description,Date,Quantity,Price,Price Currency,Net Amount,Currency",
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

  @Test
  void importStatement_reconstructsOpenPositionsFromIbkrAliasColumns() throws Exception {
    String csv =
        String.join(
            "\n",
            "Transaction History,Header,Type,Account ID,Symbol,Description,Date/Time,Qty,T."
                + " Price,Net Cash,Currency",
            "Transaction History,Data,Buy,U17959259,VWRA,VANG FTSE AW USDA,2026-01-07"
                + " 23:00:00,10,103.654,-1036.54,USD");

    service.importStatement(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)), null);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Iterable<OpenedPosition>> positionCaptor =
        (ArgumentCaptor<Iterable<OpenedPosition>>)
            (ArgumentCaptor<?>) ArgumentCaptor.forClass(Iterable.class);
    verify(openedPositionRepository).saveAll(positionCaptor.capture());
    List<OpenedPosition> positions = toList(positionCaptor.getValue());

    assertEquals(1, positions.size());
    OpenedPosition vwra = positions.getFirst();
    assertEquals(17959259L, vwra.getAccount());
    assertEquals("VWRA.US", vwra.getSymbol());
    assertEquals(10.0, vwra.getVolume(), 0.01);
    assertEquals(1036.54, vwra.getPurchaseValue(), 0.01);
    assertEquals(103.654, vwra.getOpenPrice(), 0.01);
  }

  @Test
  void importStatement_usesCashCurrencyColumnWhenPresent() throws Exception {
    String csv =
        String.join(
            "\n",
            "Transaction History,Header,Date,Account,Description,Transaction"
                + " Type,Currency,Symbol,Quantity,Price,Net Amount",
            "Transaction History,Data,2026-06-03,U17959259,EUR cash deposit,Deposit,EUR,,,"
                + ",100.00");

    service.importStatement(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)), null);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Iterable<CashOperation>> cashCaptor =
        (ArgumentCaptor<Iterable<CashOperation>>)
            (ArgumentCaptor<?>) ArgumentCaptor.forClass(Iterable.class);
    verify(cashOperationRepository).saveAll(cashCaptor.capture());
    List<CashOperation> operations = toList(cashCaptor.getValue());

    assertEquals(1, operations.size());
    assertEquals(CurrencyType.EUR, operations.getFirst().getCurrency());
    assertEquals(100.0, operations.getFirst().getAmount(), 0.01);
  }

  @Test
  void importStatement_rejectsUnknownAssetInsteadOfBootstrapping() throws Exception {
    when(assetRepository.findAllByIbrkIgnoreCase("NVDA")).thenReturn(List.of());

    String csv =
        String.join(
            "\n",
            "Transaction History,Header,Transaction Type,Account,Symbol,Description,Date,Quantity,Price,Net Amount,Currency",
            "Transaction History,Data,Buy,U17959259,NVDA,Nvidia buy,2026-07-01,1,100,-100.00,USD");

    ImportExecutionResult result =
        service.importStatement(
            new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)), null);

    assertEquals(1, result.rowsTotal());
    assertEquals(0, result.rowsApplied());
    assertEquals(1, result.rowsFailed());
    assertTrue(persistedCashOperations.isEmpty());
  }

  @Test
  void importStatement_usesStatementBaseCurrencyForCashWhenOnlyPriceCurrencyIsPresent()
      throws Exception {
    String csv =
        String.join(
            "\n",
            "Transaction History,Header,Date,Account,Description,Transaction"
                + " Type,Symbol,Quantity,Price,Price Currency,Net Amount",
            "Transaction History,Data,2026-06-03,U17959259,EUR-priced ETF buy,Buy,JGPI,1,22.54,"
                + "EUR,-26.45");

    ImportExecutionResult result =
        service.importStatement(
            new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)), null);

    assertEquals(1, result.rowsTotal());
    assertEquals(0, result.rowsFailed());

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Iterable<CashOperation>> cashCaptor =
        (ArgumentCaptor<Iterable<CashOperation>>)
            (ArgumentCaptor<?>) ArgumentCaptor.forClass(Iterable.class);
    verify(cashOperationRepository).saveAll(cashCaptor.capture());
    List<CashOperation> operations = toList(cashCaptor.getValue());

    assertEquals(1, operations.size());
    assertEquals(CashOperationType.STOCK_PURCHASE, operations.getFirst().getType());
    assertEquals(CurrencyType.USD, operations.getFirst().getCurrency());
    assertEquals(-26.45, operations.getFirst().getAmount(), 0.0001);
  }

  @Test
  void importStatement_mapsDividendWithoutRowCurrencyToStatementBaseCurrency() throws Exception {
    Asset eurListed =
        Asset.builder()
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
    when(assetRepository.findAllByIbrkIgnoreCase("XDWL")).thenReturn(List.of(eurListed));
    when(assetRepository.findAllBySymbolIn(Set.of("XDWL.DE"))).thenReturn(List.of(eurListed));

    String csv =
        String.join(
            "\n",
            "Transaction History,Header,Date,Account,Description,Transaction"
                + " Type,Symbol,Quantity,Price,Price Currency,Net Amount",
            "Transaction History,Data,2026-06-03,U17959259,XDWL cash dividend,Dividend,XDWL,-,-,-,3.99");

    ImportExecutionResult result =
        service.importStatement(
            new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)), null);

    assertEquals(1, result.rowsTotal());
    assertEquals(0, result.rowsFailed());

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Iterable<CashOperation>> cashCaptor =
        (ArgumentCaptor<Iterable<CashOperation>>)
            (ArgumentCaptor<?>) ArgumentCaptor.forClass(Iterable.class);
    verify(cashOperationRepository).saveAll(cashCaptor.capture());
    List<CashOperation> operations = toList(cashCaptor.getValue());

    assertEquals(1, operations.size());
    assertEquals(CashOperationType.DIVIDEND, operations.getFirst().getType());
    assertEquals(CurrencyType.USD, operations.getFirst().getCurrency());
    assertEquals(3.99, operations.getFirst().getAmount(), 0.0001);
  }

  @Test
  void importStatement_mapsUsDividendWithoutRowCurrencyToUsd() throws Exception {
    String csv =
        String.join(
            "\n",
            "Transaction History,Header,Date,Account,Description,Transaction"
                + " Type,Symbol,Quantity,Price,Price Currency,Net Amount",
            "Transaction History,Data,2026-06-03,U17959259,O cash dividend,Dividend,O,-,-,-,14.28");

    ImportExecutionResult result =
        service.importStatement(
            new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)), null);

    assertEquals(1, result.rowsTotal());
    assertEquals(0, result.rowsFailed());

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Iterable<CashOperation>> cashCaptor =
        (ArgumentCaptor<Iterable<CashOperation>>)
            (ArgumentCaptor<?>) ArgumentCaptor.forClass(Iterable.class);
    verify(cashOperationRepository).saveAll(cashCaptor.capture());
    List<CashOperation> operations = toList(cashCaptor.getValue());

    assertEquals(1, operations.size());
    assertEquals(CashOperationType.DIVIDEND, operations.getFirst().getType());
    assertEquals(CurrencyType.USD, operations.getFirst().getCurrency());
    assertEquals(14.28, operations.getFirst().getAmount(), 0.0001);
  }

  @Test
  void importStatement_fallsBackToStatementBaseCurrencyForSymbolLessRow() throws Exception {
    String csv =
        String.join(
            "\n",
            "Summary,Header,Field Name,Field Value",
            "Summary,Data,Base Currency,USD",
            "Transaction History,Header,Date,Account,Description,Transaction"
                + " Type,Symbol,Quantity,Price,Price Currency,Net Amount",
            "Transaction History,Data,2026-06-04,U17959259,USD Credit Interest,Credit"
                + " Interest,-,-,-,-,0.10");

    ImportExecutionResult result =
        service.importStatement(
            new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)), null);

    assertEquals(1, result.rowsTotal());
    assertEquals(0, result.rowsFailed());

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Iterable<CashOperation>> cashCaptor =
        (ArgumentCaptor<Iterable<CashOperation>>)
            (ArgumentCaptor<?>) ArgumentCaptor.forClass(Iterable.class);
    verify(cashOperationRepository).saveAll(cashCaptor.capture());
    List<CashOperation> operations = toList(cashCaptor.getValue());

    assertEquals(1, operations.size());
    assertEquals(CashOperationType.FREE_FUNDS_INTEREST, operations.getFirst().getType());
    assertEquals(CurrencyType.USD, operations.getFirst().getCurrency());
    assertEquals(0.10, operations.getFirst().getAmount(), 0.0001);
  }

  @Test
  void importStatement_rejectsUnknownBrokerSymbol() {
    when(assetRepository.findAllBySymbolIn(Set.of("UNKNOWN.US"))).thenReturn(List.of());
    String csv =
        String.join(
            "\n",
            "Transaction History,Header,Transaction Type,Account,Symbol,Description,Date,Quantity,Price,Net Amount,Currency",
            "Transaction History,Data,Buy,U17959259,UNKNOWN,Unknown asset,2026-07-01,1,10,-10.00,USD");

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                service.importStatement(
                    new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)), null));

    assertTrue(exception.getMessage().contains("Unknown asset mapping"));
  }

  @Test
  void importStatement_writesExactIbkrTradePriceToAssetPriceHistory() throws Exception {
    Asset asset =
        Asset.builder()
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
    when(assetRepository.findAllByIbrkIgnoreCase("IUVL")).thenReturn(List.of(asset));
    when(assetRepository.findAllBySymbolIn(Set.of("IUVL.UK"))).thenReturn(List.of(asset));

    String csv =
        String.join(
            "\n",
            "Transaction History,Header,Date,Account,Description,Transaction Type,Symbol,"
                + "Quantity,Price,Price Currency,Net Amount,Currency",
            "Transaction History,Data,2026-06-10,U17959259,ISHARES EDGE MSCI USA VALUE,Buy,"
                + "IUVL,100,18.3577,USD,-1835.77,USD");

    service.importStatement(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)), null);

    verify(assetPriceHistoryRepository)
        .upsertIbkrTradeObservation(
            41L, LocalDate.of(2026, 6, 10), "IUVL.UK", "IUVL", "USD", BigDecimal.valueOf(18.3577));
  }

  @Test
  void importStatement_mapsCorporateActionAndAdjustmentToConcreteCashTypes() throws Exception {
    String csv =
        String.join(
            "\n",
            "Transaction History,Header,Transaction"
                + " Type,Account,Symbol,Description,Date,Quantity,Price,Net Amount,Currency",
            "Transaction History,Data,Corporate Action,U1,TLT,Bond redemption,2026-07-01,,,100.00,USD",
            "Transaction History,Data,Adjustment,U1,,Manual correction,2026-07-02,,,-5.00,USD");

    ImportExecutionResult result =
        service.importStatement(
            new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)), null);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Iterable<CashOperation>> captor =
        (ArgumentCaptor<Iterable<CashOperation>>)
            (ArgumentCaptor<?>) ArgumentCaptor.forClass(Iterable.class);
    verify(cashOperationRepository).saveAll(captor.capture());
    List<CashOperation> operations = toList(captor.getValue());

    assertEquals(2, operations.size());
    assertEquals(CashOperationType.TRANSFER, operations.get(0).getType());
    assertEquals(CashOperationType.CORRECTION, operations.get(1).getType());
    assertEquals(2, result.rowsTotal());
    assertEquals(2, result.rowsApplied());
    assertEquals(0, result.rowsFailed());
  }

  @Test
  void importStatement_mapsIbkrCashTransferDepositToTransferInsteadOfExternalDeposit()
      throws Exception {
    String csv =
        String.join(
            "\n",
            "Transaction History,Header,Date,Account,Description,Transaction"
                + " Type,Currency,Symbol,Quantity,Price,Net Amount",
            "Transaction History,Data,2025-02-13,U17959259,Cash Transfer,Deposit,USD,,,,7838.285");

    service.importStatement(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)), null);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Iterable<CashOperation>> captor =
        (ArgumentCaptor<Iterable<CashOperation>>)
            (ArgumentCaptor<?>) ArgumentCaptor.forClass(Iterable.class);
    verify(cashOperationRepository).saveAll(captor.capture());
    List<CashOperation> operations = toList(captor.getValue());

    assertEquals(1, operations.size());
    assertEquals(CashOperationType.TRANSFER, operations.getFirst().getType());
    assertNull(operations.getFirst().getSymbol());
  }

  @Test
  void importStatement_preservesForexMetadataWithoutUsingPseudoSymbolAsAssetId() throws Exception {
    String csv =
        String.join(
            "\n",
            "Transaction History,Header,Date,Account,Description,Transaction"
                + " Type,Symbol,Quantity,Price,Net Amount,Currency",
            "Transaction History,Data,2026-05-08,Alex&Olga,Net Amount in Base from Forex Trade:"
                + " -3.32 EUR.USD,Forex Trade Component,EUR.USD,-3.32,1.17382,-0.0158696,USD");

    service.importStatement(
        new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)),
        "U17959259.TRANSACTIONS.20250211.20260612.csv");

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Iterable<CashOperation>> cashCaptor =
        (ArgumentCaptor<Iterable<CashOperation>>)
            (ArgumentCaptor<?>) ArgumentCaptor.forClass(Iterable.class);
    verify(cashOperationRepository).saveAll(cashCaptor.capture());
    List<CashOperation> operations = toList(cashCaptor.getValue());
    assertEquals(1, operations.size());
    assertEquals(CashOperationType.TRANSFER, operations.getFirst().getType());
    assertNull(operations.getFirst().getSymbol());
    assertTrue(operations.getFirst().getComment().contains("ibkrRawType=Forex Trade Component"));
    assertTrue(operations.getFirst().getComment().contains("ibkrRawSymbol=EUR.USD"));
    assertTrue(operations.getFirst().getComment().contains("ibkrQuantity=-3.32"));
    assertTrue(operations.getFirst().getComment().contains("ibkrPrice=1.17382"));
    assertEquals(17959259L, operations.getFirst().getAccount());
  }

  @Test
  void importStatement_compactsLongBondSymbolBeforeAssetInsert() throws Exception {
    String csv =
        String.join(
            "\n",
            "Transaction History,Header,Date,Account,Description,Transaction"
                + " Type,Symbol,Quantity,Price,Net Amount,Currency",
            "Transaction History,Data,2025-05-06,Alex&Olga,T 4 5/8 02/28/26,Buy,T 4 5/8"
                + " 02/28/26,8000.0,100.42611625,-8039.09,USD");

    service.importStatement(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)), null);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Iterable<OpenedPosition>> positionCaptor =
        (ArgumentCaptor<Iterable<OpenedPosition>>)
            (ArgumentCaptor<?>) ArgumentCaptor.forClass(Iterable.class);
    verify(openedPositionRepository).saveAll(positionCaptor.capture());
    List<OpenedPosition> positions = toList(positionCaptor.getValue());
    assertEquals(1, positions.size());
    assertEquals("T458022826.US", positions.getFirst().getSymbol());
    assertEquals(8.0, positions.getFirst().getVolume(), 0.01);
    assertEquals(8039.09, positions.getFirst().getPurchaseValue(), 0.01);
    assertEquals(1004.88625, positions.getFirst().getOpenPrice(), 0.00000001);
    assertEquals("T 4 5/8 02/28/26", positions.getFirst().getSourceAssetSymbol());
    assertTrue(positions.getFirst().getAssetId() > 0);
  }

  @Test
  void importStatement_removesBondClosedByFinalCallFromReconstructedPositions() throws Exception {
    String csv =
        String.join(
            "\n",
            "Transaction History,Header,Date,Account,Description,Transaction"
                + " Type,Symbol,Quantity,Price,Price Currency,Gross Amount ,Commission,Net Amount,Currency",
            "Transaction History,Data,2026-02-27,Alex&Olga,\"(US91282CKB62) Full Call / Early"
                + " Redemption for USD 1.00 per Bond (T 4 5/8 02/28/26, T 4 5/8 02/28/26,"
                + " US91282CKB62)\",Corporate Action,T 4 5/8 02/28/26,-,-,-,10000.0,-,10000.0,USD",
            "Transaction History,Data,2026-02-11,Alex&Olga,VANG FTSE AW USDA,Buy,VWRA,"
                + "2.0,177.0,USD,-354.0,-4.0,-358.0,USD",
            "Transaction History,Data,2025-05-06,Alex&Olga,T 4 5/8 02/28/26,Buy,T 4 5/8"
                + " 02/28/26,8000.0,100.42611625,USD,-8034.09,-5.0,-8039.09,USD",
            "Transaction History,Data,2025-04-17,Alex&Olga,T 4 5/8 02/28/26,Buy,T 4 5/8"
                + " 02/28/26,1000.0,100.484375,USD,-1004.84,-5.0,-1009.84,USD",
            "Transaction History,Data,2025-03-26,Alex&Olga,T 4 5/8 02/28/26,Buy,T 4 5/8"
                + " 02/28/26,1000.0,100.41049125,USD,-1004.1,-5.0,-1009.1,USD");

    service.importStatement(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)), null);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Iterable<OpenedPosition>> positionCaptor =
        (ArgumentCaptor<Iterable<OpenedPosition>>)
            (ArgumentCaptor<?>) ArgumentCaptor.forClass(Iterable.class);
    verify(openedPositionRepository).saveAll(positionCaptor.capture());
    List<OpenedPosition> positions = toList(positionCaptor.getValue());

    assertEquals(2.0, positions.stream().mapToDouble(OpenedPosition::getVolume).sum(), 0.01);
    assertTrue(positions.stream().allMatch(position -> "VWRA.US".equals(position.getSymbol())));
    assertFalse(
        positions.stream().anyMatch(position -> "T458022826.US".equals(position.getSymbol())));

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Iterable<ClosedPosition>> closedCaptor =
        (ArgumentCaptor<Iterable<ClosedPosition>>)
            (ArgumentCaptor<?>) ArgumentCaptor.forClass(Iterable.class);
    verify(closedPositionRepository).saveAll(closedCaptor.capture());
    List<ClosedPosition> closed = toList(closedCaptor.getValue());
    assertEquals(3, closed.size());
    assertTrue(closed.stream().allMatch(position -> "T458022826.US".equals(position.getSymbol())));
    assertEquals(10.0, closed.stream().mapToDouble(ClosedPosition::getVolume).sum(), 0.01);
    assertEquals(10000.0, closed.stream().mapToDouble(ClosedPosition::getSaleValue).sum(), 0.01);
    assertEquals(
        Set.of(LocalDate.of(2025, 3, 26), LocalDate.of(2025, 4, 17), LocalDate.of(2025, 5, 6)),
        closed.stream()
            .map(position -> position.getOpenTime().toLocalDate())
            .collect(Collectors.toSet()));
  }

  @Test
  void importStatement_deletesIbkrPositionsWhenTransactionReconstructionEndsEmpty()
      throws Exception {
    String csv =
        String.join(
            "\n",
            "Transaction History,Header,Date,Account,Description,Transaction"
                + " Type,Symbol,Quantity,Price,Price Currency,Gross Amount ,Commission,Net Amount,Currency",
            "Transaction History,Data,2026-02-27,Alex&Olga,\"(US91282CKB62) Full Call / Early"
                + " Redemption for USD 1.00 per Bond (T 4 5/8 02/28/26, T 4 5/8 02/28/26,"
                + " US91282CKB62)\",Corporate Action,T 4 5/8 02/28/26,-,-,-,10000.0,-,10000.0,USD",
            "Transaction History,Data,2025-05-06,Alex&Olga,T 4 5/8 02/28/26,Buy,T 4 5/8"
                + " 02/28/26,10000.0,100.42611625,USD,-10042.61,-5.0,-10047.61,USD");

    service.importStatement(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)), null);

    verify(openedPositionRepository).deleteByAccount(17959259L);
  }

  @Test
  void importStatement_usesAccountIdFromFilenameBeforeAliasColumn() throws Exception {
    String csv =
        String.join(
            "\n",
            "Transaction History,Header,Date,Account,Description,Transaction Type,Currency,Net Amount",
            "Transaction History,Data,2026-06-03,Alex&Olga,USD deposit,Deposit,USD,100.00");

    service.importStatement(
        new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)),
        "U17959259.TRANSACTIONS.20250211.20260612.csv");

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Iterable<CashOperation>> cashCaptor =
        (ArgumentCaptor<Iterable<CashOperation>>)
            (ArgumentCaptor<?>) ArgumentCaptor.forClass(Iterable.class);
    verify(cashOperationRepository).saveAll(cashCaptor.capture());
    List<CashOperation> operations = toList(cashCaptor.getValue());
    assertEquals(17959259L, operations.getFirst().getAccount());
  }

  @Test
  void importStatement_keepsAliasFallbackWhenFilenameHasNoAccount() throws Exception {
    String csv =
        String.join(
            "\n",
            "Transaction History,Header,Date,Account ID,Description,Transaction Type,Currency,Net Amount",
            "Transaction History,Data,2026-06-03,U18000001,USD deposit,Deposit,USD,100.00");

    service.importStatement(
        new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)),
        "statement-without-account.csv");

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Iterable<CashOperation>> cashCaptor =
        (ArgumentCaptor<Iterable<CashOperation>>)
            (ArgumentCaptor<?>) ArgumentCaptor.forClass(Iterable.class);
    verify(cashOperationRepository).saveAll(cashCaptor.capture());
    List<CashOperation> operations = toList(cashCaptor.getValue());
    assertEquals(18000001L, operations.getFirst().getAccount());
  }

  @Test
  void importStatement_preservesForexTradeComponentMetadataWithoutUsingPseudoAssetId()
      throws Exception {
    String csv =
        String.join(
            "\n",
            "Transaction History,Header,Date,Account,Description,Transaction Type,Symbol,Quantity,Price,Currency,Gross Amount,Commission,Net Amount",
            "Transaction History,Data,2026-06-03,U17959259,Forex Trade Component for EUR.USD,Forex Trade Component,EUR.USD,1000,1.12,USD,1120.00,0.00,1120.00");

    service.importStatement(
        new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)),
        "U17959259.TRANSACTIONS.20260211.20260612.csv");

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Iterable<CashOperation>> cashCaptor =
        (ArgumentCaptor<Iterable<CashOperation>>)
            (ArgumentCaptor<?>) ArgumentCaptor.forClass(Iterable.class);
    verify(cashOperationRepository).saveAll(cashCaptor.capture());
    List<CashOperation> operations = toList(cashCaptor.getValue());
    assertEquals(1, operations.size());
    CashOperation operation = operations.getFirst();
    assertEquals(CashOperationType.TRANSFER, operation.getType());
    assertNull(operation.getSymbol());
    assertTrue(operation.getComment().contains("ibkrRawType=Forex Trade Component"));
    assertTrue(operation.getComment().contains("ibkrRawSymbol=EUR.USD"));
    assertTrue(operation.getComment().contains("ibkrQuantity=1000.0"));
    assertTrue(operation.getComment().contains("ibkrPrice=1.12"));
  }

  @Test
  void importStatement_storesCommissionExplicitlyOnClosedPositionWithoutChangingNetCash()
      throws Exception {
    String csv =
        String.join(
            "\n",
            "Transaction History,Header,Date,Account,Description,Transaction"
                + " Type,Symbol,Quantity,Price,Price Currency,Gross Amount ,Commission,Net Amount,Currency",
            "Transaction History,Data,2026-06-01,U17959259,AAPL buy,Buy,AAPL,10,100,USD,-1000.0,-5.0,-1005.0,USD",
            "Transaction History,Data,2026-06-02,U17959259,AAPL sell,Sell,AAPL,10,110,USD,1100.0,-7.0,1093.0,USD");

    service.importStatement(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)), null);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Iterable<ClosedPosition>> closedCaptor =
        (ArgumentCaptor<Iterable<ClosedPosition>>)
            (ArgumentCaptor<?>) ArgumentCaptor.forClass(Iterable.class);
    verify(closedPositionRepository).saveAll(closedCaptor.capture());
    List<ClosedPosition> closed = toList(closedCaptor.getValue());
    assertEquals(1, closed.size());
    assertEquals(7.0, closed.getFirst().getCommission(), 0.0001);
    assertEquals(1093.0, closed.getFirst().getSaleValue(), 0.0001);
    assertEquals(88.0, closed.getFirst().getProfit(), 0.0001);
  }

  @Test
  void importStatement_rebuildsPositionsAcrossOverlappingIncrementalImports() throws Exception {
    String fileA =
        String.join(
            "\n",
            "Transaction History,Header,Date,Account,Description,Transaction Type,Symbol,Quantity,Price,Net Amount,Currency",
            "Transaction History,Data,2026-02-11,U17959259,JGPI buy,Buy,JGPI,404,10,-4040.00,USD",
            "Transaction History,Data,2026-02-12,U17959259,VWRA buy,Buy,VWRA,82,100,-8200.00,USD",
            "Transaction History,Data,2026-02-13,U17959259,IUVL buy,Buy,IUVL,610,20,-12200.00,USD");
    String fileB =
        String.join(
            "\n",
            "Transaction History,Header,Date,Account,Description,Transaction Type,Symbol,Quantity,Price,Net Amount,Currency",
            "Transaction History,Data,2026-06-08,U17959259,VWRA buy,Buy,VWRA,16,101,-1616.00,USD",
            "Transaction History,Data,2026-06-09,U17959259,VWRA buy,Buy,VWRA,16,102,-1632.00,USD",
            "Transaction History,Data,2026-06-10,U17959259,VWRA buy,Buy,VWRA,32,103,-3296.00,USD",
            "Transaction History,Data,2026-06-11,U17959259,IUVL sell,Sell,IUVL,610,21,12810.00,USD");

    service.importStatement(
        new ByteArrayInputStream(fileA.getBytes(StandardCharsets.UTF_8)), "A.csv");
    service.importStatement(
        new ByteArrayInputStream(fileB.getBytes(StandardCharsets.UTF_8)), "B.csv");

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Iterable<OpenedPosition>> openCaptor =
        (ArgumentCaptor<Iterable<OpenedPosition>>)
            (ArgumentCaptor<?>) ArgumentCaptor.forClass(Iterable.class);
    verify(openedPositionRepository, atLeastOnce()).saveAll(openCaptor.capture());
    List<OpenedPosition> latest = toList(openCaptor.getAllValues().getLast());

    assertEquals(404.0, volumeFor(latest, "JGPI.US"), 0.0001);
    assertEquals(146.0, volumeFor(latest, "VWRA.US"), 0.0001);
    assertEquals(0.0, volumeFor(latest, "IUVL.UK"), 0.0001);
  }

  @Test
  void importStatement_rebuildIsDeterministicForReverseImportOrder() throws Exception {
    String fileA =
        String.join(
            "\n",
            "Transaction History,Header,Date,Account,Description,Transaction Type,Symbol,Quantity,Price,Net Amount,Currency",
            "Transaction History,Data,2026-02-11,U17959259,JGPI buy,Buy,JGPI,404,10,-4040.00,USD",
            "Transaction History,Data,2026-02-12,U17959259,VWRA buy,Buy,VWRA,82,100,-8200.00,USD",
            "Transaction History,Data,2026-02-13,U17959259,IUVL buy,Buy,IUVL,610,20,-12200.00,USD");
    String fileB =
        String.join(
            "\n",
            "Transaction History,Header,Date,Account,Description,Transaction Type,Symbol,Quantity,Price,Net Amount,Currency",
            "Transaction History,Data,2026-06-08,U17959259,VWRA buy,Buy,VWRA,16,101,-1616.00,USD",
            "Transaction History,Data,2026-06-09,U17959259,VWRA buy,Buy,VWRA,16,102,-1632.00,USD",
            "Transaction History,Data,2026-06-10,U17959259,VWRA buy,Buy,VWRA,32,103,-3296.00,USD",
            "Transaction History,Data,2026-06-11,U17959259,IUVL sell,Sell,IUVL,610,21,12810.00,USD");

    service.importStatement(
        new ByteArrayInputStream(fileB.getBytes(StandardCharsets.UTF_8)), "B.csv");
    service.importStatement(
        new ByteArrayInputStream(fileA.getBytes(StandardCharsets.UTF_8)), "A.csv");

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Iterable<OpenedPosition>> openCaptor =
        (ArgumentCaptor<Iterable<OpenedPosition>>)
            (ArgumentCaptor<?>) ArgumentCaptor.forClass(Iterable.class);
    verify(openedPositionRepository, atLeastOnce()).saveAll(openCaptor.capture());
    List<OpenedPosition> latest = toList(openCaptor.getAllValues().getLast());

    assertEquals(404.0, volumeFor(latest, "JGPI.US"), 0.0001);
    assertEquals(146.0, volumeFor(latest, "VWRA.US"), 0.0001);
    assertEquals(0.0, volumeFor(latest, "IUVL.UK"), 0.0001);
  }

  @Test
  void importStatement_ignoresStaleSnapshotRowsWhenCanonicalHistoryShowsSymbolClosed()
      throws Exception {
    String fileA =
        String.join(
            "\n",
            "Transaction History,Header,Date,Account,Description,Transaction Type,Symbol,Quantity,Price,Net Amount,Currency",
            "Transaction History,Data,2026-01-02,U17959259,AAPL buy,Buy,AAPL,4,100,-400.00,USD",
            "Transaction History,Data,2026-01-03,U17959259,JGPI buy,Buy,JGPI,10,20,-200.00,USD");
    String fileB =
        String.join(
            "\n",
            "Transaction History,Header,Date,Account,Description,Transaction Type,Symbol,Quantity,Price,Net Amount,Currency",
            "Transaction History,Data,2026-01-05,U17959259,AAPL sell,Sell,AAPL,-4,110,440.00,USD",
            "Open Positions,Header,Symbol,Quantity,Currency,Cost Price,Cost Basis,Close Price,Unrealized P/L,DataDiscriminator",
            "Open Positions,Data,AAPL,4,USD,100,400,110,40,Summary",
            "Open Positions,Data,JGPI,10,USD,20,200,21,10,Summary");

    service.importStatement(
        new ByteArrayInputStream(fileA.getBytes(StandardCharsets.UTF_8)), "A.csv");
    service.importStatement(
        new ByteArrayInputStream(fileB.getBytes(StandardCharsets.UTF_8)), "B.csv");

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Iterable<OpenedPosition>> openCaptor =
        (ArgumentCaptor<Iterable<OpenedPosition>>)
            (ArgumentCaptor<?>) ArgumentCaptor.forClass(Iterable.class);
    verify(openedPositionRepository, atLeastOnce()).saveAll(openCaptor.capture());
    List<OpenedPosition> latest = toList(openCaptor.getAllValues().getLast());

    assertEquals(0.0, volumeFor(latest, "AAPL.US"), 0.0001);
    assertEquals(10.0, volumeFor(latest, "JGPI.US"), 0.0001);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Iterable<ClosedPosition>> closedCaptor =
        (ArgumentCaptor<Iterable<ClosedPosition>>)
            (ArgumentCaptor<?>) ArgumentCaptor.forClass(Iterable.class);
    verify(closedPositionRepository, atLeastOnce()).saveAll(closedCaptor.capture());
    List<ClosedPosition> closed = toList(closedCaptor.getAllValues().getLast());

    assertEquals(
        1, closed.stream().filter(position -> "AAPL.US".equals(position.getSymbol())).count());
    assertEquals(
        4.0,
        closed.stream()
            .filter(position -> "AAPL.US".equals(position.getSymbol()))
            .mapToDouble(ClosedPosition::getVolume)
            .sum(),
        0.0001);
  }

  private static double volumeFor(List<OpenedPosition> positions, String symbol) {
    return positions.stream()
        .filter(position -> symbol.equals(position.getSymbol()))
        .mapToDouble(OpenedPosition::getVolume)
        .sum();
  }

  private static <T> List<T> toList(Iterable<T> iterable) {
    java.util.ArrayList<T> list = new java.util.ArrayList<>();
    iterable.forEach(list::add);
    return list;
  }
}
