package com.example.demo.data.repository;

import com.example.demo.data.CurrencyType;
import com.example.demo.data.PositionType;
import jakarta.persistence.*;
import lombok.*;

import java.time.ZonedDateTime;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Entity
@EqualsAndHashCode(of = "id")
@Table(name = "closed_positions")
public class ClosedPosition {
    @Id
    private Long id;

    private String account;

    private String symbol;

    @Enumerated(value = EnumType.STRING)
    private PositionType type;

    @Enumerated(value = EnumType.STRING)
    private CurrencyType currency;

    private Double volume;

    private ZonedDateTime openTime;

    private Double openPrice;

    private ZonedDateTime closeTime;

    private Double closePrice;

    private Double purchaseValue;

    private Double saleValue;

    private Double margin;

    private Double commission;

    private Double profit;

    private String comment;
}
