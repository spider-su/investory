package com.smartbox.investory.infrastructure.repository;

import com.smartbox.investory.infrastructure.CashOperationType;
import com.smartbox.investory.infrastructure.CurrencyType;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZonedDateTime;
import lombok.*;
import org.hibernate.annotations.Formula;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Entity
@EqualsAndHashCode(of = "id")
@Table(name = "cash_operations")
public class CashOperation {

  @Id private Long id;

  @Column(name = "account_id", nullable = false)
  private Long account;

  @Enumerated(value = EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(name = "operation", nullable = false, columnDefinition = "cash_operation_type")
  private CashOperationType type;

  @Column(name = "asset_id")
  private Long assetId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "asset_id", insertable = false, updatable = false)
  private Asset asset;

  @Column(name = "source_asset_symbol", length = 128)
  private String sourceAssetSymbol;

  @Column(name = "broker_symbol", length = 128)
  private String brokerSymbol;

  /** Nullable for legacy/manual rows; mandatory for rows created by broker import orchestration. */
  @Column(name = "import_history_id")
  private Long importHistoryId;

  @Column(name = "import_source_row_id")
  private Long importSourceRowId;

  @Formula(
      "coalesce((select asset.symbol from investory.assets asset where asset.id = asset_id), source_asset_symbol)")
  private String symbol;

  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  @Column(nullable = false, precision = 20, scale = 8)
  private BigDecimal amount;

  @Enumerated(value = EnumType.STRING)
  @Column(name = "currency", nullable = false, length = 3)
  private CurrencyType currency;

  @Column(name = "comment", length = 1023)
  private String comment;

  @Column(nullable = false)
  private ZonedDateTime date;

  @Enumerated(value = EnumType.STRING)
  @Column(name = "execution_fx_base", length = 3)
  private CurrencyType executionFxBase;

  @Enumerated(value = EnumType.STRING)
  @Column(name = "execution_fx_to_currency", length = 3)
  private CurrencyType executionFxToCurrency;

  @Column(name = "execution_fx_rate", precision = 20, scale = 8)
  private BigDecimal executionFxRate;

  @Column(name = "execution_fx_observed_at")
  private ZonedDateTime executionFxObservedAt;

  @Column(name = "execution_fx_source", length = 32)
  private String executionFxSource;

  @Column(name = "execution_fx_reference", length = 256)
  private String executionFxReference;

  public Double getAmount() {
    return amount == null ? null : amount.doubleValue();
  }

  public BigDecimal getAmountValue() {
    return amount;
  }

  public void setAmount(BigDecimal amount) {
    this.amount = scaleAmount(amount);
  }

  public void setAmount(Double amount) {
    this.amount = amount == null ? null : scaleAmount(BigDecimal.valueOf(amount));
  }

  public void setAmount(double amount) {
    setAmount(Double.valueOf(amount));
  }

  public void setSymbol(String symbol) {
    this.symbol = symbol;
    if (this.sourceAssetSymbol == null) {
      this.sourceAssetSymbol = symbol;
    }
  }

  private static BigDecimal scaleAmount(BigDecimal amount) {
    return amount == null ? null : amount.setScale(8, RoundingMode.HALF_UP);
  }
}
