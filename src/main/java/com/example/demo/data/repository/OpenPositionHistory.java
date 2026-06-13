package com.example.demo.data.repository;

import com.example.demo.data.CurrencyType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "open_positions_history")
public class OpenPositionHistory {

    @Id
    @SequenceGenerator(name = "open_positions_history_id_seq", sequenceName = "open_positions_history_id_seq", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator="open_positions_history_id_seq")
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    private String symbol;

    @Enumerated(EnumType.STRING)
    private CurrencyType currency;

    private Double amount;

    @Column(name = "open_price")
    private Double openPrice;

    @Column(name = "close_price")
    private Double closePrice;

    @Column(name = "open_profit")
    private Double openProfit;

    @Column(name = "close_profit")
    private Double closeProfit;

    private ZonedDateTime date;
}

