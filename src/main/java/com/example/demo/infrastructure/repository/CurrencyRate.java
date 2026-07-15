package com.example.demo.infrastructure.repository;

import com.example.demo.infrastructure.CurrencyType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Entity
@EqualsAndHashCode(of = "id")
@Table(name = "exchange_rates")
public class CurrencyRate {

    @Id
    @SequenceGenerator(
            name = "exchange_rates_id_seq",
            sequenceName = "exchange_rates_id_seq",
            allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "exchange_rates_id_seq")
    private Long id;

    @Column(name = "month", nullable = false)
    private LocalDate monthStart;

    @Enumerated(value = EnumType.STRING)
    @Column(nullable = false)
    private CurrencyType base;

    @Enumerated(value = EnumType.STRING)
    @Column(name = "to_currency", nullable = false)
    private CurrencyType toCurrency;

    @Column(nullable = false)
    private double rate;
}

