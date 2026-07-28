package com.example.demo.services.imports.ibrk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
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

  @BeforeEach
  void setUp() {
    AssetCatalogService assetCatalogService = new AssetCatalogService(assetRepository);
    service =
        new IbkrImportService(
            closedPositionRepository,
            cashOperationRepository,
            openedPositionRepository,
            assetPriceHistoryRepository,
            assetRepository,
            accountRepository,
            assetCatalogService);
    org.mockito.Mockito.lenient()
        .when(openedPositionRepository.findAllByAccount(17959259L))
        .thenReturn(List.of());
    org.mockito.Mockito.lenient()
        .when(assetRepository.findAllBySymbolIn(org.mockito.ArgumentMatchers.anyCollection()))
        .thenReturn(List.of());
    org.mockito.Mockito.lenient()
        .when(assetRepository.findAllByTickerIn(org.mockito.ArgumentMatchers.anyCollection()))
        .thenReturn(List.of());
    org.mockito.Mockito.lenient()
        .when(assetRepository.findAllByIbrkIgnoreCase(org.mockito.ArgumentMatchers.anyString()))
        .thenReturn(List.of());
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
                + " Type,Account,Symbol,Description,Date,Quantity,Price,Net Amount",
            "Transaction History,Data,Buy,U1,O,Realty Income buy,2026-07-01,1,50,-50.00");

    service.importStatement(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Iterable<CashOperation>> cashCaptor =
        (ArgumentCaptor<Iterable<CashOperation>>)
            (ArgumentCaptor<?>) ArgumentCaptor.forClass(Iterable.class);
    verify(cashOperationRepository).saveAll(cashCaptor.capture());
    List<CashOperation> operations = toList(cashCaptor.getValue());
    assertEquals(1, operations.size());
    assertEquals(CashOperationType.STOCK_PURCHASE, operations.getFirst().getType());
    assertEquals("O.US", operations.getFirst().getSymbol());

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Iterable<Asset>> assetCaptor =
        (ArgumentCaptor<Iterable<Asset>>)
            (ArgumentCaptor<?>) ArgumentCaptor.forClass(Iterable.class);
    verify(assetRepository).saveAll(assetCaptor.capture());
    List<Asset> assets = toList(assetCaptor.getValue());
    assertEquals(1, assets.size());
    assertEquals("O.US", assets.getFirst().getSymbol());
  }

  @Test
  void importStatement_keepsAssetIdForStockSell() throws Exception {
    String csv =
        String.join(
            "\n",
            "Transaction History,Header,Transaction"
                + " Type,Account,Symbol,Description,Date,Quantity,Price,Net Amount",
            "Transaction History,Data,Sell,U17959259,AAPL,AAPL sell,2026-07-01,1,200,200.00");

    service.importStatement(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));

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
                + " Type,Account,Symbol,Description,Date,Quantity,Price,Net Amount",
            "Transaction History,Data,Buy,U17959259,AAPL,AAPL buy,2026-07-01,10,100,-1000.00",
            "Transaction History,Data,Sell,U17959259,AAPL,AAPL sell,2026-07-02,4,120,480.00",
            "Transaction History,Data,Buy,U17959259,MSFT,MSFT buy,2026-07-03,2,250,-500.00");

    service.importStatement(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Iterable<OpenedPosition>> positionCaptor =
        (ArgumentCaptor<Iterable<OpenedPosition>>)
            (ArgumentCaptor<?>) ArgumentCaptor.forClass(Iterable.class);
    verify(openedPositionRepository).saveAll(positionCaptor.capture());
    List<OpenedPosition> positions = toList(positionCaptor.getValue());

    assertEquals(2, positions.size());
    OpenedPosition aapl =
        positions.stream()
            .filter(position -> "AAPL.US".equals(position.getSymbol()))
            .findFirst()
            .orElseThrow();
    assertEquals(6.0, aapl.getVolume(), 0.01);
    assertEquals(600.0, aapl.getPurchaseValue(), 0.01);
    assertEquals(100.0, aapl.getOpenPrice(), 0.01);

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
  void importStatement_reconstructsOpenPositionsFromIbkrAliasColumns() throws Exception {
    String csv =
        String.join(
            "\n",
            "Transaction History,Header,Type,Account ID,Symbol,Description,Date/Time,Qty,T."
                + " Price,Net Cash",
            "Transaction History,Data,Buy,U17959259,VWRA,VANG FTSE AW USDA,2026-01-07"
                + " 23:00:00,10,103.654,-1036.54");

    service.importStatement(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));

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

    service.importStatement(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));

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
  void importStatement_doesNotUsePriceCurrencyAsCashCurrency() throws Exception {
    String csv =
        String.join(
            "\n",
            "Transaction History,Header,Date,Account,Description,Transaction"
                + " Type,Symbol,Quantity,Price,Price Currency,Net Amount",
            "Transaction History,Data,2026-06-03,U17959259,EUR-priced ETF buy,Buy,JGPI,1,22.54,"
                + "EUR,-26.45");

    service.importStatement(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Iterable<CashOperation>> cashCaptor =
        (ArgumentCaptor<Iterable<CashOperation>>)
            (ArgumentCaptor<?>) ArgumentCaptor.forClass(Iterable.class);
    verify(cashOperationRepository).saveAll(cashCaptor.capture());
    List<CashOperation> operations = toList(cashCaptor.getValue());

    assertEquals(1, operations.size());
    assertEquals(CurrencyType.USD, operations.getFirst().getCurrency());
    assertEquals(-26.45, operations.getFirst().getAmount(), 0.01);
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
                + "Quantity,Price,Price Currency,Net Amount",
            "Transaction History,Data,2026-06-10,U17959259,ISHARES EDGE MSCI USA VALUE,Buy,"
                + "IUVL,100,18.3577,USD,-1835.77");

    service.importStatement(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));

    verify(assetPriceHistoryRepository)
        .upsertIbkrTradeObservation(
            41L, LocalDate.of(2026, 6, 10), "IUVL.UK", "IUVL", "USD", 18.3577);
  }

  @Test
  void importStatement_mapsCorporateActionAndAdjustmentToConcreteCashTypes() throws Exception {
    String csv =
        String.join(
            "\n",
            "Transaction History,Header,Transaction"
                + " Type,Account,Symbol,Description,Date,Quantity,Price,Net Amount",
            "Transaction History,Data,Corporate Action,U1,TLT,Bond redemption,2026-07-01,,,100.00",
            "Transaction History,Data,Adjustment,U1,,Manual correction,2026-07-02,,,-5.00");

    ImportExecutionResult result =
        service.importStatement(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));

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

    service.importStatement(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));

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
  void importStatement_skipsForexPseudoSymbolFromAssetsAndCashOperationSymbol() throws Exception {
    String csv =
        String.join(
            "\n",
            "Transaction History,Header,Date,Account,Description,Transaction"
                + " Type,Symbol,Quantity,Price,Net Amount",
            "Transaction History,Data,2026-05-08,Alex&Olga,Net Amount in Base from Forex Trade:"
                + " -3.32 EUR.USD,Forex Trade Component,EUR.USD,-3.32,1.17382,-0.0158696");

    service.importStatement(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Iterable<CashOperation>> cashCaptor =
        (ArgumentCaptor<Iterable<CashOperation>>)
            (ArgumentCaptor<?>) ArgumentCaptor.forClass(Iterable.class);
    verify(cashOperationRepository).saveAll(cashCaptor.capture());
    List<CashOperation> operations = toList(cashCaptor.getValue());
    assertEquals(1, operations.size());
    assertEquals(CashOperationType.TRANSFER, operations.getFirst().getType());
    assertNull(operations.getFirst().getSymbol());
  }

  @Test
  void importStatement_compactsLongBondSymbolBeforeAssetInsert() throws Exception {
    String csv =
        String.join(
            "\n",
            "Transaction History,Header,Date,Account,Description,Transaction"
                + " Type,Symbol,Quantity,Price,Net Amount",
            "Transaction History,Data,2025-05-06,Alex&Olga,T 4 5/8 02/28/26,Buy,T 4 5/8"
                + " 02/28/26,8000.0,100.42611625,-8039.09");

    service.importStatement(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));

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
    assertEquals(100.488625, positions.getFirst().getOpenPrice(), 0.00000001);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Iterable<Asset>> assetCaptor =
        (ArgumentCaptor<Iterable<Asset>>)
            (ArgumentCaptor<?>) ArgumentCaptor.forClass(Iterable.class);
    verify(assetRepository).saveAll(assetCaptor.capture());
    List<Asset> assets = toList(assetCaptor.getValue());
    assertEquals(1, assets.size());
    assertEquals("T458022826.US", assets.getFirst().getSymbol());
    assertEquals("T458022826", assets.getFirst().getTicker());
  }

  @Test
  void importStatement_removesBondClosedByFinalCallFromReconstructedPositions() throws Exception {
    String csv =
        String.join(
            "\n",
            "Transaction History,Header,Date,Account,Description,Transaction"
                + " Type,Symbol,Quantity,Price,Price Currency,Gross Amount ,Commission,Net Amount",
            "Transaction History,Data,2026-02-27,Alex&Olga,\"(US91282CKB62) Full Call / Early"
                + " Redemption for USD 1.00 per Bond (T 4 5/8 02/28/26, T 4 5/8 02/28/26,"
                + " US91282CKB62)\",Corporate Action,T 4 5/8 02/28/26,-,-,-,10000.0,-,10000.0",
            "Transaction History,Data,2026-02-11,Alex&Olga,VANG FTSE AW USDA,Buy,VWRA,"
                + "2.0,177.0,USD,-354.0,-4.0,-358.0",
            "Transaction History,Data,2025-05-06,Alex&Olga,T 4 5/8 02/28/26,Buy,T 4 5/8"
                + " 02/28/26,8000.0,100.42611625,USD,-8034.09,-5.0,-8039.09",
            "Transaction History,Data,2025-04-17,Alex&Olga,T 4 5/8 02/28/26,Buy,T 4 5/8"
                + " 02/28/26,1000.0,100.484375,USD,-1004.84,-5.0,-1009.84",
            "Transaction History,Data,2025-03-26,Alex&Olga,T 4 5/8 02/28/26,Buy,T 4 5/8"
                + " 02/28/26,1000.0,100.41049125,USD,-1004.1,-5.0,-1009.1");

    service.importStatement(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Iterable<OpenedPosition>> positionCaptor =
        (ArgumentCaptor<Iterable<OpenedPosition>>)
            (ArgumentCaptor<?>) ArgumentCaptor.forClass(Iterable.class);
    verify(openedPositionRepository).saveAll(positionCaptor.capture());
    List<OpenedPosition> positions = toList(positionCaptor.getValue());

    assertEquals(1, positions.size());
    assertEquals("VWRA.US", positions.getFirst().getSymbol());
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
    assertEquals(10000.0, closed.stream().mapToDouble(ClosedPosition::getVolume).sum(), 0.01);
    assertEquals(10000.0, closed.stream().mapToDouble(ClosedPosition::getSaleValue).sum(), 0.01);
    assertEquals(
        Set.of(
            LocalDate.of(2025, 3, 26),
            LocalDate.of(2025, 4, 17),
            LocalDate.of(2025, 5, 6)),
        closed.stream().map(position -> position.getOpenTime().toLocalDate()).collect(Collectors.toSet()));
  }

  @Test
  void importStatement_deletesIbkrPositionsWhenTransactionReconstructionEndsEmpty() throws Exception {
    String csv =
        String.join(
            "\n",
            "Transaction History,Header,Date,Account,Description,Transaction"
                + " Type,Symbol,Quantity,Price,Price Currency,Gross Amount ,Commission,Net Amount",
            "Transaction History,Data,2026-02-27,Alex&Olga,\"(US91282CKB62) Full Call / Early"
                + " Redemption for USD 1.00 per Bond (T 4 5/8 02/28/26, T 4 5/8 02/28/26,"
                + " US91282CKB62)\",Corporate Action,T 4 5/8 02/28/26,-,-,-,10000.0,-,10000.0",
            "Transaction History,Data,2025-05-06,Alex&Olga,T 4 5/8 02/28/26,Buy,T 4 5/8"
                + " 02/28/26,10000.0,100.42611625,USD,-10042.61,-5.0,-10047.61");

    service.importStatement(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));

    verify(openedPositionRepository).deleteByAccount(17959259L);
  }

  private static <T> List<T> toList(Iterable<T> iterable) {
    java.util.ArrayList<T> list = new java.util.ArrayList<>();
    iterable.forEach(list::add);
    return list;
  }
}
