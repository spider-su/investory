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
@Table(name = "stocks")
public class Stock {
    @Id
    @SequenceGenerator(name = "stocks_id_seq", sequenceName = "stocks_id_seq", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator="stocks_id_seq")
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;
    private String symbol;
    private String ticker;
    @Enumerated(value = EnumType.STRING)
    private CurrencyType currency;
    private Double amount;
    private Double openPrice;
    @Column(name = "day_open_price")
    private Double dayOpenPrice;
    private Double marketPrice;
    private Double profit;
    private ZonedDateTime updatedDate;
    private ZonedDateTime syncDate;
}
