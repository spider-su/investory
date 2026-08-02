package com.example.demo.infrastructure.repository;

import com.example.demo.infrastructure.CurrencyType;
import com.example.demo.infrastructure.PositionType;
import com.example.demo.infrastructure.PositionQuantities;
import com.example.demo.infrastructure.PositionSettlementModel;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.ZonedDateTime;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Entity
@EqualsAndHashCode(of = "id")
@Table(name = "positions")
public class OpenedPosition {
    @Id
    private Long id;

    @Column(name = "account_id", nullable = false)
    private Long account;

    @Column(name = "asset_id", nullable = false)
    private Long assetId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", insertable = false, updatable = false)
    private Asset asset;

    @Column(name = "source_asset_symbol", nullable = false)
    private String sourceAssetSymbol;

    @Column(name = "broker_symbol")
    private String brokerSymbol;

    @Column(name = "source_position_id")
    private String sourcePositionId;

    @Builder.Default
    @Column(name = "source_row_occurrence", nullable = false)
    private Integer sourceRowOccurrence = 1;

    @Transient
    private String symbol;

    @Enumerated(value = EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "operation", nullable = false, columnDefinition = "positions_operation_type")
    private PositionType type;

    @Builder.Default
    @Enumerated(value = EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "settlement_model", nullable = false, columnDefinition = "position_settlement_model")
    private PositionSettlementModel settlementModel = PositionSettlementModel.CASH_SETTLED;

    @Enumerated(value = EnumType.STRING)
    @Column(name = "profit_currency", nullable = false)
    private CurrencyType profitCurrency;

    @Enumerated(value = EnumType.STRING)
    @Column(name = "price_currency", nullable = false)
    private CurrencyType priceCurrency;

    @Enumerated(value = EnumType.STRING)
    @Column(name = "cost_currency", nullable = false)
    private CurrencyType costCurrency;

    @Enumerated(value = EnumType.STRING)
    @Column(name = "commission_currency", nullable = false)
    private CurrencyType commissionCurrency;

    @Column(nullable = false)
    private Double volume;

    @Column(name = "open_time", nullable = false)
    private ZonedDateTime openTime;

    @Column(nullable = false)
    private Double openPrice;

    @Column(name = "source_open_price")
    private Double sourceOpenPrice;

    @Column(name = "open_conversion_rate")
    private Double openConversionRate;

    @Transient
    private Double marketPrice;

    @Column(name = "purchase_value")
    private Double purchaseValue;

    private Double swap;

    private Double margin;

    private Double commission;

    private Double profit;

    @Column(name = "close_time")
    private ZonedDateTime closeTime;

    @Column(name = "source_close_price")
    private Double sourceClosePrice;

    @Column(name = "close_conversion_rate")
    private Double closeConversionRate;

    @Column(name = "broker_product")
    private String comment;

    public void setSymbol(String symbol) {
        this.symbol = symbol;
        if (this.sourceAssetSymbol == null) {
            this.sourceAssetSymbol = symbol;
        }
    }

    @PostLoad
    void loadCanonicalSymbol() {
        this.symbol = asset != null ? asset.getSymbol() : sourceAssetSymbol;
    }

    public double signedQuantity() {
        return PositionQuantities.signed(type, volume);
    }
}
