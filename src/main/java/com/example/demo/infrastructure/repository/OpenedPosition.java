package com.example.demo.infrastructure.repository;

import com.example.demo.infrastructure.CurrencyType;
import com.example.demo.infrastructure.PositionType;
import jakarta.persistence.*;
import lombok.*;

import java.time.ZonedDateTime;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Entity
@EqualsAndHashCode(of = "id")
@Table(name = "opened_positions")
public class OpenedPosition {
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

    private Double marketPrice;

    private Double purchaseValue;

    private Double swap;

    private Double margin;

    private Double commission;

    private Double profit;

    private String comment;
}
