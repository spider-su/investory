package com.example.demo.infrastructure.repository;

import com.example.demo.infrastructure.CashOperationType;
import com.example.demo.infrastructure.CurrencyType;
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
@Table(name = "cash_operations")
public class CashOperation {

    @Id
    private Long id;

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

    @Column(name = "source_asset_symbol")
    private String sourceAssetSymbol;

    @Column(name = "broker_symbol")
    private String brokerSymbol;

    @Transient
    private String symbol;

    @Column(nullable = false)
    private Double amount;

    @Enumerated(value = EnumType.STRING)
    @Column(name = "currency", nullable = false)
    private CurrencyType currency;

    private String comment;

    @Column(nullable = false)
    private ZonedDateTime date;

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

}
