package com.example.demo.infrastructure.repository;

import com.example.demo.infrastructure.CurrencyType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZonedDateTime;

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

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @Column(nullable = false, precision = 20, scale = 8)
    private BigDecimal rate;

    @Column(name = "source", nullable = false, length = 32, insertable = false, updatable = false)
    private String source;

    @Column(name = "imported_at", nullable = false, insertable = false, updatable = false)
    private ZonedDateTime importedAt;

    public double getRate() {
        return rate == null ? 0.0 : rate.doubleValue();
    }

    public BigDecimal getRateValue() {
        return rate;
    }

    public void setRate(BigDecimal rate) {
        this.rate = scaleRate(rate);
    }

    public void setRate(Double rate) {
        this.rate = rate == null ? null : scaleRate(BigDecimal.valueOf(rate));
    }

    public void setRate(double rate) {
        setRate(Double.valueOf(rate));
    }

    private static BigDecimal scaleRate(BigDecimal rate) {
        return rate == null ? null : rate.setScale(8, RoundingMode.HALF_UP);
    }
}

