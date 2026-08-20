package com.smartbox.investory.services.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smartbox.investory.infrastructure.PositionSettlementModel;
import com.smartbox.investory.infrastructure.PositionType;
import com.smartbox.investory.infrastructure.repository.Asset;
import com.smartbox.investory.infrastructure.repository.AssetRepository;
import com.smartbox.investory.infrastructure.repository.CashOperationRepository;
import com.smartbox.investory.infrastructure.repository.ClosedPositionRepository;
import com.smartbox.investory.infrastructure.repository.OpenedPosition;
import com.smartbox.investory.infrastructure.repository.OpenedPositionRepository;
import com.smartbox.investory.infrastructure.repository.portfolio.SymbolPerformanceRepository;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AssetDetailServiceTest {

  private final AssetRepository assets = mock();
  private final OpenedPositionRepository open = mock();
  private final ClosedPositionRepository closed = mock();
  private final CashOperationRepository cash = mock();
  private final SymbolPerformanceRepository performance = mock();
  private final AssetDetailService service =
      new AssetDetailService(assets, open, closed, cash, performance);

  @Test
  void aggregatesSignedQuantitiesAndWeightedCostByAccount() {
    Asset asset =
        Asset.builder()
            .id(9L)
            .symbol("VWCE")
            .name("Vanguard")
            .ticker("VWCE")
            .country("IE")
            .currency(CurrencyType.EUR)
            .assetType("ETF")
            .marketPrice(java.math.BigDecimal.valueOf(120d))
            .build();
    OpenedPosition first = position(1L, PositionType.BUY, 2d, 100d);
    OpenedPosition second = position(1L, PositionType.BUY, 1d, 130d);
    OpenedPosition otherAccount = position(2L, PositionType.SELL, 1d, 90d);
    when(assets.findBySymbol("VWCE")).thenReturn(Optional.of(asset));
    when(open.findOpenByAssetId(9L)).thenReturn(List.of(first, second, otherAccount));
    when(closed.findClosedByAssetId(9L)).thenReturn(List.of());
    when(cash.findAllByAssetIdAndTypeInOrderByDateDescIdDesc(eq(9L), any())).thenReturn(List.of());
    when(performance.findAllBySymbol("VWCE")).thenReturn(List.of());

    AssetDetailView view = service.findBySymbol(" vwce ", DashboardPeriod.MAX);

    assertThat(view.holdings()).hasSize(2);
    assertThat(view.holdings().get(0).accountId()).isEqualTo(1L);
    assertThat(view.holdings().get(0).quantity()).isEqualTo(3d);
    assertThat(view.holdings().get(0).averageCost()).isEqualTo(110d);
    assertThat(view.holdings().get(0).marketValue()).isEqualTo(360d);
    assertThat(view.totalQuantity()).isEqualTo(2d);
  }

  @Test
  void rejectsBlankAndUnknownSymbols() {
    assertThatThrownBy(() -> service.findBySymbol(" "))
        .isInstanceOf(AssetDetailNotFoundException.class);
    when(assets.findBySymbol("NOPE")).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.findBySymbol("nope"))
        .isInstanceOf(AssetDetailNotFoundException.class);
  }

  private OpenedPosition position(Long account, PositionType type, double quantity, double price) {
    OpenedPosition position = new OpenedPosition();
    position.setAccount(account);
    position.setType(type);
    position.setVolume(quantity);
    position.setOpenPrice(price);
    position.setCostCurrency(CurrencyType.EUR);
    position.setPriceCurrency(CurrencyType.EUR);
    position.setSettlementModel(PositionSettlementModel.CASH_SETTLED);
    return position;
  }
}
