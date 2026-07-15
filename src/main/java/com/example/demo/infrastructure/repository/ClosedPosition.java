package com.example.demo.infrastructure.repository;

import com.example.demo.infrastructure.CurrencyType;
import com.example.demo.infrastructure.PositionType;
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
public class ClosedPosition {
    @Id
    private Long id;

    @Column(name = "account_id")
    private Long account;

    @Column(name = "asset_id")
    private String symbol;

    @Enumerated(value = EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "operation", columnDefinition = "positions_operation_type")
    private PositionType type;

    @Enumerated(value = EnumType.STRING)
    @Column(name = "currency")
    private CurrencyType currency;

    private Double volume;

    @Column(name = "open_time")
    private ZonedDateTime openTime;

    private Double openPrice;

    @Column(name = "close_time")
    private ZonedDateTime closeTime;

    private Double closePrice;

    @Column(name = "purchase_value")
    private Double purchaseValue;

    @Column(name = "sale_value")
    private Double saleValue;

    private Double margin;

    private Double commission;

    private Double swap;

    private Double profit;

    @Transient
    private String comment;
}
