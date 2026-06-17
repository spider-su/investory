package com.example.demo.infrastructure.repository;

import com.example.demo.infrastructure.CurrencyType;
import jakarta.persistence.*;
import lombok.*;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Entity
@EqualsAndHashCode(of = "id")
@Table(name = "currencies")
public class CurrencyRate {

    @Id
    private Long id;

    @Enumerated(value = EnumType.STRING)
    private CurrencyType base;

    @Enumerated(value = EnumType.STRING)
    private CurrencyType toCurrency;

    private double rate;
}

