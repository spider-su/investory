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
@Table(name = "portfolio_history")
public class PortfolioHistory {

    @Id
    @SequenceGenerator(name = "portfolio_history_id_seq", sequenceName = "portfolio_history_id_seq", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator="portfolio_history_id_seq")
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @Column(name = "portfolio_id")
    private Long portfolioId;

    @Enumerated(EnumType.STRING)
    private CurrencyType currency;

    @Column(name = "open_total")
    private Double openTotal;

    @Column(name = "close_total")
    private Double closeTotal;

    private ZonedDateTime date;
}

